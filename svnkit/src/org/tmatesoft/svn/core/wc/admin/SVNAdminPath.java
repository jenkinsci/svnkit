/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc.admin;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNAdminPath {
    private String myPath;
    private String myNodeID;
    private long myRevision;
    private int myTreeDepth;
    private boolean myIsDir;
    
    /**
     * @param path
     * @param nodeID
     * @param revision
     */
    public SVNAdminPath(String path, String nodeID, long revision) {
        super();
        myPath = path;
        myNodeID = nodeID;
        myRevision = revision;
    }

    /**
     * @param path
     * @param nodeID
     * @param treeDepth
     * @param isDir
     */
    public SVNAdminPath(String path, String nodeID, int treeDepth, boolean isDir) {
        myPath = path;
        myNodeID = nodeID;
        myTreeDepth = treeDepth;
        myIsDir = isDir;
    }

    
    public boolean isDir() {
        return myIsDir;
    }

    
    public String getNodeID() {
        return myNodeID;
    }

    
    public String getPath() {
        return myPath;
    }

    
    public long getRevision() {
        return myRevision;
    }

    
    public int getTreeDepth() {
        return myTreeDepth;
    }
    
    
}
