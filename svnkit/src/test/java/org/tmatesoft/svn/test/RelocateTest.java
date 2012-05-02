package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRelocate;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

public class RelocateTest {

    @Test
    public void testRelocateCleansDavCache() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRelocateCleansDavCache", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");

            final SVNURL fsfsUrl = sandbox.getFSFSAccessUrl(url);

            final String wcUrlBeforeRelocate = getWcUrl(svnOperationFactory, file);
            Assert.assertNotNull(wcUrlBeforeRelocate);

            final SvnRelocate relocate = svnOperationFactory.createRelocate();
            relocate.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            relocate.setToUrl(fsfsUrl);
            relocate.run();

            final String wcUrlAfterRelocate = getWcUrl(svnOperationFactory, file);
            Assert.assertNull(wcUrlAfterRelocate);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getWcUrl(SvnOperationFactory svnOperationFactory, File file) throws SVNException {
        final SVNProperties davCache = getBaseDavCache(svnOperationFactory, file);
        return davCache == null ? null : davCache.getStringValue(SVNProperty.WC_URL);
    }

    private SVNProperties getBaseDavCache(SvnOperationFactory svnOperationFactory, File file) throws SVNException {
        final SVNProperties baseDavCache;
        final SVNWCDb db = new SVNWCDb();
        try {
            db.open(ISVNWCDb.SVNWCDbOpenMode.ReadOnly, svnOperationFactory.getOptions(), false, false);
            baseDavCache = db.getBaseDavCache(file);
        } finally {
            db.close();
        }
        return baseDavCache;
    }

    private String getTestName() {
        return "RelocateTest";
    }
}
