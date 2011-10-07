package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;

public abstract class AbstractSvnCommit extends SvnOperation<SVNCommitInfo> {

    private String commitMessage;
    private SVNProperties revisionProperties;

    protected AbstractSvnCommit(SvnOperationFactory factory) {
        super(factory);
        setRevisionProperties(new SVNProperties());
    }

    public SVNProperties getRevisionProperties() {
        return revisionProperties;
    }

    public void setRevisionProperties(SVNProperties revisionProperties) {
        this.revisionProperties = revisionProperties;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public void setRevisionProperty(String name, SVNPropertyValue value) {
        if (value != null) {
            getRevisionProperties().put(name, value);
        } else {
            getRevisionProperties().remove(name);
        }
    }

}
