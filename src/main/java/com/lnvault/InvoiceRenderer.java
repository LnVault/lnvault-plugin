/*
 * Copyright 2018 blitzpay
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
package com.lnvault;

import com.lnvault.data.PlayerState;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.*;

public class InvoiceRenderer extends MapRenderer {

    private final Context ctx;
    
    private BufferedImage PAYMENT_RECEIVED;
    private BufferedImage SEND_PAYMENT;            
    private BufferedImage ERROR;
    private BufferedImage IDLE;     
    
    public InvoiceRenderer(Context ctx) throws Exception {
        super(true);
        this.ctx = ctx;

        PAYMENT_RECEIVED = ImageIO.read(getClass().getResource("/paymentreceived.png"));
        SEND_PAYMENT = ImageIO.read(getClass().getResource("/sendpayment.png"));
        ERROR = ImageIO.read(getClass().getResource("/error.png"));
        IDLE = ImageIO.read(getClass().getResource("/lnvault.png"));
    }
    
    private static BufferedImage deepCopy(BufferedImage bi) {
        var cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        var raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
   }
    
    private void drawCenteredString(Graphics g, String text , int y) {
        var metrics = g.getFontMetrics(g.getFont());
        int x = (128 - metrics.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }
    
    @Override
    public void render(MapView mv, MapCanvas mc, Player player) {      
       
        PlayerState playerState = LnVault.getPlayerState(player,ctx);
        try
        {
            if(!playerState.getMapUpToDate())
            {
                if(playerState.getError() != null)
                {
                    var img = deepCopy(ERROR);
                    var g = (Graphics2D)img.getGraphics();
                    
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    
                    g.setFont(g.getFont().deriveFont(18f));
                    
                    drawCenteredString(g,playerState.getError(),24);
                    
                    mc.drawImage(0, 0, img);                                         
                }
                else if(playerState.getPaidTime().isPresent() )
                {                   
                    var img = deepCopy(PAYMENT_RECEIVED);
                    var g = (Graphics2D)img.getGraphics();
                    
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    
                    g.setFont(g.getFont().deriveFont(18f));
                    
                    drawCenteredString(g,"Payment",24);
                    drawCenteredString(g,"Received",48);
                    
                    mc.drawImage(0, 0, img); 
                }
                else if(playerState.getWithdrawnTime().isPresent() )
                {
                    var img = deepCopy(PAYMENT_RECEIVED);
                    var g = (Graphics2D)img.getGraphics();
                    
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    
                    g.setFont(g.getFont().deriveFont(18f));
                    
                    drawCenteredString(g,"Withdrawal", 24);
                    drawCenteredString(g,"Sent", 48);
                    
                    mc.drawImage(0, 0, img);                     
                }
                else if (playerState.getPaymentRequest()!= null)
                {
                    var img = deepCopy(SEND_PAYMENT);
                    var g = (Graphics2D)img.getGraphics();
                    
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    
                    g.setFont(g.getFont().deriveFont(18f));
                    
                    drawCenteredString(g,"Send", 24);
                    drawCenteredString(g,"Payment", 48);
                    
                    mc.drawImage(0, 0, img); 

                    BufferedImage invoiceQr = QRCode.getQRCode(playerState.getPaymentRequest().getRequest());
                    mc.drawImage((128 - invoiceQr.getWidth())/2, 61, invoiceQr);  
                    
                }
                else if (playerState.getWithdrawalRequest() != null)
                {
                    var img = deepCopy(SEND_PAYMENT);
                    var g = (Graphics2D)img.getGraphics();
                    
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    
                    g.setFont(g.getFont().deriveFont(18f));
                    
                    drawCenteredString(g,"Receive", 24);
                    drawCenteredString(g,"Withdrawal", 48);
                    
                    mc.drawImage(0, 0, img); 
                    
                    BufferedImage invoiceQr = QRCode.getQRCode(playerState.getWithdrawalRequest().getRequest());
                    
                    mc.drawImage((128 - invoiceQr.getWidth())/2, 64, invoiceQr);  
                    
                }                
                else
                {
                    mc.drawImage(0, 0, IDLE);
                }
                
                playerState.setMapUpToDate();
            }
        }
        catch(Exception e)
        {
            ctx.getLogger().log(Level.SEVERE,"error during invoice map render",e);
            playerState.setInternalError("Internal Error" , System.currentTimeMillis());
        }
    }
    
}
