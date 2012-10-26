package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnSetLock;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.admin.SvnRepositoryCreate;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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

    @Test
    public void testLockFailsForHook() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLockFailsForHookErrorMessage", options);
        try {
            final ISVNAuthenticationManager authenticationManager = new BasicAuthenticationManager("user", "password");

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user", "password");
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            sandbox.writeHookContents(url, "pre-lock", TestUtil.getFailingHookContents());

            final SVNURL fileUrl = url.appendPath("file", false);

            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromURL(fileUrl));

            try {
                setLock.run();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                //expected
                Assert.assertEquals(SVNErrorCode.REPOS_HOOK_FAILURE, e.getErrorMessage().getErrorCode());
                Assert.assertFalse(e.getErrorMessage().getMessage().contains("\n"));
                Assert.assertFalse(e.getMessage().contains("\n"));
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "HookTest";
    }
}
