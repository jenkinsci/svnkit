/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public interface ISVNExternalsHandler {

    public void handleStatus(ISVNWorkspace parent, String path,
            ISVNWorkspace external, ISVNStatusHandler statusHandler,
            boolean remote, boolean descend, boolean includeUnmodified,
            boolean includeIgnored, boolean descendInUnversioned)
            throws SVNException;

    public void handleCheckout(ISVNWorkspace parent, String path,
            ISVNWorkspace external, SVNRepositoryLocation location,
            long revision, boolean export, boolean recurse) throws SVNException;

    public void handleUpdate(ISVNWorkspace parent, String path,
            ISVNWorkspace external, long revision) throws SVNException;

}
