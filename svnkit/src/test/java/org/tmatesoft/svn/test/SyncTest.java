package org.tmatesoft.svn.test;

import junit.framework.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc2.SvnLog;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.admin.SvnRepositoryCreate;

import java.io.File;
import java.util.Map;

public class SyncTest {

    @Test
    public void testSourceUrlContainsSpace() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSourceUrlContainsSpace", options);
        try {
            //prepare a repository with a space in the URL
            final File repositoryDirectory = sandbox.createDirectory("svn.repo with space");

            final SvnRepositoryCreate repositoryCreate = svnOperationFactory.createRepositoryCreate();
            repositoryCreate.setRepositoryRoot(repositoryDirectory);
            final SVNURL sourceUrl = repositoryCreate.run();
            final SVNURL targetUrl = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(sourceUrl);
            commitBuilder.addFile("file", "contents".getBytes());
            commitBuilder.commit();

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNAdminClient adminClient = clientManager.getAdminClient();
                adminClient.doInitialize(sourceUrl, targetUrl);

                Assert.assertEquals(sourceUrl.toString(), getPropertyString(targetUrl, SVNRevisionProperty.FROM_URL, 0));
                Assert.assertEquals(getUuid(sourceUrl), getPropertyString(targetUrl, SVNRevisionProperty.FROM_UUID, 0));
                Assert.assertEquals("0", getPropertyString(targetUrl, SVNRevisionProperty.LAST_MERGED_REVISION, 0));

                adminClient.doSynchronize(targetUrl);
            } finally {
                clientManager.dispose();
            }

            Assert.assertEquals(sourceUrl.toString(), getPropertyString(targetUrl, SVNRevisionProperty.FROM_URL, 0));
            Assert.assertEquals(getUuid(sourceUrl), getPropertyString(targetUrl, SVNRevisionProperty.FROM_UUID, 0));
            Assert.assertEquals("1", getPropertyString(targetUrl, SVNRevisionProperty.LAST_MERGED_REVISION, 0));
            Assert.assertEquals(1, getLatestRevision(targetUrl));

            final SvnLog log = svnOperationFactory.createLog();
            log.setSingleTarget(SvnTarget.fromURL(targetUrl));
            log.setDiscoverChangedPaths(true);
            log.addRange(SvnRevisionRange.create(SVNRevision.create(1), SVNRevision.HEAD));
            final SVNLogEntry logEntry = log.run();

            Assert.assertEquals(1, logEntry.getRevision());

            final Map<String,SVNLogEntryPath> changedPaths = logEntry.getChangedPaths();
            Assert.assertEquals(1, changedPaths.size());

            final SVNLogEntryPath logEntryPath = changedPaths.values().iterator().next();
            Assert.assertEquals("/file", logEntryPath.getPath());
            Assert.assertEquals(SVNNodeKind.FILE, logEntryPath.getKind());
            Assert.assertEquals('A', logEntryPath.getType());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private long getLatestRevision(SVNURL url) throws SVNException {
        final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
        try {
            return svnRepository.getLatestRevision();
        } finally {
            svnRepository.closeSession();
        }
    }

    private String getUuid(SVNURL url) throws SVNException {
        final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
        try {
            return svnRepository.getRepositoryUUID(true);
        } finally {
            svnRepository.closeSession();
        }
    }

    private String getPropertyString(SVNURL url, String propertyName, long revision) throws SVNException {
        final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
        try {
            final SVNPropertyValue propertyValue = svnRepository.getRevisionPropertyValue(revision, propertyName);
            return SVNPropertyValue.getPropertyAsString(propertyValue);
        } finally {
            svnRepository.closeSession();
        }
    }

    private String getTestName() {
        return "SyncTest";
    }
}
