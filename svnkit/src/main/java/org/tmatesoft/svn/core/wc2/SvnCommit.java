package org.tmatesoft.svn.core.wc2;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;

/**
 * @author alex
 *
 */
public class SvnCommit extends SvnOperation<Collection<SVNCommitInfo>> {
    
    private SVNProperties revisionProperties;
    
    private boolean keepChangelists;
    private boolean keepLocks;
    private String commitMessage;
    
    private SvnCommitPacket packet;

    protected SvnCommit(SvnOperationFactory factory) {
        super(factory);
        revisionProperties = new SVNProperties();
    }
    
    public void setRevisionProperty(String name, SVNPropertyValue value) {
        if (value != null) {
            revisionProperties.put(name, value);
        } else {
            revisionProperties.remove(name);
        }
    }

    public boolean isKeepChangelists() {
        return keepChangelists;
    }

    public boolean isKeepLocks() {
        return keepLocks;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setKeepChangelists(boolean keepChangelists) {
        this.keepChangelists = keepChangelists;
    }

    public void setKeepLocks(boolean keepLocks) {
        this.keepLocks = keepLocks;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }
    
    public SvnCommitPacket collectCommitItems() throws SVNException {
        ensureArgumentsAreValid();        
        if (packet != null) {
            return packet;
        }
        packet = getOperationFactory().collectCommitItems(this);
        return packet;
    }
    
    public Collection<SVNCommitInfo> run() throws SVNException {
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

    public SVNProperties getRevisionProperties() {
        return revisionProperties;
    }
    
}
