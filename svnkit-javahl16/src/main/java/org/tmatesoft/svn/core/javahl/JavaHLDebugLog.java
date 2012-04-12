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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;
import org.tmatesoft.svn.util.SVNLogType;

import org.tigris.subversion.javahl.JavaHLObjectFactory;
import org.tigris.subversion.javahl.SVNClientLogLevel;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class JavaHLDebugLog extends SVNDebugLogAdapter {

    private static final String JAVAHL_LOGGER_NAME = "svnkit-javahl";

    private static JavaHLDebugLog ourInstance = new JavaHLDebugLog();

    private Map myHandlers = new HashMap();
    private Logger myLogger;

    public static JavaHLDebugLog getInstance() {
        return ourInstance;
    }

    public synchronized void enableLogging(int logLevel, File logPath, Formatter formatter) throws SVNException {
        logPath = logPath.getAbsoluteFile();
        if (logLevel == SVNClientLogLevel.NoLog) {
            if (logPath == null) {
                resetLogHandlers();
            } else {
                Handler handler = (Handler) myHandlers.remove(logPath);
                if (handler != null) {
                    handler.close();
                    getLogger().removeHandler(handler);
                }
            }
            return;
        }

        Level level = JavaHLObjectFactory.getLoggingLevel(logLevel);
        Handler handler = (Handler) myHandlers.get(logPath);
        if (handler == null) {
            try {
                handler = new FileHandler(logPath.getAbsolutePath(), true);
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()), e, SVNLogType.DEFAULT);
            }
            myHandlers.put(logPath, handler);            
        }
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
            myLogger.setUseParentHandlers(false);
            myLogger.setLevel(Level.ALL);
            resetLogHandlers();
        }
        return myLogger;
    }

    public void log(SVNLogType logType, String message, byte[] data) {
        if (getLogger().isLoggable(Level.FINEST)) {
            try {
                getLogger().log(Level.FINEST, getMessage(message + "\n" + new String(data, "UTF-8")));
            } catch (UnsupportedEncodingException e) {
                getLogger().log(Level.FINEST, getMessage(message + "\n" + new String(data)));
            }
        }
    }

    public void log(SVNLogType logType, Throwable th, Level logLevel) {
        if (getLogger().isLoggable(logLevel) && th != null) {
            getLogger().log(logLevel, getMessage(th.getMessage()), th);
        }
    }

    public void log(SVNLogType logType, String message, Level logLevel) {
        if (getLogger().isLoggable(logLevel) && message != null) {
            getLogger().log(logLevel, getMessage(message));
        }
    }
    
    private String getMessage(String originalMessage) {
        return "JAVAHL" + ": " + originalMessage;
    }

}
