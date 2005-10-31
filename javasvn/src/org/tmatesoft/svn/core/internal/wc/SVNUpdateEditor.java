/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNUpdateEditor implements ISVNEditor {

    private String mySwitchURL;
    private String myTarget;
    private String myTargetURL;
    private boolean myIsRecursive;
    private SVNWCAccess myWCAccess;
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private long myTargetRevision;
    private boolean myIsRootOpen;
    private boolean myIsTargetDeleted;
    private boolean myIsLeaveConflicts;
    
    private SVNDeltaProcessor myDeltaProcessor;

    public SVNUpdateEditor(SVNWCAccess wcAccess, String switchURL, boolean recursive, boolean leaveConflicts) throws SVNException {
        myWCAccess = wcAccess;
        myIsRecursive = recursive;
        myTarget = wcAccess.getTargetName();
        mySwitchURL = switchURL;
        myTargetRevision = -1;
        myIsLeaveConflicts = leaveConflicts;
        myDeltaProcessor = new SVNDeltaProcessor();

        SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry("", true);
        myTargetURL = entry.getURL();
        if (myTarget != null) {
            myTargetURL = SVNPathUtil.append(myTargetURL, SVNEncodingUtil.uriEncode(myTarget));
        }
        wcAccess.getTarget().getEntries().close();

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
            SVNEntries entries = myCurrentDirectory.getDirectory().getEntries();
            SVNEntry entry = entries.getEntry("", true);
            entry.setRevision(myTargetRevision);
            entry.setURL(myCurrentDirectory.URL);
            entry.setIncomplete(true);
            if (mySwitchURL != null) {
                clearWCProperty(myCurrentDirectory.getDirectory());
            }
            entries.save(true);
        }
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String name = SVNPathUtil.tail(path);

        SVNEntry entry = myCurrentDirectory.getDirectory().getEntries().getEntry(name, true);
        if (entry == null) {
            return;
        }

        SVNLog log = myCurrentDirectory.getLog(true);
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
        if (mySwitchURL != null && kind == SVNNodeKind.DIR) {
            myCurrentDirectory.getDirectory().destroy(name, true);
        }
        log.save();
        myCurrentDirectory.runLogs();
        if (isDeleted) {
            // entry was deleted, but it was already deleted, no need to make a
            // notification.
            return;
        }
        myWCAccess.handleEvent(SVNEventFactory.createUpdateDeleteEvent(myWCAccess, myCurrentDirectory.getDirectory(), name));
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        SVNDirectory parentDir = myCurrentDirectory.getDirectory();
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, true);

        String name = SVNPathUtil.tail(path);
        File file = parentDir.getFile(name);
        if (file.exists()) {
            SVNErrorManager.error("svn: Failed to add directory '" + path + "': object of the same name already exists");
        } else if (SVNFileUtil.getAdminDirectoryName().equals(name)) {
            SVNErrorManager.error("svn: Failed to add directory '" + path + "': object of the same name as the administrative directory");
        }
        SVNEntry entry = parentDir.getEntries().getEntry(name, true);
        if (entry != null) {
            if (entry.isScheduledForAddition()) {
                SVNErrorManager.error("svn: Failed to add directory '" + path + "': object of the same name already exists and schedule for addition");
            }
        } else {
            entry = parentDir.getEntries().addEntry(name);
        }
        entry.setKind(SVNNodeKind.DIR);
        entry.setAbsent(false);
        entry.setDeleted(false);
        parentDir.getEntries().save(true);

        SVNDirectory dir = parentDir.createChildDirectory(name, myCurrentDirectory.URL, myTargetRevision);
        if (dir == null) {
            SVNErrorManager.error("svn: Failed to add directory '" + path + "': directory is missing or not locked");
        } else {
            dir.getEntries().getEntry("", false).setIncomplete(true);
            dir.getEntries().save(true);
            dir.lock();
        }
        myWCAccess.handleEvent(SVNEventFactory.createUpdateAddEvent(myWCAccess, parentDir, SVNNodeKind.DIR, entry));
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, false);
        if (myCurrentDirectory.getDirectory() == null) {
            SVNErrorManager.error("svn: Working copy '" + path + "' is missing or not locked");
        }
        SVNEntries entries = myCurrentDirectory.getDirectory().getEntries();
        SVNEntry entry = entries.getEntry("", true);
        entry.setRevision(myTargetRevision);
        entry.setURL(myCurrentDirectory.URL);
        entry.setIncomplete(true);
        if (mySwitchURL != null) {
            clearWCProperty(myCurrentDirectory.getDirectory());
        }
        entries.save(true);
    }

    public void absentDir(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.DIR);
    }

    public void absentFile(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.FILE);
    }

    private void absentEntry(String path, SVNNodeKind kind) throws SVNException {
        String name = SVNPathUtil.tail(path);
        SVNEntries entries = myCurrentDirectory.getDirectory().getEntries();
        SVNEntry entry = entries.getEntry(name, true);
        if (entry != null && entry.isScheduledForAddition()) {
            if (kind == SVNNodeKind.FILE) {
                SVNErrorManager.error("svn: Failed to add entry for absent file '" + path + "': object of the same name already exists and scheduled for addition");
            } else {
                SVNErrorManager.error("svn: Failed to add entry for absent directory '" + path + "': object of the same name already exists and scheduled for addition");
            }
        }
        if (entry == null) {
            entry = entries.addEntry(name);
        }
        if (entry != null) {
            entry.setKind(kind);
            entry.setDeleted(false);
            entry.setRevision(myTargetRevision);
            entry.setAbsent(true);
        }
        entries.save(true);

    }

    public void changeDirProperty(String name, String value) throws SVNException {
        myCurrentDirectory.propertyChanged(name, value);
    }

    private void clearWCProperty(SVNDirectory dir) throws SVNException {
        if (dir == null) {
            return;
        }
        SVNEntries entires = dir.getEntries();
        for (Iterator ents = entires.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (entry.isFile() || "".equals(entry.getName())) {
                SVNProperties props = dir.getWCProperties(entry.getName());
                props.setPropertyValue(SVNProperty.WC_URL, null);
            } else {
                clearWCProperty(dir.getChildDirectory(entry.getName()));
            }
        }
    }

    public void closeDir() throws SVNException {
        Map modifiedWCProps = myCurrentDirectory.getChangedWCProperties();
        Map modifiedEntryProps = myCurrentDirectory.getChangedEntryProperties();
        Map modifiedProps = myCurrentDirectory.getChangedProperties();

        SVNStatusType propStatus = SVNStatusType.UNCHANGED;
        SVNDirectory dir = myCurrentDirectory.getDirectory();
        if (modifiedWCProps != null || modifiedEntryProps != null || modifiedProps != null) {
            SVNLog log = myCurrentDirectory.getLog(true);

            if (modifiedProps != null && !modifiedProps.isEmpty()) {
                SVNProperties props = dir.getProperties("", false);
                Map locallyModified = dir.getBaseProperties("", false).compareTo(props);
                myWCAccess.addExternals(dir, (String) modifiedProps.get(SVNProperty.EXTERNALS));

                propStatus = dir.mergeProperties("", modifiedProps, locallyModified, true, log);
                if (locallyModified == null || locallyModified.isEmpty()) {
                    Map command = new HashMap();
                    command.put(SVNLog.NAME_ATTR, "");
                    command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_TIME), SVNLog.WC_TIMESTAMP);
                    log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                }
            }
            log.logChangedEntryProperties("", modifiedEntryProps);
            log.logChangedWCProperties("", modifiedWCProps);
            log.save();
        }
        myCurrentDirectory.runLogs();
        completeDirectory(myCurrentDirectory);
        if (!myCurrentDirectory.IsAdded && propStatus != SVNStatusType.UNCHANGED) {
            myWCAccess.handleEvent(SVNEventFactory.createUpdateModifiedEvent(myWCAccess, dir, "", SVNNodeKind.DIR, SVNEventAction.UPDATE_UPDATE, null, SVNStatusType.UNCHANGED, propStatus, null));
        }
        myCurrentDirectory = myCurrentDirectory.Parent;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (myTarget != null && !myWCAccess.getAnchor().getFile(myTarget).exists()) {
            myCurrentDirectory = createDirectoryInfo(null, "", false);
            deleteEntry(myTarget, myTargetRevision);
        }
        if (!myIsRootOpen) {
            completeDirectory(myCurrentDirectory);
        }
        if (!myIsTargetDeleted) {
            bumpDirectories();
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
        SVNDirectory dir = myCurrentFile.getDirectory();
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(myCurrentFile.Name, true);
        File baseFile = dir.getBaseFile(myCurrentFile.Name, false);
        if (entry != null && entry.getChecksum() != null) {
            if (baseChecksum == null) {
                baseChecksum = entry.getChecksum();
            }
            String realChecksum = SVNFileUtil.computeChecksum(baseFile);
            if (baseChecksum != null && (realChecksum == null || !realChecksum.equals(baseChecksum))) {
                SVNErrorManager.error("svn: Checksum mismatch for '" + myCurrentFile.getPath() + "'; expected: '" + baseChecksum + "', actual: '" + realChecksum + "'");
            }
        }
        File baseTmpFile = dir.getBaseFile(myCurrentFile.Name, true);
        SVNFileUtil.copyFile(baseFile, baseTmpFile, false);
        if (!baseTmpFile.exists()) {
            SVNFileUtil.createEmptyFile(baseTmpFile);
        }
        myCurrentFile.TextUpdated = true;
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        File file = myCurrentFile.getDirectory().getBaseFile(myCurrentFile.Name + ".txtdelta", true);
        return myDeltaProcessor.textDeltaChunk(file, diffWindow);
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        File baseTmpFile = myCurrentFile.getDirectory().getBaseFile(myCurrentFile.Name, true);
        File targetFile = myCurrentFile.getDirectory().getBaseFile(myCurrentFile.Name + ".tmp", true);
        myCurrentFile.Checksum = myDeltaProcessor.textDeltaEnd(baseTmpFile, targetFile, true);
        SVNFileUtil.rename(targetFile, baseTmpFile);
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        myDeltaProcessor.close();
        // check checksum.
        String checksum = null;
        if (textChecksum != null && myCurrentFile.TextUpdated) {            
            File baseTmpFile = myCurrentFile.getDirectory().getBaseFile(myCurrentFile.Name, true);
            checksum = myCurrentFile.Checksum != null ? myCurrentFile.Checksum : SVNFileUtil.computeChecksum(baseTmpFile);            
            if (!textChecksum.equals(checksum)) {
                SVNErrorManager.error("svn: Checksum differs, expected '" + textChecksum + "'; actual: '" + checksum + "'");
            }
            checksum = textChecksum;
        }
        SVNDirectory dir = myCurrentFile.getDirectory();
        SVNLog log = myCurrentDirectory.getLog(true);

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
        SVNProperties props = dir.getProperties(name, false);
        Map locallyModifiedProps = dir.getBaseProperties(name, false).compareTo(props);
        if (modifiedProps != null && !modifiedProps.isEmpty()) {
            magicPropsChanged = modifiedProps.containsKey(SVNProperty.EXECUTABLE) || 
            modifiedProps.containsKey(SVNProperty.NEEDS_LOCK) || 
            modifiedProps.containsKey(SVNProperty.KEYWORDS) || 
            modifiedProps.containsKey(SVNProperty.EOL_STYLE) || 
            modifiedProps.containsKey(SVNProperty.SPECIAL);
        }
        SVNStatusType propStatus = dir.mergeProperties(name, modifiedProps, locallyModifiedProps, true, log);
        if (modifiedEntryProps != null) {
            lockStatus = log.logChangedEntryProperties(name, modifiedEntryProps);
        }

        // merge contents.
        File textTmpBase = dir.getBaseFile(name, true);
        String adminDir = SVNFileUtil.getAdminDirectoryName();

        String tmpPath = adminDir + "/tmp/text-base/" + name + ".svn-base";
        String basePath = adminDir + "/text-base/" + name + ".svn-base";
        File workingFile = dir.getFile(name);

        if (!myCurrentFile.TextUpdated && magicPropsChanged && workingFile.exists()) {
            // only props were changed, but we have to retranslate file.
            // only if wc file exists (may be locally deleted), otherwise no
            // need to retranslate...
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
        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        command.clear();

        command.put(SVNLog.NAME_ATTR, name);
        command.put(SVNProperty.shortPropertyName(SVNProperty.URL), myCurrentFile.URL);
        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        command.clear();

        boolean isLocallyModified = !myCurrentFile.IsAdded && dir.hasTextModifications(name, false);
        if (myCurrentFile.TextUpdated && textTmpBase.exists()) {
            textStatus = SVNStatusType.CHANGED;
            // there is a text replace working copy with.
            if (!isLocallyModified || !workingFile.exists()) {
                command.put(SVNLog.NAME_ATTR, tmpPath);
                command.put(SVNLog.DEST_ATTR, name);
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();
            } else {
                // do test merge.
                textStatus = dir.mergeText(name, basePath, tmpPath, "", "", "", myIsLeaveConflicts, true);
                if (textStatus == SVNStatusType.UNCHANGED) {
                    textStatus = SVNStatusType.MERGED;
                }
                SVNEntries entries = dir.getEntries();
                SVNEntry entry = entries.getEntry(name, true);
                String oldRevisionStr = ".r" + entry.getRevision();
                String newRevisionStr = ".r" + myTargetRevision;
                entries.close();

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
        } else if (lockStatus == SVNStatusType.LOCK_UNLOCKED) {
            command.put(SVNLog.NAME_ATTR, name);
            log.addCommand(SVNLog.MAYBE_READONLY, command, false);
            command.clear();
        }
        if (locallyModifiedProps == null || locallyModifiedProps.isEmpty()) {
            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_TIME), SVNLog.WC_TIMESTAMP);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
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
            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), checksum);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (modifiedWCProps != null) {
            log.logChangedWCProperties(name, modifiedWCProps);
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
        myWCAccess.handleEvent(SVNEventFactory.createUpdateModifiedEvent(myWCAccess, dir, myCurrentFile.Name, SVNNodeKind.FILE, action, null, textStatus, propStatus, lockStatus));
        myCurrentFile = null;
    }

    public void abortEdit() throws SVNException {
    }

    private void bumpDirectories() throws SVNException {
        SVNDirectory dir = myWCAccess.getAnchor();
        if (myTarget != null) {
            if (dir.getChildDirectory(myTarget) == null) {
                SVNEntry entry = dir.getEntries().getEntry(myTarget, true);
                boolean save = bumpEntry(dir.getEntries(), entry, mySwitchURL, myTargetRevision, false);
                if (save) {
                    dir.getEntries().save(true);
                } else {
                    dir.getEntries().close();
                }
                return;
            }
            dir = dir.getChildDirectory(myTarget);
        }
        bumpDirectory(dir, mySwitchURL);
    }

    private void bumpDirectory(SVNDirectory dir, String url) throws SVNException {
        SVNEntries entries = dir.getEntries();
        boolean save = bumpEntry(entries, entries.getEntry("", true), url, myTargetRevision, false);
        Map childDirectories = new HashMap();
        for (Iterator ents = entries.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            String childURL = url != null ? SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(entry.getName())) : null;
            if (entry.getKind() == SVNNodeKind.FILE) {
                save |= bumpEntry(entries, entry, childURL, myTargetRevision, true);
            } else if (myIsRecursive && entry.getKind() == SVNNodeKind.DIR) {
                SVNDirectory childDirectory = dir.getChildDirectory(entry.getName());
                if (!entry.isScheduledForAddition() && (childDirectory == null || !childDirectory.isVersioned())) {
                    myWCAccess.handleEvent(SVNEventFactory.createUpdateDeleteEvent(myWCAccess, dir, entry));
                    entries.deleteEntry(entry.getName());
                    save = true;
                } else {
                    // schedule for recursion, map of dir->url
                    childDirectories.put(childDirectory, childURL);
                }
            }
        }
        if (save) {
            entries.save(true);
        }
        for (Iterator children = childDirectories.keySet().iterator(); children.hasNext();) {
            SVNDirectory child = (SVNDirectory) children.next();
            String childURL = (String) childDirectories.get(child);
            if (child != null) {
                bumpDirectory(child, childURL);
            } else {
                SVNDebugLog.logInfo("svn: Directory object is null in bump directories method");
            }
        }
    }

    private static boolean bumpEntry(SVNEntries entries, SVNEntry entry, String url, long revision, boolean delete) {
        if (entry == null) {
            return false;
        }
        boolean save = false;
        if (url != null) {
            save = entry.setURL(url);
        }
        if (revision >= 0 && !entry.isScheduledForAddition() && !entry.isScheduledForReplacement()) {
            save |= entry.setRevision(revision);
        }
        if (delete && (entry.isDeleted() || (entry.isAbsent() && entry.getRevision() != revision))) {
            entries.deleteEntry(entry.getName());
            save = true;
        }
        return save;
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
            SVNEntries entries = info.getDirectory().getEntries();
            if (entries.getEntry("", true) == null) {
                SVNErrorManager.error("svn: Cannot get root entry for '" + info.getDirectory().getPath() + "'");
            }
            for (Iterator ents = entries.entries(true); ents.hasNext();) {
                SVNEntry entry = (SVNEntry) ents.next();
                if ("".equals(entry.getName())) {
                    entry.setIncomplete(false);
                    continue;
                }
                if (entry.isDeleted()) {
                    if (!entry.isScheduledForAddition()) {
                        entries.deleteEntry(entry.getName());
                    } else {
                        entry.setDeleted(false);
                    }
                } else if (entry.isAbsent() && entry.getRevision() != myTargetRevision) {
                    entries.deleteEntry(entry.getName());
                } else if (entry.getKind() == SVNNodeKind.DIR) {
                    SVNDirectory childDirectory = info.getDirectory().getChildDirectory(entry.getName());
                    if (myIsRecursive && (childDirectory == null || !childDirectory.isVersioned()) && !entry.isAbsent() && !entry.isScheduledForAddition()) {
                        myWCAccess.handleEvent(SVNEventFactory.createUpdateDeleteEvent(myWCAccess, info.getDirectory(), entry));
                        entries.deleteEntry(entry.getName());
                    }
                }
            }
            entries.save(true);
            info = info.Parent;
        }
    }

    private SVNFileInfo createFileInfo(SVNDirectoryInfo parent, String path, boolean added) throws SVNException {
        SVNFileInfo info = new SVNFileInfo(parent, path);
        info.IsAdded = added;
        info.Name = SVNPathUtil.tail(path);
        SVNDirectory dir = parent.getDirectory();
        if (added && dir.getFile(info.Name).exists()) {
            SVNErrorManager.error("svn: Failed to add file '" + path + "': object of the same name already exists");
        }
        SVNEntries entries = null;
        try {
            entries = dir.getEntries();
            SVNEntry entry = entries.getEntry(info.Name, true);
            if (added && entry != null && entry.isScheduledForAddition()) {
                SVNErrorManager.error("svn: Failed to add file '" + path + "': object of the same name already exists and scheduled for addition");
            }
            if (!added && entry == null) {
                SVNErrorManager.error("svn: File '" + info.Name + "' in directory '" + dir.getRoot() + "' is not a versioned resource");
            }
            if (mySwitchURL != null || entry == null) {
                info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(info.Name));
            } else {
                info.URL = entry.getURL();
            }
        } finally {
            if (entries != null) {
                entries.close();
            }
        }
        parent.RefCount++;
        return info;
    }

    private SVNDirectoryInfo createDirectoryInfo(SVNDirectoryInfo parent, String path, boolean added) throws SVNException {
        SVNDirectoryInfo info = new SVNDirectoryInfo(path);
        info.Parent = parent;
        info.IsAdded = added;
        String name = path != null ? SVNPathUtil.tail(path) : "";

        if (mySwitchURL == null) {
            SVNDirectory dir = added ? null : info.getDirectory();
            if (dir != null && dir.getEntries().getEntry("", true) != null) {
                info.URL = dir.getEntries().getEntry("", true).getURL();
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

        public SVNDirectory getDirectory() {
            return Parent.getDirectory();
        }
    }

    private class SVNDirectoryInfo extends SVNEntryInfo {

        public int RefCount;
        private int myLogCount;

        public SVNDirectoryInfo(String path) {
            super(path);
        }

        public SVNDirectory getDirectory() {
            return myWCAccess.getDirectory(getPath());
        }

        public SVNLog getLog(boolean increment) {
            SVNLog log = getDirectory().getLog(myLogCount);
            if (increment) {
                myLogCount++;
            }
            return log;
        }

        public void runLogs() throws SVNException {
            getDirectory().runLogs();
            myLogCount = 0;
        }
    }
}
