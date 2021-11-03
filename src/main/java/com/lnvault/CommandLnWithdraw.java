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
import java.util.HashSet;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandLnWithdraw implements CommandExecutor {

    static
    {
        CommandLnConfig.CONFIG_KEYS.add("withdrawal.limit");
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
            if (state.getWithdrawalRequest() != null) { //Only allow 1 withdrawal to be pending.
                player.chat("Previous withdrawal is still pending.");
                return true; 
            }            
            
            var localAmount = Double.parseDouble(args[0]);
            var satsAmount = LnVault.convertLocalToSats(localAmount);

            var curBalance = LnVault.getCtx().getEconomy().getBalance(player);

            if( localAmount > curBalance) {
                return false;
            }

            LnVault.getCtx().getRepo().getTotalWithdrawalsSince(player.getUniqueId() , System.currentTimeMillis() - (1000 * 60 * 60 *24) 
                , (totalWithdrawls) -> {
                
                    try
                    {            
                        long limit = 1000;
                        var limitStr = LnVault.getCtx().getRepo().getConfig("withdrawal.limit");
                        if( limitStr != null && limitStr != "") {
                            limit = Long.parseLong(limitStr);
                        }

                        if(totalWithdrawls + satsAmount > limit) {
                            state.setWithdrawalError("Error", System.currentTimeMillis() );
                            player.chat("Withdrawal will exceed daily withdrawal limit.");
                            return null;
                        }                    

                        LnVault.getCtx().getLnBackend().generateWithdrawal(player, satsAmount,localAmount,
                            (wdReq) -> {
                                try
                                {
                                    wdReq.setPlayerUUID(player.getUniqueId());
                                    wdReq.setTimeStamp(System.currentTimeMillis());               

                                    LnVault.getCtx().getRepo().auditWithdrawalRequest(wdReq, false);

                                    state.setWithdrawalRequest(wdReq);
                                }
                                catch(Exception e)
                                {
                                    state.setWithdrawalError("Error", System.currentTimeMillis() );
                                    player.chat("lnwithdraw failed - " + e.getMessage() );
                                    LnVault.getCtx().getLogger().log(Level.WARNING, e.getMessage(), e);
                                }
                                return null;
                            },
                            (e) -> {
                                state.setWithdrawalError("Error", System.currentTimeMillis() );
                                player.chat("lnwithdraw failed - " + e.getMessage() );
                                LnVault.getCtx().getLogger().log(Level.WARNING, e.getMessage(), e);
                                return null;
                            }
                        );
                    }
                    catch(Exception ex)
                    {
                        state.setWithdrawalError("Error", System.currentTimeMillis() );
                        player.chat("lnwithdraw failed - " + ex.getMessage() );
                        LnVault.getCtx().getLogger().log(Level.WARNING, ex.getMessage(), ex);
                    }

                    return null;
                },
                (e) -> {
                    state.setWithdrawalError("Error", System.currentTimeMillis() );
                    player.chat("lnwithdraw failed - " + e.getMessage() );
                    LnVault.getCtx().getLogger().log(Level.WARNING, e.getMessage(), e);
                    return null;
                }
            );

            return true;
        } catch (Exception e) {
            state.setWithdrawalError("Error", System.currentTimeMillis() );
            player.chat("lnwithdraw failed - " + e.getMessage() );
            LnVault.getCtx().getLogger().log(Level.WARNING, e.getMessage(), e);
            return false;
        }
    }

    public static boolean isWithdrawn(WithdrawalRequest wdReq) {
        if (wdReq == null) return true;
        return LnVault.getCtx().getLnBackend().isWithdrawn(wdReq);
    }

}