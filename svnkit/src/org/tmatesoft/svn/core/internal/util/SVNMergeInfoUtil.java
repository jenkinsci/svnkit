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

import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.ISVNCommitPathHandler;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNMergeInfoManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfoUtil {

	public static Map elideMergeInfoCatalog(Map mergeInfoCatalog) throws SVNException {
	    ElideMergeInfoCatalogHandler handler = new ElideMergeInfoCatalogHandler(mergeInfoCatalog);
	    ElideMergeInfoEditor editor = new ElideMergeInfoEditor(mergeInfoCatalog);
	    SVNCommitUtil.driveCommitEditor(handler, mergeInfoCatalog.keySet(), editor, -1);
	    List elidablePaths = handler.getElidablePaths();
	    for (Iterator elidablePathsIter = elidablePaths.iterator(); elidablePathsIter.hasNext();) {
            String elidablePath = (String) elidablePathsIter.next();
            mergeInfoCatalog.remove(elidablePath);
        }
	    return mergeInfoCatalog;
	}
	
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
	
	private static class ElideMergeInfoCatalogHandler implements ISVNCommitPathHandler {
        private Map myMergeInfoCatalog;
        private List myElidablePaths;
        
        public ElideMergeInfoCatalogHandler(Map mergeInfoCatalog) {
            myMergeInfoCatalog = mergeInfoCatalog;
            myElidablePaths = new LinkedList();
        }
        
        public boolean handleCommitPath(String path, ISVNEditor editor) throws SVNException {
	        if (!path.startsWith("/")) {
	            path = "/" + path;
	        }
            ElideMergeInfoEditor elideEditor = (ElideMergeInfoEditor) editor;
	        String inheritedMergeInfoPath = elideEditor.getInheritedMergeInfoPath();
	        if (inheritedMergeInfoPath == null || "/".equals(path)) {
	            return false;
	        }
	        String pathSuffix = SVNPathUtil.getPathAsChild(inheritedMergeInfoPath, path);
	        if (pathSuffix == null) {
	            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "path suffix is null");
	            SVNErrorManager.error(err);
	        }
	        boolean elides = SVNMergeInfoManager.shouldElideMergeInfo((Map) myMergeInfoCatalog.get(inheritedMergeInfoPath), 
	                (Map) myMergeInfoCatalog.get(path), pathSuffix);
	        if (elides) {
	            myElidablePaths.add(path);
	        }
	        return false;
	    }
        
        public List getElidablePaths() {
            return myElidablePaths;
        }
	}
	
	private static class ElideMergeInfoEditor implements ISVNEditor {

	    private Map myMergeInfoCatalog;
	    private ElideMergeInfoCatalogDirBaton myCurrentDirBaton;
	    
	    public ElideMergeInfoEditor(Map mergeInfoCatalog) {
	        myMergeInfoCatalog = mergeInfoCatalog;
	    }
	    
        public void abortEdit() throws SVNException {
        }

        public void absentDir(String path) throws SVNException {
        }

        public void absentFile(String path) throws SVNException {
        }

        public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        }

        public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        }

        public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        }

        public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        }

        public void closeDir() throws SVNException {
        }

        public SVNCommitInfo closeEdit() throws SVNException {
            return null;
        }

        public void closeFile(String path, String textChecksum) throws SVNException {
        }

        public void deleteEntry(String path, long revision) throws SVNException {
        }

        public void openDir(String path, long revision) throws SVNException {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            
            ElideMergeInfoCatalogDirBaton dirBaton = new ElideMergeInfoCatalogDirBaton();
            if (myMergeInfoCatalog.get(path) != null) {
                dirBaton.myInheritedMergeInfoPath = path;
            } else {
                dirBaton.myInheritedMergeInfoPath = myCurrentDirBaton.myInheritedMergeInfoPath;
            }
            myCurrentDirBaton = dirBaton;
        }

        public void openFile(String path, long revision) throws SVNException {
        }

        public void openRoot(long revision) throws SVNException {
            myCurrentDirBaton = new ElideMergeInfoCatalogDirBaton();
        }

        public void targetRevision(long revision) throws SVNException {
        }

        public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        }

        public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
            return SVNFileUtil.DUMMY_OUT;
        }

        public void textDeltaEnd(String path) throws SVNException {
        }

        public String getInheritedMergeInfoPath() {
            return myCurrentDirBaton.myInheritedMergeInfoPath;
        }
        
        private class ElideMergeInfoCatalogDirBaton {
            private String myInheritedMergeInfoPath;
        }
	    
	}


}
