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

/**
 * @author Alexander Kitaev
 */
public class SVNLogEntryPath {
    
    private String myPath;
    private char myType;
    private String myCopyPath;
    private long myCopyRevision;
    
    public SVNLogEntryPath(String path, char type, String copyPath,
            long copyRevision) {
        myPath = path;
        myType = type;
        myCopyPath = copyPath;
        myCopyRevision = copyRevision;
    }
    public String getCopyPath() {
        return myCopyPath;
    }
    public long getCopyRevision() {
        return myCopyRevision;
    }
    public String getPath() {
        return myPath;
    }
    public char getType() {
        return myType;
    }
    
    protected void setPath(String path) {
    	myPath = path;
    }
}
