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
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.db.SVNEntryInfo;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;

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
        
        

    }

}
