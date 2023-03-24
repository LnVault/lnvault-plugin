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
import com.lnvault.repository.Repository;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class CommandLnConfig implements CommandExecutor,TabCompleter {
    
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
                    var res = LnVault.getCtx().getRepo().getConfig(args[1]);
                    player.chat(args[1] + "=" + (res != null ? res : ""));
                    return true;
                }
                case "set" -> {                    
                    if (args.length < 3) return false;
                    StringBuilder str = new StringBuilder(args[2]);
                    for(int i = 3 ; i < args.length ; i++) {
                        str.append(" ");
                        str.append(args[i]);                              
                    }
                    LnVault.getCtx().getRepo().setConfig(args[1],str.toString());
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
        catch( Exception e)
        {
            LnVault.getCtx().getLogger().log(Level.WARNING,e.getMessage(),e);
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender cs, Command cmnd, String string, String[] params) {
        switch(params.length)
        {
            case 1:
                return Arrays.asList("get","set");
            case 2:
                return CONFIG_KEYS;
            default:
                return null;
        }
    }
}
