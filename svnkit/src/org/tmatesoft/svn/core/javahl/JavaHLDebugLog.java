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

import java.io.File;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;

import org.tigris.subversion.javahl.JavaHLObjectFactory;
import org.tigris.subversion.javahl.SVNClientLogLevel;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class JavaHLDebugLog extends SVNDebugLogAdapter {

    private static final String JAVAHL_LOGGER_NAME = "javahl.svnkit";

    private static JavaHLDebugLog ourInstance = new JavaHLDebugLog();

    private Map myHandlers = new HashMap();
    private Logger myLogger;

    public static JavaHLDebugLog getInstance() {
        return ourInstance;
    }

    public void enableLogging(int logLevel, File logPath, Formatter formatter) throws SVNException {
        logPath = logPath.getAbsoluteFile();
        if (logLevel == SVNClientLogLevel.NoLog) {
            if (logPath == null) {
                resetLogHandlers();
            } else {
                Handler handler = (Handler) myHandlers.remove(logPath);
                handler.close();
                getLogger().removeHandler(handler);
            }
            return;
        }

        Level level = JavaHLObjectFactory.getLoggingLevel(logLevel);
        Handler handler = (Handler) myHandlers.get(logPath);
        if (handler == null) {
            OutputStream logStream = SVNFileUtil.openFileForWriting(logPath);
            handler = new StreamHandler(logStream, formatter);
        }
        myHandlers.put(logPath, handler);
        handler.setFormatter(formatter);
        handler.setLevel(level);
        getLogger().addHandler(handler);
    }

    private void resetLogHandlers() {
        if (getLogger().getHandlers() == null) {
            return;
        }
        for (int i = 0; i < getLogger().getHandlers().length; i++) {
            Handler handler = getLogger().getHandlers()[i];
            handler.close();
            getLogger().removeHandler(handler);
        }
    }

    private Logger getLogger() {
        if (myLogger == null) {
            myLogger = Logger.getLogger(JAVAHL_LOGGER_NAME);
            myLogger.setLevel(Level.FINEST);
            resetLogHandlers();
        }
        return myLogger;
    }

    public void logInfo(String message) {
        if (getLogger().isLoggable(Level.INFO) && message != null) {
            getLogger().log(Level.INFO, message);
        }
    }

    public void logInfo(Throwable th) {
        if (getLogger().isLoggable(Level.INFO) && th != null) {
            getLogger().log(Level.INFO, th.getMessage(), th);
        }
    }

    public void logSevere(String message) {
        if (getLogger().isLoggable(Level.SEVERE) && message != null) {
            getLogger().log(Level.SEVERE, message);
        }
    }

    public void logSevere(Throwable th) {
        if (getLogger().isLoggable(Level.SEVERE) && th != null) {
            getLogger().log(Level.SEVERE, th.getMessage(), th);
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

    public void logFine(Throwable th) {
        if (getLogger().isLoggable(Level.FINE) && th != null) {
            getLogger().log(Level.FINE, th.getMessage(), th);
        }
    }

    public void logFine(String message) {
        if (getLogger().isLoggable(Level.FINE) && message != null) {
            getLogger().log(Level.FINE, message);
        }
    }

    public void logFiner(Throwable th) {
        if (getLogger().isLoggable(Level.FINER) && th != null) {
            getLogger().log(Level.FINER, th.getMessage(), th);
        }
    }

    public void logFiner(String message) {
        if (getLogger().isLoggable(Level.FINER) && message != null) {
            getLogger().log(Level.FINER, message);
        }
    }

    public void logFinest(Throwable th) {
        if (getLogger().isLoggable(Level.FINEST) && th != null) {
            getLogger().log(Level.FINEST, th.getMessage(), th);
        }
    }

    public void logFinest(String message) {
        if (getLogger().isLoggable(Level.FINEST) && message != null) {
            getLogger().log(Level.FINEST, message);
        }
    }
}
