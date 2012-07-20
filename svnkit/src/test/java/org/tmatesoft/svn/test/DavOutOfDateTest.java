package org.tmatesoft.svn.test;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

public class DavOutOfDateTest {

    private static final String TEST_PROPERTY_VALUE = "SHA1";
	private static final String TEST_PROPERTY = "subgit:commitId";


	@Test
    public void testAlwaysOutOfDate() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));
        Assume.assumeTrue(TestUtil.areAllSvnserveOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testAlwaysOutOfDate", options);
        final BasicAuthenticationManager authenticationManager = new BasicAuthenticationManager("user1", "password1");
        final Map<String, String> loginToPassword = new HashMap<String, String>();
        loginToPassword.put("user1", "password1");

        try {
            final SVNURL davURL = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);
            final SVNURL svnURL = sandbox.createSvnRepositoryWithSvnAccess(loginToPassword);
            final SVNURL fsfsURL = sandbox.createSvnRepository();

        	testOutOfDatedCommit(fsfsURL, authenticationManager);
        	testOutOfDatedCommit(svnURL, authenticationManager);
        	testOutOfDatedCommit(davURL, authenticationManager);
        } finally {        
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }
	
	private void testOutOfDatedCommit(SVNURL url, ISVNAuthenticationManager authenticationManager) throws SVNException {
		ISVNEditor editor = null;
        
		CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.setAuthenticationManager(authenticationManager);
        commitBuilder.setDirectoryProperty("", TEST_PROPERTY, SVNPropertyValue.create(TEST_PROPERTY_VALUE));
        commitBuilder.addDirectory("trunk");
        commitBuilder.addDirectory("trunk/a");
        commitBuilder.addFile("trunk/a/alpha", "alpha".getBytes());
        commitBuilder.addDirectory("trunk/b");
        commitBuilder.addFile("trunk/b/beta", "beta".getBytes());
        commitBuilder.commit();
        
        // start commit over revision 1.
        SVNRepository repository = SVNRepositoryFactory.create(url);
        repository.setAuthenticationManager(authenticationManager);
        SVNProperties properties = new SVNProperties();
        repository.getDir("", -1, properties, -1, (ISVNDirEntryHandler) null);
        Assert.assertEquals(SVNPropertyValue.getPropertyAsString(properties.getSVNPropertyValue(TEST_PROPERTY)), TEST_PROPERTY_VALUE);
        
        
        // import
        editor = repository.getCommitEditor("failed commit", null);
        editor.openRoot(1);
        
        editor.changeDirProperty(TEST_PROPERTY, SVNPropertyValue.create(TEST_PROPERTY_VALUE));

        editor.openDir("trunk", 1);
        editor.openDir("trunk/b", 1);
        editor.openFile("trunk/b/beta", 1);
        editor.applyTextDelta("trunk/b/beta", null);
        SVNDeltaGenerator generator = new SVNDeltaGenerator();
        final String textChecksum = generator.sendDelta("trunk/b/beta", new ByteArrayInputStream("failure".getBytes()), editor, true);
        editor.closeFile("trunk/b/beta", textChecksum);
        editor.closeDir();
        editor.closeDir();
        editor.closeDir();
        
        // commit file 'alpha' modification
        commitBuilder = new CommitBuilder(url);
        commitBuilder.setAuthenticationManager(authenticationManager);
        commitBuilder.changeFile("trunk/a/alpha", "sneaky".getBytes());
        commitBuilder.commit();

        try {
            ISVNEditor tmpEditor = editor;
            editor = null;
            // continue commit, should fail.
            tmpEditor.closeEdit();
            Assert.fail();
        } catch (SVNException e) {
        	Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.FS_CONFLICT);
        } 
        Assert.assertNull(editor);
	}


    public String getTestName() {
        return "DavOutOfDateTest";
    }
}
