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

import org.tmatesoft.svn.core.io.SVNLocationEntry;

public class FSChange {    
    /*Path of the change*/
    private String path;
    
    /*the entity consist of noderev, changeKind, isTextModification and isPropsModification*/
    private FSPathChange myFSPathChange;    
    
    /*Copyfrom revision and path*/
    private SVNLocationEntry copyfromEntry;
    
    public FSChange(String newPath, FSID newID, FSPathChangeKind newKind, boolean newTextMode, boolean newPropMode, SVNLocationEntry newCopyfromEntry){
        path = newPath;
        myFSPathChange = new FSPathChange(new FSID(newID), newKind, newTextMode, newPropMode);
        copyfromEntry = newCopyfromEntry;
    }
    
    public String getPath(){
        return path;
    }
    
    public FSID getNodeRevID(){
        return myFSPathChange.getRevNodeId();
    }
    
    public FSPathChangeKind getKind(){
        return myFSPathChange.getChangeKind();
    }    
    
    public boolean getTextModification(){
        return myFSPathChange.isTextModified;
    }
    
    public boolean getPropModification(){
        return myFSPathChange.arePropertiesModified;
    }
    
    public SVNLocationEntry getCopyfromEntry(){
        return copyfromEntry;
    }
    
    public FSPathChange getFSPathChange(){
        return myFSPathChange;
    }
}
