/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.util;

import org.tmatesoft.svn.core.internal.util.DefaultSVNDebugLogger;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDebugLog {
    
    private static ISVNDebugLog ourDefaultLog;

    public static void setDefaultLog(ISVNDebugLog log) {
        ourDefaultLog = log;
    }
    
    public static ISVNDebugLog getDefaultLog() {
        if (ourDefaultLog == null) {
            ourDefaultLog = new DefaultSVNDebugLogger();
        }
        return ourDefaultLog;
    }

    public static void logInfo(String message) {
        getDefaultLog().info(message);
    }

    public static void logError(String message) {
        getDefaultLog().error(message);
    }
    
    public static void logInfo(Throwable th) {
        getDefaultLog().info(th);        
    }

    public static void logError(Throwable th) {
        getDefaultLog().error(th);
    }
}