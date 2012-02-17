package org.tmatesoft.svn.core.wc2;

public class SvnGetStatusSummary extends SvnOperation<SvnStatusSummary> {

    private String trailUrl;
    private boolean isCommitted;

    protected SvnGetStatusSummary(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isCommitted() {
        return isCommitted;
    }

    public void setCommitted(boolean isCommitted) {
        this.isCommitted = isCommitted;
    }

    public String getTrailUrl() {
        return trailUrl;
    }

    public void setTrailUrl(String trailUrl) {
        this.trailUrl = trailUrl;
    }

}
