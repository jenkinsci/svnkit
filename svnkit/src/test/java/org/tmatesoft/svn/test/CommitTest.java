package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class CommitTest {
    @Test
    public void testFileIsChangedToEmpty() throws SVNException {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileIsChangedToEmpty", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file", "originalContents".getBytes());
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");

            final String emptyString = "";
            TestUtil.writeFileContentsString(file, emptyString);

            final SvnCommit commit = svnOperationFactory.createCommit();
            commit.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            commit.setCommitMessage("File contents is changed to empty");
            final SVNCommitInfo commitInfo = commit.run();

            Assert.assertEquals(2, commitInfo.getNewRevision());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "CommitTest";
    }
}