package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

public class ExceptionsHandlingTest {

    @Test
    public void testConnectionIsClosedOnOutOfDateError() throws Exception {
        //SVNKIT-348
        final TestOptions options = TestOptions.getInstance();

        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testConnectionIsClosedOnOutOfDateError", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            //let's add a file
            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            //now let's "remove" it using wrong revisions
            final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
            try {
                final ISVNEditor editor = svnRepository.getCommitEditor("Commit message", null);
                editor.openRoot(0); // <-- the revision is wrong because it contains no file

                try {
                    editor.openFile("file", 0); // <-- delete non-existing file

                    Assert.fail("An exception should be thrown");
                } catch (SVNException e) {
                    editor.abortEdit();
                    //expected
                }

                // this call should not fail with "SVNRepository methods are not reenterable" because the connection was closed
                Assert.assertEquals(1, svnRepository.getLatestRevision());

            } finally {
                svnRepository.closeSession();
            }

        } finally {
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "MethodsReenterabilityTest";
    }
}
