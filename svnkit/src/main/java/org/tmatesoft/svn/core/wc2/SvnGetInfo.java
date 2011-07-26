package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnGetInfo extends SvnReceivingOperation<SvnInfo> {

    protected SvnGetInfo(SvnOperationFactory factory) {
        super(factory);
    }

    private boolean fetchExcluded;
    private boolean fetchActualOnly;
    
    @Override
    public void initDefaults() {
        super.initDefaults();
        setFetchActualOnly(true);
        setFetchExcluded(true);
    }

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

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        if (getPegRevision() == null) {
            setPegRevision(hasRemoteTargets() ? SVNRevision.HEAD : SVNRevision.UNDEFINED);
        }
        if (getRevision() == null) {
            setRevision(hasRemoteTargets() ? SVNRevision.HEAD : SVNRevision.UNDEFINED);
        }
        if (getDepth() == null || getDepth() == SVNDepth.UNKNOWN) {
            setDepth(SVNDepth.EMPTY);
        }
        
        super.ensureArgumentsAreValid();
    }
    
    
}
