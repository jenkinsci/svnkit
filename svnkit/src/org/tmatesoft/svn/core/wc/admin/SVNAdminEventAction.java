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
package org.tmatesoft.svn.core.wc.admin;


/**
 * The <b>SVNAdminEventAction</b> is an enumeration of possible actions that 
 * may take place in different methods of <b>SVNAdminClient</b>. 
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @since   1.1.1
 */
public class SVNAdminEventAction {
    private int myID;

    private SVNAdminEventAction(int id) {
        myID = id;
    }

    /**
     * Returns an action id
     * 
     * @return id of this action
     */
    public int getID() {
        return myID;
    }

    /**
     * Gives a string representation of this action.
     * 
     * @return string representation of this object
     */
    public String toString() {
        return Integer.toString(myID);
    }

    /**
     * An action that denotes a next revision load is started.
     */
    public static final SVNAdminEventAction REVISION_LOAD = new SVNAdminEventAction(0);
    
    /**
     * An action that denotes a next revision load is completed.
     */
    public static final SVNAdminEventAction REVISION_LOADED = new SVNAdminEventAction(1);

    /**
     * An action that denotes editing a next path within the current revision being loaded.
     */
    public static final SVNAdminEventAction REVISION_LOAD_EDIT_PATH = new SVNAdminEventAction(2);

    /**
     * An action that denotes deleting a next path within the current revision being loaded.
     */
    public static final SVNAdminEventAction REVISION_LOAD_DELETE_PATH = new SVNAdminEventAction(3);
    
    /**
     * An action that denotes adding a next path within the current revision being loaded.
     */
    public static final SVNAdminEventAction REVISION_LOAD_ADD_PATH = new SVNAdminEventAction(4);
    
    /**
     * An action that denotes replacing a next path within the current revision being loaded.
     */
    public static final SVNAdminEventAction REVISION_LOAD_REPLACE_PATH = new SVNAdminEventAction(5);
    
    /**
     * A 'next revision dumped' action.
     */
    public static final SVNAdminEventAction REVISION_DUMPED = new SVNAdminEventAction(6);

    /**
     * A 'next transaction listed' action.
     */
    public static final SVNAdminEventAction TRANSACTION_LISTED = new SVNAdminEventAction(7);

    /**
     * A 'next transaction removed' action.
     */
    public static final SVNAdminEventAction TRANSACTION_REMOVED = new SVNAdminEventAction(8);

    public static final SVNAdminEventAction UNLOCK_FAILED = new SVNAdminEventAction(9);
    
    public static final SVNAdminEventAction UNLOCKED = new SVNAdminEventAction(10);

    public static final SVNAdminEventAction NOT_LOCKED = new SVNAdminEventAction(11);

    public static final SVNAdminEventAction LOCK_LISTED = new SVNAdminEventAction(12);
    
    public static final SVNAdminEventAction RECOVERY_STARTED = new SVNAdminEventAction(13);
}
