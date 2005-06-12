package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNOptions;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;
import org.tmatesoft.svn.util.DebugLog;

import java.io.OutputStream;
import java.io.File;
import java.util.StringTokenizer;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;

public class SVNStatusEditor implements ISVNEditor {

    private SVNOptions myOptions;
    private SVNWCAccess myWCAccess;
    private String myTarget;

    private ISVNStatusHandler myHandler;

    private boolean myIsReportAll;
    private boolean myIsIncludeIgnored;
    private boolean myIsRecursive;

    private long myTargetRevision;
    private boolean myIsRootOpened;
    private Map myExternalsMap;

    public SVNStatusEditor(SVNOptions globalOptions, SVNWCAccess wcAccess, ISVNStatusHandler handler,
                           boolean includeIgnored,
                           boolean reportAll,
                           boolean recursive) {
        myWCAccess = wcAccess;
        myHandler = handler;
        myOptions = globalOptions;
        myIsIncludeIgnored = includeIgnored;
        myIsReportAll = reportAll;
        myIsRecursive = recursive;
        myExternalsMap = new HashMap();
        myTarget = "".equals(myWCAccess.getTargetName()) ? null : myWCAccess.getTargetName();
    }

    public Map getCollectedExternals() {
        return myExternalsMap;
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpened = true;
    }

    public void deleteEntry(String path, long revision) throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void openDir(String path, long revision) throws SVNException {
    }

    public void changeDirProperty(String name, String value) throws SVNException {
    }

    public void closeDir() throws SVNException {
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void openFile(String path, long revision) throws SVNException {
    }

    public void applyTextDelta(String baseChecksum) throws SVNException {
    }

    public void changeFileProperty(String name, String value) throws SVNException {
    }

    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd() throws SVNException {
    }

    public void closeFile(String textChecksum) throws SVNException {
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (myIsRootOpened) {
            return new SVNCommitInfo(myTargetRevision, null, null);
        }
        if (myTarget != null) {
            File file = myWCAccess.getAnchor().getFile(myTarget, false);
            if (file.isDirectory()) {
                SVNEntries entries = myWCAccess.getAnchor().getEntries();
                SVNEntry entry = entries.getEntry(myTarget, true);
                entries.close();
                if (entry != null) {
                    reportStatus(myWCAccess.getTarget(), null, false, myIsRecursive);
                } else {
                    // disable exclude ignore for explicit unversioned dir target
                    myIsIncludeIgnored = true;
                    reportStatus(myWCAccess.getAnchor(), myTarget, false, myIsRecursive);
                }
            } else {
                // disable exclude ignore for explicit file target
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

    private void reportStatus(SVNDirectory dir, String entryName, boolean ignoreRootEntry, boolean recursive) throws SVNException {
        SVNEntries entries = dir.getEntries();

        SVNExternalInfo[] externals = SVNWCAccess.parseExternals(dir.getPath(), dir.getProperties("", false).getPropertyValue(SVNProperty.EXTERNALS));
        for (int i = 0; i < externals.length; i++) {
            SVNExternalInfo external = externals[i];
            myExternalsMap.put(external.getPath(), external);
        }
        if (entryName != null) {
            SVNEntry entry = entries.getEntry(entryName, true);
            if (entry != null) {
                sendVersionedStatus(dir, entryName);
            } else if (dir.getFile(entryName, false).exists()) {
                DebugLog.log("sending unversioned status (1) for " + dir.getFile(entryName, false));
                sendUnversionedStatus(dir, entryName);
            }
            return;
        }
        File[] ioFiles = dir.getRoot().listFiles();
        if (ioFiles != null)  {
            Arrays.sort(ioFiles, new Comparator() {
                public int compare(Object o1, Object o2) {
                    File f1 = (File) o1;
                    File f2 = (File) o2;
                    int f1type = SVNFileType.getType(f1).getID();
                    int f2type = SVNFileType.getType(f2).getID();
                    return f1type == f2type ? 0 : (f1type > f2type) ? -1 : 1;
                }
            });
        }
        for (int i = 0; ioFiles != null && i < ioFiles.length; i++) {
            File ioFile = ioFiles[i];
            String fileName = ioFile.getName();
            if (".svn".equals(fileName) || entries.getEntry(fileName, false) != null) {
                continue;
            }
            sendUnversionedStatus(dir, fileName);
        }
        if (!ignoreRootEntry) {
            sendVersionedStatus(dir, "");
        }
        for(Iterator ents = entries.entries(false); ents.hasNext();) {
            SVNEntry childEntry = (SVNEntry) ents.next();
            if ("".equals(childEntry.getName())) {
                continue;
            }
            File file = dir.getFile(childEntry.getName(), false);
            if (file.isDirectory()) {
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
        String path;
        File file;
        SVNEntry parentEntry;
        SVNDirectory parentDir = null;
        SVNEntry entry = dir.getEntries().getEntry(name, true);

        if (entry.isDirectory() && !"".equals(name)) {
            // we are in the parent dir, with 'short' entry
            parentDir = dir;
            dir = dir.getChildDirectory(name);
            if (dir == null) {
                dir = new SVNDirectory(myWCAccess, PathUtil.append(parentDir.getPath(), name), parentDir.getFile(name, false));
            }
        } else  if (entry.isDirectory() && "".equals(name)) {
            // we are in the dir itself already, try to get parent dir.
            if (!"".equals(dir.getPath())) {
                // there is parent dir
                String parentPath = PathUtil.removeTail(dir.getPath());
                parentDir = myWCAccess.getDirectory(parentPath);
            } else {
                // it is a root of wc.
                parentDir = null;
            }
        } else if (entry.isFile()) {
            // it is a file, dir and parentDir are the same.
            parentDir = dir;
        }
        SVNEntry entryInParent = entry;
        entry = null;
        if (dir == parentDir) {
            path = PathUtil.append(dir.getPath(), name);
            file = dir.getFile(name, false);
            entry = dir.getEntries().getEntry(name, true);
            parentEntry = dir.getEntries().getEntry("", true);
        } else {
            path = dir.getPath();
            file = dir.getRoot();
            entry = dir.getEntries().getEntry("", true);
            if (entry == null && entryInParent != null) {
                // probably missing dir.
                entry = entryInParent;
                dir = parentDir;
            }
            parentEntry = parentDir != null ? parentDir.getEntries().getEntry("", true) : null;
        }
        SVNFileType fileType = SVNFileType.getType(file);
        SVNStatus status = createStatus(path, file, dir, parentEntry, entry, false, fileType, entry != null ? Collections.unmodifiableMap(entry.asMap()) : null);

        if (status != null) {
            myHandler.handleStatus(status);
        }
    }

    private void sendUnversionedStatus(SVNDirectory parent, String name) throws SVNException {
        boolean ignored = isIgnored(parent, name);
        String path = "".equals(name) ? parent.getPath() : PathUtil.append(parent.getPath(), name);
        SVNStatus status = createStatus(path, parent.getFile(name, false), parent, null, null, ignored, null, null);
        if (myExternalsMap.containsKey(path)) {
            status.markExternal();
        }
        if (status != null) {
            if (myIsIncludeIgnored || !ignored || myExternalsMap.containsKey(path) || status.getRemoteLock() != null) {
                myHandler.handleStatus(status);
            }
        }
    }

    private SVNStatus createStatus(String path, File file, SVNDirectory entryDir, SVNEntry parentEntry, SVNEntry entry /* this could be dir entry in parent*/,
                                   boolean isIgnored, SVNFileType pathKind, Map allEntryProperties) throws SVNException {
        pathKind = pathKind == null || pathKind == SVNFileType.UNKNOWN ?
                SVNFileType.getType(file) : pathKind;

        DebugLog.log("creating status for " + file);
        DebugLog.log("path kind: " + pathKind);
        if (entry == null) {
            DebugLog.log("no entry " + file);
            SVNStatusType textStatus = SVNStatusType.STATUS_NONE;
            if (pathKind != SVNFileType.NONE) {
                textStatus = isIgnored ? SVNStatusType.STATUS_IGNORED : SVNStatusType.STATUS_UNVERSIONED;
            }
            return new SVNStatus(null, file, null, null, null, null, null,
                    textStatus, SVNStatusType.STATUS_NONE,
                    SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE,
                    false, false, false,
                    null, null, null, null, null, null, null, null, null);
        }
        SVNStatusType textStatus = SVNStatusType.STATUS_NORMAL;
        SVNStatusType propStatus = SVNStatusType.STATUS_NONE;

        boolean isSwitched = false;
        boolean isLocked = false;

        if (entry.isDirectory()) {
            if (pathKind == SVNFileType.DIRECTORY) {
                if (!SVNWCAccess.isVersionedDirectory(file)) {
                    textStatus = SVNStatusType.STATUS_OBSTRUCTED;
                }
            } else if (pathKind != SVNFileType.NONE) {
                textStatus = SVNStatusType.STATUS_OBSTRUCTED;
            }
        }
        if (parentEntry != null && entry.getURL() != null && parentEntry.getURL() != null) {

            if (entry.isFile() && !entry.getName().equals(PathUtil.decode(PathUtil.tail(entry.getURL())))) {
                isSwitched = true;
            }
            if (!isSwitched && !PathUtil.removeTail(entry.getURL()).equals(parentEntry.getURL())) {
                isSwitched = true;
            }
        }
        if (textStatus != SVNStatusType.OBSTRUCTED) {
            SVNProperties props = entryDir.getProperties(entry.getName(), false);
            if (props != null && !props.isEmpty()) {
                propStatus = SVNStatusType.STATUS_NORMAL;
            }
            boolean propsModified = entryDir.hasPropModifications(entry.getName());
            boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
            DebugLog.log("special : " + special);
            boolean textModified = false;
            if (entry.isFile() && special == (pathKind == SVNFileType.SYMLINK)) {
                textModified = entryDir.hasTextModifications(entry.getName(), false);
            }
            if (propsModified) {
                propStatus = SVNStatusType.STATUS_MODIFIED;
            }
            if (textModified) {
                textStatus = SVNStatusType.STATUS_MODIFIED;
            }
            if (entry.getConflictNew() != null || entry.getConflictOld() != null || entry.getConflictWorking() != null) {
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
            if (entry.isIncomplete() && textStatus != SVNStatusType.STATUS_ADDED && textStatus != SVNStatusType.STATUS_DELETED) {
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
            if (entry.isDirectory() && pathKind == SVNFileType.DIRECTORY) {
                isLocked = entryDir.getFile(".svn/lock", false).exists();
            }
        }
        if (!myIsReportAll) {
            if ((textStatus == SVNStatusType.STATUS_NONE || textStatus == SVNStatusType.STATUS_NORMAL) &&
                    (propStatus == SVNStatusType.STATUS_NONE || propStatus == SVNStatusType.STATUS_NORMAL) &&
                    !isLocked && !isSwitched && entry.getLockToken() == null) {
                return null;
            }
        }
        SVNLock localLock = null;
        if (entry.getLockToken() != null) {
            localLock = new SVNLock(null, entry.getLockToken(), entry.getLockOwner(), entry.getLockComment(),
                TimeUtil.parseDate(entry.getLockCreationDate()), null);
        }
        File conflictOld = null;
        File conflictNew = null;
        File conflictWrk = null;
        File propReject = null;
        if (entry.getConflictOld() != null) {
            conflictOld = entryDir.getFile(entry.getConflictOld(), false);
        }
        if (entry.getConflictNew() != null) {
            conflictNew = entryDir.getFile(entry.getConflictNew(), false);
        }
        if (entry.getConflictWorking() != null) {
            conflictWrk = entryDir.getFile(entry.getConflictWorking(), false);
        }
        if (entry.getPropRejectFile() != null) {
            propReject = entryDir.getFile(entry.getPropRejectFile(), false);
        }

        return new SVNStatus(entry.getURL(), file, entry.getKind(),
                SVNRevision.create(entry.getRevision()), SVNRevision.create(entry.getCommittedRevision()),
                TimeUtil.parseDate(entry.getCommittedDate()), entry.getAuthor(),
                textStatus, propStatus,
                SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE,
                isLocked, entry.isCopied(), isSwitched,
                conflictNew, conflictOld, conflictWrk, propReject, entry.getCopyFromURL(), SVNRevision.create(entry.getCopyFromRevision()), null, localLock,
                allEntryProperties);
    }

    private boolean isIgnored(SVNDirectory dir, String name) throws SVNException {
        if (myOptions.isIgnored(name)) {
            return true;
        }
        String ignoredProperty = dir.getProperties("", false).getPropertyValue(SVNProperty.IGNORE);
        if (ignoredProperty == null) {
            return false;
        }
        for(StringTokenizer tokens = new StringTokenizer(ignoredProperty, "\r\n"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (token.length() == 0) {
                continue;
            }
            if (SVNOptions.compileNamePatter(token).matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }
}
