package org.tmatesoft.svn.core.wc2;

import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnLog extends SvnReceivingOperation<SVNLogEntry> {
    
    private long limit;
    private boolean useMergeHistory;
    private boolean stopOnCopy;
    private boolean discoverChangedPaths;
    private String[] targetPaths;
    private String[] revisionProperties;
    
    
    private Collection<SvnRevisionRange> revisionRanges;
    
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
    
    public Collection<SvnRevisionRange> getRevisionRanges()
    {
    	return revisionRanges;
    }
    
    public void setRevisionRanges(Collection<SvnRevisionRange> revisionRanges)
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
        
        if (getLimit() > Long.MAX_VALUE) {
            setLimit(Long.MAX_VALUE);
        }
        
        if (getRevisionRanges() == null || getRevisionRanges().size() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Missing required revision specification");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (hasRemoteTargets() && getTargets().size() > 1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "When specifying URL, only one target may be given.");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        }
    }

    public void addRange(SvnRevisionRange range) {
        if (range != null) {
            if (getRevisionRanges() == null) {
                this.revisionRanges = new ArrayList<SvnRevisionRange>();
            }
            this.revisionRanges.add(range);
        }
    }
}
