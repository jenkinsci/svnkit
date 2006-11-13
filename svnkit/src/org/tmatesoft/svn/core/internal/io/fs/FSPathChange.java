/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
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
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class FSPathChange extends SVNLogEntryPath {
    private static final String FLAG_TRUE = "true";
    private static final String FLAG_FALSE = "false";

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
        int delimiterInd = changeLine.indexOf(' ');

        //String[] piecesOfChangeLine = changeLine.split(" ", 5);
        if (delimiterInd == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
            SVNErrorManager.error(err);
        }
        
        String id = changeLine.substring(0, delimiterInd);
        FSID nodeRevID = FSID.fromString(id);
        
        changeLine = changeLine.substring(delimiterInd + 1);
        delimiterInd = changeLine.indexOf(' ');
        if (delimiterInd == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
            SVNErrorManager.error(err);
        }
        String changesKindStr = changeLine.substring(0, delimiterInd);

        FSPathChangeKind changesKind = FSPathChangeKind.fromString(changesKindStr);
        if (changesKind == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid change kind in rev file");
            SVNErrorManager.error(err);
        }

        changeLine = changeLine.substring(delimiterInd + 1);
        delimiterInd = changeLine.indexOf(' ');
        if (delimiterInd == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
            SVNErrorManager.error(err);
        }
        String textModeStr = changeLine.substring(0, delimiterInd);
        
        boolean textModeBool = false;
        if (FSPathChange.FLAG_TRUE.equals(textModeStr)) {
            textModeBool = true;
        } else if (FSPathChange.FLAG_FALSE.equals(textModeStr)) {
            textModeBool = false;
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid text-mod flag in rev-file");
            SVNErrorManager.error(err);
        }

        changeLine = changeLine.substring(delimiterInd + 1);
        delimiterInd = changeLine.indexOf(' ');
        if (delimiterInd == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
            SVNErrorManager.error(err);
        }
        String propModeStr = changeLine.substring(0, delimiterInd);
        
        boolean propModeBool = false;
        if (FSPathChange.FLAG_TRUE.equals(propModeStr)) {
            propModeBool = true;
        } else if (FSPathChange.FLAG_FALSE.equals(propModeStr)) {
            propModeBool = false;
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid prop-mod flag in rev-file");
            SVNErrorManager.error(err);
        }

        String pathStr = changeLine.substring(delimiterInd + 1);
        
        String copyfromPath = null;
        long copyfromRevision = FSRepository.SVN_INVALID_REVNUM;
        
        if (copyfromLine != null && copyfromLine.length() != 0) {
            delimiterInd = copyfromLine.indexOf(' ');

            if (delimiterInd == -1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Invalid changes line in rev-file");
                SVNErrorManager.error(err);
            }
            
            copyfromRevision = Long.parseLong(copyfromLine.substring(0, delimiterInd));
            copyfromPath = copyfromLine.substring(delimiterInd + 1);
        }

        return new FSPathChange(pathStr, nodeRevID, changesKind, textModeBool, propModeBool, copyfromPath, copyfromRevision);
    }

}
