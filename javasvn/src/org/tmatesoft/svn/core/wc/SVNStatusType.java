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
    
    /**
     * Gives a string representation of this object.
     * 
     * @return a string representing this object
     */
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

    /**
     * In a status operation (if it's being running with an option to report
     * of all items set to <span class="javakeyword">true</span>) denotes that the 
     * item in the Working Copy being currently processed has no local changes 
     * (in a normal state).  
     */
    public static final SVNStatusType STATUS_NORMAL = new SVNStatusType(1);
    
    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed has local modifications.
     */
    public static final SVNStatusType STATUS_MODIFIED = new SVNStatusType(2);

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is scheduled for addition to the repository.
     */
    public static final SVNStatusType STATUS_ADDED = new SVNStatusType(3);

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is scheduled for deletion from the repository.
     */
    public static final SVNStatusType STATUS_DELETED = new SVNStatusType(4);

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is not under version control.
     */
    public static final SVNStatusType STATUS_UNVERSIONED = new SVNStatusType(5);

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is under version control but is missing (for example, removed
     * from the filesystem with a non-SVN or non-JavaSVN delete command or any other
     * SVN non-compatible delete command) or somehow incomplete (for example, the previous
     * update was interrupted). 
     */
    public static final SVNStatusType STATUS_MISSING = new SVNStatusType(6);
    
    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed was replaced by another item with the same name (within
     * a single revision the item was scheduled for deletion and then a new one with
     * the same name was scheduled for addition). Though they may have the same name
     * the items have their own distinct histories. 
     */
    public static final SVNStatusType STATUS_REPLACED = new SVNStatusType(7);
    
    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed was merged - that is it was applied the differences
     * (delta) between two sources in a merge operation.
     */
    public static final SVNStatusType STATUS_MERGED = new SVNStatusType(8);

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed is in a conflict state (local changes overlap those 
     * that came from the repository). The conflicting overlaps need to be manually
     * resolved.
     */
    public static final SVNStatusType STATUS_CONFLICTED = new SVNStatusType(9);
    
    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed has a non-expected kind. For example, a file is 
     * considered to be obstructed if it was deleted (with an SVN client non-compatible 
     * delete operation) and a directory with the same name as the file had had was added 
     * (but again with an SVN client non-compatible operation).
     */
    public static final SVNStatusType STATUS_OBSTRUCTED = new SVNStatusType(10);

    /**
     * In a status operation denotes that the item in the Working Copy being 
     * currently processed was set to be ignored (svn:ignore ).
     */
    public static final SVNStatusType STATUS_IGNORED = new SVNStatusType(11);

    public static final SVNStatusType STATUS_INCOMPLETE = new SVNStatusType(12);

    public static final SVNStatusType STATUS_EXTERNAL = new SVNStatusType(13);
}
