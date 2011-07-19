package org.tmatesoft.svn.core.internal.wc2;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc2.ISvnOperationRunner;
import org.tmatesoft.svn.core.wc2.SvnOperation;

public abstract class SvnRemoteOperationRunner implements ISvnOperationRunner {

    public boolean isApplicable(SvnOperation operation) throws SVNException {
        if (operation == null || operation.getTarget() == null) {
            return false;
        }        
        return operation.getTarget().isURL();
    }
    
    
}
