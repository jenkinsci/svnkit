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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNMerger {

    private boolean myIsDryRun;
    private SVNWCAccess myWCAccess;
    private boolean myIsForce;
    private String myAddedPath;
    private String myURL;
    private long myTargetRevision;
    private boolean myIsLeaveConflicts;

    public SVNMerger(SVNWCAccess wcAccess, String url, long rev, boolean force, boolean dryRun, boolean leaveConflicts) {
        myWCAccess = wcAccess;
        myIsDryRun = dryRun;
        myIsLeaveConflicts = leaveConflicts;
        myIsForce = force;
        myTargetRevision = rev;
        myURL = url;
    }

    public boolean isDryRun() {
        return myIsDryRun;
    }

    public SVNStatusType directoryDeleted(final String path) {
        SVNDirectory parentDir = getParentDirectory(path);
        if (parentDir == null) {
            return SVNStatusType.MISSING;
        }
        String name = SVNPathUtil.tail(path);
        File targetFile = parentDir.getFile(name);
        if (targetFile.isDirectory()) {
            // check for normal entry?
            final ISVNEventHandler oldDispatcher = myWCAccess
                    .getEventDispatcher();
            myWCAccess.setEventDispatcher(new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) {
                    String eventPath = event.getPath();
                    eventPath = eventPath.replace(File.separatorChar, '/');
                    if (event.getPath().equals(path)) {
                        return;
                    }
                    if (oldDispatcher != null) {
                        oldDispatcher.handleEvent(event, progress);
                    }
                }

                public void checkCancelled() throws SVNCancelException {
                    if (oldDispatcher != null) {
                        oldDispatcher.checkCancelled();
                    }
                }
            });
            try {
                if (!myIsForce) {
                    try {
                        parentDir.canScheduleForDeletion(name);
                    } catch (SVNException e) {
                        SVNDebugLog.logInfo(e);
                        return SVNStatusType.OBSTRUCTED;
                    }
                }
                if (!myIsDryRun) {
                    try {
                        parentDir.scheduleForDeletion(name, true);
                    } catch (SVNException e) {
                        return SVNStatusType.OBSTRUCTED;
                    }
                }
            } finally {
                myWCAccess.setEventDispatcher(oldDispatcher);
            }
            return SVNStatusType.CHANGED;
        } else if (targetFile.isFile()) {
            return SVNStatusType.OBSTRUCTED;
        }
        return SVNStatusType.MISSING;
    }

    public SVNStatusType fileDeleted(String path) {
        SVNDirectory parentDir = getParentDirectory(path);
        if (parentDir == null) {
            return SVNStatusType.MISSING;
        }
        String name = SVNPathUtil.tail(path);
        File targetFile = parentDir.getFile(name);
        if (targetFile.isDirectory()) {
            return SVNStatusType.OBSTRUCTED;
        } else if (targetFile.isFile()) {
            ISVNEventHandler oldDispatcher = myWCAccess.getEventDispatcher();
            try {
                myWCAccess.setEventDispatcher(null);
                if (!myIsForce) {
                    try {
                        parentDir.canScheduleForDeletion(name);
                    } catch (SVNException e) {
                        return SVNStatusType.OBSTRUCTED;
                    }
                }
                if (!myIsDryRun) {
                    try {
                        parentDir.scheduleForDeletion(name, true);
                    } catch (SVNException e) {
                        return SVNStatusType.OBSTRUCTED;
                    }
                }
            } finally {
                myWCAccess.setEventDispatcher(oldDispatcher);
            }
            return SVNStatusType.CHANGED;
        }
        return SVNStatusType.MISSING;
    }

    public SVNStatusType directoryAdded(String path, Map entryProps) throws SVNException {
        SVNDirectory parentDir = getParentDirectory(path);
        if (parentDir == null) {
            if (myIsDryRun && myAddedPath != null
                    && path.startsWith(myAddedPath)) {
                return SVNStatusType.CHANGED;
            }
            return SVNStatusType.MISSING;
        }
        String name = SVNPathUtil.tail(path);
        File file = parentDir.getFile(name);
        if (!file.exists()) {
            SVNEntry entry = parentDir.getEntries().getEntry(name, true);
            if (entry != null && !entry.isScheduledForDeletion()) {
                // missing entry.
                return SVNStatusType.OBSTRUCTED;
            }
            if (!myIsDryRun) {
                file.mkdirs();
                String url = SVNPathUtil.append(myURL, SVNEncodingUtil.uriEncode(getPathInURL(path)));
                addDirectory(parentDir, name, url, myTargetRevision, entryProps);
            } else {
                myAddedPath = path + "/";
            }
            return SVNStatusType.CHANGED;
        } else if (file.isDirectory()) {
            SVNEntry entry = parentDir.getEntries().getEntry(name, true);
            if (entry == null || entry.isScheduledForDeletion()) {
                if (myIsDryRun) {
                    myAddedPath = path + "/";
                } else {
                    String url = SVNPathUtil.append(myURL, SVNEncodingUtil.uriEncode(getPathInURL(path)));
                    addDirectory(parentDir, name, url, myTargetRevision,
                            entryProps);
                }
                return SVNStatusType.CHANGED;
            }
            return SVNStatusType.OBSTRUCTED;            
        } else if (file.isFile()) {
            if (myIsDryRun) {
                myAddedPath = null;
            }
            return SVNStatusType.OBSTRUCTED;
        }
        return SVNStatusType.MISSING;
    }

    public SVNStatusType[] fileChanged(String path, File older, File yours,
            long rev1, long rev2, String mimeType1, String mimeType2,
            Map propDiff) throws SVNException {
        SVNStatusType[] result = new SVNStatusType[] { SVNStatusType.UNKNOWN,
                SVNStatusType.UNKNOWN };
        String parentPath = SVNPathUtil.removeTail(path);
        SVNDirectory parentDir = myWCAccess.getDirectory(parentPath);
        if (parentDir == null) {
            result[0] = SVNStatusType.MISSING;
            result[1] = SVNStatusType.MISSING;
            return result;
        }

        String name = SVNPathUtil.tail(path);
        File mine = parentDir.getFile(name);
        SVNEntry entry = parentDir.getEntries().getEntry(name, true);

        if (!mine.isFile() || entry == null || entry.isHidden()) {
            result[0] = SVNStatusType.MISSING;
            result[1] = SVNStatusType.MISSING;
            return result;
        }
        if (propDiff != null && !propDiff.isEmpty()) {
            result[1] = propertiesChanged(parentPath, name, propDiff);
        } else {
            result[1] = SVNStatusType.UNCHANGED;
        }
        if (older != null) {
            boolean isTextModified = parentDir
                    .hasTextModifications(name, false);
            SVNStatusType mergeResult = null;
            if (!isTextModified) {
                if (SVNProperty.isBinaryMimeType(mimeType1) || SVNProperty.isBinaryMimeType(mimeType2)) {
                    boolean equals = SVNFileUtil.compareFiles(mine, older, null);
                    if (equals) {
                        if (!myIsDryRun) {
                            SVNFileUtil.rename(yours, mine);
                        }
                        mergeResult = SVNStatusType.MERGED;
                    }
                }
            }
            if (mergeResult == null) {
                String minePath = name;
                String olderPath = SVNFileUtil.getBasePath(older);
                String yoursPath = SVNFileUtil.getBasePath(yours);
                String targetLabel = ".working";
                String leftLabel = ".merge-left.r" + rev1;
                String rightLabel = ".merge-right.r" + rev2;
                mergeResult = parentDir.mergeText(minePath, olderPath,
                        yoursPath, targetLabel, leftLabel, rightLabel,
                        myIsLeaveConflicts, myIsDryRun);
                parentDir.getEntries().save(true);
            }

            if (mergeResult == SVNStatusType.CONFLICTED || mergeResult == SVNStatusType.CONFLICTED_UNRESOLVED) {
                result[0] = mergeResult;
            } else if (isTextModified) {
                result[0] = SVNStatusType.MERGED;
            } else if (mergeResult == SVNStatusType.MERGED) {
                result[0] = SVNStatusType.CHANGED;
            } else {
                result[0] = SVNStatusType.UNCHANGED;
            }
        }
        return result;
    }

    public SVNStatusType[] fileAdded(String path, File older, File yours,
            long rev1, long rev2, String mimeType1, String mimeType2,
            Map propDiff, Map entryProps) throws SVNException {
        SVNStatusType[] result = new SVNStatusType[] { SVNStatusType.UNKNOWN,
                SVNStatusType.UNKNOWN };
        SVNDirectory parentDir = getParentDirectory(path);
        if (parentDir == null) {
            if (myIsDryRun && myAddedPath != null
                    && path.startsWith(myAddedPath)) {
                result[0] = SVNStatusType.CHANGED;
                result[1] = propDiff != null && !propDiff.isEmpty() ? SVNStatusType.CHANGED
                        : result[1];
            } else {
                result[0] = SVNStatusType.MISSING;
            }
            return result;
        }
        String name = SVNPathUtil.tail(path);
        File mine = parentDir.getFile(name);

        if (!mine.exists()) {
            SVNEntry entry = parentDir.getEntries().getEntry(name, true);
            if (entry != null && !entry.isScheduledForDeletion()) {
                result[0] = SVNStatusType.OBSTRUCTED;
                return result;
            } else if (!myIsDryRun) {
                String pathInURL = getPathInURL(path);
                String copyFromURL = SVNPathUtil.append(myURL, SVNEncodingUtil.uriEncode(pathInURL));
                addFile(parentDir, name, SVNFileUtil.getBasePath(yours),
                        propDiff, copyFromURL, myTargetRevision, entryProps);
            }
            result[0] = SVNStatusType.CHANGED;
            if (propDiff != null && !propDiff.isEmpty()) {
                result[1] = SVNStatusType.CHANGED;
            }
        } else if (mine.isDirectory()) {
            result[0] = SVNStatusType.OBSTRUCTED;
        } else if (mine.isFile()) {
            SVNEntry entry = parentDir.getEntries().getEntry(name, true);
            if (entry == null || entry.isScheduledForDeletion()) {
                result[0] = SVNStatusType.OBSTRUCTED;
            } else {
                return fileChanged(path, older, yours, rev1, rev2, mimeType1,
                        mimeType2, propDiff);
            }
        }
        return result;
    }

    private String getPathInURL(String path) {
        String pathInURL = path;
        if (!"".equals(myWCAccess.getTargetName())) {
            if (pathInURL.indexOf('/') > 0) {
                pathInURL = pathInURL.substring(pathInURL.indexOf('/') + 1);
            } else {
                pathInURL = "";
            }
        }
        return pathInURL;
    }

    public SVNStatusType directoryPropertiesChanged(String path, Map propDiff) throws SVNException {
        return propertiesChanged(path, "", propDiff);
    }

    public File getFile(String path, boolean base) {
        SVNDirectory dir = null;
        String parentPath = path;
        while (dir == null && !"".equals(parentPath)) {
            dir = getParentDirectory(parentPath);
            parentPath = SVNPathUtil.removeTail(parentPath);
        }
        String name = SVNPathUtil.tail(path);
        if (dir != null) {
            String extension = base ? ".tmp-base" : ".tmp-work";
            return SVNFileUtil.createUniqueFile(dir.getAdminFile("tmp/text-base"), name , extension);
        }
        return null;
    }

    private SVNStatusType propertiesChanged(String path, String name, Map propDiff) throws SVNException {
        if (propDiff == null || propDiff.isEmpty()) {
            return SVNStatusType.UNCHANGED;
        }
        SVNDirectory dir = myWCAccess.getDirectory(path);
        if (dir == null) {
            return SVNStatusType.MISSING;
        }
        SVNStatusType result = null;
        SVNLog log = null;
        if (!myIsDryRun) {
            log = dir.getLog(0);
        }
        // 1. convert props to diff (need we?), just use remote diff
        // ->
        // 2. get local mods.
        SVNProperties localBaseProps = dir.getBaseProperties(name, false);
        SVNProperties localWCProps = dir.getProperties(name, false);

        // will contain all deleted and added, but not unchanged.
        Map localDiff = localBaseProps.compareTo(localWCProps);
        // 3. merge
        result = dir.mergeProperties(name, propDiff, localDiff, false, log);
        if (log != null) {
            log.save();
            dir.runLogs();
        }
        // to make python tests pass.
        if (result == SVNStatusType.MERGED
                || result == SVNStatusType.CONFLICTED) {
            result = SVNStatusType.CHANGED;
        }
        return result;
    }

    private void addDirectory(SVNDirectory parentDir, String name,
            String copyFromURL, long copyFromRev, Map entryProps)
            throws SVNException {
        // 1. update or create entry in parent
        SVNEntries entries = parentDir.getEntries();
        SVNEntry entry = entries.getEntry(name, true);
        String url = null;
        String uuid = entries.getEntry("", true).getUUID();
        if (entry != null) {
            entry.loadProperties(entryProps);
            if (entry.isScheduledForDeletion()) {
                entry.scheduleForReplacement();
            }
            url = entry.getURL();
        } else {
            entry = parentDir.getEntries().addEntry(name);
            entry.loadProperties(entryProps);
            entry.setKind(SVNNodeKind.DIR);
            entry.scheduleForAddition();
            url = SVNPathUtil.append(entries.getEntry("", true).getURL(), SVNEncodingUtil.uriEncode(name));
        }
        entry.setCopied(true);
        entry.setCopyFromURL(copyFromURL);
        entry.setCopyFromRevision(copyFromRev);
        // 2. create dir if doesn't exists and update its root entry.
        entries.save(false);
        SVNDirectory childDir = parentDir.getChildDirectory(name);
        if (childDir == null) {
            childDir = parentDir.createChildDirectory(name, url, copyFromRev);
            SVNEntry root = childDir.getEntries().getEntry("", true);
            root.scheduleForAddition();
            root.setUUID(uuid);
        } else {
            childDir.getWCProperties("").delete();
            SVNEntry root = childDir.getEntries().getEntry("", true);
            if (root.isScheduledForDeletion()) {
                root.scheduleForReplacement();
            }
        }
        entries = childDir.getEntries();
        SVNEntry rootEntry = entries.getEntry("", true);
        rootEntry.setCopyFromURL(copyFromURL);
        rootEntry.setCopyFromRevision(copyFromRev);
        rootEntry.setCopied(true);
        entries.save(false);
    }

    private void addFile(SVNDirectory parentDir, String name, String filePath,
            Map baseProps, String copyFromURL, long copyFromRev, Map entryProps)
            throws SVNException {
        SVNEntries entries = parentDir.getEntries();
        SVNEntry entry = entries.getEntry(name, true);
        if (entry != null) {
            if (entry.isScheduledForDeletion()) {
                entry.scheduleForReplacement();
            }
            // put all entry props.
            entry.loadProperties(entryProps);
        } else {
            entry = parentDir.getEntries().addEntry(name);
            entry.loadProperties(entryProps);
            entry.setKind(SVNNodeKind.FILE);
            entry.scheduleForAddition();
        }
        entry.setCopied(true);
        entry.setCopyFromURL(copyFromURL);
        entry.setCopyFromRevision(copyFromRev);
        String url = SVNPathUtil.append(entries.getEntry("", true).getURL(),
                SVNEncodingUtil.uriEncode(name));
        entries.save(false);
        parentDir.getWCProperties(name).delete();

        SVNLog log = parentDir.getLog(0);
        Map command = new HashMap();

        // 1. props.
        SVNProperties wcPropsFile = parentDir.getProperties(name, false);
        SVNProperties basePropsFile = parentDir.getBaseProperties(name, false);
        for (Iterator propNames = baseProps.keySet().iterator(); propNames
                .hasNext();) {
            String propName = (String) propNames.next();
            wcPropsFile.setPropertyValue(propName, (String) baseProps
                    .get(propName));
            basePropsFile.setPropertyValue(propName, (String) baseProps
                    .get(propName));
        }
        if (baseProps.isEmpty()) {
            // force prop file creation.
            wcPropsFile.setPropertyValue("x", "x");
            basePropsFile.setPropertyValue("x", "x");
            wcPropsFile.setPropertyValue("x", null);
            basePropsFile.setPropertyValue("x", null);
        }
        command.put(SVNLog.NAME_ATTR, wcPropsFile.getPath());
        log.addCommand(SVNLog.READONLY, command, false);
        command.put(SVNLog.NAME_ATTR, basePropsFile.getPath());
        log.addCommand(SVNLog.READONLY, command, false);

        // 2. entry
        command.put(SVNLog.NAME_ATTR, name);
        command.put(SVNProperty.shortPropertyName(SVNProperty.KIND),
                SVNProperty.KIND_FILE);
        command.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), Long
                .toString(copyFromRev));
        command.put(SVNProperty.shortPropertyName(SVNProperty.DELETED),
                Boolean.FALSE.toString());
        command.put(SVNProperty.shortPropertyName(SVNProperty.ABSENT),
                Boolean.FALSE.toString());
        command.put(SVNProperty.shortPropertyName(SVNProperty.URL), url);
        command.put(SVNProperty.shortPropertyName(SVNProperty.COPIED),
                Boolean.TRUE.toString());
        command.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_URL),
                copyFromURL);
        command.put(SVNProperty
                .shortPropertyName(SVNProperty.COPYFROM_REVISION), Long
                .toString(copyFromRev));
        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        command.clear();

        // 3. text files.
        String basePath = SVNFileUtil.getBasePath(parentDir.getBaseFile(name, false));
        command.put(SVNLog.NAME_ATTR, filePath);
        command.put(SVNLog.DEST_ATTR, basePath);
        log.addCommand(SVNLog.MOVE, command, false);
        command.clear();
        command.put(SVNLog.NAME_ATTR, basePath);
        log.addCommand(SVNLog.READONLY, command, false);
        command.clear();
        command.put(SVNLog.NAME_ATTR, basePath);
        command.put(SVNLog.DEST_ATTR, name);
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();
        if (myWCAccess.getOptions().isUseCommitTimes()
                && wcPropsFile.getPropertyValue(SVNProperty.SPECIAL) == null) {
            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNLog.TIMESTAMP_ATTR, entry.getCommittedDate());
            log.addCommand(SVNLog.SET_TIMESTAMP, command, false);
            command.clear();
        }

        command.put(SVNLog.NAME_ATTR, name);
        command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_TIME),
                SVNLog.WC_TIMESTAMP);
        command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME),
                SVNLog.WC_TIMESTAMP);
        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);

        log.save();
        parentDir.runLogs();
    }

    private SVNDirectory getParentDirectory(String path) {
        path = SVNPathUtil.removeTail(path);
        return myWCAccess.getDirectory(path);
    }
}