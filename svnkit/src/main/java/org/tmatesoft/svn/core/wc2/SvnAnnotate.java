package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;

public class SvnAnnotate extends SvnOperation<Long> {
    
    private boolean useMergeHistory;
    private boolean force;
    private ISVNAnnotateHandler handler;

    protected SvnAnnotate(SvnOperationFactory factory) {
        super(factory);
    }
    
    public ISVNAnnotateHandler getHandler() {
        return handler;
    }

    public void setHandler(ISVNAnnotateHandler handler) {
        this.handler = handler;
    }

    public boolean isUseMergeHistory() {
        return useMergeHistory;
    }

    public void setUseMergeHistory(boolean useMergeHistory) {
        this.useMergeHistory = useMergeHistory;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

}
