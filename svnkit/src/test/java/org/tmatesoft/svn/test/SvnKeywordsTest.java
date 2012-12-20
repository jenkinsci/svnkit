package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCat;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.admin.SvnRepositoryCreate;

import java.io.ByteArrayOutputStream;
import java.io.File;

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

    @Test
    public void testRepositoryUrlContainsSpace() throws Exception {
        //SVNKIT-284
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRepositoryUrlContainsSpace", options);
        try {
            //prepare a repository with a space in the URL
            final File repositoryDirectory = sandbox.createDirectory("svn.repo with space");

            final SvnRepositoryCreate repositoryCreate = svnOperationFactory.createRepositoryCreate();
            repositoryCreate.setRepositoryRoot(repositoryDirectory);
            final SVNURL url = repositoryCreate.run();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file", "$HeadURL$ $Revision$".getBytes());
            commitBuilder.setFileProperty("file", SVNProperty.KEYWORDS, SVNPropertyValue.create("HeadURL Revision"));
            final SVNCommitInfo commitInfo = commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File file = new File(workingCopyDirectory, "file");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.run();

            Assert.assertEquals("$HeadURL: " + url.appendPath("file", false).toString() + " $ $Revision: " + commitInfo.getNewRevision() + " $",
                    TestUtil.readFileContentsString(file));

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            final SvnCat cat = svnOperationFactory.createCat();
            cat.setExpandKeywords(true);
            cat.setOutput(byteArrayOutputStream);
            cat.setSingleTarget(SvnTarget.fromURL(url.appendPath("file", false)));
            cat.run();

            Assert.assertEquals("$HeadURL: " + url.appendPath("file", false).toString() + " $ $Revision: " + commitInfo.getNewRevision() + " $",
                    byteArrayOutputStream.toString());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }

    }

    private String getTestName() {
        return "SvnKeywordsTest";
    }
}
