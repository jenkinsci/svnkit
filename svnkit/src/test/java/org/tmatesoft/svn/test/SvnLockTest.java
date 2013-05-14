package org.tmatesoft.svn.test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc2.*;
import org.tmatesoft.svn.core.wc2.admin.SvnRepositoryGetLock;

public class SvnLockTest {
    
    @Test
    public void testCommitOfLockedFile() throws SVNException {
        final String fullFilePath = "Project/Prueba/Modify/prueba.txt";
        final String filePath = "Prueba/Modify/prueba.txt";
        
        final TestOptions options = TestOptions.getInstance();
        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + ".testModifyLocked", options);
        final SVNURL url = sandbox.createSvnRepository();
        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addFile(fullFilePath);
        commitBuilder.commit();

        // user paths relative to Project directory.
        
        SVNRepository repository = SVNRepositoryFactory.create(url.appendPath("Project", false));
        repository.setAuthenticationManager(new BasicAuthenticationManager("user", "password"));
        final Map<String, Long> pathsToRevisions = new HashMap<String, Long>();
        pathsToRevisions.put(filePath, 1l);
        repository.lock(pathsToRevisions, null, false, null);

        repository.closeSession();
        // same user as one who owns the lock.
        repository.setAuthenticationManager(new BasicAuthenticationManager("user", "password"));

        final SVNLock lock = repository.getLock(filePath);
        Assert.assertNotNull(lock);
        Assert.assertNotNull(lock.getID());
        Assert.assertEquals("user", lock.getOwner());
        
        final Map<String, String> locks = new HashMap<String, String>();

        try {
            tryCommit(filePath, repository, locks);
            Assert.fail();
        } catch (SVNException e) {
            // no lock token.
        }
        
        locks.put(filePath, lock.getID());
        SVNCommitInfo info = tryCommit(filePath, repository, locks);
        Assert.assertNotNull(info);
        Assert.assertEquals(2, info.getNewRevision());
    }

    @Test
    public void testSvnRepositoryGetLock() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + ".testSvnRepositoryGetLock", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("directory/file");

            final String expectedLockMessage = "Lock message";
            final String expectedLockOwner = "owner";

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            svnOperationFactory.setAuthenticationManager(new BasicAuthenticationManager(expectedLockOwner, null));
            setLock.setSingleTarget(SvnTarget.fromFile(file));
            setLock.setLockMessage(expectedLockMessage);
            setLock.setStealLock(true);
            setLock.run();

            //svnlook-like way to obtain lock, requires access to FSFS repository
            final SvnRepositoryGetLock getLock = svnOperationFactory.createRepositoryGetLock();
            getLock.setRepositoryRoot(new File(url.getPath()));
            getLock.setPath("directory/file");
            SVNLock lock = getLock.run();

            checkLockOwnerAndMessage(expectedLockMessage, expectedLockOwner, lock);

            //svn info-like way to obtain lock, requires only working copy (even working copy is not mandatory, one can use SvnTarget#fromURL)
            final SvnGetInfo getInfo = svnOperationFactory.createGetInfo();
            getInfo.setSingleTarget(SvnTarget.fromFile(file));
            final SvnInfo info = getInfo.run();

            lock = info.getLock();
            checkLockOwnerAndMessage(expectedLockMessage, expectedLockOwner, lock);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void checkLockOwnerAndMessage(String expectedLockMessage, String expectedLockOwner, SVNLock lock) {
        String actualLockOwner = lock == null ? null : lock.getOwner();
        String actualLockMessage = lock == null ? null : lock.getComment();

        Assert.assertEquals(expectedLockOwner, actualLockOwner);
        Assert.assertEquals(expectedLockMessage, actualLockMessage);
    }

    private SVNCommitInfo tryCommit(final String filePath, SVNRepository repository, final Map<String, String> locks) throws SVNException {
        ISVNEditor editor = repository.getCommitEditor("commit message", locks, true, null, null);
        try {
            editor.openRoot(-1);
            editor.openDir("Prueba", -1);
            editor.openDir("Prueba/Modify", -1);
            editor.openFile(filePath, -1);
            editor.applyTextDelta(filePath, null);
            final SVNDeltaGenerator generator = new SVNDeltaGenerator();
            final byte[] newContents = "new contents".getBytes();
            final String checksum = generator.sendDelta(filePath, new ByteArrayInputStream(newContents), editor, true);
            editor.closeFile(filePath, checksum);
            editor.closeDir();
            editor.closeDir();
            editor.closeDir();
            return editor.closeEdit();
        } catch (SVNException e) {
            if (editor != null) {
                editor.abortEdit();
            }
            throw e;
        }
    }

}
