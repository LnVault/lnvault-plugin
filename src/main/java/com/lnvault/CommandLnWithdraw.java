/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.lnvault;

import com.lnvault.data.PaymentRequest;
import com.lnvault.data.PlayerState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lnvault.bolt11.Bolt11;
import com.lnvault.data.WithdrawalRequest;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.util.logging.Level;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandLnWithdraw implements CommandExecutor {

    static
    {
        CommandLnConfig.CONFIG_KEYS.add("withdrawal.limit");
        
        CommandLnUserConfig.CONFIG_KEYS.add("lnaddress");
    }
    
    @Override
    public boolean onCommand(CommandSender cs, Command cmnd, String string, String[] args) {

        if (!(cs instanceof Player)) {
            return false;
        }
        if (args.length != 1) {
            return false;
        }

        var player = (Player) cs;
        
       
        var state = LnVault.getPlayerState(player, LnVault.getCtx());

        try {
            LnVault.putLnVaultMapInHand(player);        
            
            var localAmount = Double.parseDouble(args[0]);
            var satsAmount = LnVault.convertLocalToSats(localAmount);

            var curBalance = LnVault.getCtx().getEconomy().getBalance(player);

            if( localAmount > curBalance) {
                state.setWithdrawalError("Error", System.currentTimeMillis() );
                player.sendMessage("Withdrawal exceeds current balance.");              
                return true;
            }

            LnVault.getCtx().getRepo().getTotalWithdrawalsSince(player.getUniqueId() , System.currentTimeMillis() - (1000 * 60 * 60 *24) 
                , (totalWithdrawls) -> {
                
                    try
                    {            
                        long limit = 1000;
                        var limitStr = LnVault.getCtx().getRepo().getConfig("withdrawal.limit");
                        if( limitStr != null && !limitStr.isEmpty()) {
                            limit = Long.parseLong(limitStr);
                        }

                        if(totalWithdrawls + satsAmount > limit) {
                            state.setWithdrawalError("Error", System.currentTimeMillis() );
                            player.sendMessage("Withdrawal exceeds daily withdrawal limit.");
                            return null;
                        }                    

                        var lnaddress = LnVault.getCtx().getRepo().getUserConfig(player.getUniqueId().toString(), "lnaddress");                       
                        if( lnaddress != null && !lnaddress.isEmpty() && lnaddress.contains("@")) {                           
                            var digest = MessageDigest.getInstance("SHA-256");
                            
                            var split = lnaddress.split("@",2);
                            WebService.call("https://" + split[1] + "/.well-known/lnurlp/" + split[0], null, null, 
                                (body) -> {
                                    try {
                                        //LnVault.getCtx().getLogger().log(Level.WARNING, "LNADDRESS BODY:" + body);

                                        var res = new JsonParser().parse(body).getAsJsonObject();                  
                                        var tag = res.get("tag").getAsString();
                                        if( !"payRequest".equals(tag)) {
                                            LnVault.getCtx().getLogger().log(Level.WARNING, "LNURL HASH mismatch");
                                            state.setWithdrawalError("Error", System.currentTimeMillis());
                                            player.sendMessage("Withdrawal failed.");                                              
                                            return null;
                                        }

                                        var callbackUrl = res.get("callback").getAsString();
                                        var metadata = res.get("metadata").getAsString();

                                        var metadataHash = digest.digest(metadata.getBytes(StandardCharsets.UTF_8));

                                        WebService.call(callbackUrl + "?amount=" + (satsAmount*1000)  , null, null,
                                        (lnurlBody) -> {
                                            //LnVault.getCtx().getLogger().log(Level.WARNING, "LNURL:" + lnurlBody);
                                            try{


                                                var lnurlRes = new JsonParser().parse(lnurlBody).getAsJsonObject();                  
                                                var pr = lnurlRes.get("pr").getAsString();
                                                var prLnUrlHash = Bolt11.ExtractLnUrlPayHash(pr);

                                                if( !Arrays.equals(metadataHash,prLnUrlHash) ) {
                                                    LnVault.getCtx().getLogger().log(Level.WARNING, "LNURL HASH mismatch");
                                                    state.setWithdrawalError("Error", System.currentTimeMillis());
                                                    player.sendMessage("Withdrawal failed.");                                                     
                                                    return null;
                                                }

                                                LnVault.getCtx().getLnBackend().generateWithdrawal(player,satsAmount, pr, 
                                                    (wdReq) -> {
                                                        try
                                                        {
                                                            wdReq.setPlayerUUID(player.getUniqueId());
                                                            wdReq.setTimeStamp(System.currentTimeMillis());  
                                                            wdReq.setLocalAmount(localAmount);
                                                            wdReq.setSatsAmount(satsAmount);

                                                            LnVault.getCtx().getRepo().auditWithdrawalRequest(wdReq, false);

                                                            state.setWithdrawalRequest(wdReq);

                                                            //Deduct from the balance immediately as we cannot reliably detect a confirmed withdrawal as some wallets modify the description causing the id to be lost
                                                            //If we are ever able to detect an error we can re-add the deducted amount back to the uesers balance
                                                            var response = LnVault.getCtx().getEconomy().withdrawPlayer(player, wdReq.getLocalAmount());
                                                            if( response.type != EconomyResponse.ResponseType.SUCCESS )                        
                                                            {
                                                                LnVault.getCtx().getLogger().log(Level.WARNING, "LNURL economyWithdrawPlayer :" + response.errorMessage);
                                                                state.setWithdrawalError("Error", wdReq.getTimeStamp());
                                                                player.sendMessage("Withdrawal failed." + response.errorMessage);                                                               
                                                            }                                    

                                                        }
                                                        catch(Exception e)
                                                        {
                                                            LnVault.getCtx().getLogger().log(Level.WARNING, e.getMessage(), e);
                                                            state.setWithdrawalError("Error", System.currentTimeMillis() );
                                                            player.sendMessage("Withdrawal failed.");
                                                        }
                                                        return null;
                                                    },
                                                    (e) -> {
                                                        LnVault.getCtx().getLogger().log(Level.WARNING, "LNURL ERROR:" + e.getMessage() ,e);
                                                        state.setWithdrawalError("Error", System.currentTimeMillis() );
                                                        player.sendMessage("Withdrawal failed.");

                                                        return null;
                                                    },
                                                    (wdReq) -> {
                                                        LnVault.confirmWithdrawal(wdReq);
                                                        return null;
                                                    });

                                                return null;
                                            } catch (Exception e) {
                                                LnVault.getCtx().getLogger().log(Level.WARNING, "LNURL ERROR:" + e.getMessage() ,e);
                                                state.setWithdrawalError("Error", System.currentTimeMillis() );
                                                player.sendMessage("Withdrawal failed.");                                                                                            
                                                return null;
                                            }
                                        },
                                        (e) -> {
                                            LnVault.getCtx().getLogger().log(Level.WARNING, "LNURL ERROR:" + e.getMessage() ,e);
                                            state.setWithdrawalError("Error", System.currentTimeMillis() );
                                            player.sendMessage("Withdrawal failed.");                                             
                                            return null;
                                        });


                                        return null;
                                    } catch (Exception e) {
                                        LnVault.getCtx().getLogger().log(Level.WARNING, "lnaddress error:" + e.getMessage() ,e);
                                        state.setWithdrawalError("Error", System.currentTimeMillis() );
                                        player.sendMessage("Withdrawal failed.");                                       
                                        return null;
                                    }
                                },
                                (e) -> {
                                    LnVault.getCtx().getLogger().log(Level.WARNING, "LNADDRESS ERROR:" + e.getMessage() ,e);
                                    state.setWithdrawalError("Error", System.currentTimeMillis() );
                                    player.sendMessage("Withdrawal failed.");                                    
                                    return null;
                                });                            
                        } else {
                            LnVault.getCtx().getLnBackend().generateWithdrawal(player, satsAmount,localAmount,
                                (wdReq) -> {
                                    try
                                    {
                                        wdReq.setPlayerUUID(player.getUniqueId());
                                        wdReq.setTimeStamp(System.currentTimeMillis());               

                                        LnVault.getCtx().getRepo().auditWithdrawalRequest(wdReq, false);

                                        state.setWithdrawalRequest(wdReq);

                                        //Deduct from the balance immediately as we cannot reliably detect a confirmed withdrawal as some wallets modify the description causing the id to be lost
                                        //If we are ever able to detect an error we can re-add the deducted amount back to the uesers balance
                                        var response = LnVault.getCtx().getEconomy().withdrawPlayer(player, wdReq.getLocalAmount());
                                        if( response.type != EconomyResponse.ResponseType.SUCCESS )                        
                                        {
                                            LnVault.getCtx().getLogger().log(Level.WARNING, "economyWithdrawPlayer :" + response.errorMessage);
                                            state.setWithdrawalError("Error", wdReq.getTimeStamp());
                                            player.sendMessage("Withdrawal failed." + response.errorMessage);                                                               
                                        }                                    

                                    }
                                    catch(Exception e)
                                    {
                                        LnVault.getCtx().getLogger().log(Level.WARNING, "generateWithdrawal " + e.getMessage(),e);
                                        state.setWithdrawalError("Error", System.currentTimeMillis());
                                        player.sendMessage("Withdrawal failed.");
                                    }
                                    return null;
                                },
                                (e) -> {
                                    LnVault.getCtx().getLogger().log(Level.WARNING, "generateWithdrawal " + e.getMessage(),e);
                                    state.setWithdrawalError("Error", System.currentTimeMillis());
                                    player.sendMessage("Withdrawal failed.");
                                    return null;
                                },
                                (wdReq) -> {
                                    LnVault.confirmWithdrawal(wdReq);
                                    return null;
                                }
                            );
                        }
                    }
                    catch(Exception ex)
                    {
                        LnVault.getCtx().getLogger().log(Level.WARNING, "CommandLnWithdraw " + ex.getMessage(),ex);
                        state.setWithdrawalError("Error", System.currentTimeMillis());
                        player.sendMessage("Withdrawal failed.");
                    }

                    return null;
                },
                (e) -> {
                    LnVault.getCtx().getLogger().log(Level.WARNING, "CommandLnWithdraw " + e.getMessage(),e);
                    state.setWithdrawalError("Error", System.currentTimeMillis());
                    player.sendMessage("Withdrawal failed.");
                    return null;
                }
            );

            return true;
        } catch (Exception e) {
            LnVault.getCtx().getLogger().log(Level.WARNING, "CommandLnWithdraw " + e.getMessage(),e);
            state.setWithdrawalError("Error", System.currentTimeMillis());
            player.sendMessage("Withdrawal failed.");
            return true;
        }
    }
}