/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.lnvault;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;

public class QRCode {
 
    public static BufferedImage getQRCode(String paymentRequest) throws Exception
    {
        com.google.zxing.Writer writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(paymentRequest, com.google.zxing.BarcodeFormat.QR_CODE, 57, 57);

        //generate an image from the byte matrix
        int width = matrix.getWidth(); 
        int height = matrix.getHeight(); 

        //create buffered image to draw to
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) { 
         for (int x = 0; x < width; x++) {        
          image.setRGB(x, y, (matrix.get(x, y) ? 0 : 0xFFFFFF ));
         }
        }

        return image;
    }    
}
