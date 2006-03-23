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
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSPathChange extends SVNLogEntryPath {
    private String myPath;
    private FSID myRevNodeId;
    private FSPathChangeKind myChangeKind;
    boolean isTextModified;
    boolean arePropertiesModified;
    
    public FSPathChange(String path, FSID id, FSPathChangeKind kind, boolean textModified, boolean propsModified, String copyfromPath, long copyfromRevision) {
        super(path, FSPathChangeKind.getType(kind), copyfromPath, copyfromRevision);
        myPath = path;
        myRevNodeId = id;
        myChangeKind = kind;
        isTextModified = textModified;
        arePropertiesModified = propsModified;
    }

    public String getPath(){
        return myPath;
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
        super.setChangeType(FSPathChangeKind.getType(changeKind));
    }

    public FSID getRevNodeId() {
        return myRevNodeId;
    }
    
    public void setRevNodeId(FSID revNodeId) {
        myRevNodeId = revNodeId;
    }

    public void setCopyRevision(long revision) {
        super.setCopyRevision(revision);
    }
    
    public void setCopyPath(String path) {
        super.setCopyPath(path);
    }

    public static FSPathChange fromString(String changeLine, String copyfromLine) throws SVNException {
        String[] piecesOfChangeLine = changeLine.split(" ", 5);
        if (piecesOfChangeLine.length < 5) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
            SVNErrorManager.error(err);
        }
        String nodeRevStr = piecesOfChangeLine[0];
        FSID nodeRevID = FSID.fromString(nodeRevStr);
        String changesKindStr = piecesOfChangeLine[1];
        FSPathChangeKind changesKind = FSPathChangeKind.fromString(changesKindStr);
        if (changesKind == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change kind in rev file");
            SVNErrorManager.error(err);
        }
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
        String pathStr = piecesOfChangeLine[4];

        String copyfromPath = null;
        long copyfromRevision = FSConstants.SVN_INVALID_REVNUM;
        
        if (copyfromLine != null && copyfromLine.length() != 0) {
            String[] piecesOfCopyfromLine = copyfromLine.split(" ", 2);
            if (piecesOfCopyfromLine.length < 2) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
                SVNErrorManager.error(err);
            }
            copyfromRevision = Long.parseLong(piecesOfCopyfromLine[0]);
            copyfromPath = piecesOfCopyfromLine[1];
        }

        return new FSPathChange(pathStr, nodeRevID, changesKind, textModeBool, propModeBool, copyfromPath, copyfromRevision);
    }

}
