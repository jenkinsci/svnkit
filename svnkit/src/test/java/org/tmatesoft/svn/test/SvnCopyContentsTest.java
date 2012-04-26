package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnCopyContentsTest {

    @Test
    public void testRemoteToRemote() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteToRemote", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source/file");
            commitBuilder.addDirectory("target");
            commitBuilder.commit();

            final SVNURL sourceUrl = url.appendPath("source", false);
            final SVNURL targetUrl = url.appendPath("target", false);

            final SvnRemoteCopy copy = svnOperationFactory.createRemoteCopy();
            copy.setFailWhenDstExists(false);
            copy.addCopySource(createCopySourceForContents(sourceUrl));
            copy.setSingleTarget(SvnTarget.fromURL(targetUrl));
            final SVNCommitInfo commitInfo = copy.run();

            Assert.assertNotNull(commitInfo);
            Assert.assertEquals(2, commitInfo.getNewRevision());
            Assert.assertTrue(isFile(url, "target/file"));
            Assert.assertFalse(exists(url, "target/source"));
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteToLocal() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteToLocal", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source/file");
            commitBuilder.addDirectory("target");
            commitBuilder.commit();

            final SVNURL sourceUrl = url.appendPath("source", false);

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File targetDirectory = new File(workingCopyDirectory, "target");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setFailWhenDstExists(false);
            copy.addCopySource(createCopySourceForContents(sourceUrl));
            copy.setSingleTarget(SvnTarget.fromFile(targetDirectory));
            copy.run();

            Assert.assertTrue(isFile(workingCopyDirectory, "target/file"));
            Assert.assertFalse(exists(workingCopyDirectory, "target/source"));
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private boolean exists(File directory, String path) {
        return new File(directory, path).exists();
    }

    private boolean isFile(File directory, String path) {
        return new File(directory, path).isFile();
    }

    private boolean exists(SVNURL url, String path) throws SVNException {
        final SVNNodeKind nodeKind = getNodeKind(url, path);
        return nodeKind == SVNNodeKind.DIR || nodeKind == SVNNodeKind.FILE;
    }

    private boolean isFile(SVNURL url, String path) throws SVNException {
        final SVNNodeKind nodeKind = getNodeKind(url, path);
        return nodeKind == SVNNodeKind.FILE;
    }

    private SVNNodeKind getNodeKind(SVNURL url, String path) throws SVNException {
        final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
        try {
            return svnRepository.checkPath(path, SVNRepository.INVALID_REVISION);
        } finally {
            svnRepository.closeSession();
        }
    }

    private SvnCopySource createCopySourceForContents(SVNURL url) {
        final SvnCopySource copySource = SvnCopySource.create(SvnTarget.fromURL(url), SVNRevision.UNDEFINED);
        copySource.setCopyContents(true);
        return copySource;
    }

    private String getTestName() {
        return "SvnCopyContentsTest";
    }
}
