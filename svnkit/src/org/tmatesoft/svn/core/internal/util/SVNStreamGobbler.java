/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNStreamGobbler extends Thread {
    InputStreamReader is;
    StringBuffer result;
    IOException error;
    private boolean myIsClosed;

    public SVNStreamGobbler(InputStream is) {
        try {
            this.is = new InputStreamReader(is, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            this.is = new InputStreamReader(is);
        }
        result = new StringBuffer();
    }
    
    public void close() {
        myIsClosed = true;
        SVNFileUtil.closeFile(is);
    }

    public void run() {
        try {
            int r;
            while ((r = is.read()) >= 0) {
                result.append((char) (r & 0xFF));
            }
        } catch (IOException ioe) {
            if (!myIsClosed) {
                error = ioe;
            }
        } finally {
            if (!myIsClosed) {
                SVNFileUtil.closeFile(is);
            }
        }
    }

    public String getResult() {
        return result.toString();
    }

    public IOException getError() {
        return error;
    }
}
