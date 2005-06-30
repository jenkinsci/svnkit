package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;

public class SVNErrorManager {
    
    public static void error(int errorCode, Throwable reason) throws SVNException {
        // if reason already svn exception - prepend one more error and re-throw.
        if (reason != null) {
            DebugLog.error(reason);
        }
        throw reason != null ? new SVNException(reason) : new SVNException();
    }

    public static void error(String message) throws SVNException {
        throw new SVNException(message == null ? "" : message);
    }

    public static void error(Throwable reason) throws SVNException {
        DebugLog.error(reason);
        throw new SVNException("svn: " + reason.getMessage());
    }

}
