package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.io.fs.revprop.SVNFSFSPackedRevProps;

import java.util.List;

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

    @Test
    public void testModification() throws Exception {
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
        final SVNProperties properties = new SVNProperties();
        properties.put(SVNRevisionProperty.AUTHOR, "anotherAuthor");
        final List<SVNFSFSPackedRevProps> packs = packedRevProps.setProperties(20, properties, 1024);

        Assert.assertEquals(1, packs.size());
        final SVNFSFSPackedRevProps modifiedPackedRevProps = packs.get(0);

        final SVNProperties parsedProperties = modifiedPackedRevProps.parseProperties(20);
        Assert.assertEquals(parsedProperties, properties);
    }

    @Test
    public void testModificationWithSplitIntoTwo() throws Exception {
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
        final SVNProperties properties = new SVNProperties();
        properties.put(SVNRevisionProperty.AUTHOR, "anotherAuthor");
        final List<SVNFSFSPackedRevProps> packs = packedRevProps.setProperties(20, properties, 64);

        Assert.assertEquals(2, packs.size());
        final SVNFSFSPackedRevProps pack1 = packs.get(0);
        final SVNFSFSPackedRevProps pack2 = packs.get(1);

        Assert.assertEquals(pack1.parseProperties(20), packedRevProps.parseProperties(20));
        Assert.assertEquals(pack2.parseProperties(21), packedRevProps.parseProperties(21));
    }

    @Test
    public void testModificationWithSplitIntoThree() throws Exception {
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
        builder.addByteArrayEntry(("K 8\n" +
                "property\n" +
                "V 5\n" +
                "value\n" +
                "END\n").getBytes());
        final SVNFSFSPackedRevProps packedRevProps = builder.build();
        final SVNProperties properties = new SVNProperties();
        properties.put(SVNRevisionProperty.AUTHOR, repeat("anotherAuthor", 10));
        final List<SVNFSFSPackedRevProps> packs = packedRevProps.setProperties(21, properties, 64);

        Assert.assertEquals(3, packs.size());
        final SVNFSFSPackedRevProps pack1 = packs.get(0);
        final SVNFSFSPackedRevProps pack2 = packs.get(1);
        final SVNFSFSPackedRevProps pack3 = packs.get(2);

        Assert.assertEquals(pack1.parseProperties(20), packedRevProps.parseProperties(20));
        Assert.assertEquals(pack2.parseProperties(21), packedRevProps.parseProperties(21));
        Assert.assertEquals(pack3.parseProperties(22), packedRevProps.parseProperties(22));
    }

    private String repeat(String s, int times) {
        final StringBuilder stringBuilder = new StringBuilder();
        while (times > 0) {
            times--;
            stringBuilder.append(s);
        }
        return stringBuilder.toString();
    }
}
