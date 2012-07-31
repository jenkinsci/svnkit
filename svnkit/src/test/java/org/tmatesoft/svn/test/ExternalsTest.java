package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;

public class ExternalsTest {

    @Test
    public void testFileExternalDeletedTogetherWithPropertyOwner() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileExternalDeletedTogetherWithPropertyOwner", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final SVNExternal external = new SVNExternal("external", url.appendPath("file", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.addDirectory("directory");
            commitBuilder.setDirectoryProperty("directory", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File directory = new File(workingCopyDirectory, "directory");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setIgnoreExternals(false);
            checkout.run();

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(directory));
            scheduleForRemoval.run();

            final SvnCommit commit = svnOperationFactory.createCommit();
            commit.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            commit.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final WorkingCopy workingCopy = new WorkingCopy(options, workingCopyDirectory);
            try {
                assertTableIsEmpty(workingCopy, SVNWCDbSchema.EXTERNALS.name());
            } finally {
                workingCopy.dispose();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testExternalTargetChangeDoesntProduceConflictingInformation() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testExternalTargetChangeDoesntProduceConflictingInformation", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final SVNExternal external = new SVNExternal("external", url.appendPath("file", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);
            final SVNExternal anotherExternal = new SVNExternal("external", url.appendPath("anotherFile", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.addFile("anotherFile");
            commitBuilder.setFileProperty("file", "custom property", SVNPropertyValue.create("custom value"));
            commitBuilder.setDirectoryProperty("", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setIgnoreExternals(false);
            checkout.run();

            final SvnSetProperty setProperty = svnOperationFactory.createSetProperty();
            setProperty.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            setProperty.setPropertyName(SVNProperty.EXTERNALS);
            setProperty.setPropertyValue(SVNPropertyValue.create(anotherExternal.toString()));
            setProperty.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final WorkingCopy workingCopy = new WorkingCopy(options, workingCopyDirectory);
            try {
                Assert.assertEquals(1, TestUtil.getTableSize(workingCopy, SVNWCDbSchema.ACTUAL_NODE.name()));
            } finally {
                workingCopy.dispose();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void assertTableIsEmpty(WorkingCopy workingCopy, String tableName) throws SqlJetException {
        Assert.assertEquals(0, TestUtil.getTableSize(workingCopy, tableName));
    }

    private String getTestName() {
        return "ExternalsTest";
    }
}
