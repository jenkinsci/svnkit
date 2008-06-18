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
    
    public void logInfo(String message) {
        if (getLogger().isLoggable(Level.INFO) && message != null) {
            getLogger().log(Level.INFO, getMessage(message));
        }
    }

    public void logInfo(Throwable th) {
        if (getLogger().isLoggable(Level.INFO) && th != null) {
            getLogger().log(Level.INFO, getMessage(th.getMessage()), th);
        }
    }

    public void logSevere(String message) {
        if (getLogger().isLoggable(Level.SEVERE) && message != null) {
            getLogger().log(Level.SEVERE, getMessage(message));
        }
    }

    public void logSevere(Throwable th) {
        if (getLogger().isLoggable(Level.SEVERE) && th != null) {
            getLogger().log(Level.SEVERE, getMessage(th.getMessage()), th);
        }
    }

    public void logFine(Throwable th) {
        if (getLogger().isLoggable(Level.FINE) && th != null) {
            getLogger().log(Level.FINE, getMessage(th.getMessage()), th);
        }
    }

    public void logFine(String message) {
        if (getLogger().isLoggable(Level.FINE) && message != null) {
            getLogger().log(Level.FINE, getMessage(message));
        }
    }

    public void logFiner(Throwable th) {
        if (getLogger().isLoggable(Level.FINER) && th != null) {
            getLogger().log(Level.FINER, getMessage(th.getMessage()), th);
        }
    }

    public void logFiner(String message) {
        if (getLogger().isLoggable(Level.FINER) && message != null) {
            getLogger().log(Level.FINER, getMessage(message));
        }
    }

    public void logFinest(Throwable th) {
        if (getLogger().isLoggable(Level.FINEST) && th != null) {
            getLogger().log(Level.FINEST, getMessage(th.getMessage()), th);
        }
    }

    public void logFinest(String message) {
        if (getLogger().isLoggable(Level.FINEST) && message != null) {
            getLogger().log(Level.FINEST, getMessage(message));
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