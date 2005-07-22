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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNErrorManager {

    public static void error(String message) throws SVNException {
        if (message == null) {
            message = "svn: unknow error";
        } else if (!message.startsWith("svn: ")) {
            message = "svn: " + message;
        }
        SVNDebugLog.log(message);
        throw new SVNException(message);
    }
}
