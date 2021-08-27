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
    
}
