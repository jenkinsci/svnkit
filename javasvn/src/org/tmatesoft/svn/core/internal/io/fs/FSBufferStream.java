/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.IOException;
import java.io.OutputStream;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSBufferStream extends OutputStream {
    protected byte[] myBuffer;
    
    protected int myBufferLength;

    public FSBufferStream(){
        super();
        myBufferLength = 0;
    }
    
    public void write(int b) throws IOException{
        byte[] result = new byte[myBufferLength + 1];
        if(myBufferLength > 0){
            System.arraycopy(myBuffer, 0, result, 0, myBufferLength);
        }
        result[myBufferLength] = (byte)b;
        myBuffer = result;
        myBufferLength++;
    }
    
    public void write(byte[] b) throws IOException{
        if(b == null){
            return;
        }
        byte[] result = new byte[myBufferLength + b.length];
        if(myBufferLength > 0){
            System.arraycopy(myBuffer, 0, result, 0, myBufferLength);
        }
        System.arraycopy(b, 0, result, myBufferLength, b.length);
        myBuffer = result;
        myBufferLength += b.length;
    }
    
    public void write(byte[] b, int off, int len) throws IOException{
        if(b == null){
            return;
        }
        byte[] result = new byte[myBufferLength + len];
        if(myBufferLength > 0){
            System.arraycopy(myBuffer, 0, result, 0, myBufferLength);
        }
        System.arraycopy(b, off, result, myBufferLength, len);
        myBuffer = result;
        myBufferLength += len;
    }
    
    public void close() throws IOException {
        super.close();
    }
}
