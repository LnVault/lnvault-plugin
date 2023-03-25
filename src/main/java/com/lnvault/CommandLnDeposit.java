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
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.util.HashSet;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandLnDeposit implements CommandExecutor {
    
    static
    {
        CommandLnConfig.CONFIG_KEYS.add("deposit.limit");
    }
    
    @Override
    public boolean onCommand(CommandSender cs, Command cmnd, String string, String[] args) {

        if (!(cs instanceof Player)) return false;
        if (args.length != 1) return false;
        
        var player = (Player)cs;
        var state = LnVault.getPlayerState(player, LnVault.getCtx());
               
        try
        {
            LnVault.putLnVaultMapInHand(player);
            
            if( state.getPaymentRequest() != null) {  //Only allow 1 payment to be pending.
                player.chat("Previous deposit is still pending payment.");
                return true;
            }
            
            var localAmount = Double.parseDouble(args[0]);
            var satsAmount = LnVault.convertLocalToSats(localAmount);
            
            LnVault.getCtx().getRepo().getTotalDepositsSince(player.getUniqueId() , System.currentTimeMillis() - (1000 * 60 * 60 *24) ,
                (totalDeposits) -> {
                
                    try
                    {
                        long limit = 1000;
                        var limitStr = LnVault.getCtx().getRepo().getConfig("deposit.limit");
                        if( limitStr != null && limitStr != "") {
                            limit = Long.parseLong(limitStr);
                        }

                        if(totalDeposits + satsAmount > limit) {
                            player.chat("Deposit will exceeded daily deposit limit.");
                            return null;
                        }

                        LnVault.getCtx().getLnBackend().generatePaymentRequest(player, satsAmount, localAmount,
                            (payReq) -> {
                                try
                                {
                                    payReq.setPlayerUUID(player.getUniqueId());
                                    payReq.setCreatedTimeStamp(System.currentTimeMillis());

                                    LnVault.getCtx().getRepo().auditPaymentRequest(payReq, false);
                                    state.setPaymentRequest(payReq);
                                }
                                catch(Exception e)
                                {
                                    state.setPaymentError("Error", System.currentTimeMillis() );
                                    player.chat("lndeposit failed - " + e.getMessage() );
                                    LnVault.getCtx().getLogger().log(Level.WARNING,e.getMessage(),e);
                                }

                                return null;
                            },
                            (e) -> {
                                state.setPaymentError("Error", System.currentTimeMillis() );
                                player.chat("lndeposit failed - " + e.getMessage() );
                                LnVault.getCtx().getLogger().log(Level.WARNING, e.getMessage(), e);
                                return null;
                            },
                            (payReq) -> {
                                LnVault.confirmPayment(payReq);
                                return null;
                            }
                        ); 
                    }  
                    catch( Exception ex)
                    {
                        state.setWithdrawalError("Error", System.currentTimeMillis() );
                        player.chat("lndeposit failed - " + ex.getMessage() );
                        LnVault.getCtx().getLogger().log(Level.WARNING,ex.getMessage(),ex);
                    }                    

                    return null;                
                },
                (e) -> {
                    state.setPaymentError("Error", System.currentTimeMillis() );
                    player.chat("lndeposit failed - " + e.getMessage() );
                    LnVault.getCtx().getLogger().log(Level.WARNING, e.getMessage(), e);
                    return null;
                }
            );
            
            return true;
        }
        catch( Exception e)
        {
            state.setWithdrawalError("Error", System.currentTimeMillis() );
            player.chat("lndeposit failed - " + e.getMessage() );
            LnVault.getCtx().getLogger().log(Level.WARNING,e.getMessage(),e);
            return false;
        }
    }
}