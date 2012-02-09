package org.tmatesoft.svn.test;

import java.io.File;
import java.util.Map;

import junit.framework.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class VirtualCopyTest {

    @Test
    public void testFileMovedVirtuallyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileMovedVirtuallyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String fileNotToAdd = "directory3/fileNotToAdd.txt"; //if makeParents for unversionedFile calls "svn add" with infinite depth, this file will be added (that is not expected)

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcFileNotToAdd = new File(workingCopyDirectory, fileNotToAdd);

            SVNFileUtil.ensureDirectoryExists(wcUnversionedFile.getParentFile());
            add(svnOperationFactory, wcUnversionedFile.getParentFile());

            SVNFileUtil.rename(wcVersionedFile, wcUnversionedFile);

            moveVirtual(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NONE, wcFileNotToAdd, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testFileMovedVirtuallyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileMovedVirtuallyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String fileNotToAdd = "directory3/fileNotToAdd.txt"; //if makeParents for unversionedFile calls "svn add" with infinite depth, this file will be added (that is not expected)

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcFileNotToAdd = new File(workingCopyDirectory, fileNotToAdd);

            SVNFileUtil.ensureDirectoryExists(wcUnversionedFile.getParentFile());
            add(svnOperationFactory, wcUnversionedFile.getParentFile());

            SVNFileUtil.rename(wcVersionedFile, wcUnversionedFile);

            copyVirtual(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_MISSING, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NONE, wcFileNotToAdd, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testFileCopiedVirtuallyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileCopiedVirtuallyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String fileNotToAdd = "directory3/fileNotToAdd.txt"; //if makeParents for unversionedFile calls "svn add" with infinite depth, this file will be added (that is not expected)

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcFileNotToAdd = new File(workingCopyDirectory, fileNotToAdd);

            SVNFileUtil.ensureDirectoryExists(wcUnversionedFile.getParentFile());
            add(svnOperationFactory, wcUnversionedFile.getParentFile());

            SVNFileUtil.copyFile(wcVersionedFile, wcUnversionedFile, false);

            copyVirtual(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NONE, wcFileNotToAdd, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testFileCopiedVirtualMoveFailed() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileCopiedVirtualMoveFailed", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcUnversionedFile.getParentFile());
            add(svnOperationFactory, wcUnversionedFile.getParentFile());

            SVNFileUtil.copyFile(wcVersionedFile, wcUnversionedFile, false);

            Assert.assertTrue(wcUnversionedFile.exists());

            try {
                moveVirtual(svnOperationFactory, wcVersionedFile, wcUnversionedFile);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.ENTRY_EXISTS, e.getErrorMessage().getErrorCode());
                //expected
            }

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);
            assertStatus(SVNStatusType.STATUS_UNVERSIONED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testFileUnversionedToVersionedVirutalMoveFailed() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileUnversionedToVersionedVirutalMoveFailed", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);

            try {
                moveVirtual(svnOperationFactory, wcUnversionedFile, wcVersionedFile);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.ENTRY_ATTRIBUTE_INVALID, e.getErrorMessage().getErrorCode());
                //expected
            }
            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile.getParentFile(), statuses);
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testFileUnversionedToUnversionedVirutalMoveFailed() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileUnversionedToUnversionedVirutalMoveFailed", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());
            SVNFileUtil.ensureDirectoryExists(wcUnversionedFile.getParentFile());

            add(svnOperationFactory, wcAnotherUnversionedFile.getParentFile());
            add(svnOperationFactory, wcUnversionedFile.getParentFile());

            Assert.assertTrue(wcAnotherUnversionedFile.createNewFile());

            try {
                moveVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.ENTRY_NOT_FOUND, e.getErrorMessage().getErrorCode());
                //expected
            }
            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);
            assertStatus(SVNStatusType.STATUS_UNVERSIONED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testCopyingAlreadyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopyingAlreadyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());
            add(svnOperationFactory, wcAnotherUnversionedFile.getParentFile());

            SVNFileUtil.ensureDirectoryExists(wcUnversionedFile.getParentFile());
            add(svnOperationFactory, wcUnversionedFile.getParentFile());

            copy(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.copyFile(wcUnversionedFile, wcAnotherUnversionedFile, false);

            copyVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcUnversionedFile).getCopyFromUrl());
            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testMovingAlreadyCopied() throws Exception {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMovingAlreadyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());
            add(svnOperationFactory, wcAnotherUnversionedFile.getParentFile());

            SVNFileUtil.ensureDirectoryExists(wcUnversionedFile.getParentFile());
            add(svnOperationFactory, wcUnversionedFile.getParentFile());

            copy(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.rename(wcUnversionedFile, wcAnotherUnversionedFile);

            moveVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testCopyingAlreadyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopyingAlreadyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());
            add(svnOperationFactory, wcAnotherUnversionedFile.getParentFile());

            SVNFileUtil.ensureDirectoryExists(wcUnversionedFile.getParentFile());
            add(svnOperationFactory, wcUnversionedFile.getParentFile());

            move(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.copyFile(wcUnversionedFile, wcAnotherUnversionedFile, false);

            copyVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcUnversionedFile).getCopyFromUrl());
            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testMovingAlreadyMoved() throws Exception {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMovingAlreadyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());
            add(svnOperationFactory, wcAnotherUnversionedFile.getParentFile());

            SVNFileUtil.ensureDirectoryExists(wcUnversionedFile.getParentFile());
            add(svnOperationFactory, wcUnversionedFile.getParentFile());

            move(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.rename(wcUnversionedFile, wcAnotherUnversionedFile);

            moveVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testCopyingWronglyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopyingWronglyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());
            add(svnOperationFactory, wcAnotherUnversionedFile.getParentFile());

            wronglyCopy(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.copyFile(wcUnversionedFile, wcAnotherUnversionedFile, false);

            copyVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertNull(statuses.get(wcUnversionedFile).getCopyFromUrl());
            Assert.assertNull(statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testMovingWronglyCopied() throws Exception {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMovingWronglyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());
            add(svnOperationFactory, wcAnotherUnversionedFile.getParentFile());

            wronglyCopy(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.rename(wcUnversionedFile, wcAnotherUnversionedFile);

            moveVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertNull(statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testCopyingWronglyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopyingWronglyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());
            add(svnOperationFactory, wcAnotherUnversionedFile.getParentFile());

            wronglyMove(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.copyFile(wcUnversionedFile, wcAnotherUnversionedFile, false);

            copyVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertNull(statuses.get(wcUnversionedFile).getCopyFromUrl());
            Assert.assertNull(statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testMovingWronglyMoved() throws Exception {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMovingWronglyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String anotherUnversionedFile = "directory4/anotherUnversionedFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcAnotherUnversionedFile = new File(workingCopyDirectory, anotherUnversionedFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherUnversionedFile.getParentFile());
            add(svnOperationFactory, wcAnotherUnversionedFile.getParentFile());

            wronglyMove(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            SVNFileUtil.rename(wcUnversionedFile, wcAnotherUnversionedFile);

            moveVirtual(svnOperationFactory, wcUnversionedFile, wcAnotherUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NONE, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherUnversionedFile.getParentFile(), statuses);

            Assert.assertNull(statuses.get(wcAnotherUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testDirectoryCopiedFileVirtuallyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDirectoryCopiedFileVirtuallyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String targetFile = "directory3/versionedFile.txt";
            final String anotherTargetFile = "directory4/anotherTargetFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcTargetFile = new File(workingCopyDirectory, targetFile);
            final File wcAnotherTargetFile = new File(workingCopyDirectory, anotherTargetFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherTargetFile.getParentFile());
            add(svnOperationFactory, wcAnotherTargetFile.getParentFile());

            copy(svnOperationFactory, wcVersionedFile.getParentFile(), wcTargetFile.getParentFile());

            SVNFileUtil.copyFile(wcTargetFile, wcAnotherTargetFile, false);

            copyVirtual(svnOperationFactory, wcTargetFile, wcAnotherTargetFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NORMAL, wcTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcTargetFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherTargetFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testDirectoryMovedFileVirtuallyCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDirectoryMovedFileVirtuallyCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String targetFile = "directory3/versionedFile.txt";
            final String anotherTargetFile = "directory4/anotherTargetFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcTargetFile = new File(workingCopyDirectory, targetFile);
            final File wcAnotherTargetFile = new File(workingCopyDirectory, anotherTargetFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherTargetFile.getParentFile());
            add(svnOperationFactory, wcAnotherTargetFile.getParentFile());

            move(svnOperationFactory, wcVersionedFile.getParentFile(), wcTargetFile.getParentFile());

            SVNFileUtil.copyFile(wcTargetFile, wcAnotherTargetFile, false);

            copyVirtual(svnOperationFactory, wcTargetFile, wcAnotherTargetFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_NORMAL, wcTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcTargetFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherTargetFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testDirectoryCopiedFileVirtuallyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDirectoryCopiedFileVirtuallyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String targetFile = "directory3/versionedFile.txt";
            final String anotherTargetFile = "directory4/anotherTargetFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcTargetFile = new File(workingCopyDirectory, targetFile);
            final File wcAnotherTargetFile = new File(workingCopyDirectory, anotherTargetFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherTargetFile.getParentFile());
            add(svnOperationFactory, wcAnotherTargetFile.getParentFile());

            copy(svnOperationFactory, wcVersionedFile.getParentFile(), wcTargetFile.getParentFile());

            SVNFileUtil.rename(wcTargetFile, wcAnotherTargetFile);

            moveVirtual(svnOperationFactory, wcTargetFile, wcAnotherTargetFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_DELETED, wcTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcTargetFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherTargetFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testDirectoryMovedFileVirtuallyMoved() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDirectoryMovedFileVirtuallyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String targetFile = "directory3/versionedFile.txt";
            final String anotherTargetFile = "directory4/anotherTargetFile.txt";

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcTargetFile = new File(workingCopyDirectory, targetFile);
            final File wcAnotherTargetFile = new File(workingCopyDirectory, anotherTargetFile);

            SVNFileUtil.ensureDirectoryExists(wcAnotherTargetFile.getParentFile());
            add(svnOperationFactory, wcAnotherTargetFile.getParentFile());

            move(svnOperationFactory, wcVersionedFile.getParentFile(), wcTargetFile.getParentFile());

            SVNFileUtil.rename(wcTargetFile, wcAnotherTargetFile);

            moveVirtual(svnOperationFactory, wcTargetFile, wcAnotherTargetFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_DELETED, wcTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcTargetFile.getParentFile(), statuses);

            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile, statuses);
            assertStatus(SVNStatusType.STATUS_ADDED, wcAnotherTargetFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcAnotherTargetFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testFileMovedVirtuallyMovedParentIsVersioned() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileMovedVirtuallyMoved", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String versionedFile = "directory1/versionedFile.txt";
            final String unversionedFile = "directory3/unversionedFile.txt";
            final String fileInSameDirectory = "directory3/fileInSameDirectory.txt"; //if makeParents for unversionedFile calls "svn add" with infinite depth, this file will be added (that is not expected)

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setCommitMessage("Added a file into repository.");
            commitBuilder.addFile(versionedFile);
            commitBuilder.addFile(fileInSameDirectory);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File wcVersionedFile = new File(workingCopyDirectory, versionedFile);
            final File wcUnversionedFile = new File(workingCopyDirectory, unversionedFile);
            final File wcFileInSameDirectory = new File(workingCopyDirectory, fileInSameDirectory);

            SVNFileUtil.rename(wcVersionedFile, wcUnversionedFile);

            moveVirtual(svnOperationFactory, wcVersionedFile, wcUnversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, wcUnversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcUnversionedFile.getParentFile(), statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, wcVersionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcFileInSameDirectory, statuses);
            assertStatus(SVNStatusType.STATUS_NORMAL, wcVersionedFile.getParentFile(), statuses);

            Assert.assertEquals(url.appendPath(versionedFile, false), statuses.get(wcUnversionedFile).getCopyFromUrl());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testVirtualCopyBetweenDifferentWorkingCopiesFailed() throws Exception {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyBetweenDifferentWorkingCopiesFailed", options);
        try {
            final SVNURL url1 = sandbox.createSvnRepository();
            final SVNURL url2 = sandbox.createSvnRepository();

            final String filePath = "directory1/file1.txt";

            //we commit only to the first repository
            final CommitBuilder commitBuilder1 = new CommitBuilder(url1);
            commitBuilder1.setCommitMessage("Added a file into repository.");
            commitBuilder1.addFile(filePath);
            commitBuilder1.commit();

            final WorkingCopy workingCopy1 = sandbox.checkoutNewWorkingCopy(url1, SVNRevision.HEAD.getNumber());
            final WorkingCopy workingCopy2 = sandbox.checkoutNewWorkingCopy(url2, SVNRevision.HEAD.getNumber());

            final File workingCopyDirectory1 = workingCopy1.getWorkingCopyDirectory();
            final File workingCopyDirectory2 = workingCopy2.getWorkingCopyDirectory();

            Assert.assertNotSame(workingCopyDirectory1, workingCopyDirectory2);

            final File file1 = new File(workingCopyDirectory1, filePath);
            final File file2 = new File(workingCopyDirectory2, filePath);

            SVNFileUtil.ensureDirectoryExists(file1.getParentFile());
            SVNFileUtil.ensureDirectoryExists(file2.getParentFile());
            add(svnOperationFactory, file2.getParentFile());

            SVNFileUtil.copyFile(file1, file2, false);

            try {
                copyVirtual(svnOperationFactory, file1, file2);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.WC_INVALID_SCHEDULE, e.getErrorMessage().getErrorCode());
                //expected
            }
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testVirtualCopyMissingToUnversioned() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyMissingToUnversioned", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("missing");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File unversionedFile = new File(workingCopyDirectory, "unversioned");
            //noinspection ResultOfMethodCallIgnored
            unversionedFile.createNewFile();

            final File missingFile = new File(workingCopyDirectory, "missing");
            SVNFileUtil.deleteFile(missingFile);

            copyVirtual(svnOperationFactory, missingFile, unversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, unversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_MISSING, missingFile, statuses);

            Assert.assertEquals(url.appendPath("missing", false), statuses.get(unversionedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualCopyDeletedToUnversioned() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyDeletedToUnversioned", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("deleted");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File unversionedFile = new File(workingCopyDirectory, "unversioned");
            //noinspection ResultOfMethodCallIgnored
            unversionedFile.createNewFile();

            final File deletedFile = new File(workingCopyDirectory, "deleted");
            workingCopy.delete(deletedFile);

            copyVirtual(svnOperationFactory, deletedFile, unversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, unversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, deletedFile, statuses);

            Assert.assertEquals(url.appendPath("deleted", false), statuses.get(unversionedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualCopyReplacedToUnversioned() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyReplacedToUnversioned", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("replaced");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File unversionedFile = new File(workingCopyDirectory, "unversioned");
            //noinspection ResultOfMethodCallIgnored
            unversionedFile.createNewFile();

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            copyVirtual(svnOperationFactory, replacedFile, unversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, unversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);

            Assert.assertEquals(url.appendPath("replaced", false), statuses.get(unversionedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualCopyMissingToAdded() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyMissingToAdded", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("missing");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File addedFile = new File(workingCopyDirectory, "added");
            //noinspection ResultOfMethodCallIgnored
            addedFile.createNewFile();
            add(svnOperationFactory, addedFile);

            final File missingFile = new File(workingCopyDirectory, "missing");
            SVNFileUtil.deleteFile(missingFile);

            copyVirtual(svnOperationFactory, missingFile, addedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, addedFile, statuses);
            assertStatus(SVNStatusType.STATUS_MISSING, missingFile, statuses);

            Assert.assertEquals(url.appendPath("missing", false), statuses.get(addedFile).getCopyFromUrl());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualCopyDeletedToAdded() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyDeletedToAdded", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("deleted");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File addedFile = new File(workingCopyDirectory, "added");
            //noinspection ResultOfMethodCallIgnored
            addedFile.createNewFile();
            add(svnOperationFactory, addedFile);

            final File deletedFile = new File(workingCopyDirectory, "deleted");
            workingCopy.delete(deletedFile);

            copyVirtual(svnOperationFactory, deletedFile, addedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, addedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, deletedFile, statuses);

            Assert.assertEquals(url.appendPath("deleted", false), statuses.get(addedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualCopyReplacedToAdded() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyReplacedToAdded", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("replaced");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File addedFile = new File(workingCopyDirectory, "added");
            //noinspection ResultOfMethodCallIgnored
            addedFile.createNewFile();
            add(svnOperationFactory, addedFile);

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            copyVirtual(svnOperationFactory, replacedFile, addedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, addedFile, statuses);
            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);

            Assert.assertEquals(url.appendPath("replaced", false), statuses.get(addedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualCopyMissingToCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyMissingToCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source");
            commitBuilder.addFile("missing");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File sourceFile = new File(workingCopyDirectory, "source");
            final File copiedFile = new File(workingCopyDirectory, "copied");
            copy(svnOperationFactory, sourceFile, copiedFile);

            final File missingFile = new File(workingCopyDirectory, "missing");
            SVNFileUtil.deleteFile(missingFile);

            copyVirtual(svnOperationFactory, missingFile, copiedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, copiedFile, statuses);
            assertStatus(SVNStatusType.STATUS_MISSING, missingFile, statuses);

            Assert.assertEquals(url.appendPath("missing", false), statuses.get(copiedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualCopyDeletedToCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyDeletedToCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source");
            commitBuilder.addFile("deleted");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File sourceFile = new File(workingCopyDirectory, "source");
            final File copiedFile = new File(workingCopyDirectory, "copied");
            copy(svnOperationFactory, sourceFile, copiedFile);

            final File deletedFile = new File(workingCopyDirectory, "deleted");
            workingCopy.delete(deletedFile);

            copyVirtual(svnOperationFactory, deletedFile, copiedFile);


            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, copiedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, deletedFile, statuses);

            Assert.assertEquals(url.appendPath("deleted", false), statuses.get(copiedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualCopyReplacedToCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyReplacedToCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source");
            commitBuilder.addFile("replaced");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File sourceFile = new File(workingCopyDirectory, "source");
            final File copiedFile = new File(workingCopyDirectory, "copied");
            copy(svnOperationFactory, sourceFile, copiedFile);

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            copyVirtual(svnOperationFactory, replacedFile, copiedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, copiedFile, statuses);
            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);

            Assert.assertEquals(url.appendPath("replaced", false), statuses.get(copiedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualCopyMissingToReplaced() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyMissingToReplaced", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("replaced");
            commitBuilder.addFile("missing");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            final File missingFile = new File(workingCopyDirectory, "missing");
            SVNFileUtil.deleteFile(missingFile);

            copyVirtual(svnOperationFactory, missingFile, replacedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);
            assertStatus(SVNStatusType.STATUS_MISSING, missingFile, statuses);

            Assert.assertEquals(url.appendPath("missing", false), statuses.get(replacedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualCopyDeletedToReplaced() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyDeletedToReplaced", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("replaced");
            commitBuilder.addFile("deleted");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            final File deletedFile = new File(workingCopyDirectory, "deleted");
            workingCopy.delete(deletedFile);

            copyVirtual(svnOperationFactory, deletedFile, replacedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, deletedFile, statuses);

            Assert.assertEquals(url.appendPath("deleted", false), statuses.get(replacedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualCopyReplacedToReplaced() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualCopyReplacedToReplaced", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("replaced");
            commitBuilder.addFile("anotherReplaced");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            final File anotherReplacedFile = new File(workingCopyDirectory, "anotherReplaced");
            workingCopy.delete(anotherReplacedFile);
            //noinspection ResultOfMethodCallIgnored
            anotherReplacedFile.createNewFile();
            workingCopy.add(anotherReplacedFile);

            copyVirtual(svnOperationFactory, anotherReplacedFile, replacedFile);


            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);
            assertStatus(SVNStatusType.STATUS_REPLACED, anotherReplacedFile, statuses);

            Assert.assertEquals(url.appendPath("anotherReplaced", false), statuses.get(replacedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }


    @Test
    public void testVirtualMoveMissingToUnversioned() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveMissingToUnversioned", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("missing");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File unversionedFile = new File(workingCopyDirectory, "unversioned");
            //noinspection ResultOfMethodCallIgnored
            unversionedFile.createNewFile();

            final File missingFile = new File(workingCopyDirectory, "missing");
            SVNFileUtil.deleteFile(missingFile);

            moveVirtual(svnOperationFactory, missingFile, unversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, unversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, missingFile, statuses);

            Assert.assertEquals(url.appendPath("missing", false), statuses.get(unversionedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualMoveDeletedToUnversioned() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveDeletedToUnversioned", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("deleted");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File unversionedFile = new File(workingCopyDirectory, "unversioned");
            //noinspection ResultOfMethodCallIgnored
            unversionedFile.createNewFile();

            final File deletedFile = new File(workingCopyDirectory, "deleted");
            workingCopy.delete(deletedFile);

            moveVirtual(svnOperationFactory, deletedFile, unversionedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, unversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, deletedFile, statuses);

            Assert.assertEquals(url.appendPath("deleted", false), statuses.get(unversionedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualMoveReplacedToUnversioned() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveReplacedToUnversioned", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("replaced");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File unversionedFile = new File(workingCopyDirectory, "unversioned");
            //noinspection ResultOfMethodCallIgnored
            unversionedFile.createNewFile();

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            try {
                moveVirtual(svnOperationFactory, replacedFile, unversionedFile);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.ENTRY_EXISTS, e.getErrorMessage().getErrorCode());
                //expected
            }

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_UNVERSIONED, unversionedFile, statuses);
            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);

            Assert.assertNull(statuses.get(unversionedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualMoveMissingToAdded() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveMissingToAdded", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("missing");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File addedFile = new File(workingCopyDirectory, "added");
            //noinspection ResultOfMethodCallIgnored
            addedFile.createNewFile();
            add(svnOperationFactory, addedFile);

            final File missingFile = new File(workingCopyDirectory, "missing");
            SVNFileUtil.deleteFile(missingFile);

            moveVirtual(svnOperationFactory, missingFile, addedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, addedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, missingFile, statuses);

            Assert.assertEquals(url.appendPath("missing", false), statuses.get(addedFile).getCopyFromUrl());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualMoveDeletedToAdded() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveDeletedToAdded", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("deleted");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File addedFile = new File(workingCopyDirectory, "added");
            //noinspection ResultOfMethodCallIgnored
            addedFile.createNewFile();
            add(svnOperationFactory, addedFile);

            final File deletedFile = new File(workingCopyDirectory, "deleted");
            workingCopy.delete(deletedFile);

            moveVirtual(svnOperationFactory, deletedFile, addedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, addedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, deletedFile, statuses);

            Assert.assertEquals(url.appendPath("deleted", false), statuses.get(addedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualMoveReplacedToAdded() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveReplacedToAdded", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("replaced");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File addedFile = new File(workingCopyDirectory, "added");
            //noinspection ResultOfMethodCallIgnored
            addedFile.createNewFile();
            add(svnOperationFactory, addedFile);

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            try {
                moveVirtual(svnOperationFactory, replacedFile, addedFile);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.ENTRY_EXISTS, e.getErrorMessage().getErrorCode());
                //expected
            }

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, addedFile, statuses);
            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);

            Assert.assertNull(statuses.get(addedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualMoveMissingToCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveMissingToCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source");
            commitBuilder.addFile("missing");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File sourceFile = new File(workingCopyDirectory, "source");
            final File copiedFile = new File(workingCopyDirectory, "copied");
            copy(svnOperationFactory, sourceFile, copiedFile);

            final File missingFile = new File(workingCopyDirectory, "missing");
            SVNFileUtil.deleteFile(missingFile);

            moveVirtual(svnOperationFactory, missingFile, copiedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, copiedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, missingFile, statuses);

            Assert.assertEquals(url.appendPath("missing", false), statuses.get(copiedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualMoveDeletedToCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveDeletedToCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source");
            commitBuilder.addFile("deleted");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File sourceFile = new File(workingCopyDirectory, "source");
            final File copiedFile = new File(workingCopyDirectory, "copied");
            copy(svnOperationFactory, sourceFile, copiedFile);

            final File deletedFile = new File(workingCopyDirectory, "deleted");
            workingCopy.delete(deletedFile);

            moveVirtual(svnOperationFactory, deletedFile, copiedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, copiedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, deletedFile, statuses);

            Assert.assertEquals(url.appendPath("deleted", false), statuses.get(copiedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualMoveReplacedToCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveReplacedToCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source");
            commitBuilder.addFile("replaced");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File sourceFile = new File(workingCopyDirectory, "source");
            final File copiedFile = new File(workingCopyDirectory, "copied");
            copy(svnOperationFactory, sourceFile, copiedFile);

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            try {
                moveVirtual(svnOperationFactory, replacedFile, copiedFile);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.ENTRY_EXISTS, e.getErrorMessage().getErrorCode());
                //expected
            }

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_ADDED, copiedFile, statuses);
            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);

            Assert.assertEquals(url.appendPath("source", false), statuses.get(copiedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualMoveMissingToReplaced() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveMissingToReplaced", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("replaced");
            commitBuilder.addFile("missing");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            final File missingFile = new File(workingCopyDirectory, "missing");
            SVNFileUtil.deleteFile(missingFile);

            moveVirtual(svnOperationFactory, missingFile, replacedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, missingFile, statuses);

            Assert.assertEquals(url.appendPath("missing", false), statuses.get(replacedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualMoveDeletedToReplaced() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveDeletedToReplaced", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("replaced");
            commitBuilder.addFile("deleted");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            final File deletedFile = new File(workingCopyDirectory, "deleted");
            workingCopy.delete(deletedFile);

            moveVirtual(svnOperationFactory, deletedFile, replacedFile);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);
            assertStatus(SVNStatusType.STATUS_DELETED, deletedFile, statuses);

            Assert.assertEquals(url.appendPath("deleted", false), statuses.get(replacedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testVirtualMoveReplacedToReplaced() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testVirtualMoveReplacedToReplaced", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("replaced");
            commitBuilder.addFile("anotherReplaced");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File replacedFile = new File(workingCopyDirectory, "replaced");
            workingCopy.delete(replacedFile);
            //noinspection ResultOfMethodCallIgnored
            replacedFile.createNewFile();
            workingCopy.add(replacedFile);

            final File anotherReplacedFile = new File(workingCopyDirectory, "anotherReplaced");
            workingCopy.delete(anotherReplacedFile);
            //noinspection ResultOfMethodCallIgnored
            anotherReplacedFile.createNewFile();
            workingCopy.add(anotherReplacedFile);

            try {
                moveVirtual(svnOperationFactory, anotherReplacedFile, replacedFile);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.ENTRY_EXISTS, e.getErrorMessage().getErrorCode());
                //expected
            }

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);

            assertStatus(SVNStatusType.STATUS_REPLACED, replacedFile, statuses);
            assertStatus(SVNStatusType.STATUS_REPLACED, anotherReplacedFile, statuses);

            Assert.assertNull(statuses.get(replacedFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void assertStatus(SVNStatusType statusType, File file, Map<File, SvnStatus> statuses) {
        if (statusType == SVNStatusType.STATUS_NONE && statuses.get(file) == null) {
            return;
        }
        Assert.assertEquals(statusType, statuses.get(file).getNodeStatus());
    }


    private void copyVirtual(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        copy(svnOperationFactory, fromFile, toFile, false, true);
    }

    private void moveVirtual(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        copy(svnOperationFactory, fromFile, toFile, true, true);
    }

    private void move(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        copy(svnOperationFactory, fromFile, toFile, true, false);
    }

    private void copy(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        copy(svnOperationFactory, fromFile, toFile, false, false);
    }

    private void copy(SvnOperationFactory svnOperationFactory, File fromFile, File toFile, boolean move, boolean virtual) throws SVNException {
        final SvnCopy copy = svnOperationFactory.createCopy();
        copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(fromFile), SVNRevision.WORKING));
        copy.setSingleTarget(SvnTarget.fromFile(toFile));
        copy.setFailWhenDstExists(true);//fail when dst exists = !copy as child
        copy.setIgnoreExternals(true);
        copy.setMakeParents(false);
        copy.setVirtual(virtual);
        copy.setMove(move);
        copy.run();
    }

    private void wronglyMove(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        SVNFileUtil.copyFile(fromFile, toFile, false);
        delete(svnOperationFactory, fromFile);
        add(svnOperationFactory, toFile);
    }

    private void wronglyCopy(SvnOperationFactory svnOperationFactory, File fromFile, File toFile) throws SVNException {
        SVNFileUtil.copyFile(fromFile, toFile, false);

        add(svnOperationFactory, toFile);
    }

    private void delete(SvnOperationFactory svnOperationFactory, File file) throws SVNException {
        final SvnScheduleForRemoval scheduleForRemoval = svnOperationFactory.createScheduleForRemoval();
        scheduleForRemoval.setSingleTarget(SvnTarget.fromFile(file));
        scheduleForRemoval.setDeleteFiles(true);
        scheduleForRemoval.setForce(false);
        scheduleForRemoval.setDryRun(false);
        scheduleForRemoval.run();
    }

    private void add(SvnOperationFactory svnOperationFactory, File file) throws SVNException {
        final SvnScheduleForAddition scheduleForAddition = svnOperationFactory.createScheduleForAddition();
        scheduleForAddition.setSingleTarget(SvnTarget.fromFile(file));
        scheduleForAddition.setAddParents(true);
        scheduleForAddition.setMkDir(false);
        scheduleForAddition.setApplyAutoProperties(false);
        scheduleForAddition.setForce(false);
        scheduleForAddition.setIncludeIgnored(true);
        scheduleForAddition.run();
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
