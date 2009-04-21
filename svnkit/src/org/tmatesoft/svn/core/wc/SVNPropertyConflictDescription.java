/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNNodeKind;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class SVNPropertyConflictDescription extends SVNConflictDescription {

    private String myPropertyName;

    public SVNPropertyConflictDescription(SVNMergeFileSet mergeFiles, SVNNodeKind nodeKind, String propertyName,
            SVNConflictAction conflictAction, SVNConflictReason conflictReason) {
        super(mergeFiles, nodeKind, conflictAction, conflictReason);
        myPropertyName = propertyName;
    }

    public boolean isTextConflict() {
        return false;
    }

    public boolean isPropertyConflict() {
        return true;
    }

    public boolean isTreeConflict() {
        return false;
    }

    public String getPropertyName() {
        return myPropertyName;
    }
}