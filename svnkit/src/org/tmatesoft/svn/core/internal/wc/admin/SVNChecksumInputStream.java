/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNChecksumInputStream extends InputStream {
    
    public static final String MD5_ALGORITHM = "MD5";
    
    private InputStream mySource;
    private MessageDigest myDigest;
    private byte[] myDigestResult;
    private boolean myCloseSource;
    private boolean myReadToEnd;
    private boolean myStreamIsFinished;
    
    public SVNChecksumInputStream(InputStream source, String algorithm, boolean closeSource, boolean readToEnd) {
        mySource = source;
        myCloseSource = closeSource;
        myReadToEnd = readToEnd;
        algorithm = algorithm == null ? MD5_ALGORITHM : algorithm;
        try {
            myDigest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
        }
    }

    public long skip(long n) throws IOException {
        long skippedN = 0;
        int r = -1;
        while ((r = read()) != -1 && n-- > 0) {
            skippedN++;
        }
        if (r == -1) {
            myStreamIsFinished = true;
        }
        return skippedN;
    }
    
    public int read(byte[] b, int off, int len) throws IOException {
        int r = mySource.read(b, off, len);
        if (r >= 0) {
            myDigest.update(b, 0, r);
        } else {
            myStreamIsFinished = true;
        }
        
        return r;
    }

    public int read(byte[] b) throws IOException {
        int r = mySource.read(b);
        if (r >= 0) {
            myDigest.update(b, 0, r);
        } else {
            myStreamIsFinished = true;
        }
        return r;
    }

    public int read() throws IOException {
        int r = mySource.read();
        if (r >= 0) {
            myDigest.update((byte) (r & 0xFF));
        } else {
            myStreamIsFinished = true;
        }
        return r;
    }

    public void close() throws IOException {
        if (myReadToEnd && !myStreamIsFinished) {
            byte[] buffer = new byte[16384];
            while (read(buffer) != -1) {
                continue;
            }
            myStreamIsFinished = true;
        }
        if (myCloseSource) {
            mySource.close();
        }

        myDigestResult = myDigest.digest();
    }
    
    public String getDigest() {
        return SVNFileUtil.toHexDigest(myDigestResult);
    }

}
