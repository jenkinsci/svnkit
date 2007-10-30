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
public class SVNWCAccept {
    public static SVNWCAccept INVALID = new SVNWCAccept("invalid");  
    /**
     * Resolve the conflict with the pre-conflict base file.
     */
    public static SVNWCAccept BASE = new SVNWCAccept("base");
    /**
     * Resolve the conflict with the pre-conflict working copy file.
     */
    public static SVNWCAccept MINE = new SVNWCAccept("mine");
    /**
     * Resolve the conflict with the post-conflict base file.
     */
    public static SVNWCAccept THEIRS = new SVNWCAccept("theirs");

    public static SVNWCAccept POSTPONE = new SVNWCAccept("postpone");
    
    public static SVNWCAccept EDIT = new SVNWCAccept("edit");
    
    public static SVNWCAccept LAUNCH = new SVNWCAccept("launch");
    
    private String myName;
    private SVNWCAccept(String name) {
        myName = name;
    }
    
    public String toString() {
        return myName;
    }
    
    public static SVNWCAccept fromString(String accept) {
        if (POSTPONE.myName.equals(accept)) {
            return SVNWCAccept.POSTPONE;
        } else if (BASE.myName.equals(accept)) {
            return SVNWCAccept.BASE;
        } else if (MINE.myName.equals(accept)) {
            return SVNWCAccept.MINE;
        } else if (THEIRS.myName.equals(accept)) {
            return SVNWCAccept.THEIRS;
        } else if (EDIT.myName.equals(accept)) {
            return SVNWCAccept.EDIT;
        } else if (LAUNCH.myName.equals(accept)) {
            return SVNWCAccept.LAUNCH;
        }
        return SVNWCAccept.INVALID;
    }

}
