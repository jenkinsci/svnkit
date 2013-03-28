package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.io.fs.revprop.SVNFSFSPackedRevProps;

public class SVNFSFSPackedRevPropsTest {

    @Test
    public void testBasics() throws Exception {
        final SVNFSFSPackedRevProps.Builder builder = new SVNFSFSPackedRevProps.Builder();
        builder.setFirstRevision(20);
        builder.addByteArrayEntry(("K 10\n" +
                "svn:author\n" +
                "V 8\n" +
                "username\n" +
                "K 8\n" +
                "svn:date\n" +
                "V 27\n" +
                "2013-03-28T14:29:46.951805Z\n" +
                "K 7\n" +
                "svn:log\n" +
                "V 7\n" +
                "message\n" +
                "END\n").getBytes());
        builder.addByteArrayEntry(("K 8\n" +
                "property\n" +
                "V 5\n" +
                "value\n" +
                "END\n").getBytes());
        final SVNFSFSPackedRevProps packedRevProps = builder.build();

        Assert.assertEquals(20, packedRevProps.getFirstRevision());
        Assert.assertEquals(2, packedRevProps.getRevisionsCount());

        final SVNProperties r20properties = packedRevProps.parseProperties(20);
        Assert.assertEquals("username", r20properties.getStringValue("svn:author"));
        Assert.assertEquals("2013-03-28T14:29:46.951805Z", r20properties.getStringValue("svn:date"));
        Assert.assertEquals("message", r20properties.getStringValue("svn:log"));

        SVNProperties r21properties = packedRevProps.parseProperties(21);
        Assert.assertEquals("value", r21properties.getStringValue("property"));

        final byte[] compressed1 = packedRevProps.asCompressedLevelNoneByteArray();
        final byte[] compressed2 = SVNFSFSPackedRevProps.fromCompressedByteArray(compressed1)
                .asCompressedLevelNoneByteArray(); //decompress and compress again

        Assert.assertArrayEquals(compressed1, compressed2);
    }
}
