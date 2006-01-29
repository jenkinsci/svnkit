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
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSPathChange {
    /* node revision id of changed path */
    private FSID myRevNodeId;
    /* kind of change */
    private FSPathChangeKind myChangeKind;
    /* were there text mods? */
    boolean isTextModified;
    /* were there property mods? */
    boolean arePropertiesModified;
    
    public FSPathChange(FSID id, FSPathChangeKind kind, boolean textModified, boolean propsModified) {
        myRevNodeId = id;
        myChangeKind = kind;
        isTextModified = textModified;
        arePropertiesModified = propsModified;
    }
    
    public boolean arePropertiesModified() {
        return arePropertiesModified;
    }

    public void setPropertiesModified(boolean propertiesModified) {
        arePropertiesModified = propertiesModified;
    }
    
    public boolean isTextModified() {
        return isTextModified;
    }

    public void setTextModified(boolean textModified) {
        isTextModified = textModified;
    }
    
    public FSPathChangeKind getChangeKind() {
        return myChangeKind;
    }

    public void setChangeKind(FSPathChangeKind changeKind) {
        myChangeKind = changeKind;
    }

    public FSID getRevNodeId() {
        return myRevNodeId;
    }
    
    public void setRevNodeId(FSID revNodeId) {
        myRevNodeId = revNodeId;
    }
}
