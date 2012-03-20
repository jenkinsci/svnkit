package org.tmatesoft.svn.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

public class DumpNodeReplacementTest {

    @Before
    public void setup() {
        Assume.assumeTrue(TestUtil.isNewWorkingCopyTest());
    }

    @Test
    public void testReplaceFileByDirectory() throws SVNException {
        final Sandbox sandbox = Sandbox.createWithCleanup("replaceFileByDirectory", TestOptions.getInstance());
        try {
            final SVNURL url = sandbox.createSvnRepository();
            initWithFile(url);
            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, -1);
            final long revision = replaceByDirectory(workingCopy);
            final File repositoryRoot = dump(url);
            assertValidReplaceKind(repositoryRoot, revision, SVNNodeKind.DIR);
        } finally {
            sandbox.dispose();
        }
    }

    @Test
    public void testReplaceDirectoryByFile() throws SVNException {
        final Sandbox sandbox = Sandbox.createWithCleanup("replaceDirectoryByFile", TestOptions.getInstance());
        try {
            final SVNURL url = sandbox.createSvnRepository();
            initWithDirectory(url);
            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, -1);
            final long revision = replaceByFile(workingCopy);
            final File repositoryRoot = dump(url);
            assertValidReplaceKind(repositoryRoot, revision, SVNNodeKind.FILE);
        } finally {
            sandbox.dispose();
        }
    }

    @Test
    public void testReplaceFileByFile() throws SVNException {
        final Sandbox sandbox = Sandbox.createWithCleanup("replaceFileByFile", TestOptions.getInstance());
        try {
            final SVNURL url = sandbox.createSvnRepository();
            initWithFile(url);
            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, -1);
            final long revision = replaceByFile(workingCopy);
            final File repositoryRoot = dump(url);
            assertValidReplaceKind(repositoryRoot, revision, SVNNodeKind.FILE);
        } finally {
            sandbox.dispose();
        }
    }

    @Test
    public void testReplaceDirectoryByDirectory() throws SVNException {
        final Sandbox sandbox = Sandbox.createWithCleanup("replaceDirectoryByDirectory", TestOptions.getInstance());
        try {
            final SVNURL url = sandbox.createSvnRepository();
            initWithDirectory(url);
            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, -1);
            final long revision = replaceByDirectory(workingCopy);
            final File repositoryRoot = dump(url);
            assertValidReplaceKind(repositoryRoot, revision, SVNNodeKind.DIR);
        } finally {
            sandbox.dispose();
        }
    }

    private void initWithFile(SVNURL url) throws SVNException {
        final CommitBuilder initialCommitBuilder = new CommitBuilder(url);
        initialCommitBuilder.addDirectory("trunk");
        initialCommitBuilder.addFile("trunk/node", "This is trunk/node".getBytes());
        initialCommitBuilder.setCommitMessage("Added file");
        initialCommitBuilder.commit();
    }

    private void initWithDirectory(SVNURL url) throws SVNException {
        final CommitBuilder initialCommitBuilder = new CommitBuilder(url);
        initialCommitBuilder.addDirectory("trunk");
        initialCommitBuilder.addDirectory("trunk/node");
        initialCommitBuilder.addFile("trunk/node/file", "This is trunk/node/file".getBytes());
        initialCommitBuilder.setCommitMessage("Added directory");
        initialCommitBuilder.commit();
    }

    private long replaceByFile(WorkingCopy workingCopy) throws SVNException {
        final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

        final File trunk = new File(workingCopyDirectory, "trunk");
        final File node = new File(trunk, "node");
        SVNFileUtil.deleteAll(node, true);
        workingCopy.delete(node);

        SVNFileUtil.createFile(node, "This is trunk/node", "UTF-8");
        workingCopy.add(node);

        return workingCopy.commit("Replaced by file");
    }

    private long replaceByDirectory(WorkingCopy workingCopy) throws SVNException {
        final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

        final File trunk = new File(workingCopyDirectory, "trunk");
        final File node = new File(trunk, "node");
        SVNFileUtil.deleteAll(node, true);
        workingCopy.delete(node);

        node.mkdir();
        workingCopy.add(node);
        final File child = new File(node, "file");
        SVNFileUtil.createFile(child, "This is trunk/node/file", "UTF-8");
        workingCopy.add(child);

        return workingCopy.commit("Replaced by directory");
    }

    private File dump(SVNURL url) throws SVNException {
        final File repositoryRoot = new File(url.getPath());
        final SVNAdminClient adminClient = SVNClientManager.newInstance().getAdminClient();
        adminClient.doDump(repositoryRoot, SVNDebugLog.getDefaultLog().createOutputLogStream(), SVNRevision.create(0), SVNRevision.HEAD, false, false);
        return repositoryRoot;
    }

    private void assertValidReplaceKind(File repositoryRoot, long revision, SVNNodeKind kind) throws SVNException {
        final CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
        decoder.onMalformedInput(CodingErrorAction.IGNORE);
        decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);

        final File revisionNodeFile = new File(repositoryRoot, "db/revs/0/" + revision);
        final InputStream in = SVNFileUtil.openFileForReading(revisionNodeFile);
        final StringBuffer buffer = new StringBuffer();
        final String replaceString = "replace-" + kind;
        boolean replaceLineFound = false;
        try {
            while (true) {
                final String line = SVNFileUtil.readLineFromStream(in, buffer, decoder);
                if (line == null) {
                    break;
                }
                buffer.setLength(0);
                if (line.indexOf(replaceString) >= 0) {
                    replaceLineFound = true;
                    break;
                }
            }
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, e), e, SVNLogType.CLIENT);
        } finally {
            SVNFileUtil.closeFile(in);
        }
        Assert.assertTrue("Could not find 'replace-" + kind + "' string in revision node file", replaceLineFound);
    }
}
