package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.replicator.SVNRepositoryReplicator;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

import java.util.Collection;
import java.util.Iterator;

public class ReplicateTest {

    @Test
    public void testReplacedNodeInHistory() throws Exception {
        //SVNKIT-271
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testReplacedNodeInHistory", options);
        try {
            final SVNURL sourceUrl = sandbox.createSvnRepository();
            final SVNURL targetUrl = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(sourceUrl);
            commitBuilder1.addFile("file");
            commitBuilder1.addFile("anotherFile");
            commitBuilder1.addDirectory("directory");
            commitBuilder1.addFile("anotherDirectory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(sourceUrl);
            commitBuilder2.delete("file");
            commitBuilder2.replaceFileByCopying("file", "anotherFile");
            commitBuilder2.replaceDirectoryByCopying("directory", "anotherDirectory");
            commitBuilder2.commit();

            final SVNRepository sourceRepository = SVNRepositoryFactory.create(sourceUrl);
            final SVNRepository targetRepository = SVNRepositoryFactory.create(targetUrl);
            try {
                final SVNRepositoryReplicator replicator = SVNRepositoryReplicator.newInstance();
                replicator.replicateRepository(sourceRepository, targetRepository, true);

                assertHaveSameHistory(sourceRepository, targetRepository);

            } finally {
                sourceRepository.closeSession();
                targetRepository.closeSession();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void assertHaveSameHistory(SVNRepository sourceRepository, SVNRepository targetRepository) throws SVNException {
        final long sourceLatestRevision = sourceRepository.getLatestRevision();
        final long targetLatestRevision = targetRepository.getLatestRevision();

        Assert.assertEquals(sourceLatestRevision, targetLatestRevision);

        final Collection sourceLogEntries = sourceRepository.log(new String[]{""}, null, 0, sourceLatestRevision, true, true);
        final Collection targetLogEntries = targetRepository.log(new String[]{""}, null, 0, targetLatestRevision, true, true);

        Assert.assertEquals(sourceLogEntries.size(), targetLogEntries.size());

        final Iterator sourceIterator = sourceLogEntries.iterator();
        final Iterator targetIterator = targetLogEntries.iterator();

        for (int i = 0; i < sourceLogEntries.size(); i++) {
            Assert.assertEquals(sourceIterator.hasNext(), targetIterator.hasNext());

            final SVNLogEntry sourceLogEntry = (SVNLogEntry) sourceIterator.next();
            final SVNLogEntry targetLogEntry = (SVNLogEntry) targetIterator.next();

            if (sourceLogEntry == null) {
                Assert.assertNull(targetLogEntry);
            } else {
                Assert.assertTrue(sourceLogEntry.equals(targetLogEntry));
            }
        }
    }

    private String getTestName() {
        return "ReplicateTest";
    }
}
