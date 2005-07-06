/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class LoggingOutputStream extends OutputStream {

    // Fields =================================================================

    private final OutputStream myOutputStream;

    private final StringBuffer myBuffer;

    private final LoggingStreamLogger myLogger;

    // Setup ==================================================================

    public LoggingOutputStream(OutputStream outputStream,
            LoggingStreamLogger logger) {
        myOutputStream = outputStream;
        myBuffer = logger != null ? new StringBuffer() : null;
        myLogger = logger;
    }

    // Implemented ============================================================

    public void write(int b) throws IOException {
        myOutputStream.write(b);
        if (myBuffer != null) {
            myBuffer.append((char) b);
            if (myBuffer.length() > 8192) {
                log();
            }
        }
    }

    public void close() throws IOException {
        myOutputStream.close();
    }

    public void write(byte[] b, int off, int len) throws IOException {
        myOutputStream.write(b, off, len);
        if (myBuffer != null) {
            myBuffer.append(new String(b, off, len));
            if (myBuffer.length() > 8192) {
                log();
            }
        }
    }

    public void flush() throws IOException {
        myOutputStream.flush();
    }

    // Accessing ==============================================================

    public void log() {
        if (myBuffer != null && myBuffer.length() > 0) {
            myLogger.logStream(myBuffer.toString(), true);
            myBuffer.delete(0, myBuffer.length());
        }
    }
}