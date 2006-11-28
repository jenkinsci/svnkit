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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tmatesoft.svn.util.SVNDebugLogAdapter;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNDebugLogger extends SVNDebugLogAdapter {

    private Logger myLogger;

    public void info(String message) {
        getLogger().log(Level.FINE, message);
    }

    public void error(String message) {
        getLogger().log(Level.SEVERE, message);
    }

    public void info(Throwable th) {
        if (getLogger().isLoggable(Level.FINE)) {
            getLogger().log(Level.FINE, th != null ? th.getMessage() : "", th);
        }
    }

    public void error(Throwable th) {
        if (getLogger().isLoggable(Level.SEVERE)) {
            getLogger().log(Level.SEVERE, th != null ? th.getMessage() : "", th);
        }
    }

    public void log(String message, byte[] data) {
        if (getLogger().isLoggable(Level.FINEST)) {
            getLogger().log(Level.FINEST, message + "\n" + new String(data));
        }
    }

    public InputStream createLogStream(InputStream is) {
        if (getLogger().isLoggable(Level.FINEST)) {
            return super.createLogStream(is);
        }
        return is;
    }

    public OutputStream createLogStream(OutputStream os) {
        if (getLogger().isLoggable(Level.FINEST)) {
            return super.createLogStream(os);
        }
        return os;
    }
    
    private Logger getLogger() {
        if (myLogger == null) {
            myLogger = Logger.getLogger("svnkit");
        }
        return myLogger;
    }
}