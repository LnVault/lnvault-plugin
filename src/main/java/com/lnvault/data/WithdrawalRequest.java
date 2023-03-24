package com.lnvault.data;

import java.util.UUID;

public class WithdrawalRequest {
    
    private String Request;
    private String Id;
    private String Description;
    private long satsAmount;
    private double localAmount;
    private long TimeStamp;
    private long ExpiresAt;
    private UUID PlayerUUID;    

    public String getRequest() {
        return Request;
    }

    public void setRequest(String Request) {
        this.Request = Request;
    }

    public String getId() {
        return Id;
    }

    public void setId(String Id) {
        this.Id = Id;
    }

    public String getDescription() {
        return Description;
    }

    public void setDescription(String Description) {
        this.Description = Description;
    }

    public long getSatsAmount() {
        return satsAmount;
    }

    public void setSatsAmount(long satsAmount) {
        this.satsAmount = satsAmount;
    }

    public double getLocalAmount() {
        return localAmount;
    }

    public void setLocalAmount(double localAmount) {
        this.localAmount = localAmount;
    }
    
    
    public long getTimeStamp() {
        return TimeStamp;
    }

    public void setTimeStamp(long TimeStamp) {
        this.TimeStamp = TimeStamp;
    }
    
    public long getExpiresAt() {
        return ExpiresAt;
    }

    public void setExpiresAt(long TimeStamp) {
        this.ExpiresAt = TimeStamp;
    }    

    public UUID getPlayerUUID() {
        return PlayerUUID;
    }

    public void setPlayerUUID(UUID PlayerUUID) {
        this.PlayerUUID = PlayerUUID;
    }
}
