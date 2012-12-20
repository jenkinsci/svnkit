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
    public void testRemovePathTailFileProtocol() throws Exception {
        testRemovePathTail("file:///path/to", "file:///path/to/repository");
        testRemovePathTail("file:///", "file:///path");
        testRemovePathTail("file:///", "file:///");
        testRemovePathTail("file:///c:/path/to", "file:///c:/path/to/repository");
    }

    @Test
    public void testParseSvnProtocol() throws Exception {
        testRemoteProtocolParsing("", "host", 3690, null, "svn://host");
        testRemoteProtocolParsing("/path", "host", 3690, null, "svn://host/path");
        testRemoteProtocolParsing("/path/to", "host", 3690, null, "svn://host/path/to");
        testRemoteProtocolParsing("/path/to/repository", "host", 3690, null, "svn://host/path/to/repository");
        testRemoteProtocolParsing("/path/to/repository", "host", 1234, null, "svn://host:1234/path/to/repository");

        testRemoteProtocolParsing("", "host", 3690, "user", "svn://user@host");
        testRemoteProtocolParsing("/path", "host", 3690, "user", "svn://user@host/path");
        testRemoteProtocolParsing("/path/to", "host", 3690, "user", "svn://user@host/path/to");
        testRemoteProtocolParsing("/path/to/repository", "host", 3690, "user", "svn://user@host/path/to/repository");
        testRemoteProtocolParsing("/path/to/repository", "host", 1234, "user", "svn://user@host:1234/path/to/repository");

        testRemoteProtocolParsing("", "host", 3690, "user:password", "svn://user:password@host");
        testRemoteProtocolParsing("/path", "host", 3690, "user:password", "svn://user:password@host/path");
        testRemoteProtocolParsing("/path/to", "host", 3690, "user:password", "svn://user:password@host/path/to");
        testRemoteProtocolParsing("/path/to/repository", "host", 3690, "user:password", "svn://user:password@host/path/to/repository");
        testRemoteProtocolParsing("/path/to/repository", "host", 1234, "user:password", "svn://user:password@host:1234/path/to/repository");
    }

    @Test
    public void testRemovePathTailSvnProtocol() throws Exception {
        testRemovePathTail("svn://host", "svn://host");
        testRemovePathTail("svn://host:1234", "svn://host:1234");
        testRemovePathTail("svn://host", "svn://host/path");
        testRemovePathTail("svn://host:1234", "svn://host:1234/path");
        testRemovePathTail("svn://host:1234/path", "svn://host:1234/path/to");
        testRemovePathTail("svn://host:1234/path/to", "svn://host:1234/path/to/repository");

        testRemovePathTail("svn://user:password@host", "svn://user:password@host");
        testRemovePathTail("svn://user:password@host:1234", "svn://user:password@host:1234");
        testRemovePathTail("svn://user:password@host", "svn://user:password@host/path");
        testRemovePathTail("svn://user:password@host:1234", "svn://user:password@host:1234/path");
        testRemovePathTail("svn://user:password@host:1234/path", "svn://user:password@host:1234/path/to");
        testRemovePathTail("svn://user:password@host:1234/path/to", "svn://user:password@host:1234/path/to/repository");
    }

    @Test
    public void testParseHttpProtocol() throws Exception {
        testRemoteProtocolParsing("", "host", 80, null, "http://host");
        testRemoteProtocolParsing("/path", "host", 80, null, "http://host/path");
        testRemoteProtocolParsing("/path/to", "host", 80, null, "http://host/path/to");
        testRemoteProtocolParsing("/path/to/repository", "host", 80, null, "http://host/path/to/repository");
        testRemoteProtocolParsing("/path/to/repository", "host", 1234, null, "http://host:1234/path/to/repository");

        testRemoteProtocolParsing("", "host", 80, "user", "http://user@host");
        testRemoteProtocolParsing("/path", "host", 80, "user", "http://user@host/path");
        testRemoteProtocolParsing("/path/to", "host", 80, "user", "http://user@host/path/to");
        testRemoteProtocolParsing("/path/to/repository", "host", 80, "user", "http://user@host/path/to/repository");
        testRemoteProtocolParsing("/path/to/repository", "host", 1234, "user", "http://user@host:1234/path/to/repository");

        testRemoteProtocolParsing("", "host", 80, "user:password", "http://user:password@host");
        testRemoteProtocolParsing("/path", "host", 80, "user:password", "http://user:password@host/path");
        testRemoteProtocolParsing("/path/to", "host", 80, "user:password", "http://user:password@host/path/to");
        testRemoteProtocolParsing("/path/to/repository", "host", 80, "user:password", "http://user:password@host/path/to/repository");
        testRemoteProtocolParsing("/path/to/repository", "host", 1234, "user:password", "http://user:password@host:1234/path/to/repository");
    }

    @Test
    public void testRemovePathTailHttpProtocol() throws Exception {
        testRemovePathTail("http://host", "http://host");
        testRemovePathTail("http://host:1234", "http://host:1234");
        testRemovePathTail("http://host", "http://host/path");
        testRemovePathTail("http://host:1234", "http://host:1234/path");
        testRemovePathTail("http://host:1234/path", "http://host:1234/path/to");
        testRemovePathTail("http://host:1234/path/to", "http://host:1234/path/to/repository");

        testRemovePathTail("http://user:password@host", "http://user:password@host");
        testRemovePathTail("http://user:password@host:1234", "http://user:password@host:1234");
        testRemovePathTail("http://user:password@host", "http://user:password@host/path");
        testRemovePathTail("http://user:password@host:1234", "http://user:password@host:1234/path");
        testRemovePathTail("http://user:password@host:1234/path", "http://user:password@host:1234/path/to");
        testRemovePathTail("http://user:password@host:1234/path/to", "http://user:password@host:1234/path/to/repository");
    }

    private void testFileProtocolParsing(String expectedPath, String urlString) throws SVNException {
        final SVNURL url = SVNURL.parseURIEncoded(urlString);
        Assert.assertEquals(expectedPath, url.getPath());
        Assert.assertTrue(url.getHost() == null || url.getHost().equals(""));
        Assert.assertNull(url.getUserInfo());
    }

    private void testRemovePathTail(String urlWithRemovedPathTailString, String urlString) throws SVNException {
        final SVNURL url = SVNURL.parseURIEncoded(urlString);
        Assert.assertEquals(urlWithRemovedPathTailString, url.removePathTail().toString());
    }

    private void testRemoteProtocolParsing(String expectedPath, String expectedHost, int expectedPort, String expectedUserInfo, String urlString) throws SVNException {
        final SVNURL url = SVNURL.parseURIEncoded(urlString);
        Assert.assertEquals(expectedPath, url.getPath());
        Assert.assertEquals(expectedHost, url.getHost());
        Assert.assertEquals(expectedPort, url.getPort());
        Assert.assertEquals(expectedUserInfo, url.getUserInfo());
    }
}
