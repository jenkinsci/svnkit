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

package org.tmatesoft.svn.core.internal.ws.fs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.tmatesoft.svn.core.diff.ISVNRAData;

/**
 * @author TMate Software Ltd.
 */
public class SVNRAFileData implements ISVNRAData {
    
    private RandomAccessFile myFile;
    private File myRawFile;
    private byte[] myBuffer;
    private boolean myIsReadonly;
    
    public SVNRAFileData(File file, boolean readonly) {        
        myRawFile = file;
        myIsReadonly = readonly;
    }

    public InputStream read(final long offset, final long length) throws IOException {
        FileChannel channel = getRAFile().getChannel();
        if (channel == null) {
            throw new IOException("svn: Error when applying delta: cannot get IO channel for '" + myRawFile + "'");
        }
        ByteBuffer buffer = ByteBuffer.allocate((int) length);
        int read = channel.read(buffer, offset);
        if (read == 0 && read != length) {
            throw new IOException("svn: Error when applying delta: expected to read '" + length + "' bytes, actually read '" + read + "' bytes");
        }
        buffer.flip();
        byte[] resultingArray = new byte[(int) length];
        buffer.get(resultingArray, 0, read);
        for(int i = read; i < length; i++) {
            resultingArray[i] = resultingArray[i - read];
        }
        return new ByteArrayInputStream(resultingArray);
    }
    
    public void append(InputStream source, long length) throws IOException {
        int lLength = (int) length;
        if (myBuffer == null || myBuffer.length < length) {
            myBuffer = new byte[lLength];
        }
        if (myFile == null) {
            getRAFile().seek(myFile.length());
        }
        int read;
        do {
            read = source.read(myBuffer, 0, lLength);
            myFile.write(myBuffer, 0, read);
            lLength -= read;
        } while(lLength > 0);
    }
    
    public void close() throws IOException {
        if (myFile == null) {
            return;
        }
        myFile.close();
        myFile = null;
    }

    public long length() {
        return myRawFile.length();
    }

    public long lastModified() {
        return myRawFile.lastModified(); 
    }
    
    private RandomAccessFile getRAFile() throws IOException {
        if (myFile == null) {
            if (!myRawFile.exists()) {
                myRawFile.getParentFile().mkdirs();
                myRawFile.createNewFile();
            }
            if (!myIsReadonly) {
                FSUtil.setReadonly(myRawFile, false);
            }
            myFile = new RandomAccessFile(myRawFile, myIsReadonly ? "r" : "rw");
        }
        return myFile;
    }
}
