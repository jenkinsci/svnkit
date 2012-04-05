package org.tmatesoft.svn.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import junit.framework.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnRemoteStatusTest {

    @Before
    public void setup() {
        Assume.assumeTrue(!TestUtil.isNewWorkingCopyOnly());
    }
    
    @Test
    public void testRemoteUrlIsNotNull() throws SVNException {
        testRemoteUrlPresence(SvnWcGeneration.V16);
        testRemoteUrlPresence(SvnWcGeneration.V17);
    }
    
    @Test
    public void testOldAndNewStatusValues() throws SVNException { 
        Map<String, SVNStatus> newStatuses = collectStatuses(SvnWcGeneration.V17);
        Map<String, SVNStatus> oldStatuses = collectStatuses(SvnWcGeneration.V16);
        
        Assert.assertEquals(newStatuses.size(), oldStatuses.size());
        for (Iterator<String> sts = newStatuses.keySet().iterator(); sts.hasNext();) {
            String path = sts.next();
            SVNStatus newStatus = newStatuses.get(path);
            SVNStatus oldStatus = oldStatuses.get(path);
           
            Assert.assertNotNull(oldStatus);
            compare(newStatus, oldStatus);
        }
    }

    private void compare(SVNStatus newStatus, SVNStatus oldStatus) {
        Assert.assertEquals(oldStatus.getRemoteRevision(), newStatus.getRemoteRevision());
        Assert.assertEquals(oldStatus.getRevision(), newStatus.getRevision());
        Assert.assertEquals(oldStatus.getDepth(), newStatus.getDepth());
        Assert.assertEquals(oldStatus.getCopyFromRevision(), newStatus.getCopyFromRevision());
        Assert.assertEquals(oldStatus.getCopyFromURL(), newStatus.getCopyFromURL());
    }

    private void testRemoteUrlPresence(SvnWcGeneration wcGeneration) throws SVNException {
        final TestOptions options = TestOptions.getInstance();

        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + "." + wcGeneration, options);
        final SVNURL url = sandbox.createSvnRepository();

        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addDirectory("trunk");
        commitBuilder.addFile("trunk/remotelyChanged.txt");
        commitBuilder.addDirectory("trunk/remotelyChanged");
        commitBuilder.addFile("trunk/remotelyDeleted.txt");
        commitBuilder.addDirectory("trunk/remotelyDeleted");
        commitBuilder.addFile("trunk/remotelyReplaced.txt");
        commitBuilder.addDirectory("trunk/remotelyReplaced");
        commitBuilder.commit();

        // move
        WorkingCopy wc = sandbox.checkoutNewWorkingCopy(url, -1, true, wcGeneration);
        final SvnOperationFactory svnOperationFactory = wc.getOperationFactory();
        
        // make changes
        final CommitBuilder remoteChange = new CommitBuilder(url);
        remoteChange.addFile("trunk/remotelyAdded.txt");
        remoteChange.addDirectory("trunk/remotelyAdded");
        remoteChange.delete("trunk/remotelyDeleted");
        remoteChange.delete("trunk/remotelyDeleted.txt");
        
        remoteChange.delete("trunk/remotelyReplaced");
        remoteChange.delete("trunk/remotelyReplaced.txt");
        
        remoteChange.addFile("trunk/remotelyReplaced.txt");
        remoteChange.addDirectory("trunk/remotelyReplaced");
        
        remoteChange.changeFile("trunk/remotelyChanged.txt", "change".getBytes());
        remoteChange.commit();
        
        SvnGetStatus st = svnOperationFactory.createGetStatus();
        st.setSingleTarget(SvnTarget.fromFile(wc.getWorkingCopyDirectory()));
        st.setRemote(true);
        Collection<SvnStatus> statuses = new ArrayList<SvnStatus>();
        st.run(statuses);
        
        for (SvnStatus status : statuses) {
            String remotePath = status.getRepositoryRelativePath();
            SVNURL repositoryRoot = status.getRepositoryRootUrl();

            Assert.assertNotNull(repositoryRoot);
            Assert.assertNotNull(remotePath);
        }
    }

    private Map<String, SVNStatus> collectStatuses(SvnWcGeneration wcGeneration) throws SVNException {
        final TestOptions options = TestOptions.getInstance();

        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName() + "." + wcGeneration, options);
        final SVNURL url = sandbox.createSvnRepository();

        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addDirectory("trunk");
        commitBuilder.addFile("trunk/remotelyChanged.txt");
        commitBuilder.addDirectory("trunk/remotelyChanged");
        commitBuilder.addFile("trunk/remotelyDeleted.txt");
        commitBuilder.addDirectory("trunk/remotelyDeleted");
        commitBuilder.addFile("trunk/remotelyReplaced.txt");
        commitBuilder.addDirectory("trunk/remotelyReplaced");
        commitBuilder.commit();

        // move
        WorkingCopy wc = sandbox.checkoutNewWorkingCopy(url, -1, true, wcGeneration);
        
        // make changes
        final CommitBuilder remoteChange = new CommitBuilder(url);
        remoteChange.addFile("trunk/remotelyAdded.txt");
        remoteChange.addDirectory("trunk/remotelyAdded");
        remoteChange.delete("trunk/remotelyDeleted");
        remoteChange.delete("trunk/remotelyDeleted.txt");
        
        remoteChange.delete("trunk/remotelyReplaced");
        remoteChange.delete("trunk/remotelyReplaced.txt");
        
        remoteChange.addFile("trunk/remotelyReplaced.txt");
        remoteChange.addDirectory("trunk/remotelyReplaced");
        
        remoteChange.changeFile("trunk/remotelyChanged.txt", "change".getBytes());
        remoteChange.commit();
        
        final Map<String, SVNStatus> result = new HashMap<String, SVNStatus>();
        SVNStatusClient stClient = SVNClientManager.newInstance().getStatusClient();
        stClient.doStatus(wc.getWorkingCopyDirectory(), SVNRevision.WORKING, SVNDepth.INFINITY, true, true, true, false, new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) throws SVNException {                
                result.put(status.getRepositoryRelativePath(), status);
            }
        }, null);
        return result;
    }
}
