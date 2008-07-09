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

    private boolean myIsFineEnabled;
    private boolean myIsInfoEnabled;
    private boolean myIsWarningEnabled;
    private boolean myIsErrorEnabled;

    private ILog myLog;
    private String myPluginID;

    public SVNKitLog(Bundle bundle, boolean debugEnabled, boolean traceEnabled) {
        myLog = Platform.getLog(bundle);
        myPluginID = bundle.getSymbolicName();

        if (traceEnabled) {
            // enable tracing even when not in debug mode
            myIsErrorEnabled = true;
            myIsWarningEnabled = true;
            myIsInfoEnabled = true;
            myIsErrorEnabled = true;
        } else {
            // enabled even when not in debug mode
            myIsErrorEnabled = Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_ERROR));

            // debug mode have to be enabled
            myIsWarningEnabled = debugEnabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_WARNING));
            myIsInfoEnabled = debugEnabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_INFO));
            myIsFineEnabled = debugEnabled && Boolean.TRUE.toString().equals(Platform.getDebugOption(myPluginID + DEBUG_FINE));
        }
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

    public InputStream createLogStream(InputStream is) {
        if (isFineEnabled()) {
            return super.createLogStream(is);
        }
        return is;
    }

    public OutputStream createLogStream(OutputStream os) {
        if (isFineEnabled()) {
            return super.createLogStream(os);
        }
        return os;
    }

    public void logFinest(Throwable th) {
        log(th, Level.FINEST);
    }

    public void logFinest(String message) {
        log(message, Level.FINEST);
    }

    public void logFiner(Throwable th) {
        log(th, Level.FINE);
    }

    public void logFiner(String message) {
        log(message, Level.FINE);
    }

    public void logFine(Throwable th) {
        log(th, Level.INFO);
    }

    public void logFine(String message) {
        log(message, Level.INFO);
    }

    public void logError(String message) {
        log(message, Level.WARNING);
    }

    public void logError(Throwable th) {
        log(th, Level.WARNING);
    }

    public void logSevere(String message) {
        log(message, Level.SEVERE);
    }

    public void logSevere(Throwable th) {
        log(th, Level.SEVERE);
    }

    public void log(String message, byte[] data) {
        if (isFineEnabled()) {
            try {
                myLog.log(createStatus(IStatus.INFO, message + " : " + new String(data, "UTF-8"), null));
            } catch (UnsupportedEncodingException e) {
                myLog.log(createStatus(IStatus.INFO, message + " : " + new String(data), null));
            }
        }
    }

    public void log(Throwable th, Level logLevel) {
        if (th != null) {
            if ((logLevel == Level.FINEST || logLevel == Level.FINE) && isFineEnabled()) {
                myLog.log(createStatus(IStatus.OK, th.getMessage(), th));
            } else if (logLevel == Level.INFO && isInfoEnabled()) {
                myLog.log(createStatus(IStatus.INFO, th.getMessage(), th));
            } else if (logLevel == Level.WARNING && isWarningEnabled()) {
                myLog.log(createStatus(IStatus.WARNING, th.getMessage(), th));
            } else if (logLevel == Level.SEVERE && isErrorEnabled()) {
                myLog.log(createStatus(IStatus.ERROR, th.getMessage(), th));
            }
        }
    }

    public void log(String message, Level logLevel) {
        if (message != null) {
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
}
