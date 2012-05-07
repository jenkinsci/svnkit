package org.tmatesoft.svn.test;

import java.io.ByteArrayInputStream;
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
