/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.internal.util.*;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class FSNodeHistory {

    private SVNLocationEntry historyEntry;

    private SVNLocationEntry searchResumeEntry;

    private boolean isInteresting;

    public FSNodeHistory(SVNLocationEntry newHistoryEntry, boolean interest, SVNLocationEntry newSearchResumeEntry) {
        historyEntry = newHistoryEntry;
        searchResumeEntry = newSearchResumeEntry;
        isInteresting = interest;
    }

    public SVNLocationEntry getHistoryEntry() {
        return historyEntry;
    }

    public void setHistoryEntry(SVNLocationEntry newHistoryEntry) {
        historyEntry = newHistoryEntry;
    }

    public SVNLocationEntry getSearchResumeEntry() {
        return searchResumeEntry;
    }

    public void setHintsEntry(SVNLocationEntry newSearchResumeEntry) {
        searchResumeEntry = newSearchResumeEntry;
    }

    public boolean isInteresting() {
        return isInteresting;
    }

    public void setInterest(boolean someInterest) {
        isInteresting = someInterest;
    }

    public static SVNLocationEntry findYoungestCopyroot(File reposRootDir, FSParentPath parPath) throws SVNException {
        SVNLocationEntry parentEntry = null;
        if (parPath.getParent() != null) {
            parentEntry = findYoungestCopyroot(reposRootDir, parPath.getParent());
        }

        SVNLocationEntry myEntry = new SVNLocationEntry(parPath.getRevNode().getCopyRootRevision(), parPath.getRevNode().getCopyRootPath());
        if (parentEntry != null) {
            if (myEntry.getRevision() >= parentEntry.getRevision()) {
                return myEntry;
            }
            return parentEntry;
        }
        return myEntry;
    }

    public static boolean checkAncestryOfPegPath(String fsPath, long pegRev, long futureRev, FSFS owner) throws SVNException {
        FSRevisionRoot root = owner.createRevisionRoot(futureRev);
        FSNodeHistory history = getNodeHistory(root, fsPath);
        fsPath = null;
        SVNLocationEntry currentHistory = null;
        while (true) {
            history = history.fsHistoryPrev(true, owner);
            if (history == null) {
                break;
            }
            currentHistory = new SVNLocationEntry(history.getHistoryEntry().getRevision(), history.getHistoryEntry().getPath());
            if (fsPath == null) {
                fsPath = currentHistory.getPath();
            }
            if (currentHistory.getRevision() <= pegRev) {
                break;
            }
        }

        if (fsPath == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "FATAL error occurred while checking ancestry of peg path");
            SVNErrorManager.error(err);
        }
        return (history != null && (fsPath.equals(currentHistory.getPath())));
    }

    public static FSNodeHistory getNodeHistory(FSRevisionRoot root, String path) throws SVNException {
        FSRevisionNode node = null;
        SVNNodeKind kind = null;
        try {
            node = root.openPath(path, true, false).getRevNode(); 
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_FOUND) {
                kind = SVNNodeKind.NONE;
            }
            throw svne;
        }

        kind = node.getType();
        if (kind == SVNNodeKind.NONE) {
            SVNErrorManager.error(FSErrors.errorNotFound(root, path));
        }
        return new FSNodeHistory(new SVNLocationEntry(root.getRevision(), path), false, new SVNLocationEntry(FSRepository.SVN_INVALID_REVNUM, null));
    }

    private FSNodeHistory historyPrev(boolean crossCopies, FSFS owner) throws SVNException {
        String path = historyEntry.getPath();
        long revision = historyEntry.getRevision();
        boolean reported = isInteresting;

        if (searchResumeEntry != null && searchResumeEntry.getPath() != null && FSRepository.isValidRevision(searchResumeEntry.getRevision())) {
            reported = false;
            if (!crossCopies) {
                return null;
            }
            path = searchResumeEntry.getPath();
            revision = searchResumeEntry.getRevision();
        }

        FSRevisionRoot root = owner.createRevisionRoot(revision);
        FSParentPath parentPath = root.openPath(path, true, true);
        FSRevisionNode revNode = parentPath.getRevNode();
        SVNLocationEntry commitEntry = new SVNLocationEntry(revNode.getId().getRevision(), revNode.getCreatedPath());

        FSNodeHistory prevHist = null;
        if (revision == commitEntry.getRevision()) {
            if (!reported) {
                prevHist = new FSNodeHistory(commitEntry, true, new SVNLocationEntry(FSRepository.SVN_INVALID_REVNUM, null));
                return prevHist;
            }
            FSID predId = revNode.getPredecessorId();
            if (predId == null) {
                return prevHist;
            }
            revNode = owner.getRevisionNode(predId);
            commitEntry = new SVNLocationEntry(revNode.getId().getRevision(), revNode.getCreatedPath());
        }
        SVNLocationEntry copyrootEntry = findYoungestCopyroot(owner.getRepositoryRoot(), parentPath);
        SVNLocationEntry srcEntry = new SVNLocationEntry(FSRepository.SVN_INVALID_REVNUM, null);
        long dstRev = FSRepository.SVN_INVALID_REVNUM;
        if (copyrootEntry.getRevision() > commitEntry.getRevision()) {
            FSRevisionRoot copyrootRoot = owner.createRevisionRoot(copyrootEntry.getRevision());
            revNode = copyrootRoot.getRevisionNode(copyrootEntry.getPath());
            String copyDst = revNode.getCreatedPath();
            String reminder = null;
            if (path.equals(copyDst)) {
                reminder = "";
            } else {
                reminder = SVNPathUtil.pathIsChild(copyDst, path);
            }
            if (reminder != null) {
                String copySrc = revNode.getCopyFromPath();
                srcEntry = new SVNLocationEntry(revNode.getCopyFromRevision(), SVNPathUtil.concatToAbs(copySrc, reminder));
                dstRev = copyrootEntry.getRevision();
            }
        }
        if (srcEntry.getPath() != null && FSRepository.isValidRevision(srcEntry.getRevision())) {
            boolean retry = false;
            if ((dstRev == revision) && reported) {
                retry = true;
            }
            return new FSNodeHistory(new SVNLocationEntry(dstRev, path), retry ? false : true, new SVNLocationEntry(srcEntry.getRevision(), srcEntry.getPath()));
        }
        return new FSNodeHistory(commitEntry, true, new SVNLocationEntry(FSRepository.SVN_INVALID_REVNUM, null));
    }

    public FSNodeHistory fsHistoryPrev(boolean crossCopies, FSFS owner) throws SVNException {
        if ("/".equals(historyEntry.getPath())) {
            if (!isInteresting) {
                return new FSNodeHistory(new SVNLocationEntry(historyEntry.getRevision(), "/"), true, new SVNLocationEntry(FSRepository.SVN_INVALID_REVNUM, null));
            } else if (historyEntry.getRevision() > 0) {
                return new FSNodeHistory(new SVNLocationEntry(historyEntry.getRevision() - 1, "/"), true, new SVNLocationEntry(FSRepository.SVN_INVALID_REVNUM, null));
            }
        } else {
            FSNodeHistory prevHist = this;
            while (true) {
                prevHist = prevHist.historyPrev(crossCopies, owner);
                if (prevHist == null) {
                    return null;
                }
                if (prevHist.isInteresting) {
                    return prevHist;
                }
            }
        }
        return null;
    }
}
