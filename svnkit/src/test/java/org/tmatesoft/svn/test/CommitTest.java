package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
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
    
    @Test
    public void testCommitFromExternals16() throws SVNException {
        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCommitFromExternals", TestOptions.getInstance());
        try {
            final SVNURL url1 = sandbox.createSvnRepository();
            final SVNURL url2 = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url1);
            commitBuilder1.addFile("trunk/file", "originalContents".getBytes());
            commitBuilder1.setDirectoryProperty("trunk", "svn:externals", SVNPropertyValue.create("ext " + url2.appendPath("trunk/dir", false)));
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url2);
            commitBuilder2.addFile("trunk/dir/file", "originalContents".getBytes());
            
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url1, SVNRevision.HEAD.getNumber(), false, SvnWcGeneration.V16);
            workingCopy.changeFileContents("trunk/ext/file", "modified");
            
            final File path = workingCopy.getFile("trunk/ext/file");
            SVNCommitClient ci = SVNClientManager.newInstance().getCommitClient();
            final SVNCommitInfo info = ci.doCommit(new File[] {path}, 
                    false, 
                    "message", null, null, false, true, SVNDepth.INFINITY);
            Assert.assertNotNull(info);
            Assert.assertEquals(2, info.getNewRevision());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "CommitTest";
    }
}