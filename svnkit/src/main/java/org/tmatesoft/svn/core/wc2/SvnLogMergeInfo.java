package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnLogMergeInfo extends SvnReceivingOperation<SVNLogEntry> {
    
    private boolean findMerged;
    private SvnTarget source;

    private boolean discoverChangedPaths;
    private String[] revisionProperties;
    
    protected SvnLogMergeInfo(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isFindMerged() {
        return findMerged;
    }

    public void setFindMerged(boolean findMerged) {
        this.findMerged = findMerged;
    }

    public SvnTarget getSource() {
        return source;
    }

    public void setSource(SvnTarget source) {
        this.source = source;
    }

    public boolean isDiscoverChangedPaths() {
        return discoverChangedPaths;
    }

    public void setDiscoverChangedPaths(boolean discoverChangedPaths) {
        this.discoverChangedPaths = discoverChangedPaths;
    }

    public String[] getRevisionProperties() {
        return revisionProperties;
    }

    public void setRevisionProperties(String[] revisionProperties) {
        this.revisionProperties = revisionProperties;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        if (getDepth() == null || getDepth() == SVNDepth.UNKNOWN) {
            setDepth(SVNDepth.EMPTY);
        }
        super.ensureArgumentsAreValid();
        if (getDepth() != SVNDepth.INFINITY && getDepth() != SVNDepth.EMPTY) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Only depths 'infinity' and 'empty' are currently supported");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }
    
    
}
