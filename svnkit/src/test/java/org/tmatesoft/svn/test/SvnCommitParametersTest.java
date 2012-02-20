package org.tmatesoft.svn.test;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnCommitPacket;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnCommitParametersTest {

    public String getTestName() {
        return "SvnCommitParametersTest";
    }

    @Test
    public void testCommitParameters() throws SVNException, IOException {
        final TestOptions options = TestOptions.getInstance();
        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCommitParams", options);
        final SVNURL url = sandbox.createSvnRepository();

        final CommitBuilder commitBuilder = new CommitBuilder(url);
        String missingFilePath = "directory/file1";
        String changedFilePath = "directory/file2";
        String missingDirPath = "directory/subdir";

        commitBuilder.addFile(missingFilePath, "contents1".getBytes());
        commitBuilder.addFile(changedFilePath, "contents2".getBytes());
        commitBuilder.addDirectory(missingDirPath);
        commitBuilder.commit();

        WorkingCopy wc = sandbox.checkoutNewWorkingCopy(url, -1);
        File missingFile = wc.deleteFile(missingFilePath);
        File changedFile = wc.changeFileContents(changedFilePath, "changed");
        File missingDir = wc.deleteFile(missingDirPath);

        // delete missing file and directory
        SvnCommitPacket packet = collectPacket(wc, new File[] {changedFile, missingFile, missingDir}, svnOperationFactory, 
                createCommitParameters(ISVNCommitParameters.DELETE, true, ISVNCommitParameters.DELETE, true), SVNDepth.EMPTY);

        Assert.assertNotNull(packet.getItem(changedFile));
        Assert.assertNotNull(packet.getItem(missingFile));
        Assert.assertNotNull(packet.getItem(missingDir));
        
        packet.dispose();

        // skip missing file and directory
        packet = collectPacket(wc, new File[] {changedFile, missingFile, missingDir}, svnOperationFactory, 
                createCommitParameters(ISVNCommitParameters.SKIP, true, ISVNCommitParameters.SKIP, true), SVNDepth.EMPTY);

        Assert.assertNotNull(packet.getItem(changedFile));
        Assert.assertNull(packet.getItem(missingFile));
        Assert.assertNull(packet.getItem(missingDir));
        
        packet.dispose();
        
        // skip missing file and directory
        packet = null;
        try {
            packet = collectPacket(wc, new File[] {changedFile, missingFile, missingDir}, svnOperationFactory, 
                    createCommitParameters(ISVNCommitParameters.ERROR, true, ISVNCommitParameters.SKIP, true), SVNDepth.EMPTY);
            Assert.fail();
        } catch (SVNException e) {
        }
        Assert.assertNull(packet);
        
        // copy dir to another dir.
        File copyDir = wc.getFile("copy");
        File missingCopyFile = wc.getFile("copy/file1");
        File missingCopyDir= wc.getFile("copy/subdir");
        File changedCopyFile= wc.getFile("copy/subdir");

        SvnCopy cp = svnOperationFactory.createCopy();
        cp.addCopySource(SvnCopySource.create(SvnTarget.fromFile(wc.getFile("directory")), SVNRevision.WORKING));
        cp.setSingleTarget(SvnTarget.fromFile(copyDir));
        cp.run();

        // delete missing file in a copied directory        
        packet = collectPacket(wc, new File[] {copyDir}, svnOperationFactory, 
                createCommitParameters(ISVNCommitParameters.DELETE, true, ISVNCommitParameters.DELETE, true), SVNDepth.INFINITY);
        Assert.assertNotNull(packet.getItem(copyDir));
        Assert.assertNotNull(packet.getItem(missingCopyFile));
        Assert.assertNotNull(packet.getItem(missingCopyDir));
        Assert.assertNotNull(packet.getItem(changedCopyFile));
        
        packet.dispose();
        
        // same non-recursive
        packet = collectPacket(wc, new File[] {missingCopyDir, missingCopyFile, changedCopyFile, copyDir}, svnOperationFactory, 
                createCommitParameters(ISVNCommitParameters.DELETE, true, ISVNCommitParameters.DELETE, true), SVNDepth.EMPTY);
        Assert.assertNotNull(packet.getItem(copyDir));
        Assert.assertNotNull(packet.getItem(missingCopyFile));
        Assert.assertNotNull(packet.getItem(missingCopyDir));
        Assert.assertNotNull(packet.getItem(changedCopyFile));
        
        packet.dispose();
        
        missingDir.mkdir();
        wc.changeFileContents(missingFilePath, "contents1");
       
        SvnScheduleForRemoval rm = svnOperationFactory.createScheduleForRemoval();
        rm.addTarget(SvnTarget.fromFile(missingDir));
        rm.addTarget(SvnTarget.fromFile(missingFile));
        rm.setDepth(SVNDepth.EMPTY);
        rm.setDeleteFiles(false);
        rm.run();
        
        Assert.assertTrue(missingDir.isDirectory());
        Assert.assertTrue(missingFile.isFile());
        
        SvnCommit ci = svnOperationFactory.createCommit();
        ci.setCommitMessage("message");
        ci.addTarget(SvnTarget.fromFile(wc.getFile("directory")));
        ci.setCommitParameters(createCommitParameters(ISVNCommitParameters.SKIP, true, ISVNCommitParameters.SKIP, false));
        ci.setDepth(SVNDepth.INFINITY);
        ci.run();

        Assert.assertTrue(missingDir.isDirectory());
        Assert.assertTrue(!missingFile.exists());
    }
    
    private SvnCommitPacket collectPacket(WorkingCopy wc, File[] targets, SvnOperationFactory of, ISVNCommitParameters params, SVNDepth depth) throws SVNException {
        SvnCommit ci = of.createCommit();
        ci.setCommitParameters(params);
        for (int i = 0; i < targets.length; i++) {
            ci.addTarget(SvnTarget.fromFile(targets[i]));
        }
        ci.setDepth(depth);
        
        return ci.collectCommitItems();
    }

    private ISVNCommitParameters createCommitParameters(final ISVNCommitParameters.Action onMissingFile, final boolean deleteFile,
            final ISVNCommitParameters.Action onMissingDir, final boolean deleteDir) {
        return new ISVNCommitParameters() {
            public Action onMissingFile(File file) {
                return onMissingFile;
            }
            public Action onMissingDirectory(File file) {
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
