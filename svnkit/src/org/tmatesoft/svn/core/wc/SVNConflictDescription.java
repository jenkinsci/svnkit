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
     * @param isPropertyConflict    <span class="javakeyword">true</span> if this object describes a property
     *                              conflict; otherwise <span class="javakeyword">false</span> 
     * @param conflictAction        action which lead to the conflict
     * @param conflictReason        why the conflict ever occurred
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
     * Returns the action which attempted on an object and which lead to the conflict.
     * @return  conflicted action  
     */
    public SVNConflictAction getConflictAction() {
        return myConflictAction;
    }
    
    /**
     * Returns the reason why the conflict occurred.
     * @return reason of the conflict 
     */
    public SVNConflictReason getConflictReason() {
        return myConflictReason;
    }
    
    /**
     * Tells whether it's a property merge conflict or not.
     * @return <span class="javakeyword">true</span> if the conflict occurred while modifying a property; 
     *         otherwise <span class="javakeyword">false</span> 
     */
    public boolean isPropertyConflict() {
        return myIsPropertyConflict;
    }
    
    /**
     * Returns the node kind of the item which the conflict occurred on.
     * @return node kind 
     */
    public SVNNodeKind getNodeKind() {
        return myNodeKind;
    }
    
    /**
     * Returns the name of the property on which the conflict occurred.
     * 
     * <p/>
     * Note: relevant only in case of a {@link #isPropertyConflict() property conflict}.  
     * 
     * @return conflicted property name 
     */
    public String getPropertyName() {
        return myPropertyName;
    }

    public String toString() {
        final StringBuffer buffer = new StringBuffer();
        buffer.append("[Conflict descriptor: merge files = ");
        buffer.append(getMergeFiles());
        buffer.append("; kind = ");
        buffer.append(getNodeKind());
        buffer.append("; reason = ");
        buffer.append(getConflictReason());
        buffer.append("; action = ");
        buffer.append(getConflictAction());
        buffer.append("; property conflicts = ");
        buffer.append(isPropertyConflict());
        buffer.append("; property name = ");
        buffer.append(getPropertyName());
        buffer.append("]");
        return buffer.toString();
    }
}
