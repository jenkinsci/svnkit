package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnList;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListTest {

    @Test
    public void testListOnRepositoryRoot() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testListOnRepositoryRoot", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory");
            commitBuilder.commit();

            final List<SVNDirEntry> entries = new ArrayList<SVNDirEntry>();

            final SvnList list = svnOperationFactory.createList();
            list.setSingleTarget(SvnTarget.fromURL(url, SVNRevision.HEAD));
            list.setRevision(SVNRevision.HEAD);
            list.setReceiver(new ISvnObjectReceiver<SVNDirEntry>() {
                public void receive(SvnTarget target, SVNDirEntry dirEntry) throws SVNException {
                    entries.add(dirEntry);
                }
            });
            list.run();

            Collections.sort(entries);

            Assert.assertEquals(2, entries.size());
            Assert.assertEquals("", entries.get(0).getName());
            Assert.assertEquals("", entries.get(0).getRelativePath());
            Assert.assertEquals("directory", entries.get(1).getName());
            Assert.assertEquals("directory", entries.get(1).getRelativePath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();

        }
    }

    @Test
    public void testListOnRepositoryRootDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testListOnRepositoryRootDavAccess", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory");
            commitBuilder.commit();

            final List<SVNDirEntry> entries = new ArrayList<SVNDirEntry>();

            final SvnList list = svnOperationFactory.createList();
            list.setSingleTarget(SvnTarget.fromURL(url, SVNRevision.HEAD));
            list.setRevision(SVNRevision.HEAD);
            list.setReceiver(new ISvnObjectReceiver<SVNDirEntry>() {
                public void receive(SvnTarget target, SVNDirEntry dirEntry) throws SVNException {
                    entries.add(dirEntry);
                }
            });
            list.run();

            Collections.sort(entries);

            Assert.assertEquals(2, entries.size());
            Assert.assertEquals("", entries.get(0).getName());
            Assert.assertEquals("", entries.get(0).getRelativePath());
            Assert.assertEquals("directory", entries.get(1).getName());
            Assert.assertEquals("directory", entries.get(1).getRelativePath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testListOnDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testListOnDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory/subdirectory");
            commitBuilder.commit();

            final SVNURL directoryUrl = url.appendPath("directory", false);

            final List<SVNDirEntry> entries = new ArrayList<SVNDirEntry>();

            final SvnList list = svnOperationFactory.createList();
            list.setSingleTarget(SvnTarget.fromURL(directoryUrl, SVNRevision.HEAD));
            list.setRevision(SVNRevision.HEAD);
            list.setReceiver(new ISvnObjectReceiver<SVNDirEntry>() {
                public void receive(SvnTarget target, SVNDirEntry dirEntry) throws SVNException {
                    entries.add(dirEntry);
                }
            });
            list.run();

            Collections.sort(entries);

            Assert.assertEquals(2, entries.size());
            Assert.assertEquals("", entries.get(0).getName());
            Assert.assertEquals("", entries.get(0).getRelativePath());
            Assert.assertEquals("subdirectory", entries.get(1).getName());
            Assert.assertEquals("subdirectory", entries.get(1).getRelativePath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();

        }
    }

    @Test
    public void testListOnDirectoryDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testListOnDirectoryDavAccess", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory/subdirectory");
            commitBuilder.commit();

            final SVNURL directoryUrl = url.appendPath("directory", false);

            final List<SVNDirEntry> entries = new ArrayList<SVNDirEntry>();

            final SvnList list = svnOperationFactory.createList();
            list.setSingleTarget(SvnTarget.fromURL(directoryUrl, SVNRevision.HEAD));
            list.setRevision(SVNRevision.HEAD);
            list.setReceiver(new ISvnObjectReceiver<SVNDirEntry>() {
                public void receive(SvnTarget target, SVNDirEntry dirEntry) throws SVNException {
                    entries.add(dirEntry);
                }
            });
            list.run();

            Collections.sort(entries);

            Assert.assertEquals(2, entries.size());
            Assert.assertEquals("", entries.get(0).getName());
            Assert.assertEquals("", entries.get(0).getRelativePath());
            Assert.assertEquals("subdirectory", entries.get(1).getName());
            Assert.assertEquals("subdirectory", entries.get(1).getRelativePath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "ListTest";
    }
}
