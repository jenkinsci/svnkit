package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            runResolve(svnOperationFactory, file, SVNConflictChoice.MERGED);

            Assert.assertEquals("mine", TestUtil.readFileContentsString(file));

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertFalse(statuses.get(file).isConflicted());

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

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            update.run();

            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromFile(file));
            final SvnInfo svnInfo = getInfo.run();

            final Collection<SVNConflictDescription> conflicts = svnInfo.getWcInfo().getConflicts();
            Assert.assertEquals(1, conflicts.size());

            final SVNTreeConflictDescription conflict = (SVNTreeConflictDescription)conflicts.iterator().next();
            Assert.assertEquals(SVNConflictAction.DELETE, conflict.getConflictAction());
            Assert.assertEquals(SVNConflictReason.DELETED, conflict.getConflictReason());
            Assert.assertEquals(url, conflict.getSourceLeftVersion().getRepositoryRoot());
            Assert.assertEquals(url, conflict.getSourceRightVersion().getRepositoryRoot());

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
