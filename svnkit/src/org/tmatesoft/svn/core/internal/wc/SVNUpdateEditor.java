/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
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
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNUpdateEditor implements ISVNEditor {

    private String mySwitchURL;
    private String myTarget;
    private String myTargetURL;
    private String myRootURL;
    private boolean myIsRecursive;
    private SVNAdminAreaInfo myAdminInfo;
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private long myTargetRevision;
    private boolean myIsRootOpen;
    private boolean myIsTargetDeleted;
    private boolean myIsLeaveConflicts;
    private SVNWCAccess myWCAccess; 
    
    private SVNDeltaProcessor myDeltaProcessor;

    public SVNUpdateEditor(SVNAdminAreaInfo info, String switchURL, boolean recursive, boolean leaveConflicts) throws SVNException {
        myAdminInfo = info;
        myWCAccess = info.getWCAccess();
        myIsRecursive = recursive;
        myTarget = info.getTargetName();
        mySwitchURL = switchURL;
        myTargetRevision = -1;
        myIsLeaveConflicts = leaveConflicts;
        myDeltaProcessor = new SVNDeltaProcessor();

        SVNEntry entry = info.getAnchor().getEntry(info.getAnchor().getThisDirName(), false);
        myTargetURL = entry != null ? entry.getURL() : null;
        myRootURL = entry != null ? entry.getRepositoryRoot() : null;
        if (myTarget != null) {
            myTargetURL = SVNPathUtil.append(myTargetURL, SVNEncodingUtil.uriEncode(myTarget));
        }
        if (mySwitchURL != null && entry != null && entry.getRepositoryRoot() != null) {
            if (!SVNPathUtil.isAncestor(entry.getRepositoryRoot(), mySwitchURL)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SWITCH, "''{0}''\nis not the same repository as\n''{1}''",
                        new Object[] {mySwitchURL, entry.getRepositoryRoot()});
                SVNErrorManager.error(err);
            }
        }
        myAdminInfo.getTarget().closeEntries();

        if ("".equals(myTarget)) {
            myTarget = null;
        }
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public long getTargetRevision() {
        return myTargetRevision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myCurrentDirectory = createDirectoryInfo(null, "", false);
        if (myTarget == null) {
            SVNAdminArea adminArea = myCurrentDirectory.getAdminArea();
            SVNEntry entry = adminArea.getEntry(adminArea.getThisDirName(), true);
            entry.setRevision(myTargetRevision);
            entry.setURL(myCurrentDirectory.URL);
            entry.setIncomplete(true);
            if (mySwitchURL != null) {
                clearWCProperty(myCurrentDirectory.getAdminArea());
            }
            adminArea.saveEntries(false);
        }
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String name = SVNPathUtil.tail(path);
        SVNAdminArea parentArea = myCurrentDirectory.getAdminArea();
        SVNEntry entry = parentArea.getEntry(name, true);
        if (entry == null) {
            return;
        }

        SVNLog log = myCurrentDirectory.getLog();
        Map attributes = new HashMap();

        attributes.put(SVNLog.NAME_ATTR, name);
        log.addCommand(SVNLog.DELETE_ENTRY, attributes, false);
        SVNNodeKind kind = entry.getKind();
        boolean isDeleted = entry.isDeleted();
        if (path.equals(myTarget)) {
            attributes.put(SVNLog.NAME_ATTR, name);
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.KIND), kind == SVNNodeKind.DIR ? SVNProperty.KIND_DIR : SVNProperty.KIND_FILE);
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), Long.toString(myTargetRevision));
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.DELETED), Boolean.TRUE.toString());
            log.addCommand(SVNLog.MODIFY_ENTRY, attributes, false);
            myIsTargetDeleted = true;
        }
        try {
            log.save(); 
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Error writing log file for ''{0}''", myCurrentDirectory.getPath());
            SVNErrorManager.error(err, svne);
        }

        if (mySwitchURL != null && kind == SVNNodeKind.DIR) {
            SVNAdminArea childArea = myWCAccess.retrieve(parentArea.getFile(name));
            try {
                childArea.removeFromRevisionControl(childArea.getThisDirName(), true, true);
            } catch (SVNException svne) {
                handleLeftLocalModificationsError(svne, log, childArea);
            }
        }
        try {
            myCurrentDirectory.runLogs();
        } catch (SVNException svne) {
            handleLeftLocalModificationsError(svne, log, parentArea);
        }

        if (isDeleted) {
            // entry was deleted, but it was already deleted, no need to make a
            // notification.
            return;
        }
        myWCAccess.handleEvent(SVNEventFactory.createUpdateDeleteEvent(myAdminInfo, myCurrentDirectory.getAdminArea(), kind, name));
    }

    private void handleLeftLocalModificationsError(SVNException originalError, SVNLog log, SVNAdminArea adminArea) throws SVNException {
        SVNException error = null;
        for (error = originalError; error != null;) {
            if (error.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LEFT_LOCAL_MOD) {
                break;
            }
            error = (error.getCause() instanceof SVNException) ? (SVNException) error.getCause() : null; 
        }
        if (error != null) {
            log.delete();
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Won''t delete locally modified directory ''{0}''", adminArea.getRoot());
            SVNErrorManager.error(err, error);
        }
        throw originalError;
    }
    
    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        SVNAdminArea parentArea = myCurrentDirectory.getAdminArea();
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, true);

        String name = SVNPathUtil.tail(path);
        File childDir = parentArea.getFile(name);
        SVNFileType kind = SVNFileType.getType(childDir);
        if (kind != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'': object of the same name already exists", path);
            SVNErrorManager.error(err);
        } else if (SVNFileUtil.getAdminDirectoryName().equals(name)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'':  object of the same name as the administrative directory", path);
            SVNErrorManager.error(err);
        }

        if (copyFromPath != null || copyFromRevision >= 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Failed to add directory ''{0}'': copyfrom arguments not yet supported", path);
            SVNErrorManager.error(err);
        }
        
        SVNEntry entry = parentArea.getEntry(name, false);
        if (entry != null) {
            if (entry.isScheduledForAddition()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'': object of the same name already exists", path);
                SVNErrorManager.error(err);
            }
        } else {
            entry = parentArea.addEntry(name);
        }
        entry.setKind(SVNNodeKind.DIR);
        entry.setAbsent(false);
        entry.setDeleted(false);
        parentArea.saveEntries(false);

        String rootURL = null;
        if (SVNPathUtil.isAncestor(myRootURL, myCurrentDirectory.URL)) {
            rootURL = myRootURL;
        }
        if (myWCAccess.getAdminArea(childDir) != null) {
            myWCAccess.closeAdminArea(childDir);
        }
        if (SVNWCManager.ensureAdmiAreaExists(childDir, myCurrentDirectory.URL, rootURL, null, myTargetRevision)) {
            // hack : remove created lock file.
            SVNFileUtil.deleteFile(new File(childDir, SVNFileUtil.getAdminDirectoryName() + "/lock"));
        }
        myWCAccess.open(childDir, true, 0);
        myWCAccess.handleEvent(SVNEventFactory.createUpdateAddEvent(myAdminInfo, parentArea, SVNNodeKind.DIR, entry));
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, false);
        SVNAdminArea adminArea = myCurrentDirectory.getAdminArea(); 
        SVNEntry entry = adminArea.getEntry(adminArea.getThisDirName(), true);
        entry.setRevision(myTargetRevision);
        entry.setURL(myCurrentDirectory.URL);
        entry.setIncomplete(true);
        if (myRootURL != null && SVNPathUtil.isAncestor(myRootURL, myCurrentDirectory.URL)) {
            entry.setRepositoryRoot(myRootURL);
        }
        if (mySwitchURL != null) {
            clearWCProperty(myCurrentDirectory.getAdminArea());
        }
        adminArea.saveEntries(false);
    }

    public void absentDir(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.DIR);
    }

    public void absentFile(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.FILE);
    }

    private void absentEntry(String path, SVNNodeKind kind) throws SVNException {
        String name = SVNPathUtil.tail(path);
        SVNAdminArea adminArea = myCurrentDirectory.getAdminArea();
        SVNEntry entry = adminArea.getEntry(name, false);
        if (entry != null && entry.isScheduledForAddition()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to mark ''{0}'' absent: item of the same name is already scheduled for addition", path);
            SVNErrorManager.error(err);
        }
        if (entry == null) {
            entry = adminArea.addEntry(name);
        }
        if (entry != null) {
            entry.setKind(kind);
            entry.setDeleted(false);
            entry.setRevision(myTargetRevision);
            entry.setAbsent(true);
        }
        adminArea.saveEntries(false);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        myCurrentDirectory.propertyChanged(name, value);
    }

    private void clearWCProperty(SVNAdminArea adminArea) throws SVNException {
        if (adminArea == null) {
            return;
        }
        for (Iterator ents = adminArea.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (entry.isFile() || adminArea.getThisDirName().equals(entry.getName())) {
                SVNVersionedProperties props = adminArea.getWCProperties(entry.getName());
                props.setPropertyValue(SVNProperty.WC_URL, null);
                adminArea.saveWCProperties(false);
            } else {
                SVNAdminArea childArea = myAdminInfo.getWCAccess().retrieve(adminArea.getFile(entry.getName()));
                clearWCProperty(childArea);
            }
        }
    }

    public void closeDir() throws SVNException {
        Map modifiedWCProps = myCurrentDirectory.getChangedWCProperties();
        Map modifiedEntryProps = myCurrentDirectory.getChangedEntryProperties();
        Map modifiedProps = myCurrentDirectory.getChangedProperties();

        SVNStatusType propStatus = SVNStatusType.UNKNOWN;
        SVNAdminArea adminArea = myCurrentDirectory.getAdminArea();
        if (modifiedWCProps != null || modifiedEntryProps != null || modifiedProps != null) {
            SVNLog log = myCurrentDirectory.getLog();
            if (modifiedProps != null && !modifiedProps.isEmpty()) {
                myAdminInfo.addExternals(adminArea, (String) modifiedProps.get(SVNProperty.EXTERNALS));

                SVNVersionedProperties oldBaseProps = adminArea.getBaseProperties(adminArea.getThisDirName());
                try {
                    propStatus = adminArea.mergeProperties(adminArea.getThisDirName(), oldBaseProps.asMap(), modifiedProps, true, false, log);
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage().wrap("Couldn't do property merge");
                    SVNErrorManager.error(err, svne);
                }
            }
            log.logChangedEntryProperties(adminArea.getThisDirName(), modifiedEntryProps);
            log.logChangedWCProperties(adminArea.getThisDirName(), modifiedWCProps);
            log.save();
        }
        myCurrentDirectory.runLogs();
        completeDirectory(myCurrentDirectory);
        if (!myCurrentDirectory.IsAdded) {
            SVNEventAction action = SVNEventAction.UPDATE_UPDATE;
            if (propStatus == SVNStatusType.UNKNOWN) {
                action = SVNEventAction.UPDATE_NONE;
            }
            myWCAccess.handleEvent(SVNEventFactory.createUpdateModifiedEvent(myAdminInfo, adminArea, "", SVNNodeKind.DIR, action, null, SVNStatusType.UNKNOWN, propStatus, null));
        }
        myCurrentDirectory = myCurrentDirectory.Parent;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (myTarget != null && myWCAccess.isMissing(myAdminInfo.getAnchor().getFile(myTarget))) {
            myCurrentDirectory = createDirectoryInfo(null, "", false);
            deleteEntry(myTarget, myTargetRevision);
        }

        if (!myIsRootOpen) {
            completeDirectory(myCurrentDirectory);
        }
        if (!myIsTargetDeleted) {
            File targetFile = myTarget != null ? myAdminInfo.getAnchor().getFile(myTarget) : myAdminInfo.getAnchor().getRoot(); 
            SVNWCManager.updateCleanup(targetFile, myWCAccess, myIsRecursive, mySwitchURL, myRootURL, myTargetRevision, true);
//            cleanupUpdate();
        }
        return null;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentFile = createFileInfo(myCurrentDirectory, path, true);
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFile = createFileInfo(myCurrentDirectory, path, false);
    }

    public void changeFileProperty(String commitPath, String name, String value) throws SVNException {
        myCurrentFile.propertyChanged(name, value);
        if (myWCAccess.getOptions().isUseCommitTimes() && SVNProperty.COMMITTED_DATE.equals(name)) {
            myCurrentFile.CommitTime = value;
        }
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        SVNAdminArea adminArea = myCurrentFile.getAdminArea();
        SVNEntry entry = adminArea.getEntry(myCurrentFile.Name, false);
        boolean replaced = entry != null && entry.isScheduledForReplacement();

        File baseFile = replaced ? adminArea.getFile(SVNAdminUtil.getTextRevertPath(myCurrentFile.Name, false)) : adminArea.getBaseFile(myCurrentFile.Name, false);
        
        if (entry != null && entry.getChecksum() != null) {
            String realChecksum = SVNFileUtil.computeChecksum(baseFile);
            if (baseChecksum != null) {
                if (!baseChecksum.equals(realChecksum)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                            new Object[] {myCurrentFile.getPath(), baseChecksum, realChecksum}); 
                    SVNErrorManager.error(err);
                }
            }
            
            String realChecksumSafe = realChecksum == null ? "" : realChecksum;
            String entryChecksumSafe = entry.getChecksum() == null ? "" : entry.getChecksum();
            if (!replaced && !realChecksumSafe.equals(entryChecksumSafe)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch for ''{0}''; recorded: ''{1}'', actual: ''{2}''",
                        new Object[] {myCurrentFile.getPath(), entry.getChecksum(), realChecksum}); 
                SVNErrorManager.error(err);
            }
        }
        File baseTmpFile = replaced ? adminArea.getFile(SVNAdminUtil.getTextRevertPath(myCurrentFile.Name, true)) : adminArea.getBaseFile(myCurrentFile.Name, true);
        myCurrentFile.TextUpdated = true;
        myDeltaProcessor.applyTextDelta(baseFile, baseTmpFile, true);
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        myCurrentFile.Checksum = myDeltaProcessor.textDeltaEnd();
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        // check checksum.
        String checksum = null;
        if (textChecksum != null && myCurrentFile.TextUpdated) {            
            if (myCurrentFile.Checksum != null && !textChecksum.equals(myCurrentFile.Checksum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                        new Object[] {myCurrentFile.getPath(), textChecksum, checksum}); 
                SVNErrorManager.error(err);
            }
            checksum = textChecksum;
        }
        SVNAdminArea adminArea = myCurrentFile.getAdminArea();
        SVNLog log = myCurrentDirectory.getLog();

        // merge props.
        Map modifiedWCProps = myCurrentFile.getChangedWCProperties();
        Map modifiedEntryProps = myCurrentFile.getChangedEntryProperties();
        Map modifiedProps = myCurrentFile.getChangedProperties();
        String name = myCurrentFile.Name;
        String commitTime = myCurrentFile.CommitTime;

        Map command = new HashMap();

        SVNStatusType textStatus = SVNStatusType.UNCHANGED;
        SVNStatusType lockStatus = SVNStatusType.LOCK_UNCHANGED;

        boolean magicPropsChanged = false;
        if (modifiedProps != null && !modifiedProps.isEmpty()) {
            magicPropsChanged = modifiedProps.containsKey(SVNProperty.EXECUTABLE) || 
            modifiedProps.containsKey(SVNProperty.NEEDS_LOCK) || 
            modifiedProps.containsKey(SVNProperty.KEYWORDS) || 
            modifiedProps.containsKey(SVNProperty.EOL_STYLE) || 
            modifiedProps.containsKey(SVNProperty.SPECIAL);
        }
        
        SVNVersionedProperties baseProps = adminArea.getBaseProperties(name);
        Map oldBaseProps = baseProps != null ? baseProps.asMap() : null;
        SVNStatusType propStatus = adminArea.mergeProperties(name, oldBaseProps, modifiedProps, true, false, log);
        if (modifiedEntryProps != null) {
            lockStatus = log.logChangedEntryProperties(name, modifiedEntryProps);
        }

        boolean isLocalPropsModified = !myCurrentFile.IsAdded && adminArea.hasPropModifications(name);
        if (modifiedProps != null && !isLocalPropsModified) {
            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_TIME), SVNLog.WC_TIMESTAMP);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (modifiedWCProps != null) {
            log.logChangedWCProperties(name, modifiedWCProps);
        }

        boolean isLocallyModified = !myCurrentFile.IsAdded && adminArea.hasTextModifications(name, false);
        boolean isReplaced = false;
        if (!isLocallyModified) {
            SVNEntry entry = adminArea.getEntry(name, false);
            if (entry != null && entry.isScheduledForReplacement()) {
                isReplaced = true;
            }
        }
        
        //merge contents.
        String adminDir = SVNFileUtil.getAdminDirectoryName();
        File textTmpBase = adminArea.getBaseFile(name, true);
        if (isReplaced) {
            textTmpBase = adminArea.getFile(SVNAdminUtil.getTextRevertPath(name, true));
        }
        File workingFile = adminArea.getFile(name);
        String tmpPath = null;
        String basePath = null;

        if (myCurrentFile.TextUpdated && textTmpBase.exists()) {
            if (!isReplaced) {
                tmpPath = adminDir + "/tmp/text-base/" + name + ".svn-base";
                basePath = adminDir + "/text-base/" + name + ".svn-base";
            } else {
                tmpPath = adminDir + "/tmp/text-base/" + name + ".svn-revert";
                basePath = adminDir + "/text-base/" + name + ".svn-revert";
            }
        } else if (!myCurrentFile.TextUpdated && magicPropsChanged && workingFile.exists()) {
            // only props were changed, but we have to retranslate file.
            // only if wc file exists (may be locally deleted), otherwise no
            // need to retranslate...
            tmpPath = SVNAdminUtil.getTextBasePath(name, true);
            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNLog.DEST_ATTR, tmpPath);
            log.addCommand(SVNLog.COPY_AND_DETRANSLATE, command, false);
            command.clear();
            command.put(SVNLog.NAME_ATTR, tmpPath);
            command.put(SVNLog.DEST_ATTR, name);
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();
        }
        
        // update entry.
        command.put(SVNLog.NAME_ATTR, name);
        command.put(SVNProperty.shortPropertyName(SVNProperty.KIND), SVNProperty.KIND_FILE);
        command.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), Long.toString(myTargetRevision));
        command.put(SVNProperty.shortPropertyName(SVNProperty.DELETED), Boolean.FALSE.toString());
        command.put(SVNProperty.shortPropertyName(SVNProperty.ABSENT), Boolean.FALSE.toString());
        if (myCurrentFile.URL != null) {
            command.put(SVNProperty.shortPropertyName(SVNProperty.URL), myCurrentFile.URL);
        }
        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        command.clear();

        if (myCurrentFile.TextUpdated && textTmpBase.exists()) {
            textStatus = SVNStatusType.CHANGED;
            // there is a text replace working copy with.
            if (!isLocallyModified && !isReplaced) {
                command.put(SVNLog.NAME_ATTR, tmpPath);
                command.put(SVNLog.DEST_ATTR, name);
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();
            } else {
                SVNFileType kind = SVNFileType.getType(workingFile);
                if (kind == SVNFileType.NONE) {
                    command.put(SVNLog.NAME_ATTR, tmpPath);
                    command.put(SVNLog.DEST_ATTR, name);
                    log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                    command.clear();
                } else {
                    SVNEntry entry = adminArea.getEntry(name, false);
                    if (entry == null) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", workingFile);
                        SVNErrorManager.error(err);
                    }
                    // do test merge.
                    String oldEolStyle = null;
                    String oldKeywords = null;
                    SVNVersionedProperties props = adminArea.getProperties(myCurrentFile.Name);
                    try {
                        if (magicPropsChanged && 
                                (modifiedProps.containsKey(SVNProperty.EOL_STYLE) || modifiedProps.containsKey(SVNProperty.KEYWORDS))) {
                            // use new valuse to let dry-run merge use the same input as real merge will use.
                            oldKeywords = props.getPropertyValue(SVNProperty.KEYWORDS);
                            oldEolStyle = props.getPropertyValue(SVNProperty.EOL_STYLE);
                            props.setPropertyValue(SVNProperty.EOL_STYLE, (String) modifiedProps.get(SVNProperty.EOL_STYLE));
                            props.setPropertyValue(SVNProperty.KEYWORDS, (String) modifiedProps.get(SVNProperty.KEYWORDS));
                        }
                        textStatus = adminArea.mergeText(name, adminArea.getFile(basePath), adminArea.getFile(tmpPath), "", "", "", myIsLeaveConflicts, true);
                    } finally {
                        if (magicPropsChanged && 
                                (modifiedProps.containsKey(SVNProperty.EOL_STYLE) || modifiedProps.containsKey(SVNProperty.KEYWORDS))) {
                            // restore original values.
                            props.setPropertyValue(SVNProperty.EOL_STYLE, oldEolStyle);
                            props.setPropertyValue(SVNProperty.KEYWORDS, oldKeywords);
                        }
                    }
                    if (textStatus == SVNStatusType.UNCHANGED) {
                        textStatus = SVNStatusType.MERGED;
                    }
                    String oldRevisionStr = ".r" + entry.getRevision();
                    String newRevisionStr = ".r" + myTargetRevision;

                    command.put(SVNLog.NAME_ATTR, name);
                    command.put(SVNLog.ATTR1, basePath);
                    command.put(SVNLog.ATTR2, tmpPath);
                    command.put(SVNLog.ATTR3, oldRevisionStr);
                    command.put(SVNLog.ATTR4, newRevisionStr);
                    command.put(SVNLog.ATTR5, ".mine");
                    if (textStatus == SVNStatusType.CONFLICTED_UNRESOLVED) {
                        command.put(SVNLog.ATTR6, Boolean.TRUE.toString());
                    }
                    log.addCommand(SVNLog.MERGE, command, false);
                    command.clear();
                }
            }
        } else if (lockStatus == SVNStatusType.LOCK_UNLOCKED) {
            command.put(SVNLog.NAME_ATTR, name);
            log.addCommand(SVNLog.MAYBE_READONLY, command, false);
            command.clear();
        }
        if (myCurrentFile.TextUpdated && textTmpBase.exists()) {
            command.put(SVNLog.NAME_ATTR, tmpPath);
            command.put(SVNLog.DEST_ATTR, basePath);
            log.addCommand(SVNLog.MOVE, command, false);
            command.clear();
            command.put(SVNLog.NAME_ATTR, basePath);
            log.addCommand(SVNLog.READONLY, command, false);
            command.clear();
            if (!isReplaced) {
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), checksum);
                log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                command.clear();
            }
        }
        if (!isLocallyModified) {
            if (commitTime != null) {
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNLog.TIMESTAMP_ATTR, commitTime);
                log.addCommand(SVNLog.SET_TIMESTAMP, command, false);
                command.clear();
            }
            if ((myCurrentFile.TextUpdated && textTmpBase.exists()) || magicPropsChanged) {
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), SVNLog.WC_TIMESTAMP);
                log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                command.clear();
            }
        }
        // bump.
        log.save();
        myCurrentFile.TextUpdated = false;
        completeDirectory(myCurrentDirectory);

        // notify.
        if (!myCurrentFile.IsAdded && textStatus == SVNStatusType.UNCHANGED && propStatus == SVNStatusType.UNCHANGED && lockStatus == SVNStatusType.LOCK_UNCHANGED) {
            // no changes, probably just wcurl switch.
            myCurrentFile = null;
            return;
        }
        SVNEventAction action = myCurrentFile.IsAdded ? SVNEventAction.UPDATE_ADD : SVNEventAction.UPDATE_UPDATE;
        myWCAccess.handleEvent(SVNEventFactory.createUpdateModifiedEvent(myAdminInfo, adminArea, myCurrentFile.Name, SVNNodeKind.FILE, action, null, textStatus, propStatus, lockStatus));
        myCurrentFile = null;
    }

    public void abortEdit() throws SVNException {
    }

    private void completeDirectory(SVNDirectoryInfo info) throws SVNException {
        while (info != null) {
            info.RefCount--;
            if (info.RefCount > 0) {
                return;
            }
            if (info.Parent == null && myTarget != null) {
                return;
            }
            SVNAdminArea adminArea = info.getAdminArea();
            if (adminArea.getEntry(adminArea.getThisDirName(), true) == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No root entry found in ''{0}''", adminArea.getRoot());
                SVNErrorManager.error(err);
            }
            for (Iterator ents = adminArea.entries(true); ents.hasNext();) {
                SVNEntry entry = (SVNEntry) ents.next();
                if (adminArea.getThisDirName().equals(entry.getName())) {
                    entry.setIncomplete(false);
                    continue;
                }
                if (entry.isDeleted()) {
                    if (!entry.isScheduledForAddition()) {
                        adminArea.deleteEntry(entry.getName());
                    } else {
                        entry.setDeleted(false);
                    }
                } else if (entry.isAbsent() && entry.getRevision() != myTargetRevision) {
                    adminArea.deleteEntry(entry.getName());
                } else if (entry.getKind() == SVNNodeKind.DIR) {
                    if (myWCAccess.isMissing(adminArea.getFile(entry.getName())) && !entry.isAbsent() && !entry.isScheduledForAddition()) {
                        adminArea.deleteEntry(entry.getName());
                        myWCAccess.handleEvent(SVNEventFactory.createUpdateDeleteEvent(myAdminInfo, info.getAdminArea(), entry));
                    }
                }
            }
            adminArea.saveEntries(true);
            info = info.Parent;
        }
    }

    private SVNFileInfo createFileInfo(SVNDirectoryInfo parent, String path, boolean added) throws SVNException {
        SVNFileInfo info = new SVNFileInfo(parent, path);
        info.IsAdded = added;
        info.Name = SVNPathUtil.tail(path);
        SVNAdminArea adminArea = parent.getAdminArea();
        SVNFileType kind = SVNFileType.getType(adminArea.getFile(info.Name));
        if (added && kind != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add file ''{0}'': object of the same name already exists", path);
            SVNErrorManager.error(err);
        }
        try {
            SVNEntry entry = adminArea.getEntry(info.Name, true);
            if (added && entry != null && entry.isScheduledForAddition()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add file ''{0}'': object of the same name already exists and scheduled for addition", path);
                SVNErrorManager.error(err);
            }
            if (!added && entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "File ''{0}'' in directory ''{1}'' is not a versioned resource", 
                        new Object[] {info.Name, adminArea.getRoot()});
                SVNErrorManager.error(err);
            }
            if (mySwitchURL != null || entry == null) {
                info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(info.Name));
            } else {
                info.URL = entry.getURL();
            }
        } finally {
            adminArea.closeEntries();
        }
        parent.RefCount++;
        return info;
    }

    private SVNDirectoryInfo createDirectoryInfo(SVNDirectoryInfo parent, String path, boolean added) {
        SVNDirectoryInfo info = new SVNDirectoryInfo(path);
        info.Parent = parent;
        info.IsAdded = added;
        String name = path != null ? SVNPathUtil.tail(path) : "";

        if (mySwitchURL == null) {
            SVNAdminArea area = null;
            SVNEntry dirEntry = null;
            try {
                area = info.getAdminArea();
                if (area != null) {
                    // could be missing.
                    dirEntry = area.getEntry(area.getThisDirName(), false);
                }
            } catch (SVNException svne) {
                //
            }
            
            if (area != null && dirEntry != null) {
                info.URL = dirEntry.getURL();
            }
            if (info.URL == null && parent != null) {
                info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(name));
            } else if (info.URL == null && parent == null) {
                info.URL = myTargetURL;
            }
        } else {
            if (parent == null) {
                info.URL = myTarget == null ? mySwitchURL : SVNPathUtil.removeTail(mySwitchURL);
            } else {
                if (myTarget != null && parent.Parent == null) {
                    info.URL = mySwitchURL;
                } else {
                    info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(name));
                }
            }
        }
        info.RefCount = 1;
        if (info.Parent != null) {
            info.Parent.RefCount++;
        }
        return info;
    }

    private class SVNEntryInfo {

        public String URL;
        public boolean IsAdded;
        public SVNDirectoryInfo Parent;
        private String myPath;
        private Map myChangedProperties;
        private Map myChangedEntryProperties;
        private Map myChangedWCProperties;

        protected SVNEntryInfo(String path) {
            myPath = path;
        }

        protected String getPath() {
            return myPath;
        }

        public void propertyChanged(String name, String value) {
            if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                myChangedEntryProperties = myChangedEntryProperties == null ? new HashMap() : myChangedEntryProperties;
                myChangedEntryProperties.put(name.substring(SVNProperty.SVN_ENTRY_PREFIX.length()), value);
            } else if (name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                myChangedWCProperties = myChangedWCProperties == null ? new HashMap() : myChangedWCProperties;
                myChangedWCProperties.put(name, value);
            } else {
                myChangedProperties = myChangedProperties == null ? new HashMap() : myChangedProperties;
                myChangedProperties.put(name, value);
            }
        }

        public Map getChangedWCProperties() {
            return myChangedWCProperties;
        }

        public Map getChangedEntryProperties() {
            return myChangedEntryProperties;
        }

        public Map getChangedProperties() {
            return myChangedProperties;
        }
    }

    private class SVNFileInfo extends SVNEntryInfo {

        public String Name;
        public String CommitTime;
        public boolean TextUpdated;
        public String Checksum;

        public SVNFileInfo(SVNDirectoryInfo parent, String path) {
            super(path);
            this.Parent = parent;
        }

        public SVNAdminArea getAdminArea() throws SVNException {
            return Parent.getAdminArea();
        }
    }

    private class SVNDirectoryInfo extends SVNEntryInfo {

        public int RefCount;

        public SVNDirectoryInfo(String path) {
            super(path);
        }

        public SVNAdminArea getAdminArea() throws SVNException {
            String path = getPath();
            File file = new File(myAdminInfo.getAnchor().getRoot(), path);
            return myAdminInfo.getWCAccess().retrieve(file);
        }

        public SVNLog getLog() throws SVNException {
            SVNLog log = getAdminArea().getLog();
            return log;
        }

        public void runLogs() throws SVNException {
            getAdminArea().runLogs();
        }
    }
}
