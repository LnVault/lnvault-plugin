/*
 * Copyright 2021 LnVault.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lnvault.repository;

import com.lnvault.LnVault;
import com.lnvault.data.PaymentRequest;
import com.lnvault.data.WithdrawalRequest;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

public class Repository {

    
    private Object crudLock = new Object();
    private Connection crudConn;

    private Object reportLock = new Object();
    private Connection reportConn;

    private boolean tableExists(String tableName) throws Exception {
        try ( var res = this.crudConn.createStatement().executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='"+tableName+"';") )
        {
            return res.next();
        }
    }
    
    private void createSettingsTable() throws Exception
    {
        if( !tableExists("CONFIG") ) {
            this.crudConn.createStatement().execute("CREATE TABLE CONFIG (NAME TEXT PRIMARY KEY,VALUE TEXT);");
        }
    }
    
    private void createTxAuditTable() throws Exception
    {
        if( !tableExists("TXAUDIT") ) {
            this.crudConn.createStatement().execute("CREATE TABLE TXAUDIT (ID TEXT PRIMARY KEY,TIMESTAMP INTEGER,PLAYER TEXT,TYPE INTEGER,SATS INTEGER,LOCALAMOUNT REAL,STATUS INTEGER,REFERENCE TEXT);");
        }
    }
    
    private void createUserSettingsTable() throws Exception
    {
        if( !tableExists("USERCONFIG") ) {
            this.crudConn.createStatement().execute("CREATE TABLE USERCONFIG (PLAYER TEXT,NAME TEXT,VALUE TEXT, PRIMARY KEY(PLAYER,NAME));");
        }
    }    
    
    
    public Repository() throws Exception
    {
        String url = "jdbc:sqlite:lnvault.db";
        // create a connection to the database
        this.crudConn = DriverManager.getConnection(url);
        this.reportConn = DriverManager.getConnection(url);

        createSettingsTable();
        createTxAuditTable();
        createUserSettingsTable();
    }
    
    public String getConfig(String name) throws Exception {
        synchronized(crudLock)
        {
            try( var stmt = crudConn.prepareStatement("SELECT VALUE FROM CONFIG WHERE NAME=?;") ) {
                stmt.setString(1, name);
                try ( var res = stmt.executeQuery() ) {
                    if( !res.next() ) return null;
                    return res.getString(1);
                }
            }
        }
    }
    
    public void setConfig(String name , String value) throws Exception {
        synchronized(crudLock)
        {
            try( var stmt = crudConn.prepareStatement("INSERT INTO CONFIG(NAME,VALUE) VALUES(?,?) ON CONFLICT(NAME) DO UPDATE SET VALUE=excluded.VALUE;") ) {
                stmt.setString(1, name);
                stmt.setString(2, value);
                stmt.execute();
            }
        }
    }
    
    public String getUserConfig(String playerUUID,String name) throws Exception {
        synchronized(crudLock)
        {
            try( var stmt = crudConn.prepareStatement("SELECT VALUE FROM USERCONFIG WHERE PLAYER=? AND NAME=?;") ) {
                stmt.setString(1, playerUUID);
                stmt.setString(2, name);
                try ( var res = stmt.executeQuery() ) {
                    if( !res.next() ) return null;
                    return res.getString(1);
                }
            }
        }
    }
    
    public void setUserConfig(String playerUUID,String name , String value) throws Exception {
        synchronized(crudLock)
        {
            try( var stmt = crudConn.prepareStatement("INSERT INTO USERCONFIG(PLAYER,NAME,VALUE) VALUES(?,?,?) ON CONFLICT(PLAYER,NAME) DO UPDATE SET VALUE=excluded.VALUE;") ) {
                stmt.setString(1, playerUUID);
                stmt.setString(2, name);
                stmt.setString(3, value);
                stmt.execute();
            }
        }
    }    
    
    public void auditPaymentRequest(PaymentRequest pReq,boolean paid) throws Exception {
        synchronized(crudLock)
        {
            try( var stmt = crudConn.prepareStatement("INSERT INTO TXAUDIT(ID,TIMESTAMP,PLAYER,TYPE,SATS,LOCALAMOUNT,STATUS,REFERENCE) VALUES(?,?,?,1,?,?,?,?) ON CONFLICT(ID) DO UPDATE SET STATUS=excluded.STATUS,TIMESTAMP=excluded.TIMESTAMP;") ) {
                stmt.setString(1, pReq.getId());
                stmt.setLong(2, pReq.getPaidTimeStamp() == 0 ? pReq.getCreatedTimeStamp() : pReq.getPaidTimeStamp() );            
                stmt.setString(3, pReq.getPlayerUUID().toString());
                stmt.setLong(4, pReq.getSatsAmount());
                stmt.setDouble(5, pReq.getLocalAmount());
                stmt.setInt(6, paid ? 2 : 1);
                stmt.setString(7, pReq.getRequest());
                stmt.execute();
            }
        }
    }

    public void auditWithdrawalRequest(WithdrawalRequest wdReq,boolean withdrawn) throws Exception {
        synchronized(crudLock)
        {            
            try( var stmt = crudConn.prepareStatement("INSERT INTO TXAUDIT(ID,TIMESTAMP,PLAYER,TYPE,SATS,LOCALAMOUNT,STATUS,REFERENCE) VALUES(?,?,?,2,?,?,?,?) ON CONFLICT(ID) DO UPDATE SET STATUS=excluded.STATUS,TIMESTAMP=excluded.TIMESTAMP;") ) {
                stmt.setString(1, wdReq.getId());
                stmt.setLong(2, wdReq.getTimeStamp());            
                stmt.setString(3, wdReq.getPlayerUUID().toString());
                stmt.setLong(4, wdReq.getSatsAmount());
                stmt.setDouble(5, wdReq.getLocalAmount());
                stmt.setInt(6, withdrawn ? 2 : 1);
                stmt.setString(7, wdReq.getRequest());
                stmt.execute();
            }
        }
    }
        
    private void getTotalTxsSince(UUID playerUUID,long timeStamp,long type, Function<Long,?> result, Function<Exception,?> fail) {
        
       LnVault.getCtx().getBackgroundQueue().execute(() -> {
            synchronized(reportLock)
            {                        
                try( var stmt = reportConn.prepareStatement("SELECT SUM(SATS) FROM TXAUDIT WHERE PLAYER=? AND TIMESTAMP>=? AND TYPE=? AND STATUS=2;") ) {
                    stmt.setString(1, playerUUID.toString());
                    stmt.setLong(2, timeStamp);            
                    stmt.setLong(3, type);            
                    var res = stmt.executeQuery();

                    long total = 0;
                    if( res.next() ) {
                        total = res.getLong(1);
                    }

                    final long fTotal = total;                    
                    LnVault.getCtx().getMinecraftQueue().add(() -> {
                        result.apply(fTotal);
                    });
                    
                } catch( Exception e) {
                    LnVault.getCtx().getMinecraftQueue().add(() -> {
                        fail.apply(e);
                    });
                }
            }
       });
    }
    
    public void getTotalDepositsSince(UUID playerUUID,long timeStamp, Function<Long,?> result, Function<Exception,?> fail) {
        getTotalTxsSince(playerUUID,timeStamp,1,result,fail);
    }
    
    public void getTotalWithdrawalsSince(UUID playerUUID,long timeStamp, Function<Long,?> result, Function<Exception,?> fail) {
        getTotalTxsSince(playerUUID,timeStamp,2,result,fail);
    }      

}
