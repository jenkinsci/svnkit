package org.tmatesoft.svn.core.wc2.hooks;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.wc2.SvnCommitItem;

public interface ISvnCommitHandler {
    
    public String getCommitMessage(String message, SvnCommitItem[] commitables) throws SVNException;

    public SVNProperties getRevisionProperties(String message, SvnCommitItem[] commitables, SVNProperties revisionProperties) throws SVNException;
}
