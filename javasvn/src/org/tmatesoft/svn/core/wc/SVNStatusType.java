/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNStatusType {

    private int myID;

    private SVNStatusType(int id) {
        myID = id;
    }

    public int getID() {
        return myID;
    }

    public String toString() {
        return Integer.toString(myID);
    }

    public static final SVNStatusType INAPPLICABLE = new SVNStatusType(0);

    public static final SVNStatusType UNKNOWN = new SVNStatusType(1);

    public static final SVNStatusType UNCHANGED = new SVNStatusType(2);

    public static final SVNStatusType MISSING = new SVNStatusType(3);

    public static final SVNStatusType OBSTRUCTED = new SVNStatusType(4);

    public static final SVNStatusType CHANGED = new SVNStatusType(5);

    public static final SVNStatusType MERGED = new SVNStatusType(6);

    public static final SVNStatusType CONFLICTED = new SVNStatusType(7);

    public static final SVNStatusType CONFLICTED_UNRESOLVED = new SVNStatusType(8);

    public static final SVNStatusType LOCK_INAPPLICABLE = new SVNStatusType(0);

    public static final SVNStatusType LOCK_UNKNOWN = new SVNStatusType(1);

    public static final SVNStatusType LOCK_UNCHANGED = new SVNStatusType(2);

    public static final SVNStatusType LOCK_LOCKED = new SVNStatusType(3);

    public static final SVNStatusType LOCK_UNLOCKED = new SVNStatusType(4);

    public static final SVNStatusType STATUS_NONE = new SVNStatusType(0);

    public static final SVNStatusType STATUS_NORMAL = new SVNStatusType(1);

    public static final SVNStatusType STATUS_MODIFIED = new SVNStatusType(2);

    public static final SVNStatusType STATUS_ADDED = new SVNStatusType(3);

    public static final SVNStatusType STATUS_DELETED = new SVNStatusType(4);

    public static final SVNStatusType STATUS_UNVERSIONED = new SVNStatusType(5);

    public static final SVNStatusType STATUS_MISSING = new SVNStatusType(6);

    public static final SVNStatusType STATUS_REPLACED = new SVNStatusType(7);

    public static final SVNStatusType STATUS_MERGED = new SVNStatusType(8);

    public static final SVNStatusType STATUS_CONFLICTED = new SVNStatusType(9);

    public static final SVNStatusType STATUS_OBSTRUCTED = new SVNStatusType(10);

    public static final SVNStatusType STATUS_IGNORED = new SVNStatusType(11);

    public static final SVNStatusType STATUS_INCOMPLETE = new SVNStatusType(12);

    public static final SVNStatusType STATUS_EXTERNAL = new SVNStatusType(13);
}
