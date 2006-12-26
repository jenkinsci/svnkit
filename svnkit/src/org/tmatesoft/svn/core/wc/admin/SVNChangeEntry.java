/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc.admin;

import org.tmatesoft.svn.core.SVNNodeKind;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNChangeEntry {
    /**
     * Char <span class="javastring">'A'</span> (item added).
     */
    public static final char TYPE_ADDED = 'A';

    /**
     * Char <span class="javastring">'D'</span> (item deleted).
     */
    public static final char TYPE_DELETED = 'D';
    
    /**
     * Char <span class="javastring">'U'</span> (item modified).
     */
    public static final char TYPE_UPDATED = 'U';
    
    /**
     * Char <span class="javastring">'R'</span> (item replaced).
     */
    public static final char TYPE_REPLACED = 'R';
    
    private String myPath;
    private char myType;
    private String myCopyFromPath;
    private long myCopyFromRevision;
    private boolean myHasTextModifications;
    private boolean myHasPropModifications;
    private SVNNodeKind myKind;
    
    /**
     * @param path
     * @param type
     * @param copyFromPath
     * @param copyFromRevision
     * @param hasTextModifications
     * @param hasPropModifications
     */
    public SVNChangeEntry(String path, SVNNodeKind kind, char type, String copyFromPath, long copyFromRevision, boolean hasTextModifications, boolean hasPropModifications) {
        myPath = path;
        myKind = kind;
        myType = type;
        myCopyFromPath = copyFromPath;
        myCopyFromRevision = copyFromRevision;
        myHasTextModifications = hasTextModifications;
        myHasPropModifications = hasPropModifications;
    }
    
    public String getCopyFromPath() {
        return myCopyFromPath;
    }
    
    public long getCopyFromRevision() {
        return myCopyFromRevision;
    }
    
    public String getPath() {
        return myPath;
    }
    
    public char getType() {
        return myType;
    }
    
    public boolean hasPropertyModifications() {
        return myHasPropModifications;
    }

    public boolean hasTextModifications() {
        return myHasTextModifications;
    }

    public SVNNodeKind getKind() {
        return myKind;
    }
}
