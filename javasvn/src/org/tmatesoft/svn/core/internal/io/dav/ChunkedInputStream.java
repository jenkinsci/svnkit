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

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Alexander Kitaev
 */
class ChunkedInputStream extends InputStream {
    
    private InputStream mySource;
    private byte[] myBuffer;
    private int myPosition;

    public ChunkedInputStream(InputStream source) {
        mySource = source;
    }

    public int read() throws IOException {
        if (mySource == null) {
            return -1;
        }
        if (myBuffer == null || myPosition >= myBuffer.length) {
            int length = readChunkLength();
            if (length == 0) {
                mySource = null;
                return -1;
            }
            myBuffer = new byte[length];
            int offset = 0;
            while(length > 0) {
                int count = mySource.read(myBuffer, offset, length);
                length -= count;
                offset += count;
            }
            myPosition = 0;
        }
        return myBuffer[myPosition++];
    }

    private int readChunkLength() throws IOException {
        int ch = 0;
        StringBuffer sb = new StringBuffer();
        while(true) {
            ch = (char) mySource.read();
            if (ch == '\r') {
                continue;
            }
            if (ch == '\n' || ch == '\r') {
                if (sb.length() == 0) {
                    continue;
                }
                break;
            }
            sb.append((char) ch);
        }
        return Integer.parseInt(sb.toString(), 16);
    }

}
