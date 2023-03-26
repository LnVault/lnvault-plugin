/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.lnvault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class CommandLnUserConfig implements CommandExecutor,TabCompleter {
    
    public static final ArrayList<String> CONFIG_KEYS = new ArrayList<String>();
    
    @Override
    public boolean onCommand(CommandSender cs, Command cmnd, String string, String[] args) {

        if (!(cs instanceof Player)) return false;
        if (args.length < 2) return false;

        var player = (Player)cs;
        
        try
        {
            switch(args[0]) {
                case "get" -> {
                    var res = LnVault.getCtx().getRepo().getUserConfig(player.getUniqueId().toString(), args[1]);
                    player.sendMessage(args[1] + "=" + (res != null ? res : ""));
                    return true;
                }
                case "set" -> {                    
                    if (args.length < 3) return false;
                    StringBuilder str = new StringBuilder(args[2]);
                    for(int i = 3 ; i < args.length ; i++) {
                        str.append(" ");
                        str.append(args[i]);                              
                    }
                    LnVault.getCtx().getRepo().setUserConfig(player.getUniqueId().toString(),args[1],str.toString());
                    return true;
                }
                case "clear" -> {                    
                    if (args.length < 2) return false;                   
                    LnVault.getCtx().getRepo().setUserConfig(player.getUniqueId().toString(),args[1],"");
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
        catch( Exception e)
        {
            LnVault.getCtx().getLogger().log(Level.WARNING, "CommandLnUserConfig " + e.getMessage(),e);
            player.sendMessage("User config failed."); 
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender cs, Command cmnd, String string, String[] params) {
        switch(params.length)
        {
            case 1:
                return Arrays.asList("get","set","clear");
            case 2:
                return CONFIG_KEYS;
            default:
                return null;
        }
    }
}
