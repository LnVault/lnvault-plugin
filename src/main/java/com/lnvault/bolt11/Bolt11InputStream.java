/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.lnvault.bolt11;

import com.lnvault.LnVault;
import java.util.BitSet;

public class Bolt11InputStream {
    private int pos;
    private byte[] data;
    
    public Bolt11InputStream(byte[] data) {
        this.pos = 7; //Skip Timestamp
        this.data=data;
    }

    private int calcBitPos(int bitCounter){
        var currentByte = bitCounter/8;
        var byteOffset = bitCounter%8;
        
        return (currentByte * 8) + 7 - byteOffset;
    }
    
    public Bolt11TaggedData read() {
        if( pos >= data.length ) return null;
        
        var tagCode = ((int)data[pos++]) & 0xFF;
        var tag = Bech32.CHARSET.charAt( tagCode );
        var lengthHigh = ((int)data[pos++]) & 0xFF;
        var lengthLow  = ((int)data[pos++]) & 0xFF;
        var length = lengthHigh << 5 | lengthLow;
        
        var bitset = new BitSet();
        int bitCounter = 0;
        
        for(int dataPos = 0 ; dataPos < length ; dataPos++) {
            var fiveBits = data[pos++];
            int bitPos;
            boolean bitValue;
 
            bitPos = calcBitPos(bitCounter);
            bitValue = (fiveBits & 0b00010000) != 0 ;
            bitset.set(bitPos, bitValue );            
            bitCounter++;

            bitPos = calcBitPos(bitCounter);
            bitValue = (fiveBits & 0b00001000) != 0 ;
            bitset.set(bitPos, bitValue );            
            bitCounter++;

            bitPos = calcBitPos(bitCounter);
            bitValue = (fiveBits & 0b00000100) != 0 ;
            bitset.set(bitPos, bitValue );            
            bitCounter++;

            bitPos = calcBitPos(bitCounter);
            bitValue = (fiveBits & 0b00000010) != 0 ;
            bitset.set(bitPos, bitValue );            
            bitCounter++;

            bitPos = calcBitPos(bitCounter);
            bitValue = (fiveBits & 0b00000001) != 0 ;
            bitset.set(bitPos, bitValue );            
            bitCounter++;
        }
        
        var unpackedData = bitset.toByteArray();
        
        var taggedData = new Bolt11TaggedData();
        taggedData.Tag = tag;
        taggedData.Data = unpackedData;     
        
        return taggedData;
    }    
}
