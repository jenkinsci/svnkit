package org.tmatesoft.svn.test;

import junit.framework.Assert;

import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

public class SvnGetContentsTest {
    
    @Test
    public void testGetContents() throws SVNException {
        
        final TestOptions options = TestOptions.getInstance();

        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + ".testGetContents", options);
        final SVNURL url = sandbox.createSvnRepository();

        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addFile("source/file");
        commitBuilder.addDirectory("target");
        commitBuilder.commit();

        final SVNURL sourceUrl = url.appendPath("source", false);
        final SVNURL fileUrl = sourceUrl.appendPath("file", false);
        final SVNURL targetUrl = url.appendPath("target", false);
        final SVNURL newFileUrl = targetUrl.appendPath("file", false);
            
        
        SVNClientManager cm  = SVNClientManager.newInstance();
        SVNCopyClient cp = cm.getCopyClient();
        cp.doCopy(new SVNCopySource[] {new SVNCopySource(SVNRevision.HEAD, SVNRevision.HEAD, sourceUrl.appendPath("file", false))}, 
                targetUrl, true, false, false, "file moved", null);
        
        SVNWCClient wc = cm.getWCClient();
        wc.doGetFileContents(fileUrl, SVNRevision.create(1), SVNRevision.create(1), false, System.out);
        wc.doGetFileContents(newFileUrl, SVNRevision.create(2), SVNRevision.create(2), false, System.out);
        try {
            wc.doGetFileContents(fileUrl, SVNRevision.create(2), SVNRevision.create(1), false, System.out);
            Assert.fail();
        } catch (SVNException e) {
            
        }
        try {
            wc.doGetFileContents(fileUrl, SVNRevision.create(1), SVNRevision.create(2), false, System.out);
            Assert.fail();
        } catch (SVNException e) {
            
        }
            
    }

}
