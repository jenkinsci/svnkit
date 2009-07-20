/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.util.List;
import java.util.Map;
import java.io.File;

/**
 * The <b>ISVNMergeStrategy</b> interface defines a number of methods
 * to be used for directories merging.
 *
 * Alternative implementation is extended merge feature, it tracks
 * copied and moved files to apply appropriate deltas to them.
 * 
 *
 * @author TMate Software Ltd.
 * @version 1.3
 */
public interface ISVNMergeStrategy {

    void preDirectoryMerge(SVNURL url1, long revision1, SVNURL url2, long revision2, SVNEntry parentEntry, SVNAdminArea adminArea, SVNDepth depth) throws SVNException;

    SVNRemoteDiffEditor getMergeReportEditor(long defaultStart, long revision, SVNAdminArea adminArea, SVNDepth depth, AbstractDiffCallback mergeCallback, SVNRemoteDiffEditor editor) throws SVNException;

    SVNRemoteDiffEditor driveMergeReportEditor(File targetWCPath, SVNURL url1, long revision1, SVNURL url2, long revision2, List childrenWithMergeInfo, boolean isRollBack, SVNDepth depth, SVNAdminArea adminArea, SVNMergeCallback mergeCallback, SVNRemoteDiffEditor editor) throws SVNException;

    ISVNEntryHandler getMergeInfoEntryHandler(String mergeSrcPath, SVNURL sourceRootURL, long revision1, long revision2, SVNRepository repository, SVNDepth depth, List childrenWithMergeInfo);

    boolean isRecordMergeInfo();

    Object[] calculateRemainingRangeList(File targetFile, SVNEntry entry, SVNURL sourceRoot, boolean[] indirect, SVNURL url1, long revision1, SVNURL url2, long revision2, SVNMergeRange range) throws SVNException;

    Map calculateImplicitMergeInfo(SVNRepository repos, SVNURL url, long[] targetRev, long start, long end) throws SVNException;
}
