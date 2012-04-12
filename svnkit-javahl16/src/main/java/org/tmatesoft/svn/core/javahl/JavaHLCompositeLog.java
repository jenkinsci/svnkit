/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.javahl;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class JavaHLCompositeLog extends SVNDebugLogAdapter {

    Map myLoggers;

    public JavaHLCompositeLog() {
        myLoggers = new HashMap();
    }

    public void addLogger(ISVNDebugLog debugLog) {
        Boolean needTracing = checkTracing(debugLog);
        myLoggers.put(debugLog, needTracing);
    }

    public void removeLogger(ISVNDebugLog debugLog) {
        myLoggers.remove(debugLog);        
    }

    private static Boolean checkTracing(ISVNDebugLog log) {
        InputStream is = log.createLogStream(SVNLogType.NETWORK, SVNFileUtil.DUMMY_IN);
        OutputStream os = log.createLogStream(SVNLogType.NETWORK, SVNFileUtil.DUMMY_OUT);
        if (is == SVNFileUtil.DUMMY_IN && os == SVNFileUtil.DUMMY_OUT) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    public InputStream createLogStream(SVNLogType logType, InputStream is) {
        if (myLoggers.containsValue(Boolean.TRUE)) {
            return super.createLogStream(logType, is);
        }
        return is;
    }

    public OutputStream createLogStream(SVNLogType logType, OutputStream os) {
        if (myLoggers.containsValue(Boolean.TRUE)) {
            return super.createLogStream(logType, os);            
        }
        return os;
    }

    public void log(SVNLogType logType, String message, byte[] data) {
        for (Iterator iterator = myLoggers.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            ISVNDebugLog log = (ISVNDebugLog) entry.getKey();
            Boolean needTracing = (Boolean) entry.getValue();
            if (needTracing.booleanValue()) {
                log.log(logType, message, data);                
            }
        }
    }

    public void log(SVNLogType logType, Throwable th, Level logLevel) {
        for (Iterator iterator = myLoggers.keySet().iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.log(logType, th, logLevel);
        }
    }

    public void log(SVNLogType logType, String message, Level logLevel) {
        for (Iterator iterator = myLoggers.keySet().iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.log(logType, message, logLevel);
        }
    }

}
