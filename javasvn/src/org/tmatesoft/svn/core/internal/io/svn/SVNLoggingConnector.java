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
import java.io.OutputStream;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class SVNLoggingConnector implements ISVNConnector {
    
    private ISVNConnector myDelegate;
    private OutputStream myOutputStream;
    private InputStream myInputStream;
    
    private static StringBuffer mySentBuffer = new StringBuffer();
    private static StringBuffer myReadBuffer = new StringBuffer();

    public SVNLoggingConnector(ISVNConnector delegate) {
        myDelegate = delegate;
    }

    public void open(SVNRepositoryImpl repository) throws SVNException {
        myDelegate.open(repository);
    }

    public void close() throws SVNException {
        myDelegate.close();
    }

    public OutputStream getOutputStream() throws IOException {
        if (myOutputStream == null) {
            myOutputStream = new OutputStream() {
                public void close() throws IOException {
                    myDelegate.getOutputStream().close();
                }
                public void write(int b) throws IOException {
                    myDelegate.getOutputStream().write(b);
                    mySentBuffer.append((char) b);
                }
                public void write(byte[] b,int off,int len) throws IOException {
                    myDelegate.getOutputStream().write(b, off, len);
                    mySentBuffer.append(new String(b, off, len));
                }                
            };
        }
        return myOutputStream;
    }

    public InputStream getInputStream() throws IOException {
        if (myInputStream != null) {
            myInputStream = new InputStream() {                
                public void close() throws IOException {
                    myDelegate.getInputStream().close();
                }                
                public int read() throws IOException {
                    int read = myDelegate.getInputStream().read();
                    if (read >= 0) {
                        myReadBuffer.append((char) read);
                    }
                    return read;
                }                
                public int read(byte[] b,int off,int len) throws IOException {
                    int read = myDelegate.getInputStream().read(b, off, len);
                    if (read >= 0) {
                        myReadBuffer.append(new String(b, off, read));
                    }
                    return read;
                }
            };
        }
        return myInputStream;
    }
    
    public static void flush() {
        if (myReadBuffer.length() > 0) {
            DebugLog.log("SVN.READ: " + myReadBuffer.toString());
            myReadBuffer.delete(0, myReadBuffer.length());
        }
        if (mySentBuffer.length() > 0) {
            DebugLog.log("SVN SENT: " + mySentBuffer.toString());
            mySentBuffer.delete(0, mySentBuffer.length());
        }
    }

}
