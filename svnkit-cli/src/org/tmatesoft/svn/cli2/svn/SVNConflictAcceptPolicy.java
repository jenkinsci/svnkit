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
package org.tmatesoft.svn.cli2.svn;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNConflictAcceptPolicy {
    public static SVNConflictAcceptPolicy INVALID = new SVNConflictAcceptPolicy("invalid");  
    /**
     * Resolve the conflict with the pre-conflict base file.
     */
    public static SVNConflictAcceptPolicy BASE = new SVNConflictAcceptPolicy("base");
    /**
     * Resolve the conflict with the pre-conflict working copy file.
     */
    public static SVNConflictAcceptPolicy MINE = new SVNConflictAcceptPolicy("mine");
    /**
     * Resolve the conflict with the post-conflict base file.
     */
    public static SVNConflictAcceptPolicy THEIRS = new SVNConflictAcceptPolicy("theirs");

    public static SVNConflictAcceptPolicy POSTPONE = new SVNConflictAcceptPolicy("postpone");
    
    public static SVNConflictAcceptPolicy EDIT = new SVNConflictAcceptPolicy("edit");
    
    public static SVNConflictAcceptPolicy LAUNCH = new SVNConflictAcceptPolicy("launch");
    
    private String myName;
    private SVNConflictAcceptPolicy(String name) {
        myName = name;
    }
    
    public String toString() {
        return myName;
    }
    
    public static SVNConflictAcceptPolicy fromString(String accept) {
        if (POSTPONE.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.POSTPONE;
        } else if (BASE.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.BASE;
        } else if (MINE.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.MINE;
        } else if (THEIRS.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.THEIRS;
        } else if (EDIT.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.EDIT;
        } else if (LAUNCH.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.LAUNCH;
        }
        return SVNConflictAcceptPolicy.INVALID;
    }

}
