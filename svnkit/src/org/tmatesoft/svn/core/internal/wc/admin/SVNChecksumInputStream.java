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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNChecksumInputStream extends InputStream {
    
    private InputStream mySource;
    private MessageDigest myDigest;
    private byte[] myDigestResult;

    public SVNChecksumInputStream(InputStream source) {
        mySource = source;
        try {
            myDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
        }
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int r = mySource.read(b, off, len);
        if (r >= 0) {
            myDigest.update(b, 0, r);
        }
        return r;
    }

    public int read(byte[] b) throws IOException {
        int r = mySource.read(b);
        if (r >= 0) {
            myDigest.update(b, 0, r);
        }
        return r;
    }

    public int read() throws IOException {
        int r = mySource.read();
        if (r >= 0) {
            myDigest.update((byte) (r & 0xFF));
        }
        return r;
    }

    public void close() throws IOException {
        myDigestResult = myDigest.digest();
    }
    
    public String getDigest() {
        return SVNFileUtil.toHexDigest(myDigestResult);
    }

}
