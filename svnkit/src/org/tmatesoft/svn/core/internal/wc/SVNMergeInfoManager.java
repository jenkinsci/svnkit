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
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;


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
    
    public void updateIndex(File dbDirectory, long newRevision, SVNProperties mergeInfo) throws SVNException {
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
    
    private void indexTxnMergeInfo(long revision, SVNProperties pathsToMergeInfos) throws SVNException {
        for (Iterator paths = pathsToMergeInfos.nameSet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            String mergeInfoToParse = SVNPropertyValue.getPropertyAsString(pathsToMergeInfos.getSVNPropertyValue(path));
            indexPathMergeInfo(revision, path, mergeInfoToParse);
        }
    }

    private void indexPathMergeInfo(long revision, String path, String mergeInfoToParse) throws SVNException {
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
            SVNMergeRangeList rangeList = removeMergeInfo ? 
            		SVNMergeRangeList.NO_MERGE_INFO_LIST : (SVNMergeRangeList) mergeInfo.get(srcMergePath);
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
                if (mergeSrcsToMergeRangeLists != null) {
                    SVNMergeInfo mergeInfo = new SVNMergeInfo(path, mergeSrcsToMergeRangeLists);
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
                Map parentSrcsToRangeLists = getInheritableMergeInfo(parentInfo.getMergeSourcesToMergeLists(), 
                                                                     null, SVNRepository.INVALID_REVISION, 
                                                                     SVNRepository.INVALID_REVISION);
                Map translatedSrcsToRangeLists = new TreeMap();
                for (Iterator paths = parentSrcsToRangeLists.keySet().iterator(); paths.hasNext();) {
                    String mergeSrcPath = (String) paths.next();
                    SVNMergeRangeList rangeList = (SVNMergeRangeList) parentSrcsToRangeLists.get(mergeSrcPath);
                    translatedSrcsToRangeLists.put(SVNPathUtil.append(mergeSrcPath, name), rangeList);
                }
                SVNMergeInfo translatedMergeInfo = new SVNMergeInfo(path, translatedSrcsToRangeLists); 
                mergeInfoCache.put(path, translatedMergeInfo);
                result.put(path, translatedMergeInfo);
            }
        }
        return result;
    }
    
    public static SVNMergeInfoManager createMergeInfoManager(ISVNDBProcessor dbProcessor) {
        if (dbProcessor == null) {
            dbProcessor =  new SVNSQLiteDBProcessor();
        }
        return new SVNMergeInfoManager(dbProcessor);
    }
    
    public static Map mergeMergeInfos(Map originalSrcsToRangeLists, Map changedSrcsToRangeLists) throws SVNException {
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
    
    public static String combineForkedMergeInfoProperties(String fromPropValue, String workingPropValue, 
            String toPropValue) throws SVNException {
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
        Map result = removeMergeInfo(leftDeleted, fromMergeInfo);
        return formatMergeInfoToString(result);
    }
    
    public static void diffMergeInfoProperties(Map deleted, Map added, String fromPropValue, Map fromMergeInfo, 
            String toPropValue, Map toMergeInfo) throws SVNException {
        if (fromPropValue.equals(toPropValue)) {
            return;
        } 
        fromMergeInfo = fromMergeInfo == null ? parseMergeInfo(new StringBuffer(fromPropValue), null) 
                                              : fromMergeInfo;
        toMergeInfo = toMergeInfo == null ? parseMergeInfo(new StringBuffer(toPropValue), null) 
                                          : toMergeInfo;
        diffMergeInfo(deleted, added, fromMergeInfo, toMergeInfo, false);
    }
    
    public static void diffMergeInfo(Map deleted, Map added, Map from, Map to, 
            boolean considerInheritance) {
        from = from == null ? Collections.EMPTY_MAP : from;
        to = to == null ? Collections.EMPTY_MAP : to;
        if (!from.isEmpty() && to.isEmpty()) {
            dupMergeInfo(from, deleted);
        } else if (from.isEmpty() && !to.isEmpty()) {
            dupMergeInfo(to, added);
        } else if (!from.isEmpty() && !to.isEmpty()) {
            walkMergeInfoHashForDiff(deleted, added, from, to, considerInheritance);
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
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                        "Pathname not terminated by ':'");
                SVNErrorManager.error(err);
            }
            String path = mergeInfo.substring(0, ind);
            mergeInfo = mergeInfo.delete(0, ind + 1);
            SVNMergeRange[] ranges = parseRevisionList(mergeInfo, path);
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

    public static boolean shouldElideMergeInfo(Map parentMergeInfo, Map childMergeInfo, String pathSuffix) {
        boolean elides = false;
        if (childMergeInfo != null) {
            if (childMergeInfo.isEmpty()) {
                if (parentMergeInfo == null || parentMergeInfo.isEmpty()) {
                    elides = true;
                }
            } else if (!(parentMergeInfo == null || parentMergeInfo.isEmpty())) {
                Map pathTweakedMergeInfo = parentMergeInfo;
                if (pathSuffix != null) {
                    pathTweakedMergeInfo = new TreeMap();
                    for (Iterator paths = parentMergeInfo.keySet().iterator(); paths.hasNext();) {
                        String mergeSrcPath = (String) paths.next();
                        pathTweakedMergeInfo.put(SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeSrcPath, 
                                pathSuffix)), parentMergeInfo.get(mergeSrcPath));
                    }
                } 
                elides = mergeInfoEquals(pathTweakedMergeInfo, childMergeInfo, true);
            }
        }
        return elides;
    }
    
    public static void elideMergeInfo(Map parentMergeInfo, Map childMergeInfo, File path, 
    		String pathSuffix, SVNWCAccess access) throws SVNException {
        boolean elides = shouldElideMergeInfo(parentMergeInfo, childMergeInfo, pathSuffix);
        if (elides) {
            SVNPropertiesManager.setProperty(access, path, SVNProperty.MERGE_INFO, null, true);
        }
    }
    
    public static boolean mergeInfoEquals(Map mergeInfo1, Map mergeInfo2, 
            boolean considerInheritance) {
        mergeInfo1 = mergeInfo1 == null ? Collections.EMPTY_MAP : mergeInfo1;
        mergeInfo2 = mergeInfo2 == null ? Collections.EMPTY_MAP : mergeInfo2;
        
        if (mergeInfo1.size() == mergeInfo2.size()) {
            Map deleted = new HashMap();
            Map added = new HashMap();
            diffMergeInfo(deleted, added, mergeInfo1, mergeInfo2, considerInheritance);
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
    
    public static Map getInheritableMergeInfo(Map mergeInfo, String path, long startRev, long endRev) {
        Map inheritableMergeInfo = new TreeMap();
        if (mergeInfo != null) {
            for (Iterator paths = mergeInfo.keySet().iterator(); paths.hasNext();) {
                String mergeSrcPath = (String) paths.next();
                SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(mergeSrcPath);
                SVNMergeRangeList inheritableRangeList = null;
                if (path == null || path.equals(mergeSrcPath)) {
                    inheritableRangeList = rangeList.getInheritableRangeList(startRev, endRev);
                } else {
                    inheritableRangeList = rangeList.dup();
                }
                inheritableMergeInfo.put(mergeSrcPath, inheritableRangeList);
            }
        }
        return inheritableMergeInfo;
    }
    
    public static Map removeMergeInfo(Map eraser, Map whiteBoard) {
    	Map mergeInfo = new TreeMap();
    	walkMergeInfoHashForDiff(mergeInfo, null, whiteBoard, eraser, true);
    	return mergeInfo;
    }
    
    public static Map intersectMergeInfo(Map mergeInfo1, Map mergeInfo2) {
        Map mergeInfo = new TreeMap();
        for (Iterator pathsIter = mergeInfo1.keySet().iterator(); pathsIter.hasNext();) {
            String path = (String) pathsIter.next();
            SVNMergeRangeList rangeList1 = (SVNMergeRangeList) mergeInfo1.get(path);
            SVNMergeRangeList rangeList2 = (SVNMergeRangeList) mergeInfo2.get(path);
            if (rangeList2 != null) {
                rangeList2 = rangeList2.intersect(rangeList1);
                if (!rangeList2.isEmpty()) {
                    mergeInfo.put(path, rangeList2);
                }
            }
        }
        return mergeInfo;
    }
    
    private static SVNMergeRange[] parseRevisionList(StringBuffer mergeInfo, String path) throws SVNException {
        Collection ranges = new LinkedList();
        while (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n' && 
                Character.isWhitespace(mergeInfo.charAt(0))) {
            mergeInfo = mergeInfo.deleteCharAt(0);
        }
        if (mergeInfo.length() == 0 || mergeInfo.charAt(0) == '\n') {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
            		"Mergeinfo for ''{0}'' maps to an empty revision range", path);
            SVNErrorManager.error(err);
        }
        
        SVNMergeRange lastRange = null;
        while (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n') {
            long startRev = parseRevision(mergeInfo);
            if (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n' && 
                mergeInfo.charAt(0) != '-' && mergeInfo.charAt(0) != ',' && 
                mergeInfo.charAt(0) != '*') {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                        "Invalid character ''{0}'' found in revision list", 
                        new Character(mergeInfo.charAt(0)));
                SVNErrorManager.error(err);
            }
            
            SVNMergeRange range = new SVNMergeRange(startRev - 1, startRev, true);
            if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == '-') {
                mergeInfo = mergeInfo.deleteCharAt(0);
                long endRev = parseRevision(mergeInfo);
                if (startRev > endRev) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                            "Unable to parse reversed revision range ''{0,number,integer}-{1,number,integer}''",
                            new Object[] { new Long(startRev), new Long(endRev) });
                    SVNErrorManager.error(err);
                } else if (startRev == endRev) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                            "Unable to parse revision range ''{0,number,integer}-{1,number,integer}'' with same start and end revisions",
                            new Object[] { new Long(startRev), new Long(endRev) });
                    SVNErrorManager.error(err);
                }
                range.setEndRevision(endRev);
            }
            
            if (mergeInfo.length() == 0 || mergeInfo.charAt(0) == '\n') {
                lastRange = combineWithAdjacentLastRange(ranges, lastRange, range, false);
                return (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
            } else if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == ',') {
                lastRange = combineWithAdjacentLastRange(ranges, lastRange, range, false);
                mergeInfo = mergeInfo.deleteCharAt(0);
            } else if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == '*') {
                range.setInheritable(false);
                mergeInfo = mergeInfo.deleteCharAt(0);
                if (mergeInfo.length() == 0 || mergeInfo.charAt(0) == ',' || 
                        mergeInfo.charAt(0) == '\n') {
                    lastRange = combineWithAdjacentLastRange(ranges, lastRange, range, false);
                    if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == ',') {
                        mergeInfo = mergeInfo.deleteCharAt(0);
                    } else {
                        return (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
                    }
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                                                                 "Invalid character ''{0}'' found in range list", 
                                                                 mergeInfo.length() > 0 ?  mergeInfo.charAt(0) + "" : "");
                    SVNErrorManager.error(err);
                }
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
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REVISION_NUMBER_PARSE_ERROR, 
                                                         "Invalid revision number found parsing ''{0}''", 
                                                         mergeInfo.toString());
            SVNErrorManager.error(err);
        }
        
        String numberStr = mergeInfo.substring(0, ind);
        long rev = -1;
        try {
            rev = Long.parseLong(numberStr);
        } catch (NumberFormatException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REVISION_NUMBER_PARSE_ERROR, 
                                                         "Invalid revision number found parsing ''{0}''", 
                                                         mergeInfo.toString());
            SVNErrorManager.error(err);
        }

        if (rev < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REVISION_NUMBER_PARSE_ERROR, 
                                                         "Negative revision number found parsing ''{0}''", 
                                                         mergeInfo.toString());
            SVNErrorManager.error(err);
        }
        
        mergeInfo = mergeInfo.delete(0, ind);
        return rev;
    }
    
    private static void walkMergeInfoHashForDiff(Map deleted, Map added, Map from, Map to, 
            boolean considerInheritance) {
        for (Iterator paths = from.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList fromRangeList = (SVNMergeRangeList) from.get(path);
            SVNMergeRangeList toRangeList = (SVNMergeRangeList) to.get(path);
            if (toRangeList != null) {
                SVNMergeRangeList deletedRangeList = fromRangeList.diff(toRangeList, 
                                                                        considerInheritance);
                SVNMergeRangeList addedRangeList = toRangeList.diff(fromRangeList, 
                                                                    considerInheritance);
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
    
    private static SVNMergeRange combineWithAdjacentLastRange(Collection result, SVNMergeRange lastRange, 
            SVNMergeRange mRange, boolean dupMRange) throws SVNException {
        SVNMergeRange pushedMRange = mRange;
        if (lastRange != null) {
            if (lastRange.getStartRevision() <= mRange.getEndRevision() && 
                    mRange.getStartRevision() <= lastRange.getEndRevision()) {
                
                if (mRange.getStartRevision() < lastRange.getEndRevision()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                            "Parsing of overlapping revision ranges ''{0}'' and ''{1}'' is not supported",
                            new Object[] { lastRange.toString(), mRange.toString() });
                    SVNErrorManager.error(err);
                } else if (lastRange.isInheritable() == mRange.isInheritable()) {
                    lastRange.setEndRevision(mRange.getEndRevision());
                    return lastRange;
                }
            } else if (lastRange.getStartRevision() > mRange.getStartRevision()) {
                  SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                          "Unable to parse unordered revision ranges ''{0}'' and ''{1}''", 
                          new Object[] { lastRange.toString(), mRange.toString() });
                  SVNErrorManager.error(err);
            }
        }
        
        if (dupMRange) {
            pushedMRange = mRange.dup();
        }
        result.add(pushedMRange);
        lastRange = pushedMRange;
        return lastRange;
    }
}
