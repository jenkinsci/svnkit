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

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public interface ISVNGNUDiffGenerator extends ISVNDiffGenerator {
    
    public static final int ADDED = 0;
    public static final int DELETED = 1;
    public static final int MODIFIED = 2;
    public static final int COPIED = 3;
    public static final int NO_DIFF = 4;
    
    public void displayHeader(int type, String path, String copyFromPath, long copyFromRevision, OutputStream result) throws SVNException; 
    
}
