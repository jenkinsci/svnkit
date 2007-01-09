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
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @since   1.1.1
 */
public class SVNAdminEventAction {
    private int myID;

    private SVNAdminEventAction(int id) {
        myID = id;
    }

    public int getID() {
        return myID;
    }

    public String toString() {
        return Integer.toString(myID);
    }

    public static final SVNAdminEventAction REVISION_LOADED = new SVNAdminEventAction(0);

    public static final SVNAdminEventAction REVISION_DUMPED = new SVNAdminEventAction(1);

    public static final SVNAdminEventAction TRANSACTION_LISTED = new SVNAdminEventAction(2);

    public static final SVNAdminEventAction TRANSACTION_REMOVED = new SVNAdminEventAction(3);

    
}
