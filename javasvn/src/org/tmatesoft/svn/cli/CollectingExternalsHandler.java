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

package org.tmatesoft.svn.cli;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNExternalsHandler;
import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

/**
 * @author TMate Software Ltd.
 */
public class CollectingExternalsHandler implements ISVNExternalsHandler {
    
    private Map myExternals = new HashMap();

    public void handleStatus(ISVNWorkspace parent, String path, ISVNWorkspace external, ISVNStatusHandler statusHandler, boolean remote, boolean descend,
            boolean includeUnmodified, boolean includeIgnored, boolean descendInUnversioned) throws SVNException {
        myExternals.put(path, external);
    }

    public void handleCheckout(ISVNWorkspace parent, String path, ISVNWorkspace external, SVNRepositoryLocation location, long revision, boolean export,
            boolean recurse) throws SVNException {
        myExternals.put(path, external);
    }

    public void handleUpdate(ISVNWorkspace parent, String path, ISVNWorkspace external, long revision) throws SVNException {
        myExternals.put(path, external);
    }

    public Iterator externalPaths() {
        return myExternals.keySet().iterator();
    }
    
    public ISVNWorkspace getExternalWorkspace(String path) {
        return (ISVNWorkspace) myExternals.get(path);
    }
}
