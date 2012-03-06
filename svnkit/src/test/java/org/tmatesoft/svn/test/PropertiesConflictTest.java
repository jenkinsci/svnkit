package org.tmatesoft.svn.test;

import java.io.File;

import junit.framework.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

public class PropertiesConflictTest {
    @Test
    public void testConflictOnUpdate() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testConflictOnUpdate", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file");
            final SVNCommitInfo commitInfo1 = commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setFileProperty("directory/file", "propertyName", SVNPropertyValue.create("r2 value"));
            final SVNCommitInfo commitInfo2 = commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, commitInfo1.getNewRevision());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = new File(workingCopyDirectory, "directory/file");

            workingCopy.setProperty(file, "propertyName", SVNPropertyValue.create("working copy value"));

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setIgnoreExternals(true);
            update.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            update.setRevision(SVNRevision.create(commitInfo2.getNewRevision()));
            update.setDepth(SVNDepth.INFINITY);
            update.setDepthIsSticky(true);
            update.run();

            final String actualPrejContents = TestUtil.readFileContentsString(new File(file.getPath() + ".prej"));
            final String expectedPrejContents = "Trying to add new property 'propertyName'\n" +
                    "but the property already exists.\n" +
                    "<<<<<<< (local property value)\n" +
                    "working copy value=======\n" +
                    "r2 value>>>>>>> (incoming property value)\n";
            Assert.assertEquals(expectedPrejContents, actualPrejContents);
        } catch (SVNException e) {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "PropertiesConflictTest";
    }
}
