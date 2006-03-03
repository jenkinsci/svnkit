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
package org.tmatesoft.svn.core.internal.io.fs2;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSChangedPath {
    
    public static final String KIND_ADDED = "add";
    public static final String KIND_DELETED = "delete";
    public static final String KIND_MODIFIED = "modify";
    public static final String KIND_REPLACED = "replace";
    
    private String myID;
    private boolean myHasPropChanges;
    private String myKind;
    private boolean myHasTextChanges;
    private String myPath;
    private String myCopyFromPath;
    private long myCopyFromRevision;
    
    public FSChangedPath(String id, String kind, boolean propChange, boolean textChange, String path, String copyFromPath, long copyFromRev) {
        myID = id;
        myKind = kind;
        myHasPropChanges = propChange;
        myHasTextChanges = textChange;
        myPath = path;
        myCopyFromPath = copyFromPath;
        myCopyFromRevision =copyFromRev;
    }
    
    public String getID() {
        return myID;
    }
    
    public String getPath() {
        return myPath;
    }
    
    public String getKind() {
        return myKind;
    }
    
    public String getCopyFromPath() {
        return myCopyFromPath;
    }
    
    public long getCopyFromRevision() {
        return myCopyFromRevision;
    }
    
    public boolean hasTextChanges() {
        return myHasTextChanges;
    }

    public boolean hasPropertyChanges() {
        return myHasPropChanges;
    }

}
