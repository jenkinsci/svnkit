/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.util.HashMap;
import java.util.Map;



/**
 * The kind of change that occurred on the path. 
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSPathChangeKind {
    //change actions - for commits
    public static final String ACTION_MODIFY = "modify";
    public static final String ACTION_ADD = "add";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_REPLACE = "replace";
    public static final String ACTION_RESET = "reset";

    /* default value */
    public static final FSPathChangeKind FS_PATH_CHANGE_MODIFY = new FSPathChangeKind(0, ACTION_MODIFY); 
    /* path added in txn */
    public static final FSPathChangeKind FS_PATH_CHANGE_ADD = new FSPathChangeKind(1, ACTION_ADD); 
    /* path removed in txn */
    public static final FSPathChangeKind FS_PATH_CHANGE_DELETE = new FSPathChangeKind(2, ACTION_DELETE); 
    /* path removed and re-added in txn */
    public static final FSPathChangeKind FS_PATH_CHANGE_REPLACE = new FSPathChangeKind(3, ACTION_REPLACE); 
    /* ignore all previous change items for path (internal-use only) */
    public static final FSPathChangeKind FS_PATH_CHANGE_RESET = new FSPathChangeKind(4, ACTION_RESET); 

    private int myID;
    
    private String myName;

    private static final Map ACTIONS_TO_CHANGE_KINDS = new HashMap();
    
    static {
        ACTIONS_TO_CHANGE_KINDS.put(ACTION_MODIFY, FSPathChangeKind.FS_PATH_CHANGE_MODIFY);
        ACTIONS_TO_CHANGE_KINDS.put(ACTION_ADD, FSPathChangeKind.FS_PATH_CHANGE_ADD);
        ACTIONS_TO_CHANGE_KINDS.put(ACTION_DELETE, FSPathChangeKind.FS_PATH_CHANGE_DELETE);
        ACTIONS_TO_CHANGE_KINDS.put(ACTION_REPLACE, FSPathChangeKind.FS_PATH_CHANGE_REPLACE);
        ACTIONS_TO_CHANGE_KINDS.put(ACTION_RESET, FSPathChangeKind.FS_PATH_CHANGE_RESET);
    }

    private FSPathChangeKind(int id, String name) {
        myID = id;
        myName = name;
    }

    public String toString(){
        return myName;
    }
    
    public int compareTo(Object o) {
        if (o == null || o.getClass() != FSPathChangeKind.class) {
            return -1;
        }
        int otherID = ((FSPathChangeKind) o).myID;
        return myID > otherID ? 1 : myID < otherID ? -1 : 0;
    }
    
    public static FSPathChangeKind fromString(String changeKindStr){
        return (FSPathChangeKind)ACTIONS_TO_CHANGE_KINDS.get(changeKindStr);
    }
}
