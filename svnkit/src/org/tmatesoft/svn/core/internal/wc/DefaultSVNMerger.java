/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.SVNMergeAction;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNMergeResult;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import de.regnis.q.sequence.line.QSequenceLineRAData;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class DefaultSVNMerger implements ISVNMerger {
    
    private byte[] myStart;
    private byte[] mySeparator;
    private byte[] myEnd;

    public DefaultSVNMerger(byte[] start, byte[] sep, byte[] end) {
        myStart = start;
        mySeparator = sep;
        myEnd = end;
    }

    public SVNMergeResult merge(SVNMergeFileSet files, boolean binary, boolean dryRun, SVNDiffOptions options) throws SVNException {
        SVNStatusType status;
        if (binary) {
            status = SVNStatusType.CONFLICTED;
        } else {
            status = mergeText(files.getBaseFile(), files.getLocalFile(), files.getRepositoryFile(), options, files.getResultFile());
        }
        if (!binary && status != SVNStatusType.CONFLICTED) {
            // compare merge result with 'wcFile' (in case of text and no conflict).
            boolean isSameContents = SVNFileUtil.compareFiles(files.getWCFile(), files.getResultFile(), null);
            status = isSameContents ? SVNStatusType.UNCHANGED : status;
        }
        return SVNMergeResult.createMergeResult(status, null);
    }

    public SVNMergeAction getMergeAction(SVNMergeFileSet files, boolean binary, SVNMergeResult mergeResult) throws SVNException {
        if (mergeResult.getMergeStatus() == SVNStatusType.CONFLICTED) {
            return SVNMergeAction.MARK_CONFLICTED;
        }
        return SVNMergeAction.CHOOSE_MERGED_FILE;
    }

    public SVNMergeResult processMergedFiles(SVNMergeFileSet files, boolean binary, SVNMergeResult mergeResult, SVNMergeAction mergeAction) throws SVNException {
        Map command = new HashMap();
        SVNLog log = files.getLog();

        if (mergeAction == SVNMergeAction.MARK_CONFLICTED || (files.isBinary() && mergeAction == SVNMergeAction.CHOOSE_MERGED_FILE)) {
            if (binary) {
                handleBinaryConflict(files);
            } else {
                handleTextConflict(files);
            }                
        } else if (mergeAction == SVNMergeAction.CHOOSE_BASE) {
            command.put(SVNLog.NAME_ATTR, files.getBasePath());
            command.put(SVNLog.DEST_ATTR, files.getWCPath());
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();
            mergeResult = SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
        } else if (mergeAction == SVNMergeAction.CHOOSE_REPOSITORY) {
            command.put(SVNLog.NAME_ATTR, files.getRepositoryPath());
            command.put(SVNLog.DEST_ATTR, files.getWCPath());
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();
            mergeResult = SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
        } else if (mergeAction == SVNMergeAction.CHOOSE_WORKING) {
            mergeResult = SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
        } else if (mergeAction == SVNMergeAction.CHOOSE_MERGED_FILE || mergeAction == SVNMergeAction.MARK_RESOLVED) {
            if (mergeAction == SVNMergeAction.CHOOSE_MERGED_FILE && mergeResult.getMergeStatus() != SVNStatusType.CONFLICTED) {
                handleMerge(files, mergeResult);
            } else {
                if (!files.isBinary()) {
                    command.put(SVNLog.NAME_ATTR, files.getResultPath());
                    command.put(SVNLog.DEST_ATTR, files.getWCPath());
                    log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                    command.clear();
                }
                mergeResult = SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
            }
        } 
        postProcessMergedFiles(files);
        
        return mergeResult;
    }

    protected SVNStatusType mergeText(File baseFile, File localFile, File latestFile, SVNDiffOptions options, File resultFile) throws SVNException {
        FSMergerBySequence merger = new FSMergerBySequence(myStart, mySeparator, myEnd);
        int mergeResult = 0;
        RandomAccessFile localIS = null;
        RandomAccessFile latestIS = null;
        RandomAccessFile baseIS = null;
        OutputStream result = null;
        try {
            result = SVNFileUtil.openFileForWriting(resultFile);
            localIS = new RandomAccessFile(localFile, "r");
            latestIS = new RandomAccessFile(latestFile, "r");
            baseIS = new RandomAccessFile(baseFile, "r");

            QSequenceLineRAData baseData = new QSequenceLineRAFileData(baseIS);
            QSequenceLineRAData localData = new QSequenceLineRAFileData(localIS);
            QSequenceLineRAData latestData = new QSequenceLineRAFileData(latestIS);
            mergeResult = merger.merge(baseData, localData, latestData, options, result);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(result);
            SVNFileUtil.closeFile(localIS);
            SVNFileUtil.closeFile(baseIS);
            SVNFileUtil.closeFile(latestIS);
        }

        SVNStatusType status = SVNStatusType.UNCHANGED;
        if (mergeResult == FSMergerBySequence.CONFLICTED) {
            status = SVNStatusType.CONFLICTED;
        } else if (mergeResult == FSMergerBySequence.MERGED) {
            status = SVNStatusType.MERGED;
        }
        return status;
    }

    protected void handleBinaryConflict(SVNMergeFileSet files) throws SVNException {
        Map command = new HashMap();
        File root = files.getAdminArea().getRoot();
        SVNLog log = files.getLog();

        File oldFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getBaseLabel());
        File newFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getRepositoryLabel());
        SVNFileUtil.copyFile(files.getBaseFile(), oldFile, false);
        SVNFileUtil.copyFile(files.getRepositoryFile(), newFile, false);
        
        
        if (!files.getLocalPath().equals(files.getWCPath())) {
            File mineFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getLocalLabel());
            String minePath = SVNFileUtil.getBasePath(mineFile);
            command.put(SVNLog.NAME_ATTR, files.getLocalPath());
            command.put(SVNLog.DEST_ATTR, minePath);
            log.addCommand(SVNLog.MOVE, command, false);
            command.clear();
            command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_WRK), minePath);
        } else {
            command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_WRK), "");
        }

        String newPath = SVNFileUtil.getBasePath(newFile);
        String oldPath = SVNFileUtil.getBasePath(oldFile);
        
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_NEW), newPath);
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_OLD), oldPath);
        log.logChangedEntryProperties(files.getWCPath(), command);
        command.clear();
        files.getAdminArea().saveEntries(false);
    }

    protected void handleTextConflict(SVNMergeFileSet files) throws SVNException {
        Map command = new HashMap();
        File root = files.getAdminArea().getRoot();
        SVNLog log = files.getLog();

        File mineFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getLocalLabel());
        File oldFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getBaseLabel());
        File newFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getRepositoryLabel());
        
        String newPath = SVNFileUtil.getBasePath(newFile);
        String oldPath = SVNFileUtil.getBasePath(oldFile);
        String minePath = SVNFileUtil.getBasePath(mineFile);
        
        File tmpOldFile = files.getBaseFile();
        
        if (!SVNPathUtil.isChildOf(root, tmpOldFile)) {
            tmpOldFile = SVNAdminUtil.createTmpFile(files.getAdminArea());
            SVNFileUtil.copyFile(files.getBaseFile(), tmpOldFile, true);
        }

        File tmpNewFile = files.getRepositoryFile();
        if (!SVNPathUtil.isChildOf(root, tmpNewFile)) {
            tmpNewFile = SVNAdminUtil.createTmpFile(files.getAdminArea());
            SVNFileUtil.copyFile(files.getRepositoryFile(), tmpNewFile, true);
        }
        
        String basePath = SVNFileUtil.getBasePath(tmpOldFile);
        command.put(SVNLog.NAME_ATTR, basePath);
        command.put(SVNLog.DEST_ATTR, oldPath);
        command.put(SVNLog.ATTR2, files.getWCPath());
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();

        String latestPath = SVNFileUtil.getBasePath(tmpNewFile);
        command.put(SVNLog.NAME_ATTR, latestPath);
        command.put(SVNLog.DEST_ATTR, newPath);
        command.put(SVNLog.ATTR2, files.getWCPath());
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();

        if (!tmpOldFile.equals(files.getBaseFile())) {
            command.put(SVNLog.NAME_ATTR, basePath);
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
        }

        if (!tmpNewFile.equals(files.getRepositoryFile())) {
            command.put(SVNLog.NAME_ATTR, latestPath);
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
        }
        
        File tmpTargetCopy = SVNTranslator.getTranslatedFile(files.getAdminArea(), files.getWCPath(), files.getWCFile(), 
                                                             false, false, false, true);
        String tmpTargetCopyPath = SVNFileUtil.getBasePath(tmpTargetCopy);
        command.put(SVNLog.NAME_ATTR, tmpTargetCopyPath);
        command.put(SVNLog.DEST_ATTR, minePath);
        command.put(SVNLog.ATTR2, files.getWCPath());
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();

        if (!tmpTargetCopy.equals(files.getLocalFile())) {
            command.put(SVNLog.NAME_ATTR, tmpTargetCopyPath);
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
        }
        
        command.put(SVNLog.NAME_ATTR, files.getResultPath());
        command.put(SVNLog.DEST_ATTR, files.getWCPath());
        command.put(SVNLog.ATTR2, files.getWCPath());
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();

        command.put(SVNLog.NAME_ATTR, files.getResultPath());
        log.addCommand(SVNLog.DELETE, command, false);
        command.clear();

        
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_WRK), minePath);
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_NEW), newPath);
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_OLD), oldPath);
        log.logChangedEntryProperties(files.getWCPath(), command);
        command.clear();
    }
    
    protected void handleMerge(SVNMergeFileSet files, SVNMergeResult mergeResult) throws SVNException {
        Map command = new HashMap();
        SVNLog log = files.getLog();

        if (mergeResult.getMergeStatus() != SVNStatusType.UNCHANGED) {
            command.put(SVNLog.NAME_ATTR, files.getResultPath());
            command.put(SVNLog.DEST_ATTR, files.getWCPath());
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();
        }
    }

    protected void postProcessMergedFiles(SVNMergeFileSet files) throws SVNException {
        Map command = new HashMap();
        SVNLog log = files.getLog();

        if (!files.getLocalPath().equals(files.getWCPath())) {
            command.put(SVNLog.NAME_ATTR, files.getLocalPath());
            log.addCommand(SVNLog.DELETE, command, false);
            command.clear();
        }
        command.put(SVNLog.NAME_ATTR, files.getWCPath());
        log.addCommand(SVNLog.MAYBE_EXECUTABLE, command, false);
        command.clear();

        command.put(SVNLog.NAME_ATTR, files.getWCPath());
        log.addCommand(SVNLog.MAYBE_READONLY, command, false);
        command.clear();

        command.put(SVNLog.NAME_ATTR, files.getResultPath());
        log.addCommand(SVNLog.DELETE, command, false);
        command.clear();
    }

}
