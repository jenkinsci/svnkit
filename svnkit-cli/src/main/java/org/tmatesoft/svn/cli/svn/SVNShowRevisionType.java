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


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNShowRevisionType {
    public static final SVNShowRevisionType INVALID = new SVNShowRevisionType("invalid");
    public static final SVNShowRevisionType MERGED = new SVNShowRevisionType("merged");    
    public static final SVNShowRevisionType ELIGIBLE = new SVNShowRevisionType("eligible");
    
    private String myName;
    private SVNShowRevisionType(String name) {
        myName = name;
    }
    
    public String toString() {
        return myName;
    }

    public static SVNShowRevisionType fromString(String showRevisions) {
        if (MERGED.myName.equals(showRevisions)) {
            return MERGED;
        } else if (ELIGIBLE.myName.equals(showRevisions)) {
            return ELIGIBLE;
        } 
        return INVALID; 
    }
}
