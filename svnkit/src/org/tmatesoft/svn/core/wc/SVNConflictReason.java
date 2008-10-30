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
 * The <b>SVNConflictReason</b> class represents an enumeration of constants describing the reason of a 
 * conflict state in a working copy produced by a merge operation. 
 *  
 * @version 1.2.0
 * @author  TMate Software Ltd.
 * @since   1.2.0
 */
public class SVNConflictReason {
    /**
     * Constant saying that local edits are already present. 
     */
    public static final SVNConflictReason EDITED = new SVNConflictReason();
    /**
     * Constant saying that another object is in the way.
     */
    public static final SVNConflictReason OBSTRUCTED = new SVNConflictReason();
    /**
     * Constant saying that an object is already schedule-delete.
     */
    public static final SVNConflictReason DELETED = new SVNConflictReason();
    /**
     * Constant saying that an object is unknown or missing. Reserved (never passed currently).
     */
    public static final SVNConflictReason MISSING = new SVNConflictReason();
    /**
     * Constant saying that an object is unversioned. Reserved (never passed currently).
     */
    public static final SVNConflictReason UNVERSIONED = new SVNConflictReason();
    
    private SVNConflictReason() {
    }
}
