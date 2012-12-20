package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.ISvnCommitParameters;
import org.tmatesoft.svn.core.wc2.ISvnCommitParameters.Action;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnCommitPacket;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

public class SvnCommitParametersTest {

    private static final String DIR_PATH = "directory";
    private static final String MISSING_DIR_PATH = "directory/subdir";
    private static final String CHANGED_FILE_PATH = "directory/file2";
    private static final String MISSING_FILE_PATH = "directory/file1";

    private static final String COPY_DIR_PATH = "copy";
    private static final String COPY_MISSING_DIR_PATH = "copy/subdir";
    private static final String COPY_CHANGED_FILE_PATH = "copy/file2";
    private static final String COPY_MISSING_FILE_PATH = "copy/file1";

    public String getTestName() {
        return "SvnCommitParametersTest";
    }
    @Test
    public void testMissingFileDelete() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMissingFileDelete", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("someFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File someFile = new File(workingCopyDirectory, "someFile");
            SVNFileUtil.deleteFile(someFile);

            final SvnCommit commit = svnOperationFactory.createCommit();
            commit.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            commit.setCommitParameters(createCommitParameters(ISvnCommitParameters.Action.DELETE, true, ISvnCommitParameters.Action.DELETE, true));
            commit.setCommitMessage("");
            final SVNCommitInfo commitInfo = commit.run();

            Assert.assertNotNull(commitInfo);
            Assert.assertEquals(2, commitInfo.getNewRevision());

            workingCopy.revert();
            Assert.assertFalse(someFile.exists());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testDeleteMissing() throws SVNException {
        WorkingCopy wc = prepareMissingWc(getTestName());
        
        File missingFile = wc.getFile(MISSING_FILE_PATH);
        File missingDir = wc.getFile(MISSING_DIR_PATH);
        File changedFile = wc.getFile(CHANGED_FILE_PATH);

        SvnCommitPacket packet = collectPacket(wc, new File[] {changedFile, missingFile, missingDir}, new SvnOperationFactory(), 
                createCommitParameters(Action.DELETE, true, Action.DELETE, true), SVNDepth.EMPTY);

        try {
            Assert.assertNotNull(packet.getItem(changedFile));
            Assert.assertNotNull(packet.getItem(missingFile));
            Assert.assertNotNull(packet.getItem(missingDir));
        } finally {
            packet.dispose();
        }
        
        SvnStatus st = wc.getStatus(MISSING_FILE_PATH);
        Assert.assertEquals(SVNStatusType.STATUS_DELETED, st.getNodeStatus());
        st = wc.getStatus(MISSING_DIR_PATH);
        Assert.assertEquals(SVNStatusType.STATUS_DELETED, st.getNodeStatus());
    }

    @Test
    public void testSkipMissing() throws SVNException {
        WorkingCopy wc = prepareMissingWc(getTestName());
        
        File missingFile = wc.getFile(MISSING_FILE_PATH);
        File missingDir = wc.getFile(MISSING_DIR_PATH);
        File changedFile = wc.getFile(CHANGED_FILE_PATH);
        
        SvnCommitPacket packet = collectPacket(wc, new File[] {changedFile, missingFile, missingDir}, new SvnOperationFactory(), 
                createCommitParameters(Action.SKIP, true, Action.SKIP, true), SVNDepth.EMPTY);

        Assert.assertNotNull(packet.getItem(changedFile));
        Assert.assertNull(packet.getItem(missingFile));
        Assert.assertNull(packet.getItem(missingDir));
        
        packet.dispose();
        
        SvnStatus st = wc.getStatus(MISSING_FILE_PATH);
        Assert.assertEquals(SVNStatusType.STATUS_MISSING, st.getNodeStatus());
        st = wc.getStatus(MISSING_DIR_PATH);
        Assert.assertEquals(SVNStatusType.STATUS_MISSING, st.getNodeStatus());
    }
    
    @Test
    public void testFailOnMissing() throws SVNException {
        WorkingCopy wc = prepareMissingWc(getTestName());
        
        File missingFile = wc.getFile(MISSING_FILE_PATH);
        File missingDir = wc.getFile(MISSING_DIR_PATH);
        File changedFile = wc.getFile(CHANGED_FILE_PATH);
        
        try {
            collectPacket(wc, new File[] {changedFile, missingFile, missingDir}, new SvnOperationFactory(), 
                createCommitParameters(Action.ERROR, true, Action.SKIP, true), SVNDepth.EMPTY);
            Assert.fail();
        } catch (SVNException e) {
            //
        }
        SvnStatus st = wc.getStatus(MISSING_FILE_PATH);
        Assert.assertEquals(SVNStatusType.STATUS_MISSING, st.getNodeStatus());
        st = wc.getStatus(MISSING_DIR_PATH);
        Assert.assertEquals(SVNStatusType.STATUS_MISSING, st.getNodeStatus());
    }

    @Test
    public void testDeleteMissingInACopy() throws SVNException {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        WorkingCopy wc = prepareMissingWc(getTestName());
        
        File missingFile = wc.getFile(COPY_MISSING_FILE_PATH);
        File missingDir = wc.getFile(COPY_MISSING_DIR_PATH);
        File changedFile = wc.getFile(COPY_CHANGED_FILE_PATH);
        File copyRoot = wc.getFile(COPY_DIR_PATH);

        wc.copy(DIR_PATH, COPY_DIR_PATH);
        
        SvnStatus st = wc.getStatus(COPY_MISSING_FILE_PATH);
        Assert.assertEquals(SVNStatusType.STATUS_MISSING, st.getNodeStatus());
        st = wc.getStatus(COPY_MISSING_DIR_PATH);
        Assert.assertEquals(SVNStatusType.STATUS_MISSING, st.getNodeStatus());

        SvnCommitPacket packet = collectPacket(wc, new File[] {copyRoot, changedFile, missingFile, missingDir}, new SvnOperationFactory(), 
                createCommitParameters(Action.DELETE, true, Action.DELETE, true), SVNDepth.EMPTY);

        Assert.assertNotNull(packet.getItem(changedFile));
        Assert.assertNotNull(packet.getItem(missingFile));
        Assert.assertNotNull(packet.getItem(missingDir));
        
        packet.dispose();
        
        st = wc.getStatus(COPY_MISSING_FILE_PATH);
        Assert.assertEquals(SVNStatusType.STATUS_DELETED, st.getNodeStatus());
        st = wc.getStatus(COPY_MISSING_DIR_PATH);
        Assert.assertEquals(SVNStatusType.STATUS_DELETED, st.getNodeStatus());
    }

    @Test
    public void testPostCommitDeletions() throws SVNException {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        WorkingCopy wc = prepareMissingWc(getTestName());
        
        File missingFile = wc.getFile(MISSING_FILE_PATH);
        File missingDir = wc.getFile(MISSING_DIR_PATH);
        File dir = wc.getFile(DIR_PATH);

        SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        SvnScheduleForRemoval rm = svnOperationFactory.createScheduleForRemoval();
        rm.addTarget(SvnTarget.fromFile(missingDir));
        rm.addTarget(SvnTarget.fromFile(missingFile));
        rm.setDepth(SVNDepth.EMPTY);
        rm.setDeleteFiles(false);
        rm.run();
        
        restoreDirectory(missingDir, svnOperationFactory);
        
        wc.changeFileContents(MISSING_FILE_PATH, "changed contents");
        
        Assert.assertTrue(missingDir.isDirectory());
        Assert.assertTrue(missingFile.isFile());
        
        SvnCommit ci = svnOperationFactory.createCommit();
        ci.setCommitMessage("message");
        ci.addTarget(SvnTarget.fromFile(dir));
        ci.setCommitParameters(createCommitParameters(Action.DELETE, true, Action.SKIP, false));
        ci.setDepth(SVNDepth.INFINITY);
        ci.run();

        Assert.assertTrue(missingDir.isDirectory());
        Assert.assertTrue(!missingFile.exists());
    }
    private void restoreDirectory(File missingDir, SvnOperationFactory svnOperationFactory) throws SVNException {
        if (TestUtil.isNewWorkingCopyTest()) {
            missingDir.mkdir();
        } else {
            SvnUpdate up = svnOperationFactory.createUpdate();
            up.setSingleTarget(SvnTarget.fromFile(missingDir));
            up.run();
        }
    }

    @Test
    public void testPostCommitForReplacement() throws SVNException {
        WorkingCopy wc = prepareMissingWc(getTestName());
        
        File missingFile = wc.getFile(MISSING_FILE_PATH);
        File missingDir = wc.getFile(MISSING_DIR_PATH);
        File dir = wc.getFile(DIR_PATH);

        SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        SvnScheduleForRemoval rm = svnOperationFactory.createScheduleForRemoval();
        rm.addTarget(SvnTarget.fromFile(missingFile));
        rm.addTarget(SvnTarget.fromFile(missingDir));
        rm.setDepth(SVNDepth.EMPTY);
        rm.run();
        
        restoreDirectory(missingDir, svnOperationFactory);
        wc.changeFileContents(MISSING_FILE_PATH, "changed contents");

        SvnScheduleForAddition add = svnOperationFactory.createScheduleForAddition();
        add.addTarget(SvnTarget.fromFile(missingFile));
        add.addTarget(SvnTarget.fromFile(missingDir));
        add.run();
        
        Assert.assertTrue(missingDir.isDirectory());
        Assert.assertTrue(missingFile.isFile());
        
        SvnCommit ci = svnOperationFactory.createCommit();
        ci.setCommitMessage("message");
        ci.addTarget(SvnTarget.fromFile(dir));
        ci.setCommitParameters(createCommitParameters(Action.SKIP, true, Action.SKIP, true));
        ci.setDepth(SVNDepth.INFINITY);
        ci.run();

        Assert.assertTrue(missingDir.isDirectory());
        Assert.assertTrue(missingFile.exists());
    }

    private WorkingCopy prepareMissingWc(String sandboxName) throws SVNException {
        final TestOptions options = TestOptions.getInstance();
        final Sandbox sandbox = Sandbox.createWithCleanup(sandboxName, options);
        final SVNURL url = sandbox.createSvnRepository();

        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addFile(MISSING_FILE_PATH, "contents1".getBytes());
        commitBuilder.addFile(CHANGED_FILE_PATH, "contents2".getBytes());
        commitBuilder.addDirectory(MISSING_DIR_PATH);
        commitBuilder.commit();

        WorkingCopy wc = sandbox.checkoutNewWorkingCopy(url, -1);

        wc.deleteFile(MISSING_FILE_PATH);
        wc.changeFileContents(CHANGED_FILE_PATH, "changed");
        wc.deleteFile(MISSING_DIR_PATH);
        
        return wc;
    }
    
    private SvnCommitPacket collectPacket(WorkingCopy wc, File[] targets, SvnOperationFactory of, ISvnCommitParameters params, SVNDepth depth) throws SVNException {
        SvnCommit ci = of.createCommit();
        ci.setCommitParameters(params);
        for (int i = 0; i < targets.length; i++) {
            ci.addTarget(SvnTarget.fromFile(targets[i]));
        }
        ci.setDepth(depth);
        
        return ci.collectCommitItems();
    }

    private ISvnCommitParameters createCommitParameters(final ISvnCommitParameters.Action onMissingFile, final boolean deleteFile,
            final ISvnCommitParameters.Action onMissingDir, final boolean deleteDir) {
        return new ISvnCommitParameters() {
            public ISvnCommitParameters.Action onMissingFile(File file) {
                return onMissingFile;
            }
            public ISvnCommitParameters.Action onMissingDirectory(File file) {
                return onMissingDir;
            }
            public boolean onDirectoryDeletion(File directory) {
                return deleteDir;
            }
            public boolean onFileDeletion(File file) {
                return deleteFile;
            }
        };
    }
}
