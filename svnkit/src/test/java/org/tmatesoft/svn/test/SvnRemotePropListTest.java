package org.tmatesoft.svn.test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class SvnRemotePropListTest {

    @Test
    public void testRemotePropList() throws SVNException {
        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + ".testRemotePropList", TestOptions.getInstance());
        final SVNURL url = sandbox.createSvnRepository();

        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addFile("source/file");
        commitBuilder.setFileProperty("source/file", "name", SVNPropertyValue.create("value"));
        commitBuilder.commit();
        
        final WorkingCopy wc = sandbox.checkoutNewWorkingCopy(url);
        SVNClientManager cm = SVNClientManager.newInstance();
        final File path = wc.getFile("source/file");
        final Map<SVNURL, SVNPropertyData> props = new HashMap<SVNURL, SVNPropertyData>();
        cm.getWCClient().doGetProperty(path, null, SVNRevision.UNDEFINED, SVNRevision.create(1), SVNDepth.INFINITY, 
                new ISVNPropertyHandler() {                    
                    public void handleProperty(long revision, SVNPropertyData property) throws SVNException {
                    }                    
                    public void handleProperty(SVNURL url, SVNPropertyData property) throws SVNException {
                        props.put(url, property);
                    }                    
                    public void handleProperty(File path, SVNPropertyData property) throws SVNException {
                    }
                }, null);
        
        Assert.assertEquals(1, props.size());
        Assert.assertNotNull(props.get(url.appendPath("source/file", false)));
    }
}
