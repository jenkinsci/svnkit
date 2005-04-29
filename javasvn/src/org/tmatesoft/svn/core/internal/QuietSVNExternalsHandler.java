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

import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.io.DefaultSVNExternalsHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
class QuietSVNExternalsHandler extends DefaultSVNExternalsHandler {

    public void handleStatus(ISVNWorkspace parent, final String path,
            ISVNWorkspace external, final ISVNStatusHandler statusHandler,
            boolean remote, boolean descend, boolean includeUnmodified,
            boolean includeIgnored, boolean descendInUnversioned) {
        try {
            super.handleStatus(parent, path, external, statusHandler, remote,
                    descend, includeUnmodified, includeIgnored,
                    descendInUnversioned);
        } catch (SVNException ex) {
            DebugLog.error(ex);
        }
    }

    public void handleCheckout(ISVNWorkspace parent, String path,
            ISVNWorkspace external, SVNRepositoryLocation location,
            long revision, boolean export, boolean recurse) {
        try {
            super.handleCheckout(parent, path, external, location, revision,
                    export, recurse);
        } catch (SVNException ex) {
            DebugLog.error(ex);
        }
    }

    public void handleUpdate(final ISVNWorkspace parent, final String path,
            ISVNWorkspace external, long revision) {
        try {
            super.handleUpdate(parent, path, external, revision);
        } catch (SVNException ex) {
            DebugLog.error(ex);
        }
    }
}