/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNStatusEditor {
    
    private SVNWCAccess myWCAccess;
    private SVNAdminAreaInfo myAdminInfo;

    private boolean myIsReportAll;
    private boolean myIsNoIgnore;
    private SVNDepth myDepth;
    
    private ISVNStatusHandler myStatusHandler;

    private Map myExternalsMap;
    private Collection myGlobalIgnores;
    
    private SVNURL myRepositoryRoot;
    private Map myRepositoryLocks;
    private long myTargetRevision;
    
    public SVNStatusEditor(ISVNOptions options, SVNWCAccess wcAccess, SVNAdminAreaInfo info, boolean noIgnore, boolean reportAll, SVNDepth depth,
            ISVNStatusHandler handler) {
        myWCAccess = wcAccess;
        myAdminInfo = info;
        myIsNoIgnore = noIgnore;
        myIsReportAll = reportAll;
        myDepth = depth;
        myStatusHandler = handler;
        myExternalsMap = new HashMap();
        myGlobalIgnores = getGlobalIgnores(options);
        myTargetRevision = -1;
    }
    
    public long getTargetRevision() {
        return myTargetRevision;
    }

    public void targetRevision(long revision) {
        myTargetRevision = revision;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        try {
            if (hasTarget()) {
                File path = myAdminInfo.getAnchor().getFile(myAdminInfo.getTargetName());
                SVNFileType type = SVNFileType.getType(path);
                if (type == SVNFileType.DIRECTORY) {
                    SVNEntry entry = myWCAccess.getEntry(path, false);
                    if (entry == null) {
                        getDirStatus(null, myAdminInfo.getAnchor(), myAdminInfo.getTargetName(), 
                                SVNDepth.EMPTY, myIsReportAll, true, null, true, myStatusHandler);
                    } else {
                        SVNAdminArea target = myWCAccess.retrieve(path);
                        getDirStatus(null, target, null, 
                                myDepth, myIsReportAll, myIsNoIgnore, null, false, myStatusHandler);
                    }
                } else {
                    getDirStatus(null, myAdminInfo.getAnchor(), myAdminInfo.getTargetName(), SVNDepth.EMPTY, myIsReportAll, true, null, true, myStatusHandler);
                }
            } else {
                getDirStatus(null, myAdminInfo.getAnchor(), null, 
                        myDepth, myIsReportAll, myIsNoIgnore, null, false, myStatusHandler);
            }
        } finally {
            cleanup();
        }
        return null;
    }
    
    public void setRepositoryInfo(SVNURL root, Map repositoryLocks) {
        myRepositoryRoot = root;
        myRepositoryLocks = repositoryLocks;
    }
    
    protected void getDirStatus(SVNEntry parentEntry, SVNAdminArea dir, String entryName, 
            SVNDepth depth, boolean getAll, boolean noIgnore, Collection ignorePatterns, boolean skipThisDir,
            ISVNStatusHandler handler) throws SVNException {
        myWCAccess.checkCancelled();
        depth = depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth;
        Map childrenFiles = getChildrenFiles(dir.getRoot());
        SVNEntry dirEntry = myWCAccess.getEntry(dir.getRoot(), false);

        String externals = dir.getProperties(dir.getThisDirName()).getStringPropertyValue(SVNProperty.EXTERNALS);
        if (externals != null) {
            String path = dir.getRelativePath(myAdminInfo.getAnchor());
            myAdminInfo.addExternal(path, externals, externals);
            myAdminInfo.addDepth(path, dirEntry.getDepth());
            
            SVNExternal[] externalsInfo = SVNExternal.parseExternals(dir.getRelativePath(myAdminInfo.getAnchor()), externals);
            for (int i = 0; i < externalsInfo.length; i++) {
                SVNExternal external = externalsInfo[i];
                myExternalsMap.put(SVNPathUtil.append(path, external.getPath()), external);
            }
        }
        if (entryName != null) {
            File file = (File) childrenFiles.get(entryName);
            SVNEntry entry = dir.getEntry(entryName, false);
            if (entry != null) {
                SVNFileType fileType = SVNFileType.getType(file);
                boolean special = fileType == SVNFileType.SYMLINK;
                SVNNodeKind fileKind = SVNFileType.getNodeKind(fileType);
                handleDirEntry(dir, entryName, dirEntry, entry, 
                        fileKind, special, depth, getAll, noIgnore, handler);
            } else {
                if (ignorePatterns == null) {
                    ignorePatterns = getIgnorePatterns(dir, myGlobalIgnores);
                }
                if (file == null) {
                    file = new File(dir.getRoot(), entryName);
                }
                sendUnversionedStatus(file, entryName, SVNNodeKind.NONE, false, dir, ignorePatterns, noIgnore, handler);
            }
            return;
        }

        if (!skipThisDir) {
            SVNStatus status = assembleStatus(dir.getRoot(), dir, dirEntry, parentEntry, 
                    SVNNodeKind.DIR, false, isReportAll(), false);
            if (status != null && handler != null) {
                handler.handleStatus(status);
            }
        }

        if (depth == SVNDepth.EMPTY) {
            return;
        }
        // iterate over files.
        childrenFiles = new TreeMap(childrenFiles);
        for (Iterator files = childrenFiles.keySet().iterator(); files.hasNext();) {
            String fileName = (String) files.next();
            if (dir.getEntry(fileName, false) != null || SVNFileUtil.getAdminDirectoryName().equals(fileName)) {
                continue;
            }
            if (ignorePatterns == null) {
                ignorePatterns = getIgnorePatterns(dir, myGlobalIgnores);
            }
            File file = (File) childrenFiles.get(fileName);
            sendUnversionedStatus(file, fileName, SVNNodeKind.NONE, false, dir, ignorePatterns, noIgnore, handler);
        }
        for(Iterator entries = dir.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (dir.getThisDirName().equals(entry.getName())) {
                continue;
            }
            if (depth == SVNDepth.FILES && entry.isDirectory()) {
                continue;
            }
            File file = (File) childrenFiles.get(entry.getName());
            SVNFileType fileType = SVNFileType.getType(file);
            boolean special = fileType == SVNFileType.SYMLINK;
            SVNNodeKind fileKind = SVNFileType.getNodeKind(fileType);
            handleDirEntry(dir, entry.getName(), dirEntry, entry, 
                    fileKind, special, depth == SVNDepth.INFINITY ? depth : SVNDepth.EMPTY, 
                            getAll, noIgnore, handler);
        }
    }

    protected void cleanup() {
        if (hasTarget()) { 
            myAdminInfo.removeExternal("");
            myAdminInfo.removeDepth("");
        }
    }
    
    protected SVNAdminArea getAnchor() {
        return myAdminInfo.getAnchor();
    }

    protected SVNWCAccess getWCAccess() {
        return myWCAccess;
    }
    
    protected SVNDepth getDepth() {
        return myDepth;
    }
    
    protected boolean isReportAll() {
        return myIsReportAll;
    }
    
    protected boolean isNoIgnore() {
        return myIsNoIgnore;
    }
    
    protected SVNAdminAreaInfo getAdminAreaInfo() {
        return myAdminInfo;
    }
    
    protected ISVNStatusHandler getDefaultHandler() {
        return myStatusHandler;
    }
    
    protected boolean hasTarget() {
        return myAdminInfo.getTargetName() != null && !"".equals(myAdminInfo.getTargetName());
    }
    
    protected SVNLock getLock(SVNURL url) {
    	return SVNStatusUtil.getLock(myRepositoryLocks, url, myRepositoryRoot);
    }

    private void handleDirEntry(SVNAdminArea dir, String entryName, SVNEntry dirEntry, SVNEntry entry, SVNNodeKind fileKind, boolean special, 
            SVNDepth depth, boolean getAll, boolean noIgnore, ISVNStatusHandler handler) throws SVNException {
        File path = dir.getFile(entryName);
        
        if (fileKind == SVNNodeKind.DIR) {
            SVNEntry fullEntry = entry;
            if (entry.getKind() == fileKind) {
                fullEntry = myWCAccess.getVersionedEntry(path, false);
            }
            if (fullEntry != entry && (depth == SVNDepth.UNKNOWN || depth == SVNDepth.IMMEDIATES
                    || depth == SVNDepth.INFINITY)) {
                SVNAdminArea childDir = myWCAccess.retrieve(path);
                getDirStatus(dirEntry, childDir, null, depth, getAll, noIgnore, null, false, handler);
            } else if (fullEntry != entry) {
                // get correct dir.
                SVNAdminArea childDir = myWCAccess.retrieve(path);
                SVNStatus status = assembleStatus(path, childDir, fullEntry, dirEntry, fileKind, 
                		special, getAll, false);
                if (status != null && handler != null) {
                    handler.handleStatus(status);
                }
            } else {
                SVNStatus status = assembleStatus(path, dir, fullEntry, dirEntry, fileKind, 
                		special, getAll, false);
                if (status != null && handler != null) {
                    handler.handleStatus(status);
                }
            }
        } else {
            SVNStatus status = assembleStatus(path, dir, entry, dirEntry, fileKind, special, 
            		getAll, false);
            if (status != null && handler != null) {
                handler.handleStatus(status);
            }
        }
    }
    
    private void sendUnversionedStatus(File file, String name, SVNNodeKind fileType, boolean special, SVNAdminArea dir, Collection ignorePatterns, 
            boolean noIgnore, ISVNStatusHandler handler) throws SVNException {
        boolean isIgnored = isIgnored(ignorePatterns, name);
        String path = dir.getRelativePath(myAdminInfo.getAnchor());
        path = SVNPathUtil.append(path, name);  
        boolean isExternal = isExternal(path);
        SVNStatus status = assembleStatus(file, dir, null, null, fileType, special, true, 
        		isIgnored);
        if (status != null) {
            if (isExternal) {
                status.setContentsStatus(SVNStatusType.STATUS_EXTERNAL);
            }
            if (handler != null && noIgnore || !isIgnored || isExternal || status.getRemoteLock() != null) {
                handler.handleStatus(status);
            }
        }
    }
    
    protected SVNStatus assembleStatus(File file, SVNAdminArea dir, 
            SVNEntry entry, SVNEntry parentEntry, SVNNodeKind fileKind, boolean special, 
            boolean reportAll, boolean isIgnored) throws SVNException {

    	return SVNStatusUtil.assembleStatus(file, dir, entry, parentEntry, fileKind, special, reportAll, 
    			isIgnored, myRepositoryLocks, myRepositoryRoot, myWCAccess);
    }
    
    private boolean isExternal(String path) {
        if (!myExternalsMap.containsKey(path)) {
            // check if path is external parent.            
            for (Iterator paths = myExternalsMap.keySet().iterator(); paths.hasNext();) {
                String externalPath = (String) paths.next();
                if (externalPath.startsWith(path + "/")) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
    
    public static Collection getIgnorePatterns(SVNAdminArea dir, Collection globalIgnores) throws SVNException {
        String localIgnores = dir.getProperties("").getStringPropertyValue(SVNProperty.IGNORE);
        if (localIgnores != null) {
            Collection patterns = new HashSet();
            patterns.addAll(globalIgnores);
            for(StringTokenizer tokens = new StringTokenizer(localIgnores, "\r\n"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken().trim();
                if (token.length() > 0) {
                    patterns.add(token);
                }
            }
            return patterns;
        }
        return globalIgnores;
    }
    
    public static Collection getGlobalIgnores(ISVNOptions options) {
        if (options != null) {
            String[] ignores = options.getIgnorePatterns();
            if (ignores != null) {
                Collection patterns = new HashSet();
                for (int i = 0; i < ignores.length; i++) {
                    patterns.add(ignores[i]);
                }
                return patterns;
            }
        }
        return Collections.EMPTY_SET;
    }
    
    public static boolean isIgnored(Collection patterns, String name) {
        for (Iterator ps = patterns.iterator(); ps.hasNext();) {
            String pattern = (String) ps.next();
            if (DefaultSVNOptions.matches(pattern, name)) {
                return true;
            }
        }
        return false;
    }
    
    private static Map getChildrenFiles(File parent) {
        File[] children = SVNFileListUtil.listFiles(parent);
        if (children != null) {
            Map map = new HashMap();
            for (int i = 0; i < children.length; i++) {
                map.put(children[i].getName(), children[i]);
            }
            return map;
        }
        return Collections.EMPTY_MAP;
    }
    
}
