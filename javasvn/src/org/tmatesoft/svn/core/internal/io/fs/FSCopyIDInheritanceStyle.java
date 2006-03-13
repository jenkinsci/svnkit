/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
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
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSCopyIDInheritanceStyle {
    
    public static final FSCopyIDInheritanceStyle COPY_ID_INHERIT_UNKNOWN = new FSCopyIDInheritanceStyle(0);
    
    public static final FSCopyIDInheritanceStyle COPY_ID_INHERIT_SELF = new FSCopyIDInheritanceStyle(1);
    
    public static final FSCopyIDInheritanceStyle COPY_ID_INHERIT_PARENT = new FSCopyIDInheritanceStyle(2);
    
    public static final FSCopyIDInheritanceStyle COPY_ID_INHERIT_NEW = new FSCopyIDInheritanceStyle(3);

    
    private int myID;

    private FSCopyIDInheritanceStyle(int id) {
        myID = id;
    }
    
    public int compareTo(Object o) {
        if (o == null || o.getClass() != FSCopyIDInheritanceStyle.class) {
            return -1;
        }
        int otherID = ((FSCopyIDInheritanceStyle) o).myID;
        return myID > otherID ? 1 : myID < otherID ? -1 : 0;
    }


}
