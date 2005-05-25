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

package org.tmatesoft.svn.core.io;

import java.util.Iterator;

import org.tmatesoft.svn.core.ISVNExternalsHandler;
import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.ISVNWorkspaceListener;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.SVNWorkspaceAdapter;
import org.tmatesoft.svn.core.internal.SVNWorkspace;
import org.tmatesoft.svn.util.PathUtil;

/**
 * <code>DefaultSVNExternalsHandler</code> is a default implementation of the
 * <code>ISVNExternalsHandler</code> interface that provides a capability to perform
 * an externals-dependent command. That is if any versioned directory has the 
 * "svn:externals" property set it means that its contents should be actually filled up 
 * according to the value pairs 
 * (for instance: <i>/path/within/this/dir - http://realhost/real/path</i>) 
 * of that property when a user performs a checkout, update or status. For example,
 * if the user is checking out a copy and one of its directories has the "svn:externals"
 * property set with a value pair like 
 * <i>/path/within/this/dir - http://realhost/real/path</i> the URL in this pair will
 * be fetched (checked out) into <i>/path/within/this/dir</i>. 
 *     
 * @version	1.0
 * @author 	TMate Software Ltd.
 * 
 */
public class DefaultSVNExternalsHandler implements ISVNExternalsHandler {
    /**
     * Gets status displaying a status code of X for the disjoint subdirectories into 
     * which externals are checked out.
     * 
     * @param  parent
     * @param  path						a path to be examined
     * @param  external
     * @param  statusHandler
     * @param  remote					if a status is performed against a repository
     * @param  descend					
     * @param  includeUnmodified		<code>true</code> if a status is also to include 
     * 									all unmodified entries  
     * @param  includeIgnored			<code>true</code> if a status is also to include 
     * 									all unversioned and set to be ignored entries
     * @param  descendInUnversioned		
     * @throws SVNException
     */
    public void handleStatus(ISVNWorkspace parent, final String path,
            ISVNWorkspace external, final ISVNStatusHandler statusHandler,
            boolean remote, boolean descend, boolean includeUnmodified,
            boolean includeIgnored, boolean descendInUnversioned)
            throws SVNException {
        external.status("", remote, new StatusHandler(path, statusHandler),
                descend, includeUnmodified, includeIgnored);
    }
    
    /**
     * Performs an external checkout.
     * 
     * @param  parent
     * @param  path
     * @param  external
     * @param  location
     * @param  revision
     * @param  export
     * @param  recurse
     * @throws SVNException
     */
    public void handleCheckout(ISVNWorkspace parent, String path,
            ISVNWorkspace external, SVNRepositoryLocation location,
            long revision, boolean export, boolean recurse) throws SVNException {
        SVNWorkspaceAdapter listener = new UpdateListener(path,
                (SVNWorkspace) parent);
        external.addWorkspaceListener(listener);
        external.checkout(location, revision, export);
        external.removeWorkspaceListener(listener);
    }
    
    /**
     * Performs an external update.
     * 
     * @param  parent
     * @param  path
     * @param  external
     * @param  revision
     * @throws SVNException
     */
    public void handleUpdate(final ISVNWorkspace parent, final String path,
            ISVNWorkspace external, long revision) throws SVNException {
        SVNWorkspaceAdapter listener = new UpdateListener(path,
                (SVNWorkspace) parent);
        external.addWorkspaceListener(listener);
        external.update("", revision, true);
        external.removeWorkspaceListener(listener);
    }

    private static class StatusHandler implements ISVNStatusHandler {
        private final String path;

        private final ISVNStatusHandler statusHandler;

        public StatusHandler(String path, ISVNStatusHandler statusHandler) {
            this.path = path;
            this.statusHandler = statusHandler;
        }

        public void handleStatus(String p, SVNStatus status) {
            p = PathUtil.append(path, p);
            status.setPath(p);
            statusHandler.handleStatus(p, status);
        }
    }

    private static final class UpdateListener extends SVNWorkspaceAdapter {

        private final String myPath;

        private final SVNWorkspace myParentWorkspace;

        private UpdateListener(String path, SVNWorkspace parent) {
            myPath = path;
            myParentWorkspace = parent;
        }

        public void updated(String externalPath, int contentsStatus,
                int propertiesStatus, long rev) {
            externalPath = PathUtil.append(myPath, externalPath);
            for (Iterator it = myParentWorkspace.getWorkspaceListeners()
                    .iterator(); it.hasNext();) {
                final ISVNWorkspaceListener listener = (ISVNWorkspaceListener) it
                        .next();
                listener.updated(externalPath, contentsStatus,
                        propertiesStatus, rev);
            }
        }
    }
}