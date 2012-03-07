package org.tmatesoft.svn.core.wc2;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;

/**
 * Implement this interface additionally to ISVNMerger, to provide custom text and properties merge code
 * for the 1.7 working copy format.
 */
public interface ISvnMerger extends ISVNMerger {
    
    public SvnMergeResult mergeText(
            ISvnMerger baseMerger,
            File resultFile, 
            File targetAbspath, 
            File detranslatedTargetAbspath, 
            File leftAbspath, 
            File rightAbspath, 
            String targetLabel, 
            String leftLabel, 
            String rightLabel, 
            SVNDiffOptions options) throws SVNException;
    
    public SvnMergeResult mergeProperties(
            ISvnMerger baseMerger,
            File localAbsPath, 
            SVNNodeKind kind, 
            SVNConflictVersion leftVersion, 
            SVNConflictVersion rightVersion,
            SVNProperties serverBaseProperties, 
            SVNProperties pristineProperties, 
            SVNProperties actualProperties, 
            SVNProperties propChanges,
            boolean baseMerge, 
            boolean dryRun) throws SVNException;
}
