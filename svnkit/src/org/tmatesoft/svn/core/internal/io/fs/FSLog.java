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

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.wc.SVNMergeInfoManager;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class FSLog {
    private static final int MAX_OPEN_HISTORIES = 128;  
    
    private FSFS myFSFS;
    private String[] myPaths;
    private boolean myIsDescending;
    private boolean myIsDiscoverChangedPaths;
    private boolean myIsStrictNode;
    private boolean myIsIncludeMergedRevisions;
    private long myStartRevision;
    private long myEndRevision;
    private long myLimit;
    private ISVNLogEntryHandler myHandler;
    private SVNMergeInfoManager myMergeInfoManager;
    private String[] myRevPropNames;
    
    public FSLog(FSFS owner, String[] paths, long limit, long start, long end, boolean descending, 
            boolean discoverChangedPaths, boolean strictNode, boolean includeMergedRevisions, 
            String[] revPropNames, ISVNLogEntryHandler handler) {
        myFSFS = owner;
        myPaths = paths;
        myStartRevision = start;
        myEndRevision = end;
        myIsDescending = descending;
        myIsDiscoverChangedPaths = discoverChangedPaths;
        myIsStrictNode = strictNode;
        myIsIncludeMergedRevisions = includeMergedRevisions;
        myRevPropNames = revPropNames;
        myLimit = limit;
        myHandler = handler;
    }
    
    public void reset(FSFS owner, String[] paths, long limit, long start, long end, boolean descending, 
            boolean discoverChangedPaths, boolean strictNode, boolean includeMergedRevisions, 
            String[] revPropNames, ISVNLogEntryHandler handler) {
        myFSFS = owner;
        myPaths = paths;
        myStartRevision = start;
        myEndRevision = end;
        myIsDescending = descending;
        myIsDiscoverChangedPaths = discoverChangedPaths;
        myIsStrictNode = strictNode;
        myIsIncludeMergedRevisions = includeMergedRevisions;
        myRevPropNames = revPropNames;
        myLimit = limit;
        myHandler = handler;
    }
    
    public long runLog() throws SVNException {
        long count = 0;
        if (!myIsIncludeMergedRevisions && myPaths.length == 1 && "/".equals(myPaths[0])) {
            count = myEndRevision - myStartRevision + 1;
            if (myLimit > 0 && count > myLimit) {
                count = myLimit;
            }
        
            for (int i = 0; i < count; i++) {
                long rev = myStartRevision + i;
                if (myIsDescending) {
                    rev = myEndRevision - i;
                }
                sendLog(rev, false);
            }
            
            return count;
        }

        return doLogs(myPaths, myStartRevision, myEndRevision, myIsIncludeMergedRevisions, myIsDescending, 
                myLimit);
    }
    
    private long doLogs(String[] paths, long startRevision, long endRevision, boolean includeMergedRevisions, 
            boolean isDescendingOrder, long limit) throws SVNException {
        long sendCount = 0;
        PathInfo[] histories = getPathHistories(paths, startRevision, endRevision, myIsStrictNode);
        
        LinkedList revisions = null;
        Map revMergeInfo = null;
        boolean anyHistoriesLeft = true;
        for (long currentRev = endRevision; anyHistoriesLeft; currentRev = getNextHistoryRevision(histories)) {
            
            boolean changed = false;
            anyHistoriesLeft = false;
            
            for (int i = 0; i < histories.length; i++) {
                PathInfo info = histories[i];
                changed = info.checkHistory(currentRev, myIsStrictNode, startRevision, changed);
                
                if (!info.myIsDone) {
                    anyHistoriesLeft = true;
                }
            }

            if (changed) {
                boolean hasChildren = false;
                Map mergeInfo = null;
                if (includeMergedRevisions) {
                    LinkedList currentPaths = new LinkedList();
                    for (int i = 0; i < histories.length; i++) {
                        PathInfo info = histories[i];
                        currentPaths.add(info.myPath);
                    }
                    mergeInfo = getMergedRevisionMergeInfo((String[]) currentPaths.toArray(new String[currentPaths.size()]), 
                            currentRev);
                    hasChildren = !mergeInfo.isEmpty();
                }
                
                if (isDescendingOrder) {
                    sendLog(currentRev, hasChildren);
                    sendCount++;
                    if (hasChildren) {
                        handleMergedRevisions(currentRev, mergeInfo);
                    }
                    if (limit > 0 && sendCount >= limit) {
                        break;
                    }
                } else {
                    if (revisions == null) {
                        revisions = new LinkedList();
                    }
                    revisions.addLast(new Long(currentRev));
                    
                    if (mergeInfo != null) {
                        if (revMergeInfo == null) {
                            revMergeInfo = new TreeMap();
                        }
                        revMergeInfo.put(new Long(currentRev), mergeInfo);
                    }
                }
            }
        }

        if (revisions != null) {
            for (int i = 0; i < revisions.size(); i++) {
                boolean hasChildren = false;
                Map mergeInfo = null;
                long rev = ((Long) revisions.get(revisions.size() - i - 1)).longValue();
                
                if (revMergeInfo != null) {
                    mergeInfo = (Map) revMergeInfo.get(new Long(rev));
                    hasChildren = mergeInfo != null && !mergeInfo.isEmpty();
                }
                sendLog(rev, hasChildren);
                if (hasChildren) {
                    handleMergedRevisions(rev, mergeInfo);
                }
                sendCount++;
                if (limit > 0 && sendCount >= limit) {
                    break;
                }
            }
        }
        return sendCount;
    }
    
    private long getNextHistoryRevision(PathInfo[] histories) {
        long nextRevision = SVNRepository.INVALID_REVISION;
        for (int i = 0; i < histories.length; i++) {
            PathInfo info = histories[i];
            if (info.myIsDone) {
                continue;
            }
            if (info.myHistoryRevision > nextRevision) {
                nextRevision = info.myHistoryRevision;
            }
        }
        return nextRevision;
    }

    private void sendLog(long revision, boolean hasChildren) throws SVNException {
        SVNLogEntry logEntry = fillLogEntry(revision);
        logEntry.setHasChildren(hasChildren);
        if (myHandler != null) {
            myHandler.handleLogEntry(logEntry);
        }
    }

    private SVNLogEntry fillLogEntry(long revision) throws SVNException {
        Map changedPaths = null;
        SVNProperties entryRevProps = null;
        boolean getRevProps = true;
        boolean censorRevProps = false;
        if (revision > 0 && myIsDiscoverChangedPaths) {
            FSRevisionRoot root = myFSFS.createRevisionRoot(revision);
            changedPaths = root.detectChanged();
        }

        //TODO: add autz check code later
        if (getRevProps) {
            SVNProperties revisionProps = myFSFS.getRevisionProperties(revision);

            if (revisionProps != null) {
                String author = revisionProps.getStringValue(SVNRevisionProperty.AUTHOR);
                String datestamp = revisionProps.getStringValue(SVNRevisionProperty.DATE);
                Date date = datestamp != null ? SVNDate.parseDateString(datestamp) : null;

                if (myRevPropNames == null || myRevPropNames.length == 0) {
                    if (censorRevProps) {
                        entryRevProps = new SVNProperties();
                        if (author != null) {
                            entryRevProps.put(SVNRevisionProperty.AUTHOR, author);
                        }
                        if (date != null) {
                            entryRevProps.put(SVNRevisionProperty.DATE, SVNDate.formatDate(date));
                        }
                    } else {
                        entryRevProps = revisionProps;
                        if (date != null) {
                            entryRevProps.put(SVNRevisionProperty.DATE, SVNDate.formatDate(date));
                        }
                    }
                } else {
                    for (int i = 0; i < myRevPropNames.length; i++) {
                        String propName = myRevPropNames[i];
                        SVNPropertyValue propVal = revisionProps.getSVNPropertyValue(propName);
                        if (censorRevProps && !SVNRevisionProperty.AUTHOR.equals(propName) &&
                                !SVNRevisionProperty.DATE.equals(propName)) {
                            continue;
                        }
                        if (entryRevProps == null) {
                            entryRevProps = new SVNProperties();
                        }
                        if (SVNRevisionProperty.DATE.equals(propName) && date != null) {
                            entryRevProps.put(propName, SVNDate.formatDate(date));
                        } else if (propVal != null) {
                            entryRevProps.put(propName, propVal);
                        }
                    }
                }
            }
        }
        
        if (changedPaths == null) {
            changedPaths = new SVNHashMap();
        }
        if (entryRevProps == null) {
            entryRevProps = new SVNProperties();
        }
        SVNLogEntry entry = new SVNLogEntry(changedPaths, revision, entryRevProps, false);
        return entry;
    }
    
    private void handleMergedRevisions(long revision, Map mergeInfo) throws SVNException {
        if (mergeInfo == null || mergeInfo.isEmpty()) {
            return;
        }
        
        LinkedList combinedList = combineMergeInfoPathLists(mergeInfo);
        for (int i = combinedList.size() - 1; i >= 0; i--) {
            PathListRange pathListRange = (PathListRange) combinedList.get(i);
            try {
                doLogs(pathListRange.myPaths, pathListRange.myRange.getStartRevision(), 
                        pathListRange.myRange.getEndRevision(), true, true, 0);
            } catch (SVNException svne) {
                SVNErrorCode errCode = svne.getErrorMessage().getErrorCode();
                if (errCode == SVNErrorCode.FS_NOT_FOUND || errCode == SVNErrorCode.FS_NO_SUCH_REVISION) {
                    continue;
                }
                throw svne;
            }
        }
        if (myHandler != null) {
            myHandler.handleLogEntry(SVNLogEntry.EMPTY_ENTRY);
        }
    }

    private PathInfo[] getPathHistories(String[] paths, long start, long end, boolean strictNodeHistory) throws SVNException {
        PathInfo[] histories = new PathInfo[paths.length];
        FSRevisionRoot root = myFSFS.createRevisionRoot(end);
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            
            PathInfo pathHistory = new PathInfo();
            pathHistory.myPath = path;
            pathHistory.myHistoryRevision = end;
            pathHistory.myIsDone = false;
            pathHistory.myIsFirstTime = true;
            
            if (i < MAX_OPEN_HISTORIES) {
                pathHistory.myHistory = root.getNodeHistory(path);
            }
            
            histories[i] = pathHistory.getHistory(strictNodeHistory, start);
        }
        return histories;
    }
    
    private Map getMergedRevisionMergeInfo(String[] paths, long revision) throws SVNException {
        if (revision == 0) {
            return new TreeMap();
        }
        
        Map currentMergeInfo = getCombinedMergeInfo(paths, revision);  
        Map previousMergeInfo = getCombinedMergeInfo(paths, revision - 1);
        Map deleted = new TreeMap();
        Map changed = new TreeMap();
        SVNMergeInfoUtil.diffMergeInfo(deleted, changed, previousMergeInfo, currentMergeInfo, false);
        changed = SVNMergeInfoUtil.mergeMergeInfos(changed, deleted);
        return changed;
    }
    
    private Map getCombinedMergeInfo(String[] paths, long revision) throws SVNException {
        if (revision == 0) {
            return new TreeMap();
        }
        FSRevisionRoot root = myFSFS.createRevisionRoot(revision);

        List existingPaths = new LinkedList();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            SVNNodeKind kind = root.checkNodeKind(path);
            if (kind == SVNNodeKind.NONE) {
                FSRevisionRoot revRoot = myFSFS.createRevisionRoot(revision + 1);
                FSRevisionNode revNode = revRoot.getRevisionNode(path);
                String copyPath = revNode.getCopyFromPath();
                if (copyPath != null) {
                    existingPaths.add(copyPath);
                }
            } else {
                existingPaths.add(path);
            }
        }
        
        String[] queryPaths = (String[]) existingPaths.toArray(new String[existingPaths.size()]);
        SVNMergeInfoManager mergeInfoManager = getMergeInfoManager(); 
        Map treeMergeInfo = mergeInfoManager.getMergeInfo(queryPaths, root, SVNMergeInfoInheritance.INHERITED, 
                true);
        Map mergeInfoCatalog = new TreeMap();
        for (Iterator mergeInfoIter = treeMergeInfo.values().iterator(); mergeInfoIter.hasNext();) {
            SVNMergeInfo mergeInfo = (SVNMergeInfo) mergeInfoIter.next();
            mergeInfoCatalog = SVNMergeInfoUtil.mergeMergeInfos(mergeInfoCatalog, 
                    mergeInfo.getMergeSourcesToMergeLists());
        }
        return mergeInfoCatalog;
    }

    private LinkedList combineMergeInfoPathLists(Map mergeInfo) {
        List rangeListPaths = new LinkedList();
        for (Iterator pathsIter = mergeInfo.keySet().iterator(); pathsIter.hasNext();) {
            String path = (String) pathsIter.next();
            SVNMergeRangeList changes = (SVNMergeRangeList) mergeInfo.get(path);
            RangeListPath rangeListPath = new RangeListPath();
            rangeListPath.myPath = path;
            rangeListPath.myRangeList = changes.dup();
            SVNMergeRange[] rangesArray = rangeListPath.myRangeList.getRanges(); 
            for (int i = 0; i < rangesArray.length; i++) {
                SVNMergeRange range = rangesArray[i];
                range.setStartRevision(range.getStartRevision() + 1);
            }
            rangeListPaths.add(rangeListPath);
        }
        
        LinkedList combinedList = new LinkedList();
        Comparator rangeListPathsComparator = new Comparator() {
            public int compare(Object arg1, Object arg2) {
                RangeListPath rangeListPath1 = (RangeListPath) arg1;
                RangeListPath rangeListPath2 = (RangeListPath) arg2;
                SVNMergeRange[] ranges1 = rangeListPath1.myRangeList.getRanges();
                SVNMergeRange[] ranges2 = rangeListPath2.myRangeList.getRanges();
                SVNMergeRange range1 = ranges1[0];
                SVNMergeRange range2 = ranges2[0];
                if (range1.getStartRevision() < range2.getStartRevision()) {
                    return -1;
                }
                if (range1.getStartRevision() > range2.getStartRevision()) {
                    return 1;
                }
                if (range1.getEndRevision() < range2.getEndRevision()) {
                    return -1;
                }
                if (range1.getEndRevision() > range2.getEndRevision()) {
                    return 1;
                }
                return 0;
            }
        };
        
        while (rangeListPaths.size() > 1) {
            Collections.sort(rangeListPaths, rangeListPathsComparator);
            RangeListPath rangeListPath = (RangeListPath) rangeListPaths.get(0);
            RangeListPath firstRLP = rangeListPath;
            long youngest = rangeListPath.myRangeList.getRanges()[0].getStartRevision();
            long nextYoungest = youngest;
            int numRevs = 1;
            for (; nextYoungest == youngest; numRevs++) {
                if (numRevs == rangeListPaths.size()) {
                    numRevs++;
                    break;
                }
                rangeListPath = (RangeListPath) rangeListPaths.get(numRevs);
                nextYoungest = rangeListPath.myRangeList.getRanges()[0].getStartRevision();
            }
            numRevs--;
            long youngestEnd = firstRLP.myRangeList.getRanges()[0].getEndRevision();
            long tail = nextYoungest - 1;
            if (nextYoungest == youngest || youngestEnd < nextYoungest) {
                tail = youngestEnd;
            }

            PathListRange pathListRange = new PathListRange();
            pathListRange.myRange = new SVNMergeRange(youngest, tail, false);
            List paths = new LinkedList();
            for (int i = 0; i < numRevs; i++) {
                RangeListPath rp = (RangeListPath) rangeListPaths.get(i); 
                paths.add(rp.myPath);
            }
            pathListRange.myPaths = (String[]) paths.toArray(new String[paths.size()]);
        
            combinedList.add(pathListRange);
            
            for (int i = 0; i < numRevs; i++) {
                RangeListPath rp = (RangeListPath) rangeListPaths.get(i);
                SVNMergeRange range = rp.myRangeList.getRanges()[0];
                range.setStartRevision(tail + 1);
                if (range.getStartRevision() > range.getEndRevision()) {
                    if (rp.myRangeList.getSize() == 1) {
                        rangeListPaths.remove(0);
                        i--;
                        numRevs--;
                    } else {
                        SVNMergeRange[] ranges = new SVNMergeRange[rp.myRangeList.getSize() - 1];
                        System.arraycopy(rp.myRangeList.getRanges(), 1, ranges, 0, ranges.length);
                        rp.myRangeList = new SVNMergeRangeList(ranges);
                    }
                }
            }
        }
        
        RangeListPath firstRangeListPath = (RangeListPath) rangeListPaths.get(0);
        while (!firstRangeListPath.myRangeList.isEmpty()) {
            PathListRange pathListRange = new PathListRange();
            pathListRange.myPaths = new String[] { firstRangeListPath.myPath };
            pathListRange.myRange = firstRangeListPath.myRangeList.getRanges()[0];
            SVNMergeRange[] ranges = new SVNMergeRange[firstRangeListPath.myRangeList.getSize() - 1];
            System.arraycopy(firstRangeListPath.myRangeList.getRanges(), 1, ranges, 0, ranges.length);
            firstRangeListPath.myRangeList = new SVNMergeRangeList(ranges);
            combinedList.add(pathListRange);
        }
        return combinedList;
    }

    private SVNMergeInfoManager getMergeInfoManager() {
        if (myMergeInfoManager == null) {
            myMergeInfoManager = new SVNMergeInfoManager();
        }
        return myMergeInfoManager;
    }
    
    private class RangeListPath {
        String myPath;
        SVNMergeRangeList myRangeList;
    }
    
    private class PathListRange {
        String myPaths[];
        SVNMergeRange myRange;
    }
    
    private class PathInfo {
        FSNodeHistory myHistory;
        boolean myIsDone;
        boolean myIsFirstTime;
        long myHistoryRevision;
        String myPath;
        
        public PathInfo getHistory(boolean strictNodeHistory, long start) throws SVNException {
            FSNodeHistory history = null;
            if (myHistory != null) {
                history = myHistory.getPreviousHistory(strictNodeHistory ? false : true);
                myHistory = history;
            } else {
                FSRevisionRoot historyRoot = myFSFS.createRevisionRoot(myHistoryRevision);
                history = historyRoot.getNodeHistory(myPath);
                history = history.getPreviousHistory(strictNodeHistory ? false : true);
                if (myIsFirstTime) {
                    myIsFirstTime = false;
                } else if (history != null) {
                    history = history.getPreviousHistory(strictNodeHistory ? false : true);
                }
            }

            if (history == null) {
                myIsDone = true;
                return this;
            }

            myPath = history.getHistoryEntry().getPath();
            myHistoryRevision = history.getHistoryEntry().getRevision();
            
            if (myHistoryRevision < start) {
                myIsDone = true;
            }
            return this;
        }

        public boolean checkHistory(long currentRevision, boolean strictNodeHistory, 
                                    long start, boolean changed) throws SVNException {
            if (myIsDone) {
                return changed;
            }
            
            if (myHistoryRevision < currentRevision) {
                return changed;
            }
            
            changed = true;
            getHistory(strictNodeHistory, start);
            return changed;
        }
    }
    
}
