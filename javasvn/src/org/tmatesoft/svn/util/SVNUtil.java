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

package org.tmatesoft.svn.util;

import java.io.File;

import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNWorkspaceManager;
import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

/**
 * @author TMate Software Ltd.
 */
public class SVNUtil {
    
    public static ISVNWorkspace createWorkspace(String filePath) throws SVNException {
        return createWorkspace(filePath, true);
    }
    
    public static ISVNWorkspace createWorkspace(String filePath, boolean root) throws SVNException {
        File file = new File(filePath);
        if (file.exists() && !file.isDirectory()) {
            file = file.getAbsoluteFile().getParentFile();
            if (file == null) { 
                return null;
            }
        }
        filePath = file.getAbsolutePath();
        ISVNWorkspace ws = SVNWorkspaceManager.createWorkspace("file", filePath);
        if (root && ws != null) {
            return ws.getRootWorkspace(true, false);
        } 
        return ws;
    }

    public static String getWorkspacePath(ISVNWorkspace ws, String absolutePath) {
        File file = new File(absolutePath);
        
        String root = new File(ws.getID()).getAbsolutePath();
        root = root.replace(File.separatorChar, '/');
        String path = file.getAbsolutePath().replace(File.separatorChar, '/');
        if (FSUtil.isWindows) {
            if (path.toLowerCase().startsWith(root.toLowerCase())) {
                path = path.substring(root.length());
            }
        } else {
            if (path.startsWith(root)) {
                path = path.substring(root.length());
            }
        }
        path = PathUtil.removeLeadingSlash(path);
        if (".".equals(path)) {
            path = "";
        }
        return path;
    }
    
    public static String getAbsolutePath(ISVNWorkspace ws, String relativePath) {
        return PathUtil.append(ws.getID(), relativePath);
    }
    
    public static SVNRepository createRepository(ISVNWorkspace ws, String relativePath) throws SVNException {
        SVNRepositoryLocation location = ws.getLocation(relativePath);
        SVNRepository repository = null;
        if (location != null) {
            repository = SVNRepositoryFactory.create(location);
            repository.setCredentialsProvider(ws.getCredentialsProvider());
        }
        return repository;
    }

}
