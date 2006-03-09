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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
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
        myFSPathChange = new FSPathChange(newID.copy(), newKind, newTextMode, newPropMode);
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
    
    public static FSChange fromString(String changeLine, String copyfromLine) throws SVNException {
        String[] piecesOfChangeLine = changeLine.split(" ", 5);
        if (piecesOfChangeLine.length < 5) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
            SVNErrorManager.error(err);
        }
        /* Get the node-id of the change. */
        String nodeRevStr = piecesOfChangeLine[0];
        FSID nodeRevID = FSID.fromString(nodeRevStr);
        /* Get the change type. */
        String changesKindStr = piecesOfChangeLine[1];
        FSPathChangeKind changesKind = FSPathChangeKind.fromString(changesKindStr);
        if (changesKind == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change kind in rev file");
            SVNErrorManager.error(err);
        }
        /* Get the text-mod flag. */
        String textModeStr = piecesOfChangeLine[2];
        boolean textModeBool = false;
        if (FSConstants.FLAG_TRUE.equals(textModeStr)) {
            textModeBool = true;
        } else if (FSConstants.FLAG_FALSE.equals(textModeStr)) {
            textModeBool = false;
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid text-mod flag in rev-file");
            SVNErrorManager.error(err);
        }
        /* Get the prop-mod flag. */
        String propModeStr = piecesOfChangeLine[3];
        boolean propModeBool = false;
        if (FSConstants.FLAG_TRUE.equals(propModeStr)) {
            propModeBool = true;
        } else if (FSConstants.FLAG_FALSE.equals(propModeStr)) {
            propModeBool = false;
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid prop-mod flag in rev-file");
            SVNErrorManager.error(err);
        }
        /* Get the changed path. */
        String pathStr = piecesOfChangeLine[4];

        SVNLocationEntry copyfromEntry = null;
        if (copyfromLine == null || copyfromLine.length() == 0) {
            copyfromEntry = new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null);
        } else {
            String[] piecesOfCopyfromLine = copyfromLine.split(" ", 2);
            if (piecesOfCopyfromLine.length < 2) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
                SVNErrorManager.error(err);
            }
            copyfromEntry = new SVNLocationEntry(Long.parseLong(piecesOfCopyfromLine[0]), piecesOfCopyfromLine[1]);
        }

        return new FSChange(pathStr, nodeRevID, changesKind, textModeBool, propModeBool, copyfromEntry);
    }
}
