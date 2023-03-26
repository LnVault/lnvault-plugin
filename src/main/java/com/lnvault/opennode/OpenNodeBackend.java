package com.lnvault.opennode;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lnvault.CommandLnConfig;
import com.lnvault.CommandLnDeposit;
import com.lnvault.CommandLnWithdraw;
import com.lnvault.Context;
import com.lnvault.LnBackend;
import com.lnvault.LnVault;
import com.lnvault.QRCode;
import com.lnvault.WebService;
import com.lnvault.bolt11.Bolt11;
import com.lnvault.data.PaymentRequest;
import com.lnvault.data.WithdrawalRequest;
import java.net.URI;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import org.bukkit.entity.Player;

public class OpenNodeBackend implements LnBackend {
  record WithdrawalRequestFunction(WithdrawalRequest withdrawal, Function<WithdrawalRequest,?> confirmed) {}  
  record PaymentRequestFunction(PaymentRequest payment, Function<PaymentRequest,?> confirmed) {}  
    
    
    static
    {
        CommandLnConfig.CONFIG_KEYS.add("opennode.deposit.key");
        CommandLnConfig.CONFIG_KEYS.add("opennode.withdraw.key");
        CommandLnConfig.CONFIG_KEYS.add("opennode.callback.url");
        CommandLnConfig.CONFIG_KEYS.add("opennode.callback.port");
    }
    
    private Context ctx;
    private long lastPaymentRequestPoll = 0;
    
    private HashMap<String,PaymentRequestFunction> pendingPaymentRequests = new HashMap<>();    
    private HashMap<String,WithdrawalRequestFunction> pendingWithdrawals = new HashMap<>();
    
    private String getCallbackUrl(String path) throws Exception {
        var callbackUrl = ctx.getRepo().getConfig("opennode.callback.url");
        if( callbackUrl == null || callbackUrl.isEmpty() ) {
            return null;
        }
        
        return new URI(callbackUrl).resolve(path).toString();   
    }
    
    public void init(Context ctx)
    {
        this.ctx = ctx;
        
        ctx.getBackgroundQueue().scheduleWithFixedDelay(()-> {
            if(ctx.isPaymentRequestActive()) {
                updatePaymentRequests();
            }
        },1,1,TimeUnit.SECONDS);

        ctx.getBackgroundQueue().scheduleWithFixedDelay(()-> {
            if(ctx.isWithdrawalActive()) {
                updateWithdrawals();
            }
        },2,2,TimeUnit.SECONDS);
        
        try {
            OpenNodeWebHookHandler.init(ctx,this);
        } catch( Exception e) {
            ctx.getLogger().log(Level.WARNING,"OpenNodeWebHookHandler" + e.getMessage(),e);
        }       
        
    }
    
    private void updatePaymentRequests()
    {
        try
        {
            var apiKey = ctx.getRepo().getConfig("opennode.deposit.key");
            if( apiKey == null ){
                return;
            }

            var now = System.currentTimeMillis();
            
            var cleanup = pendingPaymentRequests.values().iterator();
            while( cleanup.hasNext()) {
                var pending = cleanup.next();
                if( pending.payment.getExpiresAt() != 0 && now > (pending.payment.getExpiresAt() + (2 * 60 * 1000)) ){
                    ctx.getLogger().log(Level.INFO, "Cleaned up expired payment " + pending.payment.getId());
                    cleanup.remove();
                }
            }            
            
            //Backoff on Payment polling
            var payReqTimeStamp = ctx.getMostRecentPaymentRequestTimeStamp();
            var timeSincePaymentRequest = now - payReqTimeStamp;
            var scaled = (long)(0.1 * (double)timeSincePaymentRequest); //At 10 minutes poll once per minute
            var nextPollDue = lastPaymentRequestPoll + scaled;

            if( nextPollDue > now) {
                return;                
            }

            lastPaymentRequestPoll = now;
            WebService.blockingCall("https://api.opennode.com/v1/charges/",apiKey,null,
                (resStr) -> {
                    var res = new JsonParser().parse(resStr).getAsJsonObject();
                    var data = res.get("data").getAsJsonArray();

                    for( var pr : data ) {

                        var prObj = pr.getAsJsonObject();

                        var id = prObj.get("id").getAsString();
                        var status = prObj.get("status").getAsString();
                        if( "paid".equals(status) ) {
                            confirmPayment(id);
                        }
                    }

                    return null;
                },
                (e) -> {
                    //This will cause a players payment requests to stall.
                    ctx.getLogger().log(Level.WARNING,e.getMessage(),e);
                    return null;
                });            
        }
        catch( Exception e)
        {
            ctx.getLogger().log(Level.WARNING,e.getMessage(),e);
        }          
    }

    private void updateWithdrawals() {
        try
        {
            var apiKey = ctx.getRepo().getConfig("opennode.withdraw.key");
            if( apiKey == null ){
                return;
            }

            var now = System.currentTimeMillis();
            
            var cleanup = pendingWithdrawals.values().iterator();
            while( cleanup.hasNext()) {
                var pendingWithdrawal = cleanup.next();
                if( now > pendingWithdrawal.withdrawal.getExpiresAt() ){
                    ctx.getLogger().log(Level.INFO, "Cleaned up expired withdrawal" + pendingWithdrawal.withdrawal.getId());
                    cleanup.remove();
                }
            }
            
            WebService.blockingCall("https://api.opennode.com/v2/withdrawals/", apiKey, null, (resStr) -> {
                try {

                    var res = new JsonParser().parse(resStr).getAsJsonObject();                  
                    var data = res.get("data").getAsJsonObject().get("items").getAsJsonArray();
                    
                    //ctx.getLogger().log(Level.WARNING, "DEBUG WITHDRAWALS");                                                
                    
                    for (var wd : data) {

                        var wdObj = wd.getAsJsonObject();                     
                        
                        var status = wdObj.get("status").getAsString();                        
                        if ("confirmed".equals(status)) {
                            var referenceInvoice = wdObj.get("reference").getAsString();
                            var description = Bolt11.ExtractDescription(referenceInvoice);
                            var payHash = Bolt11.ExtractPaymentHash(referenceInvoice);
                            
                            //ctx.getLogger().log(Level.WARNING, "DEBUG "  + description + ":" + payHash + " : " + referenceInvoice);                            
                            
                            var i = pendingWithdrawals.values().iterator();
                            while( i.hasNext()) {
                                var pendingWithdrawal = i.next();
                                if( pendingWithdrawal.withdrawal.getId().equals(payHash)) {
                                    i.remove();
                                    ctx.getLogger().log(Level.INFO, "batch confirmed withdrawal payhash" + pendingWithdrawal.withdrawal.getId());
                                    pendingWithdrawal.confirmed.apply(pendingWithdrawal.withdrawal);                                    
                                } else if( description.contains(pendingWithdrawal.withdrawal.getId()) ){
                                    i.remove();
                                    ctx.getLogger().log(Level.INFO, "batch confirmed withdrawal " + pendingWithdrawal.withdrawal.getId());
                                    pendingWithdrawal.confirmed.apply(pendingWithdrawal.withdrawal);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    ctx.getLogger().log(Level.WARNING, e.getMessage(), e);
                }

                return null;

            },
            (e) -> {
                //This will cause a players withdrawal requests to stall.
                ctx.getLogger().log(Level.WARNING,"updateWithdrawals:" + e.getMessage(),e);
                return null;
            });            
        } catch (Exception e) {
             ctx.getLogger().log(Level.WARNING, "updateWithdrawals:" +e.getMessage(), e);
        }            
    }

    public void confirmPayment(String paymentId) {
        var pendingPayment = pendingPaymentRequests.remove(paymentId);      
        if( pendingPayment != null ) {
            ctx.getLogger().log(Level.INFO, "confirmed payment " + paymentId);        
            pendingPayment.confirmed.apply(pendingPayment.payment);
        }
    }    
    
    public void confirmWithdrawal(String withdrawalId) {
        var pendingWithdrawal = pendingWithdrawals.remove(withdrawalId);      
        if( pendingWithdrawal != null ) {
            ctx.getLogger().log(Level.INFO, "hook confirmed withdrawal " + withdrawalId);        
            pendingWithdrawal.confirmed.apply(pendingWithdrawal.withdrawal);
        }
    }
    
    public void generatePaymentRequest(Player player, long satsAmount , double localAmount, Function<PaymentRequest,?> generated , Function<Exception,?> fail ,Function<PaymentRequest,?> confirmed) {
        
        try
        {
            var apiKey = ctx.getRepo().getConfig("opennode.deposit.key");
            if( apiKey == null ){
                fail.apply(new Exception("opennode.deposit.key is not configured."));
                return;
            }
            
            var descFmt = ctx.getRepo().getConfig("deposit.description");
            if( descFmt == null ){
                descFmt = "LnVault Deposit ${0}";
            } 
            var description = MessageFormat.format(descFmt,localAmount);            

            var reqObj = new JsonObject();
            reqObj.addProperty("amount", satsAmount);
            reqObj.addProperty("description", description);
            reqObj.addProperty("callback_url", getCallbackUrl("/deposit/"));
            
            var req = new Gson().toJson(reqObj);
            
            LnVault.getCtx().getLogger().info(req);

            WebService.call("https://api.opennode.com/v1/charges",apiKey,req , (resStr) -> {
                try
                {
                    var res = new JsonParser().parse(resStr).getAsJsonObject();
                    var data = res.get("data").getAsJsonObject();
                    var id = data.get("id").getAsString();
                    var invoiceData = data.get("lightning_invoice").getAsJsonObject();
                    var invoice  = invoiceData.get("payreq").getAsString();
                    var expiresAt  = invoiceData.get("expires_at").getAsLong() * 1000; //Convert to java standard milliseconds
                    
                    LnVault.getCtx().getLogger().info("Expires At " + expiresAt);

                    var payReq = new PaymentRequest();
                    payReq.setId(id);
                    payReq.setRequest(invoice);
                    payReq.setExpiresAt(expiresAt);
                    payReq.setSatsAmount(satsAmount);
                    payReq.setLocalAmount(localAmount);
                    payReq.setPlayerUUID(player.getUniqueId());

                    pendingPaymentRequests.put(id, new PaymentRequestFunction(payReq,confirmed));
                    generated.apply(payReq);
                }
                catch(Exception e)
                {
                    LnVault.getCtx().getMinecraftQueue().add(() -> {
                        fail.apply(e);
                    });                    
                }
                return null;
            } , fail);
        }
        catch( Exception e)
        {
            fail.apply(e);
        }
    }
    
    public void generateWithdrawal(Player player, long satsAmount, String prBech32, Function<WithdrawalRequest,?> generated, Function<Exception,?> fail,Function<WithdrawalRequest,?> confirmed) {
    
        try
        {
            var apiKey = ctx.getRepo().getConfig("opennode.withdraw.key");
            if( apiKey == null ){
                fail.apply(new Exception("opennode.withdraw.key is not configured."));
                return;
            }

            var descFmt = ctx.getRepo().getConfig("withdrawal.description");
            if( descFmt == null ){
                descFmt = "LnVault Withdrawal ${0} {1}";
            } 

            var id = Bolt11.ExtractPaymentHash(prBech32);
            if( id==null || id.isEmpty() ) {
                throw new Exception("could not parse payment request");
            }
            
            var reqObj = new JsonObject();
            reqObj.addProperty("type", "ln");
            reqObj.addProperty("address", prBech32);
            reqObj.addProperty("amount", satsAmount);
            reqObj.addProperty("callback_url", getCallbackUrl("/withdraw/" + id));
            
            var req = new Gson().toJson(reqObj);
            
            LnVault.getCtx().getLogger().info(req);
            
            WebService.call("https://api.opennode.com/v2/withdrawals", apiKey, req , (resStr) -> {

                try
                {
                    var wdReq = new WithdrawalRequest();
                    wdReq.setId(id);
                    wdReq.setPlayerUUID(player.getUniqueId());
                    
                    var now = System.currentTimeMillis();
                    wdReq.setExpiresAt(now + (1 * 60 * 1000) );

                    pendingWithdrawals.put(wdReq.getId(), new WithdrawalRequestFunction(wdReq, confirmed));
                    generated.apply(wdReq);
                }
                catch (Exception e) {
                    LnVault.getCtx().getMinecraftQueue().add(() -> {
                        fail.apply(e);
                    });
                }

                return null;
            },fail);
        }
        catch( Exception e)
        {
            fail.apply(e);
        }        
    }

    public void generateWithdrawal(Player player, long satsAmount, double localAmount, Function<WithdrawalRequest,?> generated, Function<Exception,?> fail,Function<WithdrawalRequest,?> confirmed) {
        
        try
        {
            var apiKey = ctx.getRepo().getConfig("opennode.withdraw.key");
            if( apiKey == null ){
                fail.apply(new Exception("opennode.withdraw.key is not configured."));
                return;
            }

            var descFmt = ctx.getRepo().getConfig("withdrawal.description");
            if( descFmt == null ){
                descFmt = "LnVault Withdrawal ${0} {1}";
            }

            var id = UUID.randomUUID().toString();
            var description = MessageFormat.format(descFmt,localAmount,id);
            
            var reqObj = new JsonObject();
            reqObj.addProperty("min_amt", satsAmount);
            reqObj.addProperty("max_amt", satsAmount);
            reqObj.addProperty("description", description);           
            reqObj.addProperty("callback_url", getCallbackUrl("/withdraw/" + id));
            reqObj.addProperty("external_id", id);
            
            var req = new Gson().toJson(reqObj);
            
            LnVault.getCtx().getLogger().info(req);
            
            WebService.call("https://api.opennode.com/v2/lnurl-withdrawal", apiKey, req , (resStr) -> {

                try
                {
                    var res = new JsonParser().parse(resStr).getAsJsonObject();
                    //ctx.getLogger().log(Level.WARNING, "lnurl res" + res.toString());
                    var data = res.get("data").getAsJsonObject();
                    var lnurl = data.get("lnurl").getAsString();

                    var wdReq = new WithdrawalRequest();
                    wdReq.setId(id);
                    wdReq.setDescription(description);
                    wdReq.setRequest(lnurl);
                    wdReq.setSatsAmount(satsAmount);
                    wdReq.setLocalAmount(localAmount);
                    wdReq.setPlayerUUID(player.getUniqueId());
                    
                    var now = System.currentTimeMillis();
                    wdReq.setExpiresAt(now + (1 * 60 * 1000) );

                    pendingWithdrawals.put(wdReq.getId(), new WithdrawalRequestFunction(wdReq, confirmed));
                    generated.apply(wdReq);
                }
                catch (Exception e) {
                    LnVault.getCtx().getMinecraftQueue().add(() -> {
                        fail.apply(e);
                    });
                }

                return null;
            },fail);
        }
        catch( Exception e)
        {
            fail.apply(e);
        }        
    }
    
}
