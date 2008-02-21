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
package org.tmatesoft.svn.core.wc;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNConflictChoice {
    public static SVNConflictChoice POSTPONE = new SVNConflictChoice(0);
    public static SVNConflictChoice BASE = new SVNConflictChoice(1);
    public static SVNConflictChoice THEIRS_FULL = new SVNConflictChoice(2);
    public static SVNConflictChoice MINE_FULL = new SVNConflictChoice(3);
    public static SVNConflictChoice THEIRS = new SVNConflictChoice(4);
    public static SVNConflictChoice MINE = new SVNConflictChoice(5);
    public static SVNConflictChoice MERGED = new SVNConflictChoice(6);

    private int myID;

    private SVNConflictChoice (int id) {
        myID = id;
    }

    public int getID(){
        return myID;
    }
}
