package org.tmatesoft.svn.test;

import junit.framework.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnNgDowngrade;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;

public class DowngradeTest {

    @Test
    public void testBasics() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testBasics", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("conflictedFile", "base".getBytes());
            commitBuilder1.addFile("file", "contents".getBytes());
            commitBuilder1.setFileProperty("conflictedFile", "property", SVNPropertyValue.create("base"));
            commitBuilder1.addFile("treeConflictFile");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("conflictedFile", "theirs".getBytes());
            commitBuilder2.setFileProperty("conflictedFile", "property", SVNPropertyValue.create("theirs"));
            commitBuilder2.delete("treeConflictFile");
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File conflictedFile = workingCopy.getFile("conflictedFile");
            final File treeConflictFile = workingCopy.getFile("treeConflictFile");
            workingCopy.setProperty(conflictedFile, "property", SVNPropertyValue.create("ours"));
            TestUtil.writeFileContentsString(conflictedFile, "ours");
            TestUtil.writeFileContentsString(treeConflictFile, "ours");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.run();

            SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SvnNgDowngrade svnNgDowngrade = new SvnNgDowngrade();
                svnNgDowngrade.downgrade(context, workingCopyDirectory);
            } finally {
                context.close();
            }
            context = new SVNWCContext(ISVNWCDb.SVNWCDbOpenMode.ReadOnly, svnOperationFactory.getOptions(), false, false, svnOperationFactory.getEventHandler());
            SVNWCDb db = (SVNWCDb) (context.getDb());
            SVNWCDb.DirParsedInfo parsed = db.parseDir(workingCopyDirectory, SVNSqlJetDb.Mode.ReadOnly);
            Assert.assertEquals(ISVNWCDb.WC_FORMAT_17, parsed.wcDbDir.getWCRoot().getFormat());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
