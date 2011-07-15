/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNStatus17.ConflictInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.SVNWCSchedule;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ScheduleInternalInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNStatusEditor17 {

    protected SVNWCContext myWCContext;
    protected File myPath;

    protected boolean myIsReportAll;
    protected boolean myIsNoIgnore;
    protected SVNDepth myDepth;

    protected ISVNStatus17Handler myStatusHandler;

    protected Map<File, File> myExternalsMap;
    protected Collection myGlobalIgnores;

    protected SVNURL myRepositoryRoot;
    protected Map myRepositoryLocks;
    protected long myTargetRevision;
    protected String myWCRootPath;
    protected ISVNStatusFileProvider myFileProvider;
    protected ISVNStatusFileProvider myDefaultFileProvider;
    protected boolean myIsGetExcluded;

    private File myTargetAbsPath;
    private boolean myIgnoreTextMods;

    public SVNStatusEditor17(File path, SVNWCContext wcContext, ISVNOptions options, boolean noIgnore, boolean reportAll, SVNDepth depth, ISVNStatus17Handler handler) {

        myWCContext = wcContext;
        myPath = path;
        myIsNoIgnore = noIgnore;
        myIsReportAll = reportAll;
        myDepth = depth;
        myStatusHandler = handler;
        myExternalsMap = new HashMap<File, File>();
        myGlobalIgnores = getGlobalIgnores(options);
        myTargetRevision = -1;
        myDefaultFileProvider = new DefaultSVNStatusFileProvider();
        myFileProvider = myDefaultFileProvider;

        myIsGetExcluded = false;

    }

    public SVNCommitInfo closeEdit() throws SVNException {

        final SVNNodeKind localKind = SVNFileType.getNodeKind(SVNFileType.getType(myPath));
        final SVNNodeKind kind = myWCContext.readKind(myPath, false);

        File anchor_abspath;
        String target_name;
        boolean skip_root;

        if (kind == SVNNodeKind.FILE && localKind == SVNNodeKind.FILE) {
            anchor_abspath = SVNFileUtil.getFileDir(myPath);
            target_name = SVNFileUtil.getFileName(myPath);
            skip_root = true;
        } else if (kind == SVNNodeKind.DIR && localKind == SVNNodeKind.DIR) {
            anchor_abspath = myPath;
            target_name = null;
            skip_root = false;
        } else {
            anchor_abspath = SVNFileUtil.getFileDir(myPath);
            target_name = SVNFileUtil.getFileName(myPath);
            skip_root = false;
        }

        SVNFileType fileType = SVNFileType.getType(anchor_abspath);
        getDirStatus(anchor_abspath, target_name, skip_root, null, null, fileType, myGlobalIgnores, myDepth, myIsReportAll, true, getDefaultHandler());

        return null;
    }

    public long getTargetRevision() {
        return myTargetRevision;
    }

    public void targetRevision(long revision) {
        myTargetRevision = revision;
    }

    public void setFileProvider(ISVNStatusFileProvider filesProvider) {
        myFileProvider = filesProvider;
    }

    public SVNDepth getDepth() {
        return myDepth;
    }

    protected ISVNStatus17Handler getDefaultHandler() {
        return myStatusHandler;
    }

    protected boolean isReportAll() {
        return myIsReportAll;
    }

    protected boolean isNoIgnore() {
        return myIsNoIgnore;
    }

    private static Collection getGlobalIgnores(ISVNOptions options) {
        if (options != null) {
            String[] ignores = options.getIgnorePatterns();
            if (ignores != null) {
                Collection patterns = new SVNHashSet();
                for (int i = 0; i < ignores.length; i++) {
                    patterns.add(ignores[i]);
                }
                return patterns;
            }
        }
        return Collections.EMPTY_SET;
    }

    private static class DefaultSVNStatusFileProvider implements ISVNStatusFileProvider {

        public Map getChildrenFiles(File parent) {
            File[] children = SVNFileListUtil.listFiles(parent);
            if (children != null) {
                Map map = new SVNHashMap();
                for (int i = 0; i < children.length; i++) {
                    map.put(SVNFileUtil.getFileName(children[i]), children[i]);
                }
                return map;
            }
            return Collections.EMPTY_MAP;
        }
    }

    private void sendStatusStructure(File localAbsPath, WCDbRepositoryInfo parentReposInfo, SVNWCDbInfo info, SVNNodeKind pathKind, boolean pathSpecial, boolean getAll, ISVNStatus17Handler handler) throws SVNException {
        SVNLock repositoryLock = null;
        if (myRepositoryLocks != null) {
            WCDbRepositoryInfo reposInfo = getRepositoryRootUrlRelPath(parentReposInfo, info, localAbsPath);
            if (reposInfo != null && reposInfo.relPath != null) {
                repositoryLock = (SVNLock) myRepositoryLocks.get("/" + SVNFileUtil.getFilePath(reposInfo.relPath));
            }
        }
        SVNStatus17 status17 = assembleStatus(localAbsPath, parentReposInfo, info, pathKind, pathSpecial, getAll, repositoryLock);
        if (status17 != null && handler != null) {
            handler.handleStatus(status17);
        }

    }

    private void sendUnversionedItem(File nodeAbsPath, SVNNodeKind pathKind, boolean treeConflicted, Collection<String> patterns, boolean noIgnore, ISVNStatus17Handler handler) throws SVNException {
        boolean isIgnored = isIgnored(SVNFileUtil.getFileName(nodeAbsPath), patterns);
        boolean isExternal = isExternal(nodeAbsPath);
        SVNStatus17 status = myWCContext.assembleUnversioned17(nodeAbsPath, pathKind, treeConflicted, isIgnored);
        if (status != null) {
            if (isExternal) {
                status.setNodeStatus(SVNStatusType.STATUS_EXTERNAL);
            }
            if (status.isConflicted()) {
                isIgnored = false;
            }
            if (handler != null && (noIgnore || !isIgnored || isExternal)) {
                handler.handleStatus(status);
            }
        }

    }

    public SVNStatus17 assembleStatus(File localAbsPath, WCDbRepositoryInfo parentReposInfo, SVNWCDbInfo info, SVNNodeKind pathKind, boolean pathSpecial, boolean getAll, SVNLock repositoryLock) throws SVNException {

        boolean switched_p, copied = false;

        SVNStatusType node_status = SVNStatusType.STATUS_NORMAL;
        SVNStatusType text_status = SVNStatusType.STATUS_NORMAL;
        SVNStatusType prop_status = SVNStatusType.STATUS_NONE;
        
        if (info == null) {
            info = readInfo(localAbsPath);
        }
        if (info.reposRelpath == null || parentReposInfo == null || parentReposInfo.relPath == null) {
            switched_p = false;
        } else {
            String name = SVNFileUtil.getFilePath(SVNWCUtils.skipAncestor(parentReposInfo.relPath, info.reposRelpath));
            switched_p = name == null || !name.equals(SVNFileUtil.getFileName(localAbsPath)); 
        }

        if (info.kind == SVNWCDbKind.Dir) {
            if (info.status == SVNWCDbStatus.Incomplete) {
                node_status = SVNStatusType.STATUS_INCOMPLETE;
            } else if (info.status == SVNWCDbStatus.Deleted) {
                node_status = SVNStatusType.STATUS_DELETED;
                if (!info.haveBase) {
                    copied = true;
                } else {
                    copied = myWCContext.getNodeScheduleInternal(localAbsPath, false, true).copied;
                }
            } else if (pathKind == null || pathKind != SVNNodeKind.DIR) {
                if (pathKind == null || pathKind == SVNNodeKind.NONE)
                    node_status = SVNStatusType.STATUS_MISSING;
                else
                    node_status = SVNStatusType.STATUS_OBSTRUCTED;
            }
        } else {
            if (info.status == SVNWCDbStatus.Deleted) {
                node_status = SVNStatusType.STATUS_DELETED;
                copied = myWCContext.getNodeScheduleInternal(localAbsPath, false, true).copied;
            } else if (pathKind == null || pathKind != SVNNodeKind.FILE) {
                if (pathKind == null || pathKind == SVNNodeKind.NONE) {
                    node_status = SVNStatusType.STATUS_MISSING;
                } else {
                    node_status = SVNStatusType.STATUS_OBSTRUCTED;
                }
            }
        }
        
        if (info.status != SVNWCDbStatus.Deleted) {
            if (info.propsMod) {
                prop_status = SVNStatusType.STATUS_MODIFIED;
            } else if (info.hadProps) {
                prop_status = SVNStatusType.STATUS_NORMAL;
            }
        }
        
        if (info.kind != SVNWCDbKind.Dir && node_status == SVNStatusType.STATUS_NORMAL) {
            boolean text_modified_p = false;
            long fileSize = localAbsPath.length();
            long fileTime = localAbsPath.lastModified();
            
            if ((info.kind == SVNWCDbKind.File || info.kind == SVNWCDbKind.Symlink) && info.special == pathSpecial) {
                if (!info.hasChecksum) {
                    text_modified_p = true;
                } else if (myIgnoreTextMods || 
                    (pathKind != null && 
                            info.recordedSize != -1 &&
                            info.recordedModTime != 0 &&
                            info.recordedModTime == fileTime &&
                            info.recordedSize == fileSize)) {
                    text_modified_p = false;
                } else {
                    try {
                        text_modified_p = myWCContext.isTextModified(localAbsPath, false, true);
                    } catch (SVNException e) {
                        if (!SVNWCContext.isErrorAccess(e)) {
                            throw e;
                        }
                        text_modified_p = true;
                    }
                }                
            } else if (info.special != (pathKind != null && pathSpecial)) {
                node_status = SVNStatusType.STATUS_OBSTRUCTED;
            }
            if (text_modified_p) {
                text_status = SVNStatusType.STATUS_MODIFIED;
            }
        }
        boolean conflicted = info.conflicted;
        if (info.conflicted) {
            ConflictInfo conflictInfo = myWCContext.getConflicted(localAbsPath, true, true, true);
            if (!conflictInfo.propConflicted && !conflictInfo.textConflicted && !conflictInfo.treeConflicted) {
                conflicted =false;
            }
        }
        if (node_status == SVNStatusType.STATUS_NORMAL) {
            if (info.status == SVNWCDbStatus.Added) {
                if (!info.opRoot) {
                    copied = true;
                } else if (info.kind == SVNWCDbKind.File && !info.haveBase && !info.haveMoreWork) {
                    node_status = SVNStatusType.STATUS_ADDED;
                    copied = info.hasChecksum;
                } else {
                    ScheduleInternalInfo scheduleInfo = myWCContext.getNodeScheduleInternal(localAbsPath, true, true);
                    copied = scheduleInfo.copied;
                    if (scheduleInfo.schedule == SVNWCSchedule.add) {
                        node_status = SVNStatusType.STATUS_ADDED;
                    } else if (scheduleInfo.schedule == SVNWCSchedule.replace) {
                        node_status = SVNStatusType.STATUS_REPLACED;
                    }
                }
            }
        }
        
        if (node_status == SVNStatusType.STATUS_NORMAL) {
            node_status = text_status;
        }

        if (node_status == SVNStatusType.STATUS_NORMAL && prop_status != SVNStatusType.STATUS_NONE) {
            node_status = prop_status;
        }

        if (!getAll) {
            if ((node_status == SVNStatusType.STATUS_NONE || node_status == SVNStatusType.STATUS_NORMAL) 
                    && !switched_p
                    && !info.locked
                    && (info.lock == null)
                    && repositoryLock == null
                    && info.changelist == null
                    && !conflicted) {
                return null;
            }
        }
        
        WCDbRepositoryInfo reposInfo =getRepositoryRootUrlRelPath(parentReposInfo, info, localAbsPath);
        SVNNodeKind statusKind = null;
        switch (info.kind) {
        case Dir:
            statusKind = SVNNodeKind.DIR;
            break;
        case File:
        case Symlink:
            statusKind = SVNNodeKind.FILE;
            break;
        case Unknown:
        default:
            statusKind = SVNNodeKind.UNKNOWN;
        }
        SVNStatus17 stat = new SVNStatus17(myWCContext);
        stat.setKind(statusKind);
        stat.setLocalAbsPath(localAbsPath);

        if (info.lock != null) {
            stat.setLock(new SVNLock(SVNFileUtil.getFilePath(reposInfo.relPath), info.lock.token, info.lock.owner, info.lock.comment, info.lock.date, null));
        }

        stat.setDepth(info.depth);
        stat.setNodeStatus(node_status);
        stat.setTextStatus(text_status);
        stat.setPropStatus(prop_status);
        stat.setReposNodeStatus(SVNStatusType.STATUS_NONE); 
        stat.setReposTextStatus(SVNStatusType.STATUS_NONE);
        stat.setReposPropStatus(SVNStatusType.STATUS_NONE);
        stat.setSwitched(switched_p);
        stat.setCopied(copied);
        stat.setReposLock(repositoryLock);
        stat.setRevision(info.revnum);
        stat.setChangedRev(info.changedRev);
        stat.setChangedAuthor(info.changedAuthor);
        stat.setChangedDate(info.changedDate);

        stat.setOodKind(SVNNodeKind.NONE);
        stat.setOodChangedRev(-1);
        stat.setOodChangedDate(null);
        stat.setOodChangedAuthor(null);

        stat.setLocked(info.locked);
        stat.setConflicted(info.conflicted);
        stat.setVersioned(true);
        stat.setChangelist(info.changelist);
        stat.setReposRootUrl(reposInfo.rootUrl);
        stat.setReposRelpath(reposInfo.relPath);
        stat.setReposUUID(reposInfo.uuid);
        
        return stat;
    }

    private boolean isExternal(File nodeAbsPath) {
        if (!myExternalsMap.containsKey(nodeAbsPath)) {
            for (Iterator<File> paths = myExternalsMap.keySet().iterator(); paths.hasNext();) {
                File externalPath = (File) paths.next();
                if (SVNWCUtils.isChild(nodeAbsPath, externalPath)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private boolean isIgnored(String name, Collection<String> patterns) {
        for (Iterator ps = patterns.iterator(); ps.hasNext();) {
            String pattern = (String) ps.next();
            if (DefaultSVNOptions.matches(pattern, name)) {
                return true;
            }
        }
        return false;
    }

    private Collection<String> collectIgnorePatterns(File localAbsPath, Collection<String> ignores) throws SVNException {
        /* ### assert we are passed a directory? */
        /* Then add any svn:ignore globs to the PATTERNS array. */
        final String localIgnores = myWCContext.getProperty(localAbsPath, SVNProperty.IGNORE);
        if (localIgnores != null) {
            final List<String> patterns = new ArrayList<String>();
            patterns.addAll(ignores);
            for (StringTokenizer tokens = new StringTokenizer(localIgnores, "\r\n"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken().trim();
                if (token.length() > 0) {
                    patterns.add(token);
                }
            }
            return patterns;
        }
        return ignores;
    }

    public void setRepositoryInfo(SVNURL repositoryRoot, HashMap<String, SVNLock> repositoryLocks) {
        myRepositoryRoot = repositoryRoot;
        myRepositoryLocks = repositoryLocks;
    }
    
    private SVNWCDbInfo readInfo(File localAbsPath) throws SVNException {
        SVNWCDbInfo result = new SVNWCDbInfo();
        
        WCDbInfo readInfo =myWCContext.getDb().readInfo(localAbsPath, InfoField.status, InfoField.kind, InfoField.revision, InfoField.reposRelPath, InfoField.reposRootUrl, InfoField.reposUuid,
                InfoField.changedRev, InfoField.changedDate, InfoField.changedAuthor, InfoField.depth, InfoField.checksum, InfoField.lock, InfoField.translatedSize,
                InfoField.lastModTime, InfoField.changelist, InfoField.conflicted, InfoField.opRoot, InfoField.hadProps, InfoField.propsMod, InfoField.haveBase, InfoField.haveMoreWork);
        result.load(readInfo);
        
        result.locked = myWCContext.getDb().isWCLocked(localAbsPath);
        if (result.haveBase && (result.status == SVNWCDbStatus.Added || result.status == SVNWCDbStatus.Deleted)) {
            result.lock = myWCContext.getDb().getBaseInfo(localAbsPath, BaseInfoField.lock).lock;
        }
        result.hasChecksum = readInfo.checksum != null;
        if (result.kind == SVNWCDbKind.File && (result.hadProps || result.propsMod)) {
            SVNProperties properties;
            if (result.propsMod) {
                properties = myWCContext.getDb().readProperties(localAbsPath);
            } else {
                properties = myWCContext.getDb().readPristineProperties(localAbsPath);
            }
            result.special = properties.getSVNPropertyValue(SVNProperty.SPECIAL) != null;
        }
        return result;
    }
    
    public void walkStatus(File localAbsPath, SVNDepth depth, boolean getAll, boolean noIgnore, boolean ignoreTextMods, Collection<String> ignorePatterns) throws SVNException {
        myExternalsMap = myWCContext.getDb().getExternalsDefinedBelow(localAbsPath);
        if (ignorePatterns == null) {
            ignorePatterns = getGlobalIgnores(myWCContext.getOptions());
        }
        SVNWCDbInfo dirInfo = null;
        try {
            dirInfo = readInfo(localAbsPath);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
        }
        SVNFileType fileType = SVNFileType.getType(localAbsPath);
        File anchorAbsPath;
        String targetName;
        boolean skipRoot;
        if (dirInfo != null && dirInfo.kind == SVNWCDbKind.Dir) {
            anchorAbsPath = localAbsPath;
            targetName = null;
            skipRoot = false;
        } else {
            dirInfo = null;
            anchorAbsPath = SVNFileUtil.getParentFile(localAbsPath);
            targetName = SVNFileUtil.getFileName(localAbsPath);
            skipRoot = true;
        }
        
        myTargetAbsPath = localAbsPath;
        myIgnoreTextMods = ignoreTextMods;
        
        getDirStatus(anchorAbsPath, targetName, skipRoot, null, dirInfo, fileType, ignorePatterns, depth, getAll, noIgnore, getDefaultHandler());        
    }
   
    protected void getDirStatus(File localAbsPath, String selected, boolean skipThisDir, WCDbRepositoryInfo parentReposInfo, 
            SVNWCDbInfo dirInfo, SVNFileType fileType, Collection<String> ignorePatterns, SVNDepth  depth, boolean getAll, boolean noIgnore, ISVNStatus17Handler handler) throws SVNException {
        myWCContext.checkCancelled();
        
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        final Map<String, File> childrenFiles = myFileProvider.getChildrenFiles(localAbsPath);
        final Set<String> allChildren = new HashSet<String>();
        final Set<String> conflicts = new HashSet<String>();
        final Map<String, SVNWCDbInfo> nodes = new HashMap<String, ISVNWCDb.SVNWCDbInfo>();
        Collection<String> patterns = null;
        
        if (dirInfo == null) {
            dirInfo = readInfo(localAbsPath);
        }
        
        WCDbRepositoryInfo dirReposInfo = getRepositoryRootUrlRelPath(parentReposInfo, dirInfo, localAbsPath);
        if (selected == null) {
            myWCContext.getDb().readChildren(localAbsPath, nodes, conflicts);
            allChildren.addAll(nodes.keySet());
            allChildren.addAll(childrenFiles.keySet());
            allChildren.addAll(conflicts);
        } else {
            File selectedAbsPath = SVNFileUtil.createFilePath(localAbsPath, selected);
            
            SVNWCDbInfo info = null;
            try {
                info = readInfo(selectedAbsPath);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                    throw e;
                }
            }
            if (info != null) {
                if (!info.conflicted || info.status != SVNWCDbStatus.Normal || info.kind != SVNWCDbKind.Unknown) {
                    nodes.put(selected, info);
                }
                if (info.conflicted) {
                    conflicts.add(selected);
                }
                
            }
            allChildren.add(selected);
        }
        if (selected == null) {
            if (!skipThisDir) {
                sendStatusStructure(localAbsPath, parentReposInfo, dirInfo, SVNFileType.getNodeKind(fileType), fileType == SVNFileType.SYMLINK, getAll, handler);
            }
            if (depth == SVNDepth.UNKNOWN) {
                return;
            }
        }
        for(String name : allChildren) {
            File nodeAbsPath = SVNFileUtil.createFilePath(localAbsPath, name);
            SVNFileType nodeFileType = childrenFiles.containsKey(name) ? SVNFileType.getType(childrenFiles.get(name)) : null;
            SVNWCDbInfo nodeInfo = nodes.get(name);
            
            if (nodeInfo != null) {
                if (nodeInfo.status != SVNWCDbStatus.NotPresent && nodeInfo.status != SVNWCDbStatus.Excluded && 
                        nodeInfo.status != SVNWCDbStatus.ServerExcluded) {
                    if (depth == SVNDepth.FILES && nodeInfo.kind == SVNWCDbKind.Dir) {
                        continue;
                    }
                    sendStatusStructure(nodeAbsPath, dirReposInfo, nodeInfo, SVNFileType.getNodeKind(nodeFileType), nodeFileType == SVNFileType.SYMLINK, getAll, handler);
                    if (depth == SVNDepth.INFINITY && nodeInfo.kind == SVNWCDbKind.Dir) {
                        getDirStatus(nodeAbsPath, null, true, dirReposInfo, nodeInfo, nodeFileType, ignorePatterns, SVNDepth.INFINITY, getAll, noIgnore, handler);
                    }
                    continue;
                }
            }
            
            if (conflicts.contains(name)) {
                if (ignorePatterns != null && patterns == null) {
                    patterns = collectIgnorePatterns(localAbsPath, ignorePatterns);
                }
                sendUnversionedItem(nodeAbsPath, SVNFileType.getNodeKind(nodeFileType), true, patterns, noIgnore, handler);                
                continue;
            }
            if (fileType == null) {
                continue;
            }
            if (depth == SVNDepth.FILES && fileType == SVNFileType.DIRECTORY) {
                continue;
            }
            if (SVNFileUtil.getAdminDirectoryName().equals(name)) {
                continue;
            }            
            if (ignorePatterns != null && patterns == null) {
                patterns = collectIgnorePatterns(localAbsPath, ignorePatterns);
            }
            sendUnversionedItem(nodeAbsPath, SVNFileType.getNodeKind(nodeFileType), false, patterns, noIgnore || selected != null, handler);                
        }
    }
    
    private WCDbRepositoryInfo getRepositoryRootUrlRelPath(WCDbRepositoryInfo parentRelPath, SVNWCDbInfo info, File localAbsPath) throws SVNException {
        WCDbRepositoryInfo result = new WCDbRepositoryInfo();
        if (info.reposRelpath != null && info.reposRootUrl != null) {
            result.relPath = info.reposRelpath;
            result.uuid = info.reposUuid;
            result.rootUrl = info.reposRootUrl;
        } else if (parentRelPath != null && parentRelPath.rootUrl != null && parentRelPath.relPath != null) {
            result.relPath = SVNFileUtil.createFilePath(parentRelPath.relPath, SVNFileUtil.getFileName(localAbsPath));
            result.uuid = parentRelPath.uuid;
            result.rootUrl = parentRelPath.rootUrl;            
        } else if (info.status == SVNWCDbStatus.Added) {
            WCDbAdditionInfo additionInfo = myWCContext.getDb().scanAddition(localAbsPath, AdditionInfoField.reposRelPath, AdditionInfoField.reposRootUrl, AdditionInfoField.reposUuid);
            result.relPath = additionInfo.reposRelPath;
            result.uuid = additionInfo.reposUuid;
            result.rootUrl = additionInfo.reposRootUrl;
        } else if (info.haveBase) {
            WCDbRepositoryInfo repoInfo = myWCContext.getDb().scanBaseRepository(localAbsPath, RepositoryInfoField.relPath, RepositoryInfoField.rootUrl, RepositoryInfoField.uuid);
            result.relPath = repoInfo.relPath;
            result.uuid = repoInfo.uuid;
            result.rootUrl = repoInfo.rootUrl;
        }
        return result;        
    }
}
