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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfoManager {
    private ISVNDBProcessor myDBProcessor;
    
    private SVNMergeInfoManager(ISVNDBProcessor dbProcessor) {
        myDBProcessor = dbProcessor;
    }
    
    public void createIndex(File dbDirectory) throws SVNException {
        try {
            myDBProcessor.openDB(dbDirectory);
        } finally {
            myDBProcessor.closeDB();
        }
    }
    
    public void updateIndex(File dbDirectory, long newRevision, Map mergeInfo) throws SVNException {
        try {
            myDBProcessor.openDB(dbDirectory);
            myDBProcessor.beginTransaction();
            myDBProcessor.cleanUpFailedTransactionsInfo(newRevision);
            if (mergeInfo != null) {
                indexTxnMergeInfo(newRevision, mergeInfo);
            }
            myDBProcessor.commitTransaction();
        } finally {
            myDBProcessor.closeDB();
        }
    }
    
    public Map getMergeInfo(String[] paths, FSRevisionRoot root, SVNMergeInfoInheritance inherit) throws SVNException {
        Map mergeInfo = null; 
        try {
            myDBProcessor.openDB(root.getOwner().getDBRoot());
            mergeInfo = getMergeInfoImpl(paths, root, inherit);
        } finally {
            myDBProcessor.closeDB();
        }
        return mergeInfo == null ? new TreeMap() : mergeInfo;
    }
    
    public Map getMergeInfoForTree(String[] paths, FSRevisionRoot root, ISVNMergeInfoFilter filter) throws SVNException {
        Map pathsToMergeInfos = null; 
        try {
            myDBProcessor.openDB(root.getOwner().getDBRoot());

            pathsToMergeInfos = getMergeInfoImpl(paths, root, SVNMergeInfoInheritance.INHERITED);
            long revision = root.getRevision();
            for (int i = 0; i < paths.length; i++) {
                String path = paths[i];
                SVNMergeInfo mergeInfo = (SVNMergeInfo) pathsToMergeInfos.get(path); 
                Map srcsToRangeLists = mergeInfo != null ? mergeInfo.getMergeSourcesToMergeLists() 
                                                         : new TreeMap();
                if (filter != null && filter.omitMergeInfo(path, srcsToRangeLists)) {
                    pathsToMergeInfos.remove(path);
                    continue;
                }
                
                srcsToRangeLists = myDBProcessor.getMergeInfoForChildren(path, 
                                                                         revision, 
                                                                         srcsToRangeLists, 
                                                                         filter);
                if (mergeInfo == null) {
                    mergeInfo = new SVNMergeInfo(path, srcsToRangeLists);
                    pathsToMergeInfos.put(path, mergeInfo);
                } else {
                    mergeInfo.setMergeSourcesToMergeLists(srcsToRangeLists);
                }
            }
        } finally {
            myDBProcessor.closeDB();
        }

        return pathsToMergeInfos == null ? new TreeMap() : pathsToMergeInfos;
    }
    
    private void indexTxnMergeInfo(long revision, Map pathsToMergeInfos) throws SVNException {
        for (Iterator paths = pathsToMergeInfos.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            String mergeInfoToParse = (String) pathsToMergeInfos.get(path);
            indexPathMergeInfo(revision, path, mergeInfoToParse);
        }
    }

    private void  indexPathMergeInfo(long revision, String path, String mergeInfoToParse) throws SVNException {
        boolean removeMergeInfo = false;
        Map mergeInfo = SVNMergeInfoManager.parseMergeInfo(new StringBuffer(mergeInfoToParse), null);
        if (mergeInfo.isEmpty()) {
            mergeInfo = getMergeInfoForPath(path, revision, new HashMap(), mergeInfo, SVNMergeInfoInheritance.INHERITED);
            SVNMergeInfo info = (SVNMergeInfo) mergeInfo.get(path);
            if (info == null) {
                return;
            }
            mergeInfo = info.getMergeSourcesToMergeLists();
            removeMergeInfo = true;
        }
        for (Iterator paths = mergeInfo.keySet().iterator(); paths.hasNext();) {
            String srcMergePath = (String) paths.next();
            SVNMergeRangeList rangeList = removeMergeInfo ? SVNMergeRangeList.EMPTY_RANGE_LIST
                                                          : (SVNMergeRangeList) mergeInfo.get(srcMergePath);
            myDBProcessor.insertMergeInfo(revision, srcMergePath, path, rangeList.getRanges());
        }
        myDBProcessor.updateMergeInfoChanges(revision, path);
    }

    private Map getMergeInfoImpl(String[] paths, FSRevisionRoot root, SVNMergeInfoInheritance inherit) throws SVNException {
        Map mergeInfoCache = new TreeMap();
        Map result = null;
        long revision = root.getRevision();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            result = getMergeInfoForPath(path, revision, mergeInfoCache, result, inherit);
        }
        return result;
    } 
    
    private Map getMergeInfoForPath(String path, long revision, Map mergeInfoCache, Map result, SVNMergeInfoInheritance inherit) throws SVNException {
        result = result == null ? new TreeMap() : result;
        long lastMergedRevision = 0;
        if (inherit != SVNMergeInfoInheritance.NEAREST_ANCESTOR) {
            SVNMergeInfo pathMergeInfo = (SVNMergeInfo) mergeInfoCache.get(path);
            if (pathMergeInfo != null) {
                result.put(path, pathMergeInfo);
                return result;
            }
            
            lastMergedRevision = myDBProcessor.getMaxRevisionForPathFromMergeInfoChangedTable(path, revision);
            if (lastMergedRevision > 0) {
                Map mergeSrcsToMergeRangeLists = myDBProcessor.parseMergeInfoFromDB(path, lastMergedRevision);
                if (!mergeSrcsToMergeRangeLists.isEmpty()) {
                    SVNMergeInfo mergeInfo = new SVNMergeInfo(path, combineRanges(mergeSrcsToMergeRangeLists));
                    result.put(path, mergeInfo);
                    mergeInfoCache.put(path, mergeInfo);
                } else {
                    mergeInfoCache.remove(path);
                }
                return result;
            }
        }
        if ((lastMergedRevision == 0 && inherit == SVNMergeInfoInheritance.INHERITED) || 
                inherit == SVNMergeInfoInheritance.NEAREST_ANCESTOR) {
            if ("".equals(path) || "/".equals(path)) {
                return result;
            }
            String parent = SVNPathUtil.removeTail(path);
            getMergeInfoForPath(parent, revision, mergeInfoCache, null, SVNMergeInfoInheritance.INHERITED);
            SVNMergeInfo parentInfo = (SVNMergeInfo) mergeInfoCache.get(parent);
            if (parentInfo == null) {
                mergeInfoCache.remove(path);
            } else {
                String name = SVNPathUtil.tail(path);
                Map parentSrcsToRangeLists = parentInfo.getMergeSourcesToMergeLists();
                Map translatedSrcsToRangeLists = new TreeMap();
                for (Iterator paths = parentSrcsToRangeLists.keySet().iterator(); paths.hasNext();) {
                    String mergeSrcPath = (String) paths.next();
                    SVNMergeRangeList rangeList = (SVNMergeRangeList) parentSrcsToRangeLists.get(mergeSrcPath);
                    translatedSrcsToRangeLists.put(SVNPathUtil.append(mergeSrcPath, name), rangeList);
                }
                SVNMergeInfo translatedMergeInfo = new SVNMergeInfo(path, combineRanges(translatedSrcsToRangeLists)); 
                mergeInfoCache.put(path, translatedMergeInfo);
                result.put(path, translatedMergeInfo);
            }
        }
        return result;
    }
    
    private Map combineRanges(Map srcPathsToRangeLists) {
        String[] paths = (String[]) srcPathsToRangeLists.keySet().toArray(new String[srcPathsToRangeLists.keySet().size()]);
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            SVNMergeRangeList rangeList = (SVNMergeRangeList) srcPathsToRangeLists.get(path);
            rangeList = rangeList.combineRanges();
            srcPathsToRangeLists.put(path, rangeList);
        }
        return srcPathsToRangeLists;
    }
    
    public static SVNMergeInfoManager createMergeInfoManager(ISVNDBProcessor dbProcessor) {
        if (dbProcessor == null) {
            dbProcessor =  new SVNSQLiteDBProcessor();
        }
        return new SVNMergeInfoManager(dbProcessor);
    }
    
    public static Map mergeMergeInfos(Map originalSrcsToRangeLists, Map changedSrcsToRangeLists) {
        originalSrcsToRangeLists = originalSrcsToRangeLists == null ? new TreeMap() : originalSrcsToRangeLists;
        changedSrcsToRangeLists = changedSrcsToRangeLists == null ? Collections.EMPTY_MAP : changedSrcsToRangeLists;
        String[] paths1 = (String[]) originalSrcsToRangeLists.keySet().toArray(new String[originalSrcsToRangeLists.size()]);
        String[] paths2 = (String[]) changedSrcsToRangeLists.keySet().toArray(new String[changedSrcsToRangeLists.size()]);
        int i = 0;
        int j = 0;
        while (i < paths1.length && j < paths2.length) {
            String path1 = paths1[i];
            String path2 = paths2[j];
            int res = path1.compareTo(path2);
            if (res == 0) {
                SVNMergeRangeList rangeList1 = (SVNMergeRangeList) originalSrcsToRangeLists.get(path1);
                SVNMergeRangeList rangeList2 = (SVNMergeRangeList) changedSrcsToRangeLists.get(path2);
                rangeList1 = rangeList1.merge(rangeList2);
                originalSrcsToRangeLists.put(path1, rangeList1);
                i++;
                j++;
            } else if (res < 0) {
                i++;
            } else {
                originalSrcsToRangeLists.put(path2, changedSrcsToRangeLists.get(path2));
                j++;
            }
        }
        
        for (; j < paths2.length; j++) {
            String path = paths2[j];
            originalSrcsToRangeLists.put(path, changedSrcsToRangeLists.get(path));
        }
        return originalSrcsToRangeLists;
    }
    
    public static String combineMergeInfoProperties(String propValue1, String propValue2) throws SVNException {
        Map srcsToRanges1 = parseMergeInfo(new StringBuffer(propValue1), null);
        Map srcsToRanges2 = parseMergeInfo(new StringBuffer(propValue2), null);
        srcsToRanges1 = mergeMergeInfos(srcsToRanges1, srcsToRanges2);
        return formatMergeInfoToString(srcsToRanges1);
    }
    
    public static String combineForkedMergeInfoProperties(String fromPropValue, String workingPropValue, String toPropValue) throws SVNException {
        Map leftDeleted = new TreeMap();
        Map leftAdded = new TreeMap();
        Map fromMergeInfo = parseMergeInfo(new StringBuffer(fromPropValue), null);
        diffMergeInfoProperties(leftDeleted, leftAdded, null, fromMergeInfo, workingPropValue, null);
        
        Map rightDeleted = new TreeMap();
        Map rightAdded = new TreeMap();
        diffMergeInfoProperties(rightDeleted, rightAdded, fromPropValue, null, toPropValue, null);
        leftDeleted = mergeMergeInfos(leftDeleted, rightDeleted);
        leftAdded = mergeMergeInfos(leftAdded, rightAdded);
        fromMergeInfo = mergeMergeInfos(fromMergeInfo, leftAdded);
        Map result = new TreeMap();
        walkMergeInfoHashForDiff(result, null, fromMergeInfo, leftDeleted);
        return formatMergeInfoToString(result);
    }
    
    public static void diffMergeInfoProperties(Map deleted, Map added, String fromPropValue, Map fromMergeInfo, String toPropValue, Map toMergeInfo) throws SVNException {
        if (fromPropValue.equals(toPropValue)) {
            return;
        } 
        fromMergeInfo = fromMergeInfo == null ? parseMergeInfo(new StringBuffer(fromPropValue), null) 
                                              : fromMergeInfo;
        toMergeInfo = toMergeInfo == null ? parseMergeInfo(new StringBuffer(toPropValue), null) 
                                          : toMergeInfo;
        diffMergeInfo(deleted, added, fromMergeInfo, toMergeInfo);
    }
    
    public static void diffMergeInfo(Map deleted, Map added, Map from, Map to) {
        from = from == null ? Collections.EMPTY_MAP : from;
        to = to == null ? Collections.EMPTY_MAP : to;
        if (!from.isEmpty() && to.isEmpty()) {
            dupMergeInfo(from, deleted);
        } else if (from.isEmpty() && !to.isEmpty()) {
            dupMergeInfo(to, added);
        } else if (!from.isEmpty() && !to.isEmpty()) {
            walkMergeInfoHashForDiff(deleted, added, from, to);
        }
    }
    
    public static Map dupMergeInfo(Map srcsToRangeLists, Map target) {
        if (srcsToRangeLists == null) {
            return null;
        }
        target = target == null ? new TreeMap() : target;
        for (Iterator paths = srcsToRangeLists.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) srcsToRangeLists.get(path);
            target.put(path, rangeList.dup());
        }
        return target;
    }
    
    public static Map parseMergeInfo(StringBuffer mergeInfo, Map srcPathsToRangeLists) throws SVNException {
        srcPathsToRangeLists = srcPathsToRangeLists == null ? new TreeMap() : srcPathsToRangeLists;
        if (mergeInfo.length() == 0) {
            return srcPathsToRangeLists;
        }

        while (mergeInfo.length() > 0) {
            int ind = mergeInfo.indexOf(":");
            if (ind == -1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, "Pathname not terminated by ':'");
                SVNErrorManager.error(err);
            }
            String path = mergeInfo.substring(0, ind);
            mergeInfo = mergeInfo.delete(0, ind + 1);
            SVNMergeRange[] ranges = parseRanges(mergeInfo);
            if (mergeInfo.length() != 0 && mergeInfo.charAt(0) != '\n') {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, "Could not find end of line in range list line in ''{0}''", mergeInfo);
                SVNErrorManager.error(err);
            }
            if (mergeInfo.length() > 0) {
                mergeInfo = mergeInfo.deleteCharAt(0);
            }
            Arrays.sort(ranges);
            srcPathsToRangeLists.put(path, new SVNMergeRangeList(ranges));
        }
        
        return srcPathsToRangeLists;
    }

    /**
     * Each element of the resultant array is formed like this:
     * %s:%ld-%ld,.. where the first %s is a merge src path 
     * and %ld-%ld is startRev-endRev merge range.
     */
    public static String[] formatMergeInfoToArray(Map srcsToRangeLists) {
        srcsToRangeLists = srcsToRangeLists == null ? Collections.EMPTY_MAP : srcsToRangeLists;
        String[] pathRanges = new String[srcsToRangeLists.size()];
        int k = 0;
        for (Iterator paths = srcsToRangeLists.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) srcsToRangeLists.get(path);
            String output = path + ':' + rangeList;  
            pathRanges[k++] = output;
        }
        return pathRanges;
    }

    public static String formatMergeInfoToString(Map srcsToRangeLists) {
        String[] infosArray = formatMergeInfoToArray(srcsToRangeLists);
        String result = "";
        for (int i = 0; i < infosArray.length; i++) {
            result += infosArray[i];
            if (i < infosArray.length - 1) {
                result += '\n';
            }
        }
        return result;
    }

    public static void elideMergeInfo(Map parentMergeInfo, Map childMergeInfo, File path, 
                                      String pathSuffix, SVNWCAccess access) throws SVNException {
        if (childMergeInfo.isEmpty()) {
            return;
        }
        
        boolean noElide = true;
        boolean elidePartially = false;
        boolean elideFull = false;
        
        Map mergeInfo = parentMergeInfo;
        if (pathSuffix != null && !parentMergeInfo.isEmpty()) {
            mergeInfo = new TreeMap();
            for (Iterator paths = parentMergeInfo.keySet().iterator(); paths.hasNext();) {
                String mergeSrcPath = (String) paths.next();
                mergeInfo.put(SVNPathUtil.concatToAbs(mergeSrcPath, pathSuffix), parentMergeInfo.get(mergeSrcPath));
            }
        } 
        
        Map childEmptyMergeInfo = new HashMap();
        Map childNonEmptyMergeInfo = new TreeMap();
        fillEmptyRangeListsUniqueToChild(childEmptyMergeInfo, childNonEmptyMergeInfo, 
                                         childMergeInfo, mergeInfo);
        
        if (!childEmptyMergeInfo.isEmpty()) {
            noElide = false;
            if (childNonEmptyMergeInfo.isEmpty()) {
                elideFull = true;
            } else {
                elidePartially = true;
            }
        }
        
        if (noElide && !mergeInfo.isEmpty()) {
            Map parentNonEmptyMergeInfo = new TreeMap();
            fillEmptyRangeListsUniqueToChild(null, parentNonEmptyMergeInfo, 
                                             mergeInfo, childMergeInfo);
            if (mergeInfoEquals(parentNonEmptyMergeInfo, childMergeInfo)) {
                noElide = false;
                elideFull = true;
            }
        }
        
        if (!elideFull && !mergeInfo.isEmpty()) {
            if (mergeInfoEquals(childNonEmptyMergeInfo, mergeInfo)) {
                elideFull = true;
                noElide = elidePartially = false;
            }
        }
        
        if (elideFull) {
            SVNPropertiesManager.setProperty(access, path, SVNProperty.MERGE_INFO, null, true);
        } else if (elidePartially) {
            String value = childNonEmptyMergeInfo.isEmpty() ? 
                                            null : formatMergeInfoToString(childNonEmptyMergeInfo);
            SVNPropertiesManager.setProperty(access, path, SVNProperty.MERGE_INFO, value, true);
        }
    }
    
    public static boolean mergeInfoEquals(Map mergeInfo1, Map mergeInfo2) {
        if (mergeInfo1.size() == mergeInfo2.size()) {
            Map deleted = new HashMap();
            Map added = new HashMap();
            diffMergeInfo(deleted, added, mergeInfo1, mergeInfo2);
            return deleted.isEmpty() && added.isEmpty();
        }
        return false;
    }
    
    public static String findMergeSource(long revision, Map mergeInfo) {
        for (Iterator paths = mergeInfo.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(path);
            if (rangeList.includes(revision)) {
                return path;
            }
        }
        return null;
    }
    
    private static void fillEmptyRangeListsUniqueToChild(Map emptyRangeMergeInfo, 
                                                        Map nonEmptyRangeMergeInfo, 
                                                        Map childMergeInfo, 
                                                        Map parentMergeInfo) {
        for (Iterator paths = childMergeInfo.keySet().iterator(); paths.hasNext();) {
            String childPath = (String) paths.next();
            SVNMergeRangeList childRangeList = (SVNMergeRangeList) childMergeInfo.get(childPath);
            if (childRangeList.getSize() == 0 && (parentMergeInfo.isEmpty() || 
                !parentMergeInfo.containsKey(childPath))) {
                if (emptyRangeMergeInfo != null) {
                    emptyRangeMergeInfo.put(childPath, childRangeList.dup());
                }
            } else {
                if (nonEmptyRangeMergeInfo != null) {
                    nonEmptyRangeMergeInfo.put(childPath, childRangeList.dup());
                }
            }
        }
    }
    
    private static SVNMergeRange[] parseRanges(StringBuffer mergeInfo) throws SVNException {
        Collection ranges = new LinkedList();
        while (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n' && 
                Character.isWhitespace(mergeInfo.charAt(0))) {
            mergeInfo = mergeInfo.deleteCharAt(0);
        }
        if (mergeInfo.length() == 0 || mergeInfo.charAt(0) == '\n') {
            return null;
        }
        
        SVNMergeRange lastRange = null;
        while (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n') {
            long startRev = parseRevision(mergeInfo);
            if (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n' && 
                mergeInfo.charAt(0) != '-' && mergeInfo.charAt(0) != ',') {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                                                             "Invalid character ''{0}'' found in revision list", 
                                                             new Character(mergeInfo.charAt(0)));
                SVNErrorManager.error(err);
            }
            
            SVNMergeRange range = new SVNMergeRange(startRev, startRev);
            if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == '-') {
                mergeInfo = mergeInfo.deleteCharAt(0);
                long endRev = parseRevision(mergeInfo);
                range.setEndRevision(endRev);
            }
            if (mergeInfo.length() == 0 || mergeInfo.charAt(0) == '\n') {
                SVNMergeRange combinedRange = lastRange == null ? range : 
                                                                  lastRange.combine(range, false);
                if (lastRange != combinedRange) {
                    lastRange = combinedRange;
                    ranges.add(lastRange);
                }
                return (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
            } else if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == ',') {
                SVNMergeRange combinedRange = lastRange == null ? range :
                                                                  lastRange.combine(range, false);
                if (lastRange != combinedRange) {
                    lastRange = combinedRange;
                    ranges.add(lastRange);
                }
                mergeInfo = mergeInfo.deleteCharAt(0);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                                                             "Invalid character ''{0}'' found in range list", 
                                                             mergeInfo.length() > 0 ?  mergeInfo.charAt(0) + "" : "");
                SVNErrorManager.error(err);
            }
        }
        
        if (mergeInfo.length() == 0 || mergeInfo.charAt(0) != '\n' ) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, "Range list parsing ended before hitting newline");
            SVNErrorManager.error(err);
        }
        
        return (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
    }

    private static long parseRevision(StringBuffer mergeInfo) throws SVNException {
        int ind = 0;
        while (ind < mergeInfo.length() && Character.isDigit(mergeInfo.charAt(ind))) {
            ind++;
        }
        
        if (ind == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                                                         "Invalid revision number found parsing ''{0}''", 
                                                         mergeInfo.length() > 0 ? mergeInfo.charAt(0) + "" : "");
            SVNErrorManager.error(err);
        }
        
        String numberStr = mergeInfo.substring(0, ind);
        mergeInfo = mergeInfo.delete(0, ind);
        return Long.parseLong(numberStr);
    }
    
    private static void walkMergeInfoHashForDiff(Map deleted, Map added, Map from, Map to) {
        for (Iterator paths = from.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList fromRangeList = (SVNMergeRangeList) from.get(path);
            SVNMergeRangeList toRangeList = (SVNMergeRangeList) to.get(path);
            if (toRangeList != null) {
                SVNMergeRangeList deletedRangeList = fromRangeList.diff(toRangeList);
                SVNMergeRangeList addedRangeList = toRangeList.diff(fromRangeList);
                if (deleted != null && deletedRangeList.getSize() > 0) {
                    deleted.put(path, deletedRangeList);
                }
                if (added != null && addedRangeList.getSize() > 0) {
                    added.put(path, addedRangeList);
                }
            } else if (deleted != null) {
                deleted.put(path, fromRangeList.dup());
            }
        }
        
        if (added == null) {
            return;
        }
        
        for (Iterator paths = to.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList toRangeList = (SVNMergeRangeList) to.get(path);
            if (!from.containsKey(path)) {
                added.put(path, toRangeList.dup());
            }
        }        
    }
    
}
