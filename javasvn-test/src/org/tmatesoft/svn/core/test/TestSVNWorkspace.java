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
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class TestSVNWorkspace extends AbstractRepositoryTest {
    
    private static final String EOL = System.getProperty("line.separator");

    public TestSVNWorkspace(String url, String methodName) {
        super(url, methodName);
    }
    
    public void testExportAndCheckout() throws Throwable {
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();

        ISVNWorkspace workspace = createWorkspace(dst);
        // export
        long revision = workspace.checkout(getRepository().getLocation(), -2, true);
        assertEquals(1, revision);
    
        dst2.delete();
        AllTests.runSVNCommand("export", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});
        assertEquals(dst, dst2);

        dst = AllTests.createPlayground();
        dst2 = AllTests.createPlayground();
        
        workspace = createWorkspace(dst);
        revision = workspace.checkout(getRepository().getLocation(), -2, false); 
        assertEquals(1, revision);
    
        AllTests.runSVNCommand("checkout", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});
        assertEquals(dst, dst2);
    }
    
    public void testImport() throws Throwable {
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();

        ISVNWorkspace importWorkspace = createWorkspace(getFixtureRoot());
        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);

        long revision = importWorkspace.commit(SVNRepositoryLocation.parseURL(getRepositoryURL() + "/directory"), "import2");
        assertEquals(2, revision);
        
        checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -2, false);
        AllTests.runSVNCommand("checkout", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});
        assertEquals(dst, dst2);
    }
    
    
    public void testCommitAndUpdate() throws Throwable {
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();
        File dst3 = AllTests.createPlayground();
        
        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);
        AllTests.runSVNCommand("checkout", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});

        long revision = checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -2, false);
        assertEquals(dst, dst2);
        assertEquals(1, revision);
        assertNotNull(checkoutWorkspace.getLocation());
        // make some changes, after each change compare with the same in canonical wc.

        // 1. delete dir
        AllTests.runSVNAnonCommand("delete", new String[] {new File(dst2, "directory").getAbsolutePath()});
        checkoutWorkspace.delete("directory", true);
        assertEquals(dst, dst2);

        // 2. change file
        modifyFile(new File(dst2, "testFile.txt"), "modified");
        modifyFile(new File(dst, "testFile.txt"), "modified");

        // 3. add file
        createFile(new File(dst2, "added.txt"), "new file");
        createFile(new File(dst, "added.txt"), "new file");
        AllTests.runSVNAnonCommand("add", new String[] {new File(dst2, "added.txt").getAbsolutePath()});
        checkoutWorkspace.add("added.txt", false, false);
        assertEquals(dst, dst2);
        
        // commit
        revision = checkoutWorkspace.commit("change number one");
        assertEquals(2, revision);
        revision = checkoutWorkspace.update(-1);
        assertEquals(2, revision);
        
        // simple compare.
        dst2 = AllTests.createPlayground();
        AllTests.runSVNCommand("checkout", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});
        assertEquals(dst, dst2);

        ISVNWorkspace updateWorkspace = createWorkspace(dst3);
        revision = updateWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), 1, false);
        assertEquals(1, revision);
        
        try {
            assertEquals(dst2, dst3);
            fail("different revisions");
        } catch(Throwable th) {}
        try {
            assertEquals(dst, dst3);
            fail("different revisions");
        } catch(Throwable th) {}
        
        revision = updateWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(2, revision);
        assertEquals(dst3, dst);
        assertEquals(dst3, dst2);
        
        // more commit to dst, then compare with updates in dst3, dst2
        // 1. delete file
        checkoutWorkspace.update(-1);
        checkoutWorkspace.delete("added.txt", true);
        // 2. add dir, then add file.
        File dir = new File(dst, "new folder");
        checkoutWorkspace.add("new folder", true, true);
        createFile(new File(dir, "new file.java"), "new file.java");
        checkoutWorkspace.add("new folder/new file.java", false, true);
        // commit
        revision = checkoutWorkspace.commit("- dir added\n- file deleted");
        assertEquals(revision, 3);
        
        long headRevision = updateWorkspace.update(-1);
        checkoutWorkspace.update(-1);
        assertEquals(revision, headRevision);
        AllTests.runSVNCommand("update", new String[] {dst2.getAbsolutePath()});
        
//        assertEquals(dst, dst2);
//        assertEquals(dst, dst3);
        assertEquals(dst3, dst2);

        File toDelete = new File(dst3, "new folder/new file.java");
        assertTrue(toDelete.exists());
        assertTrue(toDelete.delete());
        revision = updateWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(3, revision);
        assertTrue(toDelete.exists());
        
//        assertEquals(dst, dst3);
    }
    
    public void testCommitEmptyFile() throws Throwable {
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();
        File dst3 = AllTests.createPlayground();
        
        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);
        checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -1, false);
        
        createFile(new File(dst, "empty.txt"), "");
        modifyFile(new File(dst, "testFile.txt"), "");
        
        checkoutWorkspace.add("empty.txt", false, false);
        checkoutWorkspace.commit("empty file");
        checkoutWorkspace.update(-1);
        
        AllTests.runSVNCommand("checkout", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});
        assertEquals(dst, dst2);
        
        ISVNWorkspace updateWorkspace = createWorkspace(dst3);
        updateWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -1, false);
        
        assertEquals(dst, dst3);
    }
    
    public void testCopy() throws Throwable {
        File dst = AllTests.createPlayground();//new File(getFixtureRoot(), "../dst");
        File dst2 = AllTests.createPlayground();//new File(getFixtureRoot(), "../dst2");
        
        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);
        AllTests.runSVNCommand("checkout", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});
        checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), ISVNWorkspace.HEAD, false);            
        assertEquals(dst, dst2);
        
        createFile(new File(dst, "directory/added.txt"), "TEST");
        createFile(new File(dst2, "directory/added.txt"), "TEST");
        checkoutWorkspace.add("directory/added.txt", false, true);
        
        checkoutWorkspace.copy("directory", "copy", false);
        
        AllTests.runSVNAnonCommand("add", new String[] {new File(dst2, "directory/added.txt").getAbsolutePath()});            
        AllTests.runSVNAnonCommand("copy", new String[] {new File(dst2, "directory").getAbsolutePath(), 
                new File(dst2, "copy").getAbsolutePath()});
        
        assertEquals(dst, dst2);
        long rev = checkoutWorkspace.commit("copy commit");
        assertEquals(rev, 2);

        // need to commit file, 
        // otherwise, its wcprops will not be set "correctly enough" to compare with checked out version.
        modifyFile(new File(dst, "copy/testFile2.txt"), "modifications made");
        rev = checkoutWorkspace.commit("change after copy");
        assertEquals(rev, 3);
        // now have to update... /doesn't work/
        checkoutWorkspace.update(ISVNWorkspace.HEAD);
        
        dst2 = AllTests.createPlayground();
        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});
        assertEquals(dst, dst2);
        
        // test copy of single file
        checkoutWorkspace.copy("directory/added.txt", "addedCopy.txt", false);
        AllTests.runSVNAnonCommand("copy", new String[] {new File(dst2, "directory/added.txt").getAbsolutePath(), 
                new File(dst2, "addedCopy.txt").getAbsolutePath()});
        assertEquals(dst, dst2);

        checkoutWorkspace.copy("directory/testFile2.txt", "testFile2Copy.txt", false);
        AllTests.runSVNAnonCommand("copy", new String[] {new File(dst2, "directory/testFile2.txt").getAbsolutePath(), 
                new File(dst2, "testFile2Copy.txt").getAbsolutePath()});
        assertEquals(dst, dst2);
        
        checkoutWorkspace.copy("testFile.txt", "testFileCopy.txt", false);
        AllTests.runSVNAnonCommand("copy", new String[] {new File(dst2, "testFile.txt").getAbsolutePath(), 
                new File(dst2, "testFileCopy.txt").getAbsolutePath()});
        assertEquals(dst, dst2);
        checkoutWorkspace.commit("more copies");

        dst2 = AllTests.createPlayground();
        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});
        checkoutWorkspace.update(-1);
        assertEquals(dst, dst2);
    }

    public void testMove() throws Throwable {
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();
        
        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);
        AllTests.runSVNCommand("checkout", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});

        checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), ISVNWorkspace.HEAD, false);            
        assertEquals(dst, dst2);
        
        createFile(new File(dst, "directory/added.txt"), "TEST");
        createFile(new File(dst2, "directory/added.txt"), "TEST");
        checkoutWorkspace.add("directory/added.txt", false, true);
        
        checkoutWorkspace.copy("directory", "moved", true);
        
        AllTests.runSVNAnonCommand("add", new String[] {new File(dst2, "directory/added.txt").getAbsolutePath()});            
        AllTests.runSVNAnonCommand("move", new String[] {"--force", new File(dst2, "directory").getAbsolutePath(), 
                new File(dst2, "moved").getAbsolutePath()});
        
        assertEquals(dst, dst2);
        modifyFile(new File(dst, "moved/testFile2.txt"), "test modification");
        long rev = checkoutWorkspace.commit("move commit");
        assertEquals(rev, 2);
        checkoutWorkspace.update(-1);
        
        dst2 = AllTests.createPlayground();
        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});
        assertEquals(dst, dst2);
    }
    
    public void testAutoProperties() throws Throwable {
        // add
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();
        
        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);
        AllTests.runSVNCommand("checkout", new String[] {getRepositoryURL(), dst2.getAbsolutePath()});
        
        Map properties = new HashMap();
        properties.put("*.java", "svn:eol-style=native;svn:mime-type=text/java");
        checkoutWorkspace.setAutoProperties(properties);
        
        long revision = checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), ISVNWorkspace.HEAD, false);
        assertEquals(1, revision);
        assertEquals(dst, dst2);        

        File newDir = createDirectory(dst, "org.package");
        createFile(new File(newDir, "added.java"), "java file" + EOL + "next line");
        createFile(new File(dst, "main.java"), "java file" + EOL + "next line");
        
        File newDir2 = createDirectory(dst2, "org.package");
        createFile(new File(newDir2, "added.java"), "java file" + EOL + "next line");
        File newFile = createFile(new File(dst2, "main.java"), "java file" + EOL + "next line");
        
        checkoutWorkspace.add("org.package", false, true);
        checkoutWorkspace.add("main.java", false, true);

        AllTests.runSVNAnonCommand("add", new String[] {newDir2.getAbsolutePath()});
        AllTests.runSVNAnonCommand("add", new String[] {newFile.getAbsolutePath()});

        assertEquals(dst, dst2);
        
        revision = checkoutWorkspace.commit("java file added");
        assertEquals(2, revision);
        checkoutWorkspace.update(-1);
        
        // check that props are ok after commit, compare with checked out version.
        File dst4 = AllTests.createPlayground();
        AllTests.runSVNCommand("checkout", new String[] {getRepositoryURL(), dst4.getAbsolutePath()});
        assertEquals(dst, dst4);

        // import
        File dst3 = AllTests.createPlayground();
        File newDir3 = createDirectory(dst3, "org.package.sub");
        createFile(new File(newDir3, "added.java"), "java file" + EOL + "next line");
        createFile(new File(dst3, "main.java"), "java file" + EOL + "next line");

        ISVNWorkspace importWorkspace = createWorkspace(dst3);
        SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(getRepositoryURL() + "/org.package");
        importWorkspace.setAutoProperties(properties);

        revision = importWorkspace.commit(location, "import");
        assertEquals(3, revision);
        
        // now update :)
        // update deletes props???
        revision = checkoutWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(3, revision);
        
        // check for prop files. compare with checked out.
        AllTests.runSVNCommand("up", new String[] {dst4.getAbsolutePath()});
        assertEquals(dst, dst4);
    }
    
    public void testEOLStyle() throws Throwable {
        File dst = AllTests.createPlayground();

        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);        
        Map properties = new HashMap();
        properties.put("*.java", "svn:eol-style=native;svn:mime-type=text/java");
        checkoutWorkspace.setAutoProperties(properties);        
        long revision = checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), ISVNWorkspace.HEAD, false);
        assertEquals(revision, 1);
        checkoutWorkspace.setPropertyValue("testFile.txt", "user", "value");
        checkoutWorkspace.commit("user prop changed");
        long fakeRev = checkoutWorkspace.commit("fake");
        assertEquals(-1, fakeRev);
        
        // add java file and check eols.
        String eol = System.getProperty("line.separator");
        File javaFile = createFile(new File(dst, "java.java"), "java" + eol + "java" + eol);
        File crFile = createFile(new File(dst, "java.cr"), "java" + "\r"+ "java" + "\r");
        checkoutWorkspace.add("java.java", false, true);
        checkoutWorkspace.add("java.cr", false, true);
        checkoutWorkspace.commit("java files added");
        // 
        
        // check base version.
        File baseFile = new File(dst, ".svn/text-base/java.java.svn-base");
        File baseCRFile = new File(dst, ".svn/text-base/java.cr.svn-base");
        // native for java files
        assertEquals(getEOLStyle(baseFile), "LF");
        assertEquals(getEOLStyle(javaFile), getNativeEOLStyle());
        // original for custom file
        assertEquals(getEOLStyle(baseCRFile), "CR");
        assertEquals(getEOLStyle(crFile), "CR");
        
        // check out fresh version
        File dst2 = AllTests.createPlayground();
        ISVNWorkspace updateWorkspace = createWorkspace(dst2);        
        updateWorkspace.setAutoProperties(properties);        
        revision = updateWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), ISVNWorkspace.HEAD, false);
        assertEquals(revision, 3);

        baseFile = new File(dst2, ".svn/text-base/java.java.svn-base");
        javaFile = new File(dst2, "java.java");
        baseCRFile = new File(dst2, ".svn/text-base/java.cr.svn-base");
        crFile = new File(dst2, "java.cr");
        // native way
        assertEquals(getEOLStyle(javaFile), getNativeEOLStyle());
        assertEquals(getEOLStyle(baseFile), "LF");        
        // preset, should be CR 
        assertEquals(getEOLStyle(crFile), "CR");
        assertEquals(getEOLStyle(baseCRFile), "CR");
        
        // NULL(CR)=>LF
        assertEquals(null, checkoutWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        checkoutWorkspace.setPropertyValue("java.cr", "svn:eol-style", "LF");
        assertEquals("LF", checkoutWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        
        checkoutWorkspace.commit("eol style changed");
        
        assertEquals("LF", getEOLStyle(new File(dst, "java.cr")));
        assertEquals("LF", getEOLStyle(new File(dst, ".svn/text-base/java.cr.svn-base")));
        assertEquals("LF", checkoutWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        updateWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals("LF", getEOLStyle(new File(dst2, "java.cr")));
        assertEquals("LF", getEOLStyle(new File(dst2, ".svn/text-base/java.cr.svn-base")));
        assertEquals("LF", updateWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        
        // LF=>CRLF
        assertEquals("LF", checkoutWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        checkoutWorkspace.setPropertyValue("java.cr", "svn:eol-style", "CRLF");
        assertEquals("CRLF", checkoutWorkspace.getPropertyValue("java.cr", "svn:eol-style"));        
        checkoutWorkspace.commit("eol style changed");
        assertEquals("CRLF", getEOLStyle(new File(dst, "java.cr")));
        assertEquals("CRLF", getEOLStyle(new File(dst, ".svn/text-base/java.cr.svn-base")));
        assertEquals("CRLF", checkoutWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        updateWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals("CRLF", getEOLStyle(new File(dst2, "java.cr")));
        assertEquals("CRLF", getEOLStyle(new File(dst2, ".svn/text-base/java.cr.svn-base")));
        assertEquals("CRLF", updateWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        
        // CRLF=>native
        checkoutWorkspace.setPropertyValue("java.cr", "svn:eol-style", "native");
        checkoutWorkspace.commit("eol style changed");
        assertEquals(getNativeEOLStyle(), getEOLStyle(new File(dst, "java.cr")));
        assertEquals("LF", getEOLStyle(new File(dst, ".svn/text-base/java.cr.svn-base")));
        assertEquals("native", checkoutWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        updateWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(getNativeEOLStyle(), getEOLStyle(new File(dst2, "java.cr")));
        assertEquals("LF", getEOLStyle(new File(dst2, ".svn/text-base/java.cr.svn-base")));
        assertEquals("native", updateWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        // modify
        modifyFile(new File(dst, "java.cr"), "test" + eol + "test");
        checkoutWorkspace.commit("modification");
        assertEquals(getNativeEOLStyle(), getEOLStyle(new File(dst, "java.cr")));
        assertEquals("LF", getEOLStyle(new File(dst, ".svn/text-base/java.cr.svn-base")));
        assertEquals("native", checkoutWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        updateWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(getNativeEOLStyle(), getEOLStyle(new File(dst2, "java.cr")));
        assertEquals("LF", getEOLStyle(new File(dst2, ".svn/text-base/java.cr.svn-base")));
        assertEquals("native", updateWorkspace.getPropertyValue("java.cr", "svn:eol-style"));

        // native(CRLF)=>NULL
        checkoutWorkspace.setPropertyValue("java.cr", "svn:eol-style", null);
        checkoutWorkspace.commit("eol style changed");
        assertEquals(getNativeEOLStyle(), getEOLStyle(new File(dst, "java.cr")));
        assertEquals(getNativeEOLStyle(), getEOLStyle(new File(dst, ".svn/text-base/java.cr.svn-base")));
        assertEquals(null, checkoutWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        updateWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(null, updateWorkspace.getPropertyValue("java.cr", "svn:eol-style"));
        assertEquals(getNativeEOLStyle(), getEOLStyle(new File(dst2, "java.cr")));
        assertEquals(getNativeEOLStyle(), getEOLStyle(new File(dst2, ".svn/text-base/java.cr.svn-base")));
        
        // change property to native, commit and try to commit again.
        checkoutWorkspace.setPropertyValue("java.cr", "svn:eol-style", "CR");
        long newRevision = checkoutWorkspace.commit("eol style changed");
        assertEquals(-1, checkoutWorkspace.commit("no change"));
        
        // export and check eol style for java file.
        File dst3 = AllTests.createPlayground();
        checkoutWorkspace = createWorkspace(dst3);
        SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(getRepositoryURL());
        long exported = checkoutWorkspace.checkout(location, ISVNWorkspace.HEAD, true);
        assertEquals(newRevision, exported);
        // 
        assertEquals("CR", getEOLStyle(new File(dst3, "java.cr")));
        assertEquals(getNativeEOLStyle(), getEOLStyle(new File(dst3, "java.java")));
    }
    
    public void testKeywords() throws Throwable {
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();
        File dst3 = AllTests.createPlayground();

        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);        
        long revision = checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), ISVNWorkspace.HEAD, false);
        assertEquals(revision, 1);

        ISVNWorkspace updateWorkspace = createWorkspace(dst2);        
        revision = updateWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), ISVNWorkspace.HEAD, false);
        assertEquals(revision, 1);
        
        // change file contents and keyword, commit
        checkoutWorkspace.setPropertyValue("testFile.txt", "svn:keywords", "Id Author");
        modifyFile(new File(dst, "testFile.txt"), "contents\n$Id$\n$Rev$\n$Author$=$LastChangedBy$\n$URL$\n$HeadURL$\n" +
                "$LastChangedDate$$Date$\n ... more contents");
        revision = checkoutWorkspace.commit("Id keyword added");
        assertEquals(revision, 2);
        
        // update 
        long updatedRevision = updateWorkspace.update(ISVNWorkspace.HEAD);
        checkoutWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(updatedRevision, revision);
        
        // checkout with svn.exe
        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), dst3.getAbsolutePath()});
        assertEquals(dst2, dst3);
        assertEquals(dst2, dst);
        
        // change more
        checkoutWorkspace.setPropertyValue("testFile.txt", "svn:keywords", "Author Rev URL");
        checkoutWorkspace.commit("more to expand");
        updateWorkspace.update(ISVNWorkspace.HEAD);
        checkoutWorkspace.update(ISVNWorkspace.HEAD);
        
        // checkout with svn.exe
        AllTests.runSVNCommand("up", new String[] {dst3.getAbsolutePath()});
        // workaround for svn BUG
        new File(dst3, "testFile.txt").delete();
        AllTests.runSVNCommand("up", new String[] {new File(dst3, "testFile.txt").getAbsolutePath()});

        assertEquals(dst2, dst3);
        assertEquals(dst2, dst);
        
        // rollback
        checkoutWorkspace.setPropertyValue("testFile.txt", "svn:keywords", null);
        long newRev = checkoutWorkspace.commit("more to expand");
        assertEquals(newRev, checkoutWorkspace.update(ISVNWorkspace.HEAD));
        updateWorkspace.update(ISVNWorkspace.HEAD);
        AllTests.runSVNCommand("up", new String[] {dst3.getAbsolutePath()});
        // workaround for svn BUG
        new File(dst3, "testFile.txt").delete();
        AllTests.runSVNCommand("up", new String[] {new File(dst3, "testFile.txt").getAbsolutePath()});

        assertEquals(dst2, dst3);
        assertEquals(dst2, dst);
        
    }
    
    public void testConflicts() throws Throwable {
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();
        File dst3 = AllTests.createPlayground();

        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);        
        long revision = checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), ISVNWorkspace.HEAD, false);
        assertEquals(revision, 1);

        ISVNWorkspace updateWorkspace = createWorkspace(dst2);        
        revision = updateWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), ISVNWorkspace.HEAD, false);
        assertEquals(revision, 1);

        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), dst3.getAbsolutePath()});
        
        modifyFile(new File(dst, "testFile.txt"), "change");
        modifyFile(new File(dst, "directory/testFile2.txt"), "change");
        checkoutWorkspace.setPropertyValue("testFile.txt", "svn:mime-type", "application/octet-stream");
        // similar changed
        // binary
        modifyFile(new File(dst2, "testFile.txt"), "conflicting change");
        modifyFile(new File(dst3, "testFile.txt"), "conflicting change");
        // text
        modifyFile(new File(dst2, "directory/testFile2.txt"), "conflicting change");
        modifyFile(new File(dst3, "directory/testFile2.txt"), "conflicting change");
        
        revision = checkoutWorkspace.commit("modification");
        assertEquals(revision, 2);
        long updatedRev = updateWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(revision, updatedRev);

        AllTests.runSVNCommand("up", new String[] {dst3.getAbsolutePath()});
        // will fail, because text merge is not yet implemented (?)
        assertEquals(dst2, dst3);
        assertTrue((updateWorkspace.status("directory/testFile2.txt", false).getContentsStatus() & SVNStatus.CONFLICTED) != 0);
        assertTrue((updateWorkspace.status("testFile.txt", false).getPropertiesStatus() & SVNStatus.CONFLICTED) == 0);
        assertTrue((updateWorkspace.status("testFile.txt", false).getContentsStatus() & SVNStatus.CONFLICTED) != 0);
        assertTrue((updateWorkspace.status("testFile.txt", false).getPropertiesStatus() & SVNStatus.CONFLICTED) == 0);
        
        assertTrue((updateWorkspace.status("directory", false).getPropertiesStatus() & SVNStatus.CONFLICTED) == 0);
        assertTrue((updateWorkspace.status("directory", false).getContentsStatus() & SVNStatus.CONFLICTED) == 0);
        // resolve.
        AllTests.runSVNAnonCommand("resolved", new String[] {new File(dst3, "testFile.txt").getAbsolutePath()});
        updateWorkspace.markResolved("testFile.txt", false);
        AllTests.runSVNAnonCommand("resolved", new String[] {new File(dst3, "directory/testFile2.txt").getAbsolutePath()});
        updateWorkspace.markResolved("directory/testFile2.txt", false);
        
        // will not work.
        // assertEquals(dst2, dst3);
        AllTests.runSVNAnonCommand("revert", new String[] {new File(dst3, "testFile.txt").getAbsolutePath()});
        updateWorkspace.revert("testFile.txt", false);
        AllTests.runSVNAnonCommand("revert", new String[] {new File(dst3, "directory/testFile2.txt").getAbsolutePath()});
        updateWorkspace.revert("directory/testFile2.txt", false);
        
        // should work!
        assertEquals(dst2, dst3);
        
        // make properties mergeable
        checkoutWorkspace.setPropertyValue("testFile.txt", "test", "value");
        checkoutWorkspace.setPropertyValue("directory", "test", "value");
        try {
            checkoutWorkspace.commit("property set");
            fail("should fail with out-of-date");
        } catch(SVNException e) {
            try {
                AllTests.runSVNCommand("ci", new String[] {dst.getAbsolutePath(), "-m", "message"});
                fail("should fail with out-of-date");
            } catch (Throwable e2) {}
        }
        assertEquals(SVNStatus.MODIFIED, checkoutWorkspace.status("directory", false).getPropertiesStatus());
        assertEquals(SVNStatus.MODIFIED, checkoutWorkspace.status("testFile.txt", false).getPropertiesStatus());
        checkoutWorkspace.update("directory", ISVNWorkspace.HEAD, true);
        assertEquals(SVNStatus.MODIFIED, checkoutWorkspace.status("directory", false).getPropertiesStatus());
        assertEquals(SVNStatus.MODIFIED, checkoutWorkspace.status("testFile.txt", false).getPropertiesStatus());
        checkoutWorkspace.commit("property set");
        
        AllTests.runSVNAnonCommand("ps", new String[] {"another", "value", new File(dst3, "testFile.txt").getAbsolutePath()});
        updateWorkspace.setPropertyValue("testFile.txt", "another", "value");
        assertEquals(dst2, dst3);
        AllTests.runSVNAnonCommand("ps", new String[] {"another", "value", new File(dst3, "directory").getAbsolutePath()});
        updateWorkspace.setPropertyValue("directory", "another", "value");
        assertEquals(dst2, dst3);

        AllTests.runSVNCommand("up", new String[] {dst3.getAbsolutePath()});
        updateWorkspace.update(ISVNWorkspace.HEAD);
        
        assertEquals(dst2, dst3);
        
        // now conflict
        
        checkoutWorkspace.setPropertyValue("testFile.txt", "another", "conflict");
        checkoutWorkspace.setPropertyValue("directory", "another", "conflict");
        checkoutWorkspace.commit("property set again");

        AllTests.runSVNCommand("up", new String[] {dst3.getAbsolutePath()});
        updateWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(dst2, dst3);
        
        // conflict is removed?
        checkoutWorkspace.setPropertyValue("testFile.txt", "another", "value");
        checkoutWorkspace.setPropertyValue("directory", "another", null);
        checkoutWorkspace.commit("property removed");
        // all props should be removed?

        AllTests.runSVNCommand("up", new String[] {dst3.getAbsolutePath()});
        updateWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(dst2, dst3);

        // another property
        checkoutWorkspace.setPropertyValue("testFile.txt", "another", null);
        checkoutWorkspace.commit("property removed");
        checkoutWorkspace.update(-1);
        // all props shoud be cleared
        
        AllTests.runSVNCommand("up", new String[] {dst3.getAbsolutePath()});
        updateWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(dst2, dst3);
        
        updateWorkspace.revert("directory", false);
        updateWorkspace.revert("testFile.txt", false);
        AllTests.runSVNAnonCommand("revert", new String[] {new File(dst3, "directory").getAbsolutePath()});
        AllTests.runSVNAnonCommand("revert", new String[] {new File(dst3, "testFile.txt").getAbsolutePath()});

        // this will not be true due to "revert" hack!
        //assertEquals(dst, dst2);
        
        // should be the same after "revert"?
        // there is a problem with entries that includes "prop-time" for dir.
        // this is done by "revert" command. should we do the same?
        assertEquals(dst2, dst3);
    }
    
    public void testStatus() throws Throwable {
        File dst = AllTests.createPlayground();

        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);        
        long revision = checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), ISVNWorkspace.HEAD, false);
        assertEquals(revision, 1);
        
        ISVNStatusHandler handler = new ISVNStatusHandler() {
            public void handleStatus(String path, SVNStatus status) {
                assertNotNull(path);
                assertNotNull(status);
                assertEquals(path, status.getPath());
            }  
        };
        
        modifyFile(new File(dst, "directory/testFile2.txt"), "modified");
        createFile(new File(dst, "newFile.txt"), "added");
        checkoutWorkspace.setPropertyValue("directory", "name", "value");
        
        long head = checkoutWorkspace.status("", false, handler, true, false, false);
        head = checkoutWorkspace.status("", true, handler, true, false, false);
        
        head = checkoutWorkspace.status("directory", true, handler, true, false, false);
        head = checkoutWorkspace.status("directory", true, handler, false, false, false);

        head = checkoutWorkspace.status("testFile.txt", true, handler, false, false, false);
        head = checkoutWorkspace.status("directory/testFile2.txt", true, handler, true, false, false);
        SVNStatus status = checkoutWorkspace.status("directory/testFile2.txt", false);
        assertNotNull(status);
        assertEquals(status.getContentsStatus(), SVNStatus.MODIFIED);
        assertEquals(status.getPropertiesStatus(), SVNStatus.NOT_MODIFIED);
        
        status = checkoutWorkspace.status("newFile.txt", true);
        assertNotNull(status);
        assertEquals(status.getContentsStatus(), SVNStatus.UNVERSIONED);
        assertEquals(status.getPropertiesStatus(), SVNStatus.NOT_MODIFIED);
        
        checkoutWorkspace.add("newFile.txt", false, true);
        checkoutWorkspace.delete("directory/testFile2.txt", true);

        status = checkoutWorkspace.status("directory/testFile2.txt", true);
        assertNotNull(status);
        assertEquals(status.getContentsStatus(), SVNStatus.DELETED);
        assertEquals(status.getPropertiesStatus(), SVNStatus.NOT_MODIFIED);
        assertTrue(!new File(dst, "directory/testFile2.txt").exists());
        
        status = checkoutWorkspace.status("newFile.txt", true);
        assertNotNull(status);
        assertEquals(status.getContentsStatus(), SVNStatus.ADDED);
        assertEquals(status.getPropertiesStatus(), SVNStatus.NOT_MODIFIED);
        
        assertEquals(head, 1);
        
        checkoutWorkspace.add("folder", true, true);
        checkoutWorkspace.commit("folder", "folder added", true);
        
        // switch!
        AllTests.runSVNCommand("switch", new String[] {getRepositoryURL() + "/directory", new File(dst, "folder").getAbsolutePath()});
        // check status
        final SVNStatus[] folder = new SVNStatus[1];
        revision = checkoutWorkspace.status("", true, new ISVNStatusHandler() {
            public void handleStatus(String path, SVNStatus st) {
                if ("folder".equals(path)) {
                    folder[0] = st;
                }
            }
        },  false, false, false);
        assertEquals(revision, 2);
        assertNotNull(folder[0]);
        assertTrue(folder[0].isSwitched());
        assertTrue(folder[0].isDirectory());
        
        modifyFile(new File(dst, "testFile.txt"), "change in switch");
        modifyFile(new File(dst, "folder/testFile2.txt"), "change in switch");
        DebugLog.log("COMPLEX COMMIT");
        checkoutWorkspace.commit(new String[] {"folder/testFile2.txt", "testFile.txt"}, "change in switch", false, true);
        DebugLog.log("DONE");
        
        revision = checkoutWorkspace.update(ISVNWorkspace.HEAD);        
        assertEquals(3, revision);
        
        checkoutWorkspace.revert("directory", true);
        revision = checkoutWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(3, revision);
        
        assertEquals(new File(dst, "folder"), new File(dst, "directory"), false);
        
        // try to revert unmodified file and folder
        status = checkoutWorkspace.status("testFile.txt", false);
        assertTrue(status.getContentsStatus() == status.getPropertiesStatus() && status.getContentsStatus() == SVNStatus.NOT_MODIFIED);
        checkoutWorkspace.revert("testFile.txt", false);
        checkoutWorkspace.revert("directory", true);
        status = checkoutWorkspace.status("testFile.txt", false);
        assertTrue(status.getContentsStatus() == SVNStatus.NOT_MODIFIED);
        assertTrue(status.getPropertiesStatus() == SVNStatus.NOT_MODIFIED);
        status = checkoutWorkspace.status("directory", false);
        assertTrue(status.getContentsStatus() == SVNStatus.NOT_MODIFIED);
        assertTrue(status.getPropertiesStatus() == SVNStatus.NOT_MODIFIED);
        
        // modify directory and revert.
        assertTrue(status.getPropertiesStatus() == SVNStatus.NOT_MODIFIED);
        checkoutWorkspace.setPropertyValue("directory", "p", "v\nv");
        status = checkoutWorkspace.status("directory", false);
        assertTrue(status.getContentsStatus() == SVNStatus.NOT_MODIFIED);
        assertTrue(status.getPropertiesStatus() == SVNStatus.MODIFIED);
        assertTrue(checkoutWorkspace.getProperties("directory", true, false).size() == 1);

        checkoutWorkspace.revert("directory", true);
        
        status = checkoutWorkspace.status("directory", false);
        assertTrue(status.getContentsStatus() == SVNStatus.NOT_MODIFIED);
        assertTrue(status.getPropertiesStatus() == SVNStatus.NOT_MODIFIED);
       
        assertTrue(checkoutWorkspace.getProperties("directory", true, false).isEmpty());
        
        // modify file and revert
        status = checkoutWorkspace.status("testFile.txt", false);
        assertTrue(status.getPropertiesStatus() == SVNStatus.NOT_MODIFIED);
        assertTrue(status.getContentsStatus() == SVNStatus.NOT_MODIFIED);
        modifyFile(new File(dst, "testFile.txt"), "xxx");
        status = checkoutWorkspace.status("testFile.txt", false);
        assertTrue(status.getPropertiesStatus() == SVNStatus.NOT_MODIFIED);
        assertTrue(status.getContentsStatus() == SVNStatus.MODIFIED);
        checkoutWorkspace.revert("testFile.txt", false);
        status = checkoutWorkspace.status("testFile.txt", false);
        assertTrue(status.getContentsStatus() == SVNStatus.NOT_MODIFIED);
        assertTrue(status.getPropertiesStatus() == SVNStatus.NOT_MODIFIED);
        
        // test descending in unversioned.
        checkoutWorkspace.update(ISVNWorkspace.HEAD);
        File dir = new File(dst, "directory/unversioned/test");
        dir.mkdirs();
        createFile(new File(dir,"unversioned.txt"), "contents");
        //
        final Collection entries = new HashSet();
        checkoutWorkspace.status("", false, new ISVNStatusHandler() {
          public void handleStatus(String path, SVNStatus st) {
              entries.add(path);
          }  
        }, true, true, true, true, true);
        assertTrue(entries.contains("directory"));
        assertTrue(entries.contains("directory/unversioned"));
        assertTrue(entries.contains("directory/unversioned/test"));
        assertTrue(entries.contains("directory/unversioned/test/unversioned.txt"));
        entries.clear();
        checkoutWorkspace.status("", false, new ISVNStatusHandler() {
            public void handleStatus(String path, SVNStatus st) {
                entries.add(path);
            }  
          }, true, true, true, false, false);
        assertTrue(entries.contains("directory"));
        assertTrue(entries.contains("directory/unversioned"));
        assertFalse(entries.contains("directory/unversioned/test"));
        assertFalse(entries.contains("directory/unversioned/test/unversioned.txt"));
    }
    
    public void testUpdateCheckedOutProject() throws Throwable {
        File svn = AllTests.createPlayground();
        File jsvn = AllTests.createPlayground();
        
        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), svn.getAbsolutePath()});
        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), jsvn.getAbsolutePath()});
        File file = new File(svn, "testFile.txt");
        assertTrue(file.exists());

        
        modifyFile(file, "modified contents");
        AllTests.runSVNCommand("ci", new String[] {"-m", "message", file.getAbsolutePath()});
        
        ISVNWorkspace checkoutWorkspace = createWorkspace(jsvn);
        long revision = checkoutWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(2, revision);

        AllTests.runSVNCommand("up", new String[] {svn.getAbsolutePath()});
        assertEquals(svn, jsvn);
    }
    
    public void testCommitPaths() throws Throwable {
        File jsvn = AllTests.createPlayground();

        ISVNWorkspace checkoutWorkspace = createWorkspace(jsvn);
        long revision = checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()),ISVNWorkspace.HEAD,false);
        assertEquals(1, revision);
        
        modifyFile(new File(jsvn, "directory/testFile2.txt"), "contents");
        revision = checkoutWorkspace.commit(new String[] {"directory/testFile2.txt"}, "single file", true, true);
        assertEquals(2, revision);
        
        checkoutWorkspace.update(-1);
        modifyFile(new File(jsvn, "directory/testFile2.txt"), "another contents");
        revision = checkoutWorkspace.commit(new String[] {"directory"}, "single file", true, true);
        assertEquals(3, revision);
        
        // checkout and compare
        revision = checkoutWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(3, revision);

        File svn = AllTests.createPlayground();
        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), svn.getAbsolutePath()});
        assertEquals(svn, jsvn);
        
        createDirectory(jsvn, "directory/subfolder");
        createFile(new File(jsvn, "directory/subfolder/file.txt"), LONG_STRING);
        checkoutWorkspace.add("directory/subfolder", false, true);
        checkoutWorkspace.commit("directory", "folder added", true);
        checkoutWorkspace.update(ISVNWorkspace.HEAD);

        AllTests.runSVNCommand("up", new String[] {svn.getAbsolutePath()});
        assertEquals(svn, jsvn);

        modifyFile(new File(jsvn, "directory/subfolder/file.txt"), "change1" + EOL + LONG_STRING);
        checkoutWorkspace.commit("directory/subfolder/file.txt", "file changed", false);

        checkoutWorkspace.update(ISVNWorkspace.HEAD);
        AllTests.runSVNCommand("up", new String[] {svn.getAbsolutePath()});
        assertEquals(svn, jsvn);
        
        // commit more then one file.
        modifyFile(new File(jsvn, "directory/subfolder/file.txt"), LONG_STRING);
        modifyFile(new File(jsvn, "testFile.txt"), LONG_STRING);
        checkoutWorkspace.commit(new String[] {"testFile.txt", "directory/subfolder/file.txt"}, "commit", false, true);

        checkoutWorkspace.update(ISVNWorkspace.HEAD);
        AllTests.runSVNCommand("up", new String[] {svn.getAbsolutePath()});
        assertEquals(svn, jsvn);

        modifyFile(new File(jsvn, "directory/subfolder/file.txt"), LONG_STRING + LONG_STRING);
        modifyFile(new File(jsvn, "directory/testFile2.txt"), LONG_STRING + LONG_STRING);
        checkoutWorkspace.commit(new String[] {"directory/testFile2.txt", "directory/subfolder/file.txt"}, "commit", false, true);
        
        checkoutWorkspace.update(ISVNWorkspace.HEAD);
        AllTests.runSVNCommand("up", new String[] {svn.getAbsolutePath()});
        assertEquals(svn, jsvn);
        
        createFile(new File(jsvn, "directory/subfolder/file2.txt"), LONG_STRING);
        checkoutWorkspace.add("directory/subfolder/file2.txt", false ,false);
        checkoutWorkspace.commit("new file added");
        
        // modify two files, commit one.
        modifyFile(new File(jsvn, "directory/subfolder/file2.txt"), "m1");
        modifyFile(new File(jsvn, "directory/subfolder/file.txt"), "m2");
        SVNStatus status1 = checkoutWorkspace.status("directory/subfolder/file.txt", false);
        SVNStatus status2 = checkoutWorkspace.status("directory/subfolder/file2.txt", false);
        
        assertNotNull(status1);
        assertNotNull(status2);
        assertEquals(SVNStatus.MODIFIED, status1.getContentsStatus());
        assertEquals(SVNStatus.MODIFIED, status2.getContentsStatus());

        checkoutWorkspace.commit("directory/subfolder/file.txt", "single file", false);
        
        status1 = checkoutWorkspace.status("directory/subfolder/file.txt", false);
        status2 = checkoutWorkspace.status("directory/subfolder/file2.txt", false);
        
        assertEquals(SVNStatus.NOT_MODIFIED, status1.getContentsStatus());
        assertEquals(SVNStatus.MODIFIED, status2.getContentsStatus());
        
        // try to add already added folder.
        try {
            checkoutWorkspace.add("directory", true, true);
            fail("should not be possible to add versioned folder");
        } catch (SVNException e) {            
        }
        try {
            checkoutWorkspace.add("", true, true);
            fail("should not be possible to add versioned folder");
        } catch (SVNException e) {            
        }
        // marc said that update fails to work after incorrect add.
        checkoutWorkspace.update(-1);
    }
    
    public void testReplace() throws Throwable {
        File jsvn = AllTests.createPlayground();
        File svn = AllTests.createPlayground();

        ISVNWorkspace checkoutWorkspace = createWorkspace(jsvn);
        long revision = checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()),ISVNWorkspace.HEAD,false);
        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), svn.getAbsolutePath()});
        assertEquals(1, revision);
        assertEquals(svn, jsvn);
        
        AllTests.runSVNAnonCommand("del", new String[] {new File(svn, "directory/testFile2.txt").getAbsolutePath()});
        checkoutWorkspace.delete("directory/testFile2.txt", true);
        assertEquals(svn, jsvn);

        createFile(new File(jsvn, "directory/testFile2.txt"), "replacement");
        createFile(new File(svn, "directory/testFile2.txt"), "replacement");

        checkoutWorkspace.add("directory/testFile2.txt", false, false);
        AllTests.runSVNAnonCommand("add", new String[] {new File(svn, "directory/testFile2.txt").getAbsolutePath()});        
        assertEquals(svn, jsvn);

        SVNStatus status = checkoutWorkspace.status("directory/testFile2.txt", false);
        assertEquals(SVNStatus.REPLACED, status.getContentsStatus());
        
        // directory replacement
        jsvn = AllTests.createPlayground();
        svn = AllTests.createPlayground();

        checkoutWorkspace = createWorkspace(jsvn);
        revision = checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()),ISVNWorkspace.HEAD,false);
        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), svn.getAbsolutePath()});
        assertEquals(1, revision);
        assertEquals(svn, jsvn);
        
        AllTests.runSVNAnonCommand("del", new String[] {new File(svn, "directory").getAbsolutePath()});
        checkoutWorkspace.delete("directory", true);
        assertEquals(svn, jsvn);

        createFile(new File(jsvn, "directory/testFile2.txt"), "replacement");
        createFile(new File(svn, "directory/testFile2.txt"), "replacement");

        checkoutWorkspace.add("directory", false, true);
        AllTests.runSVNAnonCommand("add", new String[] {new File(svn, "directory").getAbsolutePath()});        
        assertEquals(svn, jsvn);

        status = checkoutWorkspace.status("directory", false);
        assertEquals(SVNStatus.REPLACED, status.getContentsStatus());
        status = checkoutWorkspace.status("directory/testFile2.txt", false);
        assertEquals(SVNStatus.REPLACED, status.getContentsStatus());
        
        // commit attempt (should result in "delete" for directory and "add" for directory and files below).
        revision = checkoutWorkspace.commit("replacement");
        assertEquals(2, revision);
        revision = checkoutWorkspace.update(ISVNWorkspace.HEAD);
        assertEquals(2, revision);
        
        svn = AllTests.createPlayground();
        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), svn.getAbsolutePath()});
        assertEquals(svn, jsvn);
        
        // test log messages
        checkoutWorkspace.update(1);
        AllTests.runSVNCommand("up", new String[] {svn.getAbsolutePath(), "-r1"});
        assertEquals(svn, jsvn);

        checkoutWorkspace.update(2);
        AllTests.runSVNCommand("up", new String[] {svn.getAbsolutePath()});
        assertEquals(svn, jsvn);
    }
    
    public void testOutOfDate() throws Throwable {
        File svn = AllTests.createPlayground();
        ISVNWorkspace ws = createWorkspace(svn);
        ws.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -1, false);
        
        // commit single small file.
        modifyFile(new File(svn, "directory/testFile2.txt"), "bzzzzz");
        ws.commit("single small file");
        assertEquals(ws.getPropertyValue("directory", SVNProperty.REVISION), "1");
        assertEquals(ws.getPropertyValue("directory/testFile2.txt", SVNProperty.REVISION), "2");

        // try to commit all repos. 
        modifyFile(new File(svn, "directory/testFile2.txt"), LONG_STRING);
        modifyFile(new File(svn, "testFile.txt"), LONG_STRING);
        ws.commit("all files");
        
    }
    
    public void testSwitch() throws Throwable {
        File jsvn = AllTests.createPlayground();
        File svn = AllTests.createPlayground();

        ISVNWorkspace checkoutWorkspace = createWorkspace(jsvn);
        long revision = checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()),ISVNWorkspace.HEAD,false);
        AllTests.runSVNCommand("co", new String[] {getRepositoryURL(), svn.getAbsolutePath()});
        assertEquals(1, revision);
        assertEquals(svn, jsvn);
        
        AllTests.runSVNCommand("switch", new String[] {getRepositoryURL(), new File(svn, "directory").getAbsolutePath()});
        checkoutWorkspace.update(SVNRepositoryLocation.parseURL(getRepositoryURL()), "directory", ISVNWorkspace.HEAD, true);
        modifyFile(new File(jsvn, "directory/directory/testFile2.txt"), LONG_STRING);
        checkoutWorkspace.commit("switched file changed");
        checkoutWorkspace.update(-1);
        
        AllTests.runSVNCommand("up", new String[] {svn.getAbsolutePath()});
        
        assertEquals(svn, jsvn, new FileFilter() {
            public boolean accept(File f) {
                return f != null && !"dir-wcprops".equals(f.getName()) && !"wcprops".equals(f.getParentFile().getName());
            }
        }, false);
        
        // switch root to root (with switched folder).
        AllTests.runSVNCommand("switch", new String[] {getRepositoryURL(), svn.getAbsolutePath()});
        checkoutWorkspace.update(SVNRepositoryLocation.parseURL(getRepositoryURL()), "", ISVNWorkspace.HEAD, true);
        
        assertEquals(svn, jsvn, new FileFilter() {
            public boolean accept(File f) {
                return f != null && !"dir-wcprops".equals(f.getName()) && !"wcprops".equals(f.getParentFile().getName());
            }
        }, false);
    }
    
    public void testRevert() throws Throwable {
        File jsvn = AllTests.createPlayground();
        ISVNWorkspace checkoutWorkspace = createWorkspace(jsvn);
        
        checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()),ISVNWorkspace.HEAD,false);
        File dir = new File(jsvn, "directory");
        // add subfolder, commit
        File subDir = createDirectory(dir, "subfolder");
        File file = createFile(new File(subDir, "file.txt"), "contents");
        checkoutWorkspace.add("directory/subfolder", true, true);
        checkoutWorkspace.commit("folder and file added");
        
        AllTests.deleteAll(dir);
        assertFalse(dir.exists());
        assertFalse(subDir.exists());
        assertFalse(file.exists());
        // should be reported as missing!
        SVNStatus dirStatus = checkoutWorkspace.status("directory", false);
        assertEquals(dirStatus.getContentsStatus(), SVNStatus.MISSING);
        assertEquals(dirStatus.getPropertiesStatus(), SVNStatus.NOT_MODIFIED);
        
        // restored by update.
        checkoutWorkspace.update("", -1, true);
        assertTrue(dir.exists());
        assertTrue(subDir.exists());
        assertTrue(file.exists());

        dirStatus = checkoutWorkspace.status("directory", false);
        assertEquals(dirStatus.getContentsStatus(), SVNStatus.NOT_MODIFIED);
        assertEquals(dirStatus.getPropertiesStatus(), SVNStatus.NOT_MODIFIED);
        
        // restored by revert?
        AllTests.deleteAll(dir);

        assertFalse(dir.exists());
        assertFalse(subDir.exists());
        assertFalse(file.exists());
        
        try {
            checkoutWorkspace.revert("directory", true);
            fail("should not be able to revert missing folder");
        } catch (SVNException e) {
            // ok.
        }
        assertFalse(dir.exists());
        assertFalse(subDir.exists());
        assertFalse(file.exists());
        // do update.
        checkoutWorkspace.update(ISVNWorkspace.HEAD);
        assertTrue(dir.exists());
        assertTrue(subDir.exists());
        assertTrue(file.exists());
        
        // add folder, then revert.
        dir = createDirectory(jsvn, "folder");
        file = createFile(new File(dir, "file.txt"), "contents");
        checkoutWorkspace.add("folder", true, true);
        assertTrue(dir.exists());
        assertTrue(file.exists());
        dirStatus = checkoutWorkspace.status("folder", false);
        assertEquals(dirStatus.getContentsStatus(), SVNStatus.ADDED);
        
        checkoutWorkspace.revert("", true);
        dirStatus = checkoutWorkspace.status("folder", false);
        
        assertEquals(dirStatus.getContentsStatus(), SVNStatus.UNVERSIONED);        
        assertTrue(file.exists());
        assertTrue(dir.exists());
        
        checkoutWorkspace.copy("testFile.txt", "directory/copyFile.txt", true);
        checkoutWorkspace.revert("", true);
        dirStatus = checkoutWorkspace.status("directory/copyFile.txt", false);
        assertEquals(dirStatus.getContentsStatus(), SVNStatus.UNVERSIONED);

        dirStatus = checkoutWorkspace.status("testFile.txt", false);
        assertEquals(dirStatus.getContentsStatus(), SVNStatus.NOT_MODIFIED);
    }

    public void testMoveSingleFile() throws Throwable {
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();

        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);
        AllTests.runSVNCommand("checkout", new String[]{getRepositoryURL(), dst2.getAbsolutePath()});

        checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), ISVNWorkspace.HEAD, false);
        assertEquals(dst, dst2);

        checkoutWorkspace.copy("directory/testFile2.txt", "directory/testFile2.moved.txt", true);
        AllTests.runSVNAnonCommand("move", new String[]{"--force", new File(dst2, "directory/testFile2.txt").getAbsolutePath(), new File(dst2, "directory/testFile2.moved.txt").getAbsolutePath()});

        assertEquals(dst, dst2);
        long rev = checkoutWorkspace.commit("move commit");
        assertEquals(rev, 2);
        checkoutWorkspace.update(-1);

        dst2 = AllTests.createPlayground();
        AllTests.runSVNCommand("co", new String[]{getRepositoryURL(), dst2.getAbsolutePath()});
        assertEquals(dst, dst2);
    }
    

    public void testDeleteFileAndCommit() throws Throwable {
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();

        ISVNWorkspace checkoutWorkspace = createWorkspace(dst);
        AllTests.runSVNCommand("checkout", new String[]{getRepositoryURL(), dst2.getAbsolutePath()});

        long revision = checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -2, false);
        assertEquals(dst, dst2);
        assertEquals(1, revision);

        // 1. delete file
        AllTests.runSVNAnonCommand("delete", new String[]{new File(dst2, "testFile.txt").getAbsolutePath()});
        AllTests.runSVNAnonCommand("delete", new String[]{new File(dst2, "directory").getAbsolutePath()});
        checkoutWorkspace.delete("testFile.txt", true);
        checkoutWorkspace.delete("directory", true);
        assertEquals(dst, dst2);

        // 2. commit
        revision = checkoutWorkspace.commit("testFile removed.");
        assertEquals(2, revision);
        // 2.1. check deleted file status.
        // get remote status
        checkoutWorkspace.status("", true, new ISVNStatusHandler() {
            public void handleStatus(String path,SVNStatus status) {
                System.err.println("status.l: " + status.getContentsStatus());
                System.err.println("status.r: " + status.getRepositoryContentsStatus());
            }
        }, true, false, false);
        
        SVNStatus status = null;
        status = checkoutWorkspace.status("testFile.txt", false);
        assertNull(status);

        // 3. update 
        checkoutWorkspace.update(-1);
        AllTests.runSVNCommand("up", new String[]{dst2.getAbsolutePath()});
        assertEquals(dst, dst2);
    }
    
    public void testDeleteAndReplace() throws Throwable {
        File svn = AllTests.createPlayground();
        ISVNWorkspace checkoutWorkspace = createWorkspace(svn);
        
        // checkout
        checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -2, false);
        
        // delete file, commit
        checkoutWorkspace.delete("testFile.txt", true);
        checkoutWorkspace.commit("file deleted");
        
        // add file in place
        checkoutWorkspace.copy("directory/testFile2.txt", "testFile.txt", true);
        // "deleted" property should be set for "testFile" (and after update as well).
        
        assertNotNull(checkoutWorkspace.getPropertyValue("testFile.txt", SVNProperty.DELETED));
        checkoutWorkspace.update(-1);
        assertNotNull(checkoutWorkspace.getPropertyValue("testFile.txt", SVNProperty.DELETED));
        checkoutWorkspace.commit("file moved");
        
        // now "deleted" property should be removed.
        assertNull(checkoutWorkspace.getPropertyValue("testFile.txt", SVNProperty.DELETED));
        checkoutWorkspace.update(-1);
    }
    
    public void testUpdateSingleFile() throws Throwable {
        File svn = AllTests.createPlayground();
        ISVNWorkspace checkoutWorkspace = createWorkspace(svn);
        
        // checkout
        checkoutWorkspace.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -2, false);
        String prevContent = getFileContent(new File(svn, "testFile.txt"));
        modifyFile(new File(svn, "testFile.txt"), "changed");
        checkoutWorkspace.commit("file modified, revision changed");
        assertEquals("2", checkoutWorkspace.getPropertyValue("testFile.txt", SVNProperty.REVISION));
        assertEquals("1", checkoutWorkspace.getPropertyValue("", SVNProperty.REVISION));
        
        // now do update single file, may fail in case of incorrect report
        checkoutWorkspace.update("testFile.txt", -1, false);
        assertEquals("2", checkoutWorkspace.getPropertyValue("testFile.txt", SVNProperty.REVISION));
        assertEquals("1", checkoutWorkspace.getPropertyValue("", SVNProperty.REVISION));
        assertEquals("changed", getFileContent(new File(svn, "testFile.txt")));

        // update whole directory.
        checkoutWorkspace.update("", -1, false);
        assertEquals("2", checkoutWorkspace.getPropertyValue("testFile.txt", SVNProperty.REVISION));
        assertEquals("2", checkoutWorkspace.getPropertyValue("", SVNProperty.REVISION));
        assertEquals("changed", getFileContent(new File(svn, "testFile.txt")));

        checkoutWorkspace.update("testFile.txt", 1, false);
        assertEquals("1", checkoutWorkspace.getPropertyValue("testFile.txt", SVNProperty.REVISION));
        assertEquals("2", checkoutWorkspace.getPropertyValue("", SVNProperty.REVISION));
        assertEquals(prevContent, getFileContent(new File(svn, "testFile.txt")));

        checkoutWorkspace.update("", -1, false);
        assertEquals("2", checkoutWorkspace.getPropertyValue("testFile.txt", SVNProperty.REVISION));
        assertEquals("2", checkoutWorkspace.getPropertyValue("", SVNProperty.REVISION));
        assertEquals("changed", getFileContent(new File(svn, "testFile.txt")));
    }

    
    private String getEOLStyle(File file) throws IOException {
        InputStream is = new BufferedInputStream(new FileInputStream(file));
        byte[] buffer = new byte[1024];
        try {
            int read = is.read(buffer);
            String str = new String(buffer, 0, read);
            if (str.indexOf("\r\n") >= 0) {
                return "CRLF";
            } else if (str.indexOf("\r") >= 0) {
                return "CR";
            } else if (str.indexOf("\n") >= 0) {
                return "LF";
            }
        } finally {
            is.close();
        }
        return null;
    }
    
    private static String getNativeEOLStyle() {
        String eol = System.getProperty("line.separator");
        if ("\n".equals(eol)) {            
            return "LF";
        } else if ("\r".equals(eol)) {
            return "CR";
        } else if ("\r\n".equals(eol)) {
            return "CRLF";
        }
        return null;
    }

    private static final String LONG_STRING = 
    "This package contains the modified source code of Netbeans' JavaCVS client library (http://javacvs.netbeans.org)," + EOL +
    "copyrighted by SUN. The initial developer is Robert Greig; parts of the code were contributed by Milos Kleint" + EOL + 
    "and Thomas Singer. Significant refactorings were done by Thomas Singer." + EOL + 
    "" + EOL + 
    "All this source code is published under the SUN PUBLIC LICENSE, that is included. Using these source code" + EOL +
    "implies that you must ensure to comply to the SUN PUBLIC LICENSE." + EOL +
    "" + EOL;
    
    
}
 
