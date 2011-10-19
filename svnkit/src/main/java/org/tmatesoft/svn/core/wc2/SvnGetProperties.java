package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnGetProperties extends SvnReceivingOperation<SVNProperties> {

    private boolean revisionProperties;
    private long revisionNumber;

    protected SvnGetProperties(SvnOperationFactory factory) {
        super(factory);
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        if (getDepth() == SVNDepth.UNKNOWN) {
            setDepth(SVNDepth.EMPTY);
        }
        if (getPegRevision() == null || !getPegRevision().isValid()) {
            setPegRevision(hasRemoteTargets() ? SVNRevision.HEAD : SVNRevision.WORKING);
        }
        if (getRevision() == null || !getRevision().isValid()) {
            setRevision(getPegRevision());
        }
        super.ensureArgumentsAreValid();
    }

    public boolean isRevisionProperties() {
        return revisionProperties;
    }

    public void setRevisionProperties(boolean revisionProperties) {
        this.revisionProperties = revisionProperties;
    }

    public long getRevisionNumber() {
        return revisionNumber;
    }
    
    public void setRevisionNumber(long revisionNumber) {
        this.revisionNumber = revisionNumber;
    }
    
    
}
