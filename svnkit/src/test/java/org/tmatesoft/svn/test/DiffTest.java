package org.tmatesoft.svn.test;

import java.io.ByteArrayOutputStream;

import junit.framework.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class DiffTest {

    @Test
    public void testRemoteDiffTwoFiles() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteDiffTwoFiles", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file1", "contents1".getBytes());
            commitBuilder.addFile("directory/file2", "contents2".getBytes());
            final SVNCommitInfo commitInfo = commitBuilder.commit();

            final SVNRevision svnRevision = SVNRevision.create(commitInfo.getNewRevision());

            final SVNURL url1 = url.appendPath("directory/file1", false);
            final SVNURL url2 = url.appendPath("directory/file2", false);

            final String actualDiffOutput = runDiff(svnOperationFactory, url1, svnRevision, url2, svnRevision);
            final String expectedDiffOutput = "Index: file1" + "\n" +
                    "===================================================================" + "\n" +
                    "--- file1\t(.../file1)\t(revision 1)" + "\n" +
                    "+++ file1\t(.../file2)\t(revision 1)" + "\n" +
                    "@@ -1 +0,0 @@" + "\n" +
                    "-contents1\n" +
                    "\\ No newline at end of file" + "\n" +
                    "Index: file1" + "\n" +
                    "===================================================================" + "\n" +
                    "--- file1\t(.../file1)\t(revision 0)" + "\n" +
                    "+++ file1\t(.../file2)\t(revision 1)" + "\n" +
                    "@@ -0,0 +1 @@" + "\n" +
                    "+contents2" + "\n" +
                    "\\ No newline at end of file" + "\n";

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String runDiff(SvnOperationFactory svnOperationFactory, SVNURL url1, SVNRevision svnRevision1, SVNURL url2, SVNRevision svnRevision2) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setTargets(SvnTarget.fromURL(url1, svnRevision1), SvnTarget.fromURL(url2, svnRevision2));
        diff.setOutput(byteArrayOutputStream);
        diff.run();

        return new String(byteArrayOutputStream.toByteArray());
    }

    public String getTestName() {
        return "DiffTest";
    }
}
