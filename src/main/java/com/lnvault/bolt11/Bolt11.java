/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.lnvault.bolt11;

import com.lnvault.LnVault;

public class Bolt11 {
    
    public static String ExtractDescription(String invoice) throws Exception{
        
        var decoded = Bech32.getInstance().bech32Decode(invoice);

        //Remove Signature
        byte[] data = new byte[decoded.Data.length - 104];
        System.arraycopy(decoded.Data, 0, data, 0, data.length);
        
        var bis = new Bolt11InputStream(data);
        
        for(;;){
            var taggedData = bis.read();
            if( taggedData == null ) break;
            
            if( taggedData.Tag == 'd' ){
                return new String(taggedData.Data);
            }
        }
        
        return "";
    }
    
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }    
    
    public static String ExtractPaymentHash(String invoice) throws Exception{
        
        var decoded = Bech32.getInstance().bech32Decode(invoice);

        //Remove Signature
        byte[] data = new byte[decoded.Data.length - 104];
        System.arraycopy(decoded.Data, 0, data, 0, data.length);
        
        var bis = new Bolt11InputStream(data);
        
        for(;;){
            var taggedData = bis.read();
            if( taggedData == null ) break;
            
            if( taggedData.Tag == 'p' ){
                return bytesToHex(taggedData.Data);
            }
        }
        
        return "";
    }
    
    public static byte[] ExtractLnUrlPayHash(String invoice) throws Exception{
        
        var decoded = Bech32.getInstance().bech32Decode(invoice);

        //Remove Signature
        byte[] data = new byte[decoded.Data.length - 104];
        System.arraycopy(decoded.Data, 0, data, 0, data.length);
        
        var bis = new Bolt11InputStream(data);
        
        for(;;){
            var taggedData = bis.read();
            if( taggedData == null ) break;
            
            if( taggedData.Tag == 'h' ){
                return taggedData.Data;
            }
        }
        
        return null;
    }    
    
}
