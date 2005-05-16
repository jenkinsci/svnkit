package org.tmatesoft.svn.core.internal.ws.log;

import org.tmatesoft.svn.core.io.SVNException;

public class SVNErrorManager {
    
    public static void error(int errorCode, Throwable reason) throws SVNException {
        // if reason already svn exception - prepend one more error and re-throw. 
        throw new SVNException();
    }

}
