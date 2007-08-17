/*
 * ====================================================================
 * Copyright (c) 2004-2002 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNConflictAction {
    public static final SVNConflictAction EDIT = new SVNConflictAction();
    public static final SVNConflictAction ADD = new SVNConflictAction();
    public static final SVNConflictAction DELETE = new SVNConflictAction();
    
    private SVNConflictAction() {
    }
}
