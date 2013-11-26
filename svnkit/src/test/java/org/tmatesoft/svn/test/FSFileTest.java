package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSFile;

import java.io.File;

public class FSFileTest {

    @Test
    public void testParseFile() throws Exception {
        final String content =
                "K 10\n" +
                        "svn:author\n" +
                        "V 8\n" +
                        "username\n" +
                        "K 8\n" +
                        "svn:date\n" +
                        "V 27\n" +
                        "2013-03-27T14:47:31.324233Z\n" +
                        "K 7\n" +
                        "svn:log\n" +
                        "V 7\n" +
                        "Trunk.\n" +
                        "\n" +
                        "END";

        final TestOptions options = TestOptions.getInstance();

        FSFile file = null;
        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + ".testParseFile", options);
        try {
            final File directory = sandbox.createDirectory("directory");
            final File fsFile = new File(directory, "fsfile");
            TestUtil.writeFileContentsString(fsFile, content);


            file = new FSFile(fsFile);
            final SVNProperties properties = file.readProperties(true, true);

            Assert.assertEquals("Trunk.\n", properties.getStringValue(SVNRevisionProperty.LOG));
            Assert.assertEquals("username", properties.getStringValue(SVNRevisionProperty.AUTHOR));
            Assert.assertEquals("2013-03-27T14:47:31.324233Z", properties.getStringValue(SVNRevisionProperty.DATE));
        } finally {
            if (file != null) {
                file.close();
            }
            sandbox.dispose();
        }
    }

    @Test
    public void testParseByteArray() throws Exception {
        final String content =
                "K 10\n" +
                        "svn:author\n" +
                        "V 8\n" +
                        "username\n" +
                        "K 8\n" +
                        "svn:date\n" +
                        "V 27\n" +
                        "2013-03-27T14:47:31.324233Z\n" +
                        "K 7\n" +
                        "svn:log\n" +
                        "V 7\n" +
                        "Trunk.\n" +
                        "\n" +
                        "END";

        final TestOptions options = TestOptions.getInstance();

        final FSFile file = new FSFile(content.getBytes());
        try {
            final SVNProperties properties = file.readProperties(true, true);

            Assert.assertEquals("Trunk.\n", properties.getStringValue(SVNRevisionProperty.LOG));
            Assert.assertEquals("username", properties.getStringValue(SVNRevisionProperty.AUTHOR));
            Assert.assertEquals("2013-03-27T14:47:31.324233Z", properties.getStringValue(SVNRevisionProperty.DATE));
        } finally {
            file.close();
        }
    }
}
