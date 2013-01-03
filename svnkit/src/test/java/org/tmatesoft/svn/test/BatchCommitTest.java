package org.tmatesoft.svn.test;

import java.io.File;

import junit.framework.Assert;

import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCommitPacket;

public class BatchCommitTest {
    
    @Test
    public void testCollectCommitItems() throws SVNException {
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCommitFromExternals", TestOptions.getInstance());
        
        final SVNURL firstRepository = sandbox.createSvnRepository();
        createFixture(sandbox, firstRepository);
        final SVNURL secondRepository = sandbox.createSvnRepository();
        createFixture(sandbox, secondRepository);
        
        // wc with a nested wcs
        final WorkingCopy topWc = sandbox.checkoutNewWorkingCopy(firstRepository.appendPath("project1/trunk", false));
        
        final WorkingCopy nestedWcSame = sandbox.checkoutNewWorkingCopy(
                firstRepository.appendPath("project2/trunk", false),
                -1, 
                true, 
                TestUtil.getDefaultWcGeneration(), 
                new File(topWc.getWorkingCopyDirectory(), "same"));
        
        final WorkingCopy nestedWcAnother = sandbox.checkoutNewWorkingCopy(
                secondRepository.appendPath("project1/trunk", false),
                -1, 
                true, 
                TestUtil.getDefaultWcGeneration(), 
                new File(topWc.getWorkingCopyDirectory(), "another"));
        
        // modify all the files.
        TestUtil.writeFileContentsString(new File(topWc.getWorkingCopyDirectory(), "file.txt"), "modified");
        TestUtil.writeFileContentsString(new File(nestedWcSame.getWorkingCopyDirectory(), "file.txt"), "modified");
        TestUtil.writeFileContentsString(new File(nestedWcAnother.getWorkingCopyDirectory(), "file.txt"), "modified");
        
        // collect: at least two packets (combine = true), default three (combine = false)
        final File[] paths = new File[] {
                topWc.getWorkingCopyDirectory(),
                nestedWcAnother.getWorkingCopyDirectory(),
                nestedWcSame.getWorkingCopyDirectory()
                };
        
        SVNCommitPacket[] packets = null;
        SVNCommitClient cc = SVNClientManager.newInstance().getCommitClient();
        try {
            packets = cc.doCollectCommitItems(paths, false, false, SVNDepth.INFINITY, false, null);

            Assert.assertNotNull(packets);
            Assert.assertEquals(3, packets.length);
        } finally {
            for (int i = 0; packets != null && i < packets.length; i++) {
                try {
                    packets[i].dispose();
                } catch (SVNException e) {}
            }
        }
        
        try {
            packets = cc.doCollectCommitItems(paths, false, false, SVNDepth.INFINITY, true, null);

            Assert.assertNotNull(packets);
            Assert.assertEquals(2, packets.length);
        } finally {
            for (int i = 0; packets != null && i < packets.length; i++) {
                try {
                    packets[i].dispose();
                } catch (SVNException e) {}
            }
        }
    }

    @Test
    public void testCommitCollectedItems() throws SVNException {
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCommitFromExternals", TestOptions.getInstance());
        
        final SVNURL firstRepository = sandbox.createSvnRepository();
        createFixture(sandbox, firstRepository);
        final SVNURL secondRepository = sandbox.createSvnRepository();
        createFixture(sandbox, secondRepository);
        
        // wc with a nested wcs
        final WorkingCopy topWc = sandbox.checkoutNewWorkingCopy(firstRepository.appendPath("project1/trunk", false));
        
        final WorkingCopy nestedWcSame = sandbox.checkoutNewWorkingCopy(
                firstRepository.appendPath("project2/trunk", false),
                -1, 
                true, 
                TestUtil.getDefaultWcGeneration(), 
                new File(topWc.getWorkingCopyDirectory(), "same"));
        
        final WorkingCopy nestedWcAnother = sandbox.checkoutNewWorkingCopy(
                secondRepository.appendPath("project1/trunk", false),
                -1, 
                true, 
                TestUtil.getDefaultWcGeneration(), 
                new File(topWc.getWorkingCopyDirectory(), "another"));
        
        // modify all the files.
        TestUtil.writeFileContentsString(new File(topWc.getWorkingCopyDirectory(), "file.txt"), "modified");
        TestUtil.writeFileContentsString(new File(nestedWcSame.getWorkingCopyDirectory(), "file.txt"), "modified");
        TestUtil.writeFileContentsString(new File(nestedWcAnother.getWorkingCopyDirectory(), "file.txt"), "modified");
        
        // collect: at least two packets (combine = true), default three (combine = false)
        final File[] paths = new File[] {
                topWc.getWorkingCopyDirectory(),
                nestedWcAnother.getWorkingCopyDirectory(),
                nestedWcSame.getWorkingCopyDirectory()
                };
        
        topWc.updateToRevision(-1);
        long currentRevision = topWc.getCurrentRevision();
        SVNCommitPacket[] packets = null;
        SVNCommitClient cc = SVNClientManager.newInstance().getCommitClient();
        try {
            packets = cc.doCollectCommitItems(paths, false, false, SVNDepth.INFINITY, false, null);

            Assert.assertNotNull(packets);
            Assert.assertEquals(3, packets.length);

            // do commit, should have three revs
            SVNCommitInfo[] infos = cc.doCommit(packets, false, "three separate commits");
            Assert.assertEquals(3, infos.length);
            topWc.updateToRevision(-1);
            Assert.assertEquals(currentRevision + 2, topWc.getCurrentRevision());
        } catch (SVNException e) {
            e.printStackTrace();
            for (int i = 0; packets != null && i < packets.length; i++) {
                try {
                    packets[i].dispose();
                } catch (SVNException inner) {}
            }
            throw e;
        }

        // modify all the files.
        TestUtil.writeFileContentsString(new File(topWc.getWorkingCopyDirectory(), "file.txt"), "modified again");
        TestUtil.writeFileContentsString(new File(nestedWcSame.getWorkingCopyDirectory(), "file.txt"), "modified again");
        TestUtil.writeFileContentsString(new File(nestedWcAnother.getWorkingCopyDirectory(), "file.txt"), "modified again");

        topWc.updateToRevision(-1);
        currentRevision = topWc.getCurrentRevision();
        try {
            packets = cc.doCollectCommitItems(paths, false, false, SVNDepth.INFINITY, true, null);

            Assert.assertNotNull(packets);
            Assert.assertEquals(2, packets.length);

            // do commit, should have three revs
            SVNCommitInfo[] infos = cc.doCommit(packets, false, "three separate commits");
            Assert.assertEquals(2, infos.length);

            topWc.updateToRevision(-1);
            Assert.assertEquals(currentRevision + 1, topWc.getCurrentRevision());
        } catch (SVNException e) {
            for (int i = 0; packets != null && i < packets.length; i++) {
                try {
                    packets[i].dispose();
                } catch (SVNException inner) {}
            }
            throw e;
        }
}


    private void createFixture(final Sandbox sandbox, final SVNURL repositoryRootURL) throws SVNException {
        final WorkingCopy wc = sandbox.checkoutNewWorkingCopy(repositoryRootURL);
        
        final File p1Directory = new File(wc.getWorkingCopyDirectory(), "project1/trunk");
        final File p1File = new File(wc.getWorkingCopyDirectory(), "project1/trunk/file.txt");
        p1Directory.mkdirs();
        TestUtil.writeFileContentsString(p1File, "project1 file");
        final File p2Directory = new File(wc.getWorkingCopyDirectory(), "project2/trunk");
        final File p2File = new File(wc.getWorkingCopyDirectory(), "project2/trunk/file.txt");
        TestUtil.writeFileContentsString(p2File, "project1 file");
        
        p2Directory.mkdirs();
        wc.add(p1Directory);
        wc.add(p2Directory);
        
        wc.commit("initial import");
        wc.dispose();
    }


    private String getTestName() {
        return getClass().getSimpleName();
    }

}
