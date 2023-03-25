package com.lnvault.opennode;

import com.lnvault.Context;
import com.lnvault.LnVault;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class OpenNodeWebHookHandler {
    
    static void init(Context ctx,OpenNodeBackend backend) throws Exception {
        
        var callbackPort = ctx.getRepo().getConfig("opennode.callback.port");
        if( callbackPort == null || callbackPort.isEmpty() ) {
            return;
        }
        
        var portNum = Integer.parseInt(callbackPort);
        ctx.getLogger().log(Level.INFO, "lnvault starting webhook server on port " + portNum);
        
        var ss = new ServerSocket(portNum);     
        new Thread(() -> {
            while(true) {               
                try {              
                    try (java.net.Socket client = ss.accept()) {
                        client.setSoTimeout(1000); //Avoid blocking for more than 1 second
                        try {
                            var r = new BufferedReader(new InputStreamReader(client.getInputStream()));

                            var req = r.readLine();
                            if(req == null) {
                                ctx.getLogger().log(Level.WARNING, "lnvault opennode webhook empty request");
                                continue;
                            }
                            //ctx.getLogger().log(Level.INFO, "REQ:"+req);

                            var reqSplit = req.split(" ");
                            if( reqSplit.length!= 3 ) {
                                ctx.getLogger().log(Level.WARNING, "lnvault opennode webhook malformed request " + req);
                                continue;
                            }

                            if( !reqSplit[1].startsWith("/withdraw/")  && !reqSplit[1].startsWith("/deposit/") ) {
                                ctx.getLogger().log(Level.WARNING, "lnvault opennode webhook unknown url " + reqSplit[1]);
                                continue;                           
                            }

                            var contentLength = 0;
                            var contentType = "";

                            String reqHeader;
                            do {
                                reqHeader = r.readLine(); 

                                if(reqHeader.startsWith("Content-Length:")){
                                    contentLength = Integer.parseInt(reqHeader, 16,reqHeader.length(),10);
                                }

                                if(reqHeader.startsWith("Content-Type:")){
                                    contentType = reqHeader.substring(14,reqHeader.length());
                                }
                                //ctx.getLogger().log(Level.INFO, "HEADER:"+reqHeader);
                            } while(reqHeader != null && reqHeader.length()!=0);

                            if( !contentType.equals("application/x-www-form-urlencoded") ) {
                                ctx.getLogger().log(Level.WARNING, "lnvault opennode webhook unknown content-type" + contentType);
                                continue;
                            }

                            //ctx.getLogger().log(Level.INFO, "contentLength:" + contentLength);

                            var bodyChars = new char[contentLength];
                            var readChars = 0;
                            while(readChars < contentLength) {
                                var numChars = r.read(bodyChars,readChars,contentLength-readChars);
                                if( numChars == -1 ) {
                                    break;
                                }
                                readChars += numChars;                      
                            }

                            var bodyStr = new String(bodyChars,0,readChars);
                            //ctx.getLogger().log(Level.INFO, "BODY:"+bodyStr);                           

                            ctx.getMinecraftQueue().add(()->{                              
                                handleRequest(backend,reqSplit,bodyStr);
                            });

                        } finally {
                            try {
                                var w = new DataOutputStream(client.getOutputStream());
                                w.writeBytes("HTTP/1.1 200 OK\r\n\r\n");
                                w.flush();                        
                            } catch(Exception e) {
                                //Ignore
                            }
                        }
                    }                    
                } catch (Exception ex) {
                    ctx.getLogger().log(Level.WARNING, "lnvault opennode webhook accept", ex);
                }
            }                        
        }, "LnVault-OpenNode-WebHook").start();
    }
    
    private static HashMap<String,String> parseFormData(String formData) {
        var map = new HashMap<String, String>();
        
        for( var pair : formData.split("&") ) {
            var keyValue = pair.split("=",2);
            if(keyValue.length == 2 ) {
                map.put(keyValue[0],keyValue[1]);
            }
        }
        
        return map;
    }
    
    private static void handleRequest(OpenNodeBackend backend,String[] reqSplit,String body) {

        if( reqSplit[1].startsWith("/withdraw/") ) {           
            if( body.contains("status=confirmed")) {
                var withdrawalId = reqSplit[1].substring(10);
                LnVault.getCtx().getLogger().log(Level.INFO, "hook withdrawalid:" + withdrawalId);                       
                backend.confirmWithdrawal(withdrawalId);         
            }
        } else if( reqSplit[1].startsWith("/deposit/") ) {
            var props = parseFormData(body);
            var paymentId = props.get("id");
            var status = props.get("status");
            if( paymentId != null && "paid".equals(status) ) {
                LnVault.getCtx().getLogger().log(Level.INFO, "hook paymentId:" + paymentId);                       
                backend.confirmPayment(paymentId);          
            }
        }
    }   
}

