package org.tmatesoft.svn.core.wc2;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;

public interface ISvnMerger extends ISVNMerger {
    
    public SvnMergeResult mergeText(
            File resultFile, 
            File detranslatedTargetAbspath, 
            File leftAbspath, 
            File rightAbspath, 
            String targetLabel, 
            String leftLabel, 
            String rightLabel, 
            SVNDiffOptions options) throws SVNException;
    
    public SvnMergeResult mergeProperties(
            File localAbsPath, 
            SVNNodeKind kind, 
            SVNConflictVersion leftVersion, 
            SVNConflictVersion rightVersion,
            SVNProperties serverBaseProperties, 
            SVNProperties pristineProperties, 
            SVNProperties actualProperties, 
            SVNProperties propChanges,
            boolean baseMerge, 
            boolean dryRun,
            /* out parameters */
            SVNProperties newBaseProperties,
            SVNProperties newActualProperties
            ) throws SVNException;
}
