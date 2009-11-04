/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.db;

import java.io.File;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNPristineDirectory {
    private SVNWCRoot myWCRoot;
    private SVNPristineDirectory myParentDirectory;
    private boolean myIsLocked;
    private boolean myIsObstructedFile;
    private File myPath;
    
    public SVNWCRoot getWCRoot() {
        return myWCRoot;
    }
    
    public SVNPristineDirectory getParentDirectory() {
        return myParentDirectory;
    }
    
    public boolean isIsLocked() {
        return myIsLocked;
    }
    
    public boolean isIsObstructedFile() {
        return myIsObstructedFile;
    }
    
    public File getPath() {
        return myPath;
    }
    
}
