/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class SVNOperation {
    public static final SVNOperation UPDATE = new SVNOperation("update");
    public static final SVNOperation SWITCH = new SVNOperation("switch");
    public static final SVNOperation MERGE = new SVNOperation("merge");
    public static final SVNOperation NONE = new SVNOperation("none");

    public static SVNOperation fromString(String operation) {
        if (UPDATE.getName().equals(operation)) {
            return UPDATE;
        }
        if (SWITCH.getName().equals(operation)) {
            return SWITCH;
        }
        if (MERGE.getName().equals(operation)) {
            return MERGE;
        }
        if (NONE.getName().equals(operation)) {
            return NONE;
        }
        return null;
    }
    
    private final String myName;

    private SVNOperation(String name) {
        myName = name;
    }

    public String getName() {
        return myName;
    }

    public String toString() {
        return getName();
    }
}
