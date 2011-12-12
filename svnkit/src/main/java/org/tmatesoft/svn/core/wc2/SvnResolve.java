package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc.SVNConflictChoice;

public class SvnResolve extends SvnOperation<Long> {

    private SVNConflictChoice accept;
    
    protected SvnResolve(SvnOperationFactory factory) {
        super(factory);
    }

    public SVNConflictChoice getAccept() {
        return accept;
    }

    public void setAccept(SVNConflictChoice accept) {
        this.accept = accept;
    }

}
