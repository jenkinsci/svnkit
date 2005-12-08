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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNStatusEditor implements ISVNEditor {

    private ISVNOptions myOptions;
    private SVNWCAccess myWCAccess;
    private String myTarget;
    private ISVNStatusHandler myHandler;
    private boolean myIsReportAll;
    private boolean myIsIncludeIgnored;
    private boolean myIsRecursive;
    private long myTargetRevision;
    private boolean myIsRootOpened;

    private Map myExternalsMap;
    private SVNStatusReporter myStatusReporter;
    private DirectoryInfo myCurrentDirectory;
    private FileInfo myCurrentFile;
    private SVNStatus myAnchorStatus;

    public SVNStatusEditor(ISVNOptions globalOptions, SVNWCAccess wcAccess,
            ISVNStatusHandler handler, Map externals, boolean includeIgnored,
            boolean reportAll, boolean recursive) {
        myWCAccess = wcAccess;
        myHandler = handler;
        myOptions = globalOptions;
        myIsIncludeIgnored = includeIgnored;
        myIsReportAll = reportAll;
        myIsRecursive = recursive;
        myExternalsMap = externals;
        myTarget = "".equals(myWCAccess.getTargetName()) ? null : myWCAccess
                .getTargetName();
        myTargetRevision = -1;
    }

    public void setStatusReporter(SVNStatusReporter reporter)
            throws SVNException {
        myStatusReporter = reporter;
        if (myStatusReporter != null) {
            SVNEntry anchorEntry = myWCAccess.getAnchor().getEntries()
                    .getEntry("", false);
            boolean oldReportAll = myIsReportAll;
            myIsReportAll = true;
            myAnchorStatus = createStatus(anchorEntry.getSVNURL(), myWCAccess
                    .getAnchor().getRoot(), myWCAccess.getAnchor(), null,
                    anchorEntry, false, SVNFileType.DIRECTORY, anchorEntry
                            .asMap());
            myIsReportAll = oldReportAll;
        }
    }

    public Map getCollectedExternals() {
        return myExternalsMap;
    }

    public long getTargetRevision() {
        return myTargetRevision;
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpened = true;
        myCurrentDirectory = new DirectoryInfo(null, "", false);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String name = SVNPathUtil.tail(path);
        String originalName = name;

        File ioFile = new File(myWCAccess.getAnchor().getRoot(), path);
        SVNFileType type = SVNFileType.getType(ioFile);
        String dirPath = path;
        SVNNodeKind kind;
        if (type != SVNFileType.DIRECTORY) {
            dirPath = SVNPathUtil.removeTail(path);
            kind = SVNNodeKind.FILE;
        } else {
            name = "";
            kind = SVNNodeKind.DIR;
        }
        SVNDirectory dir = myWCAccess.getDirectory(dirPath);
        if (dir == null) {
            return;
        }
        if (dir.getEntries().getEntry(name, false) != null) {
            myCurrentDirectory.tweakStatus(path, kind, originalName,
                    SVNStatusType.STATUS_DELETED, SVNStatusType.STATUS_NONE,
                    null, SVNRevision.UNDEFINED, null, null);
        }
        if (myTarget == null && myCurrentDirectory.Parent != null) {            
            myCurrentDirectory.Parent.tweakStatus(myCurrentDirectory.Path, SVNNodeKind.DIR,
                    myCurrentDirectory.Name, SVNStatusType.STATUS_MODIFIED,
                    SVNStatusType.STATUS_NONE, null, SVNRevision.UNDEFINED, null, null);
        }

    }

    public void addDir(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        myCurrentDirectory = new DirectoryInfo(myCurrentDirectory, path, true);
        myCurrentDirectory.Parent.IsContentsChanged = true;
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = new DirectoryInfo(myCurrentDirectory, path, false);
    }

    public void changeDirProperty(String name, String value)
            throws SVNException {
        if (name != null && !name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                && !name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            myCurrentDirectory.IsPropertiesChanged = true;
        }
        if (SVNProperty.COMMITTED_REVISION.equals(name) && value != null) {
            myCurrentDirectory.RemoteRevision = SVNRevision.parse(value);
        } else if (SVNProperty.COMMITTED_DATE.equals(name) && value != null) {
            myCurrentDirectory.RemoteDate = SVNTimeUtil.parseDate(value);
        } else if (SVNProperty.LAST_AUTHOR.equals(name)) {
            myCurrentDirectory.RemoteAuthor = value;
        }
    }

    public void closeDir() throws SVNException {
        if (myCurrentDirectory.IsAdded || myCurrentDirectory.IsContentsChanged || myCurrentDirectory.IsPropertiesChanged) {
            SVNStatusType reposContentsStatus;
            SVNStatusType reposPropStatus;
            if (myCurrentDirectory.IsAdded) {
                reposContentsStatus = SVNStatusType.STATUS_ADDED;
                reposPropStatus = myCurrentDirectory.IsPropertiesChanged ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
            } else {
                reposContentsStatus = myCurrentDirectory.IsContentsChanged ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
                reposPropStatus = myCurrentDirectory.IsPropertiesChanged ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NONE;
            }
            if (myCurrentDirectory.Parent != null) {
                myCurrentDirectory.Parent.tweakStatus(myCurrentDirectory.Path,
                        SVNNodeKind.DIR, myCurrentDirectory.Name,
                        reposContentsStatus, reposPropStatus, null, myCurrentDirectory.RemoteRevision, myCurrentDirectory.RemoteDate, myCurrentDirectory.RemoteAuthor);
            } else if (myAnchorStatus != null && myTarget == null) {
                // we are in the anchor.
                myAnchorStatus.setRemoteStatus(myCurrentDirectory.getURL(), reposContentsStatus, reposPropStatus, null, SVNNodeKind.DIR, myCurrentDirectory.RemoteRevision,
                        myCurrentDirectory.RemoteDate, myCurrentDirectory.RemoteAuthor);
            }
        }
        if (myCurrentDirectory.Parent != null && myIsRecursive) {
            boolean deleted = false;
            SVNStatus dirStatus = (SVNStatus) myCurrentDirectory.Parent.ChildrenStatuses.get(myCurrentDirectory.Name);
            if (dirStatus != null && 
                    (dirStatus.getRemoteContentsStatus() == SVNStatusType.STATUS_DELETED || dirStatus.getRemoteContentsStatus() == SVNStatusType.STATUS_REPLACED)) {
                deleted = true;
            }
            handleDirStatuses(myCurrentDirectory, deleted);
            if (dirStatus != null) {
                if (isSendableStatus(dirStatus)) {
                    myHandler.handleStatus(dirStatus);
                }
            }
            myCurrentDirectory.Parent.ChildrenStatuses.remove(myCurrentDirectory.Name);
            myCurrentDirectory = myCurrentDirectory.Parent;
        } else if (myCurrentDirectory.Parent == null) {
            if (myTarget != null) {
                SVNStatus targetStatus = (SVNStatus) myCurrentDirectory.ChildrenStatuses.get(myTarget);
                if (targetStatus != null) {
                    if (targetStatus.getURL() != null && targetStatus.getKind() == SVNNodeKind.DIR) {
                        reportStatus(myWCAccess.getTarget(), null, true, myIsRecursive);
                    }
                    if (isSendableStatus(targetStatus)) {
                        myHandler.handleStatus(targetStatus);
                    }
                }
            } else {
                handleDirStatuses(myCurrentDirectory, false);
                if (myAnchorStatus != null && isSendableStatus(myAnchorStatus)) {
                    myHandler.handleStatus(myAnchorStatus);
                }
                myAnchorStatus = null;

            }
            myCurrentDirectory = null;
        } else {
            myCurrentDirectory = myCurrentDirectory.Parent;
        }
    }

    private void handleDirStatuses(DirectoryInfo dirInfo, boolean dirIsDeleted)
            throws SVNException {
        final ISVNStatusHandler oldHalder = myHandler;
        if (dirIsDeleted) {
            myHandler = new ISVNStatusHandler() {
                public void handleStatus(SVNStatus status) {
                    if (oldHalder != null) {
                        if (status.getRemoteContentsStatus() != SVNStatusType.STATUS_ADDED) {                            
                            status.setRemoteStatus(SVNStatusType.STATUS_DELETED);
                        } 
                        oldHalder.handleStatus(status);
                    }
                }
            };
        }
        SVNDirectory dir = myWCAccess.getDirectory(dirInfo.Path);
        File dirFile = new File(myWCAccess.getAnchor().getRoot(), dirInfo.Path);
        for (Iterator names = dirInfo.ChildrenStatuses.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            SVNStatus status = (SVNStatus) dirInfo.ChildrenStatuses.get(name);
            File childFile = new File(dirFile, name);
            SVNFileType currentFileType = SVNFileType.getType(childFile);
            if (currentFileType == SVNFileType.NONE
                    && (dir != null && dir.getEntries().getEntry(name, false) != null)) {
                SVNEntry currentEntry = dir.getEntries().getEntry(name, false);
                if (currentEntry != null
                        && !currentEntry.isScheduledForDeletion()) {
                    status.setRemoteStatus(SVNStatusType.STATUS_MISSING);
                }
            } else if (myIsRecursive && status.getURL() != null && status.getKind() == SVNNodeKind.DIR) {
                String path = "".equals(dirInfo.Path) ? name : SVNPathUtil.append(dirInfo.Path, name);
                SVNDirectory childDir = myWCAccess.getDirectory(path);
                if (childDir != null) {
                    reportStatus(childDir, null, true, myIsRecursive);
                }
            }
            if (isSendableStatus(status)) {
                myHandler.handleStatus(status);
            }
        }
        dirInfo.ChildrenStatuses.clear();
        myHandler = oldHalder;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        myCurrentFile = new FileInfo(myCurrentDirectory, path, true);
        myCurrentFile.Parent.IsContentsChanged = true;
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFile = new FileInfo(myCurrentDirectory, path, false);
    }

    public void applyTextDelta(String commitPath, String baseChecksum)
            throws SVNException {
        myCurrentFile.IsContentsChanged = true;
    }

    public void changeFileProperty(String commitPath, String name, String value) throws SVNException {
        if (name != null && !name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                && !name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            myCurrentFile.IsPropertiesChanged = true;
        }
        if (SVNProperty.COMMITTED_REVISION.equals(name) && value != null) {
            myCurrentFile.RemoteRevision = SVNRevision.parse(value);
        } else if (SVNProperty.COMMITTED_DATE.equals(name) && value != null) {
            myCurrentFile.RemoteDate = SVNTimeUtil.parseDate(value);
        } else if (SVNProperty.LAST_AUTHOR.equals(name)) {
            myCurrentFile.RemoteAuthor = value;
        }
    }

    public void closeFile(String commitPath, String textChecksum)
            throws SVNException {
        if (!(myCurrentFile.IsAdded || myCurrentFile.IsContentsChanged || myCurrentFile.IsPropertiesChanged)) {
            return;
        }
        SVNStatusType reposContentStatus;
        SVNStatusType reposPropStatus;
        SVNLock lock = null;
        if (myCurrentFile.IsAdded) {
            reposContentStatus = SVNStatusType.STATUS_ADDED;
            reposPropStatus = myCurrentFile.IsPropertiesChanged ? SVNStatusType.STATUS_MODIFIED
                    : SVNStatusType.STATUS_NONE;
            SVNURL dirURL = myCurrentDirectory.getURL();
            if (dirURL != null) {
                dirURL = dirURL.appendPath(myCurrentFile.Name, false);
                lock = getRepositoryLock(dirURL);
            }
        } else {
            reposContentStatus = myCurrentFile.IsContentsChanged ? SVNStatusType.STATUS_MODIFIED
                    : SVNStatusType.STATUS_NONE;
            reposPropStatus = myCurrentFile.IsPropertiesChanged ? SVNStatusType.STATUS_MODIFIED
                    : SVNStatusType.STATUS_NONE;
        }
        myCurrentDirectory.tweakStatus(myCurrentFile.Path, SVNNodeKind.FILE,
                myCurrentFile.Name, reposContentStatus, reposPropStatus, lock, myCurrentFile.RemoteRevision,
                myCurrentFile.RemoteDate, myCurrentFile.RemoteAuthor);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (myIsRootOpened) {
            return new SVNCommitInfo(myTargetRevision, null, null);
        }
        if (myTarget != null) {
            File file = myWCAccess.getAnchor().getFile(myTarget);
            // this could be a file from entries point of view.
            SVNEntries entries = myWCAccess.getAnchor().getEntries();
            SVNEntry entry = entries.getEntry(myTarget, false);
            SVNNodeKind kind = entry == null ? null : entry.getKind();
            entries.close();
            if (file.isDirectory() && (kind == null || kind == SVNNodeKind.DIR)) {
                if (entry != null) {
                    reportStatus(myWCAccess.getTarget(), null, false, myIsRecursive);
                } else {
                    myIsIncludeIgnored = true;
                    reportStatus(myWCAccess.getAnchor(), myTarget, false, myIsRecursive);
                }
            } else {
                myIsIncludeIgnored = true;
                reportStatus(myWCAccess.getAnchor(), myTarget, false, myIsRecursive);
            }
        } else {
            reportStatus(myWCAccess.getAnchor(), null, false, myIsRecursive);
        }
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public OutputStream textDeltaChunk(String commitPath,
            SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
    }

    public void reportStatus(SVNDirectory dir, String entryName, boolean ignoreRootEntry, boolean recursive) throws SVNException {
        myWCAccess.checkCancelled();

        SVNEntries entries = dir.getEntries();
        boolean anchorOfTarget = myTarget != null && dir == myWCAccess.getAnchor();
        if (!anchorOfTarget) {            
            SVNExternalInfo[] externals = SVNWCAccess.parseExternals(dir.getPath(), dir.getProperties("", false).getPropertyValue(SVNProperty.EXTERNALS));
            for (int i = 0; i < externals.length; i++) {
                SVNExternalInfo external = externals[i];
                myExternalsMap.put(external.getPath(), external);
            }
        }
        if (entryName != null) {
            SVNEntry entry = entries.getEntry(entryName, false);
            if (entry != null) {
                sendVersionedStatus(dir, entryName);
            } else if (dir.getFile(entryName).exists()) {
                sendUnversionedStatus(dir, entryName);
            }
            return;
        }
        File[] ioFiles = dir.getRoot().listFiles();
        
        if (ioFiles != null) {
            Arrays.sort(ioFiles);
        }
        for (int i = 0; ioFiles != null && i < ioFiles.length; i++) {
            File ioFile = ioFiles[i];
            String fileName = ioFile.getName();
            String adminDir = SVNFileUtil.getAdminDirectoryName();
            if (adminDir.equals(fileName) || entries.getEntry(fileName, false) != null) {
                continue;
            }
            sendUnversionedStatus(dir, fileName);
        }
        if (!ignoreRootEntry) {
            sendVersionedStatus(dir, "");
        }
        for (Iterator ents = entries.entries(false); ents.hasNext();) {
            SVNEntry childEntry = (SVNEntry) ents.next();
            if ("".equals(childEntry.getName())) {
                continue;
            }
            File file = dir.getFile(childEntry.getName());
            SVNFileType fType = SVNFileType.getType(file);
            if (fType == SVNFileType.DIRECTORY) {
                SVNDirectory childDir = dir.getChildDirectory(childEntry.getName());
                if (childDir != null && recursive) {
                    reportStatus(childDir, null, false, recursive);
                } else {
                    sendVersionedStatus(dir, childEntry.getName());
                }
            } else {
                sendVersionedStatus(dir, childEntry.getName());
            }
        }
    }

    private void sendVersionedStatus(SVNDirectory dir, String name) throws SVNException {
        File file;
        SVNEntry parentEntry;
        SVNDirectory parentDir = null;
        SVNEntry entry = dir.getEntries().getEntry(name, false);

        if (entry.isDirectory()) {
            if (!"".equals(name)) {
                parentDir = dir;
                dir = dir.getChildDirectory(name);
                if (dir == null) {
                    File dirFile = parentDir.getFile(name);
                    if (SVNFileType.getType(dirFile) == SVNFileType.DIRECTORY) {
                         dir = new SVNDirectory(myWCAccess, "".equals(parentDir.getPath()) ? name : SVNPathUtil.append(parentDir.getPath(), name), parentDir.getFile(name));
                    }

                }
                SVNEntry fullEntry = dir != null ? dir.getEntries().getEntry("", false) : null;
                if (fullEntry != null) {
                    entry = fullEntry;
                }
                if (dir == null) {
                    dir = parentDir;
                }
            } else {
                // we are in the dir itself already, try to get parent dir.
                if (!"".equals(dir.getPath())) {
                    // there is parent dir
                    String parentPath = SVNPathUtil.removeTail(dir.getPath());
                    parentDir = myWCAccess.getDirectory(parentPath);
                } else {
                    // it is a root of wc.
                    parentDir = null;
                }
            }
        } else {
            parentDir = dir;
        }
        SVNEntry entryInParent = entry;
        if (dir == parentDir) {
            file = dir.getFile(name);
            entry = dir.getEntries().getEntry(name, false);
            parentEntry = dir.getEntries().getEntry("", false);
        } else {
            file = dir.getRoot();
            entry = dir.getEntries().getEntry("", false);
            if (entry == null) {
                // probably missing dir.
                entry = entryInParent;
                dir = parentDir;
            }
            parentEntry = parentDir != null ? parentDir.getEntries().getEntry("", true) : null;
        }
        SVNFileType fileType = SVNFileType.getType(file);
        SVNStatus status = createStatus(entry.getSVNURL(), file, dir, parentEntry,
                entry, false, fileType, Collections.unmodifiableMap(entry.asMap()));

        if (status != null) {
            myHandler.handleStatus(status);
        }
    }

    private void sendUnversionedStatus(SVNDirectory parent, String name)
            throws SVNException {
        boolean ignored = isIgnored(parent, name);
        String path = "".equals(name) ? parent.getPath() : SVNPathUtil.append(
                parent.getPath(), name);
        SVNURL url = null;
        if (parent.getEntries() != null
                && parent.getEntries().getEntry("", false) != null) {
            url = parent.getEntries().getEntry("", false).getSVNURL();
            if (url != null) {
                url = url.appendPath(name, false);
            }
        }
        SVNStatus status = createStatus(url, parent.getFile(name), parent, null, null, ignored, null, null);
        if (myExternalsMap.containsKey(path)) {
            status.markExternal();
        }
        if (status != null) {
            if (myIsIncludeIgnored || !ignored
                    || myExternalsMap.containsKey(path)
                    || status.getRemoteLock() != null) {
                myHandler.handleStatus(status);
            }
        }
    }

    private SVNStatus createStatus(SVNURL url, File file,
            SVNDirectory entryDir, SVNEntry parentEntry,
            SVNEntry entry /* this could be dir entry in parent */,
            boolean isIgnored, SVNFileType pathKind, Map allEntryProperties)
            throws SVNException {
        pathKind = pathKind == null || pathKind == SVNFileType.UNKNOWN ? SVNFileType
                .getType(file)
                : pathKind;

        SVNLock remoteLock = null;
        if (url != null && myAnchorStatus != null && myAnchorStatus.getURL() != null) {
            remoteLock = getRepositoryLock(url);
        }

        if (entry == null) {
            SVNStatusType textStatus = SVNStatusType.STATUS_NONE;
            if (pathKind != SVNFileType.NONE) {
                textStatus = isIgnored ? SVNStatusType.STATUS_IGNORED
                        : SVNStatusType.STATUS_UNVERSIONED;
            }
            return new SVNStatus(null, file, null, null, null, null, null,
                    textStatus, SVNStatusType.STATUS_NONE,
                    SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE,
                    false, false, false, null, null, null, null, null, null,
                    remoteLock, null, null);
        }
        SVNStatusType textStatus = SVNStatusType.STATUS_NORMAL;
        SVNStatusType propStatus = SVNStatusType.STATUS_NONE;

        boolean isSwitched = false;
        boolean isLocked = false;
        boolean isDir = entry.isDirectory();

        if (isDir) {
            if (pathKind == SVNFileType.DIRECTORY) {
                if (!SVNWCAccess.isVersionedDirectory(file)) {
                    textStatus = SVNStatusType.STATUS_OBSTRUCTED;
                }
            } else if (pathKind != SVNFileType.NONE) {
                textStatus = SVNStatusType.STATUS_OBSTRUCTED;
            }
        }

        if (parentEntry != null && entry.getURL() != null
                && parentEntry.getURL() != null) {
            String realName = entry.getName();
            if ("".equals(entry.getName())) {
                realName = file.getName();
            }
            if (!realName.equals(SVNEncodingUtil.uriDecode(SVNPathUtil.tail(entry.getURL())))) {
                isSwitched = true;
            }
            if (!isSwitched && !SVNPathUtil.removeTail(entry.getURL()).equals(parentEntry.getURL())) {
                isSwitched = true;
            }
        }
        if (textStatus != SVNStatusType.OBSTRUCTED) {
            SVNProperties props = entryDir.getProperties(entry.getName(), false);
            if (props != null && !props.isEmpty()) {
                propStatus = SVNStatusType.STATUS_NORMAL;
            }
            boolean propsModified = entryDir.hasPropModifications(entry.getName());
            boolean special = !SVNFileUtil.isWindows && !isDir && props.getPropertyValue(SVNProperty.SPECIAL) != null;
            boolean textModified = false;
            if (!isDir && special == (pathKind == SVNFileType.SYMLINK)) {
                textModified = entryDir.hasTextModifications(entry.getName(), false);
            }
            if (propsModified) {
                propStatus = SVNStatusType.STATUS_MODIFIED;
            }
            if (textModified) {
                textStatus = SVNStatusType.STATUS_MODIFIED;
            }
            if (entry.getConflictNew() != null
                    || entry.getConflictOld() != null
                    || entry.getConflictWorking() != null) {
                textStatus = SVNStatusType.STATUS_CONFLICTED;
            }
            if (entry.getPropRejectFile() != null) {
                propStatus = SVNStatusType.STATUS_CONFLICTED;
            }
            if (entry.isScheduledForAddition()) {
                textStatus = SVNStatusType.STATUS_ADDED;
                propStatus = SVNStatusType.STATUS_NONE;
            } else if (entry.isScheduledForDeletion()) {
                textStatus = SVNStatusType.STATUS_DELETED;
                propStatus = SVNStatusType.STATUS_NONE;
            } else if (entry.isScheduledForReplacement()) {
                textStatus = SVNStatusType.STATUS_REPLACED;
                propStatus = SVNStatusType.STATUS_NONE;
            }
            if (entry.isIncomplete()
                    && textStatus != SVNStatusType.STATUS_ADDED
                    && textStatus != SVNStatusType.STATUS_DELETED) {
                textStatus = SVNStatusType.STATUS_INCOMPLETE;
            } else if (pathKind == SVNFileType.NONE) {
                if (textStatus != SVNStatusType.STATUS_DELETED) {
                    textStatus = SVNStatusType.STATUS_MISSING;
                }
            } else if (!SVNFileType.equals(pathKind, entry.getKind())) {
                textStatus = SVNStatusType.STATUS_OBSTRUCTED;
            } else if (special != (pathKind == SVNFileType.SYMLINK)) {
                textStatus = SVNStatusType.STATUS_OBSTRUCTED;
            }
            if (isDir && pathKind == SVNFileType.DIRECTORY) {
                isLocked = entryDir.getAdminFile("lock").exists();
            }
        }
        if (!myIsReportAll) {
            if ((textStatus == SVNStatusType.STATUS_NONE || textStatus == SVNStatusType.STATUS_NORMAL)
                    && (propStatus == SVNStatusType.STATUS_NONE || propStatus == SVNStatusType.STATUS_NORMAL)
                    && !isLocked && !isSwitched && entry.getLockToken() == null) {
                return null;
            }
        }
        SVNLock localLock = null;
        if (entry.getLockToken() != null) {
            localLock = new SVNLock(null, entry.getLockToken(), entry.getLockOwner(), entry.getLockComment(), 
                    SVNTimeUtil.parseDate(entry.getLockCreationDate()), null);
        }
        File conflictOld = null;
        File conflictNew = null;
        File conflictWrk = null;
        File propReject = null;
        if (entry.getConflictOld() != null) {
            conflictOld = entryDir.getFile(entry.getConflictOld());
        }
        if (entry.getConflictNew() != null) {
            conflictNew = entryDir.getFile(entry.getConflictNew());
        }
        if (entry.getConflictWorking() != null) {
            conflictWrk = entryDir.getFile(entry.getConflictWorking());
        }
        if (entry.getPropRejectFile() != null) {
            propReject = entryDir.getFile(entry.getPropRejectFile());
        }

        return new SVNStatus(entry.getSVNURL(), file, entry.getKind(), SVNRevision
                .create(entry.getRevision()),
                entry.getCommittedRevision() >= 0 ? SVNRevision.create(entry.getCommittedRevision()) : null, 
                        SVNTimeUtil.parseDate(entry.getCommittedDate()),
                entry.getAuthor(), textStatus, propStatus,
                SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE, isLocked,
                entry.isCopied(), isSwitched, conflictNew, conflictOld,
                conflictWrk, propReject, entry.getCopyFromURL(), SVNRevision.create(entry.getCopyFromRevision()), 
                remoteLock, localLock, allEntryProperties);
    }

    private boolean isIgnored(SVNDirectory dir, String name) throws SVNException {
        if (myOptions.isIgnored(name)) {
            return true;
        }
        return dir != null && dir.isIgnored(name);
    }

    private SVNLock getRepositoryLock(SVNURL url) {
        if (myStatusReporter == null) {
            return null;
        }
        return myStatusReporter.getLock(url);
    }

    private boolean isSendableStatus(SVNStatus status) {
        if (status.getRemoteContentsStatus() != SVNStatusType.STATUS_NONE) {
            return true;
        }
        if (status.getRemotePropertiesStatus() != SVNStatusType.STATUS_NONE) {
            return true;
        }
        if (status.getRemoteLock() != null) {
            return true;
        }
        if (status.getContentsStatus() == SVNStatusType.STATUS_IGNORED
                && !myIsIncludeIgnored) {
            return false;
        }
        if (myIsReportAll) {
            return true;
        }
        if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED) {
            return true;
        }
        if (status.getContentsStatus() != SVNStatusType.STATUS_NONE
                && status.getContentsStatus() != SVNStatusType.STATUS_NORMAL) {
            return true;
        }
        if (status.getPropertiesStatus() != SVNStatusType.STATUS_NONE
                && status.getPropertiesStatus() != SVNStatusType.STATUS_NORMAL) {
            return true;
        }
        return status.isLocked() || status.isSwitched()
                || status.getLocalLock() != null;
    }

    private class DirectoryInfo {

        public DirectoryInfo(DirectoryInfo parent, String path, boolean added) throws SVNException {
            Parent = parent;
            if (!"".equals(path)) {
                Path = path;
                Name = SVNPathUtil.tail(path);
            } else {
                Path = "";
                Name = null;
            }
            IsAdded = added;
            ChildrenStatuses = new HashMap();

            SVNStatus parentStatus;
            if (Parent != null) {
                parentStatus = (SVNStatus) Parent.ChildrenStatuses.get(Name);
            } else {
                parentStatus = myAnchorStatus;
            }
            if (parentStatus != null) {
                SVNStatusType pContent = parentStatus.getContentsStatus();
                if (pContent != SVNStatusType.STATUS_UNVERSIONED
                        && pContent != SVNStatusType.STATUS_DELETED
                        && pContent != SVNStatusType.STATUS_MISSING
                        && pContent != SVNStatusType.OBSTRUCTED
                        && pContent != SVNStatusType.STATUS_EXTERNAL
                        && parentStatus.getKind() == SVNNodeKind.DIR
                        && (myIsRecursive || Parent == null)) {
                    // put children statuses into this dir map.
                    ISVNStatusHandler oldHandler = myHandler;
                    boolean oldRecursive = myIsRecursive;
                    boolean oldReportAll = myIsReportAll;
                    boolean oldIncludeIgnored = myIsIncludeIgnored;

                    SVNDirectory dir = myWCAccess.getDirectory(path);
                    if (dir == null) {
                        return;
                    }

                    myIsRecursive = false;
                    myIsReportAll = true;
                    myIsIncludeIgnored = true;
                    myHandler = new ISVNStatusHandler() {
                        public void handleStatus(SVNStatus status) {
                            ChildrenStatuses.put(status.getFile().getName(), status);
                        }
                    };
                    reportStatus(dir, null, true, false);
                    myIsRecursive = oldRecursive;
                    myIsIncludeIgnored = oldIncludeIgnored;
                    myIsReportAll = oldReportAll;
                    myHandler = oldHandler;
                }
            }
            RemoteRevision = SVNRevision.UNDEFINED;
        }

        public SVNURL getURL() {
            if (Name == null && myAnchorStatus != null) {
                return myAnchorStatus.getURL();
            } else if (Parent != null) {
                SVNStatus thisStatus = (SVNStatus) Parent.ChildrenStatuses
                        .get(Name);
                if (thisStatus != null && thisStatus.getURL() != null) {
                    return thisStatus.getURL();
                }
                SVNURL url = Parent.getURL();
                if (url != null) {
                    return url.appendPath(Name, false);
                }
            }
            return null;
        }

        public void tweakStatus(String path, SVNNodeKind kind, String name, SVNStatusType contents, SVNStatusType props, SVNLock lock, SVNRevision remoteRevision, Date remoteDate, String remoteAuthor) throws SVNException {
            SVNStatus existingStatus = (SVNStatus) ChildrenStatuses.get(name);
            if (existingStatus == null) {
                if (contents != SVNStatusType.STATUS_ADDED) {
                    return;
                }
                String dirPath = path;
                String target = "";
                if (kind == SVNNodeKind.FILE) {
                    dirPath = SVNPathUtil.removeTail(dirPath);
                    target = SVNPathUtil.tail(path);
                }
                SVNDirectory dir = myWCAccess.getDirectory(dirPath);
                SVNEntry entry = null;
                SVNEntry parentEntry = null;
                if (dir != null) {
                    entry = dir.getEntries().getEntry(target, false);
                    if (entry != null && !"".equals(dirPath)) {
                        if (!"".equals(target)) {
                            parentEntry = dir.getEntries().getEntry("", false);
                        } else {
                            SVNDirectory parentDir = myWCAccess
                                    .getDirectory(SVNPathUtil.removeTail(dirPath));
                            if (parentDir != null) {
                                parentEntry = parentDir.getEntries().getEntry("", false);
                            }
                        }
                    }
                }
                boolean oldReportAll = myIsReportAll;
                SVNURL url;
                if (entry != null) {
                    url = entry.getSVNURL();
                } else {
                    url = getURL();
                    if (url != null) {
                        url = url.appendPath(name, false);
                    }
                }
                try {
                    myIsReportAll = true;
                    existingStatus = createStatus(url, new File(myWCAccess.getAnchor().getRoot(), path), dir, parentEntry,
                            entry, false, SVNFileType.UNKNOWN, entry != null ? entry.asMap() : null);
                } finally {
                    myIsReportAll = oldReportAll;
                }
                // get revision in case of remote status.
                existingStatus.setRemoteStatus(url, contents, props, lock, kind, remoteRevision, remoteDate, remoteAuthor);
                ChildrenStatuses.put(name, existingStatus);
            } else {
                SVNURL url = null;
                if (myAnchorStatus != null) {
                    url = myAnchorStatus.getURL().appendPath(path, false);
                }
                existingStatus.setRemoteStatus(url, contents, props, null, kind, remoteRevision, remoteDate, remoteAuthor);
            }
        }

        public String Path;
        public String Name;
        public DirectoryInfo Parent;
        public SVNRevision RemoteRevision;
        public Date RemoteDate;
        public String RemoteAuthor;
        public boolean IsAdded;
        public boolean IsPropertiesChanged;
        public boolean IsContentsChanged;
        public Map ChildrenStatuses;
    }

    private static class FileInfo {

        public FileInfo(DirectoryInfo parent, String path, boolean added) {
            Parent = parent;
            Path = path;
            Name = SVNPathUtil.tail(path);
            IsAdded = added;
            RemoteRevision = SVNRevision.UNDEFINED;
        }

        public DirectoryInfo Parent;
        public String Path;
        public String Name;
        public boolean IsAdded;
        public boolean IsContentsChanged;
        public boolean IsPropertiesChanged;
        public SVNRevision RemoteRevision;
        public Date RemoteDate;
        public String RemoteAuthor;
    }
}
