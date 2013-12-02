package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectFieldsStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class CopyTest {

    @Test
    public void testMoveBasePegRevision() throws Exception {
        testCopyBasePegRevision(true, "testMoveBasePegRevision");
    }

    @Test
    public void testCopyBasePegRevision() throws Exception {
        testCopyBasePegRevision(false, "testCopyBasePegRevision");
    }

    @Test
    public void testCopyDoesntCorruptPristineTable() throws Exception {
        //a test for a problem: a sha1 checksum was written to PRISTINE table instead of md5 checksum in some cases
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + "." + "testCopyDoesntCorruptPristineTable", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory1/file", "original contents".getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.addDirectoryByCopying("directory2", "directory1");
            commitBuilder2.addFile("directory2/anotherFile", "remote contents".getBytes());
            commitBuilder2.commit();

            final CommitBuilder commitBuilder3 = new CommitBuilder(url);
            commitBuilder3.changeFile("directory1/file", "new contents".getBytes());
            commitBuilder3.commit();

            final SVNURL directory1Url = url.appendPath("directory1", false);
            final SVNURL directory2Url = url.appendPath("directory2", false);

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(directory1Url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final SvnMerge merge = svnOperationFactory.createMerge();
            merge.setSource(SvnTarget.fromURL(directory2Url, SVNRevision.create(2)), false);
            merge.addRevisionRange(SvnRevisionRange.create(SVNRevision.create(1), SVNRevision.create(2)));
            merge.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            merge.run();

            assertWCDbContainsCorrectChecksumTypesInPristineTable(svnOperationFactory, workingCopyDirectory);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testWorkingToRepositoryCopy() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testWorkingToRepositoryCopy", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("directory");
            commitBuilder2.commit();

            final SVNURL subUrl = url.appendPath("directory", false);

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(subUrl, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File localFile = new File(workingCopyDirectory, "local");
            TestUtil.writeFileContentsString(localFile, "contents");
            workingCopy.add(localFile);

            final SvnRemoteCopy remoteCopy = svnOperationFactory.createRemoteCopy();
            remoteCopy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(workingCopyDirectory), SVNRevision.create(1)));
            remoteCopy.setSingleTarget(SvnTarget.fromURL(url.appendPath("another directory", false)));
            final SVNCommitInfo commitInfo = remoteCopy.run();

            Assert.assertEquals(3, commitInfo.getNewRevision());

            //check SVN log
            SVNRepository svnRepository = SVNRepositoryFactory.create(url);
            try {
                final Collection logEntries = svnRepository.log(new String[]{""}, null, 3, 3, true, true);

                Assert.assertEquals(1, logEntries.size());
                final SVNLogEntry logEntry = (SVNLogEntry) logEntries.iterator().next();

                final Map<String, SVNLogEntryPath> changedPaths = logEntry.getChangedPaths();
                Assert.assertEquals(1, changedPaths.size());

                final SVNLogEntryPath logEntryPath = changedPaths.get("/another directory");
                Assert.assertNotNull(logEntryPath);
                Assert.assertEquals(SVNNodeKind.DIR, logEntryPath.getKind());
                Assert.assertEquals('A', logEntryPath.getType());
            } finally {
                svnRepository.closeSession();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRepositoryToWorkingCopyCorrectRepositoryPathSvnAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllSvnserveOptionsSpecified(options));
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRepositoryToWorkingCopyCorrectRepositoryPathSvnAccess", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithSvnAccess();
            Assume.assumeTrue(url.getPath().length() == 0);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.addFile("targetFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);

            final SVNURL sourceFileUrl = url.appendPath("sourceFile", false);
            final File targetFile = workingCopy.getFile("targetFile");

            workingCopy.delete(targetFile);

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromURL(sourceFileUrl), SVNRevision.HEAD));
            copy.setSingleTarget(SvnTarget.fromFile(targetFile));
            copy.run();

            assertNoRepositoryPathStartsWithSlash(svnOperationFactory, workingCopy.getWorkingCopyDirectory());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Ignore("SVNKIT-284, currently fails")
    @Test
    public void testRemoteCopyURLAutoEncodeCorruptsFilename() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteCopyURLAutoEncodeCorruptsFilename", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory");
            commitBuilder.addFile("file%20with%20space");
            commitBuilder.commit();

            final SvnRemoteCopy remoteCopy = svnOperationFactory.createRemoteCopy();
            remoteCopy.addCopySource(SvnCopySource.create(SvnTarget.fromURL(url.appendPath("file%20with%20space", false)), SVNRevision.HEAD));
            remoteCopy.setSingleTarget(SvnTarget.fromURL(url.appendPath("directory", false)));
            remoteCopy.setFailWhenDstExists(false);
            remoteCopy.run();

            final int[] count = {0};
            final SvnList list = svnOperationFactory.createList();
            list.setSingleTarget(SvnTarget.fromURL(url.appendPath("directory", false)));
            list.setReceiver(new ISvnObjectReceiver<SVNDirEntry>() {
                public void receive(SvnTarget target, SVNDirEntry dirEntry) throws SVNException {
                    count[0]++;
                    if (dirEntry.getKind() == SVNNodeKind.FILE) {
                        Assert.assertEquals("file%20with%20space", dirEntry.getName());
                    }
                }
            });
            list.run();

            Assert.assertEquals(2, count[0]);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testImpossibilityToMoveFileUnderUnversionedDirectory() throws Exception  {
        //SVNKIT-295
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testImpossibilityToMoveFileUnderUnversionedDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file");

            final File unversionedDirectory = workingCopy.getFile("unversionedDirectory");
            SVNFileUtil.ensureDirectoryExists(unversionedDirectory);

            final File targetFile = new File(unversionedDirectory, "file");

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNCopyClient copyClient = clientManager.getCopyClient();
                copyClient.doCopy(new SVNCopySource[]{new SVNCopySource(SVNRevision.UNDEFINED, SVNRevision.WORKING, file)}, targetFile, true, false, true);

                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                //expected
                e.printStackTrace();
                Assert.assertEquals(TestUtil.isNewWorkingCopyTest() ? SVNErrorCode.WC_PATH_NOT_FOUND : SVNErrorCode.WC_NOT_WORKING_COPY, e.getErrorMessage().getErrorCode());
            } finally {
                clientManager.dispose();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testWCWithExternalsToRepos() throws Exception {
        //SVNKIT-324
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testWCWithExternalsToRepos", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();
            final SVNURL targetUrl = url.appendPath("target", false);

            final SVNExternal external = new SVNExternal("external", targetUrl.toString(), SVNRevision.HEAD, SVNRevision.HEAD, false, false, true);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.setDirectoryProperty("", SVNProperty.EXTERNALS, SVNPropertyValue.create(external.toString()));
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final SvnRemoteCopy remoteCopy = svnOperationFactory.createRemoteCopy();
            remoteCopy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(workingCopyDirectory), SVNRevision.WORKING));
            remoteCopy.setSingleTarget(SvnTarget.fromURL(targetUrl));
            final SVNCommitInfo commitInfo = remoteCopy.run();

            Assert.assertEquals(2, commitInfo.getNewRevision());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testCopyAddedDirectoryWithUnversionedFiles() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopyAddedDirectoryWithUnversionedFiles", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File sourceDirectory = workingCopy.getFile("sourceDirectory");
            final File targetDirectory = workingCopy.getFile("targetDirectory");
            final File sourceFile = new File(sourceDirectory, "file");
            final File targetFile = new File(targetDirectory, "file");

            SVNFileUtil.ensureDirectoryExists(sourceDirectory);
            workingCopy.add(sourceDirectory);
            TestUtil.writeFileContentsString(sourceFile, "content");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceDirectory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(targetDirectory));
            copy.run();

            Assert.assertTrue(targetFile.isFile());
            Assert.assertTrue(sourceFile.isFile());

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(sourceFile).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(targetFile).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(sourceDirectory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(targetDirectory).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testCopySymlink() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopySymlink", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File sourceSymlink = workingCopy.getFile("sourceSymlink");
            final File targetSymlink = workingCopy.getFile("targetSymlink");
            SVNFileUtil.createSymlink(sourceSymlink, "target");
            workingCopy.add(sourceSymlink);

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceSymlink), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(targetSymlink));
            copy.run();

            Assert.assertEquals(SVNFileType.SYMLINK, SVNFileType.getType(sourceSymlink));
            Assert.assertEquals(SVNFileType.SYMLINK, SVNFileType.getType(targetSymlink));

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(sourceSymlink).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(targetSymlink).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
     }

    @Test
    public void testCopyIntoUnversionedDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopyIntoUnversionedDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();
            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory/subdirectory");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File subdirectory = workingCopy.getFile("directory/subdirectory");
            final File unversioned = workingCopy.getFile("unversioned");
            final File unversionedTarget = new File(unversioned, "subdirectory");

            SVNFileUtil.ensureDirectoryExists(unversioned);

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setMakeParents(true);
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(subdirectory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(unversionedTarget));
            copy.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(unversioned).getNodeStatus());
            Assert.assertFalse(statuses.get(unversioned).isCopied());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(unversionedTarget).getNodeStatus());
            Assert.assertTrue(statuses.get(unversionedTarget).isCopied());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testCopyIntoMissingDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopyIntoMissingDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();
            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory/subdirectory");
            commitBuilder.addDirectory("missing");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File subdirectory = workingCopy.getFile("directory/subdirectory");
            final File missing = workingCopy.getFile("missing");

            SVNFileUtil.deleteAll(missing, true);

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setFailWhenDstExists(false);
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(subdirectory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(missing));
            try {
                copy.run();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                //expected
                Assert.assertEquals(SVNErrorCode.WC_MISSING, e.getErrorMessage().getErrorCode());
                Assert.assertTrue(e.getMessage().contains("is not a directory"));
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMarkerFilesNotCopied() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMarkerFilesNotCopied", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.addFile("directory/file", "another contents".getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File directory = workingCopy.getFile("directory");
            final File targetDirectory = workingCopy.getFile("targetDirectory");
            final File file = workingCopy.getFile("directory/file");
            TestUtil.writeFileContentsString(file, "contents");
            workingCopy.add(file);

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(directory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(targetDirectory));
            copy.run();

            final File mineCopiedFile = new File(targetDirectory, "file.mine");
            File r0CopiedFile = new File(targetDirectory, "file.r0");
            File r2CopiedFile = new File(targetDirectory, "file.r2");
            Assert.assertFalse(mineCopiedFile.exists());
            Assert.assertFalse(r0CopiedFile.exists());
            Assert.assertFalse(r2CopiedFile.exists());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testCopyOverExcludedDirectoryShouldFail() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testCopyOverExcludedDirectoryShouldFail", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("sourceDirectory");
            commitBuilder.addDirectory("targetDirectory");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File targetDirectory = workingCopy.getFile("targetDirectory");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setDepthIsSticky(true);
            update.setDepth(SVNDepth.EXCLUDE);
            update.setSingleTarget(SvnTarget.fromFile(targetDirectory));
            update.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromURL(url.appendPath("sourceDirectory", false)), SVNRevision.HEAD));
            copy.setSingleTarget(SvnTarget.fromFile(targetDirectory));
            try {
                copy.run();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                Assert.assertEquals(SVNErrorCode.WC_OBSTRUCTED_UPDATE, e.getErrorMessage().getErrorCode());
                Assert.assertTrue(e.getMessage().contains("exists, but is excluded"));
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void assertNoRepositoryPathStartsWithSlash(SvnOperationFactory svnOperationFactory, File workingCopyDirectory) throws SVNException {
        final SVNWCContext context = new SVNWCContext(ISVNWCDb.SVNWCDbOpenMode.ReadOnly, svnOperationFactory.getOptions(), false, false, svnOperationFactory.getEventHandler());
        try {
            final SVNWCDb db = (SVNWCDb) context.getDb();
            final SVNWCDb.DirParsedInfo dirParsedInfo = db.parseDir(workingCopyDirectory, SVNSqlJetDb.Mode.ReadOnly);
            final SVNSqlJetDb sdb = dirParsedInfo.wcDbDir.getWCRoot().getSDb();

            final SelectRepositoryRelPath selectRepositoryRelPath = new SelectRepositoryRelPath(sdb);
            try {
                selectRepositoryRelPath.bindf("i", dirParsedInfo.wcDbDir.getWCRoot().getWcId());

                int recordsCount = 0;
                while (selectRepositoryRelPath.next()) {
                    recordsCount++;
                    final String reposPath = selectRepositoryRelPath.getColumnString(SVNWCDbSchema.NODES__Fields.repos_path);
                    Assert.assertFalse(reposPath.startsWith("/"));
                }

                Assert.assertEquals(4, recordsCount); //1 for root, 1 for sourceFile, 2 for targetFile
            } finally {
                selectRepositoryRelPath.reset();
            }

        } finally {
            context.close();
        }
    }

    private void testCopyBasePegRevision(boolean move, String testName) throws SVNException, IOException {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + "." +
                testName, options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile", "original contents".getBytes());
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File sourceFile = new File(workingCopyDirectory, "sourceFile");
            final File targetFile = new File(workingCopyDirectory, "targetFile");

            final String expectedNewContents = move ? "new contents" : "original contents";
            TestUtil.writeFileContentsString(sourceFile, "new contents");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceFile, SVNRevision.BASE), SVNRevision.UNDEFINED));
            copy.setSingleTarget(SvnTarget.fromFile(targetFile));
            copy.setMove(move);
            copy.run();

            Assert.assertTrue(targetFile.isFile());

            final String actualNewContents = TestUtil.readFileContentsString(targetFile);
            Assert.assertEquals(expectedNewContents, actualNewContents);

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(move ? SVNStatusType.STATUS_DELETED : SVNStatusType.STATUS_MODIFIED, statuses.get(sourceFile).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(targetFile).getNodeStatus());
            Assert.assertEquals(url.appendPath(sourceFile.getName(), false), statuses.get(targetFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void assertWCDbContainsCorrectChecksumTypesInPristineTable(SvnOperationFactory svnOperationFactory, File workingCopyDirectory) throws SVNException {
        final SVNWCDb db = new SVNWCDb();
        db.open(ISVNWCDb.SVNWCDbOpenMode.ReadOnly, svnOperationFactory.getOptions(), false, true);
        try {
            final SVNWCDb.DirParsedInfo dirParsedInfo = db.parseDir(workingCopyDirectory, SVNSqlJetDb.Mode.ReadOnly);
            final SVNSqlJetStatement selectMd5Statement = dirParsedInfo.wcDbDir.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_PRISTINE_MD5_CHECKSUM);
            try {
                while (selectMd5Statement.next()) {
                    final SvnChecksum md5Checksum = SvnWcDbStatementUtil.getColumnChecksum(selectMd5Statement, SVNWCDbSchema.PRISTINE__Fields.md5_checksum);
                    final SvnChecksum sha1Checksum = SvnWcDbStatementUtil.getColumnChecksum(selectMd5Statement, SVNWCDbSchema.PRISTINE__Fields.checksum);
                    Assert.assertEquals(SvnChecksum.Kind.md5, md5Checksum.getKind());
                    Assert.assertEquals(SvnChecksum.Kind.sha1, sha1Checksum.getKind());
                }
            } finally {
                selectMd5Statement.reset();
            }
        } finally {
            db.close();
        }
    }

    private String getTestName() {
        return "CopyTest";
    }

    private static class SelectRepositoryRelPath extends SVNSqlJetSelectFieldsStatement<SVNWCDbSchema.NODES__Fields> {

        public SelectRepositoryRelPath(SVNSqlJetDb sDb) throws SVNException {
            super(sDb, SVNWCDbSchema.NODES);
        }

        @Override
        protected void defineFields() {
            fields.add(SVNWCDbSchema.NODES__Fields.repos_path);
        }
    }
}
