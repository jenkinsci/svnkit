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
package org.tmatesoft.svn.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;

import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.framework.log.FrameworkLogEntry;
import org.osgi.framework.FrameworkEvent;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class SVNKitLog extends SVNDebugLogAdapter {

    private static final String DEBUG_FINE = "/debug/fine";
    private static final String DEBUG_INFO = "/debug/info";
    private static final String DEBUG_WARNING = "/debug/warning";
    private static final String DEBUG_ERROR = "/debug/error";
    private static final String DEBUG_TRACE = "/debug/trace";

    private SVNKitActivator myActivator;

    public SVNKitLog(SVNKitActivator activator) {
        myActivator = activator;
    }

    public boolean isFineEnabled() {
        return myActivator.getDebugOption(DEBUG_FINE);
    }

    public boolean isInfoEnabled() {
        return myActivator.getDebugOption(DEBUG_INFO);
    }

    public boolean isWarningEnabled() {
        return myActivator.getDebugOption(DEBUG_WARNING);
    }

    public boolean isErrorEnabled() {
        return myActivator.getDebugOption(DEBUG_ERROR);
    }

    public boolean isTraceEnabled() {
        return myActivator.getDebugOption(DEBUG_TRACE);
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
        FrameworkLog log = myActivator.getFrameworkLog();
        if (log == null) {
            return;
        }
        if (isTraceEnabled()) {
            FrameworkLogEntry entry = null;
            String dataMessage = null;
            try {
                dataMessage = new String(data, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                dataMessage = new String(data);
            }
            message += " : " + dataMessage;
            message = getMessage(logType, message);
            entry = myActivator.createFrameworkLogEntry(FrameworkEvent.INFO, message, null);
            if (entry != null) {
                log.log(entry);
            }
        }
    }

    public void log(SVNLogType logType, Throwable th, Level logLevel) {
        FrameworkLog log = myActivator.getFrameworkLog();
        if (log == null) {
            return;
        }
        if (th != null) {
            int level = getSeverity(logLevel);
            if (level >= 0) {
                String message = getMessage(logType, th.getMessage());
                FrameworkLogEntry entry = myActivator.createFrameworkLogEntry(FrameworkEvent.ERROR, message, th);
                if (entry != null) {
                    log.log(entry);
                }
            }
        }
    }

    public void log(SVNLogType logType, String message, Level logLevel) {
        FrameworkLog log = myActivator.getFrameworkLog();
        if (log == null) {
            return;
        }
        if (message != null) {
            int level = getSeverity(logLevel);
            if (level >= 0) {
                message = getMessage(logType, message);
                FrameworkLogEntry entry = myActivator.createFrameworkLogEntry(FrameworkEvent.ERROR, message, null);
                if (entry != null) {
                    log.log(entry);
                }
            }
        }
    }

    private int getSeverity(Level logLevel) {
        int level = -1;
        if ((logLevel == Level.FINEST || logLevel == Level.FINE) && isFineEnabled()) {
            level = FrameworkEvent.INFO;
        } else if (logLevel == Level.INFO && isInfoEnabled()) {
            level = FrameworkEvent.INFO;
        } else if (logLevel == Level.WARNING && isWarningEnabled()) {
            level = FrameworkEvent.WARNING;
        } else if (logLevel == Level.SEVERE && isErrorEnabled()) {
            level = FrameworkEvent.ERROR;
        }
        return level;
    }

    private String getMessage(SVNLogType logType, String originalMessage) {
        if (originalMessage == null) {
            return logType.getName();
        }
        return logType.getShortName() + ": " + originalMessage;
    }

}
