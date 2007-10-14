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


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeResult {
    
    private SVNStatusType myMergeStatus;
    private SVNConflictReason myConflictReason;

    private SVNMergeResult(SVNStatusType status, SVNConflictReason conflictReason) {
        myMergeStatus = status;
        myConflictReason = conflictReason;
    }
    
    public static SVNMergeResult createMergeResult(SVNStatusType status, SVNConflictReason reason) {
        if (status == SVNStatusType.CONFLICTED) {
            if (reason == null) {
                reason = SVNConflictReason.EDITED;
            }
        } else {
            reason = null;
        }
        return new SVNMergeResult(status, reason);
    }
    
    public SVNStatusType getMergeStatus() {
        return myMergeStatus;
    }
    
    public SVNConflictReason getConflictReason() {
        return myConflictReason;
    }

}
