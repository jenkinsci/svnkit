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


/**
 * The <b>SVNConflictAction</b> represents the type of action being attempted on an object which leads to 
 * a conflict.  
 * 
 * @version 1.2.0
 * @author  TMate Software Ltd.
 * @since   1.2.0
 */
public class SVNConflictAction {
    /**
     * Constant representing an attempt to change text or props.
     */
    public static final SVNConflictAction EDIT = new SVNConflictAction();
    /**
     * Constant representing an attempt to add an object.
     */
    public static final SVNConflictAction ADD = new SVNConflictAction();
    /**
     * Constant representing an attempt to delete an object.
     */
    public static final SVNConflictAction DELETE = new SVNConflictAction();
    
    private SVNConflictAction() {
    }
}
