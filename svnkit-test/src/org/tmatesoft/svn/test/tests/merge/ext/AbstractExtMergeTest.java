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
package org.tmatesoft.svn.test.tests.merge.ext;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.ResourceBundle;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.test.ISVNTestOptions;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;
import org.tmatesoft.svn.test.wc.SVNWorkingCopyValidator;
import org.tmatesoft.svn.test.environments.AbstractSVNTestEnvironment;
import org.tmatesoft.svn.test.sandboxes.AbstractSVNSandbox;
import org.tmatesoft.svn.test.tests.AbstractSVNTest;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class AbstractExtMergeTest extends AbstractSVNTest implements ISVNTestOptions {

    private ISVNTestExtendedMergeCallback myMergeCallback;
    private boolean myIsFeatureMode;

    protected ISVNTestExtendedMergeCallback getMergeCallback() {
        return myMergeCallback;
    }

    public void setMergeCallback(ISVNTestExtendedMergeCallback mergeCallback) {
        myMergeCallback = mergeCallback;
    }

    protected boolean isFeatureMode() {
        return myIsFeatureMode;
    }

    public void setFeatureMode(boolean isFeatureMode) {
        myIsFeatureMode = isFeatureMode;
    }

    public void init(AbstractSVNSandbox sandbox, AbstractSVNTestEnvironment environment) throws SVNException {
        super.init(sandbox, environment);
    }

    public ISVNTestOptions getOptions() {
        return this;
    }

    public void load(ResourceBundle bundle) throws SVNException {
        String mode = bundle.getString("merge.ext.mode");
        boolean isFeatureMode = "feature".equals(mode);
        setFeatureMode(isFeatureMode);
    }

    public void prepareMerge(SVNURL source, File target, SVNRevision start, SVNRevision end) throws SVNException {
        if (getMergeCallback() != null) {
            getMergeCallback().prepareMerge(source, target, start, end);
        }
    }

    public void dispose() throws SVNException {
        super.dispose();
    }

    protected void createWCs() throws SVNException {
        getEnvironment().copy(getTrunk(), SVNRevision.HEAD, getBranch(), false, false, true, "test branch created");
        getEnvironment().checkout(getBranch(), getBranchWC(), SVNRevision.HEAD, SVNDepth.INFINITY);
        getEnvironment().checkout(getTrunk(), getTrunkWC(), SVNRevision.HEAD, SVNDepth.INFINITY);
    }

    protected void initializeMergeCallback() {
        ISVNTestExtendedMergeCallback defaultCallback = getDefaultCallback();
        ISVNTestExtendedMergeCallback callback = getMergeCallback() != null ? getMergeCallback() : defaultCallback;
        setMergeCallback(callback);
        getEnvironment().setExtendedMergeCallback(callback);
        getEnvironment().setEventHandler(SVNTestDebugLog.getEventHandler());
    }

    private ISVNTestExtendedMergeCallback getDefaultCallback() {
        if (isFeatureMode()) {
            return getFeatureModeCallback();
        }
        return getReleaseModeCallback();
    }

    protected void validateWC(File wc) throws SVNException {
        SVNWCDescriptor descriptor = getMergeCallback().getExpectedState();
        if (descriptor == null) {
            return;
        }
        SVNWorkingCopyValidator validator = createWCValidator(wc, descriptor, true);
        validator.validate();
    }

    protected void mergeLastRevisions(SVNURL url, File wc, long revCount, SVNDepth depth, boolean dryRun, boolean recordOnly) throws SVNException {
        SVNRepository repository = SVNRepositoryFactory.create(url);
        long headRev = repository.getLatestRevision();
        Collection ranges = new LinkedList();
        final SVNRevision startRevision = SVNRevision.create(headRev - revCount);
        final SVNRevision endRevision = SVNRevision.create(headRev);
        ranges.add(new SVNRevisionRange(startRevision, endRevision));
        prepareMerge(url, wc, startRevision, endRevision);
        getEnvironment().merge(url, wc, ranges, depth, dryRun, recordOnly);
    }

    public File getTrunkWC() {
        return getWC();
    }

    public File getBranchWC() {
        return getSecondaryWC();
    }

    public File getTrunkFile(String name) {
        return getFile(name);
    }

    public File getBranchFile(String name) {
        return getSecondaryFile(name);
    }

    public abstract ISVNTestExtendedMergeCallback getReleaseModeCallback();

    public abstract ISVNTestExtendedMergeCallback getFeatureModeCallback();
}
