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
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNMergeResult;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import de.regnis.q.sequence.line.QSequenceLineRAByteData;
import de.regnis.q.sequence.line.QSequenceLineRAData;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class DefaultSVNMerger extends AbstractSVNMerger implements ISVNMerger {

	private static List STATUS_ORDERING = new LinkedList(); 
	static {
		STATUS_ORDERING.add(SVNStatusType.UNKNOWN);
		STATUS_ORDERING.add(SVNStatusType.UNCHANGED);
		STATUS_ORDERING.add(SVNStatusType.INAPPLICABLE);
		STATUS_ORDERING.add(SVNStatusType.CHANGED);
		STATUS_ORDERING.add(SVNStatusType.MERGED);
		STATUS_ORDERING.add(SVNStatusType.OBSTRUCTED); 
		STATUS_ORDERING.add(SVNStatusType.CONFLICTED);
	};
	
    private ISVNConflictHandler myConflictCallback;

    public DefaultSVNMerger(byte[] start, byte[] sep, byte[] end) {
        this(start, sep, end, null);
    }

    public DefaultSVNMerger(byte[] start, byte[] sep, byte[] end, ISVNConflictHandler callback) {
        super(start, sep, end);
        myConflictCallback = callback;
    }

	public SVNMergeResult mergeProperties(SVNVersionedProperties workingProperties, 
			SVNVersionedProperties baseProperties, SVNProperties serverBaseProps, 
			SVNProperties propDiff,	boolean baseMerge, boolean dryRun, boolean isDir) throws SVNException {
        propDiff = propDiff == null ? SVNProperties.EMPTY_PROPERTIES : propDiff;
/*
        if (serverBaseProps == null) {
            serverBaseProps = baseProperties.asMap();
        }

        Collection conflicts = new LinkedList();
        SVNStatusType result = propDiff.isEmpty() ? SVNStatusType.UNCHANGED : SVNStatusType.CHANGED;

        for (Iterator propEntries = propDiff.nameSet().iterator(); propEntries.hasNext();) {
            String propName = (String) propEntries.next();
            SVNPropertyValue toValue = propDiff.getSVNPropertyValue(propName);
            SVNPropertyValue nullValue = new SVNPropertyValue(propName, (String) null);
            SVNPropertyValue fromValue = serverBaseProps.getSVNPropertyValue(propName);
            if (fromValue == null) {
            	fromValue = nullValue;	
            }
            SVNPropertyValue workingValue = workingProperties.getPropertyValue(propName);
            workingValue = workingValue == null ? nullValue : workingValue;
            SVNPropertyValue baseValue = baseProperties.getPropertyValue(propName);
            baseValue = baseValue == null ? nullValue : baseValue;
            boolean isNormal = SVNProperty.isRegularProperty(propName);
            if (baseMerge) {
                baseProperties.setPropertyValue(propName, toValue);
            }            

            result = isNormal ? SVNStatusType.CHANGED : result;
            if (fromValue.hasNullValue()) {
                if (!baseValue.hasNullValue()) {
                    if (!workingValue.hasNullValue()) {
                        if (workingValue.equals(toValue)) {
                            result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                        } else {
                            result = isNormal ? SVNStatusType.CONFLICTED : result;
                            conflicts.add(MessageFormat.format("Trying to create property ''{0}'' with value ''{1}'',\n" +
                                                               "but it already exists.",
                                                               new Object[] { propName, SVNPropertyValue.getPropertyAsString(toValue) }));
                        }
                    } else {
                        result = isNormal ? SVNStatusType.CONFLICTED : result;
                        conflicts.add(MessageFormat.format("Trying to create property ''{0}'' with value ''{1}'',\n" +
                                                           "but it has been locally deleted.",
                                                           new Object[] { propName, SVNPropertyValue.getPropertyAsString(toValue) }));
                    }
                } else if (!workingValue.hasNullValue()) {
                    if (workingValue.equals(toValue)) {
                        result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                    } else {
                        if (SVNProperty.MERGE_INFO.equals(propName)) {
                            toValue = new SVNPropertyValue(SVNProperty.MERGE_INFO, SVNMergeInfoManager.combineMergeInfoProperties(workingValue.getString(), toValue.getString()));
                            working.setPropertyValue(propName, toValue);
                            result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                        } else {
                            result = isNormal ? SVNStatusType.CONFLICTED : result;
                            conflicts.add(MessageFormat.format("Trying to add new property ''{0}'' with value ''{1}'',\n" +
                                                               "but property already exists with value ''{2}''.",
                                                               new Object[] { propName, SVNPropertyValue.getPropertyAsString(toValue),
                                                               SVNPropertyValue.getPropertyAsString(workingValue) }));
                        }
                    }
                } else {
                    working.setPropertyValue(propName, toValue);
                }
            } else if (toValue.hasNullValue()) {
                if (baseValue.hasNullValue()) {
                    if (!workingValue.hasNullValue()) {
                        working.setPropertyValue(propName, null);
                    }
                    result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                } else if (baseValue.equals(fromValue)) {
                    if (!workingValue.hasNullValue()) {
                        if (workingValue.equals(fromValue)) {
                            working.setPropertyValue(propName, toValue);
                        } else {
                            result = isNormal ? SVNStatusType.CONFLICTED : result;
                            conflicts.add(MessageFormat.format("Trying to delete property ''{0}'' with value ''{1}''\n " +
                                                               "but it has been modified from ''{2}'' to ''{3}''.",
                                                               new Object[] { propName, SVNPropertyValue.getPropertyAsString(fromValue),
                                                                       SVNPropertyValue.getPropertyAsString(baseValue),
                                                                       SVNPropertyValue.getPropertyAsString(workingValue) }));
                        }
                    } else {
                        result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                    }
                } else {
                    result = isNormal ? SVNStatusType.CONFLICTED : result;
                    conflicts.add(MessageFormat.format("Trying to delete property ''{0}'' with value ''{1}''\n " +
                                                       "but the local value is ''{2}''.",
                                                       new Object[] { propName, SVNPropertyValue.getPropertyAsString(baseValue),
                                                               SVNPropertyValue.getPropertyAsString(workingValue) }));
                }
            } else {
                if ((!workingValue.hasNullValue() && baseValue.hasNullValue()) ||
                    (workingValue.hasNullValue() && !baseValue.hasNullValue()) ||
                    (!workingValue.hasNullValue() && !baseValue.hasNullValue() && !workingValue.equals(baseValue))) {
                    if (!workingValue.hasNullValue()) {
                        if (workingValue.equals(toValue)) {
                            result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                        } else {
                            if (SVNProperty.MERGE_INFO.equals(propName)) {
                                toValue = new SVNPropertyValue(SVNProperty.MERGE_INFO, SVNMergeInfoManager.combineForkedMergeInfoProperties(fromValue.getString(),
                                                                                               workingValue.getString(),
                                                                                               toValue.getString()));
                                working.setPropertyValue(propName, toValue);
                                result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                            } else {
                                result = isNormal ? SVNStatusType.CONFLICTED : result;
                                if (!baseValue.hasNullValue()) {
                                    conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                                                       "but property has been locally changed from ''{3}'' to ''{4}''.",
                                                                       new Object[] { propName,
                                                                                      SVNPropertyValue.getPropertyAsString(fromValue),
                                                                                      SVNPropertyValue.getPropertyAsString(toValue),
                                                                                      SVNPropertyValue.getPropertyAsString(baseValue),
                                                                                      SVNPropertyValue.getPropertyAsString(workingValue) }));
                                } else {
                                    conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                                                       "but property has been locally added with value ''{3}''",
                                                                       new Object[] { propName,
                                                                                      SVNPropertyValue.getPropertyAsString(fromValue),
                                                                                      SVNPropertyValue.getPropertyAsString(toValue),
                                                                                      SVNPropertyValue.getPropertyAsString(workingValue) }));
                                }
                            }
                        }
                    } else {
                        result = isNormal ? SVNStatusType.CONFLICTED : result;
                        conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                                           "but it has been locally deleted.",
                                                           new Object[] { propName,
                                                                          SVNPropertyValue.getPropertyAsString(fromValue),
                                                                          SVNPropertyValue.getPropertyAsString(toValue) }));
                    }
                } else if (workingValue.hasNullValue()) {
                    if (SVNProperty.MERGE_INFO.equals(propName)) {
                        Map addedMergeInfo = new TreeMap();
                        SVNMergeInfoManager.diffMergeInfoProperties(null, addedMergeInfo, fromValue.getString(), null, toValue.getString(), null);
                        toValue = new SVNPropertyValue(SVNProperty.MERGE_INFO, SVNMergeInfoManager.formatMergeInfoToString(addedMergeInfo));
                        working.setPropertyValue(propName, toValue);
                    } else {
                        result = isNormal ? SVNStatusType.CONFLICTED : result;
                        conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                                           "but the property does not exist.",
                                                           new Object[] { propName,
                                                                          SVNPropertyValue.getPropertyAsString(fromValue),
                                                                          SVNPropertyValue.getPropertyAsString(toValue) }));
                    }

                } else {
                    if (baseValue.equals(fromValue)) {
                        working.setPropertyValue(propName, toValue);
                    } else {
                        if (SVNProperty.MERGE_INFO.equals(propName)) {
                            toValue = new SVNPropertyValue(SVNProperty.MERGE_INFO, SVNMergeInfoManager.combineForkedMergeInfoProperties(fromValue.getString(),
                                                                                           workingValue.getString(),
                                                                                           toValue.getString()));
                            working.setPropertyValue(propName, toValue);
                            result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                        } else {
                            result = isNormal ? SVNStatusType.CONFLICTED : result;

                            conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                                               "but property already exists with value ''{3}''.",
                                                               new Object[] { propName,
                                                                              SVNPropertyValue.getPropertyAsString(fromValue),
                                                                              SVNPropertyValue.getPropertyAsString(toValue),
                                                                              SVNPropertyValue.getPropertyAsString(workingValue) }));
                        }

                    }
                }
            }
        }
*/
		
		return null;
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

	protected SVNMergeResult processMergedFiles(SVNMergeFileSet files, SVNMergeResult mergeResult) throws SVNException {
	    DefaultSVNMergerAction mergeAction = getMergeAction(files, mergeResult);

	    if (mergeAction == DefaultSVNMergerAction.MARK_CONFLICTED) {
	        mergeResult = handleMarkConflicted(files);
	    } else if (mergeAction == DefaultSVNMergerAction.CHOOSE_BASE) {
	        mergeResult = handleChooseBase(files);
	    } else if (mergeAction == DefaultSVNMergerAction.CHOOSE_REPOSITORY) {
	        mergeResult = handleChooseRepository(files);
	    } else if (mergeAction == DefaultSVNMergerAction.CHOOSE_WORKING) {
	        mergeResult = handleChooseWorking(files);
	    } else if (mergeAction == DefaultSVNMergerAction.CHOOSE_MERGED_FILE) {
	        mergeResult = handleChooseMerged(files, mergeResult);
	    } else if (mergeAction == DefaultSVNMergerAction.MARK_RESOLVED) {
	        mergeResult = handleMarkResolved(files, mergeResult);
	    }

	    postMergeCleanup(files);
	    return mergeResult;
	}

	protected DefaultSVNMergerAction getMergeAction(SVNMergeFileSet files, SVNMergeResult mergeResult) throws SVNException {
	    if (mergeResult.getMergeStatus() == SVNStatusType.CONFLICTED) {
	        if (myConflictCallback != null) {
                SVNConflictDescription descr = new SVNConflictDescription(files, SVNNodeKind.FILE, null, false, 
                        SVNConflictAction.EDIT, SVNConflictReason.EDITED);
                SVNConflictResult result = myConflictCallback.handleConflict(descr);
                SVNConflictChoice choice = result.getConflictChoice();
                if (choice == SVNConflictChoice.BASE) {
                    return DefaultSVNMergerAction.CHOOSE_BASE;                        
                } else if (choice == SVNConflictChoice.MERGED) {
                    return DefaultSVNMergerAction.CHOOSE_MERGED_FILE;                        
                } else if (choice == SVNConflictChoice.MINE) {
                    return DefaultSVNMergerAction.CHOOSE_WORKING;                        
                } else if (choice == SVNConflictChoice.THEIRS) {
                    return DefaultSVNMergerAction.CHOOSE_REPOSITORY;                        
                }
	        }
	        return DefaultSVNMergerAction.MARK_CONFLICTED;
	    }
	    return DefaultSVNMergerAction.CHOOSE_MERGED_FILE;
    }
    
    protected SVNMergeResult handleChooseBase(SVNMergeFileSet files) throws SVNException {
        SVNProperties command = new SVNProperties();
        SVNLog log = files.getLog();

        command.put(SVNLog.NAME_ATTR, files.getBasePath());
        command.put(SVNLog.DEST_ATTR, files.getWCPath());
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();
        return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
    }
    
    protected SVNMergeResult handleChooseRepository(SVNMergeFileSet files) throws SVNException {
        SVNProperties command = new SVNProperties();
        SVNLog log = files.getLog();
        
        command.put(SVNLog.NAME_ATTR, files.getRepositoryPath());
        command.put(SVNLog.DEST_ATTR, files.getWCPath());
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();

        return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);
    }
    
    protected SVNMergeResult handleChooseWorking(SVNMergeFileSet files) throws SVNException {
        if (files == null) {
            SVNErrorManager.cancel("");
        }
        return SVNMergeResult.createMergeResult(SVNStatusType.MERGED, null);        
    }

    protected SVNMergeResult handleMarkConflicted(SVNMergeFileSet files) throws SVNException {
        if (files.isBinary()) {
            return handleMarkBinaryConflicted(files);
        }
        return handleMarkTextConflicted(files);                

    }

    protected SVNMergeResult handleMarkBinaryConflicted(SVNMergeFileSet files) throws SVNException {
        SVNProperties command = new SVNProperties();
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
        SVNProperties command = new SVNProperties();
        SVNLog log = files.getLog();

        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_NEW), newFilePath);
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_OLD), oldFilePath);
        log.logChangedEntryProperties(files.getWCPath(), command);
        command.clear();
        files.getAdminArea().saveEntries(false);
    }

    protected SVNMergeResult handleMarkTextConflicted(SVNMergeFileSet files) throws SVNException {
        SVNProperties command = new SVNProperties();
        File root = files.getAdminArea().getRoot();
        SVNLog log = files.getLog();

        File mineFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getLocalLabel());
        File oldFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getBaseLabel());
        File newFile = SVNFileUtil.createUniqueFile(root, files.getWCPath(), files.getRepositoryLabel());
        
        String newPath = SVNFileUtil.getBasePath(newFile);
        String oldPath = SVNFileUtil.getBasePath(oldFile);
        String minePath = SVNFileUtil.getBasePath(mineFile);
        
        String basePath = files.getBasePath();
        command.put(SVNLog.NAME_ATTR, basePath);
        command.put(SVNLog.DEST_ATTR, oldPath);
        command.put(SVNLog.ATTR2, files.getWCPath());
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();

        String latestPath = files.getRepositoryPath();
        command.put(SVNLog.NAME_ATTR, latestPath);
        command.put(SVNLog.DEST_ATTR, newPath);
        command.put(SVNLog.ATTR2, files.getWCPath());
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();

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

        makeTextConflictEntry(files, minePath, newPath, oldPath);
        
        return SVNMergeResult.createMergeResult(SVNStatusType.CONFLICTED, null);
    }

    protected void makeTextConflictEntry(SVNMergeFileSet files, String mineFilePath, String newFilePath, String oldFilePath) throws SVNException {
        SVNProperties command = new SVNProperties();
        SVNLog log = files.getLog();
        
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_WRK), mineFilePath);
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_NEW), newFilePath);
        command.put(SVNProperty.shortPropertyName(SVNProperty.CONFLICT_OLD), oldFilePath);
        log.logChangedEntryProperties(files.getWCPath(), command);
        command.clear();
    }
    
    protected SVNMergeResult handleChooseMerged(SVNMergeFileSet files, SVNMergeResult mergeResult) throws SVNException {
        SVNProperties command = new SVNProperties();
        SVNLog log = files.getLog();
        if (mergeResult.getMergeStatus() != SVNStatusType.CONFLICTED) {
            // do normal merge.
            if (mergeResult.getMergeStatus() != SVNStatusType.UNCHANGED) {
                command.put(SVNLog.NAME_ATTR, files.getResultPath());
                command.put(SVNLog.DEST_ATTR, files.getWCPath());
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();
            }
            return mergeResult;
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
    

    protected SVNMergeResult handleMarkResolved(SVNMergeFileSet files, SVNMergeResult mergeResult) throws SVNException {
        if (!files.isBinary()) {
            // same as choose merged.
            return handleChooseMerged(files, mergeResult);
        }
        // same as choose working.
        return handleChooseWorking(files);
    }

    protected void postMergeCleanup(SVNMergeFileSet files) throws SVNException {
        SVNProperties command = new SVNProperties();
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

    /*
    private SVNStatusType applySinglePropertyAdd(SVNStatusType status, SVNVersionedProperties workingProps, 
    		String propName, SVNPropertyValue baseValue, SVNPropertyValue newValue) {
        SVNPropertyValue workingValue = workingProps.getPropertyValue(propName);
    	
        if (workingValue != null && !workingValue.hasNullValue()) {
            if (workingValue.equals(newValue)) {
                status = setPropMergeStatus(status, SVNStatusType.MERGED);
            } else {
                if (SVNProperty.MERGE_INFO.equals(propName)) {
                    newValue = new SVNPropertyValue(SVNProperty.MERGE_INFO, 
                    		SVNMergeInfoManager.combineMergeInfoProperties(workingValue.getString(), 
                    				newValue.getString()));
                    
                    workingProps.setPropertyValue(propName, newValue);
                    status = setPropMergeStatus(status, SVNStatusType.MERGED);
                } else {
                    result = isNormal ? SVNStatusType.CONFLICTED : result;
                    conflicts.add(MessageFormat.format("Trying to add new property ''{0}'' with value ''{1}'',\n" +
                                                       "but property already exists with value ''{2}''.",
                                                       new Object[] { propName, SVNPropertyValue.getPropertyAsString(toValue),
                                                       SVNPropertyValue.getPropertyAsString(workingValue) }));
                }
            }

    	}
    	if (!baseValue.hasNullValue()) {
            if (!workingValue.hasNullValue()) {
                if (workingValue.equals(toValue)) {
                    result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                } else {
                    result = isNormal ? SVNStatusType.CONFLICTED : result;
                    conflicts.add(MessageFormat.format("Trying to create property ''{0}'' with value ''{1}'',\n" +
                                                       "but it already exists.",
                                                       new Object[] { propName, SVNPropertyValue.getPropertyAsString(toValue) }));
                }
            } else {
                result = isNormal ? SVNStatusType.CONFLICTED : result;
                conflicts.add(MessageFormat.format("Trying to create property ''{0}'' with value ''{1}'',\n" +
                                                   "but it has been locally deleted.",
                                                   new Object[] { propName, SVNPropertyValue.getPropertyAsString(toValue) }));
            }
        } else if (!workingValue.hasNullValue()) {
            if (workingValue.equals(toValue)) {
                result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
            } else {
                if (SVNProperty.MERGE_INFO.equals(propName)) {
                    toValue = new SVNPropertyValue(SVNProperty.MERGE_INFO, SVNMergeInfoManager.combineMergeInfoProperties(workingValue.getString(), toValue.getString()));
                    working.setPropertyValue(propName, toValue);
                    result = result != SVNStatusType.CONFLICTED && isNormal ? SVNStatusType.MERGED : result;
                } else {
                    result = isNormal ? SVNStatusType.CONFLICTED : result;
                    conflicts.add(MessageFormat.format("Trying to add new property ''{0}'' with value ''{1}'',\n" +
                                                       "but property already exists with value ''{2}''.",
                                                       new Object[] { propName, SVNPropertyValue.getPropertyAsString(toValue),
                                                       SVNPropertyValue.getPropertyAsString(workingValue) }));
                }
            }
        } else {
            working.setPropertyValue(propName, toValue);
        }

    }

    private static SVNStatusType setPropMergeStatus(SVNStatusType status, SVNStatusType newStatus) {
    	if (status == null) {
    		return null;
    	}
    	
    	int statusInd = STATUS_ORDERING.indexOf(status);
    	int newStatusInd = STATUS_ORDERING.indexOf(newStatus);
    	if (newStatusInd <= statusInd) {
    		return status;
    	}
    	return newStatus;
    	
    }
    
    private boolean maybeGeneratePropConflict(File path, SVNPropertyValue oldValue, SVNPropertyValue newValue, 
    		SVNPropertyValue baseValue, SVNPropertyValue workingValue) throws SVNException {
    	if (myConflictCallback == null) {
    		return true;
    	}
    	
    	File workingFile = null;
    	if (workingValue != null && !workingValue.hasNullValue()) {
    		workingFile = SVNFileUtil.createUniqueFile(path.getParentFile(), path.getName(), ".tmp");
    		workingFile.deleteOnExit();
            OutputStream os = SVNFileUtil.openFileForWriting(workingFile);
            try {
            	os.write(SVNPropertyValue.getPropertyAsBytes(workingValue));
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                		"Cannot write a working property value file: {1}", e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
            } finally {
                SVNFileUtil.closeFile(os);
            }
    	}

    	File newFile = null;
    	if (newValue != null && !newValue.hasNullValue()) {
    		newFile = SVNFileUtil.createUniqueFile(path.getParentFile(), path.getName(), ".tmp");
    		newFile.deleteOnExit();
            OutputStream os = SVNFileUtil.openFileForWriting(newFile);
            try {
            	os.write(SVNPropertyValue.getPropertyAsBytes(newValue));
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                		"Cannot write a new property value file: {1}", e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
            } finally {
                SVNFileUtil.closeFile(os);
            }
    	}
    	
    	File baseFile = null;
    	File mergedFile = null;
    	if ((baseValue != null && !baseValue.hasNullValue() && (oldValue == null || oldValue.hasNullValue())) ||
    			((baseValue == null || baseValue.hasNullValue()) && (oldValue != null || oldValue.hasNullValue()))) {
    		SVNPropertyValue theValue = baseValue != null && !baseValue.hasNullValue() ? baseValue : oldValue;
    		baseFile = SVNFileUtil.createUniqueFile(path.getParentFile(), path.getName(), ".tmp");
    		baseFile.deleteOnExit();
    		OutputStream os = SVNFileUtil.openFileForWriting(baseFile);
    		try {
            	os.write(SVNPropertyValue.getPropertyAsBytes(theValue));
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                		"Cannot write a base property value file: {1}", e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
            } finally {
                SVNFileUtil.closeFile(os);
            }
    	} else if (baseValue != null && !baseValue.hasNullValue() && 
    			oldValue != null && !oldValue.hasNullValue()) {
    		SVNPropertyValue theValue = baseValue;
    		if (!baseValue.equals(oldValue)) {
    			if (workingValue != null && !workingValue.hasNullValue() && baseValue.equals(workingValue)) {
    				theValue = oldValue;
    			} 
    		} 
    		baseFile = SVNFileUtil.createUniqueFile(path.getParentFile(), path.getName(), ".tmp");
    		baseFile.deleteOnExit();
    		OutputStream os = SVNFileUtil.openFileForWriting(baseFile);
    		try {
            	os.write(SVNPropertyValue.getPropertyAsBytes(theValue));
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                		"Cannot write a base property value file: {1}", e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
            } finally {
                SVNFileUtil.closeFile(os);
            }
    	
            if (workingValue != null && !workingValue.hasNullValue() && newValue != null && !newValue.hasNullValue()) {
                OutputStream result = null;
                try {
                    QSequenceLineRAData baseData = new QSequenceLineRAByteData(theValue.getBytes());
                    QSequenceLineRAData localData = new QSequenceLineRAByteData(workingValue.getBytes());
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


            }
    	}
    }
    */
}
