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
     * A 'next revision loaded' action.
     */
    public static final SVNAdminEventAction REVISION_LOADED = new SVNAdminEventAction(0);

    /**
     * A 'next revision dumped' action.
     */
    public static final SVNAdminEventAction REVISION_DUMPED = new SVNAdminEventAction(1);

    /**
     * A 'next transaction listed' action.
     */
    public static final SVNAdminEventAction TRANSACTION_LISTED = new SVNAdminEventAction(2);

    /**
     * A 'next transaction removed' action.
     */
    public static final SVNAdminEventAction TRANSACTION_REMOVED = new SVNAdminEventAction(3);

    
}
