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
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNMergeAction;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNMergeResult;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import de.regnis.q.sequence.line.QSequenceLineRAData;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class DefaultSVNMerger extends AbstractSVNMerger implements ISVNMerger {
    

    public DefaultSVNMerger(byte[] start, byte[] sep, byte[] end) {
        super(start, sep, end);
    }

    public SVNMergeAction getMergeAction(SVNMergeFileSet files) throws SVNException {
        if (files.getMergeResult().getMergeStatus() == SVNStatusType.CONFLICTED) {
            return SVNMergeAction.MARK_CONFLICTED;
        }
        return SVNMergeAction.CHOOSE_MERGED_FILE;
    }

    protected SVNStatusType mergeBinary(File baseFile, File localFile, File repositoryFile, SVNDiffOptions options, File resultFile) throws SVNException {
        return SVNStatusType.CONFLICTED;
    }

    protected SVNStatusType mergeText(File baseFile, File localFile, File latestFile, SVNDiffOptions options, File resultFile) throws SVNException {
        FSMergerBySequence merger = new FSMergerBySequence(getConflictStartMarker(), getConflictSeparatorMarker(), getConflictEndMarker());
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
    
    protected SVNMergeResult handleChooseBase(SVNMergeFileSet files) throws SVNException {
        Map command = new HashMap();
        SVNLog log = files.getLog();

        command.put(SVNLog.NAME_ATTR, files.getBasePath());
        command.put(SVNLog.DEST_ATTR, files.getWCPath());
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();
        return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
    }
    
    protected SVNMergeResult handleChooseRepository(SVNMergeFileSet files) throws SVNException {
        Map command = new HashMap();
        SVNLog log = files.getLog();
        
        command.put(SVNLog.NAME_ATTR, files.getRepositoryPath());
        command.put(SVNLog.DEST_ATTR, files.getWCPath());
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();

        return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
    }
    
    protected SVNMergeResult handleChooseWorking(SVNMergeFileSet files) throws SVNException {
        return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);        
    }

    protected SVNMergeResult handleMarkConflicted(SVNMergeFileSet files) throws SVNException {
        if (files.isBinary()) {
            return handleMarkBinaryConflicted(files);
        }
        return handleMarkTextConflicted(files);                

    }

    protected SVNMergeResult handleMarkBinaryConflicted(SVNMergeFileSet files) throws SVNException {
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
        
        makeBinaryConflictEntry(files, newPath, oldPath);

        return SVNMergeResult.createMergeResult(SVNStatusType.CONFLICTED, null);
    }
    
    protected void makeBinaryConflictEntry(SVNMergeFileSet files, String newFilePath, String oldFilePath) throws SVNException {
        Map command = new HashMap();
        SVNLog log = files.getLog();

        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_NEW), newFilePath);
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_OLD), oldFilePath);
        log.logChangedEntryProperties(files.getWCPath(), command);
        command.clear();
        files.getAdminArea().saveEntries(false);
    }

    protected SVNMergeResult handleMarkTextConflicted(SVNMergeFileSet files) throws SVNException {
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
        makeTextConflictEntry(files, minePath, newPath, oldPath);
        
        return SVNMergeResult.createMergeResult(SVNStatusType.CONFLICTED, null);
    }

    protected void makeTextConflictEntry(SVNMergeFileSet files, String mineFilePath, String newFilePath, String oldFilePath) throws SVNException {
        Map command = new HashMap();
        SVNLog log = files.getLog();
        
        command.put(SVNLog.NAME_ATTR, files.getResultPath());
        command.put(SVNLog.DEST_ATTR, files.getWCPath());
        command.put(SVNLog.ATTR2, files.getWCPath());
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();
        
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_WRK), mineFilePath);
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_NEW), newFilePath);
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_OLD), oldFilePath);
        log.logChangedEntryProperties(files.getWCPath(), command);
        command.clear();
    }
    
    protected SVNMergeResult handleChooseMerged(SVNMergeFileSet files) throws SVNException {
        Map command = new HashMap();
        SVNLog log = files.getLog();
        SVNMergeResult mergeResult = files.getMergeResult();
        if (mergeResult.getMergeStatus() != SVNStatusType.CONFLICTED) {
            // do normal merge.
            if (files.getMergeResult().getMergeStatus() != SVNStatusType.UNCHANGED) {
                command.put(SVNLog.NAME_ATTR, files.getResultPath());
                command.put(SVNLog.DEST_ATTR, files.getWCPath());
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();
            }
            return files.getMergeResult();
        } else if (files.isBinary()) {
            // this action is not applicable for binary conflited files.
            return handleMarkConflicted(files);
        } else {
            // for text file we could use merged version in case of conflict.
            command.put(SVNLog.NAME_ATTR, files.getResultPath());
            command.put(SVNLog.DEST_ATTR, files.getWCPath());
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();
            return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
        }
    }
    

    protected SVNMergeResult handleMarkResolved(SVNMergeFileSet files) throws SVNException {
        if (!files.isBinary()) {
            // same as choose merged.
            return handleChooseMerged(files);
        }
        // same as choose working.
        return handleChooseWorking(files);
    }

    protected void postMergeCleanup(SVNMergeFileSet files) throws SVNException {
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
