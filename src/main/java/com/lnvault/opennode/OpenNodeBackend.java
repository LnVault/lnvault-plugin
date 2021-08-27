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
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import org.bukkit.entity.Player;

public class OpenNodeBackend implements LnBackend {

    static
    {
        CommandLnConfig.CONFIG_KEYS.add("opennode.deposit.key");
        CommandLnConfig.CONFIG_KEYS.add("opennode.withdraw.key");
    }
    
    private Context ctx;
    private long lastPaymentRequestPoll = 0;
    
    private HashSet<String> processedPaymentRequests = new HashSet<String>();
    private HashSet<String> processedWithdrawals = new HashSet<String>();
    
    
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
        
    }
    
    private void updatePaymentRequests()
    {
        try
        {
            var apiKey = ctx.getRepo().getConfig("opennode.deposit.key");
            if( apiKey == null ){
                return;
            }
            
            //Backoff on Payment polling
            var now = System.currentTimeMillis();
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

                    processedPaymentRequests.clear();
                    for( var pr : data ) {

                        var prObj = pr.getAsJsonObject();

                        var id = prObj.get("id").getAsString();
                        var status = prObj.get("status").getAsString();
                        if( "paid".equals(status) ) {
                            processedPaymentRequests.add(id);
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
            
            WebService.blockingCall("https://api.opennode.com/v1/withdrawals/", apiKey, null, (resStr) -> {
                try {

                    var res = new JsonParser().parse(resStr).getAsJsonObject();
                    var data = res.get("data").getAsJsonArray();

                    processedWithdrawals.clear();
                    for (var wd : data) {

                        var wdObj = wd.getAsJsonObject();

                        var status = wdObj.get("status").getAsString();
                        if ("confirmed".equals(status)) {
                            var referenceInvoice = wdObj.get("reference").getAsString();
                            var description = Bolt11.ExtractDescription(referenceInvoice);
                            processedWithdrawals.add(description);

                        }
                    }
                } catch (Exception e) {
                    ctx.getLogger().log(Level.WARNING, e.getMessage(), e);
                }

                return null;

            },
            (e) -> {
                //This will cause a players withdrawal requests to stall.
                ctx.getLogger().log(Level.WARNING,e.getMessage(),e);
                return null;
            });
        } catch (Exception e) {
             ctx.getLogger().log(Level.WARNING, e.getMessage(), e);
        }            
    }
    
    public boolean isPaid(PaymentRequest payReq)
    {
        if( payReq == null ) return true;
        return processedPaymentRequests.contains(payReq.getId());    
    }

    public boolean isWithdrawn(WithdrawalRequest wdReq) {
        if (wdReq == null) return false;
        return processedWithdrawals.contains(wdReq.getDescription());
    }
    
    public void generatePaymentRequest(Player player, long satsAmount , double localAmount, Function<PaymentRequest,?> generated , Function<Exception,?> fail ) {
        
        try
        {
            var apiKey = ctx.getRepo().getConfig("opennode.deposit.key");
            if( apiKey == null ){
                fail.apply(new Exception("opennode.deposit.key is not configured."));
                return;
            }

            var reqObj = new JsonObject();
            reqObj.addProperty("amount", satsAmount);
            reqObj.addProperty("description", "LnVault Deposit $" + localAmount);
            
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

                    generated.apply(payReq);
                }
                catch(Exception e)
                {
                    fail.apply(e);
                }
                return null;
            } , fail);
        }
        catch( Exception e)
        {
            fail.apply(e);
        }
    }
    
    public void generateWithdrawal(Player player, long satsAmount, double localAmount, Function<WithdrawalRequest,?> generated, Function<Exception,?> fail) {
        
        try
        {
            var apiKey = ctx.getRepo().getConfig("opennode.withdraw.key");
            if( apiKey == null ){
                fail.apply(new Exception("opennode.withdraw.key is not configured."));
                return;
            }            

            var id = UUID.randomUUID().toString();
            var description = "LnVault Withdrawal $" + localAmount + " " + id;
            
            var reqObj = new JsonObject();
            reqObj.addProperty("min_amt", satsAmount);
            reqObj.addProperty("max_amt", satsAmount);
            reqObj.addProperty("description", description);
            reqObj.addProperty("callback_url", "");
            reqObj.addProperty("external_id", id);
            
            var req = new Gson().toJson(reqObj);
            
            LnVault.getCtx().getLogger().info(req);
            
            WebService.call("https://api.opennode.com/v2/lnurl-withdrawal", apiKey, req , (resStr) -> {

                try
                {
                    var res = new JsonParser().parse(resStr).getAsJsonObject();
                    var data = res.get("data").getAsJsonObject();
                    var lnurl = data.get("lnurl").getAsString();

                    var wdReq = new WithdrawalRequest();
                    wdReq.setId(id);
                    wdReq.setDescription(description);
                    wdReq.setRequest(lnurl);
                    wdReq.setSatsAmount(satsAmount);
                    wdReq.setLocalAmount(localAmount);

                    generated.apply(wdReq);
                }
                catch (Exception e) {
                    fail.apply(e);
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
