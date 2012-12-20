package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectFieldsStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
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
