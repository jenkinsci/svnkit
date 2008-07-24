/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.ISVNDebugLog;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNLogInputStream extends InputStream {

    private OutputStream myLog;
    private InputStream myIn;

    public SVNLogInputStream(InputStream in, ISVNDebugLog log) {
        myIn = in;
        myLog = log.createInputLogStream();
        if (myLog == null) {
            myLog = SVNFileUtil.DUMMY_OUT;
        }
    }

    public long skip(long n) throws IOException {
        return myIn.skip(n);
    }

    public void close() throws IOException {
        try {
            myIn.close();
        } catch (IOException e) {
            throw e;
        } finally {
            try {
                myLog.close();
            } catch (IOException e) {
            }
        }
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int read;
        try {
            read = myIn.read(b, off, len);
            if (read > 0) {
                try {
                    myLog.write(b, off, read);
                } catch (IOException e) {
                }
            }
        } catch (IOException e) {
            throw e;
        } 
        return read;
    }

    public int read() throws IOException {
        int read;
        try {
            read = myIn.read();
            try {
                if (read >= 0) {
                    myLog.write(read & 0xFF);
                }
            } catch (IOException e) {
            }
        } catch (IOException e) {
            throw e;
        } 
        return read;
    }
    
    public void flushBuffer() {
        try {
            myLog.flush();            
        } catch (IOException e) {
        }
    }
}
