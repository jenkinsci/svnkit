package org.tmatesoft.svn.core.internal.wc;

import java.io.IOException;

import org.tmatesoft.svn.core.SVNException;


public class IOExceptionWrapper extends IOException {
    private SVNException myOriginalException;
    
    public IOExceptionWrapper(SVNException cause) {
        myOriginalException = cause;
    }

    public SVNException getOriginalException() {
        return myOriginalException;
    }
    
}
