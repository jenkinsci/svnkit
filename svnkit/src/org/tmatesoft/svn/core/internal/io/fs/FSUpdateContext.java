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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class FSUpdateContext {

    private File myReportFile;
    private String myTarget;
    private OutputStream myReportOS;
    private FSFile myReportIS;
    private ISVNEditor myEditor;
    private long myTargetRevision;
    private boolean isRecursive;
    private PathInfo myCurrentPathInfo;
    private boolean ignoreAncestry;
    private boolean sendTextDeltas;
    private String myTargetPath;
    private boolean isSwitch;
    private FSRevisionRoot myTargetRoot;
    private LinkedList myRootsCache;
    private FSFS myFSFS;
    private FSRepository myRepository;
    private SVNDeltaGenerator myDeltaGenerator;
    private SVNDeltaCombiner myDeltaCombiner;

    public FSUpdateContext(FSRepository repository, FSFS owner, long revision, File reportFile, String target, String targetPath, boolean isSwitch, boolean recursive, boolean ignoreAncestry,
            boolean textDeltas, ISVNEditor editor) {
        myRepository = repository;
        myFSFS = owner;
        myTargetRevision = revision;
        myReportFile = reportFile;
        myTarget = target;
        myEditor = editor;
        isRecursive = recursive;
        this.ignoreAncestry = ignoreAncestry;
        sendTextDeltas = textDeltas;
        myTargetPath = targetPath;
        this.isSwitch = isSwitch;
    }

    public void reset(FSRepository repository, FSFS owner, long revision, File reportFile, String target, String targetPath, boolean isSwitch, boolean recursive, boolean ignoreAncestry,
            boolean textDeltas, ISVNEditor editor) throws SVNException {
        dispose();
        myRepository = repository;
        myFSFS = owner;
        myTargetRevision = revision;
        myReportFile = reportFile;
        myTarget = target;
        myEditor = editor;
        isRecursive = recursive;
        this.ignoreAncestry = ignoreAncestry;
        sendTextDeltas = textDeltas;
        myTargetPath = targetPath;
        this.isSwitch = isSwitch;
    }

    public OutputStream getReportFileForWriting() throws SVNException {
        if (myReportOS == null) {
            myReportOS = SVNFileUtil.openFileForWriting(myReportFile);
        }
        return myReportOS;
    }

    private boolean isIgnoreAncestry() {
        return ignoreAncestry;
    }

    private boolean isSwitch() {
        return isSwitch;
    }

    private boolean isSendTextDeltas() {
        return sendTextDeltas;
    }

    private String getReportTarget() {
        return myTarget;
    }

    private String getReportTargetPath() {
        return myTargetPath;
    }

    public void dispose() throws SVNException {
        SVNFileUtil.closeFile(myReportOS);
        myReportOS = null;

        if (myReportIS != null) {
            myReportIS.close();
            myReportIS = null;
        }

        if (myReportFile != null) {
            SVNFileUtil.deleteFile(myReportFile);
            myReportFile = null;
        }

        if (myDeltaCombiner != null) {
            myDeltaCombiner.reset();
        }

        myTargetRoot = null;
        myRootsCache = null;
    }

    private ISVNEditor getEditor() {
        return myEditor;
    }

    private boolean isRecursive() {
        return isRecursive;
    }

    private long getTargetRevision() {
        return myTargetRevision;
    }

    private PathInfo getNextPathInfo() throws IOException {
        if (myReportIS == null) {
            myReportIS = new FSFile(myReportFile);
        }

        myCurrentPathInfo = myReportIS.readPathInfoFromReportFile();
        return myCurrentPathInfo;
    }

    private PathInfo getCurrentPathInfo() {
        return myCurrentPathInfo;
    }

    private FSRevisionRoot getTargetRoot() {
        if (myTargetRoot == null) {
            myTargetRoot = myFSFS.createRevisionRoot(myTargetRevision);
        }
        return myTargetRoot;
    }

    private LinkedList getRootsCache() {
        if (myRootsCache == null) {
            myRootsCache = new LinkedList();
        }
        return myRootsCache;
    }

    private FSRevisionRoot getSourceRoot(long revision) {
        LinkedList cache = getRootsCache();
        FSRevisionRoot root = null;
        int i = 0;

        for (; i < cache.size() && i < 10; i++) {
            root = (FSRevisionRoot) myRootsCache.get(i);
            if (root.getRevision() == revision) {
                if (i != 0) {
                    myRootsCache.remove(i);
                    myRootsCache.addFirst(root);
                }
                break;
            }
            root = null;
        }

        if (root == null) {
            if (i == 10) {
                myRootsCache.removeLast();
            }
            root = myFSFS.createRevisionRoot(revision);
            myRootsCache.addFirst(root);
        }

        return root;
    }

    public void drive() throws SVNException {
        OutputStream reportOS = getReportFileForWriting();
        try {
            reportOS.write('-');
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } finally {
            SVNFileUtil.closeFile(reportOS);
        }

        PathInfo info = null;

        try {
            info = getNextPathInfo();
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }

        if (info == null || !info.getPath().equals(getReportTarget()) || info.getLinkPath() != null || FSRepository.isInvalidRevision(info.getRevision())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_REVISION_REPORT, "Invalid report for top level of working copy");
            SVNErrorManager.error(err);
        }

        long sourceRevision = info.getRevision();
        PathInfo lookahead = null;

        try {
            lookahead = getNextPathInfo();
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }

        if (lookahead != null && lookahead.getPath().equals(getReportTarget())) {
            if ("".equals(getReportTarget())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_REVISION_REPORT, "Two top-level reports with no target");
                SVNErrorManager.error(err);
            }

            info = lookahead;

            try {
                getNextPathInfo();
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe);
            }
        }

        getEditor().targetRevision(getTargetRevision());

        String fullTargetPath = getReportTargetPath();
        String fullSourcePath = SVNPathUtil.concatToAbs(myRepository.getRepositoryPath(""), getReportTarget());
        FSEntry targetEntry = fakeDirEntry(fullTargetPath, getTargetRoot());
        FSRevisionRoot srcRoot = getSourceRoot(sourceRevision);
        FSEntry sourceEntry = fakeDirEntry(fullSourcePath, srcRoot);

        if (FSRepository.isValidRevision(info.getRevision()) && info.getLinkPath() == null && sourceEntry == null) {
            fullSourcePath = null;
        }

        if ("".equals(getReportTarget()) && (sourceEntry == null || sourceEntry.getType() != SVNNodeKind.DIR || targetEntry == null || targetEntry.getType() != SVNNodeKind.DIR)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_PATH_SYNTAX, "Cannot replace a directory from within");
            SVNErrorManager.error(err);
        }

        if (myDeltaGenerator == null) {
            myDeltaGenerator = new SVNDeltaGenerator();
        }

        if (myDeltaCombiner == null) {
            myDeltaCombiner = new SVNDeltaCombiner();
        }

        getEditor().openRoot(sourceRevision);

        if ("".equals(getReportTarget())) {
            diffDirs(sourceRevision, fullSourcePath, fullTargetPath, "", info.isStartEmpty());
        } else {
            updateEntry(sourceRevision, fullSourcePath, sourceEntry, fullTargetPath, targetEntry, getReportTarget(), info, true);
        }

        getEditor().closeDir();
        getEditor().closeEdit();
    }

    private void diffDirs(long sourceRevision, String sourcePath, String targetPath, String editPath, boolean startEmpty) throws SVNException {
        diffProplists(sourceRevision, startEmpty == true ? null : sourcePath, editPath, targetPath, null, true);
        Map sourceEntries = null;
        if (sourcePath != null && !startEmpty) {
            FSRevisionRoot sourceRoot = getSourceRoot(sourceRevision);
            FSRevisionNode sourceNode = sourceRoot.getRevisionNode(sourcePath);
            sourceEntries = sourceNode.getDirEntries(myFSFS);
        }
        FSRevisionNode targetNode = getTargetRoot().getRevisionNode(targetPath);

        Map targetEntries = targetNode.getDirEntries(myFSFS);

        while (true) {
            Object[] nextInfo = fetchPathInfo(editPath);
            String entryName = (String) nextInfo[0];
            if (entryName == null) {
                break;
            }
            PathInfo pathInfo = (PathInfo) nextInfo[1];
            if (pathInfo != null && FSRepository.isInvalidRevision(pathInfo.getRevision())) {
                if (sourceEntries != null) {
                    sourceEntries.remove(entryName);
                }
                continue;
            }

            String entryEditPath = SVNPathUtil.append(editPath, entryName);
            String entryTargetPath = SVNPathUtil.concatToAbs(targetPath, entryName);
            FSEntry targetEntry = (FSEntry) targetEntries.get(entryName);
            String entrySourcePath = sourcePath != null ? SVNPathUtil.concatToAbs(sourcePath, entryName) : null;
            FSEntry sourceEntry = sourceEntries != null ? (FSEntry) sourceEntries.get(entryName) : null;
            updateEntry(sourceRevision, entrySourcePath, sourceEntry, entryTargetPath, targetEntry, entryEditPath, pathInfo, isRecursive());
            targetEntries.remove(entryName);

            if (sourceEntries != null) {
                sourceEntries.remove(entryName);
            }
        }

        if (sourceEntries != null) {
            FSEntry[] srcEntries = (FSEntry[]) new ArrayList(sourceEntries.values()).toArray(new FSEntry[sourceEntries.size()]);
            Arrays.sort(srcEntries);
            for (int i = 0; i < srcEntries.length; i++) {
                FSEntry srcEntry = srcEntries[i];
                if (targetEntries.get(srcEntry.getName()) == null) {
                    String entryEditPath = SVNPathUtil.append(editPath, srcEntry.getName());
                    if (isRecursive() || srcEntry.getType() != SVNNodeKind.DIR) {
                        getEditor().deleteEntry(entryEditPath, FSRepository.SVN_INVALID_REVNUM);
                    }
                }
            }
        }

        FSEntry[] tgtEntries = (FSEntry[]) new ArrayList(targetEntries.values()).toArray(new FSEntry[targetEntries.size()]);
        final Map srcMap = sourceEntries;
        Arrays.sort(tgtEntries, new Comparator() {
            public int compare(Object o1, Object o2) {
                FSEntry e1 = (FSEntry) o1;
                FSEntry e2 = (FSEntry) o2;
                if (srcMap != null) {
                    boolean has1Src = srcMap.containsKey(e1.getName()); 
                    boolean has2Src = srcMap.containsKey(e2.getName());
                    if (has1Src != has2Src) {
                        return has1Src ? 1 : -1;
                    }
                }
                return e1.compareTo(e2);
            }
        });
        for (int i = 0; i < tgtEntries.length; i++) {
            FSEntry tgtEntry = tgtEntries[i];
            String entryEditPath = SVNPathUtil.append(editPath, tgtEntry.getName());
            String entryTargetPath = SVNPathUtil.concatToAbs(targetPath, tgtEntry.getName());
            FSEntry srcEntry = sourceEntries != null ? (FSEntry) sourceEntries.get(tgtEntry.getName()) : null;
            String entrySourcePath = srcEntry != null ? SVNPathUtil.concatToAbs(sourcePath, tgtEntry.getName()) : null;
            updateEntry(sourceRevision, entrySourcePath, srcEntry, entryTargetPath, tgtEntry, entryEditPath, null, isRecursive());
        }
    }

    private void diffFiles(long sourceRevision, String sourcePath, String targetPath, String editPath, String lockToken) throws SVNException {
        diffProplists(sourceRevision, sourcePath, editPath, targetPath, lockToken, false);
        String sourceHexDigest = null;
        FSRevisionRoot sourceRoot = null;
        if (sourcePath != null) {
            sourceRoot = getSourceRoot(sourceRevision);

            boolean changed = false;
            if (isIgnoreAncestry()) {
                changed = checkFilesDifferent(sourceRoot, sourcePath, getTargetRoot(), targetPath);
            } else {
                changed = FSRepositoryUtil.areFileContentsChanged(sourceRoot, sourcePath, getTargetRoot(), targetPath);
            }
            if (!changed) {
                return;
            }
            FSRevisionNode sourceNode = sourceRoot.getRevisionNode(sourcePath);
            sourceHexDigest = sourceNode.getFileChecksum();
        }

        getEditor().applyTextDelta(editPath, sourceHexDigest);

        if (isSendTextDeltas()) {
            InputStream sourceStream = null;
            InputStream targetStream = null;
            try {
                if (sourceRoot != null && sourcePath != null) {
                    sourceStream = sourceRoot.getFileStreamForPath(myDeltaCombiner, sourcePath);
                } else {
                    sourceStream = FSInputStream.createDeltaStream(myDeltaCombiner, (FSRevisionNode) null, myFSFS);
                }
                //TODO: not sure whether we can use the same combiner here
                targetStream = getTargetRoot().getFileStreamForPath(myDeltaCombiner, targetPath);
                myDeltaGenerator.sendDelta(editPath, sourceStream, 0, targetStream, getEditor(), false);
            } finally {
                SVNFileUtil.closeFile(sourceStream);
                SVNFileUtil.closeFile(targetStream);
            }
        } else {
            getEditor().textDeltaEnd(editPath);
        }
    }

    private boolean checkFilesDifferent(FSRoot root1, String path1, FSRoot root2, String path2) throws SVNException {
        boolean changed = FSRepositoryUtil.areFileContentsChanged(root1, path1, root2, path2);
        if (!changed) {
            return false;
        }

        FSRevisionNode revNode1 = root1.getRevisionNode(path1);
        FSRevisionNode revNode2 = root2.getRevisionNode(path2);
        if (revNode1.getFileLength() != revNode2.getFileLength()) {
            return true;
        }

        if (!revNode1.getFileChecksum().equals(revNode2.getFileChecksum())) {
            return true;
        }

        InputStream file1IS = null;
        InputStream file2IS = null;
        try {
            file1IS = root1.getFileStreamForPath(myDeltaCombiner, path1);
            file2IS = root2.getFileStreamForPath(myDeltaCombiner, path2);

            int r1 = -1;
            int r2 = -1;
            while (true) {
                r1 = file1IS.read();
                r2 = file2IS.read();
                if (r1 != r2) {
                    return true;
                }
                if (r1 == -1) {// we've finished - files do not differ
                    break;
                }
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } finally {
            SVNFileUtil.closeFile(file1IS);
            SVNFileUtil.closeFile(file2IS);
        }
        return false;
    }

    private void updateEntry(long sourceRevision, String sourcePath, FSEntry sourceEntry, String targetPath, FSEntry targetEntry, String editPath, PathInfo pathInfo, boolean recursive)
            throws SVNException {
        if (pathInfo != null && pathInfo.getLinkPath() != null && !isSwitch()) {
            targetPath = pathInfo.getLinkPath();
            targetEntry = fakeDirEntry(targetPath, getTargetRoot());
        }

        if (pathInfo != null && FSRepository.isInvalidRevision(pathInfo.getRevision())) {
            sourcePath = null;
            sourceEntry = null;
        } else if (pathInfo != null && sourcePath != null) {
            sourcePath = pathInfo.getLinkPath() != null ? pathInfo.getLinkPath() : sourcePath;
            sourceRevision = pathInfo.getRevision();
            FSRevisionRoot srcRoot = getSourceRoot(sourceRevision);
            sourceEntry = fakeDirEntry(sourcePath, srcRoot);
        }

        if (sourcePath != null && sourceEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Working copy path ''{0}'' does not exist in repository", editPath);
            SVNErrorManager.error(err);
        }

        if (!recursive && ((sourceEntry != null && sourceEntry.getType() == SVNNodeKind.DIR) || (targetEntry != null && targetEntry.getType() == SVNNodeKind.DIR))) {
            skipPathInfo(editPath);
            return;
        }
        boolean related = false;

        if (sourceEntry != null && targetEntry != null && sourceEntry.getType() == targetEntry.getType()) {
            int distance = sourceEntry.getId().compareTo(targetEntry.getId());
            if (distance == 0 && !PathInfo.isRelevant(getCurrentPathInfo(), editPath) && (pathInfo == null || (!pathInfo.isStartEmpty() && pathInfo.getLockToken() == null))) {
                return;
            } else if (distance != -1 || isIgnoreAncestry()) {
                related = true;
            }
        }

        if (sourceEntry != null && !related) {
            getEditor().deleteEntry(editPath, FSRepository.SVN_INVALID_REVNUM);
            sourcePath = null;
        }

        if (targetEntry == null) {
            skipPathInfo(editPath);
            return;
        }

        if (targetEntry.getType() == SVNNodeKind.DIR) {
            if (related) {
                getEditor().openDir(editPath, sourceRevision);
            } else {
                getEditor().addDir(editPath, null, FSRepository.SVN_INVALID_REVNUM);
            }
            diffDirs(sourceRevision, sourcePath, targetPath, editPath, pathInfo != null ? pathInfo.isStartEmpty() : false);
            getEditor().closeDir();
        } else {
            SVNDebugLog.getDefaultLog().info("processing file: " + editPath);
            if (related) {
                getEditor().openFile(editPath, sourceRevision);
            } else {
                getEditor().addFile(editPath, null, FSRepository.SVN_INVALID_REVNUM);
            }
            diffFiles(sourceRevision, sourcePath, targetPath, editPath, pathInfo != null ? pathInfo.getLockToken() : null);
            FSRevisionNode targetNode = getTargetRoot().getRevisionNode(targetPath);
            String targetHexDigest = targetNode.getFileChecksum();
            getEditor().closeFile(editPath, targetHexDigest);
        }
    }

    private void diffProplists(long sourceRevision, String sourcePath, String editPath, String targetPath, String lockToken, boolean isDir) throws SVNException {
        FSRevisionNode targetNode = getTargetRoot().getRevisionNode(targetPath);
        long createdRevision = targetNode.getId().getRevision();

        if (FSRepository.isValidRevision(createdRevision)) {
            Map entryProps = myFSFS.compoundMetaProperties(createdRevision);
            changeProperty(editPath, SVNProperty.COMMITTED_REVISION, (String) entryProps.get(SVNProperty.COMMITTED_REVISION), isDir);
            String committedDate = (String) entryProps.get(SVNProperty.COMMITTED_DATE);

            if (committedDate != null || sourcePath != null) {
                changeProperty(editPath, SVNProperty.COMMITTED_DATE, committedDate, isDir);
            }

            String lastAuthor = (String) entryProps.get(SVNProperty.LAST_AUTHOR);

            if (lastAuthor != null || sourcePath != null) {
                changeProperty(editPath, SVNProperty.LAST_AUTHOR, lastAuthor, isDir);
            }

            String uuid = (String) entryProps.get(SVNProperty.UUID);

            if (uuid != null || sourcePath != null) {
                changeProperty(editPath, SVNProperty.UUID, uuid, isDir);
            }
        }

        if (lockToken != null) {
            SVNLock lock = myFSFS.getLockHelper(targetPath, false);
            if (lock == null || !lockToken.equals(lock.getID())) {
                changeProperty(editPath, SVNProperty.LOCK_TOKEN, null, isDir);
            }
        }

        Map sourceProps = null;
        if (sourcePath != null) {
            FSRevisionRoot sourceRoot = getSourceRoot(sourceRevision);
            FSRevisionNode sourceNode = sourceRoot.getRevisionNode(sourcePath);
            boolean propsChanged = !FSRepositoryUtil.arePropertiesEqual(sourceNode, targetNode);
            if (!propsChanged) {
                return;
            }
            sourceProps = sourceNode.getProperties(myFSFS);
        } else {
            sourceProps = new HashMap();
        }

        Map targetProps = targetNode.getProperties(myFSFS);
        Map propsDiffs = FSRepositoryUtil.getPropsDiffs(sourceProps, targetProps);
        Object[] names = propsDiffs.keySet().toArray();
        for (int i = 0; i < names.length; i++) {
            String propName = (String) names[i];
            changeProperty(editPath, propName, (String) propsDiffs.get(propName), isDir);
        }
    }

    private Object[] fetchPathInfo(String prefix) throws SVNException {
        Object[] result = new Object[2];
        PathInfo pathInfo = getCurrentPathInfo();
        if (!PathInfo.isRelevant(pathInfo, prefix)) {
            result[0] = null;
            result[1] = null;
        } else {
            String relPath = "".equals(prefix) ? pathInfo.getPath() : pathInfo.getPath().substring(prefix.length() + 1);
            if (relPath.indexOf('/') != -1) {
                result[0] = relPath.substring(0, relPath.indexOf('/'));
                result[1] = null;
            } else {
                result[0] = relPath;
                result[1] = pathInfo;
                try {
                    getNextPathInfo();
                } catch (IOException ioe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                    SVNErrorManager.error(err);
                }
            }
        }
        return result;
    }

    private void changeProperty(String path, String name, String value, boolean isDir) throws SVNException {
        if (isDir) {
            getEditor().changeDirProperty(name, value);
        } else {
            getEditor().changeFileProperty(path, name, value);
        }
    }

    private FSEntry fakeDirEntry(String reposPath, FSRevisionRoot root) throws SVNException {
        if (root.checkNodeKind(reposPath) == SVNNodeKind.NONE) {
            return null;
        }

        FSRevisionNode node = root.getRevisionNode(reposPath);
        FSEntry dirEntry = new FSEntry(node.getId(), node.getType(), SVNPathUtil.tail(node.getCreatedPath()));
        return dirEntry;
    }

    private void skipPathInfo(String prefix) throws SVNException {
        while (PathInfo.isRelevant(getCurrentPathInfo(), prefix)) {
            try {
                getNextPathInfo();
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err);
            }
        }
    }

    public void writePathInfoToReportFile(String path, String linkPath, String lockToken, long revision, boolean startEmpty) throws SVNException {
        String anchorRelativePath = SVNPathUtil.append(getReportTarget(), path);
        String linkPathRep = linkPath != null ? "+" + linkPath.length() + ":" + linkPath : "-";
        String revisionRep = FSRepository.isValidRevision(revision) ? "+" + revision + ":" : "-";
        String lockTokenRep = lockToken != null ? "+" + lockToken.length() + ":" + lockToken : "-";
        String startEmptyRep = startEmpty ? "+" : "-";
        String fullRepresentation = "+" + anchorRelativePath.length() + ":" + anchorRelativePath + linkPathRep + revisionRep + startEmptyRep + lockTokenRep;

        try {
            OutputStream reportOS = getReportFileForWriting();
            reportOS.write(fullRepresentation.getBytes("UTF-8"));
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }

}
