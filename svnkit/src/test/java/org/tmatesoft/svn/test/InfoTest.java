package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

public class InfoTest {
    @Test
    public void testLowLevelInfo() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLowLevelInfo", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory");
            commitBuilder.commit();

            final SVNRepository svnRepository = svnOperationFactory.getRepositoryPool().createRepository(url, true);
            final SVNDirEntry dirEntry = svnRepository.info("", 1);

            Assert.assertEquals(SVNNodeKind.DIR, dirEntry.getKind());
            Assert.assertEquals("", dirEntry.getName());
            Assert.assertEquals("", dirEntry.getRelativePath());
            Assert.assertEquals(1, dirEntry.getRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testLowLevelInfoDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLowLevelInfoDavAccess", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory");
            commitBuilder.commit();

            final SVNRepository svnRepository = svnOperationFactory.getRepositoryPool().createRepository(url, true);
            final SVNDirEntry dirEntry = svnRepository.info("", 1);

            Assert.assertEquals(SVNNodeKind.DIR, dirEntry.getKind());
            Assert.assertEquals("", dirEntry.getName());
            Assert.assertEquals("", dirEntry.getRelativePath());
            Assert.assertEquals(1, dirEntry.getRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "InfoTest";
    }
}
