package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNLogEntry;

public class SvnLogMergeInfo extends SvnReceivingOperation<SVNLogEntry> {
    
    private boolean findMerged;
    private SvnTarget source;

    private boolean discoverChangedPaths;
    private String[] revisionProperties;

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
    
    protected SvnLogMergeInfo(SvnOperationFactory factory) {
        super(factory);
    }

}
