/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.File;
import java.util.Iterator;

import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public abstract class SVNAdminArea {
    
    public static SVNAdminArea MISSING = new SVNAdminArea(null) {
        public Iterator entries() throws SVNException {
            return null;
        }
        public void lock() {
        }
    };
    
    private File myRoot;
    
    protected SVNAdminArea(File root) {
        myRoot = root;
    }
    
    public File getRoot() {
        return myRoot;
    }

    public abstract void lock();

    public abstract Iterator entries() throws SVNException;

}
