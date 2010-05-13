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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.db.SVNEntryInfo;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNStatus;

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
        final SVNNodeKind kind = myWCContext.getNodeKind(myContextInfo.getTargetAbsPath(), false);

        if (kind == SVNNodeKind.FILE && localKind == SVNNodeKind.FILE) {
            getDirStatus(null, SVNPathUtil.getDirName(myContextInfo.getTargetAbsPath()), SVNPathUtil.getBaseName(myContextInfo.getTargetAbsPath()), myGlobalIgnores, myDepth, myIsReportAll, true,
                    true, myIsGetExcluded, myStatusHandler);
        } else if (kind == SVNNodeKind.DIR && localKind == SVNNodeKind.DIR) {
            getDirStatus(null, myContextInfo.getTargetAbsPath(), null, myGlobalIgnores, myDepth, myIsReportAll, myIsNoIgnore, false, myIsGetExcluded, myStatusHandler);
        } else {
            getDirStatus(null, SVNPathUtil.getDirName(myContextInfo.getTargetAbsPath()), SVNPathUtil.getBaseName(myContextInfo.getTargetAbsPath()), myGlobalIgnores, myDepth, myIsReportAll,
                    myIsNoIgnore, true, myIsGetExcluded, myStatusHandler);
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
                    map.put(children[i].getName(), children[i]);
                }
                return map;
            }
            return Collections.EMPTY_MAP;
        }
    }

    protected void getDirStatus(SVNEntryInfo parentEntry, String localAbsPath, String selected, Collection ignorePatterns, SVNDepth depth, boolean getAll, boolean noIgnore, boolean skipThisDir,
            boolean getExcluded, ISVNStatusHandler handler) throws SVNException {

        myWCContext.checkCancelled();
        depth = depth == SVNDepth.UNKNOWN ? SVNDepth.INFINITY : depth;
        File path = new File(localAbsPath);
        Map childrenFiles = myFileProvider.getChildrenFiles(path);

        List<String> childNodes = myWCContext.getChildNodes(localAbsPath);
        Map<String, String> nodes = new HashMap<String, String>();
        for (String childNode : childNodes) {
            nodes.put(childNode, childNode);
        }

        SVNEntryInfo dirEntry = myWCContext.getEntry(localAbsPath, false, SVNNodeKind.DIR, false);

        Map<String, Object> allChildren = new HashMap();
        Map<String, Object> conflicts = new HashMap();
        List patterns = null;

        if (selected == null) {
            /* Create a hash containing all children */
            allChildren.putAll(childrenFiles);
            allChildren.putAll(nodes);
            List<String> victims = myWCContext.readConfilctVictims(localAbsPath);
            for (String confict : victims) {
                conflicts.put(confict, confict);
            }
            /* Optimize for the no-tree-conflict case */
            allChildren.putAll(conflicts);
        } else {
            allChildren.put(selected, selected);
            String selectedAbsPath = SVNPathUtil.append(localAbsPath, selected);
            SVNConflictDescription tc = myWCContext.readTreeConflict(selectedAbsPath);
            /* Note this path if a tree conflict is present. */
            if (tc != null) {
                conflicts.put(selected, "");
            }
        }

        handleExternals(localAbsPath, dirEntry.getDepth());

        if (selected == null) {
            /* Handle "this-dir" first. */
            if (!skipThisDir) {
                sendStatusStructure(localAbsPath, dirEntry, parentEntry, SVNNodeKind.DIR, false, getAll, false, handler);
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
            String nodeAbsPath = SVNPathUtil.append(localAbsPath, key);
            File dirent = (File) childrenFiles.get(key);
            SVNFileType direntFileType = dirent != null ? SVNFileType.getType(dirent) : null;
            final SVNNodeKind direntNodeKind = dirent != null ? SVNFileType.getNodeKind(direntFileType) : SVNNodeKind.NONE;
            boolean direntIsSpecial = dirent != null ? direntFileType == SVNFileType.SYMLINK : false;

            if (nodes.containsKey(key)) {
                /* Versioned node */
                SVNEntryInfo entry = null;
                boolean hidden = myWCContext.isNodeHidden(nodeAbsPath);
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
                    if (depth == SVNDepth.FILES && entry.getNodeKind() == SVNNodeKind.DIR) {
                        continue;
                    }
                    /* Handle this entry (possibly recursing). */
                    handleDirEntry(nodeAbsPath, dirEntry, entry, direntNodeKind, direntIsSpecial, ignorePatterns, depth == SVNDepth.INFINITY ? depth : SVNDepth.EMPTY, getAll, noIgnore, getExcluded,
                            handler);
                    continue;
                }
            }

            if (conflicts.containsKey(key)) {
                /* Tree conflict */
                if (ignorePatterns != null && patterns == null) {
                    patterns = myWCContext.collectIgnorePatterns(localAbsPath, ignorePatterns);
                }
                sendUnversionedItem(nodeAbsPath, direntNodeKind, direntIsSpecial, patterns, noIgnore, handler);
                continue;
            }

            /* Unversioned node */
            if (dirent == null) {
                continue; /* Selected node, but not found */
            }

            if (depth == SVNDepth.FILES && SVNFileType.getNodeKind(direntFileType) == SVNNodeKind.DIR) {
                continue;
            }

            if (myWCContext.isAdminDirectory(key)) {
                continue;
            }

            if (ignorePatterns != null && patterns == null)
                myWCContext.collectIgnorePatterns(localAbsPath, ignorePatterns);

            sendUnversionedItem(nodeAbsPath, direntNodeKind, direntIsSpecial, patterns, noIgnore || selected != null, handler);
        }

    }

    private void sendUnversionedItem(String nodeAbsPath, SVNNodeKind svnNodeKind, boolean b, List patterns, boolean noIgnore, ISVNStatusHandler handler) {
    }

    private void handleDirEntry(String nodeAbsPath, SVNEntryInfo dirEntry, SVNEntryInfo entry, SVNNodeKind svnNodeKind, boolean b, Collection ignorePatterns, SVNDepth svnDepth, boolean getAll,
            boolean noIgnore, boolean getExcluded, ISVNStatusHandler handler) {
    }

    private void sendStatusStructure(String localAbsPath, SVNEntryInfo dirEntry, SVNEntryInfo parentEntry, SVNNodeKind dir, boolean b, boolean getAll, boolean c, ISVNStatusHandler handler) {
    }

    private void handleExternals(String localAbsPath, SVNDepth depth) {
    }

}
