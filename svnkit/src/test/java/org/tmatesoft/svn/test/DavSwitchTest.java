package org.tmatesoft.svn.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnLog;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnSwitch;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class DavSwitchTest {
    
    @Test
    public void testSwitchInvalidateWcProps() throws SVNException {
        
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + ".testSwithcInvalidatesWcProps", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("trunk/a.txt");
            commitBuilder.addFile("trunk/dir/b.txt");
            commitBuilder.addFile("trunk/dir/subdir/c.txt");
            commitBuilder.addDirectory("branches");
            commitBuilder.commit();

            SvnRemoteCopy cp = svnOperationFactory.createRemoteCopy();
            cp.setSingleTarget(SvnTarget.fromURL(url.appendPath("branches/branch", false)));
            cp.addCopySource(SvnCopySource.create(SvnTarget.fromURL(url.appendPath("trunk", false)), SVNRevision.HEAD));
            cp.setCommitMessage("branch created");
            cp.run();

            commitBuilder = new CommitBuilder(url);
            commitBuilder.changeFile("trunk/dir/subdir/c.txt", "modified".getBytes());
            commitBuilder.commit();

            // full switch
            WorkingCopy wc1 = sandbox.checkoutNewWorkingCopy(url.appendPath("trunk", false));
            SvnSwitch sw = svnOperationFactory.createSwitch();
            sw.setSingleTarget(SvnTarget.fromFile(wc1.getWorkingCopyDirectory()));
            sw.setSwitchTarget(SvnTarget.fromURL(url.appendPath("branches/branch", false)));
            sw.run();
            wc1.changeFileContents("a.txt", "new contents in trunk");
            wc1.commit("modification goes to trunk");

            // partial switch
            WorkingCopy wc2 = sandbox.checkoutNewWorkingCopy(url.appendPath("branches/branch", false));
            sw = svnOperationFactory.createSwitch();
            sw.setSingleTarget(SvnTarget.fromFile(wc2.getFile("dir")));
            sw.setSwitchTarget(SvnTarget.fromURL(url.appendPath("trunk/dir", false)));
            sw.run();

            wc2.changeFileContents("a.txt", "new contents in branch");
            wc2.changeFileContents("dir/subdir/c.txt", "new contents in trunk");
            wc2.commit("modification goes both to trunk and branch");

            // test log.
            SvnLog log = svnOperationFactory.createLog();
            log.setSingleTarget(SvnTarget.fromURL(url));
            log.addRange(SvnRevisionRange.create(SVNRevision.HEAD, SVNRevision.create(0)));
            log.setDiscoverChangedPaths(true);
            log.setUseMergeHistory(false);
            log.setLimit(2);

            List<SVNLogEntry> logEntries = new ArrayList<SVNLogEntry>();
            log.run(logEntries);

            // first should contain two paths
            SVNLogEntry log2 = logEntries.get(0);
            SVNLogEntry log1 = logEntries.get(1);

            Map<String, SVNLogEntryPath> paths2 = log2.getChangedPaths();
            Map<String, SVNLogEntryPath> paths1 = log1.getChangedPaths();

            Assert.assertNotNull(paths2.remove("/trunk/dir/subdir/c.txt"));
            Assert.assertNotNull(paths2.remove("/branches/branch/a.txt"));
            Assert.assertTrue(paths2.isEmpty());

            Assert.assertNotNull(paths1.remove("/branches/branch/a.txt"));
            Assert.assertTrue(paths1.isEmpty());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

}
