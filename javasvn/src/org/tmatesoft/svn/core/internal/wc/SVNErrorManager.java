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
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNErrorManager {

    public static void error(int errorCode, Throwable reason)
            throws SVNException {
        error(reason);
    }

    public static void error(String message) throws SVNException {
        if (message == null) {
            message = "svn: unknow error";
        } else if (!message.startsWith("svn: ")) {
            message = "svn: " + message;
        }
        DebugLog.error(message);
        throw new SVNException(message);
    }

    public static void error(Throwable reason) throws SVNException {
        if (reason == null) {
            DebugLog.error(new Exception());
            throw new SVNException("svn: unknown error");
        }
        DebugLog.error(reason);
        if (reason instanceof SVNException
                && reason.getMessage().startsWith("snv: ")) {
            throw (SVNException) reason;
        }
        String message = reason.getMessage();
        message = message == null ? "svn: unknown error" : message;
        if (!message.startsWith("svn: ")) {
            message = "svn: " + message;
        }
        throw new SVNException(message);
    }

}
