package org.tmatesoft.svn.core.internal.wc17;

import org.tmatesoft.svn.core.SVNException;

public interface ISVNStatus17Handler {
    public void handleStatus(SVNStatus17 status) throws SVNException;
}
