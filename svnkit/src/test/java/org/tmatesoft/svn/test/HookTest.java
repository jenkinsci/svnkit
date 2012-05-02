package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.admin.SvnRepositoryCreate;

import java.io.File;

public class HookTest {

    @Test
    public void testRepositoryPathContainsSpace() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRepositoryPathContainsSpace", options);
        try {
            final File directory = sandbox.createDirectory("directory with space");

            final SvnRepositoryCreate repositoryCreate = svnOperationFactory.createRepositoryCreate();
            repositoryCreate.setRepositoryRoot(directory);
            repositoryCreate.run();

            //create pre-commit hook
            final SVNURL url = SVNURL.fromFile(directory);
            final File preCommitHookFile = TestUtil.getHookFile(directory, "pre-commit");

            //blocked by hook
            TestUtil.writeFileContentsString(preCommitHookFile, TestUtil.getFailingHookContents());
            SVNFileUtil.setExecutable(preCommitHookFile, true);

            final CommitBuilder failingCommitBuilder = new CommitBuilder(url);
            failingCommitBuilder.addFile("file");
            try {
                failingCommitBuilder.commit();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                //expected
                Assert.assertEquals(SVNErrorCode.REPOS_HOOK_FAILURE, e.getErrorMessage().getErrorCode());
            }

            //not blocked by hook
            TestUtil.writeFileContentsString(preCommitHookFile, TestUtil.getSucceedingHookContents());

            final CommitBuilder succeedingCommitBuilder = new CommitBuilder(url);
            succeedingCommitBuilder.addFile("file");
            final SVNCommitInfo commitInfo = succeedingCommitBuilder.commit();
            Assert.assertEquals(1, commitInfo.getNewRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "HookTest";
    }
}
