/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNMergeInfoManager;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class FSFileRevisionsFinder {
    private FSFS myFSFS;
    
    public FSFileRevisionsFinder(FSFS fsfs) {
        myFSFS = fsfs;
    }
    
    public int getFileRevisions(String path, long startRevision, long endRevision, 
                                boolean includeMergedRevisions, ISVNFileRevisionHandler handler) throws SVNException {
     
        LinkedList pathRevisions = findInterestingRevisions(null, path, startRevision, endRevision, 
                                                            includeMergedRevisions, false);
        
        if (includeMergedRevisions) {
            pathRevisions = sortAndScrubRevisions(pathRevisions);
        }
       
        SVNDebugLog.assertCondition(!pathRevisions.isEmpty(), "ASSERTION FAILURE in FSFileRevisionsFinder: pathRevisions is empty");

        FSRoot lastRoot = null;
        String lastPath = null;
        Map lastProps = new HashMap();
        for (ListIterator locations = pathRevisions.listIterator(); locations.hasNext();) {
            SVNLocationEntry pathRevision = (SVNLocationEntry) locations.next();
            long rev = pathRevision.getRevision();
            String revPath = pathRevision.getPath();
            
            Map revProps = myFSFS.getRevisionProperties(rev);
            FSRevisionRoot root = myFSFS.createRevisionRoot(rev);
            FSRevisionNode fileNode = root.getRevisionNode(revPath);
            Map props = fileNode.getProperties(myFSFS);
            Map propDiffs = FSRepositoryUtil.getPropsDiffs(lastProps, props);
            boolean contentsChanged = false;
            if (lastRoot != null) {
                contentsChanged = FSRepositoryUtil.areFileContentsChanged(lastRoot, lastPath, root, revPath);
            } else {
                contentsChanged = true;
            }

            if (handler != null) {
                handler.openRevision(new SVNFileRevision(revPath, rev, revProps, propDiffs, 
                                                         pathRevision.isResultOfMerge()));
                if (contentsChanged) {
                    SVNDeltaCombiner sourceCombiner = new SVNDeltaCombiner();
                    SVNDeltaCombiner targetCombiner = new SVNDeltaCombiner();
                    handler.applyTextDelta(path, null);
                    InputStream sourceStream = null;
                    InputStream targetStream = null;
                    try {
                        if (lastRoot != null && lastPath != null) {
                            sourceStream = lastRoot.getFileStreamForPath(sourceCombiner, lastPath);
                        } else {
                            sourceStream = FSInputStream.createDeltaStream(sourceCombiner, (FSRevisionNode) null, myFSFS);
                        }
                        targetStream = root.getFileStreamForPath(targetCombiner, revPath);
                        SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
                        deltaGenerator.sendDelta(path, sourceStream, 0, targetStream, handler, false);
                    } finally {
                        SVNFileUtil.closeFile(sourceStream);
                        SVNFileUtil.closeFile(targetStream);
                    }
                    handler.closeRevision(path);
                } else {
                    handler.closeRevision(path);
                }
            }
            lastRoot = root;
            lastPath = revPath;
            lastProps = props;
        }

        return pathRevisions.size();
    }
    
    private LinkedList sortAndScrubRevisions(LinkedList pathRevisions) {
        Collections.sort(pathRevisions, new Comparator() {
            public int compare(Object arg0, Object arg1) {
                SVNLocationEntry pathRevision1 = (SVNLocationEntry) arg0;
                SVNLocationEntry pathRevision2 = (SVNLocationEntry) arg1;
                
                if (pathRevision1.getRevision() == pathRevision2.getRevision()) {
                    String path1 = pathRevision1.getPath();
                    String path2 = pathRevision2.getPath();
                    int pathCompare = path1.compareTo(path2);
                    if (pathCompare == 0) {
                        if (pathRevision1.isResultOfMerge() == pathRevision2.isResultOfMerge()) {
                            return 0;
                        }
                        return pathRevision1.isResultOfMerge() ? 1 : -1; 
                    }
                    return pathCompare;
                }
                return pathRevision1.getRevision() < pathRevision2.getRevision() ? 1 : -1;
            }
        });
        
        SVNLocationEntry previousPathRevision = new SVNLocationEntry(0, null, false);
        LinkedList outPathRevisions = new LinkedList();
        for (Iterator entries = pathRevisions.iterator(); entries.hasNext();) {
            SVNLocationEntry pathRevision = (SVNLocationEntry) entries.next();
            if (previousPathRevision.getRevision() != pathRevision.getRevision() ||
                !previousPathRevision.getPath().equals(pathRevision.getPath())) {
                outPathRevisions.addFirst(pathRevision);
            }
            previousPathRevision = pathRevision;
        }
        
        return outPathRevisions;
    }
    
    private LinkedList findInterestingRevisions(LinkedList pathRevisions, String path, 
                                                long startRevision, long endRevision, 
                                                boolean includeMergedRevisions, 
                                                boolean markAsMerged) throws SVNException {

        FSRevisionRoot root = myFSFS.createRevisionRoot(endRevision);
        if (root.checkNodeKind(path) != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, 
                                                         "''{0}'' is not a file in revision ''{1,number,integer}''", 
                                                         new Object[] { path, new Long(endRevision) });
            SVNErrorManager.error(err);
        }

        pathRevisions = pathRevisions == null ? new LinkedList() : pathRevisions;
        FSNodeHistory history = root.getNodeHistory(path);
        while (true) {
            history = history.getPreviousHistory(true);
            if (history == null) {
                break;
            }

            long histRev = history.getHistoryEntry().getRevision();
            String histPath = history.getHistoryEntry().getPath();
            SVNLocationEntry pathRev = new SVNLocationEntry(histRev, histPath, markAsMerged); 
            pathRevisions.addFirst(pathRev);

            if (includeMergedRevisions) {
                getMergedPathRevisions(pathRevisions, pathRev);
                FSRevisionRoot mergeRoot = myFSFS.createRevisionRoot(pathRev.getRevision());
                boolean isBranching = FSLog.isBranchingCopy(mergeRoot, null, pathRev.getPath());
                if (isBranching) {
                    break;
                }
            }

            if (histRev <= startRevision) {
                break;
            }
        }
        return pathRevisions;
    }

    private void getMergedPathRevisions(LinkedList pathRevisions, 
                                        SVNLocationEntry oldPathRevision) throws SVNException {

        FSRevisionRoot root = myFSFS.createRevisionRoot(oldPathRevision.getRevision());
        Map currentMergeInfo = FSLog.getPathMergeInfo(oldPathRevision.getPath(), 
                                                      root);
        FSRevisionRoot previousRoot = myFSFS.createRevisionRoot(oldPathRevision.getRevision() - 1);
        Map previousMergeInfo = FSLog.getPathMergeInfo(oldPathRevision.getPath(), 
                                                       previousRoot);


        Map deleted = new TreeMap();
        Map changed = new TreeMap();
        SVNMergeInfoManager.diffMergeInfo(deleted, changed, previousMergeInfo, currentMergeInfo, false);

        changed = SVNMergeInfoManager.mergeMergeInfos(changed, deleted);
        if (changed.isEmpty()) {
            return;
        }

        for (Iterator mergeSrcs = changed.keySet().iterator(); mergeSrcs.hasNext();) {
            String path = (String) mergeSrcs.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) changed.get(path);
            SVNMergeRange[] ranges = rangeList.getRanges();
            for (int i = 0; i < rangeList.getSize(); i++) {
                SVNMergeRange range = ranges[i];
                try {
                    pathRevisions = findInterestingRevisions(pathRevisions, path, range.getStartRevision(), 
                                                             range.getEndRevision(), true, true);
                } catch (SVNException svne) {
                    if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.FS_NOT_FILE) {
                        throw svne;
                    }
                }
            }
        }
    }

}
