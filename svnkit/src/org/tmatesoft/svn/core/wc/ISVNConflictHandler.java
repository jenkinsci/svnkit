/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNException;


/**
 * The <b>ISVNConflictHandler</b> interface 
 * 
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public interface ISVNConflictHandler {
   
    /**
     * 
     * @param  conflictDescription 
     * @return 
     * @throws SVNException 
     */
    public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription) throws SVNException;
}
