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
package org.tmatesoft.svn.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;

import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;

/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNKitLog extends SVNDebugLogAdapter {

    private static final String DEBUG_FINE = "/debug/fine";
    private static final String DEBUG_INFO = "/debug/info";
    private static final String DEBUG_WARNING = "/debug/warning";
    private static final String DEBUG_ERROR = "/debug/error";
    private static final String DEBUG_TRACE = "/debug/trace";

    private boolean myIsFineEnabled;
    private boolean myIsInfoEnabled;
    private boolean myIsWarningEnabled;
    private boolean myIsErrorEnabled;
    private boolean myIsTraceEnabled;

    private ILog myLog;
    private String myPluginID;

    public SVNKitLog(Bundle bundle, boolean debugEnabled) {
        myLog = Platform.getLog(bundle);
        myPluginID = bundle.getSymbolicName();

        // enabled even when not in debug mode
        myIsErrorEnabled = Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_ERROR));
        myIsTraceEnabled = Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_TRACE));

        // debug mode have to be enabled
        myIsWarningEnabled = debugEnabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_WARNING));
        myIsInfoEnabled = debugEnabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_INFO));
        myIsFineEnabled = debugEnabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_FINE));
    }

    public boolean isFineEnabled() {
        return myIsFineEnabled;
    }

    public boolean isInfoEnabled() {
        return myIsInfoEnabled;
    }

    public boolean isWarningEnabled() {
        return myIsWarningEnabled;
    }

    public boolean isErrorEnabled() {
        return myIsErrorEnabled;
    }

    public boolean isTraceEnabled() {
        return myIsTraceEnabled;
    }

    public InputStream createLogStream(SVNLogType logType, InputStream is) {
        if (isTraceEnabled()) {
            return super.createLogStream(logType, is);
        }
        return is;
    }

    public OutputStream createLogStream(SVNLogType logType, OutputStream os) {
        if (isTraceEnabled()) {
            return super.createLogStream(logType, os);
        }
        return os;
    }

    public void logFinest(SVNLogType logType, Throwable th) {
        log(logType, th, Level.FINEST);
    }

    public void logFinest(SVNLogType logType, String message) {
        log(logType, message, Level.FINEST);
    }

    public void logFiner(SVNLogType logType, Throwable th) {
        log(logType, th, Level.FINE);
    }

    public void logFiner(SVNLogType logType, String message) {
        log(logType, message, Level.FINE);
    }

    public void logFine(SVNLogType logType, Throwable th) {
        log(logType, th, Level.INFO);
    }

    public void logFine(SVNLogType logType, String message) {
        log(logType, message, Level.INFO);
    }

    public void logError(SVNLogType logType, String message) {
        log(logType, message, Level.WARNING);
    }

    public void logError(SVNLogType logType, Throwable th) {
        log(logType, th, Level.WARNING);
    }

    public void logSevere(SVNLogType logType, String message) {
        log(logType, message, Level.SEVERE);
    }

    public void logSevere(SVNLogType logType, Throwable th) {
        log(logType, th, Level.SEVERE);
    }

    public void log(SVNLogType logType, String message, byte[] data) {
        if (logType == SVNLogType.NETWORK && !isTraceEnabled()) {
            return;
        }
        if (isFineEnabled() || isTraceEnabled()) {
            try {
                myLog.log(createStatus(IStatus.INFO, getMessage(logType, message + " : " +
                        new String(data, "UTF-8")), null));
            } catch (UnsupportedEncodingException e) {
                myLog.log(createStatus(IStatus.INFO, getMessage(logType, message + " : " +
                        new String(data)), null));
            }
        }
    }

    public void log(SVNLogType logType, Throwable th, Level logLevel) {
        if (th != null) {
            if ((logLevel == Level.FINEST || logLevel == Level.FINE) && isFineEnabled()) {
                myLog.log(createStatus(IStatus.OK, getMessage(logType, th.getMessage()), th));
            } else if (logLevel == Level.INFO && isInfoEnabled()) {
                myLog.log(createStatus(IStatus.INFO, getMessage(logType, th.getMessage()), th));
            } else if (logLevel == Level.WARNING && isWarningEnabled()) {
                myLog.log(createStatus(IStatus.WARNING, getMessage(logType, th.getMessage()), th));
            } else if (logLevel == Level.SEVERE && isErrorEnabled()) {
                myLog.log(createStatus(IStatus.ERROR, getMessage(logType, th.getMessage()), th));
            }
        }
    }

    public void log(SVNLogType logType, String message, Level logLevel) {
        if (message != null) {
            message = getMessage(logType, message);
            if ((logLevel == Level.FINEST || logLevel == Level.FINE) && isFineEnabled()) {
                myLog.log(createStatus(IStatus.OK, message, null));
            } else if (logLevel == Level.INFO && isInfoEnabled()) {
                myLog.log(createStatus(IStatus.INFO, message, null));
            } else if (logLevel == Level.WARNING && isWarningEnabled()) {
                myLog.log(createStatus(IStatus.WARNING, message, null));
            } else if (logLevel == Level.SEVERE && isErrorEnabled()) {
                myLog.log(createStatus(IStatus.ERROR, message, null));
            }
        }
    }

    private Status createStatus(int severity, String message, Throwable th) {
        return new Status(severity, myPluginID, IStatus.OK, message == null ? "" : message, th);
    }

    private String getMessage(SVNLogType logType, String originalMessage) {
        return logType.getShortName() + ": " + originalMessage;
    }

}
