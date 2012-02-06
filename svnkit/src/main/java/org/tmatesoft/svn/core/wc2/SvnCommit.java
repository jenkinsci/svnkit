package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;

/**
 * @author alex
 *
 */
public class SvnCommit extends AbstractSvnCommit {
    
    private boolean keepChangelists;
    private boolean keepLocks;
    private ISVNCommitParameters commitParameters;
    
    private SvnCommitPacket packet;

    protected SvnCommit(SvnOperationFactory factory) {
        super(factory);
    }
    
    public boolean isKeepChangelists() {
        return keepChangelists;
    }

    public boolean isKeepLocks() {
        return keepLocks;
    }

    public void setKeepChangelists(boolean keepChangelists) {
        this.keepChangelists = keepChangelists;
    }

    public void setKeepLocks(boolean keepLocks) {
        this.keepLocks = keepLocks;
    }

    public ISVNCommitParameters getCommitParameters() {
        return commitParameters;
    }

    public void setCommitParameters(ISVNCommitParameters commitParameters) {
        this.commitParameters = commitParameters;
    }
    
    public SvnCommitPacket collectCommitItems() throws SVNException {
        ensureArgumentsAreValid();        
        if (packet != null) {
            return packet;
        }
        packet = getOperationFactory().collectCommitItems(this);
        return packet;
    }
    
    public SVNCommitInfo run() throws SVNException {
        if (packet == null) {
            packet = collectCommitItems();
        }
        return super.run();
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        if (getDepth() == SVNDepth.UNKNOWN) {
            setDepth(SVNDepth.INFINITY);
        }
    }

    @Override
    protected int getMaximumTargetsCount() {
        return Integer.MAX_VALUE;
    }
}
