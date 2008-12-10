/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Stack;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNEditorAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNCopyTask;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNExtendedMergeEditor extends SVNRemoteDiffEditor {

    private ISVNExtendedMergeCallback myMergeCallback;
    private SVNExtendedMergeDriver myMergeDriver;
    private SVNWCAccess myWCAccess;
    private SVNDepth myDepth;

    private SVNURL mySourceURL;
    private SVNURL myTargetURL;

    private Stack myDirectories;

    public SVNExtendedMergeEditor(SVNExtendedMergeDriver mergeDriver, ISVNExtendedMergeCallback mergeCallback, SVNAdminArea adminArea, File target, AbstractDiffCallback callback,
                                  SVNURL sourceURL, SVNRepository repos, long revision1, long revision2, boolean dryRun, SVNDepth depth, ISVNEventHandler handler,
                                  ISVNEventHandler cancelHandler) throws SVNException {
        super(adminArea, target, callback, repos, revision1, revision2, dryRun, handler, cancelHandler);

        SVNEntry rootEntry = adminArea.getEntry(adminArea.getThisDirName(), false);
        String url = rootEntry.getURL();
        myWCAccess = adminArea.getWCAccess();
        myDepth = depth;
        myTargetURL = SVNURL.parseURIEncoded(url);
        myMergeCallback = mergeCallback;
        myMergeDriver = mergeDriver;
        mySourceURL = sourceURL;

        myDirectories = new Stack();
    }

    public ISVNExtendedMergeCallback getMergeCallback() {
        return myMergeCallback;
    }

    public SVNExtendedMergeDriver getMergeDriver() {
        return myMergeDriver;
    }

    protected File getTempDirectory() throws SVNException {
        return getMergeDriver().getTempDirectory();
    }

    protected Stack getDirectories() {
        return myDirectories;
    }

    protected boolean checkDepth(File path, SVNEntry entry, SVNNodeKind kind) throws SVNException {
        if (path.equals(myTarget)) {
            return true;
        }
        boolean reportDepthAllows = checkReportDepth(path, kind);
        if (!reportDepthAllows) {
            return false;
        }
        return checkSparseWC(path, entry, kind);
    }

    private boolean checkReportDepth(File path, SVNNodeKind kind) throws SVNException {
        if (myDepth == SVNDepth.EMPTY) {
            return false;
        }
        if ((myDepth == SVNDepth.IMMEDIATES || myDepth == SVNDepth.FILES) && !myTarget.equals(path.getParentFile())) {
            return false;
        }
        if (myDepth == SVNDepth.FILES && kind == SVNNodeKind.DIR) {
            return false;
        }
        return true;
    }

    private boolean checkSparseWC(File path, SVNEntry entry, SVNNodeKind kind) throws SVNException {
        if (entry != null) {
            return true;
        }
        entry = myWCAccess.getEntry(path, true);
        if (entry != null) {
            return true;
        }
        File parentPath = path.getParentFile();
        SVNEntry parentEntry = myWCAccess.getEntry(parentPath, true);
        if (parentEntry != null) {
            SVNDepth parentDepth = parentEntry.getDepth();
            return checkEntryDepth(parentDepth, kind);
        }
        return walkToTarget(parentPath);
    }

    private boolean walkToTarget(File path) throws SVNException {
        if (myTarget.equals(path)) {
            return true;
        }
        File parentPath = path.getParentFile();
        SVNEntry parentEntry = myWCAccess.getEntry(parentPath, true);
        if (parentEntry != null) {
            SVNDepth parentDepth = parentEntry.getDepth();
            return checkEntryDepth(parentDepth, SVNNodeKind.DIR);
        }
        return walkToTarget(parentPath);
    }

    private static boolean checkEntryDepth(SVNDepth parentDepth, SVNNodeKind kind) {
        if (parentDepth == SVNDepth.EMPTY) {
            return false;
        }
        if (parentDepth == SVNDepth.FILES && kind != SVNNodeKind.FILE) {
            return false;
        }
        return true;
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        SVNNodeKind nodeKind = myRepos.checkPath(path, myRevision1);
        SVNAdminArea dir = retrieve(myCurrentDirectory.myWCFile, true);
        if (nodeKind != SVNNodeKind.FILE) {
            deleteEntry(path, nodeKind, dir);
            return;
        }

        long targetRevision = getTargetRevision(path);
        SVNURL[] targets = getMergeCallback().getTrueMergeTargets(getSourceURL(path), myRevision1, myRevision1, myRevision2, getTargetURL(path), targetRevision, SVNEditorAction.DELETE);

        if (targets == null) {
            deleteEntry(path, nodeKind, dir);
            return;
        }

        SVNFileInfoExt fileInfo = null;
        File expectedTarget = getFile(path);
        for (int i = 0; i < targets.length; i++) {
            SVNURL targetURL = targets[i];
            String targetPath = getPath(targetURL);
            File target = getFile(targetPath);

            if (!expectedTarget.equals(target)) {
                boolean depthAllows = checkDepth(target, null, nodeKind);
                if (!depthAllows) {
                    continue;
                }
            }

            fileInfo = getFileInfo(path, revision, SVNEditorAction.DELETE, nodeKind);
            fileInfo.addTarget(targetPath, null);
        }

        if (fileInfo != null) {
            fileInfo.delete();
        }
        myCurrentFile = null;
    }

    private File getCopySourcePath(SVNCopySource source) throws SVNException {
        if (source == null) {
            return null;
        }
        if (!source.isURL()) {
            return source.getFile();
        }
        File target = null;
        String sPath = SVNPathUtil.getPathAsChild(mySourceURL.getPath(), source.getURL().getPath());
        String tPath = SVNPathUtil.getPathAsChild(myTargetURL.getPath(), source.getURL().getPath());
        if (sPath != null) {
            target = getFile(sPath);
        } else if (tPath != null) {
            target = getFile(tPath);
        } else {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "merge ext: Neither merge source URL nor merge target URL are not ancestors of copy source URL");
        }
        return target;
    }

    private SVNURL getCopySourceURL(SVNCopySource source) throws SVNException {
        if (source == null) {
            return null;
        }
        if (source.isURL()) {
            return source.getURL();
        }
        String relativePath = SVNPathUtil.getPathAsChild(myTarget.getAbsolutePath(), source.getFile().getAbsolutePath());
        if (relativePath == null) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "merge ext: illegal file path passed as copy source");
            return null;
        }
        return myTargetURL.appendPath(relativePath, false);
    }

    private void deletePath(File file) throws SVNException {
        if (file == null) {
            return;
        }
        String path = getPath(file);
        SVNStatusType type = SVNStatusType.INAPPLICABLE;
        SVNEventAction action = SVNEventAction.SKIP;
        SVNEventAction expectedAction = SVNEventAction.UPDATE_DELETE;

        SVNEntry entry = myWCAccess.getEntry(file, false);
        if (entry == null) {
            return;
        }
        SVNNodeKind nodeKind = entry.getKind();
        SVNAdminArea dir = retrieve(myCurrentDirectory.myWCFile, true);

        if (dir != null) {
            if (nodeKind == SVNNodeKind.FILE) {
                SVNVersionedProperties baseProperties = dir.getBaseProperties(file.getName());
                String baseType = baseProperties.getStringPropertyValue(SVNProperty.MIME_TYPE);
                File baseFile = dir.getBaseFile(file.getName(), false);
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "merge ext: del " + path);
                type = getDiffCallback().fileDeleted(path, baseFile, null, baseType, null, baseProperties.asMap());
            } else if (nodeKind == SVNNodeKind.DIR) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "merge ext: attempt to delete directory " + path + " skipped");
            }
            if (type != SVNStatusType.MISSING && type != SVNStatusType.OBSTRUCTED) {
                action = SVNEventAction.UPDATE_DELETE;
                if (myIsDryRun) {
                    getDiffCallback().addDeletedPath(path);
                }
            }
        }
        addDeletedPath(path, nodeKind, type, action, expectedAction);
    }

    protected void addDeletedPath(String path, SVNNodeKind nodeKind, SVNStatusType type, SVNEventAction action, SVNEventAction expectedAction) {
        if (myEventHandler != null) {
            File deletedFile = getFile(path);
            KindActionState kas = (KindActionState) myDeletedPaths.get(deletedFile);
            if (kas != null) {
                if (action == SVNEventAction.SKIP && (kas.myAction == SVNEventAction.UPDATE_DELETE || kas.myAction == SVNEventAction.SKIP)) {
                    return;
                }
            }
        }
        super.addDeletedPath(path, nodeKind, type, action, expectedAction);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        SVNURL url = getSourceURL(path);
        SVNURL expectedTargetURL = getTargetURL(path);
        long targetRevision = getTargetRevision(path);
        SVNURL[] mergeURLs = getMergeCallback().getTrueMergeTargets(url, Math.max(myRevision1, myRevision2), myRevision1, myRevision2, expectedTargetURL, targetRevision, SVNEditorAction.ADD);

        if (mergeURLs == null) {
            super.addFile(path, copyFromPath, copyFromRevision);
            return;
        }

        for (int i = 0; i < mergeURLs.length; i++) {
            SVNURL targetURL = mergeURLs[i];
            String targetPath = getPath(targetURL);
            File target = getFile(targetPath);


            if (!expectedTargetURL.equals(targetURL)) {
                boolean depthAllows = checkDepth(target, null, SVNNodeKind.FILE);
                if (!depthAllows) {
                    continue;
                }
            }

            SVNURL[] mergeSources = new SVNURL[2];
            SVNMergeRangeList remainingRanges = getMergeDriver().calculateRemainingRanges(target, url, mergeSources);
            boolean mergeInfoConflicts = getMergeDriver().mergeInfoConflicts(remainingRanges, target);
            SVNCopyTask copyTask = getMergeCallback().getTargetCopySource(url, Math.max(myRevision1, myRevision2), myRevision1, myRevision2, targetURL, targetRevision);
            SVNCopySource copySource = copyTask == null ? null : copyTask.getCopySource();
            copySource = processCopySource(copySource, targetRevision);
            boolean deleteSource = copyTask != null && copyTask.isMove();

            if (deleteSource) {
                File deleteTarget = getCopySourcePath(copySource);
                boolean depthAllows = checkDepth(deleteTarget, null, SVNNodeKind.FILE);
                if (depthAllows) {
                    deletePath(deleteTarget);
                }
            }

            SVNEntry targetEntry = myWCAccess.getEntry(target, true);
            boolean targetExists = targetEntry != null && !targetEntry.isScheduledForDeletion();
            boolean applyDelta;

            if (targetExists) {
                getMergeDriver().addMergeSource(path, mergeSources, target, remainingRanges, mergeInfoConflicts, copySource);
                applyDelta = false;
            } else {
                applyDelta = true;
            }

            if (applyDelta) {
                SVNFileInfoExt fileInfo = getFileInfo(path, -1, SVNEditorAction.ADD, null);
                fileInfo.addTarget(targetPath, copySource);
            }
        }
    }

    public void openFile(String path, long revision) throws SVNException {
        SVNURL url = getSourceURL(path);
        SVNURL expectedTargetURL = getTargetURL(path);
        long targetRevision = getTargetRevision(path);
        SVNURL[] mergeURLs = getMergeCallback().getTrueMergeTargets(url, Math.max(myRevision1, myRevision2), myRevision1, myRevision2, getTargetURL(path), targetRevision, SVNEditorAction.MODIFY);

        if (mergeURLs == null) {
            super.openFile(path, revision);
            return;
        }

        for (int i = 0; i < mergeURLs.length; i++) {
            SVNURL targetURL = mergeURLs[i];
            String targetPath = getPath(targetURL);
            File target = getFile(targetPath);

            if (!expectedTargetURL.equals(targetURL)) {
                boolean depthAllows = checkDepth(target, null, SVNNodeKind.FILE);
                if (!depthAllows) {
                    continue;
                }
            }

            SVNURL[] mergeSources = new SVNURL[2];
            SVNMergeRangeList remainingRanges = getMergeDriver().calculateRemainingRanges(target, url, mergeSources);
            boolean mergeInfoConflicts = getMergeDriver().mergeInfoConflicts(remainingRanges, target);
            SVNCopyTask copyTask = getMergeCallback().getTargetCopySource(url, Math.max(myRevision1, myRevision2), myRevision1, myRevision2, targetURL, targetRevision);
            SVNCopySource copySource = copyTask == null ? null : copyTask.getCopySource();
            copySource = processCopySource(copySource, targetRevision);
            boolean deleteSource = copyTask != null && copyTask.isMove();

            if (deleteSource) {
                File deleteTarget = getCopySourcePath(copySource);
                boolean depthAllows = checkDepth(deleteTarget, null, SVNNodeKind.FILE);
                if (depthAllows) {
                    deletePath(deleteTarget);
                }
            }

            SVNEntry targetEntry = myWCAccess.getEntry(target, true);
            boolean targetExists = targetEntry != null && !targetEntry.isScheduledForDeletion();
            boolean applyDelta;

            if (targetExists) {
                if (mergeInfoConflicts) {
                    getMergeDriver().addMergeSource(path, mergeSources, target, remainingRanges, true, copySource);
                    applyDelta = false;
                } else {
                    applyDelta = true;
                }
            } else {
                if (copySource != null) {
                    getMergeDriver().copy(copySource, target, false);
                }
                applyDelta = true;
            }

            if (applyDelta) {
                SVNFileInfoExt fileInfo = getFileInfo(path, revision, SVNEditorAction.MODIFY, null);
                fileInfo.addTarget(targetPath, copySource);
            }
        }
    }

    private SVNCopySource processCopySource(SVNCopySource copySource, long targetRevision) throws SVNException {
        if (copySource == null) {
            return null;
        }
        if (copySource.isURL() && (copySource.getRevision() != null && copySource.getRevision().getID() == 10)) {
            return copySource;
        }

        SVNRevision pegRevision;
        SVNRevision revision;
        try {
            pegRevision = processCopySourceRevision(copySource.getPegRevision(), copySource, targetRevision);
            revision = processCopySourceRevision(copySource.getRevision(), copySource, targetRevision);
        } catch (SVNException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "merge ext: Error while fetching copy source revision");
            return null;
        }

        SVNURL url = copySource.getURL();
        if (url == null) {
            url = getCopySourceURL(copySource);
            if (url == null) {
                return null;
            }
        }
        return new SVNCopySource(pegRevision, revision, url);
    }

    private SVNRevision processCopySourceRevision(SVNRevision revision, SVNCopySource copySource, long targetRevision) throws SVNException {
        if (revision.getID() == 10) {
            return revision;
        }
        if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
            return SVNRevision.create(targetRevision);
        }
        return SVNRevision.create(getMergeDriver().getRevision(copySource));
    }

    private long getTargetRevision(String path) throws SVNException {
        File file = getFile(path);
        SVNEntry entry = myWCAccess.getEntry(file, true);
        return calculateTargetRevision(myWCAccess, entry, file);
    }

    public void openDir(String path, long revision) throws SVNException {
        super.openDir(path, revision);
        processDir(path, false);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        super.addDir(path, copyFromPath, copyFromRevision);
        processDir(path, true);
    }

    private void processDir(String path, boolean remoteAdd) throws SVNException {
        File dir = getFile(path);
        SVNEntry dirEntry = myWCAccess.getEntry(dir, true);

        long dirRevision = calculateTargetRevision(myWCAccess, dirEntry, dir);
        boolean skipped = dirEntry == null;
        boolean added = dirEntry != null && (dirEntry.isScheduledForAddition() || dirEntry.isScheduledForReplacement());

        if (skipped || added) {
            SVNDirectoryInfoExt dirInfo = new SVNDirectoryInfoExt(dirRevision);
            dirInfo.myAdded = added;
            dirInfo.myRemotelyAdded = remoteAdd;
            dirInfo.mySkipped = skipped;
            getDirectories().push(dirInfo);
        }
    }

    private long calculateTargetRevision(SVNWCAccess access, SVNEntry entry, File path) throws SVNException {
        long targetRevision;
        if (entry == null) {
            if (getDirectories().empty()) {
                SVNEntry parentEntry = access.getEntry(path.getParentFile(), true);
                checkParentEntry(path, parentEntry);
                targetRevision = parentEntry.getRevision();
            } else {
                SVNDirectoryInfoExt parentDirInfo = (SVNDirectoryInfoExt) getDirectories().peek();
                targetRevision = parentDirInfo.myRevision;
            }
        } else {
            targetRevision = entry.getRevision();
            if (entry.isScheduledForAddition() || entry.isScheduledForReplacement()) {
                if (getDirectories().empty()) {
                    SVNEntry parentEntry = access.getEntry(path.getParentFile(), true);
                    checkParentEntry(path, parentEntry);
                    targetRevision = parentEntry.getRevision();
                } else {
                    SVNDirectoryInfoExt parentDirInfo = (SVNDirectoryInfoExt) getDirectories().peek();
                    targetRevision = parentDirInfo.myRevision;
                }
            }
        }
        return targetRevision;
    }

    private static void checkParentEntry(File path, SVNEntry parentEntry) throws SVNException {
        if (parentEntry == null) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Parent directory of ''{0}'' is unexpectedly unversioned", path);
            SVNErrorManager.error(error, SVNLogType.WC);
        }
        if (parentEntry.isScheduledForAddition() || parentEntry.isScheduledForReplacement()) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ENTRY_ATTRIBUTE_INVALID, "Parent directory of ''{0}'' unexpectedly scheduled for ''{1}''", new Object[]{path, parentEntry.getSchedule()});
            SVNErrorManager.error(error, SVNLogType.WC);
        }
    }

    public void closeDir() throws SVNException {
        super.closeDir();

        if (!getDirectories().empty()) {
            getDirectories().pop();
        }
    }

    private SVNFileInfoExt getFileInfo(String path, long revision, SVNEditorAction action, SVNNodeKind kind) throws SVNException {
        if (myCurrentFile == null) {
            myCurrentFile = createFileInfo(path, action, kind);
            if (action == SVNEditorAction.ADD) {
                myCurrentFile.myBaseProperties = new SVNProperties();
                myCurrentFile.myBaseFile = SVNFileUtil.createUniqueFile(getTempDirectory(), ".diff", ".tmp", false);
            } else if (action == SVNEditorAction.MODIFY) {
                myCurrentFile.loadFromRepository(revision);
            }
        }
        return (SVNFileInfoExt) myCurrentFile;
    }

    public void changeFileProperty(String commitPath, String name, SVNPropertyValue value) throws SVNException {
        if (myCurrentFile == null) {
            return;
        }
        myCurrentFile.myPropertyDiff.put(name, value);
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        if (myCurrentFile == null) {
            return;
        }
        if (myCurrentFile instanceof SVNFileInfoExt) {
            SVNFileInfoExt fileInfo = (SVNFileInfoExt) myCurrentFile;
            fileInfo.applyTextDelta(commitPath, baseChecksum);
        } else {
            super.applyTextDelta(commitPath, baseChecksum);
        }
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        if (myCurrentFile == null) {
            return SVNFileUtil.DUMMY_OUT;
        }
        if (myCurrentFile instanceof SVNFileInfoExt) {
            SVNFileInfoExt fileInfo = (SVNFileInfoExt) myCurrentFile;
            return fileInfo.textDeltaChunk(commitPath, diffWindow);
        }
        return super.textDeltaChunk(commitPath, diffWindow);
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        if (myCurrentFile == null) {
            return;
        }
        if (myCurrentFile instanceof SVNFileInfoExt) {
            SVNFileInfoExt fileInfo = (SVNFileInfoExt) myCurrentFile;
            fileInfo.textDeltaEnd(commitPath);
        } else {
            super.textDeltaEnd(commitPath);
        }
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        if (myCurrentFile == null) {
            return;
        }
        if (myCurrentFile instanceof SVNFileInfoExt) {
            SVNFileInfoExt fileInfo = (SVNFileInfoExt) myCurrentFile;
            fileInfo.close();
        } else {
            super.closeFile(commitPath, textChecksum);
        }
        myCurrentFile = null;
    }

    private SVNURL getSourceURL(String path) throws SVNException {
        return mySourceURL.appendPath(path, false);
    }

    private SVNURL getTargetURL(String path) throws SVNException {
        return myTargetURL.appendPath(path, false);
    }

    private File getFile(String path) {
        return new File(myTarget, path);
    }

    private String getPath(SVNURL target) {
        return SVNPathUtil.getRelativePath(myTargetURL.getPath(), target.getPath());
    }

    private String getPath(File path) {
        return SVNPathUtil.getRelativePath(myTarget.getAbsolutePath(), path.getAbsolutePath());
    }

    protected SVNFileInfoExt createFileInfo(String path, SVNEditorAction action, SVNNodeKind kind) {
        return new SVNFileInfoExt(path, action == SVNEditorAction.ADD, action, kind);
    }

    protected class SVNDirectoryInfoExt {

        protected long myRevision;
        protected boolean myAdded;
        protected boolean myRemotelyAdded;
        protected boolean mySkipped;

        public SVNDirectoryInfoExt(long revision) {
            myRevision = revision;
        }
    }

    protected class SVNFileInfoExt extends SVNFileInfo implements ISVNDeltaConsumer {

        protected SVNEditorAction myAction;
        protected Collection myTargets;
        protected SVNNodeKind myKind;
        protected boolean myIsLoaded;

        protected SVNFileInfoExt(String path, boolean added, SVNEditorAction action, SVNNodeKind nodeKind) {
            super(path, added);
            myAction = action;
            myKind = nodeKind;
        }

        public void loadFromRepository(long revision) throws SVNException {
            if (!myIsLoaded) {
                super.loadFromRepository(revision);
                myIsLoaded = true;
            }
        }

        protected SVNNodeKind getNodeKind() throws SVNException {
            if (myKind == null) {
                myKind = myRepos.checkPath(myRepositoryPath, myRevision1);
            }
            return myKind;
        }

        protected Collection getTargets() {
            if (myTargets == null) {
                myTargets = new ArrayList();
            }
            return myTargets;
        }

        protected void addTarget(String targetPath, SVNCopySource copySource) {
            SVNDeltaProcessor processor;
            if (myAction == SVNEditorAction.DELETE) {
                processor = null;
            } else {
                processor = getTargets().size() == 0 ? myDeltaProcessor : new SVNDeltaProcessor();
            }
            MergeTarget target = new MergeTarget(targetPath, processor, copySource);
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "ext merge: " + target.toString());
            getTargets().add(target);
        }

        public void applyTextDelta(String path, String baseChecksum) throws SVNException {
            if (myTargets == null || myTargets.isEmpty()) {
                return;
            }
            for (Iterator iterator = getTargets().iterator(); iterator.hasNext();) {
                MergeTarget target = (MergeTarget) iterator.next();
                target.applyTextDelta(path, baseChecksum);
            }
        }

        public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
            if (myTargets == null || myTargets.isEmpty()) {
                return SVNFileUtil.DUMMY_OUT;
            }
            for (Iterator iterator = getTargets().iterator(); iterator.hasNext();) {
                MergeTarget target = (MergeTarget) iterator.next();
                target.textDeltaChunk(path, diffWindow);
            }
            return SVNFileUtil.DUMMY_OUT;
        }

        public void textDeltaEnd(String path) throws SVNException {
            if (myTargets == null || myTargets.isEmpty()) {
                return;
            }
            for (Iterator iterator = getTargets().iterator(); iterator.hasNext();) {
                MergeTarget target = (MergeTarget) iterator.next();
                target.textDeltaEnd(path);
            }
        }

        protected void close() throws SVNException {
            if (myTargets == null || myTargets.isEmpty()) {
                return;
            }
            for (Iterator iterator = getTargets().iterator(); iterator.hasNext();) {
                MergeTarget target = (MergeTarget) iterator.next();
                target.close();
            }
        }

        protected void delete() throws SVNException {
            if (myTargets == null || myTargets.isEmpty()) {
                return;
            }
            for (Iterator iterator = getTargets().iterator(); iterator.hasNext();) {
                MergeTarget target = (MergeTarget) iterator.next();
                target.delete();
            }
        }
    }

    protected class MergeTarget implements ISVNDeltaConsumer {

        private SVNDeltaProcessor myTargetProcessor;
        private String myPath;
        private File myWCFile;
        private File myFile;
        private SVNCopySource myCopySource;

        public MergeTarget(String path, SVNDeltaProcessor targetProcessor, SVNCopySource copySource) {
            myPath = path;
            myWCFile = myTarget == null ? null : new File(myTarget, path);
            myTargetProcessor = targetProcessor;
            myCopySource = copySource;
        }

        private SVNDeltaProcessor getDeltaProcessor() {
            return myTargetProcessor;
        }

        public void applyTextDelta(String path, String baseChecksum) throws SVNException {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "ext merge: apply delta " + myPath);
            SVNAdminArea dir;
            try {
                dir = retrieveParent(myWCFile, true);
            } catch (SVNException e) {
                dir = null;
            }
            myFile = createTempFile(dir, SVNPathUtil.tail(myPath));
            getDeltaProcessor().applyTextDelta(myCurrentFile.myBaseFile, myFile, false);
        }

        public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "ext merge: delta chunk " + myPath);
            return getDeltaProcessor().textDeltaChunk(diffWindow);
        }

        public void textDeltaEnd(String path) throws SVNException {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "ext merge: delta end " + myPath);
            getDeltaProcessor().textDeltaEnd();
        }

        protected void close() throws SVNException {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "ext merge: close " + myPath);
            closeFile(myPath, myCurrentFile.myIsAdded, myWCFile, myFile, myCurrentFile.myPropertyDiff, myCurrentFile.myBaseProperties, myCurrentFile.myBaseFile);

            if (myCopySource == null) {
                return;
            }

            SVNEntry targetEntry = myWCAccess.getEntry(myWCFile, false);
            if (targetEntry != null) {
                SVNURL copyFromLocation = targetEntry.getCopyFromSVNURL();
                long copyFromRevision = targetEntry.getCopyFromRevision();

                SVNURL sourceURL = myCopySource.getURL();
                long sourceRevision = myCopySource.getRevision().getNumber();
                if (copyFromLocation == null || !copyFromLocation.equals(sourceURL) || copyFromRevision != sourceRevision) {
                    getMergeDriver().copy(myCopySource, myWCFile, true);
                }
            }
        }

        protected void delete() throws SVNException {
            SVNStatusType type = SVNStatusType.INAPPLICABLE;
            SVNEventAction action = SVNEventAction.SKIP;
            SVNEventAction expectedAction = SVNEventAction.UPDATE_DELETE;

            SVNFileInfoExt fileInfo = (SVNFileInfoExt) myCurrentFile;
            final SVNNodeKind nodeKind = fileInfo.getNodeKind();

            SVNAdminArea dir = retrieveParent(myWCFile, true);
            if (myAdminArea == null || dir != null) {
                if (nodeKind == SVNNodeKind.FILE) {
                    fileInfo.loadFromRepository(myRevision1);
                    String baseType = fileInfo.myBaseProperties.getStringValue(SVNProperty.MIME_TYPE);
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "merge ext: del " + myPath);
                    type = getDiffCallback().fileDeleted(myPath, fileInfo.myBaseFile, null, baseType, null, fileInfo.myBaseProperties);
                } else if (nodeKind == SVNNodeKind.DIR) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, "merge ext: attempt to delete directory skipped");
                }
                if (type != SVNStatusType.MISSING && type != SVNStatusType.OBSTRUCTED) {
                    action = SVNEventAction.UPDATE_DELETE;
                    if (myIsDryRun) {
                        getDiffCallback().addDeletedPath(myPath);
                    }
                }
            }
            addDeletedPath(myPath, nodeKind, type, action, expectedAction);
        }

        public String toString() {
            StringBuffer buffer = new StringBuffer();
            buffer.append("[merge target: path = ");
            buffer.append(myPath);
            buffer.append("; wc file = ");
            buffer.append(myWCFile);
            buffer.append("; file = ");
            buffer.append(myFile);
            buffer.append("; base file = ");
            buffer.append(myCurrentFile.myBaseFile);
            buffer.append("; copyFromURL = ");
            buffer.append(myCopySource == null ? null : myCopySource.getURL());
            buffer.append("; copyFromRevision = ");
            buffer.append(myCopySource == null ? "unknown" : String.valueOf(myCopySource.getRevision()));
            buffer.append("]");
            return buffer.toString();
        }
    }
}
