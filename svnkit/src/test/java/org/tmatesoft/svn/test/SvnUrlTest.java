package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

public class SvnUrlTest {

    @Test
    public void testParseFileProtocol() throws Exception {
        testFileProtocolParsing("/path/to/repository", "file:///path/to/repository");
        testFileProtocolParsing("/path", "file:///path");
        testFileProtocolParsing("/", "file:///");
//        TODO: check expected path testFileProtocolParsing("/c:/path/to/repository", "file:///c:/path/to/repository");
    }

    @Test
    public void testRemovePathTail() throws Exception {
        testFileProtocolRemovePathTail("file:///path/to", "file:///path/to/repository");
        testFileProtocolRemovePathTail("file:///", "file:///path");
        testFileProtocolRemovePathTail("file:///", "file:///");
        testFileProtocolRemovePathTail("file:///c:/path/to", "file:///c:/path/to/repository");
    }

    private void testFileProtocolParsing(String expectedPath, String urlString) throws SVNException {
        final SVNURL url = SVNURL.parseURIEncoded(urlString);
        Assert.assertEquals(expectedPath, url.getPath());
        Assert.assertTrue(url.getHost() == null || url.getHost().equals(""));
        Assert.assertNull(url.getUserInfo());
    }

    private void testFileProtocolRemovePathTail(String urlWithRemovedPathTailString, String urlString) throws SVNException {
        final SVNURL url = SVNURL.parseURIEncoded(urlString);
        Assert.assertEquals(urlWithRemovedPathTailString, url.removePathTail().toString());
    }
}
