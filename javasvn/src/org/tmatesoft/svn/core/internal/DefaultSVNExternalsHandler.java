/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal;

import org.tmatesoft.svn.core.ISVNExternalsHandler;
import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
class DefaultSVNExternalsHandler implements ISVNExternalsHandler {

    public void handleStatus(ISVNWorkspace parent, final String path, ISVNWorkspace external, final ISVNStatusHandler statusHandler,
            boolean remote, boolean descend,
            boolean includeUnmodified, boolean includeIgnored, boolean descendInUnversioned) throws SVNException {
        external.status("", remote, new ISVNStatusHandler() {
            public void handleStatus(String p,SVNStatus status) {
                p = PathUtil.append(path, p);
                status.setPath(p);
                statusHandler.handleStatus(p, status);
            }
        }, descend, includeUnmodified, includeIgnored); 
        
    }

    public void handleCheckout(ISVNWorkspace parent, String path, ISVNWorkspace external, SVNRepositoryLocation location, long revision, boolean export, boolean recurse) throws SVNException {
        external.checkout(location, revision, export);
    }

    public void handleUpdate(ISVNWorkspace parent, String path, ISVNWorkspace external, long revision) throws SVNException {
        external.update("", revision, true);
    }

}
