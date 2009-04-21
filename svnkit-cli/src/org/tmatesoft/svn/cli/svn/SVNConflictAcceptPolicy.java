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
package org.tmatesoft.svn.cli.svn;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNConflictAcceptPolicy {
    public static final SVNConflictAcceptPolicy INVALID = new SVNConflictAcceptPolicy("invalid");  

    public static final SVNConflictAcceptPolicy UNSPECIFIED = new SVNConflictAcceptPolicy("unspecified");  

    /**
     * Resolve the conflict with the pre-conflict base file.
     */
    public static final SVNConflictAcceptPolicy BASE = new SVNConflictAcceptPolicy("base");
    /**
     * Resolve the conflict with the pre-conflict working copy file.
     */
    public static final SVNConflictAcceptPolicy MINE = new SVNConflictAcceptPolicy("mine");

    public static final SVNConflictAcceptPolicy MINE_FULL = new SVNConflictAcceptPolicy("mine-full");

    public static final SVNConflictAcceptPolicy WORKING = new SVNConflictAcceptPolicy("working");
    /**
     * Resolve the conflict with the post-conflict base file.
     */
    public static final SVNConflictAcceptPolicy THEIRS = new SVNConflictAcceptPolicy("theirs");

    public static final SVNConflictAcceptPolicy THEIRS_FULL = new SVNConflictAcceptPolicy("theirs-full");

    public static final SVNConflictAcceptPolicy POSTPONE = new SVNConflictAcceptPolicy("postpone");
    
    public static final SVNConflictAcceptPolicy EDIT = new SVNConflictAcceptPolicy("edit");
    
    public static final SVNConflictAcceptPolicy LAUNCH = new SVNConflictAcceptPolicy("launch");
    
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
        /* TODO: not implemented yet
        } else if (MINE.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.MINE;
        } else if (THEIRS.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.THEIRS;
        */
        } else if (EDIT.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.EDIT;
        } else if (LAUNCH.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.LAUNCH;
        } else if (MINE_FULL.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.MINE_FULL;
        } else if (THEIRS_FULL.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.THEIRS_FULL;
        } else if (WORKING.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.WORKING;
        }
        return SVNConflictAcceptPolicy.INVALID;
    }

}
