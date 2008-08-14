/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
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
 * The <b>SVNConflictDescription</b> represents an object that describes a conflict that has occurred in the
 * working copy. It's passed to {@link ISVNConflictHandler#handleConflict(SVNConflictDescription)}.
 * 
 * @version 1.2.0
 * @author  TMate Software Ltd.
 * @since   1.2.0
 */
public class SVNConflictDescription {
    private SVNMergeFileSet myMergeFiles;
    private SVNNodeKind myNodeKind;
    private String myPropertyName;
    private boolean myIsPropertyConflict;
    private SVNConflictAction myConflictAction;
    private SVNConflictReason myConflictReason;

    /**
     * Creates a new <code>SVNConflictDescription</code> object.
     * 
     * <p/>
     * <code>propertyName</code> is relevant only for property conflicts (i.e. in case 
     * <code>isPropertyConflict</code> is <span class="javakeyword">true</span>).
     * 
     * @param mergeFiles            files involved in the merge 
     * @param nodeKind              node kind of the item which the conflict occurred on           
     * @param propertyName          name of the property property which the conflict occurred on          
     * @param isPropertyConflict    if 
     * @param conflictAction 
     * @param conflictReason 
     */
    public SVNConflictDescription(SVNMergeFileSet mergeFiles, SVNNodeKind nodeKind, String propertyName, 
            boolean isPropertyConflict, SVNConflictAction conflictAction, SVNConflictReason conflictReason) {
        myMergeFiles = mergeFiles;
        myNodeKind = nodeKind;
        myPropertyName = propertyName;
        myIsPropertyConflict = isPropertyConflict;
        myConflictAction = conflictAction;
        myConflictReason = conflictReason;
    }

    /**
     * Returns information about files involved in the merge.
     * @return merge file set 
     */
    public SVNMergeFileSet getMergeFiles() {
        return myMergeFiles;
    }
    
    /**
     * 
     */
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
