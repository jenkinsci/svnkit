package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class CorruptionTest {

    @Test
    public void testDavUpdateFileWithCorruptedPristine() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDavUpdateFileWithCorruptedPristine", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final String originalContentsString = "original contents";
            final String newContentsString = "new contents";

            final SvnChecksum originalContentsSha1 = TestUtil.calculateSha1(originalContentsString.getBytes());

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", originalContentsString.getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", newContentsString.getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = new File(workingCopyDirectory, "file");

            final File pristinePath = getPristinePath(svnOperationFactory, originalContentsSha1, file);

            corruptContents(pristinePath, "corrupted contents");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(file));
            try {
                update.run();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                //expected
                e.printStackTrace();
                Assert.assertEquals(SVNErrorCode.WC_CORRUPT_TEXT_BASE, e.getErrorMessage().getErrorCode());
                Assert.assertTrue(e.getErrorMessage().getMessage().contains("Checksum mismatch"));
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testReposPathDoesntStartWithSlashAfterUpdateOnFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testReposPathDoesntStartWithSlashAfterUpdateOnFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopy.getFile("file")));
            update.setRevision(SVNRevision.create(0));
            update.run();

            assertNoReposPathStartsWithSlash(workingCopy);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testPropdelOfSvnEolStyleResetsTranslatedSizeCache() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPropdelOfSvnEolStyleResetsTranslatedSizeCache", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.setFileProperty("file", SVNProperty.EOL_STYLE, SVNPropertyValue.create(SVNProperty.EOL_STYLE_NATIVE));
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file");

            final SvnSetProperty setProperty = svnOperationFactory.createSetProperty();
            setProperty.setSingleTarget(SvnTarget.fromFile(file));
            setProperty.setPropertyName(SVNProperty.EOL_STYLE);
            setProperty.setPropertyValue(null);
            setProperty.run();

            assertTranslatedSizeCacheIsReset(workingCopy);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testSymlinkHasCorrectTranslatedSize() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSymlinkHasCorrectTranslatedSize", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File link = workingCopy.getFile("directory/link");
            SVNFileUtil.ensureDirectoryExists(link.getParentFile());
            SVNFileUtil.createSymlink(link, "target");
            workingCopy.add(link);
            workingCopy.commit("Added a link");

            assertTranslatedSizeMaybeEquals(workingCopy, "directory/link", "target".getBytes().length);

            workingCopy.copy("directory", "copiedDirectory");
            assertTranslatedSizeMaybeEquals(workingCopy, "copiedDirectory/link", "target".getBytes().length);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testActualNodeConflictWorkingHasNullValueForBinaryConflict() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testActualNodeConflictWorkingHasNullValueForBinaryConflict", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", new byte[]{0, 1, 2});
            commitBuilder2.setFileProperty("file", SVNProperty.MIME_TYPE, SVNPropertyValue.create("application/octet-stream"));
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File file = workingCopy.getFile("file");

            writeBinaryContents(file, new byte[]{0, 1});

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopy.getWorkingCopyDirectory()));
            update.run();

            assertActualNodeHasNullConflictWorking(workingCopy, "file");

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void assertActualNodeHasNullConflictWorking(WorkingCopy workingCopy, String path) throws SqlJetException {
        final SqlJetDb db = SqlJetDb.open(workingCopy.getWCDbFile(), false);
        try {
            final ISqlJetTable table = db.getTable(SVNWCDbSchema.ACTUAL_NODE.name());
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            final ISqlJetCursor cursor = table.open();

            for (; !cursor.eof(); cursor.next()) {
                String cursorPath = cursor.getString(SVNWCDbSchema.NODES__Fields.local_relpath.name());
                if (!path.equals(cursorPath)) {
                    continue;
                }

                final String conflictWorking = cursor.getString(SVNWCDbSchema.ACTUAL_NODE__Fields.conflict_working.name());
                Assert.assertNull(conflictWorking);
            }
            cursor.close();
            db.commit();
        } finally {
            db.close();
        }
    }

    private void assertTranslatedSizeMaybeEquals(WorkingCopy workingCopy, String path, int expectedTranslatedSize) throws SqlJetException {
        final SqlJetDb db = SqlJetDb.open(workingCopy.getWCDbFile(), false);
        try {
            final ISqlJetTable table = db.getTable(SVNWCDbSchema.NODES.name());
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            final ISqlJetCursor cursor = table.open();

            for (; !cursor.eof(); cursor.next()) {
                String cursorPath = cursor.getString(SVNWCDbSchema.NODES__Fields.local_relpath.name());
                if (!path.equals(cursorPath)) {
                    continue;
                }
                final String translatedSizeString = cursor.getString(SVNWCDbSchema.NODES__Fields.translated_size.name());
                if (translatedSizeString == null) {
                    //valid value, skip it
                    continue;
                }
                int translatedSize = Integer.parseInt(translatedSizeString);
                Assert.assertEquals(expectedTranslatedSize, translatedSize);
            }
            cursor.close();
            db.commit();
        } finally {
            db.close();
        }
    }

    private void assertTranslatedSizeCacheIsReset(WorkingCopy workingCopy) throws SqlJetException {
        final SqlJetDb db = SqlJetDb.open(workingCopy.getWCDbFile(), false);
        try {
            final ISqlJetTable table = db.getTable(SVNWCDbSchema.NODES.name());
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            final ISqlJetCursor cursor = table.open();

            for (; !cursor.eof(); cursor.next()) {
                final ISVNWCDb.SVNWCDbKind kind = SvnWcDbStatementUtil.parseKind(cursor.getString(SVNWCDbSchema.NODES__Fields.kind.name()));
                if (kind != ISVNWCDb.SVNWCDbKind.File) {
                    continue;
                }
                final long translatedSize = cursor.getInteger(SVNWCDbSchema.NODES__Fields.translated_size.name());
                Assert.assertEquals(-1, translatedSize);
            }
            cursor.close();
            db.commit();
        } finally {
            db.close();
        }
    }

    private void writeBinaryContents(File file, byte[] binaryContents) throws IOException {
        BufferedOutputStream bufferedOutputStream = null;
        try {
            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file));
            bufferedOutputStream.write(binaryContents);
        } finally {
            SVNFileUtil.closeFile(bufferedOutputStream);
        }
    }

    private void assertNoReposPathStartsWithSlash(WorkingCopy workingCopy) throws SqlJetException {
        final SqlJetDb db = SqlJetDb.open(workingCopy.getWCDbFile(), false);
        try {
            final ISqlJetTable table = db.getTable(SVNWCDbSchema.NODES.name());
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            final ISqlJetCursor cursor = table.open();

            for (; !cursor.eof(); cursor.next()) {
                final String reposPath = cursor.getString(SVNWCDbSchema.NODES__Fields.repos_path.name());

                Assert.assertFalse("repos_path '" + reposPath + "' starts with '/'", reposPath.startsWith("/"));
            }
            cursor.close();
            db.commit();
        } finally {
            db.close();
        }
    }

    private File getPristinePath(SvnOperationFactory svnOperationFactory, SvnChecksum originalContentsSha1, File file) throws SVNException {
        final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
        try {
            final ISVNWCDb db = context.getDb();
            return db.getPristinePath(file, originalContentsSha1);
        } finally {
            context.close();
        }
    }

    private void corruptContents(File pristinePath, String corruptedContents) throws SVNException {
        TestUtil.writeFileContentsString(pristinePath, corruptedContents);
    }

    private String getTestName() {
        return "CorruptionTest";
    }
}
