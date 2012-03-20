package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnCommitPacket;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevert;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnCaseMoveTest {

    @Before
    public void setup() {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
    }
    
    @Test
    public void testFileMove() throws SVNException {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + ".file", options);
        final SVNURL url = sandbox.createSvnRepository();

        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addFile("file.txt");
        commitBuilder.commit();

        // move
        WorkingCopy wc = sandbox.checkoutNewWorkingCopy(url, -1);
        
        SvnCopy mv = svnOperationFactory.createCopy();
        mv.addCopySource(SvnCopySource.create(SvnTarget.fromFile(wc.getFile("file.txt")), SVNRevision.WORKING));
        mv.setSingleTarget(SvnTarget.fromFile(wc.getFile("File.txt")));
        mv.setMove(true);
        mv.run();
        assertSingleFilePresence(wc.getWorkingCopyDirectory(), "File.txt");

        // revert
        SvnRevert revert = svnOperationFactory.createRevert();
        revert.setSingleTarget(SvnTarget.fromFile(wc.getWorkingCopyDirectory()));
        revert.setDepth(SVNDepth.INFINITY);
        revert.run();
        assertSingleFilePresence(wc.getWorkingCopyDirectory(), "file.txt");

        // move again
        mv.run();
        assertSingleFilePresence(wc.getWorkingCopyDirectory(), "File.txt");
        
        // commit
        SvnCommit ci = svnOperationFactory.createCommit();
        ci.setCommitMessage("case rename");
        ci.setSingleTarget(SvnTarget.fromFile(wc.getWorkingCopyDirectory()));
        ci.setDepth(SVNDepth.INFINITY);
        SvnCommitPacket packet = ci.collectCommitItems();
        Assert.assertEquals(2, packet.getItems(packet.getRepositoryRoots().iterator().next()).size());
        ci.run();
        
        // move back
        mv = svnOperationFactory.createCopy();
        mv.addCopySource(SvnCopySource.create(SvnTarget.fromFile(wc.getFile("File.txt")), SVNRevision.WORKING));
        mv.setSingleTarget(SvnTarget.fromFile(wc.getFile("file.txt")));
        mv.setMove(true);
        mv.run();
        assertSingleFilePresence(wc.getWorkingCopyDirectory(), "file.txt");
        
        // commit
        ci = svnOperationFactory.createCommit();
        ci.setCommitMessage("case rename back");
        ci.setSingleTarget(SvnTarget.fromFile(wc.getWorkingCopyDirectory()));
        ci.setDepth(SVNDepth.INFINITY);
        packet = ci.collectCommitItems();
        Assert.assertEquals(2, packet.getItems(packet.getRepositoryRoots().iterator().next()).size());
        ci.run();
    }

    private void assertSingleFilePresence(File dir, String name) {
        File[] files = dir.listFiles();
        Assert.assertTrue(files != null);
        for (int i = 0; i < files.length; i++) {
            if (SVNFileUtil.getAdminDirectoryName().equals(files[i].getName())) {
                continue;
            }
            Assert.assertTrue(files[i].isFile());
            Assert.assertEquals(name, files[i].getName());
        }
    }

    @Test
    public void testDirectoryMove() throws SVNException {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + ".dir", options);
        final SVNURL url = sandbox.createSvnRepository();

        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addDirectory("dima");
        commitBuilder.addFile("dima/file.txt");
        commitBuilder.commit();

        // move
        WorkingCopy wc = sandbox.checkoutNewWorkingCopy(url, -1);
        
        SvnCopy mv = svnOperationFactory.createCopy();
        mv.addCopySource(SvnCopySource.create(SvnTarget.fromFile(wc.getFile("dima")), SVNRevision.WORKING));
        mv.setSingleTarget(SvnTarget.fromFile(wc.getFile("Dima")));
        mv.setMove(true);
        mv.run();
        assertSingleDirectoryPresence(wc.getWorkingCopyDirectory(), "Dima");

        // revert
        SvnRevert revert = svnOperationFactory.createRevert();
        revert.setSingleTarget(SvnTarget.fromFile(wc.getWorkingCopyDirectory()));
        revert.setDepth(SVNDepth.INFINITY);
        revert.run();
        assertSingleDirectoryPresence(wc.getWorkingCopyDirectory(), "dima");

        // move again
        mv.run();
        assertSingleDirectoryPresence(wc.getWorkingCopyDirectory(), "Dima");
        
        // commit
        SvnCommit ci = svnOperationFactory.createCommit();
        ci.setCommitMessage("case rename");
        ci.setSingleTarget(SvnTarget.fromFile(wc.getWorkingCopyDirectory()));
        ci.setDepth(SVNDepth.INFINITY);
        SvnCommitPacket packet = ci.collectCommitItems();
        Assert.assertEquals(2, packet.getItems(packet.getRepositoryRoots().iterator().next()).size());
        ci.run();
        
        // move back
        mv = svnOperationFactory.createCopy();
        mv.addCopySource(SvnCopySource.create(SvnTarget.fromFile(wc.getFile("Dima")), SVNRevision.WORKING));
        mv.setSingleTarget(SvnTarget.fromFile(wc.getFile("dima")));
        mv.setMove(true);
        mv.run();
        assertSingleDirectoryPresence(wc.getWorkingCopyDirectory(), "dima");
        
        // modify a file within
        // commit
        wc.changeFileContents("dima/file.txt", "modified");
        
        ci = svnOperationFactory.createCommit();
        ci.setCommitMessage("case rename back");
        ci.setSingleTarget(SvnTarget.fromFile(wc.getWorkingCopyDirectory()));
        ci.setDepth(SVNDepth.INFINITY);
        packet = ci.collectCommitItems();
        Assert.assertEquals(3, packet.getItems(packet.getRepositoryRoots().iterator().next()).size());
        ci.run();
        
    }

    private void assertSingleDirectoryPresence(File dir, String name) {
        File[] files = dir.listFiles();
        Assert.assertTrue(files != null);
        for (int i = 0; i < files.length; i++) {
            if (SVNFileUtil.getAdminDirectoryName().equals(files[i].getName())) {
                continue;
            }
            Assert.assertTrue(files[i].isDirectory());
            Assert.assertEquals(name, files[i].getName());
        }
    }

}
