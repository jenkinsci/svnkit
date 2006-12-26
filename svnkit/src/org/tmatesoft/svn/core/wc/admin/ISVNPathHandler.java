/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc.admin;

import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public interface ISVNPathHandler {

    public void handlePath(long revision, String path, String nodeID) throws SVNException;

    public void handlePath(String path, String nodeID, int treeDepth, boolean isDir) throws SVNException;

    public void handleDir(String path) throws SVNException;
    
}
