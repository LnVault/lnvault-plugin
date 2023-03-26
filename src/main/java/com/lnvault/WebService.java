package com.lnvault;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.Function;
import java.util.logging.Level;
import java.net.SocketTimeoutException;

public class WebService {

    private static final int RETRIES = 3;  
    private static final int TIMEOUT_MILLIS = 1000;   
    
    public static void blockingCall(String url, String apiKey, String json, Function<String,?> then , Function<Exception,?> fail) {
        
        try {
            var retries = RETRIES;
            var timeout = TIMEOUT_MILLIS;

            var retriesStr = LnVault.getCtx().getRepo().getConfig("http.retries");
            if( retriesStr !=null && !retriesStr.isEmpty() ) {
                retries = Integer.parseInt(retriesStr);
            }

            var timeoutStr = LnVault.getCtx().getRepo().getConfig("http.timeoutmillis");
            if( timeoutStr !=null && !timeoutStr.isEmpty() ) {
                timeout = Integer.parseInt(timeoutStr);
                if( timeout <= 0 ) {
                    throw new Exception("invalid timeout");
                }
            }

            var retry = 0;
            while(true) {
                try {               
                    blockingCallImpl(url, apiKey, json, then, fail,timeout);
                    return;
                } catch( SocketTimeoutException timeoutException) {
                    if(retry>=retries) {
                        LnVault.getCtx().getMinecraftQueue().add(() -> {
                            fail.apply(timeoutException);
                        });                
                        return;                 
                    } else {
                        LnVault.getCtx().getLogger().log(Level.WARNING, "Retrying webservice call due to timeout : " + timeoutException.getMessage());
                        retry++;
                    }
                } catch( IOException e) {
                    LnVault.getCtx().getMinecraftQueue().add(() -> {
                        fail.apply(e);
                    });                
                    return;
                }
            }
        } catch( Exception e) {
            fail.apply(e);
        }
    }
    
    private static void blockingCallImpl(String url, String apiKey, String json, Function<String,?> then , Function<Exception,?> fail,int timeout) throws IOException {
        //if( timeout > 0) { try{ Thread.sleep(timeout); } catch(Exception e) {} } throw new SocketTimeoutException("SABOTAGE");
        
        String charset = "UTF-8";
        URLConnection connection = new URL(url).openConnection();
        connection.setRequestProperty("Accept-Charset", charset);
        if( apiKey != null ) {
            connection.setRequestProperty("Authorization", apiKey);
        }
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
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
    
    public static void call(String url, String apiKey, String json , Function<String,?> then, Function<Exception,?> fail ) {
        
        LnVault.getCtx().getBackgroundQueue().submit( () -> {
            blockingCall(url,apiKey,json,then,fail);
        });
    }

}
