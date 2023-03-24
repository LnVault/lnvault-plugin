/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.lnvault;

import com.lnvault.bolt11.Bech32;
import com.lnvault.bolt11.Bolt11;
import com.lnvault.data.PlayerState;
import com.lnvault.opennode.OpenNodeBackend;
import com.lnvault.repository.Repository;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.map.MapRenderer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class LnVault extends JavaPlugin implements Listener, Runnable {

    static
    {
        CommandLnConfig.CONFIG_KEYS.add("vault.exchangerate");
        CommandLnConfig.CONFIG_KEYS.add("vault.joingreeting");
        CommandLnConfig.CONFIG_KEYS.add("withdrawal.description");
        CommandLnConfig.CONFIG_KEYS.add("deposit.description");
    }
    
    
    private static MapView INVOICE_MAP_VIEW;
    
    private static Context ctx;
    private static final HashMap<UUID,PlayerState> stateMap = new HashMap();
    
    private static final long STATUS_DISPLAY_MILLIS = 15000;
    
    private void configureMap(MapView mapView) throws Exception
    {
        var renderers = mapView.getRenderers();
        if( renderers != null ){
            for(MapRenderer mr : renderers)
            {
                mapView.removeRenderer(mr);
            }
        }
        mapView.addRenderer(new InvoiceRenderer(ctx));
    }
    
    @Override
    public void onEnable() {
        try
        {
            this.getCommand("lndeposit").setExecutor(new CommandLnDeposit());
            this.getCommand("lnwithdraw").setExecutor(new CommandLnWithdraw());

            var lnconfigCmd = new CommandLnConfig();
            this.getCommand("lnconfig").setExecutor(lnconfigCmd);
            this.getCommand("lnconfig").setTabCompleter(lnconfigCmd);

            var economy = getEconomy();
            var repo = new Repository();
            
            var backend = new OpenNodeBackend();
            
            ctx = new Context(getLogger(),economy , repo, backend);

            backend.init(ctx);
            
            var mapIdString = repo.getConfig("internal.invoiceMapId");
            if( mapIdString == null || mapIdString == "")
            {
                INVOICE_MAP_VIEW = Bukkit.createMap(Bukkit.getWorlds().get(0));
                var id = INVOICE_MAP_VIEW.getId();
                repo.setConfig("internal.invoiceMapId", "" + id);
            } else {
                var id = Integer.parseInt(mapIdString);
                INVOICE_MAP_VIEW = Bukkit.getMap(id);                
            }
            
            configureMap(INVOICE_MAP_VIEW);
            
            getServer().getPluginManager().registerEvents(this, this);
            getServer().getScheduler().scheduleSyncRepeatingTask(this, this, 5l, 5l);
        }
        catch(Exception e)
        {
            getLogger().log(Level.WARNING, e.getMessage(), e);
        }

    }

    @Override
    public void onDisable() {
    }
    
    @Override
    public void run()
    {
        updatePlayerStates(ctx);
        
        for(;;) {
            var runnable = ctx.getMinecraftQueue().poll();
            if( runnable == null ) break;
            runnable.run();
        }
    }

    @EventHandler
    public void PlayerLoginEvent(PlayerLoginEvent event) {
        PlayerState playerState = getPlayerState(event.getPlayer(),ctx);
        playerState.playerLogin();
    }
    
    public static void putLnVaultMapInHand(Player player) throws Exception {
        
        var inventory = player.getInventory();

        ItemStack invoice = new ItemStack(Material.FILLED_MAP);
        var meta = (MapMeta)invoice.getItemMeta();               
        meta.setMapView(INVOICE_MAP_VIEW);
        meta.setDisplayName("LnVault");
        meta.setColor(Color.fromRGB(109,54,153));
        invoice.setItemMeta(meta);
        
        var invoiceSlot = inventory.first(invoice);

        if( invoiceSlot == -1 ) {
            inventory.addItem(invoice);            
        }      
        
        invoiceSlot = inventory.first(invoice);
        
        if( invoiceSlot == -1 ) {
            throw new Exception("LnVault card not found");
        }      

        var heldSlot = inventory.getHeldItemSlot();
        
        if(heldSlot != invoiceSlot){
            var heldItem = inventory.getItem(heldSlot);
            inventory.setItem(invoiceSlot, heldItem);
            inventory.setItem(heldSlot, invoice);
        }       
    } 
    
    private Economy getEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return null;
        }
        var econ = rsp.getProvider();
        return econ;
    }

    private static void updatePlayerStates(Context ctx)
    {
        boolean foundPendingPayment = false;
        long maxPendingPaymentTimeStamp = 0;
        
        boolean foundPendingWithdrawal = false;
        var now = System.currentTimeMillis();
        
        for(PlayerState playerState : stateMap.values()){
            
            if (playerState.getPaymentRequest() != null) {
                var payReq = playerState.getPaymentRequest();
                var isPaid = CommandLnDeposit.isPaid(payReq);
                if( isPaid ) {
                    try
                    {
                        LnVault.ctx.getRepo().auditPaymentRequest(payReq, true);
                        payReq.setPaidTimeStamp(now);

                        var player = Bukkit.getPlayer(playerState.getPlayerId());
                        var response = ctx.getEconomy().depositPlayer(player, payReq.getLocalAmount());
                        if( response.type == EconomyResponse.ResponseType.SUCCESS )                        
                        {
                            playerState.setPaymentReceived(now);
                        } else {
                            playerState.setPaymentError("Error", now);
                        }
                    }
                    catch(Exception e)
                    {
                        playerState.setPaymentError("Error", now);
                    }
                } else {
                    if(payReq.getExpiresAt() != 0 && now > (payReq.getExpiresAt() + (2 * 60 * 1000)) ) { //2 minute grace period after expriy incase payment is underway.
                        playerState.setPaymentError("Expired",now);
                    } else {
                        foundPendingPayment = true;
                        maxPendingPaymentTimeStamp = Math.max(maxPendingPaymentTimeStamp,payReq.getCreatedTimeStamp());
                    }                        
                }
            }

            if (playerState.getWithdrawalRequest() != null) {
                
                var wdReq = playerState.getWithdrawalRequest();

                var isWithdrawn = CommandLnWithdraw.isWithdrawn(wdReq);

                if( isWithdrawn ) {
                    try
                    {
                        wdReq.setTimeStamp(now);
                        LnVault.ctx.getRepo().auditWithdrawalRequest(wdReq, true);
                        playerState.setWithdrawalSent(wdReq.getTimeStamp());
                    }
                    catch(Exception e)
                    {
                        playerState.setWithdrawalError("Error", now);
                    }
                    
                } else {
                    if(wdReq.getExpiresAt() != 0 && now > wdReq.getExpiresAt() ) {
                        wdReq.setTimeStamp(now);
                        playerState.setWithdrawalError("Expired",now);
                    } else {
                        foundPendingWithdrawal = true;
                    }
                }
            }
            
            ctx.setPaymentRequestActive(foundPendingPayment,maxPendingPaymentTimeStamp);
            ctx.setWithdrawalActive(foundPendingWithdrawal);

            if( playerState.getPaidTime().isPresent() ) {
                if( (System.currentTimeMillis() - playerState.getPaidTime().get() ) > STATUS_DISPLAY_MILLIS )
                {
                    playerState.clearPaidTime();
                }
            }

            if( playerState.getWithdrawnTime().isPresent() ) {
                if( (System.currentTimeMillis() - playerState.getWithdrawnTime().get() ) > STATUS_DISPLAY_MILLIS )
                {
                    playerState.clearWithdrawnTime();
                }
            }
            
            if( playerState.getErrorTime().isPresent() ) {
                if( (System.currentTimeMillis() - playerState.getErrorTime().get() ) > STATUS_DISPLAY_MILLIS )
                {
                    playerState.clearError();
                }
            }            
        }
    }
    
    public static PlayerState getPlayerState(Player player,Context ctx)
    { 
        PlayerState playerState = stateMap.get(player.getUniqueId());
        if(playerState == null)
        {
            playerState = new PlayerState(player.getUniqueId());
            stateMap.put(player.getUniqueId(), playerState);
        }
        
        return playerState;        
    }    

    public static Context getCtx() {
        return ctx;
    }
    
    private static double getBtcExchangeRate() throws Exception
    {
        var str = ctx.getRepo().getConfig("vault.exchangerate");
        if( str == null || "".equals(str.trim()) )
        {
            return 1000000; //1 in game cent == 1 sat
        }
        
        return Double.parseDouble(str);
    }
    
    public static long convertLocalToSats(double local) throws Exception {
        var rate = getBtcExchangeRate();
        var btc = local / rate;
        var sats = (long)Math.ceil(btc * 100000000);
        return sats;
    }
    
    @EventHandler
    public void PlayerJoinEvent(PlayerJoinEvent event) {
        try
        {
            var greeting = ctx.getRepo().getConfig("vault.joingreeting");
            if( greeting != null && !"".equals(greeting)) {
                event.getPlayer().sendMessage(greeting);
            }
        }
        catch(Exception e)
        {
            //Ignore
        }
    }            
}
