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
import java.io.PushbackInputStream;

/**
 * @author Alexander Kitaev
 */
class RollbackInputStream extends InputStream {

    private PushbackInputStream mySource;
    private byte[] myBuffer;
    private int myLength;
    
    public RollbackInputStream(InputStream source) {
        mySource = new PushbackInputStream(source, 0x100);
    }

    public boolean markSupported() {
        return true;
    }

    public int read() throws IOException {
        int read = mySource.read();
        if (myBuffer != null && myLength < myBuffer.length && myLength >= 0) {
            myBuffer[myLength] = (byte) read;
            myLength++;
        } else {
            myLength = -1;
            myBuffer = null;
        }
        return read;
    }
    
    public synchronized void mark(int readlimit) {
        myBuffer = new byte[readlimit];
        myLength = 0;
    }
    
    public synchronized void reset() throws IOException {
        if (myLength < 0) {
            throw new IOException("maximum read limit exceeded");
        }
        if (myBuffer == null) {
            throw new IOException("mark was not set, buffer is null");
        }
        mySource.unread(myBuffer, 0, myLength);
        myBuffer = null;
        myLength = 0;
    }
} 