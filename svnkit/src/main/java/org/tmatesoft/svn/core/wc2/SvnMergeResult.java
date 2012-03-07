package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.wc.SVNStatusType;

public class SvnMergeResult {
    
    private final SVNStatusType mergeOutcome;
    private SVNProperties actualProperties;
    private SVNProperties baseProperties;

    public static SvnMergeResult create(SVNStatusType mergeOutcome) {
        return new SvnMergeResult(mergeOutcome);
    }
    
    private SvnMergeResult(SVNStatusType mergeOutcome) {
        this.mergeOutcome = mergeOutcome;
    }
    
    public SVNStatusType getMergeOutcome() {
        return mergeOutcome;
    }

    public SVNProperties getActualProperties() {
        if (actualProperties == null) {
            actualProperties = new SVNProperties();
        }
        return actualProperties;
    }

    public void setActualProperties(SVNProperties actualProperties) {
        this.actualProperties = actualProperties;
    }

    public SVNProperties getBaseProperties() {
        if (baseProperties == null) {
            baseProperties = new SVNProperties();
        }
        return baseProperties;
    }

    public void setBaseProperties(SVNProperties baseProperties) {
        this.baseProperties = baseProperties;
    }
}
