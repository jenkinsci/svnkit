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

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.wc.SVNCopyTask;
import org.tmatesoft.svn.core.wc.SVNEditorAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;
import org.tmatesoft.svn.test.wc.SVNTestFileDescriptor;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class MergeAlreadyMergedFileTest2 extends AbstractExtMergeTest {

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

        getEnvironment().copy(getTrunkFile("A/file"), SVNRevision.WORKING, getTrunkFile("A/file2"), true, false, true);
        getEnvironment().commit(getTrunkFile("A"), "A/file renamed to A/file2", SVNDepth.INFINITY);

        long revision = getEnvironment().modifyAndCommit(getBranchFile("A/file"));

        getEnvironment().setProperty(getBranchFile("A/file"), "property", SVNPropertyValue.create("value is set at r" + revision), SVNDepth.UNKNOWN);
        long propChangeRev = getEnvironment().commit(getBranchWC(), "A/file property changed", SVNDepth.INFINITY);

        long revision2 = getEnvironment().modifyAndCommit(getBranchFile("A/file"));

        Collection ranges = new LinkedList();
        ranges.add(new SVNRevisionRange( SVNRevision.create(revision - 1), SVNRevision.create(revision)));
        ranges.add(new SVNRevisionRange( SVNRevision.create(revision2 - 1), SVNRevision.create(revision2)));
        getEnvironment().merge(getBranch().appendPath("A/file", false), getTrunkFile("A/file2"), ranges, SVNDepth.INFINITY, false, false);
        getEnvironment().commit(getTrunkWC(), "A/file content modifications merged to A/file2", SVNDepth.INFINITY);

        getEnvironment().setEventHandler(SVNTestDebugLog.getEventHandler());

        ranges.clear();
        setStartRevision(propChangeRev - 1);
        setEndRevision(propChangeRev);
        ranges.add(new SVNRevisionRange(SVNRevision.create(propChangeRev - 1), SVNRevision.create(propChangeRev)));
        prepareMerge(getBranch(), getTrunkWC());
        getEnvironment().merge(getBranch(), getTrunkWC(), ranges, SVNDepth.INFINITY, false, false);

        validateWC(getTrunkWC());
    }

// ###############  FEATURE MODE  ###################

    private class FeatureModeCallback implements ISVNTestExtendedMergeCallback {

        public void prepareMerge(SVNURL source, File target) throws SVNException {
        }

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) throws SVNException {
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            return new SVNURL[0];
        }

        public SVNURL transformLocation(SVNURL sourceUrl, long sourceRevision, long targetRevision) throws SVNException {
            return null;
        }

        public SVNWCDescriptor getExpectedState() throws SVNException {
            return null;
        }
    }

// ###############  RELEASE MODE  ###################

    private class ReleaseModeCallback implements ISVNTestExtendedMergeCallback {

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) throws SVNException {
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            if (sourceUrl.getPath().endsWith("branch/A/file")) {
                return new SVNURL[]{getTrunk().appendPath("A/file2", false)};
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
            descriptor.addFile("A");

            String content = "this is A/file\n" +
                    "this line added to file file at r4.\n" +
                    "this line added to file file at r6.";
            SVNTestFileDescriptor file = descriptor.addFile("A/file2", content);
            file.setVersioned(true);
            file.setConflicted(false);
            file.setAdded(false);
            file.setReplaced(false);
            file.setNodeKind(SVNNodeKind.FILE);
            file.setCopyFromLocation(getTrunk().appendPath("A/file", false));
            file.setCopyFromRevision(7);
            
            SVNProperties baseProperties = new SVNProperties();
            baseProperties.put(SVNProperty.MERGE_INFO, "/branches/branch/A/file:4,6");
            file.setBaseProperties(baseProperties);

            SVNProperties properties = new SVNProperties();
            properties.put("property", "value is set at r4");
            properties.put(SVNProperty.MERGE_INFO, "/branches/branch/A/file:4-6");
            file.setProperties(properties);
            return descriptor;
        }
    }
}