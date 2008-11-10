/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ResourceBundle;
import java.util.Collection;
import java.util.HashSet;
import java.util.logging.Level;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNTestDebugLog extends SVNDebugLogAdapter implements ISVNEventHandler {

    private static final SVNTestDebugLog INSTANCE = new SVNTestDebugLog();

    public static void init(ResourceBundle bundle) {
        String tmp = bundle.getString("test.tmp.dir");
        INSTANCE.setTMP(tmp);
    }

    public static void enable() {
        SVNDebugLog.setDefaultLog(getDebugLog());
    }

    public static void disable() {
        SVNDebugLog.setDefaultLog(null);
    }

    public static void enableTracing(boolean enable) {
        synchronized (INSTANCE) {
            INSTANCE.useTracing(enable);
        }
    }

    public void disable(SVNLogType logType) {
        synchronized (INSTANCE) {
            INSTANCE.getExceptions().add(logType);
        }
    }

    public void enable(SVNLogType logType) {
        synchronized (INSTANCE) {
            INSTANCE.getExceptions().remove(logType);
        }
    }

    public static ISVNDebugLog getDebugLog() {
        return INSTANCE;
    }

    public static ISVNEventHandler getEventHandler() {
        return INSTANCE;
    }

    public static void log(String message) {
        System.out.println(message);
    }

    public static void log(Throwable th) {
        th.printStackTrace(System.err);
    }

    private String myTMP;
    private boolean myIsTracing;

    private Collection myExceptions;

    private void useTracing(boolean enable) {
        myIsTracing = enable;
    }

    public void setTMP(String TMP) {
        myTMP = TMP;
    }

    private String getTMP() {
        return myTMP;
    }

    private Collection getExceptions() {
        if (myExceptions == null) {
            myExceptions = new HashSet();
        }
        return myExceptions;
    }

    private String getRelativePath(File file) {
        if (file == null) {
            return "NULL";
        }

        String path = file.getAbsolutePath();
        return SVNPathUtil.getRelativePath(getTMP(), path);
    }

    public void log(SVNLogType logType, Throwable th, Level logLevel) {
        if (getExceptions().contains(logType)) {
            return;
        }
        log(logType, th.getMessage(), logLevel);
    }

    public void log(SVNLogType logType, String message, Level logLevel) {
        if (getExceptions().contains(logType)) {
            return;
        }
        StringBuffer buffer = new StringBuffer();
        buffer.append(logType.getShortName());
        buffer.append(": ");
        buffer.append(message);
        log(buffer.toString());
    }

    public void log(SVNLogType logType, String message, byte[] data) {
        if (!myIsTracing) {
            return;
        }
        String dataString;
        try {
            dataString = new String(data, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            dataString = new String(data);
        }
        log(logType, message + '\n' + dataString, Level.FINE);
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[Event]");
        buffer.append("\n\tfile: ");
        buffer.append(getRelativePath(event.getFile()));
        buffer.append("\n\taction: ");
        buffer.append(event.getAction());
        buffer.append("\n\tcontent status: ");
        buffer.append(event.getContentsStatus());
        buffer.append("\n\tproperties status: ");
        buffer.append(event.getPropertiesStatus());
        log(buffer.toString());
    }

    public void checkCancelled() throws SVNCancelException {
    }
}
