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
package org.tmatesoft.svn.core.internal.util;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.tmatesoft.svn.util.ISVNDebugLog;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNLogInputStream extends FilterInputStream {

    private ISVNDebugLog myLog;
    private ByteArrayOutputStream myBuffer;

    public SVNLogInputStream(InputStream in, ISVNDebugLog log) {
        super(in);
        myLog = log;
        myBuffer = new ByteArrayOutputStream(2048);
    }

    public int read() throws IOException {
        int r = super.read();
        if (r >= 0) {
            log(new byte[] {(byte) r}, 0, 1);
        }
        return r;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int read = super.read(b, off, len);
        if (read > 0) {
            log(b, off, read);
        }
        return read;
    }

    public void close() throws IOException {
        super.close();
        flushBuffer(true);
    }
    
    private void log(byte[] data, int off, int len) {
        if (myLog != null && len > 0 && off + len <= data.length && off < data.length) {
            myBuffer.write(data, off, len);
            flushBuffer(false);
        }
    }
    
    public void flushBuffer(boolean force) {
        if (!force && myBuffer.size() < 1024) {
            return;
        }
        if (myLog != null && myBuffer.size() > 0) {
            myLog.log("READ", myBuffer.toByteArray());
        }
        myBuffer.reset();
    }
    
    
}
