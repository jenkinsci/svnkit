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
public interface DebugLogger {
    public boolean isFineEnabled();

    public boolean isInfoEnabled();

    public void logFine(String message);

    public void logInfo(String message);

    public boolean isErrorEnabled();

    public void logError(String message, Throwable th);

    public LoggingInputStream getLoggingInputStream(String protocol,
            InputStream stream);

    public LoggingOutputStream getLoggingOutputStream(String protocol,
            OutputStream stream);
}