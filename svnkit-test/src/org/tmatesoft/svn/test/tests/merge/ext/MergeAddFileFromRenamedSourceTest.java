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

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNCopyTask;
import org.tmatesoft.svn.core.wc.SVNEditorAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;
import org.tmatesoft.svn.test.wc.SVNTestFileDescriptor;
import org.tmatesoft.svn.test.wc.SVNTestFile;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class MergeAddFileFromRenamedSourceTest extends AbstractExtMergeTest {

    public ISVNTestExtendedMergeCallback getFeatureModeCallback() {
        return new FeatureModeCallback();
    }

    public ISVNTestExtendedMergeCallback getReleaseModeCallback() {
        return new ReleaseModeCallback();
    }

    public String getDumpFile() {
        return null;
    }

    public SVNWCDescriptor getInitialFS() {
        SVNWCDescriptor fs = new SVNWCDescriptor();
        fs.addFile("A");
        fs.addFile("A/file", "this is A/file");
        return fs;
    }

    public void run() throws SVNException {
        fill();

        createWCs();
        initializeMergeCallback();

        getEnvironment().copy(getBranchFile("A"), SVNRevision.WORKING, getBranchFile("B"), true, false, true);
        getEnvironment().commit(getBranchWC(), "Moved A to B", SVNDepth.INFINITY);

        SVNTestFile tFile = new SVNTestFile(getBranchWC(), "B/new", "This file has been added in the branch");
        tFile.dump(getBranchWC());
        getEnvironment().add(getBranchFile("B/new"), false, SVNDepth.EMPTY, false);
        getEnvironment().commit(getBranchWC(), "Added B/new", SVNDepth.INFINITY);

        getEnvironment().update(getTrunkWC(), SVNRevision.HEAD, SVNDepth.INFINITY);
        mergeLastRevisions(getBranch(), getTrunkWC(), 1, SVNDepth.INFINITY, false, false);
        validateWC(getTrunkWC());
    }

//    Implementations are absolutely similar

// ###############  FEATURE MODE  ###################

    private class FeatureModeCallback implements ISVNTestExtendedMergeCallback {

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) throws SVNException {
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            if (sourceUrl.getPath().endsWith("branch/B/new")) {
                return new SVNURL[]{getTrunk().appendPath("A/new", false)};
            }

            return null;
        }

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            return null;
        }

        public void prepareMerge(SVNURL source, File target, SVNRevision start, SVNRevision end) throws SVNException {
        }

        public SVNWCDescriptor getExpectedState() throws SVNException {
            SVNWCDescriptor descriptor = new SVNWCDescriptor();
            descriptor.addFile("A").setVersioned(true);

            SVNTestFileDescriptor file = descriptor.addFile("A/new", "This file has been added in the branch");
            file.setAdded(true);
            file.setProperties(new SVNProperties());
            file.setVersioned(true);
            return descriptor;
        }
    }

// ###############  RELEASE MODE  ###################

    private class ReleaseModeCallback implements ISVNTestExtendedMergeCallback {

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) throws SVNException {
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            if (sourceUrl.getPath().endsWith("branch/B/new")) {
                return new SVNURL[]{getTrunk().appendPath("A/new", false)};
            }

            return null;
        }

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            return null;
        }

        public void prepareMerge(SVNURL source, File target, SVNRevision start, SVNRevision end) throws SVNException {
        }

        public SVNWCDescriptor getExpectedState() throws SVNException {
            SVNWCDescriptor descriptor = new SVNWCDescriptor();
            descriptor.addFile("A").setVersioned(true);

            SVNTestFileDescriptor file = descriptor.addFile("A/new", "This file has been added in the branch");
            file.setAdded(true);
            file.setNodeKind(SVNNodeKind.FILE);
            file.setConflicted(false);
            file.setBaseProperties(new SVNProperties());
            file.setProperties(new SVNProperties());
            file.setVersioned(true);
            return descriptor;
        }
    }
}