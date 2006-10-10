/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.ISVNMergerFactory;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNDirectory {

    private File myDirectory;
    private SVNEntries myEntries;
    private SVNWCAccess myWCAccess;
    private String myPath;
    private File myAdminRoot;
    private File myLockFile;
    private File myEntriesFile;

    public SVNDirectory(SVNWCAccess wcAccess, String path, File dir) {
        myDirectory = dir;
        myAdminRoot = new File(dir, SVNFileUtil.getAdminDirectoryName());
        myLockFile = new File(myAdminRoot, "lock");
        myEntriesFile = new File(myAdminRoot, "entries");
        myPath = path;
        myWCAccess = wcAccess;
    }

    public SVNWCAccess getWCAccess() {
        return myWCAccess;
    }

    public String getPath() {
        return myPath;
    }

    public SVNDirectory[] getChildDirectories() {
        return myWCAccess.getChildDirectories(myPath);
    }

    public SVNDirectory getChildDirectory(String name) {
        return myWCAccess.getDirectory("".equals(myPath) ? name : SVNPathUtil.append(myPath, name));
    }

    public boolean isVersioned() {
        if (getAdminDirectory().isDirectory() && myEntriesFile.canRead()) {
            try {
                if (getEntries().getEntry("", false) != null) {
                    return true;
                }
            } catch (SVNException e) {
                //
            }
        }
        return false;
    }

    public boolean isLocked() {
        return getLockFile().isFile();
    }

    public boolean lock() throws SVNException {
        if (!isVersioned()) {
            return false;
        }
        return innerLock();
    }

    boolean innerLock() throws SVNException {
        if (getLockFile().isFile()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked; try performing ''cleanup''", getRoot());
            SVNErrorManager.error(err);
        }
        boolean created = false;
        try {
            created = getLockFile().createNewFile();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Cannot lock working copy ''{0}'': {1}", 
                    new Object[] {getRoot(), e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        }
        if (!created) {
            if (getLockFile().isFile()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' is locked; try performing 'cleanup'", getRoot());
                SVNErrorManager.error(err);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Cannot lock working copy ''{0}''", getRoot());
                SVNErrorManager.error(err);
            }
        }
        return created;
    }

    public boolean unlock() throws SVNException {
        if (!getLockFile().exists()) {
            return true;
        }
        // only if there are not locks or killme files.
        boolean killMe = getAdminFile("KILLME").exists();
        if (killMe) {
            return false;
        }
        File[] logs = getAdminDirectory().listFiles();
        for (int i = 0; logs != null && i < logs.length; i++) {
            File log = logs[i];
            if (log.getName().endsWith(".ro")) {
                // temporary readonly file, left there by mistake or after a crash.
                SVNFileUtil.deleteFile(log);
                continue;
            }
            if ("log".equals(log.getName()) || log.getName().startsWith("log.")) {
                return false;
            }

        }
        boolean deleted = getLockFile().delete();
        if (!deleted) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Failed to unlock working copy ''{0}''", getRoot());
            SVNErrorManager.error(err);
        }
        return deleted;
    }

    public SVNEntries getEntries() throws SVNException {
        if (myEntries == null) {
            myEntries = new SVNEntries(myEntriesFile);
        }
        myEntries.open();
        return myEntries;
    }

    public SVNProperties getProperties(String name, boolean tmp) {
        String path = !tmp ? "" : "tmp/";
        path += "".equals(name) ? "dir-props" : "props/" + name + ".svn-work";
        File propertiesFile = getAdminFile(path);
        return new SVNProperties(propertiesFile, getAdminDirectory().getName() + "/" + path);
    }

    public SVNProperties getBaseProperties(String name, boolean tmp) {
        String path = !tmp ? "" : "tmp/";
        path += "".equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
        File propertiesFile = getAdminFile(path);
        return new SVNProperties(propertiesFile, getAdminDirectory().getName() + "/" + path);
    }

    public SVNProperties getWCProperties(String name) {
        String path = "".equals(name) ? "dir-wcprops" : "wcprops/" + name + ".svn-work";
        File propertiesFile = getAdminFile(path);
        return new SVNProperties(propertiesFile, getAdminDirectory().getName() + "/" + path);
    }

    public SVNStatusType mergeProperties(String name, Map serverBaseProps, Map propDiff, boolean baseMerge, SVNLog log) throws SVNException {
        serverBaseProps = serverBaseProps == null ? Collections.EMPTY_MAP : serverBaseProps;
        propDiff = propDiff == null ? Collections.EMPTY_MAP : propDiff;
        
        SVNProperties working = getProperties(name, false);
        Map workingProps = working.asMap();
        SVNProperties workingTmp = getProperties(name, true);
        SVNProperties base = getBaseProperties(name, false);
        SVNProperties baseTmp = getBaseProperties(name, true);

        working.copyTo(workingTmp);
        if (baseMerge) {
            base.copyTo(baseTmp);
        }

        Collection conflicts = new ArrayList();
        SVNStatusType result = propDiff.isEmpty() ? SVNStatusType.UNCHANGED : SVNStatusType.CHANGED;
        
        for (Iterator propEntries = propDiff.entrySet().iterator(); propEntries.hasNext();) {
            Map.Entry incomingEntry = (Map.Entry) propEntries.next();
            String propName = (String) incomingEntry.getKey();
            String toValue = (String) incomingEntry.getValue();
            String fromValue = (String) serverBaseProps.get(propName);
            String workingValue = (String) workingProps.get(propName);
            if (baseMerge) {
                baseTmp.setPropertyValue(propName, toValue);
            }
            if (fromValue == null) {
                if (workingValue != null) {
                    if (workingValue.equals(toValue)) {
                        result = result != SVNStatusType.CONFLICTED ? SVNStatusType.MERGED : result;
                    } else {
                        conflicts.add(MessageFormat.format("Trying to add new property ''{0}'' with value ''{1}'',\n" +
                                "but property already exists with value ''{2}''.", new Object[] { propName, toValue, workingValue }));
                        result = SVNStatusType.CONFLICTED;                            
                    }
                } else {
                    workingTmp.setPropertyValue(propName, toValue);
                }
            } else {
                if (workingValue == null) {
                    if (toValue != null) {
                        result = SVNStatusType.CONFLICTED;
                        conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                "but the property does not exist.", new Object[] { propName, fromValue, toValue }));
                    } else {
                        result = result != SVNStatusType.CONFLICTED ? SVNStatusType.MERGED : result;
                    }
                } else {
                    if (workingValue.equals(fromValue)) {
                        workingTmp.setPropertyValue(propName, toValue);
                    } else if (toValue == null && !workingValue.equals(fromValue)) {
                        result = SVNStatusType.CONFLICTED;
                        conflicts.add(MessageFormat.format("Trying to delete property ''{0}'' but value has been modified from ''{1}'' to ''{2}''.",
                                 new Object[] { propName, fromValue, workingValue }));
                    } else if (workingValue.equals(toValue)) {
                        result = result != SVNStatusType.CONFLICTED ? SVNStatusType.MERGED : result;
                    } else {
                        result = SVNStatusType.CONFLICTED;
                        conflicts.add(MessageFormat.format("Trying to change property ''{0}'' from ''{1}'' to ''{2}'',\n" +
                                "but property already exists with value ''{3}''.", new Object[] { propName, fromValue, toValue, workingValue }));
                    }
                }
            }
        }
        Map command = new HashMap();
        if (log != null) {
            command.put(SVNLog.NAME_ATTR, workingTmp.getPath());
            command.put(SVNLog.DEST_ATTR, working.getPath());
            log.addCommand(SVNLog.MOVE, command, false);
            command.clear();
            command.put(SVNLog.NAME_ATTR, working.getPath());
            log.addCommand(SVNLog.READONLY, command, false);
            if (baseMerge) {
                command.put(SVNLog.NAME_ATTR, baseTmp.getPath());
                command.put(SVNLog.DEST_ATTR, base.getPath());
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, base.getPath());
                log.addCommand(SVNLog.READONLY, command, false);
            }
        }

        if (!conflicts.isEmpty()) {
            result = SVNStatusType.CONFLICTED;
            if (log != null) {
                String prejTmpPath = "".equals(name) ? "tmp/dir_conflicts" : "tmp/props/" + name;
                File prejTmpFile = SVNFileUtil.createUniqueFile(getAdminDirectory(),  prejTmpPath, ".prej");
                prejTmpPath = SVNFileUtil.getBasePath(prejTmpFile);

                String prejPath = getEntries().getEntry(name, true)
                        .getPropRejectFile();
                getEntries().close();

                if (prejPath == null) {
                    prejPath = "".equals(name) ? "dir_conflicts" : name;
                    File prejFile = SVNFileUtil.createUniqueFile(getRoot(),
                            prejPath, ".prej");
                    prejPath = SVNFileUtil.getBasePath(prejFile);
                }
                File file = getFile(prejTmpPath);
                OutputStream os = SVNFileUtil.openFileForWriting(file);
                try {
                    for (Iterator lines = conflicts.iterator(); lines.hasNext();) {
                        String line = (String) lines.next();
                        os.write(line.getBytes("UTF-8"));
                    }
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot write properties conflict file: {1}", e.getLocalizedMessage());
                    SVNErrorManager.error(err, e);
                } finally {
                    SVNFileUtil.closeFile(os);
                }

                command.put(SVNLog.NAME_ATTR, prejTmpPath);
                command.put(SVNLog.DEST_ATTR, prejPath);
                log.addCommand(SVNLog.APPEND, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, prejTmpPath);
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();

                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNProperty
                        .shortPropertyName(SVNProperty.PROP_REJECT_FILE),
                        prejPath);
                log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            }
        }
        if (log == null) {
            workingTmp.delete();
            baseTmp.delete();
        }
        return result;
    }

    public SVNStatusType mergeText(String localPath, String basePath,
            String latestPath, String localLabel, String baseLabel,
            String latestLabel, boolean leaveConflict, boolean dryRun) throws SVNException {
        String mimeType = getProperties(localPath, false).getPropertyValue(SVNProperty.MIME_TYPE);
        SVNEntry entry = getEntries().getEntry(localPath, true);
        if (SVNProperty.isBinaryMimeType(mimeType)) {
            // binary
            if (!dryRun) {
                File oldFile = SVNFileUtil.createUniqueFile(getRoot(),
                        localPath, baseLabel);
                File newFile = SVNFileUtil.createUniqueFile(getRoot(),
                        localPath, latestLabel);
                SVNFileUtil.copyFile(getFile(basePath), oldFile, false);
                SVNFileUtil.copyFile(getFile(latestPath), newFile, false);
                // update entry props
                entry.setConflictNew(SVNFileUtil.getBasePath(newFile));
                entry.setConflictOld(SVNFileUtil.getBasePath(oldFile));
                entry.setConflictWorking(null);
            }
            return SVNStatusType.CONFLICTED;
        }
        // text
        // 1. destranslate local
        File localTmpFile = SVNFileUtil.createUniqueFile(getRoot(), localPath, ".tmp");
        SVNTranslator.translate(this, localPath, localPath, SVNFileUtil.getBasePath(localTmpFile), false, false);
        // 2. run merge between all files we have :)
        OutputStream result = null;
        File resultFile = dryRun ? null : SVNFileUtil.createUniqueFile(getRoot(), localPath, ".result");

        byte[] conflictStart = ("<<<<<<< " + localLabel).getBytes();
        byte[] conflictEnd = (">>>>>>> " + latestLabel).getBytes();
        byte[] separator = ("=======").getBytes();
        ISVNMergerFactory factory = myWCAccess.getOptions().getMergerFactory();
        ISVNMerger merger = factory.createMerger(conflictStart, separator, conflictEnd);
        
        result = resultFile == null ? SVNFileUtil.DUMMY_OUT : SVNFileUtil.openFileForWriting(resultFile);
        SVNStatusType status = SVNStatusType.UNCHANGED;
        try {
            status = merger.mergeText(getFile(basePath), localTmpFile, getFile(latestPath), dryRun, result);
        } finally {
            SVNFileUtil.closeFile(result);
        }
        if (dryRun) {
            localTmpFile.delete();
            if (leaveConflict && status == SVNStatusType.CONFLICTED) {
                return SVNStatusType.CONFLICTED_UNRESOLVED;
            }
            return status;
        }
        if (status != SVNStatusType.CONFLICTED) {
            SVNTranslator.translate(this, localPath, SVNFileUtil.getBasePath(resultFile), localPath, true, true);
        } else {
            // copy all to wc.
            File mineFile = SVNFileUtil.createUniqueFile(getRoot(), localPath, localLabel);
            String minePath = SVNFileUtil.getBasePath(mineFile);
            SVNFileUtil.copyFile(getFile(localPath), mineFile, false);
            File oldFile = SVNFileUtil.createUniqueFile(getRoot(), localPath, baseLabel);
            String oldPath = SVNFileUtil.getBasePath(oldFile);
            File newFile = SVNFileUtil.createUniqueFile(getRoot(), localPath, latestLabel);
            String newPath = SVNFileUtil.getBasePath(newFile);
            SVNTranslator.translate(this, localPath, basePath, oldPath, true, false);
            SVNTranslator.translate(this, localPath, latestPath, newPath, true, false);
            // translate result to local
            if (!leaveConflict) {
                SVNTranslator.translate(this, localPath, SVNFileUtil.getBasePath(resultFile), localPath, true, true);
            }

            entry.setConflictNew(newPath);
            entry.setConflictOld(oldPath);
            entry.setConflictWorking(minePath);
        }
        localTmpFile.delete();
        if (resultFile != null) {
            resultFile.delete();
        }
        if (status == SVNStatusType.CONFLICTED && leaveConflict) {
            return SVNStatusType.CONFLICTED_UNRESOLVED;
        }
        return status;
    }

    public boolean markResolved(String name, boolean text, boolean props) throws SVNException {
        if (!text && !props) {
            return false;
        }
        SVNEntry entry = getEntries().getEntry(name, true);
        if (entry == null) {
            return false;
        }
        boolean modified = false;
        if (text && entry.getConflictOld() != null) {
            modified = true;
            File file = new File(myDirectory, entry.getConflictOld());
            file.delete();
            entry.setConflictOld(null);
        }
        if (text && entry.getConflictNew() != null) {
            modified = true;
            File file = new File(myDirectory, entry.getConflictNew());
            file.delete();
            entry.setConflictNew(null);
        }
        if (text && entry.getConflictWorking() != null) {
            modified = true;
            File file = new File(myDirectory, entry.getConflictWorking());
            file.delete();
            entry.setConflictWorking(null);
        }
        if (props && entry.getPropRejectFile() != null) {
            File file = new File(myDirectory, entry.getPropRejectFile());
            file.delete();
            modified = true;
            entry.setPropRejectFile(null);
        }
        if (modified) {
            getEntries().save(false);
        }
        return modified;
    }

    public boolean revert(String name) throws SVNException {
        boolean magicPropsChanged = false;
        boolean wasReverted = false;

        SVNEntry entry = getEntries().getEntry(name, true);
        if (entry == null || entry.isHidden()) {
            return wasReverted;
        }

        SVNProperties baseProps = getBaseProperties(name, false);
        SVNProperties wcProps = getProperties(name, false);
        if (hasPropModifications(name)) {
            Map propDiff = baseProps.compareTo(wcProps);
            if (propDiff != null && !propDiff.isEmpty()) {
                magicPropsChanged = propDiff
                        .containsKey(SVNProperty.EXECUTABLE)
                        || propDiff.containsKey(SVNProperty.KEYWORDS)
                        || propDiff.containsKey(SVNProperty.NEEDS_LOCK)
                        || propDiff.containsKey(SVNProperty.EOL_STYLE)
                        || propDiff.containsKey(SVNProperty.SPECIAL);
            }
            // copy base over wc
            if (baseProps.getFile().isFile()) {
                baseProps.copyTo(wcProps);
                entry.setPropTime(SVNTimeUtil.formatDate(new Date(wcProps
                        .getFile().lastModified())));
            } else {
                wcProps.delete();
                entry.setPropTime(null);
            }
            getEntries().save(false);
            wasReverted = true;
        } else if (entry.isScheduledForReplacement()) {
            baseProps.copyTo(wcProps);
            entry.setPropTime(SVNTimeUtil.formatDate(new Date(wcProps.getFile().lastModified())));
            wasReverted = true;
        }

        if (entry.isFile()) {
            boolean textModified = false;
            if (!magicPropsChanged) {
                textModified = hasTextModifications(name, false);
            }
            File file = getFile(entry.getName());
            if (textModified || magicPropsChanged || !file.exists()) {
                // copy base to wc and expand.
                boolean special = wcProps.getPropertyValue(SVNProperty.SPECIAL) != null;

                File src = getBaseFile(name, false);
                File dst = getFile(name);
                if (!src.exists()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LEFT_LOCAL_MOD, "Error restoring text for ''{0}''", dst);
                    SVNErrorManager.error(err);
                }
                SVNTranslator.translate(this, name, SVNFileUtil.getBasePath(src), SVNFileUtil.getBasePath(dst), true, true);

                boolean executable = wcProps.getPropertyValue(SVNProperty.EXECUTABLE) != null;
                boolean needsLock = wcProps.getPropertyValue(SVNProperty.NEEDS_LOCK) != null;
                if (executable) {
                    SVNFileUtil.setExecutable(dst, true);
                }
                long tstamp;
                if (myWCAccess.getOptions().isUseCommitTimes() && !special) {
                    entry.setTextTime(entry.getCommittedDate());
                    tstamp = SVNTimeUtil.parseDate(entry.getCommittedDate()).getTime();
                    dst.setLastModified(tstamp);
                } else {
                    tstamp = System.currentTimeMillis();
                    dst.setLastModified(tstamp);
                    entry.setTextTime(SVNTimeUtil.formatDate(new Date(tstamp)));
                }
                if (needsLock) {
                    SVNFileUtil.setReadonly(dst, entry.getLockToken() == null);
                }
                getEntries().save(false);
                wasReverted |= true;
            }
            wasReverted |= markResolved(name, true, false);
        }
        return wasReverted;
    }

    public boolean hasTextModifications(String name, boolean force) throws SVNException {
        SVNFileType fType = SVNFileType.getType(getFile(name));
        if (fType == SVNFileType.DIRECTORY || fType == SVNFileType.NONE) {
            return false;
        }
        SVNEntries entries = getEntries();
        if (entries == null || entries.getEntry(name, true) == null) {
            return false;
        }
        SVNEntry entry = entries.getEntry(name, true);
        if (entry.isDirectory()) {
            return false;
        }
        if (!force) {
            String textTime = entry.getTextTime();
            long textTimeAsLong = SVNFileUtil.roundTimeStamp(SVNTimeUtil.parseDateAsLong(textTime));
            long tstamp = SVNFileUtil.roundTimeStamp(getFile(name).lastModified());
            if (textTimeAsLong == tstamp ) {
                return false;
            }
        }
        File baseFile = getBaseFile(name, false);
        if (!baseFile.isFile()) {
            return true;
        }
        // translate versioned file.
        File baseTmpFile = SVNFileUtil.createUniqueFile(getRoot(), 
                SVNFileUtil.getBasePath(getBaseFile(name, true)), ".tmp");
        if (!baseTmpFile.getParentFile().exists()) {
            baseTmpFile.getParentFile().mkdirs();
        }
        File versionedFile = getFile(name);
        SVNTranslator.translate(this, name, name, SVNFileUtil.getBasePath(baseTmpFile), false, false);

        // now compare file and get base file checksum (when forced)
        MessageDigest digest;
        boolean equals = true;
        try {
            digest = force ? MessageDigest.getInstance("MD5") : null;
            equals = SVNFileUtil.compareFiles(baseFile, baseTmpFile, digest);
            if (force) {
                // if checksum differs from expected - throw exception
                String checksum = SVNFileUtil.toHexDigest(digest);
                if (!checksum.equals(entry.getChecksum())) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch indicates corrupt text base: ''{0}''\n" +
                            "   expected: {1}\n" +
                            "     actual: {2}\n", new Object[] {baseFile, entry.getChecksum(), checksum});
                    SVNErrorManager.error(err);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {1}", e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            baseTmpFile.delete();
        }

        if (equals && isLocked()) {
            entry.setTextTime(SVNTimeUtil.formatDate(new Date(versionedFile
                    .lastModified())));
            entries.save(false);
        }
        return !equals;
    }

    public boolean hasPropModifications(String name) throws SVNException {
        File propFile;
        File baseFile;
        if ("".equals(name)) {
            propFile = getAdminFile("dir-props");
            baseFile = getAdminFile("dir-prop-base");
        } else {
            propFile = getAdminFile("props/" + name + ".svn-work");
            baseFile = getAdminFile("prop-base/" + name + ".svn-base");
        }
        SVNEntry entry = getEntries().getEntry(name, true);
        long propLength = propFile.length();
        boolean propEmtpy = propLength <= 4;
        if (entry.isScheduledForReplacement()) {
            return !propEmtpy;
        }
        if (propEmtpy) {
            boolean baseEmtpy = baseFile.length() <= 4;
            if (baseEmtpy) {
                return !propEmtpy;
            }
            return true;
        }
        if (propLength != baseFile.length()) {
            return true;
        }
        String realTimestamp = SVNTimeUtil.formatDate(new Date(propFile.lastModified()));
        String fullRealTimestamp = realTimestamp;
        realTimestamp = realTimestamp.substring(0, 23);
        String timeStamp = entry.getPropTime();
        if (timeStamp != null) {
            timeStamp = timeStamp.substring(0, 23);
            if (realTimestamp.equals(timeStamp)) {
                return false;
            }
        }
        Map m1 = getProperties(name, false).asMap();
        Map m2 = getBaseProperties(name, false).asMap();
        if (m1.equals(m2)) {
            if (isLocked()) {
                entry.setPropTime(fullRealTimestamp);
                getEntries().save(false);
            }
            return false;
        }
        return true;
    }

    public void cleanup() throws SVNException {
        getWCAccess().checkCancelled();
        SVNEntries svnEntries = getEntries();
        for (Iterator entries = svnEntries.entries(true); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (!"".equals(entry.getName()) && entry.isDirectory()) {
                SVNDirectory childDir = getChildDirectory(entry.getName());
                if (childDir != null) {
                    childDir.cleanup();
                }
                continue;
            }
            hasTextModifications(entry.getName(), false);
            hasPropModifications(entry.getName());
        }
        svnEntries.save(true);
        if (getAdminFile("KILLME").isFile()) {
            destroy("", true);
        } else {
            runLogs();
        }
        File tmpDir = getAdminFile("tmp");
        if (tmpDir.isDirectory()) {
            SVNFileUtil.deleteAll(tmpDir, false, getWCAccess());
        }
    }

    public void canScheduleForDeletion(String name, boolean skipIgnored) throws SVNException {
        // TODO use status call.
        // check if this dir doesn't have obstructed, unversioned or modified
        // entries.
        SVNEntries entries = getEntries();
        File[] files = getRoot().listFiles();
        if (files == null) {
            return;
        }
        getWCAccess().checkCancelled();
        if ("".equals(name) && hasPropModifications(name)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MODIFIED, "''{0}'' has local modifications", getPath());
            SVNErrorManager.error(err);
        }
        for (int i = 0; i < files.length; i++) {
            File childFile = files[i];
            if (getAdminDirectory().getName().equals(childFile.getName())) {
                continue;
            }
            if (!"".equals(name) && !childFile.getName().equals(name)) {
                continue;
            }
            String fileName = childFile.getName();
            SVNEntry entry = entries.getEntry(fileName, true);
            String path = SVNPathUtil.append(getPath(), fileName);
            path = path.replace('/', File.separatorChar);
            if (entry == null || entry.isHidden()) {
                // no entry or entry is 'deleted'
                // no error if file is ignored.
                if (skipIgnored && isIgnored(fileName)) {
                    continue;
                } else if (skipIgnored && getWCAccess() != null) {
                    ISVNOptions options = getWCAccess().getOptions();
                    if (options != null && options.isIgnored(fileName)) {
                        continue;
                    }
                }
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", path);
                SVNErrorManager.error(err);
            } else {
                SVNNodeKind kind = entry.getKind();
                SVNFileType fileType = SVNFileType.getType(childFile);
                if ((fileType.isFile() && kind == SVNNodeKind.DIR) || (fileType == SVNFileType.DIRECTORY && kind == SVNNodeKind.FILE)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "''{0}'' is in the way of the resource actually under version control", path);
                    SVNErrorManager.error(err);
                } else if (kind == SVNNodeKind.FILE) {
                    // chek for mods.
                    if (hasTextModifications(entry.getName(), false)
                            || hasPropModifications(entry.getName())) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MODIFIED, "''{0}'' has local modifications", path);
                        SVNErrorManager.error(err);
                    }
                }
                if (kind == SVNNodeKind.DIR) {
                    SVNDirectory childDir = getChildDirectory(childFile
                            .getName());
                    if (childDir != null) {
                        childDir.canScheduleForDeletion("", skipIgnored);
                    }
                }
            }
        }
    }

    public void dispose() {
        if (myEntries != null) {
            myEntries.close();
        }
        myEntries = null;
    }

    private File getLockFile() {
        return myLockFile;
    }

    public File getAdminDirectory() {
        return myAdminRoot;
    }

    public File getFile(String name) {
        return new File(getRoot(), name);
    }

    public File getAdminFile(String name) {
        return new File(getAdminDirectory(), name);
    }

    public File getBaseFile(String name, boolean tmp) {
        String path = tmp ? "tmp/" : "";
        path += "text-base/" + name + ".svn-base";
        return getAdminFile(path);
    }

    public File getRoot() {
        return myDirectory;
    }

    public SVNLog getLog(int id) {
        return new SVNLog(this, id);
    }

    public void runLogs() throws SVNException {
        SVNLogRunner runner = new SVNLogRunner();
        int index = 0;
        Collection processedLogs = new ArrayList();
        // find first, not yet executed log file.
        try {
            while (true) {
                SVNLog log = new SVNLog(this, index);
                getWCAccess().checkCancelled();
                if (log.exists()) {
                    log.run(runner);
                    processedLogs.add(log);
                    index++;
                    continue;
                }
                break;
            }
        } catch (SVNException e) {
            // to save modifications made to .svn/entries
            runner.logFailed(this);
            deleteLogs(processedLogs);
            int newIndex = 0;
            while (true && index != 0) {
                File logFile = getAdminFile("log." + index);
                if (logFile.exists()) {
                    File newFile = getAdminFile(newIndex == 0 ? "log" : "log." + newIndex);
                    SVNFileUtil.rename(logFile, newFile);
                    newIndex++;
                    index++;
                    continue;
                }
                break;
            }
            throw e;
        }
        runner.logCompleted(this);
        deleteLogs(processedLogs);
    }
    
    private static void deleteLogs(Collection logsList) {
        for (Iterator logs = logsList.iterator(); logs.hasNext();) {
            SVNLog log = (SVNLog) logs.next();
            log.delete();
        }
    }

    public SVNDirectory createChildDirectory(String name, String url, String reposURL, long revision) throws SVNException {
        File dir = new File(myDirectory, name);
        createVersionedDirectory(dir);

        String childPath = SVNPathUtil.append(myPath, name);

        SVNDirectory child = myWCAccess.addDirectory(childPath, dir);
        SVNEntry rootEntry = child.getEntries().getEntry("", true);
        if (rootEntry == null) {
            rootEntry = child.getEntries().addEntry("");
        }
        if (url != null) {
            rootEntry.setURL(url);
        }
        rootEntry.setRepositoryRoot(reposURL);
        rootEntry.setRevision(revision);
        rootEntry.setKind(SVNNodeKind.DIR);
        child.getEntries().save(true);
        return child;
    }

    public static void createVersionedDirectory(File dir) throws SVNException {
        dir.mkdirs();
        File adminDir = new File(dir, SVNFileUtil.getAdminDirectoryName());
        adminDir.mkdir();
        SVNFileUtil.setHidden(adminDir, true);
        // lock dir.
        File lock = new File(adminDir, "lock"); 
        SVNFileUtil.createEmptyFile(lock);
        SVNAdminUtil.createReadmeFile(adminDir);
        SVNFileUtil.createEmptyFile(new File(adminDir, "empty-file"));
        File[] tmp = {
                new File(adminDir, "tmp"),
                new File(adminDir, "tmp" + File.separatorChar + "props"),
                new File(adminDir, "tmp" + File.separatorChar + "prop-base"),
                new File(adminDir, "tmp" + File.separatorChar + "text-base"),
                new File(adminDir, "tmp" + File.separatorChar + "wcprops"),
                new File(adminDir, "props"), new File(adminDir, "prop-base"),
                new File(adminDir, "text-base"), new File(adminDir, "wcprops") };
        for (int i = 0; i < tmp.length; i++) {
            tmp[i].mkdir();
        }
        SVNAdminUtil.createFormatFile(adminDir);
        // unlock dir.
        SVNFileUtil.deleteFile(lock);
    }

    public void destroy(String name, boolean deleteWorkingFiles) throws SVNException {
        getWCAccess().checkCancelled();
        
        if ("".equals(name)) {
            SVNDirectory parent = null;
            if ("".equals(myPath)) {
                SVNWCAccess parentWCAccess = null;
                try {
                    parentWCAccess = SVNWCAccess.create(getRoot().getParentFile());
                    parentWCAccess.open(true, false);
                    parent = parentWCAccess.getAnchor();
                    destroyDirectory(parent, this, deleteWorkingFiles);
                } catch (SVNException e) {
                    parent = null;
                } finally {
                    if (parent != null) {
                        parent.getEntries().save(true);
                    }
                    if (parentWCAccess != null) {
                        parentWCAccess.close(true);
                    }
                    myWCAccess.removeDirectory("");
                }
                if (parent != null) {
                    return;
                }
            } else {
                String parentPath = SVNPathUtil.removeTail(myPath);
                parent = myWCAccess.getDirectory(parentPath);
                if (parent != null && !parent.isVersioned()) {
                    parent = null;
                }
            }
            destroyDirectory(parent, this, deleteWorkingFiles);
            myWCAccess.removeDirectory(myPath);
            if (parent != null) {
                parent.getEntries().save(true);
            }
        } else {
            File file = getFile(name);
            SVNFileType type = SVNFileType.getType(file);
            if (type == SVNFileType.DIRECTORY) {
                SVNDirectory childDir = getChildDirectory(name);
                if (childDir != null && childDir.isVersioned()) {
                    destroyDirectory(this, childDir, deleteWorkingFiles);
                    myWCAccess.removeDirectory(childDir.getPath());
                }
            } else {
                destroyFile(name, deleteWorkingFiles);
            }
        }
        getEntries().save(false);
    }

    public void scheduleForDeletion(String name, boolean deleteFiles) throws SVNException {
        SVNEntries entries = getEntries();
        SVNEntry entry = entries.getEntry(name, false);
        if (entry == null) {
            if (deleteFiles) {
                SVNFileUtil.deleteAll(getFile(name), getWCAccess());
            }
            return;
        }
        
        getWCAccess().checkCancelled();

        boolean added = entry.isScheduledForAddition();
        boolean deleted = false;
        SVNNodeKind kind = entry.getKind();
        if (entry.getKind() == SVNNodeKind.DIR) {
            // try to get parent entry
            SVNDirectory parent;
            SVNDirectory child;
            String nameInParent;
            if (!"".equals(name)) {
                parent = this;
                nameInParent = name;
                child = getChildDirectory(name);
            } else {
                child = this;
                nameInParent = SVNPathUtil.tail(myPath);
                String parentPath = SVNPathUtil.removeTail(myPath);
                parent = myWCAccess.getDirectory(parentPath);
            }
            deleted = parent != null ? parent.getEntries().getEntry(nameInParent, true).isDeleted() : false;
            if (added && !deleted) {
                // destroy whole child dir.
                if (child != null) {
                    child.destroy("", deleteFiles);
                } else {
                    // no child, remove entry in parent
                    parent.getEntries().deleteEntry(nameInParent);
                    parent.getEntries().save(false);
                }
            } else if (child != null) {
                // recursively mark for deletion (but not "").
                child.updateEntryProperty(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_DELETE, true);
            }
            if (parent != null) {
                parent.getEntries().save(false);
            }
            if (child != null) {
                child.getEntries().save(false);
            }
        }
        if (!(kind == SVNNodeKind.DIR && added && !deleted)) {
            // schedule entry in parent.
            entry.scheduleForDeletion();
        }
        SVNEvent event = SVNEventFactory.createDeletedEvent(myWCAccess, this, entry.getName());
        myWCAccess.handleEvent(event);
        if (deleteFiles) {
            if (added) {
                SVNFileUtil.deleteAll(getFile(name), getWCAccess());
            } else {
                deleteWorkingFiles(name);
            }
        }
        entries.save(true);
    }

    public SVNEntry add(String name, boolean mkdir, boolean force) throws SVNException {
    	File file = getFile(name);
        SVNFileType fileType = SVNFileType.getType(file);
        if (fileType == SVNFileType.NONE && !mkdir) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' not found", file);            
            SVNErrorManager.error(err);
        }
        SVNNodeKind fileKind = fileType == SVNFileType.NONE || fileType == SVNFileType.DIRECTORY ? 
                SVNNodeKind.DIR : SVNNodeKind.FILE;
        SVNEntry entry = getEntries().getEntry(name, true);

        if (entry != null && !entry.isDeleted() && !entry.isScheduledForDeletion()) {
            if (!force) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' already under version control", file);            
                SVNErrorManager.error(err);
            }
            return entry;
        } else if (entry != null && entry.getKind() != fileKind) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NODE_KIND_CHANGE, "Can''t replace ''{0}'' with a node of different type;" +
                    "\ncommit the deletion, update the parent and then add ''{0}''", file);            
            SVNErrorManager.error(err);
        } else if (entry == null && fileType != SVNFileType.SYMLINK && SVNWCUtil.isVersionedDirectory(file)) {
            if (!force) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' already under version control", file);            
                SVNErrorManager.error(err);
            }
            return null;
        }
        boolean replace = entry != null && entry.isScheduledForDeletion();
        // TODO check parent dir
        if (entry == null) {
            entry = getEntries().addEntry(name);
        }
        if (replace) {
            entry.scheduleForReplacement();
        } else {
            entry.scheduleForAddition();
        }
        if (!replace) {
            entry.setRevision(0);
        }
        entry.setKind(fileKind);
        if (replace) {
            getProperties(name, false).delete();
        }
        if (fileKind == SVNNodeKind.DIR) {
            // compose new url
            String parentURL = getEntries().getEntry("", true).getURL();
            String reposURL = getEntries().getEntry("", true).getRepositoryRoot();
            String childURL = SVNPathUtil.append(parentURL, SVNEncodingUtil.uriEncode(name));
            // if child dir exists (deleted) -> check that url is the same and
            // revision is the same
            SVNDirectory childDir = getChildDirectory(name);
            if (childDir != null) {
                String existingURL = childDir.getEntries().getEntry("", true).getURL();
                if (!existingURL.equals(childURL)) {
                    // TODO not sure this should be done this way
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.UNKNOWN));
                }
            } else {
                childDir = createChildDirectory(name, childURL, reposURL, 0);
            }
            if (!replace) {
                childDir.getEntries().getEntry("", true).scheduleForAddition();
            } else {
                childDir.getEntries().getEntry("", true).scheduleForReplacement();
            }
            childDir.getEntries().save(true);
        }
        SVNEvent event = SVNEventFactory.createAddedEvent(myWCAccess, this, entry);
        myWCAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);
        getEntries().save(false);
        return entry;
    }

    public void updateEntryProperty(String propertyName, String value, boolean recursive) throws SVNException {
        SVNEntries entries = getEntries();
        for (Iterator ents = entries.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            if (entry.isDirectory() && recursive) {
                SVNDirectory childDir = getChildDirectory(entry.getName());
                if (childDir != null) {
                    childDir.updateEntryProperty(propertyName, value, recursive);
                }
            }
            entries.setPropertyValue(entry.getName(), propertyName, value);
            if (SVNProperty.SCHEDULE_DELETE.equals(value)) {
                SVNEvent event = SVNEventFactory.createDeletedEvent(myWCAccess, this, entry.getName());
                myWCAccess.handleEvent(event);
            }
        }
        SVNEntry root = entries.getEntry("", true);
        if (!(SVNProperty.SCHEDULE_DELETE.equals(value) && root.isScheduledForAddition())) {
            root.scheduleForDeletion();
        }
        entries.save(false);
    }

    public void updateURL(String rootURL, boolean recursive) throws SVNException {
        SVNEntries entries = getEntries();
        for (Iterator ents = entries.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (!"".equals(entry.getName()) && entry.isDirectory() && recursive) {
                SVNDirectory childDir = getChildDirectory(entry.getName());
                if (childDir != null) {
                    String childURL = SVNPathUtil.append(rootURL, SVNEncodingUtil.uriEncode(entry.getName()));
                    childDir.updateURL(childURL, recursive);
                }
                continue;
            }
            entries.setPropertyValue(entry.getName(), SVNProperty.URL, ""
                    .equals(entry.getName()) ? rootURL : SVNPathUtil.append(
                    rootURL, SVNEncodingUtil.uriEncode(entry.getName())));
        }
        entries.save(false);
    }

    private void deleteWorkingFiles(String name) throws SVNException {
        File file = getFile(name);
        SVNFileType fileType = SVNFileType.getType(file);
        if (fileType == SVNFileType.DIRECTORY) {
            SVNDirectory childDir = getChildDirectory(file.getName());
            if (childDir != null) {
                SVNEntries childEntries = childDir.getEntries();
                for (Iterator childEnts = childEntries.entries(true); childEnts.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) childEnts.next();
                    if ("".equals(childEntry.getName())) {
                        continue;
                    }
                    childDir.deleteWorkingFiles(childEntry.getName());
                }
                File[] allFiles = file.listFiles();
                for (int i = 0; allFiles != null && i < allFiles.length; i++) {
                    if (getAdminDirectory().getName().equals(allFiles[i].getName())) {
                        continue;
                    }
                    if (childEntries.getEntry(allFiles[i].getName(), true) != null) {
                        continue;
                    }
                    SVNFileUtil.deleteAll(allFiles[i], getWCAccess());
                }
            } else {
                SVNFileUtil.deleteAll(file, getWCAccess());
            }
        } else {
            file.delete();
        }
    }

    public void setWCAccess(SVNWCAccess wcAccess, String path) {
        myWCAccess = wcAccess;
        myPath = path;
    }

    private void destroyFile(String name, boolean deleteWorkingFile)
            throws SVNException {
        SVNEntries entries = getEntries();
        if (entries.getEntry(name, true) != null) {
            if (deleteWorkingFile && !hasTextModifications(name, false)) {
                getFile(name).delete();
            }
        }
        entries.deleteEntry(name);

        getBaseProperties(name, false).delete();
        getWCProperties(name).delete();

        getProperties(name, false).delete();
        File baseFile = getBaseFile(name, false);
        baseFile.delete();
    }

    private static void destroyDirectory(SVNDirectory parent, SVNDirectory dir, boolean deleteWorkingFiles) throws SVNException {
        dir.getWCAccess().checkCancelled();
        SVNEntries entries = dir.getEntries();
        entries.getEntry("", true).setIncomplete(true);

        for (Iterator ents = entries.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            if (entry.getKind() == SVNNodeKind.FILE) {
                dir.destroyFile(entry.getName(), deleteWorkingFiles);
            } else if (entry.getKind() == SVNNodeKind.DIR) {
                SVNDirectory childDirectory = dir.getChildDirectory(entry.getName());
                if (childDirectory == null) {
                    try {
                        File childFile = dir.getFile(entry.getName());
                        String childPath = SVNPathUtil.append(dir.getPath(), entry.getName());
                        childDirectory = dir.getWCAccess().addDirectory(childPath, childFile, true, true, true);
                    } catch (SVNException e) {
                        //
                    }
                    if (childDirectory != null) {
                        destroyDirectory(dir, childDirectory, deleteWorkingFiles);
                    } else {
                        entries.deleteEntry(entry.getName());
                    }
                } else {
                    destroyDirectory(dir, childDirectory, deleteWorkingFiles);
                }
            }
        }

        if (parent != null) {
            SVNEntries parentEntries = parent.getEntries();
            parentEntries.deleteEntry(dir.getRoot().getName());
        }
        dir.getEntries().save(true);

        SVNFileUtil.deleteAll(new File(dir.getRoot(), dir.getAdminDirectory().getName()), dir.getWCAccess());
        if (deleteWorkingFiles) {
            dir.getRoot().delete();
        }
    }

    public void commit(String target, SVNCommitInfo info, Map wcPropChanges,
            boolean removeLock, boolean recursive, Collection explicitCommitPaths) throws SVNException {
        
        String path = "".equals(target) ? getPath() : SVNPathUtil.append(getPath(), target);
        if (!explicitCommitPaths.contains(path)) {
            // if this item is explicitly copied -> skip it.
            SVNEntry entry = getEntries().getEntry(target, true);
            if (entry != null && entry.getCopyFromURL() != null) {
                return;
            }
        }

        SVNLog log = getLog(0);
        //
        String checksum = null;
        if (!"".equals(target)) {
            File baseFile = getBaseFile(target, true);
            SVNFileType baseType = SVNFileType.getType(baseFile);
            if (baseType == SVNFileType.NONE) {
                baseFile = getBaseFile(target, false);
                baseType = SVNFileType.getType(baseFile);
            }
            if (baseType == SVNFileType.FILE) {
                checksum = SVNFileUtil.computeChecksum(baseFile);
            }
            recursive = false;
        } else {

        }
        Map command = new HashMap();
        if (info != null) {
            command.put(SVNLog.NAME_ATTR, target);
            command.put(SVNProperty.shortPropertyName(SVNProperty.COMMITTED_REVISION), Long.toString(info.getNewRevision()));
            command.put(SVNProperty.shortPropertyName(SVNProperty.COMMITTED_DATE), SVNTimeUtil.formatDate(info.getDate()));
            command.put(SVNProperty.shortPropertyName(SVNProperty.LAST_AUTHOR), info.getAuthor());
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (checksum != null) {
            command.put(SVNLog.NAME_ATTR, target);
            command.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), checksum);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (removeLock) {
            command.put(SVNLog.NAME_ATTR, target);
            log.addCommand(SVNLog.DELETE_LOCK, command, false);
            command.clear();
        }
        command.put(SVNLog.NAME_ATTR, target);
        command.put(SVNLog.REVISION_ATTR, info == null ? null : Long.toString(info.getNewRevision()));
        if (!explicitCommitPaths.contains(path)) {
            command.put("implicit", "true");
        }
        log.addCommand(SVNLog.COMMIT, command, false);
        command.clear();
        if (wcPropChanges != null && !wcPropChanges.isEmpty()) {
            for (Iterator propNames = wcPropChanges.keySet().iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                String propValue = (String) wcPropChanges.get(propName);
                command.put(SVNLog.NAME_ATTR, target);
                command.put(SVNLog.PROPERTY_NAME_ATTR, propName);
                command.put(SVNLog.PROPERTY_VALUE_ATTR, propValue);
                log.addCommand(SVNLog.MODIFY_WC_PROPERTY, command, false);
                command.clear();
            }
        }
        log.save();
        runLogs();

        if (recursive) {
            SVNEntries entries = getEntries();
            for (Iterator ents = entries.entries(true); ents.hasNext();) {
                SVNEntry entry = (SVNEntry) ents.next();
                if ("".equals(entry.getName())) {
                    continue;
                }
                if (entry.getKind() == SVNNodeKind.DIR) {
                    SVNDirectory childDir = getChildDirectory(entry.getName());
                    if (childDir != null) {
                        childDir.commit("", info, null, removeLock, true, explicitCommitPaths);
                    }
                } else {
                    commit(entry.getName(), info, null, removeLock, false, explicitCommitPaths);
                }
            }
        }
    }
    
    public boolean isIgnored(String fileName) throws SVNException {
        String ignoredProperty = getProperties("", false).getPropertyValue(SVNProperty.IGNORE);
        if (ignoredProperty == null) {
            return false;
        }
        for (StringTokenizer tokens = new StringTokenizer(ignoredProperty, "\r\n"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (token.trim().length() == 0) {
                continue;
            }
            if (DefaultSVNOptions.matches(token, fileName)) {
                return true;
            }
        }
        return false;

    }
}
