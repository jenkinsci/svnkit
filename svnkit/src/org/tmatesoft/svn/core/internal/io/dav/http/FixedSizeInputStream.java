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

import java.io.IOException;
import java.io.InputStream;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
class FixedSizeInputStream extends InputStream {
    
    private long myLength;
    private InputStream mySource;

    public FixedSizeInputStream(InputStream source, long length) {
    	mySource = source;
        myLength = length;
    }

    public int read() throws IOException {
        if (myLength > 0) {
            myLength--;
            return mySource.read();
        }
        return -1;
    }
    
    public void close() {
        // just read remainging data.
        if (myLength > 0) {
            try {
                consumeRemaining(this);
            } catch (IOException e) {
            }
        }
    }
    
    static void consumeRemaining(InputStream is) throws IOException {
        byte[] buffer = new byte[1024];
        while(is.read(buffer) > 0);
    }

}
