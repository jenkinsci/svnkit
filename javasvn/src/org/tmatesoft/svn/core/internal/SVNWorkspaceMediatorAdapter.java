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
import java.util.Map;

import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;


class SVNWorkspaceMediatorAdapter implements ISVNWorkspaceMediator {
    
    private ISVNWorkspaceMediator myMediator;
	private Map myCommitTree; 

    public SVNWorkspaceMediatorAdapter(ISVNWorkspaceMediator mediator,  Map commitTree) {
        myMediator = mediator;
        myCommitTree = commitTree;
    }

    public String getWorkspaceProperty(String path, String name) throws SVNException {
    	if (myCommitTree != null) {
    		ISVNEntry entry = (ISVNEntry) myCommitTree.get(path);
    		if (entry != null) {
    	        DebugLog.log("getting property for path (citem): " + entry.getPath());
    			return myMediator.getWorkspaceProperty(entry.getPath(), name);
    		}
    	}
        return null;
    }
    
    public void setWorkspaceProperty(String path, String name, String value) throws SVNException {
    	if (myCommitTree != null) {
    		ISVNEntry entry = (ISVNEntry) myCommitTree.get(path);
    		if (entry != null) {
    	        DebugLog.log("setting property for path (citem): " + entry.getPath());
    	        DebugLog.log("prop value: " + value);
    			myMediator.setWorkspaceProperty(entry.getPath(), name, value);
    		}
    	}
    }
    
    public OutputStream createTemporaryLocation(String path, Object id) throws IOException {
        if (myCommitTree != null) {
            ISVNEntry entry = (ISVNEntry) myCommitTree.get(path);
            if (entry != null) {
                path = entry.getPath();
            } else {
                path = null;
            }
        }
        return myMediator.createTemporaryLocation(path, id);
    }
    public void deleteAdminFiles(String path) {
        if (myCommitTree != null) {
            ISVNEntry entry = (ISVNEntry) myCommitTree.get(path);
            path = entry.getPath();
        }
        myMediator.deleteAdminFiles(path);
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