/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
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

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.ISVNExtendedMergeCallback;
import org.tmatesoft.svn.core.javahl.SVNClientImpl;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tigris.subversion.javahl.SVNAdmin;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class JavaHLTestEnvironment extends AbstractSVNTestEnvironment {

    SVNClientImpl myClient;
    SVNAdmin myAdmin;

    public void init() throws SVNException {
        super.init();
        myClient = SVNClientImpl.newInstance();
        myAdmin = new SVNAdmin();
    }

    public void dispose() throws SVNException {
        myClient = null;
        myAdmin = null;
    }

    public void setEventHandler(ISVNEventHandler eventHandler) {
    }

    public void setExtendedMergeCallback(ISVNExtendedMergeCallback mergeCallback) {
    }

    public void createRepository(File path, String uuid) throws SVNException {
    }

    public void load(File path, InputStream dumpStream) throws SVNException {
    }

    public void checkout(SVNURL url, File path, SVNRevision pegRevision, SVNRevision revision, SVNDepth depth) throws SVNException {
    }

    public void switchPath(File wc, SVNURL url, SVNRevision revision, SVNDepth depth) throws SVNException {
    }

    public void update(File wc, SVNRevision revision, SVNDepth depth, boolean depthIsSticky) throws SVNException {
    }

    public void importDirectory(File path, SVNURL url, String commitMessage, SVNProperties revProperties, SVNDepth depth) throws SVNException {
    }

    public long commit(File wc, String commitMessage, SVNProperties revProperties, SVNDepth depth) throws SVNException {
        return -1;
    }

    public void merge(SVNURL url, File wc, SVNRevision pegRevision, Collection mergeRanges, SVNDepth depth, boolean dryRun, boolean recordOnly) throws SVNException {
    }

    public void merge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, File wc, SVNDepth depth, boolean dryRun, boolean recordOnly) throws SVNException {
    }

    public void mergeReintegrate(SVNURL src, SVNRevision pegRevision, File dst, boolean dryRun) throws SVNException {
    }

    public void resolve(File path, SVNDepth depth, SVNConflictChoice conflictChoice) throws SVNException {
    }

    public void status(File wc, SVNRevision revision, SVNDepth depth, boolean remote, boolean reportAll, boolean collectParentExternals, ISVNStatusHandler handler) throws SVNException {
    }

    public void copy(File src, SVNRevision pegRevision, SVNRevision revision, File dst, boolean isMove, boolean makeParents, boolean failWhenDstExists) throws SVNException {
    }

    public void copy(SVNURL src, SVNRevision pegRevision, SVNRevision revision, SVNURL dst, boolean isMove, boolean makeParents, boolean failWhenDstExists, String commitMessage) throws SVNException {
    }

    public void add(File path, boolean mkdir, SVNDepth depth, boolean makeParents) throws SVNException {
    }

    public void setProperty(File path, String propName, SVNPropertyValue propValue, SVNDepth depth) throws SVNException {
    }

    public SVNPropertyValue getProperty(File path, String propName, SVNRevision revision) throws SVNException {
        return null;
    }

    public void delete(File path) throws SVNException {
    }

    public void reset() {
    }
}
