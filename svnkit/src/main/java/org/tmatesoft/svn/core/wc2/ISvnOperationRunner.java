package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;

public interface ISvnOperationRunner<V, T extends SvnOperation<V>> {
    
    public boolean isApplicable(T operation, SvnWcGeneration wcGeneration) throws SVNException;
    
    public V run(T operation) throws SVNException;
    
    public void setWcContext(SVNWCContext context);
    
    public void reset();
    
    public SvnWcGeneration getWcGeneration();

}
