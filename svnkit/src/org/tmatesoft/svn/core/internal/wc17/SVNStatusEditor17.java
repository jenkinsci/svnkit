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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNStatusEditor17 {

    private SVNWCContext myWCContext;
    private SVNWCContextInfo myContextInfo;

    private boolean myIsReportAll;
    private boolean myIsNoIgnore;
    private SVNDepth myDepth;

    private ISVNStatusHandler myStatusHandler;

    private Map myExternalsMap;
    private Collection myGlobalIgnores;

    private SVNURL myRepositoryRoot;
    private Map myRepositoryLocks;
    private long myTargetRevision;
    private String myWCRootPath;
    private ISVNStatusFileProvider myFileProvider;
    private ISVNStatusFileProvider myDefaultFileProvider;
    private boolean myIsGetExcluded;

    public SVNStatusEditor17(ISVNOptions options, SVNWCContext wcContext, SVNWCContextInfo info, boolean noIgnore, boolean reportAll, SVNDepth depth, ISVNStatusHandler handler) {

        myWCContext = wcContext;
        myContextInfo = info;
        myIsNoIgnore = noIgnore;
        myIsReportAll = reportAll;
        myDepth = depth;
        myStatusHandler = handler;
        myExternalsMap = new SVNHashMap();
        myGlobalIgnores = getGlobalIgnores(options);
        myTargetRevision = -1;
        myDefaultFileProvider = new DefaultSVNStatusFileProvider();
        myFileProvider = myDefaultFileProvider;

        myIsGetExcluded = false;

    }

    public SVNCommitInfo closeEdit() throws SVNException {

        final SVNNodeKind localKind = SVNFileType.getNodeKind(SVNFileType.getType(myContextInfo.getTargetAbsFile()));
        final SVNNodeKind kind = myWCContext.getNodeKind(myContextInfo.getTargetAbsFile(), false);

        if (kind == SVNNodeKind.FILE && localKind == SVNNodeKind.FILE) {
            getDirStatus(myContextInfo.getTargetAbsFile().getParentFile(), null, null, myContextInfo.getTargetAbsFile().getName(), myGlobalIgnores, myDepth, myIsReportAll, true, true,
                    myIsGetExcluded, myStatusHandler);
        } else if (kind == SVNNodeKind.DIR && localKind == SVNNodeKind.DIR) {
            getDirStatus(myContextInfo.getTargetAbsFile(), null, null, null, myGlobalIgnores, myDepth, myIsReportAll, myIsNoIgnore, false, myIsGetExcluded, myStatusHandler);
        } else {
            getDirStatus(myContextInfo.getTargetAbsFile().getParentFile(), null, null, myContextInfo.getTargetAbsFile().getName(), myGlobalIgnores, myDepth, myIsReportAll, myIsNoIgnore, true,
                    myIsGetExcluded, myStatusHandler);
        }

        return null;
    }

    public long getTargetRevision() {
        return myTargetRevision;
    }

    public void setFileProvider(ISVNStatusFileProvider filesProvider) {
        myFileProvider = filesProvider;
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

    private static class WrapperSVNStatusFileProvider implements ISVNStatusFileProvider {

        private final ISVNStatusFileProvider myDefault;
        private final ISVNStatusFileProvider myDelegate;

        private WrapperSVNStatusFileProvider(ISVNStatusFileProvider defaultProvider, ISVNStatusFileProvider delegate) {
            myDefault = defaultProvider;
            myDelegate = delegate;
        }

        public Map getChildrenFiles(File parent) {
            final Map result = myDelegate.getChildrenFiles(parent);
            if (result != null) {
                return result;
            }
            return myDefault.getChildrenFiles(parent);
        }
    }

    private static class DefaultSVNStatusFileProvider implements ISVNStatusFileProvider {

        public Map getChildrenFiles(File parent) {
            File[] children = SVNFileListUtil.listFiles(parent);
            if (children != null) {
                Map map = new SVNHashMap();
                for (int i = 0; i < children.length; i++) {
                    map.put(SVNFileUtil.getBasePath(children[i]), children[i]);
                }
                return map;
            }
            return Collections.EMPTY_MAP;
        }
    }

    protected void getDirStatus(File localAbsPath, SVNURL parentReposRootUrl, File parentReposRelPath, String selected, Collection ignorePatterns, SVNDepth depth, boolean getAll, boolean noIgnore,
            boolean skipThisDir, boolean getExcluded, ISVNStatusHandler handler) throws SVNException {

        myWCContext.checkCancelled();
        depth = depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth;
        final Map<String, File> childrenFiles = myFileProvider.getChildrenFiles(localAbsPath);
        final ISVNWCDb db = myWCContext.getDb();

        /* Load list of childnodes. */
        final List<String> childNodes = db.readChildren(localAbsPath);
        final Map<String, File> nodes = new HashMap<String, File>();
        for (String childNode : childNodes) {
            nodes.put(childNode, new File(childNode));
        }

        final WCDbInfo dirInfo = db.readInfo(localAbsPath, InfoField.status, InfoField.reposRelPath, InfoField.reposRootUrl, InfoField.depth);
        SVNWCDbStatus dirStatus = dirInfo.status;
        File dirReposRelPath = dirInfo.reposRelPath;
        SVNURL dirReposRootUrl = dirInfo.reposRootUrl;
        SVNDepth dirDepth = dirInfo.depth;

        if (dirReposRelPath == null) {
            if (dirReposRootUrl != null) {
                dirReposRootUrl = parentReposRootUrl;
                dirReposRelPath = new File(parentReposRelPath, localAbsPath.getName());
            } else if (dirStatus != SVNWCDbStatus.Deleted && dirStatus != SVNWCDbStatus.Added) {
                final WCDbRepositoryInfo baseReposInfo = db.scanBaseRepository(localAbsPath, RepositoryInfoField.relPath, RepositoryInfoField.rootUrl);
                dirReposRelPath = baseReposInfo.relPath;
                dirReposRootUrl = baseReposInfo.rootUrl;
            } else {
                dirReposRelPath = null;
                dirReposRootUrl = null;
            }
        }

        final Map<String, File> allChildren = new HashMap();
        final Map<String, File> conflicts = new HashMap();
        Collection<String> patterns = null;

        if (selected == null) {
            /* Create a hash containing all children */
            allChildren.putAll(childrenFiles);
            allChildren.putAll(nodes);
            List<String> victims = db.readConflictVictims(localAbsPath);
            for (String confict : victims) {
                conflicts.put(confict, new File(confict));
            }
            /* Optimize for the no-tree-conflict case */
            allChildren.putAll(conflicts);
        } else {
            final File selectedAbsPath = new File(localAbsPath, selected);
            allChildren.put(selected, selectedAbsPath);
            final SVNConflictDescription tc = db.opReadTreeConflict(selectedAbsPath);
            /* Note this path if a tree conflict is present. */
            if (tc != null) {
                conflicts.put(selected, selectedAbsPath);
            }
        }

        handleExternals(localAbsPath, dirDepth);

        if (selected == null) {
            /* Handle "this-dir" first. */
            if (!skipThisDir) {
                sendStatusStructure(localAbsPath, parentReposRootUrl, parentReposRelPath, SVNNodeKind.DIR, false, getAll, false, handler);
            }
            /* If the requested depth is empty, we only need status on this-dir. */
            if (depth == SVNDepth.EMPTY) {
                return;
            }
        }

        /*
         * Add empty status structures for each of the unversioned things. This
         * also catches externals; not sure whether that's good or bad, but it's
         * what's happening right now.
         */
        for (String key : allChildren.keySet()) {
            final File nodeAbsPath = new File(localAbsPath, key);
            final File dirent = childrenFiles.get(key);
            final SVNFileType direntFileType = dirent != null ? SVNFileType.getType(dirent) : null;
            final SVNNodeKind direntNodeKind = dirent != null ? SVNFileType.getNodeKind(direntFileType) : SVNNodeKind.NONE;
            boolean direntIsSpecial = dirent != null ? direntFileType == SVNFileType.SYMLINK : false;

            if (nodes.containsKey(key)) {
                /* Versioned node */
                SVNEntry entry = null;
                boolean hidden = db.isNodeHidden(nodeAbsPath);
                if (!hidden || getExcluded) {
                    try {
                        entry = myWCContext.getEntry(nodeAbsPath, false, SVNNodeKind.UNKNOWN, false);
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.NODE_UNEXPECTED_KIND) {
                            /* We asked for the contents, but got the stub. */
                        } else if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_MISSING) {
                            /*
                             * Most likely the parent refers to a missing child;
                             * retrieve the stub stored in the parent
                             */
                            try {
                                entry = myWCContext.getEntry(nodeAbsPath, false, SVNNodeKind.DIR, true);
                            } catch (SVNException e2) {
                                if (e2.getErrorMessage().getErrorCode() != SVNErrorCode.NODE_UNEXPECTED_KIND) {
                                    throw e2;
                                }
                            }
                        } else {
                            throw e;
                        }
                    }
                    if (depth == SVNDepth.FILES && entry.getKind() == SVNNodeKind.DIR) {
                        continue;
                    }
                    /* Handle this entry (possibly recursing). */
                    handleDirEntry(nodeAbsPath, entry, dirReposRootUrl, dirReposRelPath, direntNodeKind, direntIsSpecial, ignorePatterns, depth == SVNDepth.INFINITY ? depth : SVNDepth.EMPTY, getAll,
                            noIgnore, getExcluded, handler);
                    continue;
                }
            }

            if (conflicts.containsKey(key)) {
                /* Tree conflict */
                if (ignorePatterns != null && patterns == null) {
                    patterns = collectIgnorePatterns(localAbsPath, ignorePatterns);
                }
                sendUnversionedItem(nodeAbsPath, direntNodeKind, patterns, noIgnore, handler);
                continue;
            }

            /* Unversioned node */
            if (dirent == null) {
                continue; /* Selected node, but not found */
            }

            if (depth == SVNDepth.FILES && SVNFileType.getNodeKind(direntFileType) == SVNNodeKind.DIR) {
                continue;
            }

            if (SVNWCContext.isAdminDirectory(key)) {
                continue;
            }

            if (ignorePatterns != null && patterns == null)
                patterns = collectIgnorePatterns(localAbsPath, ignorePatterns);

            sendUnversionedItem(nodeAbsPath, direntNodeKind, patterns, noIgnore || selected != null, handler);
        }

    }

    private void sendStatusStructure(File localAbsPath, SVNURL parentReposRootUrl, File parentReposRelPath, SVNNodeKind pathKind, boolean pathSpecial, boolean getAll, boolean isIgnored,
            ISVNStatusHandler handler) throws SVNException {

        final ISVNWCDb db = myWCContext.getDb();

        /* Check for a repository lock. */
        SVNLock repositoryLock = null;
        if (myRepositoryLocks != null) {
            final WCDbInfo info = db.readInfo(localAbsPath, InfoField.status, InfoField.reposRelPath, InfoField.baseShadowed);
            SVNWCDbStatus status = info.status;
            File reposRelpath = info.reposRelPath;
            boolean baseShadowed = info.baseShadowed;

            /* A switched path can be deleted: check the right relpath */
            if (status == SVNWCDbStatus.Deleted && baseShadowed) {
                final WCDbRepositoryInfo reposInfo = db.scanBaseRepository(localAbsPath, RepositoryInfoField.relPath);
                reposRelpath = reposInfo.relPath;
            }

            if (reposRelpath == null && parentReposRelPath != null) {
                reposRelpath = new File(parentReposRelPath, localAbsPath.getName());
            }

            if (reposRelpath != null) {
                /*
                 * repos_lock still uses the deprecated filesystem absolute path
                 * format
                 */
                repositoryLock = (SVNLock) myRepositoryLocks.get(SVNURL.parseURIEncoded("/" + reposRelpath.toString()).getPath());
            }

        }

        SVNStatus status = myWCContext.assembleStatus(localAbsPath, parentReposRootUrl, parentReposRelPath, pathKind, pathSpecial, getAll, repositoryLock);
        if (status != null && handler != null) {
            handler.handleStatus(status);
        }

    }

    private void sendUnversionedItem(File nodeAbsPath, SVNNodeKind pathKind, Collection<String> patterns, boolean noIgnore, ISVNStatusHandler handler) throws SVNException {
        boolean isIgnored = isIgnored(nodeAbsPath.getName(), patterns);
        boolean isExternal = isExternal(nodeAbsPath);
        SVNStatus status = myWCContext.assembleUnversioned(nodeAbsPath, pathKind, isIgnored);
        if (status != null) {
            if (isExternal) {
                status.setContentsStatus(SVNStatusType.STATUS_EXTERNAL);
            }
            /*
             * We can have a tree conflict on an unversioned path, i.e. an
             * incoming delete on a locally deleted path during an update. Don't
             * ever ignore those!
             */
            if (status.isConflicted()) {
                isIgnored = false;
            }
            if (handler != null && (noIgnore || !isIgnored || isExternal || status.getRemoteLock() != null)) {
                handler.handleStatus(status);
            }
        }

    }

    private boolean isExternal(File nodeAbsPath) {
        if (!myExternalsMap.containsKey(nodeAbsPath)) {
            // check if path is external parent.
            for (Iterator paths = myExternalsMap.keySet().iterator(); paths.hasNext();) {
                String externalPath = (String) paths.next();
                if (externalPath.startsWith(nodeAbsPath + "/")) {
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

    private void handleDirEntry(File localAbsPath, SVNEntry entry, SVNURL dirReposRootUrl, File dirReposRelPath, SVNNodeKind pathKind, boolean pathSpecial, Collection ignores, SVNDepth depth,
            boolean getAll, boolean noIgnore, boolean getExcluded, ISVNStatusHandler handler) throws SVNException {

        assert (entry != null);

        /* We are looking at a directory on-disk. */
        if (pathKind == SVNNodeKind.DIR) {
            /*
             * Descend only if the subdirectory is a working copy directory
             * (which we've discovered because we got a THIS_DIR entry. And only
             * descend if DEPTH permits it, of course.
             */
            if (entry.getName() == "" && (depth == SVNDepth.UNKNOWN || depth == SVNDepth.IMMEDIATES || depth == SVNDepth.INFINITY)) {
                getDirStatus(localAbsPath, dirReposRootUrl, dirReposRelPath, null, ignores, depth, getAll, noIgnore, false, getExcluded, handler);
            } else {
                /*
                 * ENTRY is a child entry (file or parent stub). Or we have a
                 * directory entry but DEPTH is limiting our recursion.
                 */
                sendStatusStructure(localAbsPath, dirReposRootUrl, dirReposRelPath, pathKind, pathSpecial, getAll, false, handler);
            }
        } else {
            /* This is a file/symlink on-disk. */
            sendStatusStructure(localAbsPath, dirReposRootUrl, dirReposRelPath, pathKind, pathSpecial, getAll, false, handler);
        }
    }

    private void handleExternals(File localAbsPath, SVNDepth depth) throws SVNException {
        String externals = myWCContext.getProperty(localAbsPath, SVNProperty.EXTERNALS);
        if (externals != null) {
            if (SVNPathUtil.isAncestor(myContextInfo.getTargetAbsPath(), localAbsPath.toString())) {
                storeExternals(localAbsPath, externals, externals, depth);
            }
            /*
             * Now, parse the thing, and copy the parsed results into our
             * "global" externals hash.
             */
            SVNExternal[] externalsInfo = SVNExternal.parseExternals(localAbsPath, externals);
            for (int i = 0; i < externalsInfo.length; i++) {
                SVNExternal external = externalsInfo[i];
                myExternalsMap.put(new File(localAbsPath, external.getPath()), external);
            }
        }
    }

    private void storeExternals(File localAbsPath, String oldValue, String newValue, SVNDepth depth) {
        myContextInfo.addExternal(localAbsPath, oldValue, newValue);
        myContextInfo.addDepth(localAbsPath, depth);
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

}
