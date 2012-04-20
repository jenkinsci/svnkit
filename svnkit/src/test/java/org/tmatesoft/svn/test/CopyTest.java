package org.tmatesoft.svn.test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

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

                final Map<String,SVNLogEntryPath> changedPaths = logEntry.getChangedPaths();
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

            final Map<File,SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopyDirectory);
            Assert.assertEquals(move ? SVNStatusType.STATUS_DELETED : SVNStatusType.STATUS_MODIFIED, statuses.get(sourceFile).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(targetFile).getNodeStatus());
            Assert.assertEquals(url.appendPath(sourceFile.getName(), false), statuses.get(targetFile).getCopyFromUrl());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "CopyTest";
    }
}
