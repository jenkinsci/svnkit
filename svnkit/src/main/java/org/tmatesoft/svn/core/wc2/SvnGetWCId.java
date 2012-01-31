package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNException;

public class SvnGetWCId extends SvnOperation<String>{

    private String trailUrl;
    private boolean committed;

    protected SvnGetWCId(SvnOperationFactory factory) {
        super(factory);
    }

    public String getTrailUrl() {
        return trailUrl;
    }

    public void setTrailUrl(String trailUrl) {
        this.trailUrl = trailUrl;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();

        //TODO: check that target is path
    }
}
