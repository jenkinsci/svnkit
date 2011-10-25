package org.tmatesoft.svn.core.wc2;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;

public class SvnLog extends SvnReceivingOperation<SVNLogEntry> {
    
    private long limit;
    private boolean useMergeHistory;
    private boolean stopOnCopy;
    private boolean discoverChangedPaths;
    private String[] targetPaths;
    private String[] revisionProperties;
    
    
    private Collection<SVNRevisionRange> revisionRanges;
    
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
    
    public boolean isDiscoverChangedPaths() {
        return discoverChangedPaths;
    }

    public void setDiscoverChangedPaths(boolean discoverChangedPaths) {
        this.discoverChangedPaths = discoverChangedPaths;
    }

    public boolean isStopOnCopy() {
        return stopOnCopy;
    }

    public void setStopOnCopy(boolean stopOnCopy) {
        this.stopOnCopy = stopOnCopy;
    }
    
    public Collection<SVNRevisionRange> getRevisionRanges()
    {
    	return revisionRanges;
    }
    
    public void setRevisionRanges(Collection<SVNRevisionRange> revisionRanges)
    {
    	this.revisionRanges = revisionRanges;
    }
    
    public String[] getTargetPaths() {
		return targetPaths;
	}

	public void setTargetPaths(String[] targetPaths) {
		this.targetPaths = targetPaths;
	}
	
	public String[] getRevisionProperties() {
		return revisionProperties;
	}

	public void setRevisionProperties(String[] revisionProperties) {
		this.revisionProperties = revisionProperties;
	}
	
	
    
    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        
        if (getLimit() > Integer.MAX_VALUE) {
            setLimit(Integer.MAX_VALUE);
        }
    }

	
    
    

    
}
