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
package org.tmatesoft.svn.core.internal.wc.db;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWCDbStatus {
    private static final SVNWCDbStatus NORMAL = new SVNWCDbStatus("normal");
    private static final SVNWCDbStatus ADDED = new SVNWCDbStatus(null);
    private static final SVNWCDbStatus MOVED_HERE = new SVNWCDbStatus(null);
    private static final SVNWCDbStatus COPIED = new SVNWCDbStatus(null);
    private static final SVNWCDbStatus DELETED = new SVNWCDbStatus(null);
    private static final SVNWCDbStatus OBSTRUCTED = new SVNWCDbStatus(null);
    private static final SVNWCDbStatus OBSTRUCTED_DELETE = new SVNWCDbStatus(null);
    private static final SVNWCDbStatus OBSTRUCTED_ADD = new SVNWCDbStatus(null); 
    private static final SVNWCDbStatus ABSENT = new SVNWCDbStatus("absent");
    private static final SVNWCDbStatus EXCLUDED = new SVNWCDbStatus("excluded");
    private static final SVNWCDbStatus NOT_PRESENT = new SVNWCDbStatus("not-present");
    private static final SVNWCDbStatus INCOMPLETE = new SVNWCDbStatus("incomplete");
    private static final SVNWCDbStatus BASE_DELETED = new SVNWCDbStatus("base-deleted");
    
    private String myName;
    
    private SVNWCDbStatus(String name) {
        myName = name;
    }
    
    public String toString() {
        return myName;
    }
}
