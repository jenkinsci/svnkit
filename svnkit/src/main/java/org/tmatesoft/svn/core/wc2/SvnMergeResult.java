package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNStatusType;

public class SvnMergeResult {
    
    private final SVNStatusType mergeOutcome;
    private final SVNConflictReason conflictReason;
    
    private SvnMergeResult(SVNStatusType mergeOutcome, SVNConflictReason conflictReason) {
        this.mergeOutcome = mergeOutcome;
        this.conflictReason = conflictReason;
    }
    
    public SVNStatusType getMergeOutcome() {
        return mergeOutcome;
    }
    
    public SVNConflictReason getConflictReason() {
        return conflictReason;
    }
}
