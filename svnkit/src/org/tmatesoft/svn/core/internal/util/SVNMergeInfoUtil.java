/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNMergeRangeList;

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfoUtil {

	public static Map adjustMergeInfoSourcePaths(Map mergeInfo, String walkPath, Map wcMergeInfo) {
        mergeInfo = mergeInfo == null ? new TreeMap() : mergeInfo;
		for (Iterator paths = wcMergeInfo.keySet().iterator(); paths.hasNext();) {
            String srcMergePath = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) wcMergeInfo.get(srcMergePath); 
            mergeInfo.put(SVNPathUtil.getAbsolutePath(SVNPathUtil.append(srcMergePath, walkPath)), rangeList);
        }
		return mergeInfo;
	}
	
	public static boolean removeEmptyRangeLists(Map mergeInfo) {
		boolean removedSomeRanges = false;
		if (mergeInfo != null) {
			for (Iterator mergeInfoIter = mergeInfo.entrySet().iterator(); mergeInfoIter.hasNext();) {
				Map.Entry mergeInfoEntry = (Map.Entry) mergeInfoIter.next();
				SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfoEntry.getValue();
				if (rangeList.isEmpty()) {
					mergeInfoIter.remove();
					removedSomeRanges = true;
				}
			}
		}
		return removedSomeRanges;
	}
}
