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

import org.tmatesoft.svn.core.diff.ISVNRAData;

/**
 * @author TMate Software Ltd.
 */
public class SVNRAFileData implements ISVNRAData {
    
    private final class SelfStream extends InputStream {
        private long myOffset;
        private int myLength;

        private SelfStream(long length, long offset) {
            myOffset = offset;
            myLength = (int) length;
        }
        
        public void reset(long length, long offset) {
            myOffset = offset;
            myLength = (int) length;            
        }

        public int read() throws IOException {
            byte[] buffer = new byte[] {-1};
            read(buffer, 0, 1);
            return buffer[0];
        }

        public int read(byte[] buffer, int userOffset, int userLength) throws IOException {
            if (myLength <= 0) {
                return -1;
            }
            int available = (int) (getRAFile().length() - myOffset);
            int toRead = Math.min(available, myLength);
            toRead = Math.min(userLength, toRead);
            myLength -= toRead;
            
            long pos = getRAFile().length();
            myFile.seek(myOffset);
            int result = myFile.read(buffer, userOffset, toRead);
            myFile.seek(pos);
            myOffset += toRead;
            return result;
        }
    }

    private RandomAccessFile myFile;
    private File myRawFile;
    private byte[] myBuffer;
    private SelfStream mySelfStream;
    
    public SVNRAFileData(File file) {        
        myRawFile = file;
    }

    public InputStream read(final long offset, final long length) throws IOException {
        if (mySelfStream != null) {
            mySelfStream.reset(length, offset);
        } else {
            mySelfStream = new SelfStream(length, offset);
        }
        return mySelfStream;
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
            FSUtil.setReadonly(myRawFile, false);
            myFile = new RandomAccessFile(myRawFile, "rw");
        }
        return myFile;
    }
}
