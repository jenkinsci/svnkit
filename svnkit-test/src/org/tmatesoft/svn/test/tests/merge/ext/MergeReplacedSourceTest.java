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
package org.tmatesoft.svn.test.tests.merge.ext;

import java.io.File;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNCopyTask;
import org.tmatesoft.svn.core.wc.SVNEditorAction;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;
import org.tmatesoft.svn.test.wc.SVNTestFileDescriptor;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class MergeReplacedSourceTest extends AbstractExtMergeTest {

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
        fs.addFile(new SVNTestFileDescriptor("A"));
        fs.addFile(new SVNTestFileDescriptor("A/file", "this is A/file"));
        return fs;
    }

    public void run() throws SVNException {
        fill();

        createWCs();
        initializeMergeCallback();

        getEnvironment().delete(getBranchFile("A/file"));
        getEnvironment().commit(getBranchFile("A"), "A/file deleted", SVNDepth.INFINITY);

        SVNTestFileDescriptor file = new SVNTestFileDescriptor("A/file", "this is new A/file");
        file.dump(getBranchWC());
        getEnvironment().add(getBranchFile("A/file"), false, SVNDepth.UNKNOWN, false);
        getEnvironment().commit(getBranchWC(), "A/file added", SVNDepth.INFINITY);

        getEnvironment().setEventHandler(SVNTestDebugLog.getEventHandler());

        mergeLastRevisions(getBranch(), getTrunkWC(), 2, SVNDepth.INFINITY, false, false);

        validateWC(getTrunkWC());
    }

// ###############  FEATURE MODE  ###################

    private class FeatureModeCallback implements ISVNTestExtendedMergeCallback {

        public void prepareMerge(SVNURL source, File target) throws SVNException {
        }

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) throws SVNException {
            if (sourceUrl.getPath().endsWith("A/file")) {
                return SVNCopyTask.create(new SVNCopySource(SVNRevision.WORKING, SVNRevision.WORKING, getTrunk().appendPath("A/file", false)), false);
            }
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            if (sourceUrl.getPath().endsWith("A/file")) {
                return new SVNURL[]{getTrunk().appendPath("A/file", false)};
            }
            return null;
        }

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            return null;
        }

        public SVNWCDescriptor getExpectedState() throws SVNException {
            SVNWCDescriptor wc = new SVNWCDescriptor();
            wc.addFile("A");
            SVNTestFileDescriptor file = wc.addFile("A/file", "this is new A/file");
            file.setVersioned(true);
            file.setConflicted(false);
            file.setCopyFromLocation(getTrunk().appendPath("A/file", false));
            file.setReplaced(true);
            return wc;
        }
    }

// ###############  RELEASE MODE  ###################

    private class ReleaseModeCallback implements ISVNTestExtendedMergeCallback {

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) {
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            if (action == SVNEditorAction.ADD || action == SVNEditorAction.MODIFY) {
                if (sourceUrl.getPath().endsWith("branch/A/file2")) {
                    return new SVNURL[]{getTrunk().appendPath("A/file", false)};
                }
            }
            return null;
        }

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            return null;
        }

        public void prepareMerge(SVNURL source, File target) throws SVNException {
        }

        public SVNWCDescriptor getExpectedState() throws SVNException {
            SVNWCDescriptor descriptor = new SVNWCDescriptor();
            descriptor.addFile(new SVNTestFileDescriptor("A"));
            String expectedContent = "this is A/file\n";
            expectedContent = expectedContent.concat("this line added to file file2 at r4.");
            descriptor.addFile(new SVNTestFileDescriptor("A/file", expectedContent));
            return descriptor;
        }
    }
}