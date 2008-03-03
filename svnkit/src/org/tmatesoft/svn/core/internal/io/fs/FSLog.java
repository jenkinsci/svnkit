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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNDate;
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
    
    public FSLog(FSFS owner, String[] paths, long limit, long start, 
                 long end, boolean descending, boolean discoverChangedPaths, 
                 boolean strictNode, boolean includeMergedRevisions, 
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
        if (myPaths.length == 1 && "/".equals(myPaths[0])) {
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

        if (myIsIncludeMergedRevisions) {
            return doMergedLogs(myPaths, myStartRevision, myEndRevision, myLimit, null, myIsDescending);
        } 
        return doLogs();
    }
    
    private long doLogs() throws SVNException {
        long sendCount = 0;
        PathInfo[] histories = getPathHistories(myPaths, myStartRevision, myEndRevision, myIsStrictNode);
        
        LinkedList revisions = null;
        boolean anyHistoriesLeft = true;
        for (long currentRev = myEndRevision; 
             currentRev >= myStartRevision && anyHistoriesLeft; 
             currentRev = getNextHistoryRevision(histories)) {
            
            boolean changed = false;
            anyHistoriesLeft = false;
            
            for (int i = 0; i < histories.length; i++) {
                PathInfo info = histories[i];
                changed = info.checkHistory(currentRev, myIsStrictNode, myStartRevision, changed);
                
                if (!info.myIsDone) {
                    anyHistoriesLeft = true;
                }
            }

            if (changed) {
                if (myIsDescending) {
                    sendLog(currentRev, false);
                    sendCount++;
                    if (myLimit > 0 && sendCount >= myLimit) {
                        break;
                    }
                } else {
                    if (revisions == null) {
                        revisions = new LinkedList();
                    }
                    revisions.addLast(new Long(currentRev));
                }
            }
        }

        if (revisions != null) {
            for (int i = 0; i < revisions.size(); i++) {
                long rev = ((Long) revisions.get(revisions.size() - i - 1)).longValue();
                sendLog(rev, false);
                sendCount++;
                if (myLimit > 0 && sendCount >= myLimit) {
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
            changedPaths = new HashMap();
        }
        if (entryRevProps == null) {
            entryRevProps = new SVNProperties();
        }
        SVNLogEntry entry = new SVNLogEntry(changedPaths, revision, entryRevProps, false);
        return entry;
    }
    
    private long doMergedLogs(String[] paths, long histStart, long histEnd, long limit, Map foundRevisions, 
            boolean isDescendingOrder) throws SVNException {
        boolean anyHistoriesLeft = true;
        boolean mainLineRun = false;
        boolean useLimit = true;
        long sendCount = 0;
        LinkedList revs = new LinkedList();
        
        if (foundRevisions == null) {
            mainLineRun = true;
            foundRevisions = new HashMap();
        }
        
        if (limit == 0) {
            useLimit = false;
        }
        
        PathInfo[] histories = getPathHistories(paths, 0, histEnd, myIsStrictNode);
        for (long current = histEnd; anyHistoriesLeft; current = getNextHistoryRevision(histories)) {
            boolean changed = false;
            anyHistoriesLeft = false;
            if (!mainLineRun && foundRevisions.get(new Long(current)) != null) {
                break;
            }
            for (int i = 0; i < histories.length; i++) {
                PathInfo info = histories[i];
                changed = info.checkHistory(current, myIsStrictNode, 0, changed);
                if (!info.myIsDone) {
                    anyHistoriesLeft = true;
                }
            }
            if (changed) {
                String[] currentPaths = new String[histories.length];
                for (int i = 0; i < histories.length; i++) {
                    PathInfo info = histories[i];
                    currentPaths[i] = info.myPath;
                }                
                revs.add(new Long(current));
                Map mergeInfo = getMergedRevisionMergeInfo(currentPaths, current);
                foundRevisions.put(new Long(current), mergeInfo);
            }
        }
        
        for (int i = 0; i < revs.size() && !(useLimit && limit == 0); i++) {
            Long rev = (Long) revs.get(isDescendingOrder ? i : revs.size() - i - 1);
            Map mergeInfo = (Map) foundRevisions.get(rev);
            boolean hasChildren = mergeInfo != null && !mergeInfo.isEmpty();
            if (rev.longValue() < histStart) {
                break;
            }
            
            sendLog(rev.longValue(), hasChildren);
            sendCount++;
            
            if (hasChildren) {
                LinkedList combinedList = combineMergeInfoPathLists(mergeInfo);
                for (int j = combinedList.size() - 1; j >= 0; j--) {
                    PathListRange pathListRange = (PathListRange) combinedList.get(j);
                    sendCount += doMergedLogs(pathListRange.myPaths, pathListRange.myRange.getStartRevision(), 
                            pathListRange.myRange.getEndRevision(), 0, foundRevisions, true);
                }
                if (myHandler != null) {
                    myHandler.handleLogEntry(SVNLogEntry.EMPTY_ENTRY);
                }
            }
            limit--;
        }
        return sendCount;
    }

    private PathInfo[] getPathHistories(String[] paths, long start, 
                                        long end, boolean strictNodeHistory) throws SVNException {
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

    private LinkedList combineMergeInfoPathLists(Map mergeInfo) throws SVNException {
        SVNMergeRangeList rangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
        for (Iterator pathsIter = mergeInfo.keySet().iterator(); pathsIter.hasNext();) {
            String path = (String) pathsIter.next();
            SVNMergeRangeList changes = (SVNMergeRangeList) mergeInfo.get(path);
            rangeList = rangeList.merge(changes);
        }
        
        long[] revs = rangeList.toRevisionsArray();
        List pathLists = new ArrayList(revs.length);
        for (int i = 0; i < revs.length; i++) {
            long rev = revs[i];
            String[] paths = SVNMergeInfoUtil.findMergeSources(rev, mergeInfo);
            pathLists.add(paths);
        }
        
        LinkedList combinedList = new LinkedList();
        PathListRange pathListRange = new PathListRange();
        pathListRange.myPaths = (String[]) pathLists.get(0);
        long rangeStart = revs[0];
        long rangeEnd = SVNRepository.INVALID_REVISION;
        int i = 1;
        for (; i < revs.length; i++) {
            String[] curPathList = (String[]) pathLists.get(i);
            String[] prevPathList = (String[]) pathLists.get(i - 1);
            if (!arePathListsEqual(curPathList, prevPathList)) {
                rangeEnd = revs[i - 1];
                pathListRange.myRange = new SVNMergeRange(rangeStart, rangeEnd, false);
                combinedList.add(pathListRange);
                pathListRange = new PathListRange();
                pathListRange.myPaths = curPathList;
                rangeStart = revs[i];
            }
        }
        rangeEnd = revs[i - 1]; 
        pathListRange.myRange = new SVNMergeRange(rangeStart, rangeEnd, false);
        combinedList.add(pathListRange);
        return combinedList;
    }
    
    private boolean arePathListsEqual(String[] pathList1, String[] pathList2) {
        if (pathList1.length != pathList2.length) {
            return false;
        }
        for (int i = 0; i < pathList1.length; i++) {
            String path1 = pathList1[i];
            String path2 = pathList2[i];
            if (!path1.equals(path2)) {
                return false;
            }
        }
        return true;
    }
    
    private SVNMergeInfoManager getMergeInfoManager() {
        if (myMergeInfoManager == null) {
            myMergeInfoManager = new SVNMergeInfoManager();
        }
        return myMergeInfoManager;
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
