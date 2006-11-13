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

package org.tmatesoft.svn.core.internal.io.dav.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
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
                if (count < 0) {
                    mySource = null;
                    throw new IOException("Cannot read chunk of data, connection is closed by the server or end of stream reached");
                }
                length -= count;
                offset += count;
            }
            myPosition = 0;
        }
        return myBuffer[myPosition++] & 0xff;
    }

    public void close() throws IOException {
        if (mySource != null) {
            try {
                FixedSizeInputStream.consumeRemaining(this);
            } catch (IOException e) {}
            mySource = null;
        }
    }

    private int readChunkLength() throws IOException {
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int state = 0; 
        while (state != -1) {
            int b = mySource.read();
            if (b == -1) { 
                throw new IOException("Chunked stream ended unexpectedly");
            }
            switch (state) {
                case 0: 
                    switch (b) {
                        case '\r':
                            state = 1;
                            break;
                        case '\"':
                            state = 2;
                        default:
                            baos.write(b);
                    }
                    break;
                case 1:
                    if (b == '\n') {
                        state = -1;
                    } else {
                        throw new IOException("Protocol violation: Unexpected single newline character in chunk size");
                    }
                    break;
                case 2:
                    switch (b) {
                        case '\\':
                            b = mySource.read();
                            baos.write(b);
                            break;
                        case '\"':
                            state = 0;
                        default:
                            baos.write(b);
                    }
                    break;
                default: throw new IOException("Assertion failed while reading chunk length");
            }
        }

        String dataString = new String(baos.toByteArray());
        int separator = dataString.indexOf(';');
        dataString = (separator > 0) ? dataString.substring(0, separator).trim() : dataString.trim();
        if (dataString.trim().length() == 0) {
            return readChunkLength();
        }

        int result;
        try {
            result = Integer.parseInt(dataString.trim(), 16);
        } catch (NumberFormatException e) {
            throw new IOException ("Bad chunk size: " + dataString);
        }
        return result;
    }
}
