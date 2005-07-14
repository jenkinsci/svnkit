/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.test;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.internal.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.internal.ws.fs.SVNRAFileData;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNLogEntry;
import org.tmatesoft.svn.core.io.SVNLogEntryPath;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;


/**
 * @author TMate Software Ltd.
 *
 */
public class TestSVNRepository extends AbstractRepositoryTest {

    public TestSVNRepository(String url, String methodName) {
        super(url, methodName);
    }
    
    public void testSVNRepository() throws Throwable {
        doTest();
    }

    public void testSubSVNRepository() throws Throwable {
        // modify getRepository() to return sub-repository
        // and getFixtureRoot() to return corresponding subdir.
        setRoot("directory");
        
        doTest();
    }
    
    public void doTest() throws Throwable {
        doTestRoot();
        doTestLastRevision();
        doTestDatedRevision();
        doTestFixture();
        doTestMissingNodeKind();
        doTestLog();
        doTestFileRevisions();
        doTestUpdate();
        doTestStatus();
        doTestLocations();
        doTestProperties();
        
        doTestCommit();
    }
    
    public void doTestRoot() throws Throwable {
        getRepository().testConnection();
        assertNotNull(getRepository().getRepositoryRoot());
        assertTrue(!getRepository().getRepositoryRoot().endsWith("/"));
        assertTrue(getRepository().getRepositoryRoot().startsWith("/"));        
    }

	public void doTestLastRevision() throws Throwable {
        assertEquals(getRepository().getLatestRevision(), 1);
	}

    public void doTestDatedRevision() throws Throwable {
        try {
            Thread.sleep(2000, 0);
        } catch (InterruptedException e) {}
        Date now = new Date(System.currentTimeMillis());
        long revision = getRepository().getDatedRevision(now);
        long latest = getRepository().getLatestRevision();
        assertEquals(latest, revision);
        assertEquals(revision, 1);
    }
    
    public void doTestFixture() throws Throwable {
        visitFixture(new IFileVisitor() {
            public void visitDirectory(File dir, String relativePath) throws Throwable {
                // here getDir is tested
                SVNNodeKind kind = getRepository().checkPath(relativePath, -1);
                assertEquals(kind, SVNNodeKind.DIR);
                File[] children = dir.listFiles();
                Map childrenMap = new HashMap();
                for(int i = 0; i < children.length; i++) {
                    childrenMap.put(children[i].getName(), children[i]);
                }
                assertNotNull(children);

                Collection entriesList = getRepository().getDir(relativePath, -2, null, (Collection) null);
                
                assertNotNull(entriesList);                
                assertEquals(children.length, entriesList.size());
                for(Iterator entries = entriesList.iterator(); entries.hasNext();) {
                    SVNDirEntry entry = (SVNDirEntry) entries.next();
                    assertNotNull(entry);
                    File file = (File) childrenMap.get(entry.getName());
                    assertNotNull(file);
                    if (!file.isDirectory()) {
                        assertEquals(file.length(), entry.size());
                    } else {
                        assertEquals(0, entry.size());
                    }
                    assertEquals(entry.getRevision(), 1);
                    assertEquals(entry.getKind(), file.isDirectory() ? SVNNodeKind.DIR : SVNNodeKind.FILE);
                }
            }
            
            public void visitFile(File file, String relativePath) throws Throwable {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                long rev = getRepository().getFile(relativePath, -1, null, os);
                assertEquals(rev, 1);
                byte[] contents = os.toByteArray();
                assertEquals(file.length(), contents.length);
                // here getFile is tested
                InputStream is = new BufferedInputStream(new FileInputStream(file));
                byte[] fileContents = new byte[contents.length];
                int count = is.read(fileContents);
                assertEquals(count, contents.length);
                assertTrue(is.read() < 0);
                is.close();
                for(int i = 0; i < fileContents.length; i++) {
                    assertTrue(fileContents[i] == contents[i]);
                }
            }            
        });
    }
    
    public void doTestMissingNodeKind() throws Throwable {
        SVNNodeKind kind = getRepository().checkPath("test/missing.txt", -1);
        assertEquals(kind, SVNNodeKind.NONE);
    }
    
    public void doTestLog() throws Throwable {
        Collection entries = getRepository().log(new String[] {""}, null, 1, 1, true, true);
        assertNotNull(entries);
        assertEquals(entries.size(), 1);
        SVNLogEntry entry = (SVNLogEntry) entries.iterator().next();
        assertEquals(entry.getMessage(), AllTests.getImportMessage());
        assertEquals(entry.getRevision(), 1);
        assertNotNull(entry.getChangedPaths());
        
        assertEquals(3, entry.getChangedPaths().size());
        Map pathsMap = entry.getChangedPaths();
        for(Iterator paths = pathsMap.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            assertNotNull(path);
            SVNLogEntryPath logPath = (SVNLogEntryPath) pathsMap.get(path);
            assertNotNull(logPath);
            assertEquals(logPath.getPath(), path);
            // compare one in fixture with one in repos.
            path = PathUtil.removeLeadingSlash(path);
            
            File file = new File(getRealFixtureRoot(), path);
            assertTrue(file.exists());
            SVNRepository r = createRepository(SVNRepositoryLocation.parseURL(getRealRepositoryURL()));
            SVNNodeKind kind = r.checkPath(path, -2);
            assertNotNull(kind);
            if (file.isDirectory()) {
                assertEquals(kind, SVNNodeKind.DIR);
            } else {
                assertEquals(kind, SVNNodeKind.FILE);
            }
        }
    }
    
    public void doTestFileRevisions() throws Throwable {
        final Map revisions = new HashMap();
        final Map tempFilesMap = new HashMap();
        // collect revisions (1) for every file in fixture
        visitFixture(new IFileVisitor() {
            public void visitDirectory(File dir, String relativePath) throws Throwable {
                try {
                    int count = collectFileRevisions(relativePath, revisions, tempFilesMap);
                    assertEquals(0, count);
                } catch(SVNException e) {
                    return;
                }
            }

            public void visitFile(File file, String relativePath) throws Throwable {
                collectFileRevisions(relativePath, revisions, tempFilesMap);
            }
            
        });
        assertFalse(revisions.isEmpty());
        for(Iterator paths = revisions.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            List revsList = (List) revisions.get(path);
            assertNotNull(revsList);
            assertEquals(1, revsList.size());
            SVNFileRevision revision = (SVNFileRevision) revsList.get(0);
            assertNotNull(revision);
            assertNotNull(revision.getProperties());
            assertNotNull(revision.getPropertiesDelta());
            assertEquals(1, revision.getRevision());
            assertEquals(path, revision.getPath());
            
            path = PathUtil.removeLeadingSlash(path);
            File fixtureFile = new File(getRealFixtureRoot(), path);
            assertTrue(fixtureFile.exists());
            assertFalse(fixtureFile.isDirectory());
        }
        // collected contents should be the same as in fixture.
        assertFalse(tempFilesMap.isEmpty());
        visitFixture(new IFileVisitor() {
            public void visitDirectory(File dir, String relativePath) throws Throwable {
            }
            public void visitFile(File file, String relativePath) throws Throwable {
                // compare file contents with fixture one!
                relativePath = "/" + relativePath;
                if (getFixtureRoot() != getRealFixtureRoot() ) {
                    relativePath = "/directory" + relativePath;
                }
                File tempFile = (File) tempFilesMap.get(relativePath);
                assertNotNull(tempFile);
                try {
                    assertEquals(file, tempFile);
                } finally {
                  tempFile.delete();
                }
            }
            
        });
    }
    
    public void doTestUpdate() throws Throwable {
        // test checkout for HEAD and for revision
        // test update with some of the files missing, for HEAD and for rev.
        File dst = AllTests.createPlayground();
        // should checkout all
        dst = checkoutAll(dst);
        // compare with fixture
        assertEquals(dst, getFixtureRoot(), false);
        AllTests.deleteAll(dst);
        final String[] lastPath = new String[1];
        
        final String[] lastFile = new String[1];
        visitFixture(new IFileVisitor() {
            public void visitDirectory(File dir, String relativePath) throws Throwable {
                if (!"".equals(relativePath)) {
                    lastPath[0] = relativePath;
                }
            }
            public void visitFile(File file, String relativePath) throws Throwable {
                lastFile[0] = relativePath;
            }
        });
        final String path = lastPath[0] != null ? lastPath[0] : lastFile[0];
        ISVNEditor editor = new SVNCheckoutEditor(dst);

        getRepository().update(-2, null, true, new ISVNReporterBaton() {
            public void report(ISVNReporter reporter) throws SVNException {
                reporter.setPath("", null, 1, false);
                reporter.deletePath(path);
                reporter.finishReport();
            }
        }, editor); 
        // compare path with the same in fixture (it may be dir or file).
        assertEquals(new File(dst, path), new File(getFixtureRoot(), path), true);
    }
    
    public void doTestStatus() throws Throwable {
        // just run
        File dst = AllTests.createPlayground();//new File(getFixtureRoot(), "../test");        
        ISVNEditor editor = new SVNCheckoutEditor(dst);
        getRepository().status(-2, null, true, new ISVNReporterBaton() {
            public void report(ISVNReporter reporter) throws SVNException {
                reporter.setPath("", null, 1, false);
                reporter.finishReport();
            }
        }, editor); 
    }
    
    public void doTestLocations() throws Throwable {
        String path = "";
        Collection locations = getRepository().getLocations(path, null, 1, new long[] {1});
        assertNotNull(locations);
        assertEquals(1, locations.size());
        SVNLocationEntry location = (SVNLocationEntry) locations.iterator().next();
        assertNotNull(location);
        if (getRealFixtureRoot() != getFixtureRoot()) {
            path = "directory";
        }
        assertEquals(location.getPath(), "/" + path);
        assertEquals(location.getRevision(), 1);
    }
    
    public void doTestProperties() throws Throwable {
        getRepository().setRevisionPropertyValue(1, "test", "testValue");
        String value = getRepository().getRevisionPropertyValue(1, "test");
        assertEquals("testValue", value);
        getRepository().setRevisionPropertyValue(1, "test", null);
        value = getRepository().getRevisionPropertyValue(1, "test");
        assertNull(value);        
    }
    
    public void doTestCommit() throws Throwable {
        ISVNWorkspaceMediator mediator = new SVNCommitMediator(null);

        ISVNEditor editor = getRepository().getCommitEditor("add dir", mediator);
        try {
            editor.openRoot(1); 
            editor.addDir("dir2", null, -1); // open
            editor.changeDirProperty("testDirProperty", "testDirValue");
            editor.addFile("dir2/test.txt", null, -1);
            editor.applyTextDelta("dir2/test.txt", null);
            SVNDiffWindow window = SVNDiffWindowBuilder.createReplacementDiffWindow(4);
            OutputStream os = editor.textDeltaChunk("dir2/test.txt", window);
            os.write("test".getBytes());
            os.close();
            editor.textDeltaEnd("dir2/test.txt");
            editor.closeFile("dir2/test.txt", null);
            editor.closeDir(); // added dir2
            
            editor.closeDir(); // ROOT
            SVNCommitInfo info = editor.closeEdit();
            
            assertNotNull(info);
            assertEquals(info.getNewRevision(), 2);

            editor = getRepository().getCommitEditor("change file and copy dir", mediator);
            editor.openRoot(2);
            editor.addDir("addedDir", null, -1); // open
            editor.closeDir(); // close

            
            editor.openDir("dir2", 2); //open
            editor.openFile("dir2/test.txt", 2);
            editor.applyTextDelta("dir2/test.txt", null);
            window = SVNDiffWindowBuilder.createReplacementDiffWindow(8);
            os = editor.textDeltaChunk("dir2/test.txt", window);
            os.write("modified".getBytes());
            os.close();
            editor.textDeltaEnd("dir2/test.txt");
            editor.closeFile("dir2/test.txt", null);
            editor.closeDir(); // close

            // like here close dir is called for root?
            editor.closeDir(); // ROOT
            
            info = editor.closeEdit();
            
            editor = getRepository().getCommitEditor("delete added dir", mediator);

            editor.openRoot(3);
            editor.deleteEntry("addedDir", 3);
            editor.closeDir(); // ROOT
            info = editor.closeEdit();
            assertNotNull(info);
            assertEquals(info.getNewRevision(), 4);
        } catch (SVNException e) {
            e.printStackTrace();
            editor.abortEdit();
            fail(e.getMessage());
        }
        File dst = AllTests.createPlayground();//new File(getFixtureRoot(), "../test");
        File dir = new File(getFixtureRoot(), "dir2");
        try {
            dir.mkdirs();
            File file = new File(dir, "test.txt");

            OutputStream os = new FileOutputStream(file);
            os.write("modified".getBytes());
            os.close();            
            checkoutAll(dst);
            assertEquals(getFixtureRoot(), dst);
        } finally {
            AllTests.deleteAll(dir);
        }
    }
    
    private int collectFileRevisions(String path, final Map revisions, final Map tempFilesMap) throws SVNException {
        return getRepository().getFileRevisions(path, 1, 1, new ISVNFileRevisionHandler() {
            File myTempFile;
            SVNDiffWindow myWindow;
            public OutputStream handleDiffWindow(String token, SVNDiffWindow diffWindow) {
                assertNotNull(token);
                try {
                    myTempFile = File.createTempFile("svn", "test");
                    myWindow = diffWindow;
                    return new FileOutputStream(myTempFile);
                } catch (IOException e) {
                    fail(e.getMessage());
                }
                return null;
            }
            
            public void handleDiffWindowClosed(String token) {
                File tempTarget = null;
                InputStream is = null;
                try {
                    assertNotNull(token);
                    assertTrue(myTempFile.exists());
                    assertEquals(myWindow.getNewDataLength(), myTempFile.length());

                    is = new FileInputStream(myTempFile);
                    tempTarget = File.createTempFile("svn", "test");
                    SVNRAFileData target = new SVNRAFileData(tempTarget, false);
                    myWindow.apply(target, target, is, 0);
                    target.close();      
                    tempFilesMap.put(token, tempTarget);
                } catch (IOException e) {
                    fail(e.getMessage());
                } catch (SVNException e) {
                    fail(e.getMessage());
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            fail(e.getMessage());
                        }
                    }
                    myTempFile.delete();
                }
            }
            public void handleFileRevision(SVNFileRevision fileRevision) {
                if (revisions.get(fileRevision.getPath()) == null) {
                    revisions.put(fileRevision.getPath(), new LinkedList());
                }
                ((List) revisions.get(fileRevision.getPath())).add(fileRevision);
            }
        });
    }

    private File checkoutAll(final File dst) throws Throwable {
        ISVNEditor editor = new SVNCheckoutEditor(dst);        
        // should checkout all
        try {
            getRepository().update(-2, null, true, new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, 1, true);
                    reporter.finishReport();
                }
            }, editor);
        } catch (SVNException e) {
            fail(e.getMessage());
        }
        return dst;
    }
}
