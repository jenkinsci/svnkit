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
package org.tmatesoft.svn.core.io.diff;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNRABufferData implements ISVNRAData {
    private byte[] myBuffer;
    private int myLength;
    
    public SVNRABufferData(byte[] buffer, int length){
        myBuffer = length > 0 ? buffer : new byte[0];
        myLength = length > 0 ? length : 0;
    }
    /**
     * @return
     * @throws SVNException
     */
    public InputStream readAll() throws SVNException {
        byte[] buf = new byte[myLength];
        System.arraycopy(myBuffer, 0, buf, 0, myLength);
        return new LocalInputStream(buf);
    }
    
    /**
     * @param offset
     * @param length
     * @return
     * @throws SVNException
     */
    public InputStream read(long offset, long length) throws SVNException {
        byte[] resultingArray = new byte[(int) length];
        int read = -1;
        if(offset < myLength){
            read = (int)Math.min(length, myLength - offset);
            System.arraycopy(myBuffer, (int)offset, resultingArray, 0, read);
        }
        for (int i = read; i < length && read > 0; i++) {
            resultingArray[i] = resultingArray[i - read];
        }
        return new LocalInputStream(resultingArray);
    }

    /**
     * @param source
     * @param length
     * @throws SVNException
     */
    public void append(InputStream source, long length) throws SVNException {
        byte[] bytes = null;
        try {
            if (source instanceof LocalInputStream) {
                bytes = ((LocalInputStream) source).getBuffer();
            } else {
                bytes = new byte[(int) length];
                length = source.read(bytes, 0, (int) length);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } 
        byte[] newBuffer = new byte[(int)length + myLength];
        System.arraycopy(myBuffer, 0, newBuffer, 0, myLength);
        System.arraycopy(bytes, 0, newBuffer, myLength, (int)length);
        myBuffer = newBuffer;
        myLength += (int)length;
    }

    /**
     * @return
     */
    public long length() {
        return myLength;
    }

    /**
     * @return
     */
    public long lastModified() {
        return 0;
    }

    /**
     * @throws IOException
     */
    public void close() throws IOException {
    }

    private static class LocalInputStream extends ByteArrayInputStream {

        public LocalInputStream(byte[] buffer) {
            super(buffer);
        }
        
        public byte[] getBuffer() {
            return buf;
        }
        
    }

}
