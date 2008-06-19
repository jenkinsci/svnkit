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
package org.tmatesoft.svn.util;

import java.util.Map;

import org.tmatesoft.svn.core.internal.util.DefaultSVNDebugLogger;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNDebugLog {
    
    private static ISVNDebugLog ourDefaultLog;
    private static Map ourLogs;
    
    static {
        ourLogs = new SVNHashMap();
        registerLog(SVNLogType.NETWORK, new DefaultSVNDebugLogger(SVNLogType.NETWORK));
        registerLog(SVNLogType.WC, new DefaultSVNDebugLogger(SVNLogType.WC));
        registerLog(SVNLogType.CLIENT, new DefaultSVNDebugLogger(SVNLogType.CLIENT));
    }
    
    public static ISVNDebugLog registerLog(SVNLogType logType, ISVNDebugLog log) {
        return (ISVNDebugLog) ourLogs.put(logType, log);
    }
    
    public static void setDefaultLog(ISVNDebugLog log) {
        ourDefaultLog = log;
    }
    
    public static ISVNDebugLog getDefaultLog() {
        if (ourDefaultLog == null) {
            ourDefaultLog = new DefaultSVNDebugLogger(SVNLogType.DEFAULT);
        }
        return ourDefaultLog;
    }

    public static ISVNDebugLog getLog(SVNLogType logType) {
        ISVNDebugLog logger = (ISVNDebugLog) ourLogs.get(logType);
        return logger == null ? getDefaultLog() : logger; 
    }
    
    public static void assertCondition(boolean condition, String message) {
        if (!condition) {
            getDefaultLog().logSevere(message);
            getDefaultLog().logSevere(new Exception(message));
        }
    }

}