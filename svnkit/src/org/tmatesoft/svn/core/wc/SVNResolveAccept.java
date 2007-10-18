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
package org.tmatesoft.svn.core.wc;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNResolveAccept {
    public static SVNResolveAccept INVALID = new SVNResolveAccept("invalid");  
    /**
     * Resolve the conflict as usual.
     */
    public static SVNResolveAccept DEFAULT = new SVNResolveAccept("default");
    /**
     * Resolve the conflict with the pre-conflict base file.
     */
    public static SVNResolveAccept BASE = new SVNResolveAccept("base");
    /**
     * Resolve the conflict with the pre-conflict working copy file.
     */
    public static SVNResolveAccept MINE = new SVNResolveAccept("mine");
    /**
     * Resolve the conflict with the post-conflict base file.
     */
    public static SVNResolveAccept THEIRS = new SVNResolveAccept("theirs");

    public static SVNResolveAccept POSTPONE = new SVNResolveAccept("postpone");
    
    public static SVNResolveAccept EDIT = new SVNResolveAccept("edit");
    public static SVNResolveAccept LAUNCH = new SVNResolveAccept("launch");
    
    private String myName;
    private SVNResolveAccept(String name) {
        myName = name;
    }
    
    public String toString() {
        return myName;
    }
    
    public static SVNResolveAccept fromString(String accept) {
        if ("left".equals(accept)) {
            return BASE;
        } else if ("working".equals(accept)) {
            return MINE;
        } else if ("right".equals(accept)) {
            return THEIRS;
        }
        return INVALID;
    }
}
