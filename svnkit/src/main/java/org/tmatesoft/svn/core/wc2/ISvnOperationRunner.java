package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNException;

public interface ISvnOperationRunner {
    
    public boolean isApplicable(SvnOperation operation) throws SVNException;
    
    public void run(SvnOperation operation) throws SVNException;
}
