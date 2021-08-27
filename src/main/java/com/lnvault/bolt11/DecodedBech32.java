package com.lnvault.bolt11;

public class DecodedBech32 {
    public String HRP;
    public byte[] Data;
    
    public DecodedBech32(byte[] a, byte[] b)
    {
        this.HRP= new String(a);
        this.Data=b;
    }
}
