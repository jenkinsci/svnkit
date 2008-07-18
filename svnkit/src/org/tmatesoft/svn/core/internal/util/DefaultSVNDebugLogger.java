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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class DefaultSVNDebugLogger extends SVNDebugLogAdapter {

    private Logger myLogger;
    private SVNLogType myLogType;

    public DefaultSVNDebugLogger(SVNLogType logType) {
        myLogType = logType != null ? logType : SVNLogType.DEFAULT;
    }

    public void log(Throwable th, Level logLevel) {
        if (getLogger().isLoggable(logLevel) && th != null) {
            getLogger().log(logLevel, getMessage(th.getMessage()), th);
        }
    }

    public void log(String message, Level logLevel) {
        if (getLogger().isLoggable(logLevel) && message != null) {
            getLogger().log(logLevel, getMessage(message));
        }
    }
    
    public void log(String message, byte[] data) {
        if (getLogger().isLoggable(Level.FINEST)) {
            try {
                getLogger().log(Level.FINEST, message + "\n" + new String(data, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                getLogger().log(Level.FINEST, message + "\n" + new String(data));
            }
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
            myLogger = Logger.getLogger(myLogType.getName());
        }
        return myLogger;
    }

    private String getMessage(String originalMessage) {
        return myLogType.getShortName() + ": " + originalMessage;
    }

}