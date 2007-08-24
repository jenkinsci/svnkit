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

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeInheritance;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNMergeInfoFilter;
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
    private boolean myIsOmitLogText;
    private boolean myIsIncludeMergedRevisions;
    private long myStartRevision;
    private long myEndRevision;
    private long myLimit;
    private ISVNLogEntryHandler myHandler;
    private SVNMergeInfoManager myMergeInfoManager;
    
    public FSLog(FSFS owner, String[] paths, long limit, long start, 
                 long end, boolean descending, boolean discoverChangedPaths, 
                 boolean strictNode, boolean includeMergedRevisions, 
                 boolean omitLogText, ISVNLogEntryHandler handler) {
        myFSFS = owner;
        myPaths = paths;
        myStartRevision = start;
        myEndRevision = end;
        myIsDescending = descending;
        myIsDiscoverChangedPaths = discoverChangedPaths;
        myIsStrictNode = strictNode;
        myIsIncludeMergedRevisions = includeMergedRevisions;
        myIsOmitLogText = omitLogText;
        myLimit = limit;
        myHandler = handler;
    }
    
    public long runLog() throws SVNException {
        long count = 0;
        long sendCount = 0;
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
                LogTreeNode tree = buildLogTree(myPaths, rev, myIsIncludeMergedRevisions);
                sendCount += sendLogTree(tree);
            }
            
            return count;
        }

        return doLogs();
    }
    
    private long doLogs() throws SVNException {
        long sendCount = 0;
        PathInfo[] histories = getPathHistories(myPaths, myStartRevision, 
                                                myEndRevision, myIsStrictNode);
        
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
                    LogTreeNode tree = buildLogTree(myPaths, currentRev, myIsIncludeMergedRevisions);
                    sendCount += sendLogTree(tree);
                    if (myLimit > 0 && sendCount >= myLimit) {
                        break;
                    }
                } else {
                    if (revisions == null) {
                        revisions = new LinkedList();
                    }
                    revisions.addFirst(new Long(currentRev));
                }
            }
        }

        if (revisions != null) {
            for (ListIterator revs = revisions.listIterator(); revs.hasNext();) {
                long nextRev = ((Long) revs.next()).longValue(); 
                LogTreeNode tree = buildLogTree(myPaths, nextRev, myIsIncludeMergedRevisions);
                sendCount += sendLogTree(tree);
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

    private LogTreeNode buildLogTree(String[] paths, long revision, 
                                     boolean includeMergedRevisions) throws SVNException {
        LogTreeNode treeNode = new LogTreeNode();
        treeNode.myChildren = new LinkedList();

        Map revisionProps = myFSFS.getRevisionProperties(revision);
        Map changedPaths = null;
        String author = null;
        Date date = null;
        String message = null;

        if (revisionProps != null) {
            author = (String) revisionProps.get(SVNRevisionProperty.AUTHOR);
            String datestamp = (String) revisionProps.get(SVNRevisionProperty.DATE);
            message = (String) revisionProps.get(SVNRevisionProperty.LOG);
            date = datestamp != null ? SVNTimeUtil.parseDateString(datestamp) : null;
        }

        FSRevisionRoot root = null;
        if (revision > 0 && myIsDiscoverChangedPaths) {
            root = myFSFS.createRevisionRoot(revision);
            changedPaths = root.detectChanged();
        }

        changedPaths = changedPaths == null ? new HashMap() : changedPaths;

        if (myIsOmitLogText && message != null) {
            message = null;
        }

        treeNode.myLogEntry = new SVNLogEntry(changedPaths, revision, author, date, message);

        Map mergeInfo = null;
        long numberOfChildren = 0;
        SVNMergeRangeList combinedRangeList = null;
        if (includeMergedRevisions) {
            mergeInfo = getMergedRevisionMergeInfo(paths, revision);
            combinedRangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
            for (Iterator pathsIter = mergeInfo.keySet().iterator(); pathsIter.hasNext();) {
                String path = (String) pathsIter.next();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(path);
                combinedRangeList = combinedRangeList.merge(rangeList, 
                                                            SVNMergeRangeInheritance.EQUAL_INHERITANCE);
            }
            numberOfChildren = combinedRangeList.countRevisions();
        }
        
        if (numberOfChildren > 0) {
            long[] revs = combinedRangeList.toRevisionsArray();
            for (int i = 0; i < revs.length; i++) {
                long rev = revs[i];
                String mergeSource = SVNMergeInfoManager.findMergeSource(rev, mergeInfo);
                root = myFSFS.createRevisionRoot(rev);
                if (root.checkNodeKind(mergeSource) == SVNNodeKind.NONE) {
                    continue;
                }
                LogTreeNode subTree = doMergedLog(mergeSource, rev);
                if (subTree != null) {
                    treeNode.myChildren.add(subTree);
                }
            }
        }
        return treeNode;    
    }
    
    private LogTreeNode doMergedLog(String path, long revision) throws SVNException {
        String[] paths = new String[] {path};
        PathInfo[] histories = getPathHistories(paths, revision, revision, 
                                                true);
        boolean changed = false;
        for (int i = 0; i < histories.length; i++) {
            PathInfo info = histories[i];
            changed = info.checkHistory(revision, true, revision, changed);
        }

        if (changed) {
            return buildLogTree(paths, revision, true);
        }
        return null;
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
        
        Map currentMergeInfo = getCombinedMergeInfo(paths, revision, revision);  
        Map previousMergeInfo = getCombinedMergeInfo(paths, revision - 1, revision);
        Map deleted = new TreeMap();
        Map changed = new TreeMap();
        SVNMergeInfoManager.diffMergeInfo(deleted, changed, previousMergeInfo, currentMergeInfo,
                                          SVNMergeRangeInheritance.IGNORE_INHERITANCE);
        changed = SVNMergeInfoManager.mergeMergeInfos(changed, deleted, 
                                                      SVNMergeRangeInheritance.EQUAL_INHERITANCE);
        return changed;
    }
    
    private Map getCombinedMergeInfo(String[] paths, long revision, long currentRevision) throws SVNException {
        Map mergeInfo = new TreeMap();
        if (revision == 0) {
            return mergeInfo;
        }
        FSRevisionRoot root = myFSFS.createRevisionRoot(revision);
        SVNMergeInfoManager mergeInfoManager = getMergeInfoManager(); 
        ISVNMergeInfoFilter filter = new BranchingCopyFilter(revision, root, 
                                                             revision == currentRevision);
        Map treeMergeInfo = mergeInfoManager.getMergeInfoForTree(paths, root, filter);
        for (Iterator pathsIter = treeMergeInfo.keySet().iterator(); pathsIter.hasNext();) {
            String path = (String) pathsIter.next();
            SVNMergeInfo info = (SVNMergeInfo) treeMergeInfo.get(path);
            mergeInfo = SVNMergeInfoManager.mergeMergeInfos(mergeInfo, 
                                                            info.getMergeSourcesToMergeLists(),
                                                            SVNMergeRangeInheritance.EQUAL_INHERITANCE);
        }
        return mergeInfo;
    }
    
    private long sendLogTree(LogTreeNode treeNode) throws SVNException {
        long ret = 0;
        treeNode.myLogEntry.setNumberOfChildren(treeNode.myChildren.size());
        if (myHandler != null) {
            ++ret;
            myHandler.handleLogEntry(treeNode.myLogEntry);
        }

        for (Iterator childrent = treeNode.myChildren.iterator(); childrent.hasNext();) {
            LogTreeNode subTree = (LogTreeNode) childrent.next();
            ret += sendLogTree(subTree);
        }
        return ret;
    }

    private SVNMergeInfoManager getMergeInfoManager() {
        if (myMergeInfoManager == null) {
            myMergeInfoManager = SVNMergeInfoManager.createMergeInfoManager(null);
        }
        return myMergeInfoManager;
    }
    
    private class LogTreeNode {
        SVNLogEntry myLogEntry;
        Collection myChildren;
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
    
    private class BranchingCopyFilter implements ISVNMergeInfoFilter {
        private long myRevision;
        private FSRevisionRoot myRoot;
        private boolean myIsFindingCurrentRevision;
        
        public BranchingCopyFilter(long revision, FSRevisionRoot root, 
                                   boolean isFindingCurrentRevision) {
            myRevision = revision;
            myRoot = root;
            myIsFindingCurrentRevision = isFindingCurrentRevision;
        }
        
        public boolean omitMergeInfo(String path, Map pathMergeInfo) throws SVNException {
            if (!myIsFindingCurrentRevision) {
                return false;
            }
            
            SVNNodeKind kind = myRoot.checkNodeKind(path);
            if (kind == SVNNodeKind.NONE) {
                return false;
            }
            return isBranchingCopy(pathMergeInfo, path);
        }
        
        private boolean isBranchingCopy(Map pathMergeInfo, String path) throws SVNException {
            Map mergeInfo = null;
            if (pathMergeInfo != null) {
                mergeInfo = pathMergeInfo;
            } else {
                mergeInfo = getPathMergeInfo(path); 
            }
            
            FSClosestCopy closestCopy = myRoot.getClosestCopy(path);
            FSRevisionRoot copyRoot = closestCopy != null ? closestCopy.getRevisionRoot()
                                                          : null;
            
            if (copyRoot == null) {
                return false;
            }

            if (copyRoot.getRevision() != myRevision) {
                return false;
            }

            Map impliedMergeInfo = calculateBranchingCopyMergeInfo(copyRoot, 
                                                                   closestCopy.getPath(), 
                                                                   path, 
                                                                   myRevision);           

            Map added = new HashMap();
            Map deleted = new HashMap();
            SVNMergeInfoManager.diffMergeInfo(deleted, added, impliedMergeInfo, mergeInfo,
                                              SVNMergeRangeInheritance.IGNORE_INHERITANCE);
            if (deleted.isEmpty() && added.isEmpty()) {
                return false;
            }
            return true;
        }
        
        private Map getPathMergeInfo(String path) throws SVNException {
            SVNMergeInfoManager mergeInfoManager = SVNMergeInfoManager.createMergeInfoManager(null);
            Map tmpMergeInfo = mergeInfoManager.getMergeInfo(new String[] {path}, 
                                                             myRoot, 
                                                             SVNMergeInfoInheritance.INHERITED);
            SVNMergeInfo mergeInfo = (SVNMergeInfo) tmpMergeInfo.get(path);
            if (mergeInfo != null) {
                return mergeInfo.getMergeSourcesToMergeLists();
            }
            return new TreeMap();
        }
        
        private Map calculateBranchingCopyMergeInfo(FSRevisionRoot srcRoot, 
                                                    String srcPath,
                                                    String dstPath, 
                                                    long revision) throws SVNException {
            
            Map impliedMergeInfo = new TreeMap();
            FSClosestCopy closestCopy = srcRoot.getClosestCopy(srcPath);
            FSRevisionRoot copyRoot = closestCopy != null ? closestCopy.getRevisionRoot() : null;  
            if (copyRoot == null) {
                return impliedMergeInfo;
            }
            
            long oldestRevision = copyRoot.getRevision();
            SVNMergeRangeList rangeList = new SVNMergeRangeList(new SVNMergeRange[] {
                                                                new SVNMergeRange(oldestRevision, 
                                                                                  revision - 1, 
                                                                                  true)
                                                                });
            impliedMergeInfo.put(dstPath, rangeList);    
            return impliedMergeInfo;
        }
    }

}
