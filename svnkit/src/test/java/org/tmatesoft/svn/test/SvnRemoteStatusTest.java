package org.tmatesoft.svn.test;

import java.util.ArrayList;
import java.util.Collection;

import junit.framework.Assert;

import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnRemoteStatusTest {
    
    @Test
    public void testRemoteUrl() throws SVNException {
        testRemoteUrlPresence(SvnWcGeneration.V16);
        testRemoteUrlPresence(SvnWcGeneration.V17);
    }

    private void testRemoteUrlPresence(SvnWcGeneration wcGeneration) throws SVNException {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
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
        WorkingCopy wc = sandbox.checkoutNewWorkingCopy(url, -1, wcGeneration);
        
        
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

}
