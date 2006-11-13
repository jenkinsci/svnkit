/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
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
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNStatusEditor {
    
    private SVNWCAccess myWCAccess;
    private SVNAdminAreaInfo myAdminInfo;

    private boolean myIsReportAll;
    private boolean myIsNoIgnore;
    private boolean myIsDescend;

    private ISVNStatusHandler myStatusHandler;

    private Map myExternalsMap;
    private Collection myGlobalIgnores;
    
    private SVNURL myRepositoryRoot;
    private Map myRepositoryLocks;
    private long myTargetRevision;
    private Map myExternalsInfo;
    
    public SVNStatusEditor(ISVNOptions options, SVNWCAccess wcAccess, SVNAdminAreaInfo info, boolean noIgnore, boolean reportAll, boolean descend,
            ISVNStatusHandler handler) {
        myWCAccess = wcAccess;
        myAdminInfo = info;
        myIsNoIgnore = noIgnore;
        myIsReportAll = reportAll;
        myIsDescend = descend;
        myStatusHandler = handler;
        myExternalsMap = new HashMap();
        myExternalsInfo = new HashMap();
        myGlobalIgnores = getGlobalIgnores(options);
        myTargetRevision = -1;
    }
    
    public void setExternals(Map externals) {
        if (externals != null) {
            myExternalsMap = externals;
        }
    }
    
    public Map getExternals() {
        return myExternalsMap;
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
                                false, myIsReportAll, true, null, true, myStatusHandler);
                    } else {
                        SVNAdminArea target = myWCAccess.retrieve(path);
                        getDirStatus(null, target, null, 
                                myIsDescend, myIsReportAll, myIsNoIgnore, null, false, myStatusHandler);
                    }
                } else {
                    getDirStatus(null, myAdminInfo.getAnchor(), myAdminInfo.getTargetName(), false, myIsReportAll, true, null, true, myStatusHandler);
                }
            } else {
                getDirStatus(null, myAdminInfo.getAnchor(), null, 
                        myIsDescend, myIsReportAll, myIsNoIgnore, null, false, myStatusHandler);
            }
        } finally {
            if (hasTarget() && myExternalsInfo.containsKey(myAdminInfo.getAnchor())) {
                SVNExternalInfo[] anchorExternals = (SVNExternalInfo[]) myExternalsInfo.get(myAdminInfo.getAnchor());
                for (int i = 0; i < anchorExternals.length; i++) {
                    myExternalsMap.remove(anchorExternals[i].getPath());
                }
            }
            cleanup();
        }
        return null;
    }
    
    public void setRepositoryInfo(SVNURL root, Map repositoryLocks) {
        myRepositoryRoot = root;
        myRepositoryLocks = repositoryLocks;
    }
    
    protected void getDirStatus(SVNEntry parentEntry, SVNAdminArea dir, String entryName, 
            boolean descend, boolean getAll, boolean noIgnore, Collection ignorePatterns, boolean skipThisDir,
            ISVNStatusHandler handler) throws SVNException {
        myWCAccess.checkCancelled();
        
        Map childrenFiles = getChildrenFiles(dir.getRoot());
        SVNEntry dirEntry = myWCAccess.getEntry(dir.getRoot(), false);

        String externals = dir.getProperties("").getPropertyValue(SVNProperty.EXTERNALS);
        if (externals != null) {
            SVNExternalInfo[] externalsInfo = SVNWCAccess.parseExternals(dir.getRelativePath(myAdminInfo.getAnchor()), externals);
            for (int i = 0; i < externalsInfo.length; i++) {
                SVNExternalInfo external = externalsInfo[i];
                myExternalsMap.put(external.getPath(), external);
            }
            myExternalsInfo.put(dir, externalsInfo);
        }
        if (entryName != null) {
            File file = (File) childrenFiles.get(entryName);
            SVNEntry entry = dir.getEntry(entryName, false);
            if (entry != null) {
                SVNFileType fileType = SVNFileType.getType(file);
                boolean special = fileType == SVNFileType.SYMLINK;
                SVNNodeKind fileKind = SVNFileType.getNodeKind(fileType);
                handleDirEntry(dir, entryName, dirEntry, entry, 
                        fileKind, special, descend, getAll, noIgnore, handler);
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
        if (!skipThisDir) {
            SVNStatus status = assembleStatus(dir.getRoot(), dir, dirEntry, parentEntry, 
                    SVNNodeKind.DIR, false, getAll, false);
            if (status != null && handler != null) {
                handler.handleStatus(status);
            }
        }
        for(Iterator entries = dir.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            File file = (File) childrenFiles.get(entry.getName());
            SVNFileType fileType = SVNFileType.getType(file);
            boolean special = fileType == SVNFileType.SYMLINK;
            SVNNodeKind fileKind = SVNFileType.getNodeKind(fileType);
            handleDirEntry(dir, entry.getName(), dirEntry, entry, 
                    fileKind, special, descend, getAll, noIgnore, handler);
        }
    }

    protected void cleanup() {
        if (hasTarget()) { 
            myExternalsMap.remove(myAdminInfo.getAnchor().getRoot());
        }
    }
    
    protected SVNAdminArea getAnchor() {
        return myAdminInfo.getAnchor();
    }

    protected SVNWCAccess getWCAccess() {
        return myWCAccess;
    }
    
    protected boolean isDescend() {
        return myIsDescend;
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
        // get decoded path
        if (myRepositoryRoot == null || myRepositoryLocks == null || myRepositoryLocks.isEmpty() || url == null) {
            return null;
        }
        String urlString = url.getPath();
        String root = myRepositoryRoot.getPath();
        String path;
        if (urlString.equals(root)) {
            path = "/";
        } else {
            path = urlString.substring(root.length());
        }
        return (SVNLock) myRepositoryLocks.get(path);
    }

    private void handleDirEntry(SVNAdminArea dir, String entryName, SVNEntry dirEntry, SVNEntry entry, SVNNodeKind fileKind, boolean special, 
            boolean descend, boolean getAll, boolean noIgnore, ISVNStatusHandler handler) throws SVNException {
        File path = dir.getFile(entryName);
        
        if (fileKind == SVNNodeKind.DIR) {
            SVNEntry fullEntry = entry;
            if (entry.getKind() == fileKind) {
                fullEntry = myWCAccess.getEntry(path, false);
                if (fullEntry == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", path);
                    SVNErrorManager.error(err);
                }
            }
            if (descend && fullEntry != entry) {
                SVNAdminArea childDir = myWCAccess.retrieve(path);
                getDirStatus(dirEntry, childDir, null, descend, getAll, noIgnore, null, false, handler);
            } else if (fullEntry != entry) {
                // get correct dir.
                SVNAdminArea childDir = myWCAccess.retrieve(path);
                SVNStatus status = assembleStatus(path, childDir, fullEntry, dirEntry, fileKind, special, getAll, false);
                if (status != null && handler != null) {
                    handler.handleStatus(status);
                }
            } else {
                SVNStatus status = assembleStatus(path, dir, fullEntry, dirEntry, fileKind, special, getAll, false);
                if (status != null && handler != null) {
                    handler.handleStatus(status);
                }
            }
        } else {
            SVNStatus status = assembleStatus(path, dir, entry, dirEntry, fileKind, special, getAll, false);
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
        SVNStatus status = assembleStatus(file, dir, null, null, fileType, special, true, isIgnored);
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
        
        boolean hasProps = false;
        boolean isTextModified = false;
        boolean isPropsModified = false;
        boolean isLocked = false;
        boolean isSwitched = false;
        boolean isSpecial = false;
        
        SVNStatusType textStatus = SVNStatusType.STATUS_NORMAL;
        SVNStatusType propStatus = SVNStatusType.STATUS_NONE;
        
        SVNLock repositoryLock = null;
        
        if (myRepositoryLocks != null) {
            SVNURL url = null;
            if (entry != null && entry.getSVNURL() != null) {
                url = entry.getSVNURL();
            } else if (parentEntry != null && parentEntry.getSVNURL() != null) {
                url = parentEntry.getSVNURL().appendPath(file.getName(), false);
            }
            if (url != null) {
                repositoryLock = getLock(url);
            }
        }
        if (fileKind == SVNNodeKind.UNKNOWN || fileKind == null) {
            SVNFileType fileType = SVNFileType.getType(file);
            fileKind = SVNFileType.getNodeKind(fileType);
            special = SVNFileUtil.isWindows ? false : fileType == SVNFileType.SYMLINK;
        }
        if (entry == null) {
            SVNStatus status = new SVNStatus(null, file, SVNNodeKind.NONE,
                    SVNRevision.UNDEFINED, SVNRevision.UNDEFINED,
                    null, null, SVNStatusType.STATUS_NONE,  SVNStatusType.STATUS_NONE, 
                    SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE, false,
                    false, false, null, null, null, null,
                    null, SVNRevision.UNDEFINED,
                    repositoryLock, null, null);
            status.setRemoteStatus(SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE, repositoryLock, SVNNodeKind.NONE);
            SVNStatusType text = SVNStatusType.STATUS_NONE;
            SVNFileType fileType = SVNFileType.getType(file);
            if (fileType != SVNFileType.NONE) {
                text = isIgnored ? SVNStatusType.STATUS_IGNORED : SVNStatusType.STATUS_UNVERSIONED;
            }
            status.setContentsStatus(text);
            return status;
        }
        if (entry.getKind() == SVNNodeKind.DIR) {
            if (fileKind == SVNNodeKind.DIR) {
                if (myWCAccess.isMissing(file)) {
                    textStatus = SVNStatusType.STATUS_OBSTRUCTED;
                }
            } else if (fileKind != SVNNodeKind.NONE) {
                textStatus = SVNStatusType.STATUS_OBSTRUCTED;
            }
        }
        if (entry.getSVNURL() != null && parentEntry != null && parentEntry.getSVNURL() != null) {
            String urlName = SVNPathUtil.tail(entry.getSVNURL().getURIEncodedPath());
            if (!SVNEncodingUtil.uriEncode(file.getName()).equals(urlName)) {
                isSwitched = true;
            }
            if (!isSwitched && !entry.getSVNURL().removePathTail().equals(parentEntry.getSVNURL())) {
                isSwitched = true;
            }
        }
        if (textStatus != SVNStatusType.STATUS_OBSTRUCTED) {
            String name = entry.getName();
            if (dir != null && dir.hasProperties(name)) {
                propStatus = SVNStatusType.STATUS_NORMAL;
                hasProps = true;
            }
            isPropsModified = dir != null && dir.hasPropModifications(name);
            if (hasProps) {
                isSpecial = !SVNFileUtil.isWindows && dir != null && dir.getProperties(name).getPropertyValue(SVNProperty.SPECIAL) != null;
            }
            if (entry.getKind() == SVNNodeKind.FILE && special == isSpecial) {
                isTextModified = dir != null && dir.hasTextModifications(name, false);
            }
            if (isTextModified) {
                textStatus = SVNStatusType.STATUS_MODIFIED;
            }
            if (isPropsModified) {
                propStatus = SVNStatusType.STATUS_MODIFIED;
            }
            if (entry.getPropRejectFile() != null || 
                    entry.getConflictOld() != null || entry.getConflictNew() != null || entry.getConflictWorking() != null) {
                if (dir != null && dir.hasTextConflict(name)) {
                    textStatus = SVNStatusType.STATUS_CONFLICTED;
                }
                if (dir != null && dir.hasPropConflict(name)) {
                    propStatus = SVNStatusType.STATUS_CONFLICTED;
                }
            }
            if (entry.isScheduledForAddition() && textStatus != SVNStatusType.STATUS_CONFLICTED) {
                textStatus = SVNStatusType.STATUS_ADDED;
                propStatus = SVNStatusType.STATUS_NONE;
            } else if (entry.isScheduledForReplacement() && textStatus != SVNStatusType.STATUS_CONFLICTED) {
                textStatus = SVNStatusType.STATUS_REPLACED;
                propStatus = SVNStatusType.STATUS_NONE;
            } else if (entry.isScheduledForDeletion() && textStatus != SVNStatusType.STATUS_CONFLICTED) {
                textStatus = SVNStatusType.STATUS_DELETED;
                propStatus = SVNStatusType.STATUS_NONE;
            }
            if (entry.isIncomplete() && textStatus != SVNStatusType.STATUS_DELETED && textStatus != SVNStatusType.STATUS_ADDED) { 
                textStatus = SVNStatusType.STATUS_INCOMPLETE;
            } else if (fileKind == SVNNodeKind.NONE) {
                if (textStatus != SVNStatusType.STATUS_DELETED) {
                    textStatus = SVNStatusType.STATUS_MISSING;
                }
            } else if (fileKind != entry.getKind()) {
                textStatus = SVNStatusType.STATUS_OBSTRUCTED;
            } else if ((!isSpecial && special) || (isSpecial && !special)) {
                textStatus = SVNStatusType.STATUS_OBSTRUCTED;
            }
            if (fileKind == SVNNodeKind.DIR && entry.getKind() == SVNNodeKind.DIR) {
                isLocked = myWCAccess.isLocked(file);
            }
        }
        if (!reportAll) {
            if ((textStatus == SVNStatusType.STATUS_NONE || textStatus == SVNStatusType.STATUS_NORMAL) &&
                (propStatus == SVNStatusType.STATUS_NONE || propStatus == SVNStatusType.STATUS_NORMAL) &&
                !isLocked && !isSwitched && entry.getLockToken() == null && repositoryLock == null) {
                return null;
            }
        }
        SVNLock localLock = null;
        if (entry.getLockToken() != null) {
            localLock = new SVNLock(null, entry.getLockToken(), entry.getLockOwner(), entry.getLockComment(),
                    SVNTimeUtil.parseDate(entry.getLockCreationDate()), null);
        }
        File conflictNew = dir != null ? dir.getFile(entry.getConflictNew()) : null;
        File conflictOld = dir != null ? dir.getFile(entry.getConflictOld()) : null;
        File conflictWrk = dir != null ? dir.getFile(entry.getConflictWorking()) : null;
        File conflictProp = dir != null ? dir.getFile(entry.getPropRejectFile()) : null;
        SVNStatus status = new SVNStatus(entry.getSVNURL(), file, entry.getKind(),
                SVNRevision.create(entry.getRevision()), SVNRevision.create(entry.getCommittedRevision()),
                SVNTimeUtil.parseDate(entry.getCommittedDate()), entry.getAuthor(), 
                textStatus,  propStatus, 
                SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE, 
                isLocked, entry.isCopied(), isSwitched, 
                conflictNew, conflictOld, conflictWrk, conflictProp, 
                entry.getCopyFromURL(), SVNRevision.create(entry.getCopyFromRevision()),
                repositoryLock, localLock, entry.asMap());
        status.setEntry(entry);
        return status;
    }
    
    private boolean isExternal(String path) {
        return myExternalsMap.containsKey(path);
    }
    
    public static Collection getIgnorePatterns(SVNAdminArea dir, Collection globalIgnores) throws SVNException {
        String localIgnores = dir.getProperties("").getPropertyValue(SVNProperty.IGNORE);
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
        String[] ignores = options.getIgnorePatterns();
        if (ignores != null) {
            Collection patterns = new HashSet();
            for (int i = 0; i < ignores.length; i++) {
                patterns.add(ignores[i]);
            }
            return patterns;
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
        File[] children = parent.listFiles();
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
