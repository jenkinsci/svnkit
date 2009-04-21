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
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNTextConflictDescription extends SVNConflictDescription {

    /**
     * Creates a new <code>SVNConflictDescription</code> object.
     *
     * <p/>
     * <code>propertyName</code> is relevant only for property conflicts (i.e. in case
     * <code>isPropertyConflict</code> is <span class="javakeyword">true</span>).
     *
     * @param mergeFiles            files involved in the merge
     * @param nodeKind              node kind of the item which the conflict occurred on
     *                              conflict; otherwise <span class="javakeyword">false</span>
     * @param conflictAction        action which lead to the conflict
     * @param conflictReason        why the conflict ever occurred
     */
    public SVNTextConflictDescription(SVNMergeFileSet mergeFiles, SVNNodeKind nodeKind, SVNConflictAction conflictAction, SVNConflictReason conflictReason) {
        super(mergeFiles, nodeKind, conflictAction, conflictReason);
    }

    public boolean isTextConflict() {
        return true;
    }

    public boolean isPropertyConflict() {
        return false;
    }

    public boolean isTreeConflict() {
        return false;
    }
    
    public String getPropertyName() {
        return null;
    }
}