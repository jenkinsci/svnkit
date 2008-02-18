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

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSEntry;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSParentPath;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfoManager {
    
    public Map getMergeInfo(String[] paths, FSRevisionRoot root, SVNMergeInfoInheritance inherit, 
            boolean includeDescendants) throws SVNException {
        Map mergeInfoAsHashes = getMergeInfoHashesForPaths(root, paths, inherit, includeDescendants);
        Map mergeInfo = new TreeMap();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            Map pathMergeInfo = (Map) mergeInfoAsHashes.get(path);
            if (pathMergeInfo != null) {
                mergeInfo.put(path, new SVNMergeInfo(path, pathMergeInfo));
            }
        }
        return mergeInfo;
    }
    
    private Map getMergeInfoHashesForPaths(FSRevisionRoot root, String[] paths, 
            SVNMergeInfoInheritance inherit, boolean includeDescendants) throws SVNException {
        Map result = new TreeMap();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            Map pathMergeInfoHash = getMergeInfoHashForPath(root, path, inherit);
            if (pathMergeInfoHash != null) {
                result.put(path, pathMergeInfoHash);
            }
            if (includeDescendants) {
                addDescendantMergeInfo(result, root, path);
            }
        }    
        return result;
    }

    private void addDescendantMergeInfo(Map result, FSRevisionRoot root, String path) throws SVNException {
        FSRevisionNode node = root.getRevisionNode(path);
        if (node.hasDescendantsWithMergeInfo()) {
            crawlDirectoryForMergeInfo(root, path, node, result);
        }
    }
    
    private Map crawlDirectoryForMergeInfo(FSRevisionRoot root, String path, FSRevisionNode node, 
            Map result) throws SVNException {
        FSFS fsfs = root.getOwner();
        Map entries = node.getDirEntries(fsfs);
        for (Iterator entriesIter = entries.values().iterator(); entriesIter.hasNext();) {
            FSEntry entry = (FSEntry) entriesIter.next();
            String kidPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(path, entry.getName()));
            FSRevisionNode kidNode = root.getRevisionNode(kidPath);
            if (kidNode.hasMergeInfo()) {
                SVNProperties propList = kidNode.getProperties(fsfs);
                String mergeInfoString = propList.getStringValue(SVNProperty.MERGE_INFO);
                if (mergeInfoString == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, 
                            "Node-revision #''{0}'' claims to have mergeinfo but doesn''t", entry.getId());
                    SVNErrorManager.error(err);
                }
                Map kidMergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(mergeInfoString), null);
                result.put(kidPath, kidMergeInfo);
            }
            if (kidNode.hasDescendantsWithMergeInfo()) {
                crawlDirectoryForMergeInfo(root, path, node, result);
            }
        }
        return result;
    }
    
    private Map getMergeInfoHashForPath(FSRevisionRoot revRoot, String path, 
            SVNMergeInfoInheritance inherit) throws SVNException {
        Map mergeInfoHash = null;
        FSParentPath parentPath = revRoot.openPath(path, true, true);
        if (inherit == SVNMergeInfoInheritance.NEAREST_ANCESTOR && parentPath.getParent() == null) {
            return mergeInfoHash;
        }
        
        FSParentPath nearestAncestor = null;
        if (inherit == SVNMergeInfoInheritance.NEAREST_ANCESTOR) {
            nearestAncestor = parentPath.getParent();
        } else {
            nearestAncestor = parentPath;
        }
        
        FSFS fsfs = revRoot.getOwner();
        while (true) {
            boolean hasMergeInfo = nearestAncestor.getRevNode().hasMergeInfo();
            if (hasMergeInfo) {
                break;
            }
            
            if (inherit == SVNMergeInfoInheritance.EXPLICIT) {
                return mergeInfoHash;
            }
            nearestAncestor = nearestAncestor.getParent();
            if (nearestAncestor == null) {
                return mergeInfoHash;
            }
        }
        
        SVNProperties propList = nearestAncestor.getRevNode().getProperties(fsfs);
        String mergeInfoString = propList.getStringValue(SVNProperty.MERGE_INFO);
        if (mergeInfoString == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, 
                    "Node-revision ''{0}@{1,number,integer}'' claims to have mergeinfo but doesn''t", 
                    new Object[] { nearestAncestor.getAbsPath(), new Long(revRoot.getRevision()) });
            SVNErrorManager.error(err);
        }
        
        if (nearestAncestor == parentPath) {
            return SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(mergeInfoString), null);
        } 
        
        Map tmpMergeInfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(mergeInfoString), null); 
        tmpMergeInfo = SVNMergeInfoUtil.getInheritableMergeInfo(tmpMergeInfo, null, 
                SVNRepository.INVALID_REVISION, SVNRepository.INVALID_REVISION);
        mergeInfoHash = appendToMergedFroms(tmpMergeInfo, parentPath.getRelativePath(nearestAncestor));
        return mergeInfoHash;
    }
    
    private Map appendToMergedFroms(Map mergeInfo, String pathComponent) {
        Map result = new TreeMap(); 
        for (Iterator pathsIter = mergeInfo.keySet().iterator(); pathsIter.hasNext();) {
            String path = (String) pathsIter.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(path);
            result.put(SVNPathUtil.append(path, pathComponent), rangeList.dup());
        }
        return result;
    }
    
}
