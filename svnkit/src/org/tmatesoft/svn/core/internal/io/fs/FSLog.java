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

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
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
                sendLogs(myPaths, rev, myIsIncludeMergedRevisions);
            }
            
            return count;
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
                    sendLogs(myPaths, currentRev, myIsIncludeMergedRevisions);
                    if (myLimit > 0 && ++sendCount >= myLimit) {
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
                sendLogs(myPaths, nextRev, myIsIncludeMergedRevisions);
                if (myLimit > 0 && ++sendCount >= myLimit) {
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

    private void sendLogs(String[] paths, long revision, boolean includeMergedRevisions) throws SVNException {

        SVNLogEntry logEntry = fillLogEntry(revision);

        Map mergeInfo = null;
        SVNMergeRangeList combinedRangeList = null;
        if (includeMergedRevisions) {
            mergeInfo = getMergedRevisionMergeInfo(paths, revision);
            combinedRangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
            for (Iterator pathsIter = mergeInfo.keySet().iterator(); pathsIter.hasNext();) {
                String path = (String) pathsIter.next();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(path);
                combinedRangeList = combinedRangeList.merge(rangeList);
            }
            if (combinedRangeList.countRevisions() != 0) {
                logEntry.setHasChildren(true);
            }
        }

        if (myHandler != null) {
            myHandler.handleLogEntry(logEntry);
        }
        
        if (logEntry.hasChildren()) {
            FSRevisionRoot root = null;
            long[] revs = combinedRangeList.toRevisionsArray();
            for (int i = 0; i < revs.length; i++) {
                long rev = revs[i];
                String mergeSource = SVNMergeInfoUtil.findMergeSource(rev, mergeInfo);
                root = myFSFS.createRevisionRoot(rev);
                if (root.checkNodeKind(mergeSource) == SVNNodeKind.NONE) {
                    continue;
                }
                
                doMergedLog(mergeSource, rev);
            }
            if (myHandler != null) {
                myHandler.handleLogEntry(SVNLogEntry.EMPTY_ENTRY);
            }
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
    
    private void doMergedLog(String path, long revision) throws SVNException {
        String[] paths = new String[] {path};
        PathInfo[] histories = getPathHistories(paths, revision, revision, 
                                                true);
        boolean changed = false;
        for (int i = 0; i < histories.length; i++) {
            PathInfo info = histories[i];
            changed = info.checkHistory(revision, true, revision, changed);
        }

        if (changed) {
            sendLogs(paths, revision, true);
        }
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
        SVNMergeInfoUtil.diffMergeInfo(deleted, changed, previousMergeInfo, currentMergeInfo, false);
        changed = SVNMergeInfoUtil.mergeMergeInfos(changed, deleted);
        return changed;
    }
    
    private Map getCombinedMergeInfo(String[] paths, long revision, long currentRevision) throws SVNException {
        if (revision == 0) {
            return new TreeMap();
        }
        FSRevisionRoot root = myFSFS.createRevisionRoot(revision);
        String[] queryPaths = paths;
        if (revision != currentRevision) {
            List existingPaths = new LinkedList();
            for (int i = 0; i < paths.length; i++) {
                String path = paths[i];
                SVNNodeKind kind = root.checkNodeKind(path);
                if (kind != SVNNodeKind.NONE) {
                    existingPaths.add(path);
                }
            }
            queryPaths = (String[]) existingPaths.toArray(new String[existingPaths.size()]);
        }
        
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

    private SVNMergeInfoManager getMergeInfoManager() {
        if (myMergeInfoManager == null) {
            myMergeInfoManager = new SVNMergeInfoManager();
        }
        return myMergeInfoManager;
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
