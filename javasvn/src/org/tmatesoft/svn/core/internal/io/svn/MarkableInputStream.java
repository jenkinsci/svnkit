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
package org.tmatesoft.svn.core.internal.io.svn;

import java.io.IOException;
import java.io.InputStream;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class MarkableInputStream extends InputStream {
    
    private int myLength;
    private byte[] myBuffer;
    
    private InputStream mySource;
    private int myPosition;
    
    public MarkableInputStream(InputStream source, int maxBufferSize) {
        mySource = source;
        myBuffer = new byte[maxBufferSize];
        myLength = 0;
        myPosition = 0;
    }


    public int read() throws IOException {
        // 1. from buffer.
        if (myPosition < myLength) {
            myPosition++;
            return myBuffer[myPosition - 1];
        }
        // 2. directly from source.
        int r = mySource.read();
        // 3. buffer what was read.
        if (r >= 0) {
            buffer((byte) (r & 0xff));
        }
        return r;
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int read = 0;
        int reallyRead = 0;
        if (myPosition < myLength) {
            // from buffer as long as possible.
            read = Math.min(len, myLength - myPosition);
            reallyRead = read;
            System.arraycopy(myBuffer, myPosition, b, off, Math.min(len, myLength - myPosition));
            myPosition += read;
        }
        if (read < len) {
            // from the source.
            reallyRead += mySource.read(b, off + read, len - read);
            buffer(b, off + read, reallyRead - read);
        }
        return reallyRead;
    }

    public synchronized void mark(int readlimit) {
    }

    public synchronized void reset() throws IOException {
    }

    public boolean markSupported() {
        return true;
    }

    // if there is an active mark =>
    // these two methods should fill buffer,
    // update length and position 
    // and validate existing marks.
    // then check if there are valid marks...
    private void buffer(byte b) {
        if (myLength + 1 < myBuffer.length) {
            myBuffer[myLength + 1] = b;
            myLength++;
        } else {
            shiftBufferLeft(1);
            myBuffer[myLength - 1] = b;
        }
        myPosition = myLength;
    }

    private void buffer(byte b[], int off, int len) {
        if (myLength + len < myBuffer.length) {
            System.arraycopy(b, off, myBuffer, myLength, len);
            myLength += len;
        } else {
            if (len < myBuffer.length) {
                shiftBufferLeft(len);
                System.arraycopy(b, off, myBuffer, myBuffer.length - len, len);
            } else {
                myLength = 0;
            }
        }
        myPosition = myLength;
    }

    private void shiftBufferLeft(int shift) {
        byte[] newBuffer = new byte[myBuffer.length];
        System.arraycopy(myBuffer, shift, newBuffer, 0, myBuffer.length - shift);
        myBuffer = newBuffer;
    }
}
