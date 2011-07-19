package org.tmatesoft.svn.core.wc2;

public class SvnGetInfo extends SvnReceivingOperation<SvnInfo> {

    protected SvnGetInfo(SvnOperationFactory factory) {
        super(factory);
    }

    private boolean fetchExcluded;
    private boolean fetchActualOnly;
    
    public void setFetchExcluded(boolean fetchExcluded) {
        this.fetchExcluded = fetchExcluded;
    }

    public void setFetchActualOnly(boolean fetchActualOnly) {
        this.fetchActualOnly = fetchActualOnly;
    }
    
    public boolean isFetchExcluded() {
        return fetchExcluded;
    }
    
    public boolean isFetchActualOnly() {
        return fetchActualOnly;
    }
}
