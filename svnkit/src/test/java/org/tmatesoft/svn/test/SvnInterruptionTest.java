package org.tmatesoft.svn.test;

import java.util.HashSet;
import java.util.Set;

import junit.framework.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnCleanup;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

public class SvnInterruptionTest {
    
    @Test
    public void testUpdateInterruptedOnReceive() throws SVNException {
        Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName(), TestOptions.getInstance());
        final SVNURL url = sandbox.createSvnRepository();

        CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addFile("dir/file", "contents1".getBytes());
        commitBuilder.addFile("dir/subdir/file", "contents2".getBytes());
        commitBuilder.addDirectory("dir/subdir/inner");
        SVNCommitInfo info = commitBuilder.commit();
        final long firstRev = info.getNewRevision();

        // two files changed, tree added
        commitBuilder = new CommitBuilder(url);
        commitBuilder.changeFile("dir/file", "changed".getBytes());
        commitBuilder.addFile("dir/file2", "contents1".getBytes());
        commitBuilder.addFile("dir/subdir/file2", "contents2".getBytes());
        commitBuilder.changeFile("dir/subdir/file", "changed".getBytes());
        commitBuilder.addFile("dir/subdir/inner/file3", "file3".getBytes());
        info = commitBuilder.commit();
        final long secondRev = info.getNewRevision();

        final WorkingCopy wc = sandbox.checkoutNewWorkingCopy(url, firstRev);

        SvnOperationFactory of = new SvnOperationFactory();
        
        // this interrupts update with non-empty wq leaving wc locked.
        of.setEventHandler(new ISVNEventHandler() {
            public void checkCancelled() throws SVNCancelException {
            }
            public void handleEvent(SVNEvent event, double progress) throws SVNException {
                if (event.getAction() == SVNEventAction.UPDATE_ADD) {
                    if (wc.getFile("dir/subdir/inner/file3").equals(event.getFile())) {
                        throw new NullPointerException("TEST EXCEPTION");
                    }
                }
            }
        });
        
        SvnUpdate up = of.createUpdate();
        up.setSingleTarget(SvnTarget.fromFile(wc.getWorkingCopyDirectory()));
        up.setRevision(SVNRevision.create(secondRev));

        try {
            up.run();
            Assert.fail();
        } catch (Throwable npe) {
            assertStacktraceContainsMessage("TEST EXCEPTION", npe);
        }
        
        SvnStatus st = wc.getStatus("");
        if (TestUtil.isNewWorkingCopyTest()) {
            Assert.assertTrue(st.isWcLocked());
        }
        Assert.assertEquals(SVNStatusType.STATUS_INCOMPLETE, st.getNodeStatus());
        
        SvnCleanup cup = of.createCleanup();
        cup.setSingleTarget(SvnTarget.fromFile(wc.getFile("")));
        cup.run();

        st = wc.getStatus("");
        Assert.assertFalse(st.isWcLocked());
        Assert.assertEquals(SVNStatusType.STATUS_INCOMPLETE, st.getNodeStatus());
        
        of.setEventHandler(null);
        up.run();
        
        st = wc.getStatus("");
        Assert.assertFalse(st.isWcLocked());
        Assert.assertEquals(SVNStatusType.STATUS_NORMAL, st.getNodeStatus());
    }

    private void assertStacktraceContainsMessage(String message, Throwable th) {
        final Set<Throwable> seen = new HashSet<Throwable>();
        boolean found = false;
        while (th != null && !seen.contains(th)) {
            if (message.equals(th.getMessage())) {
                found = true;
                break;
            }
            seen.add(th);
            th = th.getCause();
        }

        if (!found) {
            Assert.fail("Message \"" + message + "\" is not found in the stacktrace");
        }
    }
}
