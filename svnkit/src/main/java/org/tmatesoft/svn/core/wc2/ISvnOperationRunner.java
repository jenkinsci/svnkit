package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNException;

public interface ISvnOperationRunner<T extends SvnOperation> {
    
    public boolean isApplicable(T operation) throws SVNException;
    
    public void run(T operation) throws SVNException;
}
