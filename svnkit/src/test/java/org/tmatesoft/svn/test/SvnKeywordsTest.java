package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

public class SvnKeywordsTest {
    @Test
    public void testKeywordAreReexpanded() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testKeywordAreReexpanded", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String notExpandedContentsString = "This revision is $Rev$.";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file", notExpandedContentsString.getBytes());
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "directory/file");
            workingCopy.setProperty(file, SVNProperty.KEYWORDS, SVNPropertyValue.create("Rev"));

            final long committedRevision = workingCopy.commit("Changed a keyword");
            workingCopy.updateToRevision(committedRevision);

            final String fileContentsString = TestUtil.readFileContentsString(file);
            Assert.assertEquals(notExpandedContentsString.replace("Rev", "Rev: " + committedRevision + " "), fileContentsString);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "SvnKeywordsTest";
    }
}
