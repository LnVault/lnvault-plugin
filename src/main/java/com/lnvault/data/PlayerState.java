package com.lnvault.data;

import com.lnvault.CommandLnDeposit;
import com.lnvault.Context;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PlayerState {
    
    private final UUID playerId;
    private boolean mapUpToDate;
    
    private PaymentRequest currentPayment;
    private Optional<Long> paidTime;
    
    private WithdrawalRequest currentWithdrawal;
    private Optional<Long> withdrawnTime;
    
    private String errorMessage;
    private Optional<Long> errorTime;
    
    public PlayerState(UUID playerId)
    {
        this.playerId = playerId;
        paidTime = Optional.empty();
        withdrawnTime = Optional.empty();
        errorTime = Optional.empty();
    }
    
    public UUID getPlayerId() {
        return playerId;
    }
    
    public boolean getMapUpToDate() {
        return mapUpToDate;
    }

    public void playerLogin() {
        mapUpToDate = false;
    }
    
    public void setMapUpToDate() {
        mapUpToDate = true;
    }
    
    public void setPaymentRequest(PaymentRequest req)
    {
        paidTime = Optional.empty();
        currentPayment = req;
        mapUpToDate = false;
    }
    
    public PaymentRequest getPaymentRequest() {
        return currentPayment;
    }

    public void setPaymentReceived(long timeStamp)
    {
        paidTime = Optional.of(timeStamp);
        currentPayment = null;
        mapUpToDate = false;
    }

    public Optional<Long> getPaidTime()
    {
        return paidTime;
    } 
    
    public void clearPaidTime()
    {
        paidTime = Optional.empty();
        mapUpToDate = false;
    }

    public void setWithdrawalRequest(WithdrawalRequest req)
    {
        withdrawnTime = Optional.empty();
        currentWithdrawal = req;       
        mapUpToDate = false;
    }

    public WithdrawalRequest getWithdrawalRequest() {
        return currentWithdrawal;
    }

    public void setWithdrawalSent(long timeStamp)
    {
        withdrawnTime = Optional.of(timeStamp);
        currentWithdrawal = null;
        mapUpToDate = false;
    }

    public Optional<Long> getWithdrawnTime()
    {
        return withdrawnTime;
    }
    
    public void clearWithdrawnTime()
    {
        withdrawnTime = Optional.empty();
        mapUpToDate = false;
    }
    
    public void setPaymentError(String errorMessage,long timeStamp)
    {
        this.currentPayment = null;
        this.errorMessage = errorMessage;
        this.errorTime = Optional.of(timeStamp);
        mapUpToDate = false;
    }

    public void setWithdrawalError(String errorMessage,long timeStamp)
    {
        this.currentWithdrawal = null;
        this.errorMessage = errorMessage;
        this.errorTime = Optional.of(timeStamp);
        mapUpToDate = false;
    }
    
    public void setInternalError(String errorMessage,long timeStamp)
    {
        this.errorMessage = errorMessage;
        this.errorTime = Optional.of(timeStamp);
        mapUpToDate = false;
    }
    
    public Optional<Long> getErrorTime() {
        return errorTime;
    }
    
    public String getError() {
        return errorMessage;
    }

    public void clearError()
    {
        errorMessage = null;
        errorTime = Optional.empty();
        mapUpToDate = false;
    }
}
