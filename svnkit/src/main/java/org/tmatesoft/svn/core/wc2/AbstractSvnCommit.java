package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc2.hooks.ISvnCommitHandler;

public abstract class AbstractSvnCommit extends SvnReceivingOperation<SVNCommitInfo> {

    private String commitMessage;
    private SVNProperties revisionProperties;
    private ISvnCommitHandler commitHandler;

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

    public ISvnCommitHandler getCommitHandler() {
        if (commitHandler == null) {
            commitHandler = new ISvnCommitHandler() {                
                public SVNProperties getRevisionProperties(String message, SvnCommitItem[] commitables, SVNProperties revisionProperties) throws SVNException {
                    return revisionProperties == null ? new SVNProperties() : revisionProperties;
                }                
                public String getCommitMessage(String message, SvnCommitItem[] commitables) throws SVNException {
                    return message == null ? "" : message;
                }
            };
        }
        return commitHandler;
    }

    public void setCommitHandler(ISvnCommitHandler commitHandler) {
        this.commitHandler = commitHandler;
    }

}
