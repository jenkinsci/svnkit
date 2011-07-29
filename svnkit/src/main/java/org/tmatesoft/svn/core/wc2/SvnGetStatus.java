package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.hooks.ISvnFileListHook;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnGetStatus extends SvnReceivingOperation<SvnStatus> {
    
    private boolean remote;
    private boolean depthAsSticky;
    private boolean reportIgnored;
    private boolean reportAll;
    private boolean reportExternals;
    private ISvnFileListHook fileListHook;
    private boolean collectParentExternals;
    private long remoteRevision;

    protected SvnGetStatus(SvnOperationFactory factory) {
        super(factory);
    }

    public boolean isRemote() {
        return remote;
    }

    public boolean isDepthAsSticky() {
        return depthAsSticky;
    }

    public boolean isReportIgnored() {
        return reportIgnored;
    }

    public boolean isReportAll() {
        return reportAll;
    }

    public boolean isReportExternals() {
        return reportExternals;
    }
    
    public ISvnFileListHook getFileListHook() {
        return this.fileListHook;
    }

    public void setRemote(boolean remote) {
        this.remote = remote;
    }

    public void setDepthAsSticky(boolean depthAsSticky) {
        this.depthAsSticky = depthAsSticky;
    }

    public void setReportIgnored(boolean reportIgnored) {
        this.reportIgnored = reportIgnored;
    }

    public void setReportAll(boolean reportAll) {
        this.reportAll = reportAll;
    }

    public void setReportExternals(boolean reportExternals) {
        this.reportExternals = reportExternals;
    }
    
    public void setFileListHook(ISvnFileListHook fileListHook) {
        this.fileListHook = fileListHook;
    }
    
    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        setRemoteRevision(SVNWCContext.INVALID_REVNUM);
        if (hasRemoteTargets()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "''{0}'' is not a local path", getFirstTarget().getURL());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }

    @Override
    public void initDefaults() {
        super.initDefaults();
        setRevision(SVNRevision.HEAD);
        setReportAll(true);
        setReportIgnored(true);
        setReportExternals(true);
        setRemoteRevision(SVNWCContext.INVALID_REVNUM);
    }

    public boolean isCollectParentExternals() {
        return collectParentExternals;
    }
    
    /**
     * Only relevant for 1.6 working copies
     */
    public void setCollectParentExternals(boolean collect) {
        this.collectParentExternals = collect;
    }

    public void setRemoteRevision(long revision) {
        this.remoteRevision = revision;
    }
    
    public long getRemoteRevision() {
        return this.remoteRevision;
    }
}
