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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;


class SVNWorkspaceMediatorAdapter implements ISVNWorkspaceMediator {
    
    private ISVNWorkspaceMediator myMediator;
	private String myRootPath; 

    public SVNWorkspaceMediatorAdapter(ISVNWorkspaceMediator mediator, String rootPath) {
        myMediator = mediator;
        myRootPath = rootPath;
    }

    public String getWorkspaceProperty(String path, String name) throws SVNException {
        DebugLog.log("fetching property for path: " + path);
        path = PathUtil.append(myRootPath, path);
        DebugLog.log("real path: " + path);
        return myMediator.getWorkspaceProperty(path, name);
    }
    public void setWorkspaceProperty(String path, String name, String value) throws SVNException {
        DebugLog.log("setting property for path: " + path);
        path = PathUtil.append(myRootPath, path);
        DebugLog.log("real path: " + path);
        myMediator.setWorkspaceProperty(path, name, value);
    }
    public OutputStream createTemporaryLocation(Object id) throws IOException {
        return myMediator.createTemporaryLocation(id);
    }
    public InputStream getTemporaryLocation(Object id) throws IOException {
        return myMediator.getTemporaryLocation(id);
    }
    public long getLength(Object id) throws IOException {
        return myMediator.getLength(id);
    }
    public void deleteTemporaryLocation(Object id) {
        myMediator.deleteTemporaryLocation(id);
    }
}