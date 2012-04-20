package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

public class MergeTest {

    @Test
    public void testConflictResolution() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testConflictResolution", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "base".getBytes());
            SVNCommitInfo commitInfo1 = commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "theirs".getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, commitInfo1.getNewRevision());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");
            TestUtil.writeFileContentsString(file, "mine");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            runResolve(svnOperationFactory, file, SVNConflictChoice.MINE_CONFLICT);

            final String fileContentsString = TestUtil.readFileContentsString(file);
            Assert.assertEquals("mine", fileContentsString);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void runResolve(SvnOperationFactory svnOperationFactory, File file, SVNConflictChoice resolution) throws SVNException {
        final SVNClientManager clientManager = SVNClientManager.newInstance(svnOperationFactory.getOptions(), svnOperationFactory.getRepositoryPool());
        try {
            final SVNWCClient wcClient = clientManager.getWCClient();
            wcClient.doResolve(file, SVNDepth.INFINITY, resolution);
        } finally {
            clientManager.dispose();
        }
    }

    private String getTestName() {
        return "MergeTest";
    }
}
