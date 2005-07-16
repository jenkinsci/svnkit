/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.util;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class DebugLog {

    private static DebugLogger ourLogger;

    public static void setLogger(DebugLogger logger) {
        ourLogger = logger;
    }

    public static boolean isEnabled() {
        return getLogger() != null;
    }

    public static void log(String message) {
        if (getLogger() == null || !getLogger().isFineEnabled()) {
            return;
        }
        getLogger().logFine(message);
    }

    public static void logInfo(String message) {
        if (getLogger() == null || !getLogger().isInfoEnabled()) {
            return;
        }
        getLogger().logInfo(message);
    }

    public static void benchmark(String message) {
        if (getLogger() == null || !getLogger().isInfoEnabled()) {
            return;
        }
        getLogger().logInfo(message);
    }

    public static void error(String message) {
        if (getLogger() == null || !getLogger().isErrorEnabled()) {
            return;
        }
        getLogger().logError(message, null);
    }

    public static void error(Throwable th) {
        if (getLogger() == null || !getLogger().isErrorEnabled()) {
            return;
        }
        getLogger().logError(th.getMessage(), th);
    }

    public static LoggingInputStream getLoggingInputStream(String protocol,
            InputStream stream) {
        if (getLogger() == null) {
            return new LoggingInputStream(stream, null);
        }

        return getLogger().getLoggingInputStream(protocol, stream);
    }

    public static LoggingOutputStream getLoggingOutputStream(String protocol,
            OutputStream stream) {
        if (getLogger() == null) {
            return new LoggingOutputStream(stream, null);
        }

        return getLogger().getLoggingOutputStream(protocol, stream);
    }

    private static DebugLogger getLogger() {
        if (ourLogger == null) {
            ourLogger = new DebugDefaultLogger();
        }
        return ourLogger;
    }

}
