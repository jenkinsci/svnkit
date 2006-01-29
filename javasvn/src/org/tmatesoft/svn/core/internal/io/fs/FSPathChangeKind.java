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



/**
 * The kind of change that occurred on the path. 
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSPathChangeKind {
    /* default value */
    public static final FSPathChangeKind FS_PATH_CHANGE_MODIFY = new FSPathChangeKind(0, FSConstants.ACTION_MODIFY); 
    /* path added in txn */
    public static final FSPathChangeKind FS_PATH_CHANGE_ADD = new FSPathChangeKind(1, FSConstants.ACTION_ADD); 
    /* path removed in txn */
    public static final FSPathChangeKind FS_PATH_CHANGE_DELETE = new FSPathChangeKind(2, FSConstants.ACTION_DELETE); 
    /* path removed and re-added in txn */
    public static final FSPathChangeKind FS_PATH_CHANGE_REPLACE = new FSPathChangeKind(3, FSConstants.ACTION_REPLACE); 
    /* ignore all previous change items for path (internal-use only) */
    public static final FSPathChangeKind FS_PATH_CHANGE_RESET = new FSPathChangeKind(4, FSConstants.ACTION_RESET); 

    private int myID;
    
    private String myName;
    
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
}
