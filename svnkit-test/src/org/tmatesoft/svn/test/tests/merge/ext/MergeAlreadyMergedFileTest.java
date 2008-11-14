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
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.wc.SVNCopyTask;
import org.tmatesoft.svn.core.wc.SVNEditorAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;
import org.tmatesoft.svn.test.wc.SVNTestFileDescriptor;
import org.tmatesoft.svn.test.wc.SVNWCDescriptor;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class MergeAlreadyMergedFileTest extends AbstractExtMergeTest {

    private long myFisrtMergeStart;
    private long myFisrtMergeEnd;

    private long getFisrtMergeStart() {
        return myFisrtMergeStart;
    }

    private void setFisrtMergeStart(long fisrtMergeStart) {
        myFisrtMergeStart = fisrtMergeStart;
    }

    private long getFisrtMergeEnd() {
        return myFisrtMergeEnd;
    }

    private void setFisrtMergeEnd(long fisrtMergeEnd) {
        myFisrtMergeEnd = fisrtMergeEnd;
    }

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

        getEnvironment().copy(getBranchFile("A/file"), SVNRevision.WORKING, getBranchFile("A/file2"), true, false, true);
        getEnvironment().commit(getBranchFile("A"), "A/file renamed to A/file2", SVNDepth.INFINITY);

        long change1 = getEnvironment().modifyAndCommit(getBranchFile("A/file2"));
        getEnvironment().modifyAndCommit(getBranchFile("A/file2"));
        long change3 = getEnvironment().modifyAndCommit(getBranchFile("A/file2"));
        long change4 = getEnvironment().modifyAndCommit(getBranchFile("A/file2"));

        long start = change1 - 1;
        Collection rangesToMerge = new LinkedList();
        setFisrtMergeStart(start);
        setFisrtMergeEnd(change3);
        rangesToMerge.add(new SVNRevisionRange(SVNRevision.create(start), SVNRevision.create(change3)));
        getEnvironment().merge(getBranch().appendPath("A/file2", false), getTrunkFile("A/file"), rangesToMerge, SVNDepth.INFINITY, false, false);
        getEnvironment().commit(getTrunkWC(), "branch/A/file2 merged to trunk/A/file", SVNDepth.INFINITY);
        getEnvironment().update(getTrunkWC(), SVNRevision.HEAD, SVNDepth.INFINITY);

        getEnvironment().setEventHandler(SVNTestDebugLog.getEventHandler());

        rangesToMerge.clear();
        rangesToMerge.add(new SVNRevisionRange(SVNRevision.create(start), SVNRevision.create(change4)));
        prepareMerge(getBranch(), getTrunkWC(), SVNRevision.create(start), SVNRevision.create(change4));
        setStartRevision(start);
        setEndRevision(change4);
        getEnvironment().merge(getBranch(), getTrunkWC(), rangesToMerge, SVNDepth.INFINITY, false, false);

        validateWC(getTrunkWC());
    }

// ###############  FEATURE MODE  ###################    

    private class FeatureModeCallback implements ISVNTestExtendedMergeCallback {

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) throws SVNException {
            if (targetUrl.getPath().endsWith("trunk/A/file2")) {
                SVNCopySource copySource = new SVNCopySource(SVNRevision.create(targetRevision), SVNRevision.create(targetRevision), getTrunk().appendPath("A/file", false));
                return SVNCopyTask.create(copySource, true); 
            }
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            if (sourceUrl.getPath().endsWith("branch/A/file2")) {
                return new SVNURL[]{getTrunk().appendPath("A/file2", false)};
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

            String content = "";

            SVNTestFileDescriptor file = descriptor.addFile("A/file2", content);
            file.setReplaced(false);
            file.setAdded(false);
            file.setDeleted(false);
            file.setConflicted(true);
            file.setCopyFromLocation(getTrunk().appendPath("A/file", false));
            file.setNodeKind(SVNNodeKind.FILE);

            file.setBaseProperties(new SVNProperties());

            SVNProperties props = new SVNProperties();
            props.put(SVNProperty.MERGE_INFO, "/branches/branch/A/file2:" + new SVNMergeRange(getStartRevision(), getEndRevision(), true).toString());
            file.setProperties(props);

            SVNTestFileDescriptor deletedFile = descriptor.addFile("A/file", null);
            deletedFile.setFileType(SVNFileType.NONE);
            deletedFile.setDeleted(true);
            deletedFile.setNodeKind(SVNNodeKind.FILE);

            return descriptor;
        }
    }

// ###############  RELEASE MODE  ###################    

    private class ReleaseModeCallback implements ISVNTestExtendedMergeCallback {

        public SVNCopyTask getTargetCopySource(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision) {
            return null;
        }

        public SVNURL[] getTrueMergeTargets(SVNURL sourceUrl, long sourceRevision, long sourceMergeFromRevision, long sourceMergeToRevision, SVNURL targetUrl, long targetRevision, SVNEditorAction action) throws SVNException {
            if (sourceUrl.getPath().endsWith("branch/A/file2")) {
                return new SVNURL[]{getTrunk().appendPath("A/file", false)};
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

            String content = "this is A/file\n" +
                    "this line added to file file2 at r4.\n" +
                    "this line added to file file2 at r5.\n" +
                    "this line added to file file2 at r6.\n" +
                    "this line added to file file2 at r7.";

            SVNTestFileDescriptor file = descriptor.addFile("A/file", content);
            file.setReplaced(false);
            file.setAdded(false);
            file.setDeleted(false);
            file.setConflicted(false);
            file.setCopyFromLocation(null);
            file.setCopyFromRevision(-1);
            file.setNodeKind(SVNNodeKind.FILE);

            SVNProperties baseProps = new SVNProperties();
            baseProps.put(SVNProperty.MERGE_INFO, "/branches/branch/A/file2:" + new SVNMergeRange(getFisrtMergeStart(), getFisrtMergeEnd(), true).toString());
            file.setBaseProperties(baseProps);

            SVNProperties props = new SVNProperties();
            props.put(SVNProperty.MERGE_INFO, "/branches/branch/A/file2:" + new SVNMergeRange(getStartRevision(), getEndRevision(), true).toString());
            file.setProperties(props);

            return descriptor;
        }
    }
}