package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnCommitParametersTest {
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
            commit.setCommitParameters(createCommitParametersForAction(ISVNCommitParameters.DELETE, true));
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

    private ISVNCommitParameters createCommitParametersForAction(final ISVNCommitParameters.Action action, final boolean onDeletion) {
        return new ISVNCommitParameters() {
            public Action onMissingFile(File file) {
                return action;
            }

            public Action onMissingDirectory(File file) {
                return action;
            }

            public boolean onDirectoryDeletion(File directory) {
                return onDeletion;
            }

            public boolean onFileDeletion(File file) {
                return onDeletion;
            }
        };
    }

    private String getTestName() {
        return "SvnCommitParametersTest";
    }
}
