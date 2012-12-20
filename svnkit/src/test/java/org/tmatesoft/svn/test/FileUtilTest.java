package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

import java.io.File;

public class FileUtilTest {

    @Test
    public void testSymlinkSizeAndTimestamp() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(SVNFileUtil.symlinksSupported());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSymlinkSizeAndTimestamp", options);
        try {
            final File directory = sandbox.createDirectory("directory");
            final File link = new File(directory, "link");

            Assert.assertTrue(SVNFileUtil.createSymlink(link, "target"));
            final long currentTime = System.currentTimeMillis();

            Assert.assertEquals("target".getBytes().length, SVNFileUtil.getFileLength(link));
            Assert.assertTrue(Math.abs(SVNFileUtil.getFileLastModified(link) - currentTime) < 100000);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRegularFileSizeAndTimestamp() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRegularFileSizeAndTimestamp", options);
        try {
            final File directory = sandbox.createDirectory("directory");
            final File file = new File(directory, "file");

            TestUtil.writeFileContentsString(file, "contents");
            final long currentTime = System.currentTimeMillis();

            Assert.assertEquals("contents".getBytes().length, SVNFileUtil.getFileLength(file));
            Assert.assertEquals(file.length(), SVNFileUtil.getFileLength(file));

            final long lastModified = SVNFileUtil.getFileLastModified(file);
            Assert.assertTrue(Math.abs(lastModified - currentTime) < 100000);
            Assert.assertTrue(Math.abs(lastModified - file.lastModified()) < 100000);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "FileUtilTest";
    }
}
