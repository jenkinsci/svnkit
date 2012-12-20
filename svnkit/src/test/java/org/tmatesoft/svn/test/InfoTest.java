package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

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
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

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

    @Test
    public void testPegRevisionIsConsideredForRemoteInfo() throws Exception {
        //SVNKIT-272
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPegRevisionIsConsideredForRemoteInfo", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("directory");
            commitBuilder2.commit();

            final SVNURL directoryUrl = url.appendPath("directory", false);

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromURL(directoryUrl, SVNRevision.create(1)));
            final SvnInfo info = getInfo.run();

            Assert.assertEquals(1, info.getRevision());
            Assert.assertEquals(1, info.getLastChangedRevision());
            Assert.assertEquals(directoryUrl, info.getUrl());
            Assert.assertEquals(url, info.getRepositoryRootUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testPegRevisionIsConsideredForLocalInfo() throws Exception {
        //SVNKIT-272
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPegRevisionIsConsideredForLocalInfo", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("directory");
            commitBuilder2.commit();

            final SVNURL directoryUrl = url.appendPath("directory", false);

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File directory = workingCopy.getFile("directory");

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromFile(directory, SVNRevision.create(1)));
            final SvnInfo info = getInfo.run();

            Assert.assertEquals(1, info.getRevision());
            Assert.assertEquals(1, info.getLastChangedRevision());
            Assert.assertEquals(directoryUrl, info.getUrl());
            Assert.assertEquals(url, info.getRepositoryRootUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "InfoTest";
    }
}
