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
package org.tmatesoft.svn.core.javahl;

import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;

import org.tigris.subversion.javahl.ProgressListener;
import org.tigris.subversion.javahl.ProgressEvent;
import org.tigris.subversion.javahl.JavaHLObjectFactory;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class JavaHLDebugLog implements ISVNDebugLog {
    ProgressListener myListener;
    ISVNDebugLog myDebugLog;
    long myProgress;

    public static ISVNDebugLog wrap(ProgressListener listener, ISVNDebugLog debugLog) {
        debugLog = debugLog == null ? SVNDebugLog.getDefaultLog() : debugLog;
        if (listener == null){
            return debugLog;           
        }
        return new JavaHLDebugLog(listener, debugLog);
    }

    private JavaHLDebugLog(ProgressListener listener, ISVNDebugLog debugLog) {
        myListener = listener;
        myDebugLog = debugLog;
        myProgress = 0;
    }

    public void reset() {
        myProgress = 0;    
    }

    public void info(String message) {
        myDebugLog.info(message);
    }

    public void error(String message) {
        myDebugLog.error(message);
    }

    public void info(Throwable th) {
        myDebugLog.info(th);
    }

    public void error(Throwable th) {
        myDebugLog.error(th);
    }

    public void log(String message, byte[] data) {
        myDebugLog.log(message, data);
        myProgress += data.length;
        myListener.onProgress(JavaHLObjectFactory.createProgressEvent(myProgress, -1L));
    }

    public void flushStream(Object stream) {
        myDebugLog.flushStream(stream);
    }

    public InputStream createLogStream(InputStream is) {
        return myDebugLog.createLogStream(is);
    }

    public OutputStream createInputLogStream() {
        return myDebugLog.createInputLogStream();
    }

    public OutputStream createLogStream(OutputStream os) {
        return myDebugLog.createLogStream(os);
    }

    public OutputStream createOutputLogStream() {
        return myDebugLog.createOutputLogStream();
    }
}
