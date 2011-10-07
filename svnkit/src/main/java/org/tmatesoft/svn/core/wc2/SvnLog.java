package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnLog extends SvnReceivingOperation<SVNLogEntry> {
    
    private long limit;
    private boolean useMergeHistory;
    private boolean stopOnCopy;
    
    private SVNRevision startRevision;
    private SVNRevision endRevision;

    protected SvnLog(SvnOperationFactory factory) {
        super(factory);
    }

    public long getLimit() {
        return limit;
    }

    public void setLimit(long limit) {
        this.limit = limit;
    }

    public boolean isUseMergeHistory() {
        return useMergeHistory;
    }

    public void setUseMergeHistory(boolean useMergeHistory) {
        this.useMergeHistory = useMergeHistory;
    }

    public boolean isStopOnCopy() {
        return stopOnCopy;
    }

    public void setStopOnCopy(boolean stopOnCopy) {
        this.stopOnCopy = stopOnCopy;
    }

    public SVNRevision getStartRevision() {
        return startRevision;
    }

    public void setStartRevision(SVNRevision startRevision) {
        this.startRevision = startRevision;
    }

    public SVNRevision getEndRevision() {
        return endRevision;
    }

    public void setEndRevision(SVNRevision endRevision) {
        this.endRevision = endRevision;
    }

}
