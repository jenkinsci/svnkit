package org.tmatesoft.svn.test;

import junit.framework.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnSetProperty;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

public class SetPropertyTest {

    @Ignore("SVNKIT-230, currently fails")
    @Test
    public void testSetEolStyleAndMimeType() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSetEolStyleAndMimeType", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");

            final SvnSetProperty setEolStyle = svnOperationFactory.createSetProperty();
            setEolStyle.setSingleTarget(SvnTarget.fromFile(file));
            setEolStyle.setPropertyName(SVNProperty.EOL_STYLE);
            setEolStyle.setPropertyValue(SVNPropertyValue.create(SVNProperty.EOL_STYLE_LF));
            setEolStyle.run();

            final SvnSetProperty setMimeType = svnOperationFactory.createSetProperty();
            setMimeType.setSingleTarget(SvnTarget.fromFile(file));
            setMimeType.setPropertyName(SVNProperty.MIME_TYPE);
            setMimeType.setPropertyValue(SVNPropertyValue.create("application/xml"));
            setMimeType.run();

            final SvnCommit commit = svnOperationFactory.createCommit();
            commit.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            final SVNCommitInfo commitInfo = commit.run();

            Assert.assertEquals(2, commitInfo.getNewRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Ignore("SVNKIT-230, currently fails")
    @Test
    public void testSetMimeTypeAndEolStyle() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSetEolStyleAndMimeType", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");

            final SvnSetProperty setMimeType = svnOperationFactory.createSetProperty();
            setMimeType.setSingleTarget(SvnTarget.fromFile(file));
            setMimeType.setPropertyName(SVNProperty.MIME_TYPE);
            setMimeType.setPropertyValue(SVNPropertyValue.create("application/xml"));
            setMimeType.run();

            final SvnSetProperty setEolStyle = svnOperationFactory.createSetProperty();
            setEolStyle.setSingleTarget(SvnTarget.fromFile(file));
            setEolStyle.setPropertyName(SVNProperty.EOL_STYLE);
            setEolStyle.setPropertyValue(SVNPropertyValue.create(SVNProperty.EOL_STYLE_LF));
            setEolStyle.run();

            final SvnCommit commit = svnOperationFactory.createCommit();
            commit.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            final SVNCommitInfo commitInfo = commit.run();

            Assert.assertEquals(2, commitInfo.getNewRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "SetPropertyTest";
    }
}