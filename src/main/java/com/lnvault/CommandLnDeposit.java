package com.lnvault;

import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandLnDeposit implements CommandExecutor {
    
    static
    {
        CommandLnConfig.CONFIG_KEYS.add("deposit.limit");
        
        CommandLnConfig.CONFIG_KEYS.add("http.retries");
        CommandLnConfig.CONFIG_KEYS.add("http.timeoutmillis");
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
                player.sendMessage("Previous deposit is still pending payment.");
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
                        if( limitStr != null && !limitStr.isEmpty()) {
                            limit = Long.parseLong(limitStr);
                        }

                        if(totalDeposits + satsAmount > limit) {
                            player.sendMessage("Deposit will exceeds daily deposit limit.");
                            state.setPaymentError("Error", System.currentTimeMillis());
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
                                    LnVault.getCtx().getLogger().log(Level.WARNING, "CommandLnDeposit " + e.getMessage(),e);
                                    state.setPaymentError("Error", System.currentTimeMillis());
                                    player.sendMessage("Deposit failed.");                                   
                                }

                                return null;
                            },
                            (e) -> {
                                LnVault.getCtx().getLogger().log(Level.WARNING, "CommandLnDeposit " + e.getMessage(),e);
                                state.setPaymentError("Error", System.currentTimeMillis());
                                player.sendMessage("Deposit failed.");                                   
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
                        LnVault.getCtx().getLogger().log(Level.WARNING, "CommandLnDeposit " + ex.getMessage(),ex);
                        state.setPaymentError("Error", System.currentTimeMillis());
                        player.sendMessage("Deposit failed.");                                   
                    }                    

                    return null;                
                },
                (e) -> {
                    LnVault.getCtx().getLogger().log(Level.WARNING, "CommandLnDeposit " + e.getMessage(),e);
                    state.setPaymentError("Error", System.currentTimeMillis());
                    player.sendMessage("Deposit failed.");                                   
                    return null;
                }
            );
            
            return true;
        }
        catch( Exception e)
        {
            LnVault.getCtx().getLogger().log(Level.WARNING, "CommandLnDeposit " + e.getMessage(),e);
            state.setPaymentError("Error", System.currentTimeMillis());
            player.sendMessage("Deposit failed.");
            return true;
        }
    }
}