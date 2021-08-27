/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.lnvault;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.Function;
import java.util.logging.Level;

public class WebService {
    
    public static void blockingCall(String url, String apiKey, String json, Function<String,?> then , Function<Exception,?> fail) {
        try
        {
            String charset = "UTF-8";
            URLConnection connection = new URL(url).openConnection();
            connection.setRequestProperty("Accept-Charset", charset);
            connection.setRequestProperty("Authorization", apiKey);
            if( json != null )
            {
                connection.setDoOutput(true); // Triggers POST.
                connection.setRequestProperty("Content-Type", "application/json;charset=" + charset);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(json.getBytes(charset));
                }
            }

            try (InputStream response = connection.getInputStream()) {
                var b = response.readAllBytes();
                var s = new String(b,charset);

                LnVault.getCtx().getMinecraftQueue().add(() -> {
                        then.apply(s);
                });
            }
        }
        catch( Exception e ){
            LnVault.getCtx().getMinecraftQueue().add(() -> {
                fail.apply(e);
            });
        }
    }
    
    public static void call(String url, String apiKey, String json , Function<String,?> then, Function<Exception,?> fail ) {
        
        LnVault.getCtx().getBackgroundQueue().submit( () -> {
            blockingCall(url,apiKey,json,then,fail);
        });
    }

}
