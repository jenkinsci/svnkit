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
    public static SVNWCAccept INVALID = new SVNWCAccept();  
    /**
     * Resolve the conflict with the pre-conflict base file.
     */
    public static SVNWCAccept BASE = new SVNWCAccept();
    /**
     * Resolve the conflict with the pre-conflict working copy file.
     */
    public static SVNWCAccept MINE = new SVNWCAccept();
    /**
     * Resolve the conflict with the post-conflict base file.
     */
    public static SVNWCAccept THEIRS = new SVNWCAccept();

    public static SVNWCAccept POSTPONE = new SVNWCAccept();
    
    public static SVNWCAccept EDIT = new SVNWCAccept();
    
    public static SVNWCAccept LAUNCH = new SVNWCAccept();
    
    private SVNWCAccept() {
    }
    
    public static SVNWCAccept fromString(String accept) {
        if ("postpone".equals(accept)) {
            return SVNWCAccept.POSTPONE;
        } else if ("base".equals(accept)) {
            return SVNWCAccept.BASE;
        } else if ("mine".equals(accept)) {
            return SVNWCAccept.MINE;
        } else if ("theirs".equals(accept)) {
            return SVNWCAccept.THEIRS;
        } else if ("edit".equals(accept)) {
            return SVNWCAccept.EDIT;
        } else if ("launch".equals(accept)) {
            return SVNWCAccept.LAUNCH;
        }
        return SVNWCAccept.INVALID;
    }

}
