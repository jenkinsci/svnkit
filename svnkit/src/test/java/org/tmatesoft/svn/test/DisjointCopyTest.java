package org.tmatesoft.svn.test;

import java.io.File;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class DisjointCopyTest {

    @Test
    public void testBasics() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testBasics", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source/directory/sourceFile");
            commitBuilder.addFile("target/targetFile");
            commitBuilder.commit();

            final SVNURL sourceUrl = url.appendPath("source", false);
            final SVNURL targetUrl = url.appendPath("target", false);

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(sourceUrl, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File anotherDirectory = new File(workingCopyDirectory, "anotherDirectory");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(targetUrl));
            checkout.setSingleTarget(SvnTarget.fromFile(anotherDirectory));
            checkout.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setSingleTarget(SvnTarget.fromFile(anotherDirectory));
            copy.setDisjoint(true);
            copy.run();

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(anotherDirectory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(new File(anotherDirectory, "targetFile")).getNodeStatus());
            Assert.assertEquals(targetUrl, statuses.get(anotherDirectory).getCopyFromUrl());

            final long committedRevision = workingCopy.commit("");
            Assert.assertEquals(2, committedRevision);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testLocalModificationsInNestedWc() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLocalModificationsInNestedWc", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source/directory/sourceFile");
            commitBuilder.addFile("target/deletedFile");
            commitBuilder.commit();

            final SVNURL sourceUrl = url.appendPath("source", false);
            final SVNURL targetUrl = url.appendPath("target", false);

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(sourceUrl, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File anotherDirectory = new File(workingCopyDirectory, "anotherDirectory");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(targetUrl));
            checkout.setSingleTarget(SvnTarget.fromFile(anotherDirectory));
            checkout.run();

            final File deletedFile = new File(anotherDirectory, "deletedFile");
            final File addedFile = new File(anotherDirectory, "addedFile");

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(deletedFile));
            scheduleForRemoval.run();

            //noinspection ResultOfMethodCallIgnored
            addedFile.createNewFile();
            final SvnScheduleForAddition scheduleForAddition = svnOperationFactory.createScheduleForAddition();
            scheduleForAddition.setSingleTarget(SvnTarget.fromFile(addedFile));
            scheduleForAddition.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setSingleTarget(SvnTarget.fromFile(anotherDirectory));
            copy.setDisjoint(true);
            copy.run();

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(anotherDirectory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(deletedFile).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(addedFile).getNodeStatus());

            Assert.assertEquals(targetUrl, statuses.get(anotherDirectory).getCopyFromUrl());
            Assert.assertNull(statuses.get(deletedFile).getCopyFromUrl());
            Assert.assertNull(statuses.get(addedFile).getCopyFromUrl());

            final long committedRevision = workingCopy.commit("");
            Assert.assertEquals(2, committedRevision);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDisjointCopyToAlreadyVersionedDirectoryFails() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDisjointCopyToAlreadyVersionedDirectoryFails", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("source/alreadyVersioned");
            commitBuilder.addFile("source/directory/sourceFile");
            commitBuilder.addFile("target/targetFile");
            commitBuilder.commit();

            final SVNURL sourceUrl = url.appendPath("source", false);
            final SVNURL targetUrl = url.appendPath("target", false);

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(sourceUrl, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File alreadyVersionedDirectory = new File(workingCopyDirectory, "alreadyVersioned");

            final WorkingCopy anotherWorkingCopy = sandbox.checkoutNewWorkingCopy(targetUrl, SVNRevision.HEAD.getNumber());
            final File anotherWorkingCopyDirectory = anotherWorkingCopy.getWorkingCopyDirectory();
            SVNFileUtil.deleteAll(alreadyVersionedDirectory, true);
            SVNFileUtil.rename(anotherWorkingCopyDirectory, alreadyVersionedDirectory);

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setSingleTarget(SvnTarget.fromFile(alreadyVersionedDirectory));
            copy.setDisjoint(true);
            try {
                copy.run();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                Assert.assertEquals(SVNErrorCode.ENTRY_EXISTS, e.getErrorMessage().getErrorCode());
                //expected
            }

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(alreadyVersionedDirectory).getNodeStatus());
            Assert.assertEquals(
                    TestUtil.isNewWorkingCopyTest() ? SVNStatusType.STATUS_UNVERSIONED : SVNStatusType.STATUS_NORMAL,
                    statuses.get(new File(alreadyVersionedDirectory, "targetFile")).getNodeStatus());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDisjointCopyBetweenWorkingCopiesFails() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDisjointCopyBetweenWorkingCopiesFails", options);
        try {
            final SVNURL sourceUrl = sandbox.createSvnRepository();
            final SVNURL targetUrl = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(sourceUrl);
            commitBuilder1.addFile("directory/sourceFile");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(targetUrl);
            commitBuilder2.addFile("targetFile");
            commitBuilder2.commit();


            final WorkingCopy sourceWorkingCopy = sandbox.checkoutNewWorkingCopy(sourceUrl, SVNRevision.HEAD.getNumber());
            final WorkingCopy targetWorkingCopy = sandbox.checkoutNewWorkingCopy(targetUrl, SVNRevision.HEAD.getNumber());

            final File sourceWorkingCopyDirectory = sourceWorkingCopy.getWorkingCopyDirectory();
            final File targetWorkingCopyDirectory = targetWorkingCopy.getWorkingCopyDirectory();


            final File nestedWorkingCopyDirectory = new File(sourceWorkingCopyDirectory, "nested");
            SVNFileUtil.rename(targetWorkingCopyDirectory, nestedWorkingCopyDirectory);

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setSingleTarget(SvnTarget.fromFile(nestedWorkingCopyDirectory));
            copy.setDisjoint(true);
            try {
                copy.run();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                Assert.assertEquals(SVNErrorCode.WC_INVALID_SCHEDULE, e.getErrorMessage().getErrorCode());
                //expected
            }

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, sourceWorkingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(nestedWorkingCopyDirectory).getNodeStatus());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }


    @Test
    public void testDisjointCopyToChildFails() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDisjointCopyToChildFails", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source/directory/sourceFile");
            commitBuilder.addFile("targetFile");
            commitBuilder.commit();

            final SVNURL sourceUrl = url.appendPath("source", false);
            final SVNURL targetUrl = url;

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(sourceUrl, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File anotherDirectory = new File(workingCopyDirectory, "anotherDirectory");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(targetUrl));
            checkout.setSingleTarget(SvnTarget.fromFile(anotherDirectory));
            checkout.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setSingleTarget(SvnTarget.fromFile(anotherDirectory));
            copy.setDisjoint(true);
            try {
                copy.run();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                Assert.assertEquals(SVNErrorCode.UNSUPPORTED_FEATURE, e.getErrorMessage().getErrorCode());
                Assert.assertTrue(e.getMessage().contains("into its own child"));
                //expected
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "DisjointCopyTest";
    }
}
