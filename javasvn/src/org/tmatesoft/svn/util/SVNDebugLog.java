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
package org.tmatesoft.svn.util;

import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.internal.util.DefaultSVNDebugLogger;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDebugLog {
    
    private static ISVNDebugLogger ourLogger;

    public static void setLogger(ISVNDebugLogger logger) {
        ourLogger = logger;
    }
    
    private static ISVNDebugLogger getLogger() {
        if (ourLogger == null) {
            ourLogger = new DefaultSVNDebugLogger();
        }
        return ourLogger;
    }

    public static InputStream createLogStream(InputStream is) {
        if (getLogger() != null) {
            return getLogger().createLogStream(is);
        }
        return is;
    }

    public static OutputStream createLogStream(OutputStream os) {
        if (getLogger() != null) {
            return getLogger().createLogStream(os);
        }
        return os;
    }

    public static void flushStream(Object stream) {
        if (getLogger() != null) {
            getLogger().flushStream(stream);
        }
    }

    public static void log(String message, byte[] data) {
        if (getLogger() != null) {
            getLogger().log(message, data);
        }
    }

    public static void logInfo(String message) {
        if (getLogger() != null) {
            getLogger().logInfo(message);
        }
    }

    public static void logError(String message) {
        if (getLogger() != null) {
            getLogger().logError(message);
        }
    }
    
    public static void logInfo(Throwable th) {
        if (getLogger() != null) {
            getLogger().logInfo(th);
        }
    }

    public static void logError(Throwable th) {
        if (getLogger() != null) {
            getLogger().logError(th);
        }
    }
}
