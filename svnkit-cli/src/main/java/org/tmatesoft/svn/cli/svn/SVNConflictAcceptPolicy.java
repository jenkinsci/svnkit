/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svn;

import java.util.Collection;
import java.util.Map;

import org.tmatesoft.svn.cli.AbstractSVNCommand;
import org.tmatesoft.svn.cli.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;


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
    public static final SVNConflictAcceptPolicy MINE_CONFLICT = new SVNConflictAcceptPolicy("mine-conflict");

    public static final SVNConflictAcceptPolicy MINE_FULL = new SVNConflictAcceptPolicy("mine-full");

    public static final SVNConflictAcceptPolicy WORKING = new SVNConflictAcceptPolicy("working");
    /**
     * Resolve the conflict with the post-conflict base file.
     */
    public static final SVNConflictAcceptPolicy THEIRS_CONFLICT = new SVNConflictAcceptPolicy("theirs-conflict");

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
        if (POSTPONE.myName.equals(accept) || "p".equals(accept) || ":-P".equals(accept)) {
            return SVNConflictAcceptPolicy.POSTPONE;
        } else if (BASE.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.BASE;
        } else if (MINE_CONFLICT.myName.equals(accept) || "mc".equals(accept) || "X-)".equals(accept)) {
            return SVNConflictAcceptPolicy.MINE_CONFLICT;
        } else if (THEIRS_CONFLICT.myName.equals(accept) || "tc".equals(accept) || "X-(".equals(accept)) {
            return SVNConflictAcceptPolicy.THEIRS_CONFLICT;
        } else if (EDIT.myName.equals(accept) || "e".equals(accept) || ":-E".equals(accept)) {
            return SVNConflictAcceptPolicy.EDIT;
        } else if (LAUNCH.myName.equals(accept) || "l".equals(accept) || ":-l".equals(accept)) {
            return SVNConflictAcceptPolicy.LAUNCH;
        } else if (MINE_FULL.myName.equals(accept) || "mf".equals(accept) || ":-)".equals(accept)) {
            return SVNConflictAcceptPolicy.MINE_FULL;
        } else if (THEIRS_FULL.myName.equals(accept) || "tf".equals(accept) || ":-(".equals(accept)) {
            return SVNConflictAcceptPolicy.THEIRS_FULL;
        } else if (WORKING.myName.equals(accept)) {
            return SVNConflictAcceptPolicy.WORKING;
        }
        return SVNConflictAcceptPolicy.INVALID;
    }

}
