package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteSetProperty;
import org.tmatesoft.svn.core.wc2.SvnSetProperty;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.util.Collection;

public class LogTest {

    @Test
    public void testLogEntryContainsRevisionProperties() throws Exception {
        //SVNKIT-60
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLogEntryContainsRevisionProperties", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("file");
            commitBuilder2.commit();

            final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
            try {
                svnRepository.setRevisionPropertyValue(1, "propertyName", SVNPropertyValue.create("propertyValue1"));
                svnRepository.setRevisionPropertyValue(2, "propertyName", SVNPropertyValue.create("propertyValue2"));

                final Collection logEntries1 = svnRepository.log(new String[]{""}, null, 1, 1, true, true);
                final Collection logEntries2 = svnRepository.log(new String[]{""}, null, 2, 2, true, true);

                final SVNLogEntry logEntry1 = (SVNLogEntry) logEntries1.iterator().next();
                final SVNLogEntry logEntry2 = (SVNLogEntry) logEntries2.iterator().next();

                final SVNProperties revisionProperties1 = logEntry1.getRevisionProperties();
                final SVNProperties revisionProperties2 = logEntry2.getRevisionProperties();
                Assert.assertEquals("propertyValue1", SVNPropertyValue.getPropertyAsString(revisionProperties1.getSVNPropertyValue("propertyName")));
                Assert.assertEquals("propertyValue2", SVNPropertyValue.getPropertyAsString(revisionProperties2.getSVNPropertyValue("propertyName")));
            } finally {
                svnRepository.closeSession();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
