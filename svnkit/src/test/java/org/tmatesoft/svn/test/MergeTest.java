package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.util.*;

public class MergeTest {

    @Ignore("Temporarily ignored")
    @Test
    public void testConflictResolution() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testConflictResolution", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "base".getBytes());
            SVNCommitInfo commitInfo1 = commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "theirs".getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, commitInfo1.getNewRevision());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");
            TestUtil.writeFileContentsString(file, "mine");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            runResolve(svnOperationFactory, file, SVNConflictChoice.MINE_CONFLICT);

            final String fileContentsString = TestUtil.readFileContentsString(file);
            Assert.assertEquals("mine", fileContentsString);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteAddOverUnversionedFileConflictResolution() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteAddOverUnversionedFileConflictResolution", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.addFile("file", "their".getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = workingCopy.getFile("file");

            SVNFileUtil.ensureDirectoryExists(file.getParentFile());
            TestUtil.writeFileContentsString(file, "mine");

            boolean isExceptionExpected = !TestUtil.isNewWorkingCopyTest();

            if (isExceptionExpected) {
                final SvnUpdate update = svnOperationFactory.createUpdate();
                update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
                try {
                    update.run();
                    Assert.fail("An exception should be thrown");
                } catch (SVNException e) {
                    //expected for WC 1.6
                    Assert.assertEquals(SVNErrorCode.WC_OBSTRUCTED_UPDATE, e.getErrorMessage().getErrorCode());
                }
            } else {
                final SvnUpdate update = svnOperationFactory.createUpdate();
                update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
                update.run();

                runResolve(svnOperationFactory, file, SVNConflictChoice.MERGED);

                Assert.assertEquals("mine", TestUtil.readFileContentsString(file));

                final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
                Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
                Assert.assertFalse(statuses.get(file).isConflicted());
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testBothDeletedTreeConflict() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testBothDeletedTreeConflict", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("file");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File file = workingCopy.getFile("file");

            workingCopy.delete(file);

            update(svnOperationFactory, workingCopy);

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromFile(file));
            final SvnInfo svnInfo = getInfo.run();

            final Collection<SVNConflictDescription> conflicts = svnInfo.getWcInfo().getConflicts();
            Assert.assertEquals(1, conflicts.size());

            final SVNTreeConflictDescription conflict = (SVNTreeConflictDescription) conflicts.iterator().next();
            Assert.assertEquals(SVNConflictAction.DELETE, conflict.getConflictAction());
            Assert.assertEquals(SVNConflictReason.DELETED, conflict.getConflictReason());
            Assert.assertEquals(url, conflict.getSourceLeftVersion().getRepositoryRoot());
            Assert.assertEquals(url, conflict.getSourceRightVersion().getRepositoryRoot());

            final SvnSchedule schedule = svnInfo.getWcInfo().getSchedule();
            Assert.assertEquals(SvnSchedule.NORMAL, schedule);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            final SvnStatus fileStatus = statuses.get(file);

            Assert.assertTrue(fileStatus.isConflicted());
            Assert.assertEquals(SVNStatusType.STATUS_CONFLICTED, fileStatus.getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateDeletedLockedFileDoesntCauseTreeConflictDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateDeletedLockedFileDoesntCauseTreeConflictDavAccess", options);
        try {
            final BasicAuthenticationManager authenticationManager = new BasicAuthenticationManager("user", "password");
            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user", "password");
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File file = new File(workingCopyDirectory, "directory/file");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(file));
            scheduleForRemoval.run();

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertNotNull(statuses.get(file).getLock());

            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus oldStatus = SvnCodec.status(context, statuses.get(file));
                Assert.assertNull(oldStatus.getTreeConflict());
            } finally {
                context.close();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateDeletedLockedFileDoesntCauseTreeConflictSvnAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllSvnserveOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateDeletedLockedFileDoesntCauseTreeConflictSvnAccess", options);
        try {
            final BasicAuthenticationManager authenticationManager = new BasicAuthenticationManager("user", "password");
            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user", "password");
            final SVNURL url = sandbox.createSvnRepositoryWithSvnAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File file = new File(workingCopyDirectory, "directory/file");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(file));
            scheduleForRemoval.run();

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertNotNull(statuses.get(file).getLock());

            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus oldStatus = SvnCodec.status(context, statuses.get(file));
                Assert.assertNull(oldStatus.getTreeConflict());
            } finally {
                context.close();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateOnRenamedParentOfLockedFileDoesntCauseTreeConflictDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateOnRenamedParentOfLockedFileDoesntCauseTreeConflictDavAccess", options);
        try {
            final BasicAuthenticationManager authenticationManager = new BasicAuthenticationManager("user", "password");
            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user", "password");
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File file = new File(workingCopyDirectory, "directory/file");
            final File directory = new File(workingCopyDirectory, "directory");
            final File renamedDirectory = new File(workingCopyDirectory, "renamedDirectory");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(file));
            scheduleForRemoval.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setMove(true);
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(directory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(renamedDirectory));
            copy.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(directory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertNotNull(statuses.get(file).getLock());

            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus oldDirectoryStatus = SvnCodec.status(context, statuses.get(directory));
                Assert.assertNull(oldDirectoryStatus.getTreeConflict());
                final SVNStatus oldFileStatus = SvnCodec.status(context, statuses.get(file));
                Assert.assertNull(oldFileStatus.getTreeConflict());
            } finally {
                context.close();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateOnRenamedParentOfLockedFileDoesntCauseTreeConflictSvnAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllSvnserveOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateOnRenamedParentOfLockedFileDoesntCauseTreeConflictSvnAccess", options);
        try {
            final BasicAuthenticationManager authenticationManager = new BasicAuthenticationManager("user", "password");
            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user", "password");
            final SVNURL url = sandbox.createSvnRepositoryWithSvnAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File file = new File(workingCopyDirectory, "directory/file");
            final File directory = new File(workingCopyDirectory, "directory");
            final File renamedDirectory = new File(workingCopyDirectory, "renamedDirectory");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(file));
            scheduleForRemoval.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setMove(true);
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(directory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(renamedDirectory));
            copy.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(directory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertNotNull(statuses.get(file).getLock());

            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus oldDirectoryStatus = SvnCodec.status(context, statuses.get(directory));
                Assert.assertNull(oldDirectoryStatus.getTreeConflict());
                final SVNStatus oldFileStatus = SvnCodec.status(context, statuses.get(file));
                Assert.assertNull(oldFileStatus.getTreeConflict());
            } finally {
                context.close();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateOnRenamedParentOfLockedFileAnotherFileDeletedDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateOnRenamedParentOfLockedFileAnotherFileDeletedDavAccess", options);
        try {
            final BasicAuthenticationManager authenticationManager = new BasicAuthenticationManager("user", "password");
            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user", "password");
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.setAuthenticationManager(authenticationManager);
            commitBuilder1.addFile("directory/file");
            commitBuilder1.addFile("directory/fileToDelete");
            commitBuilder1.addFile("fileToDelete");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setAuthenticationManager(authenticationManager);
            commitBuilder2.delete("directory/fileToDelete");
            commitBuilder2.delete("fileToDelete");
            commitBuilder2.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File file = new File(workingCopyDirectory, "directory/file");
            final File directory = new File(workingCopyDirectory, "directory");
            final File renamedDirectory = new File(workingCopyDirectory, "renamedDirectory");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(file));
            scheduleForRemoval.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setMove(true);
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(directory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(renamedDirectory));
            copy.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(directory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertNotNull(statuses.get(file).getLock());

            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus oldDirectoryStatus = SvnCodec.status(context, statuses.get(directory));
                Assert.assertNull(oldDirectoryStatus.getTreeConflict());
                final SVNStatus oldFileStatus = SvnCodec.status(context, statuses.get(file));
                Assert.assertNull(oldFileStatus.getTreeConflict());
            } finally {
                context.close();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateOnRenamedParentOfLockedFileAnotherFileDeletedSvnAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllSvnserveOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateOnRenamedParentOfLockedFileAnotherFileDeletedSvnAccess", options);
        try {
            final BasicAuthenticationManager authenticationManager = new BasicAuthenticationManager("user", "password");
            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user", "password");
            final SVNURL url = sandbox.createSvnRepositoryWithSvnAccess(loginToPassword);

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.setAuthenticationManager(authenticationManager);
            commitBuilder1.addFile("directory/file");
            commitBuilder1.addFile("directory/fileToDelete");
            commitBuilder1.addFile("fileToDelete");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setAuthenticationManager(authenticationManager);
            commitBuilder2.delete("directory/fileToDelete");
            commitBuilder2.delete("fileToDelete");
            commitBuilder2.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File file = new File(workingCopyDirectory, "directory/file");
            final File directory = new File(workingCopyDirectory, "directory");
            final File renamedDirectory = new File(workingCopyDirectory, "renamedDirectory");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(file));
            scheduleForRemoval.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setMove(true);
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(directory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(renamedDirectory));
            copy.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(directory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertNotNull(statuses.get(file).getLock());

            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus oldDirectoryStatus = SvnCodec.status(context, statuses.get(directory));
                Assert.assertNull(oldDirectoryStatus.getTreeConflict());
                final SVNStatus oldFileStatus = SvnCodec.status(context, statuses.get(file));
                Assert.assertNull(oldFileStatus.getTreeConflict());
            } finally {
                context.close();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateOnMovedParentOfLockedFileDoesntCauseTreeConflictDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateOnRenamedParentOfLockedFileDoesntCauseTreeConflictDavAccess", options);
        try {
            final BasicAuthenticationManager authenticationManager = new BasicAuthenticationManager("user", "password");
            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user", "password");
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager);
            commitBuilder.addFile("directory/subdirectory/file");
            commitBuilder.addDirectory("anotherDirectory");
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File directory = new File(workingCopyDirectory, "directory");
            final File subdirectory = new File(directory, "subdirectory");
            final File file = new File(subdirectory, "file");
            final File anotherDirectory = new File(workingCopyDirectory, "anotherDirectory");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(file));
            scheduleForRemoval.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setFailWhenDstExists(false);
            copy.setMove(true);
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(subdirectory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(anotherDirectory));
            copy.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(subdirectory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertNotNull(statuses.get(file).getLock());

            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus oldDirectoryStatus = SvnCodec.status(context, statuses.get(subdirectory));
                Assert.assertNull(oldDirectoryStatus.getTreeConflict());
                final SVNStatus oldFileStatus = SvnCodec.status(context, statuses.get(file));
                Assert.assertNull(oldFileStatus.getTreeConflict());
            } finally {
                context.close();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUpdateOnMovedParentOfLockedFileDoesntCauseTreeConflictSvnAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllSvnserveOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUpdateOnRenamedParentOfLockedFileDoesntCauseTreeConflictSvnAccess", options);
        try {
            final BasicAuthenticationManager authenticationManager = new BasicAuthenticationManager("user", "password");
            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user", "password");
            final SVNURL url = sandbox.createSvnRepositoryWithSvnAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager);
            commitBuilder.addFile("directory/subdirectory/file");
            commitBuilder.addDirectory("anotherDirectory");
            commitBuilder.commit();

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File directory = new File(workingCopyDirectory, "directory");
            final File subdirectory = new File(directory, "subdirectory");
            final File file = new File(subdirectory, "file");
            final File anotherDirectory = new File(workingCopyDirectory, "anotherDirectory");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.run();

            final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
            scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(file));
            scheduleForRemoval.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setFailWhenDstExists(false);
            copy.setMove(true);
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(subdirectory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(anotherDirectory));
            copy.run();

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(subdirectory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertNotNull(statuses.get(file).getLock());

            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SVNStatus oldDirectoryStatus = SvnCodec.status(context, statuses.get(subdirectory));
                Assert.assertNull(oldDirectoryStatus.getTreeConflict());
                final SVNStatus oldFileStatus = SvnCodec.status(context, statuses.get(file));
                Assert.assertNull(oldFileStatus.getTreeConflict());
            } finally {
                context.close();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }


    @Test
    public void testMergeFileAdditionAndDeletion() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMergeFileAdditionAndDeletion", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory1/directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.addDirectoryByCopying("directory2", "directory1");
            commitBuilder2.commit();

            final CommitBuilder commitBuilder3 = new CommitBuilder(url);
            commitBuilder3.addFile("directory2/directory/file", "contents".getBytes());
            commitBuilder3.commit();

            final CommitBuilder commitBuilder4 = new CommitBuilder(url);
            commitBuilder4.delete("directory2/directory/file");
            commitBuilder4.commit();

            final SVNURL directory1Url = url.appendPath("directory1", false);
            final SVNURL directory2Url = url.appendPath("directory2", false);

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(directory1Url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final SvnMerge merge = svnOperationFactory.createMerge();
            merge.addRevisionRange(SvnRevisionRange.create(SVNRevision.create(2), SVNRevision.create(3)));
            merge.addRevisionRange(SvnRevisionRange.create(SVNRevision.create(3), SVNRevision.create(4)));
            merge.setSource(SvnTarget.fromURL(directory2Url), false);
            merge.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            merge.run();

            Assert.assertFalse(workingCopy.getFile("directory/file").exists());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testConflictOnFileExternalUpdate() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testConflictOnFileExternalUpdate", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final SVNExternal external = new SVNExternal("file", url.appendPath("file", false).toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.addFile("file");

            commitBuilder1.setDirectoryProperty("directory", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder1.commit();

            final SVNURL directoryUrl = url.appendPath("directory", false);

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File file = new File(workingCopyDirectory, "file");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.setSource(SvnTarget.fromURL(directoryUrl));
            checkout.setIgnoreExternals(false);
            checkout.run();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "theirs".getBytes());
            commitBuilder2.commit();

            TestUtil.writeFileContentsString(file, "mine");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final Set<String> expectedNames = new HashSet<String>();
            expectedNames.add("file");
            expectedNames.add("file.r1");
            expectedNames.add("file.r2");
            expectedNames.add("file.mine");

            final Set<String> actualNames = new HashSet<String>();
            final File[] files = SVNFileListUtil.listFiles(workingCopyDirectory);
            for (File child : files) {
                String name = SVNFileUtil.getFileName(child);
                if (SVNFileUtil.getAdminDirectoryName().equals(name)) {
                    continue;
                }
                actualNames.add(name);
            }

            Assert.assertEquals(expectedNames, actualNames);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMergeRecordsActualPropertiesForFileWithMergeInfo() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMergeRecordsActualPropertiesForFileWithMergeInfo", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file");
            commitBuilder1.setFileProperty("directory/file", SVNProperty.MERGE_INFO, SVNPropertyValue.create("/somefile:1"));
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.addFileByCopying("directory/copiedFile", "directory/file");
            commitBuilder2.delete("directory/file");
            commitBuilder2.commit();

            final CommitBuilder commitBuilder3 = new CommitBuilder(url);
            commitBuilder3.addDirectoryByCopying("copiedDirectory", "directory", 1);
            commitBuilder3.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url.appendPath("copiedDirectory", false));
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final SvnMerge merge = svnOperationFactory.createMerge();
            merge.addRevisionRange(SvnRevisionRange.create(SVNRevision.create(1), SVNRevision.create(2)));
            merge.setSource(SvnTarget.fromURL(url.appendPath("directory", false)), false);
            merge.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            merge.run();

            Assert.assertEquals(2, TestUtil.getTableSize(workingCopy, SVNWCDbSchema.ACTUAL_NODE.name()));

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testImplicitGapIsExcludedFromMerging() throws Exception {
        //SVNKIT-305
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testImplicitGapIsExcludedFromMerging", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("trunk/file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("trunk/file");
            commitBuilder2.commit();

            final CommitBuilder commitBuilder3 = new CommitBuilder(url);
            commitBuilder3.addDirectoryByCopying("branches/branch", "trunk", 1);
            commitBuilder3.commit();

            final CommitBuilder commitBuilder4 = new CommitBuilder(url); //implicit src gap
            commitBuilder4.commit();

            final CommitBuilder commitBuilder5 = new CommitBuilder(url);
            commitBuilder5.delete("branches/branch");
            commitBuilder5.commit();

            final CommitBuilder commitBuilder6 = new CommitBuilder(url);
            commitBuilder6.addDirectoryByCopying("branches/branch", "trunk", 3);
            commitBuilder6.commit();

            final CommitBuilder commitBuilder7 = new CommitBuilder(url);
            commitBuilder7.addFileByCopying("trunk/file", "trunk/file", 1);
            commitBuilder7.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url.appendPath("trunk", false));

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNDiffClient diffClient = clientManager.getDiffClient();

                workingCopy.setProperty(workingCopy.getFile("file"), SVNProperty.MERGE_INFO, SVNPropertyValue.create("/branches/branch/file:3")); //the value doesn't matter
                workingCopy.setProperty(workingCopy.getWorkingCopyDirectory(), SVNProperty.MERGE_INFO, SVNPropertyValue.create("/branches/branch:6-7"));
                Assert.assertEquals(8, workingCopy.commit(""));
                workingCopy.updateToRevision(-1);

                diffClient.doMerge(url.appendPath("branches/branch", false), SVNRevision.HEAD, Arrays.asList(
                        new SVNRevisionRange(SVNRevision.create(1), SVNRevision.HEAD)
                ), workingCopy.getWorkingCopyDirectory(), SVNDepth.INFINITY, true, false, false, false);

                Assert.assertEquals(9, workingCopy.commit(""));

            } finally {
                clientManager.dispose();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMergeAfterShallowMergeProducesCorrectMergeinfo() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMergeAfterShallowMergeProducesCorrectMergeinfo", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/subdirectory/file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.addDirectoryByCopying("copiedDirectory", "directory");
            commitBuilder2.commit();

            final CommitBuilder commitBuilder3 = new CommitBuilder(url);
            commitBuilder3.changeFile("directory/subdirectory/file", "contents".getBytes());
            commitBuilder3.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);

            final SvnMerge shallowMerge = svnOperationFactory.createMerge();
            shallowMerge.addRevisionRange(SvnRevisionRange.create(SVNRevision.create(1), SVNRevision.HEAD));
            shallowMerge.setSource(SvnTarget.fromURL(url.appendPath("directory", false)), false);
            shallowMerge.setSingleTarget(SvnTarget.fromFile(workingCopy.getFile("copiedDirectory")));
            shallowMerge.setDepth(SVNDepth.FILES);
            shallowMerge.run();

            final SvnMerge merge = svnOperationFactory.createMerge();
            merge.addRevisionRange(SvnRevisionRange.create(SVNRevision.create(1), SVNRevision.HEAD));
            merge.setSource(SvnTarget.fromURL(url.appendPath("directory", false)), false);
            merge.setSingleTarget(SvnTarget.fromFile(workingCopy.getFile("copiedDirectory")));
            merge.setDepth(SVNDepth.INFINITY);
            merge.run();

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromFile(workingCopy.getFile("copiedDirectory")));
            final SVNProperties properties = getProperties.run();

            Assert.assertEquals("/directory:2-3", SVNPropertyValue.getPropertyAsString(properties.getSVNPropertyValue(SVNProperty.MERGE_INFO)));

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void update(SvnOperationFactory svnOperationFactory, WorkingCopy workingCopy) throws SVNException {
        final SvnUpdate update = svnOperationFactory.createUpdate();
        update.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
        update.run();
    }

    private void runResolve(SvnOperationFactory svnOperationFactory, File file, SVNConflictChoice resolution) throws SVNException {
        final SVNClientManager clientManager = SVNClientManager.newInstance(svnOperationFactory.getOptions(), svnOperationFactory.getRepositoryPool());
        try {
            final SVNWCClient wcClient = clientManager.getWCClient();
            wcClient.doResolve(file, SVNDepth.INFINITY, resolution);
        } finally {
            clientManager.dispose();
        }
    }

    private String getTestName() {
        return "MergeTest";
    }
}
