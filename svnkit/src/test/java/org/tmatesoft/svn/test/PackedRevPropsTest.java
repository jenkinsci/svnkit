package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSFile;
import org.tmatesoft.svn.core.internal.io.fs.FSPacker;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

import java.io.File;

public class PackedRevPropsTest {

    @Test
    public void testPackFSFSRepository() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPackFSFSRepository", options);
        try {
            final File repositoryRoot = sandbox.createDirectory("svn.repo");
            SVNURL url = SVNRepositoryFactory.createLocalRepository(repositoryRoot, null, true,
                    false, false, false, false, false, true);

            updateMaxFilesPerDirectory(repositoryRoot);

            for (int i = 0; i < 20; i++) {
                createCommitThatAddsFile(url, "file" + i);
            }
            final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
            try {
                for (int i = 0; i <= 10; i++) {
                    svnRepository.setRevisionPropertyValue(i, "test" + i, SVNPropertyValue.create("value" + i));
                }
                final FSFS fsfs = new FSFS(repositoryRoot);
                fsfs.open();
                new FSPacker(null).pack(fsfs);
                fsfs.close();

                for (int i = 0; i <= 10; i++) {
                    final SVNPropertyValue propertyValue = svnRepository.getRevisionPropertyValue(i, "test" + i);
                    Assert.assertEquals("value" + i, SVNPropertyValue.getPropertyAsString(propertyValue));
                }
            } finally {
                svnRepository.closeSession();
            }

        } finally {
            sandbox.dispose();
        }
    }

    @Test
    public void testPackAndCompressFSFSRepository() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPackAndCompressFSFSRepository", options);
        try {
            final File repositoryRoot = sandbox.createDirectory("svn.repo");
            SVNURL url = SVNRepositoryFactory.createLocalRepository(repositoryRoot, null, true,
                    false, false, false, false, false, true);

            updateCompressedFlag(repositoryRoot, true);
            updateMaxFilesPerDirectory(repositoryRoot);

            for (int i = 0; i < 20; i++) {
                createCommitThatAddsFile(url, "file" + i);
            }
            final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
            try {
                for (int i = 0; i <= 10; i++) {
                    svnRepository.setRevisionPropertyValue(i, "test" + i, SVNPropertyValue.create("value" + i));
                }
                final FSFS fsfs = new FSFS(repositoryRoot);
                fsfs.open();
                new FSPacker(null).pack(fsfs);
                fsfs.close();

                for (int i = 0; i <= 10; i++) {
                    final SVNPropertyValue propertyValue = svnRepository.getRevisionPropertyValue(i, "test" + i);
                    Assert.assertEquals("value" + i, SVNPropertyValue.getPropertyAsString(propertyValue));
                }
                for (int i = 11; i <= 20; i++) {
                    svnRepository.setRevisionPropertyValue(i, "test" + i, SVNPropertyValue.create("value" + i));
                    final SVNPropertyValue propertyValue = svnRepository.getRevisionPropertyValue(i, "test" + i);
                    Assert.assertEquals("value" + i, SVNPropertyValue.getPropertyAsString(propertyValue));
                }
            } finally {
                svnRepository.closeSession();
            }

        } finally {
            sandbox.dispose();
        }
    }

    private void updateCompressedFlag(File repositoryRoot, boolean compressed) throws SVNException {
        final FSFS fsfs = new FSFS(repositoryRoot);
        fsfs.open();
        final File configFile = fsfs.getConfigFile();
        SVNConfigFile config = new SVNConfigFile(configFile);
        config.setPropertyValue(FSFS.PACKED_REVPROPS_SECTION, FSFS.COMPRESS_PACKED_REVPROPS_OPTION, String.valueOf(compressed), true);
        fsfs.close();
    }

    private void updateMaxFilesPerDirectory(File repositoryRoot) throws SVNException {
        final FSFS fsfs = new FSFS(repositoryRoot);
        fsfs.open();
        fsfs.writeDBFormat(fsfs.getDBFormat(), 10, true);
        fsfs.close();
    }

    private void createCommitThatAddsFile(SVNURL url, String filename) throws SVNException {
        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addFile(filename);
        commitBuilder.commit();
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
