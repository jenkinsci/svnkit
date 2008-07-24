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
package org.tmatesoft.svn.core;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfoInheritance {
    public static final SVNMergeInfoInheritance EXPLICIT = new SVNMergeInfoInheritance("explicit");
    public static final SVNMergeInfoInheritance INHERITED = new SVNMergeInfoInheritance("inherited");
    public static final SVNMergeInfoInheritance NEAREST_ANCESTOR = new SVNMergeInfoInheritance("nearest-ancestor");
    
    private String myName;
    
    private SVNMergeInfoInheritance(String name) {
        myName = name;
    }
    
    public String toString() {
        return myName;
    }
}
