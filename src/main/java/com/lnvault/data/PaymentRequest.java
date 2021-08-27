package com.lnvault.data;

import java.util.UUID;

public class PaymentRequest {
    private String Request;
    private String Id;
    private UUID PlayerUUID;
    private long satsAmount;
    private double localAmount;
    private long createdTimeStamp;
    private long expiresAt;
    private long paidTimeStamp;

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

    public UUID getPlayerUUID() {
        return PlayerUUID;
    }

    public void setPlayerUUID(UUID PlayerUUID) {
        this.PlayerUUID = PlayerUUID;
    }

    public long getSatsAmount() {
        return satsAmount;
    }

    public void setSatsAmount(long satsAmount) {
        this.satsAmount = satsAmount;
    }

    public void setLocalAmount(double localAmount) {
        this.localAmount = localAmount;
    }
    
    public double getLocalAmount() {
        return this.localAmount;
    }    
    
    public long getCreatedTimeStamp() {
        return createdTimeStamp;
    }

    public void setCreatedTimeStamp(long timeStamp) {
        this.createdTimeStamp = timeStamp;
    }
    
    public long getPaidTimeStamp() {
        return paidTimeStamp;
    }

    public void setPaidTimeStamp(long timeStamp) {
        this.paidTimeStamp = timeStamp;
    }    

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}