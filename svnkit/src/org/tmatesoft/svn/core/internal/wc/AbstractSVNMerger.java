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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNMergeAction;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNMergeResult;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class AbstractSVNMerger implements ISVNMerger {
    
    private byte[] myStart;
    private byte[] mySeparator;
    private byte[] myEnd;

    protected AbstractSVNMerger(byte[] start, byte[] sep, byte[] end) {
        myStart = start;
        mySeparator = sep;
        myEnd = end;
    }
    
    protected byte[] getConflictSeparatorMarker() {
        return mySeparator;
    }
    
    protected byte[] getConflictStartMarker() {
        return myStart;
    }

    protected byte[] getConflictEndMarker() {
        return myEnd;
    }

    public SVNMergeResult merge(SVNMergeFileSet files, boolean dryRun, SVNDiffOptions options) throws SVNException {
        SVNStatusType status;
        if (files.isBinary()) {
            status = mergeBinary(files.getBaseFile(), files.getLocalFile(), files.getRepositoryFile(), options, files.getResultFile());
        } else {
            status = mergeText(files.getBaseFile(), files.getLocalFile(), files.getRepositoryFile(), options, files.getResultFile());
        }
        if (!files.isBinary() && status != SVNStatusType.CONFLICTED) {
            // compare merge result with 'wcFile' (in case of text and no conflict).
            boolean isSameContents = SVNFileUtil.compareFiles(files.getWCFile(), files.getResultFile(), null);
            status = isSameContents ? SVNStatusType.UNCHANGED : status;
        }
        return SVNMergeResult.createMergeResult(status, null);
    }

    public SVNMergeResult processMergedFiles(SVNMergeFileSet files) throws SVNException {
        SVNMergeResult mergeResult = files.getMergeResult();
        SVNMergeAction mergeAction = files.getMergeAction();

        if (mergeAction == SVNMergeAction.MARK_CONFLICTED) {
            mergeResult = handleMarkConflicted(files);
        } else if (mergeAction == SVNMergeAction.CHOOSE_BASE) {
            mergeResult = handleChooseBase(files);
        } else if (mergeAction == SVNMergeAction.CHOOSE_REPOSITORY) {
            mergeResult = handleChooseRepository(files);
        } else if (mergeAction == SVNMergeAction.CHOOSE_WORKING) {
            mergeResult = handleChooseWorking(files);
        } else if (mergeAction == SVNMergeAction.CHOOSE_MERGED_FILE) {
            mergeResult = handleChooseMerged(files);
        } else if (mergeAction == SVNMergeAction.MARK_RESOLVED) {
            mergeResult = handleMarkResolved(files);
        }
        
        postMergeCleanup(files);
        return mergeResult;
    }

    protected abstract SVNStatusType mergeText(File baseFile, File localFile, File repositoryFile, SVNDiffOptions options, File resultFile) throws SVNException;

    protected abstract SVNStatusType mergeBinary(File baseFile, File localFile, File repositoryFile, SVNDiffOptions options, File resultFile) throws SVNException;

    protected abstract SVNMergeResult handleMarkConflicted(SVNMergeFileSet files) throws SVNException;

    protected abstract SVNMergeResult handleMarkResolved(SVNMergeFileSet files) throws SVNException;;

    protected abstract SVNMergeResult handleChooseMerged(SVNMergeFileSet files) throws SVNException;

    protected abstract SVNMergeResult handleChooseWorking(SVNMergeFileSet files) throws SVNException;

    protected abstract SVNMergeResult handleChooseRepository(SVNMergeFileSet files) throws SVNException;

    protected abstract SVNMergeResult handleChooseBase(SVNMergeFileSet files) throws SVNException;

    protected abstract void postMergeCleanup(SVNMergeFileSet files) throws SVNException;
}
