package org.tmatesoft.svn.test;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.io.fs.FSCommitter;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

public class AutoUnlockTest {

    @Test
    public void testAutoUnlock() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testAutoUnlock", options);

        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory");
            commitBuilder.addFile("directory/file.txt", "contents".getBytes());
            commitBuilder.commit();

            // lock by user 1
            final SVNRepository svnRepository = svnOperationFactory.getRepositoryPool().createRepository(url, true);
            svnRepository.setAuthenticationManager(new BasicAuthenticationManager(new SVNAuthentication[] { new SVNUserNameAuthentication("user1", false, url, false), }));
            Map<String, Long> pathsToRevisions = new HashMap<String, Long>();
            pathsToRevisions.put("directory/file.txt", 1l);
            svnRepository.lock(pathsToRevisions, null, false, null);

            SVNLock lock = svnRepository.getLock("directory/file.txt");
            Assert.assertNotNull(lock);
            Assert.assertNotNull(lock.getID());
            Assert.assertEquals("user1", lock.getOwner());

            // will fail.
            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setAuthenticationManager(new BasicAuthenticationManager(new SVNAuthentication[] { new SVNUserNameAuthentication("user2", false, url, false), }));

            try {
                commitBuilder2.changeFile("directory/file.txt", "new contents".getBytes());
                commitBuilder2.commit();
                Assert.fail();
            } catch (SVNException e) {
            }
            lock = svnRepository.getLock("directory/file.txt");
            Assert.assertNotNull(lock);
            Assert.assertNotNull(lock.getID());
            Assert.assertEquals("user1", lock.getOwner());

            // now
            FSCommitter.setAutoUnlock(true);
            final CommitBuilder commitBuilder3 = new CommitBuilder(url);
            commitBuilder3.changeFile("directory/file.txt", "new contents".getBytes());
            commitBuilder3.commit();

            lock = svnRepository.getLock("directory/file.txt");
            Assert.assertNull(lock);

        } finally {
            FSCommitter.setAutoUnlock(false);
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoveLockInDeletedDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoveLockInDeletedDirectory", options);

        SVNRepository svnRepository = null;

        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.addFile("directory/file.txt", "contents".getBytes());
            commitBuilder1.commit();

            svnRepository = SVNRepositoryFactory.create(url);
            svnRepository.setAuthenticationManager(new BasicAuthenticationManager("username", "password"));

            final Map<String, Long> pathsToRevisions = new HashMap<String, Long>();
            pathsToRevisions.put("directory/file.txt", 1l);
            svnRepository.lock(pathsToRevisions, "locked", false, null);
            FSCommitter.setAutoUnlock(true);

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.replaceDirectoryByCopying("directory", "directory");
            commitBuilder2.changeFile("directory/file.txt", "changed contents".getBytes());
            commitBuilder2.commit();

            final SVNLock lock = svnRepository.getLock("directory/file.txt");
            Assert.assertNull(lock);

        } finally {
            if (svnRepository != null) {
                svnRepository.closeSession();
            }
            FSCommitter.setAutoUnlock(false);
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "AutoUnlockTest";
    }
}
