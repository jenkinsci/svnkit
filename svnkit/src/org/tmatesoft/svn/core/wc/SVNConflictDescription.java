/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNNodeKind;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNConflictDescription {
    private SVNMergeFileSet myMergeFiles;
    private SVNNodeKind myNodeKind;
    private String myPropertyName;
    private boolean myIsPropertyConflict;
    private SVNConflictAction myConflictAction;
    private SVNConflictReason myConflictReason;
    
    public SVNConflictDescription(SVNMergeFileSet mergeFiles, SVNNodeKind nodeKind, String propertyName, 
            boolean isPropertyConflict, SVNConflictAction conflictAction, SVNConflictReason conflictReason) {
        myMergeFiles = mergeFiles;
        myNodeKind = nodeKind;
        myPropertyName = propertyName;
        myIsPropertyConflict = isPropertyConflict;
        myConflictAction = conflictAction;
        myConflictReason = conflictReason;
    }

    public SVNMergeFileSet getMergeFiles() {
        return myMergeFiles;
    }
    
    public SVNConflictAction getConflictAction() {
        return myConflictAction;
    }
    
    public SVNConflictReason getConflictReason() {
        return myConflictReason;
    }
    
    public boolean isPropertyConflict() {
        return myIsPropertyConflict;
    }
    
    public SVNNodeKind getNodeKind() {
        return myNodeKind;
    }
    
    public String getPropertyName() {
        return myPropertyName;
    }

}
