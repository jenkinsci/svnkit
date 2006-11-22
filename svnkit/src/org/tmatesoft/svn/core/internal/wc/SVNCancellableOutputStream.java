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
package org.tmatesoft.svn.core.internal.wc;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNCancellableOutputStream extends FilterOutputStream {

    private ISVNEventHandler myEventHandler;

    public SVNCancellableOutputStream(OutputStream out, ISVNEventHandler eventHandler) {
        super(out == null ? SVNFileUtil.DUMMY_OUT : out);
        myEventHandler = eventHandler;
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (myEventHandler != null) {
            try {
                myEventHandler.checkCancelled();
            } catch (final SVNCancelException e) {
                throw new IOCancelException(e.getMessage());
            }
        }
        out.write(b, off, len);
    }

    public void write(byte[] b) throws IOException {
        if (myEventHandler != null) {
            try {
                myEventHandler.checkCancelled();
            } catch (final SVNCancelException e) {
                throw new IOCancelException(e.getMessage());
            }
        }
        out.write(b);
    }
    
    public static class IOCancelException extends IOException {

        public IOCancelException(String message) {
            super(message);
        }
    }
}
