/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNInputFileChannel implements ISVNInputFile {
    
    FileChannel myChannel;
    FileInputStream myFileStream;
    
    public SVNInputFileChannel(File file) throws FileNotFoundException {
        myFileStream = new FileInputStream(file); 
        myChannel = myFileStream.getChannel();
    }
    
    public void seek(long pos) throws IOException {
        myChannel.position(pos);
    }

    public long getFilePointer() throws IOException {
        return myChannel.position();
    }

    public int read() throws IOException {
        //return myFileStream.read();
        byte[] buf = new byte[1];
        int r = myChannel.read(ByteBuffer.wrap(buf));
        return r < 0 ? -1 : (int)(buf[0] & 0xFF);
    }

    public int read(byte[] b) throws IOException {
        //return myFileStream.read(b);
        return myChannel.read(ByteBuffer.wrap(b));
    }

    public int read(byte[] b, int off, int len) throws IOException {
        //return myFileStream.read(b, off, len);
        return myChannel.read(ByteBuffer.wrap(b, off, len));
    }
    
    public long length() throws IOException {
        return myChannel.size();
    }


    public void close() throws IOException {
        if(myChannel != null){
            myChannel.close(); 
        }
        if(myFileStream != null){
            myFileStream.close(); 
        }
    }

}
