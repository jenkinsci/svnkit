/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test.environments;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.ISVNExtendedMergeCallback;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class SVNTestEnvironment extends AbstractSVNTestEnvironment {

    private SVNClientManager myManager;
    private ISVNOptions myOptions;
    private ISVNAuthenticationManager myAuthManager;

    protected SVNClientManager getManager() {
        return myManager;
    }

    protected ISVNAuthenticationManager getAuthenticationManager() {
        if (myAuthManager == null) {
            myAuthManager = new DefaultSVNAuthenticationManager(null, false, getDefaultUser(), getDefaultPassword());
        }
        return myAuthManager;
    }

    public void setAuthManager(ISVNAuthenticationManager authManager) {
        if (myManager != null) {
            myManager.setAuthenticationManager(authManager);
        }
        myAuthManager = authManager;
    }

    protected ISVNOptions getOptions() {
        if (myOptions == null) {
            myOptions = new DefaultSVNOptions();
        }
        return myOptions;
    }

    public void setOptions(ISVNOptions options) {
        if (myManager != null) {
            myManager.setOptions(options);
        }

        myOptions = options;
    }

    public void init() throws SVNException {
        super.init();
        myManager = SVNClientManager.newInstance(getOptions(), getAuthenticationManager());
    }

    public void dispose() throws SVNException {
        myManager = null;
        myAuthManager = null;
        myOptions = null;
    }

    public void setEventHandler(ISVNEventHandler eventHandler) {
        getManager().setEventHandler(eventHandler);
    }

    public void setExtendedMergeCallback(ISVNExtendedMergeCallback mergeCallback) {
        getManager().getDiffClient().setExtendedMergeCallback(mergeCallback);
    }

    public void createRepository(File path, String uuid) throws SVNException {
        getManager().getAdminClient().doCreateRepository(path, uuid, false, false, false, false);
    }

    public void load(File path, InputStream dumpStream) throws SVNException {
        getManager().getAdminClient().doLoad(path, dumpStream);
    }

    public void checkout(SVNURL url, File path, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth) throws SVNException {
        getManager().getUpdateClient().doCheckout(url, path, pegRevision, revision, depth, false);
    }

    public void update(File wc, SVNRevision revision, SVNDepth depth, boolean depthIsSticky) throws SVNException {
        getManager().getUpdateClient().doUpdate(wc, revision, depth, false, depthIsSticky);
    }

    public void switchPath(File wc, SVNURL url, SVNRevision revision, SVNDepth depth) throws SVNException {
        getManager().getUpdateClient().doSwitch(wc, url, SVNRevision.UNDEFINED, revision, depth, false, false);
    }

    public void importDirectory(File path, SVNURL url, String commitMessage, SVNProperties revProperties, SVNDepth depth) throws SVNException {
        getManager().getCommitClient().doImport(path, url, commitMessage, revProperties, false, false, depth);
    }

    public long commit(File wc, String commitMessage, SVNProperties revProperties, SVNDepth depth) throws SVNException {
        SVNCommitInfo info = getManager().getCommitClient().doCommit(new File[]{wc}, false, commitMessage, revProperties, null, false, false, depth);
        return info.getNewRevision();
    }

    public void merge(SVNURL url, File wc, SVNRevision pegRevision, Collection mergeRanges, SVNDepth depth, boolean dryRun, boolean recordOnly) throws SVNException {
        getManager().getDiffClient().doMerge(url, pegRevision, mergeRanges, wc, depth, true, false, dryRun, recordOnly);
    }

    public void mergeReintegrate(SVNURL src, SVNRevision pegRevision, File dst, boolean dryRun) throws SVNException {
        getManager().getDiffClient().doMergeReIntegrate(src, pegRevision, dst, dryRun);
    }

    public void merge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File wc, SVNDepth depth, boolean dryRun, boolean recordOnly) throws SVNException {
        getManager().getDiffClient().doMerge(url1, revision1, url2, revision2, wc, depth, true, false, dryRun, recordOnly);
    }

    public void status(File wc, SVNRevision revision, SVNDepth depth, boolean remote, boolean reportAll, boolean collectParentExternals, ISVNStatusHandler handler) throws SVNException {
        getManager().getStatusClient().doStatus(wc, revision, depth, remote, reportAll, false, collectParentExternals, handler, null);
    }

    public void copy(File src, SVNRevision pegRevision, SVNRevision revision, File dst, boolean isMove, boolean makeParents, boolean failWhenDstExists) throws SVNException {
        SVNCopySource source = new SVNCopySource(pegRevision, revision, src);
        getManager().getCopyClient().doCopy(new SVNCopySource[]{source}, dst, isMove, makeParents, failWhenDstExists);
    }

    public void copy(SVNURL src, SVNRevision pegRevision, SVNRevision revision, SVNURL dst, boolean isMove, boolean makeParents, boolean failWhenDstExists, String commitMessage) throws SVNException {
        SVNCopySource source = new SVNCopySource(pegRevision, revision, src);
        getManager().getCopyClient().doCopy(new SVNCopySource[]{source}, dst, isMove, makeParents, failWhenDstExists, commitMessage, null);
    }

    public void add(File path, boolean mkdir, SVNDepth depth, boolean makeParents) throws SVNException {
        getManager().getWCClient().doAdd(path, false, mkdir, false, depth, false, makeParents);
    }

    public void setProperty(File path, String propName, SVNPropertyValue propValue, SVNDepth depth) throws SVNException {
        getManager().getWCClient().doSetProperty(path, propName, propValue, false, depth, null, null);
    }

    public SVNPropertyValue getProperty(File path, String propName, SVNRevision revision) throws SVNException {
        SVNPropertyData propertyData = getManager().getWCClient().doGetProperty(path, propName, SVNRevision.UNDEFINED, revision);
        return propertyData == null ? null : propertyData.getValue();
    }

    public void delete(File path) throws SVNException {
        getManager().getWCClient().doDelete(path, false, false);
    }

    public void resolve(File path, SVNDepth depth, SVNConflictChoice conflictChoice) throws SVNException {
        getManager().getWCClient().doResolve(path, depth, true, true, conflictChoice);
    }

    public void reset() {
        myManager = SVNClientManager.newInstance(getOptions(), getAuthenticationManager());
    }
}
