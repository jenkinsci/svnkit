package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.internal.io.fs.revprop.SVNFSFSPackedRevPropsManifest;

public class SVNFSFSPackedRevPropsManifestTest {

    @Test
    public void testBasics() throws Exception {
        final String manifestString =
                "1.0\n" +
                "2.0\n" +
                "3.0\n" +
                "4.0\n" +
                "5.0\n";
        final SVNFSFSPackedRevPropsManifest manifest = SVNFSFSPackedRevPropsManifest.fromString(20,manifestString);
        Assert.assertEquals(manifestString, manifest.asString());
        Assert.assertEquals(manifestString, SVNFSFSPackedRevPropsManifest.fromString(manifest.getFirstRevision(), manifestString).asString());
        Assert.assertEquals("1.0", manifest.getPackName(20));
        Assert.assertEquals("2.0", manifest.getPackName(21));
        Assert.assertEquals("3.0", manifest.getPackName(22));
        Assert.assertEquals("4.0", manifest.getPackName(23));
        Assert.assertEquals("5.0", manifest.getPackName(24));

        Assert.assertEquals(20, manifest.getFirstRevision());
        Assert.assertEquals(5, manifest.getRevisionsCount());
    }

    @Test
    public void testUpdatePackName() throws Exception {
        final String manifestString =
                "1.0\n" +
                "2.0\n" +
                "3.0\n" +
                "4.0\n" +
                "5.0\n";
        final SVNFSFSPackedRevPropsManifest manifest = SVNFSFSPackedRevPropsManifest.fromString(20,manifestString);
        final String newPackName = manifest.updatePackName(22, 2);
        Assert.assertEquals("22.1", newPackName);
        Assert.assertEquals("1.0", manifest.getPackName(20));
        Assert.assertEquals("2.0", manifest.getPackName(21));
        Assert.assertEquals("22.1", manifest.getPackName(22));
        Assert.assertEquals("22.1", manifest.getPackName(23));
        Assert.assertEquals("5.0", manifest.getPackName(24));
    }
}
