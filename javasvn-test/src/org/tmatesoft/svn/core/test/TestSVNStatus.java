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

import java.io.File;

import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

/**
 * @author TMate Software Ltd.
 */
public class TestSVNStatus extends AbstractRepositoryTest {

    public TestSVNStatus(String url, String methodName) {
        super(url, methodName);
    }
    
    public void testLocalStatus() throws Throwable {
        File svn = AllTests.createPlayground();
        
        ISVNWorkspace ws = createWorkspace(svn);
        ws.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -1, false);
        ws.setPropertyValue("", SVNProperty.IGNORE, "ignored.txt");
        ws.commit("property set");
        
        File status = createDirectory(svn, "status");

        makeStatusFixture(ws, status);
    }
    
    public void testIgnore() throws Throwable {
        File svn = AllTests.createPlayground();
        
        ISVNWorkspace ws = createWorkspace(svn);
        ws.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -1, false);
        ws.setGlobalIgnore("*.o");
        
        new File(svn, "foo").mkdirs();
        new File(svn, "fo.o").mkdirs();
        
        final boolean[] wasFoo = new boolean[] {false};
        ws.status("", false, new ISVNStatusHandler() {
            public void handleStatus(String path,SVNStatus status) {
                if (path.endsWith(".o")) {
                    fail("file " + path + " should be ignored");
                }
                if (path.equals("foo")) {
                    wasFoo[0] = true;
                }
            }
        }, true, false /* unmodified */, false /* ignored */);
        assertTrue(wasFoo[0]);        
    }
    
    private void makeStatusFixture(ISVNWorkspace ws, File root) throws Throwable {
        // create files that should be in repo.
        createFile(new File(root, "normal.txt"), "contents");
        File modified = createFile(new File(root, "modified.txt"), "contents");
        createFile(new File(root, "ignored.txt"), "contents");
        createFile(new File(root, "deleted.txt"), "contents");
        File conflict = createFile(new File(root, "conflict.txt"), "contents");
        createFile(new File(root, "propsConflict.txt"), "contents");
        
        File missing = createFile(new File(root, "missing.txt"), "contents");
        createFile(new File(root, "properties.txt"), "contents");
        
        createFile(new File(root, "remoteModified.txt"), "contents");
//        File remoteAdded = createFile(new File(root, "remoteAdded.txt"), "contents");
        createFile(new File(root, "remoteDeleted.txt"), "contents");
        createFile(new File(root, "remotePropsModified.txt"), "contents");
        createFile(new File(root, "replaced.txt"), "contents");
        createFile(new File(root, "replacedWithFolder.txt"), "contents");
        
        createDirectory(root, "folder1");
        createDirectory(root, "folder2");
        
        // add folder and all files
        ws.add("status", true, true);
        ws.commit("status files added");
        
        // add files that shouldn't be in repo
        createFile(new File(root, "added.txt"), "contents");
        ws.add("status/added.txt", false, false);
        createFile(new File(root, "unversioned.txt"), "contents");

        // make local changes
        modifyFile(modified, "modified file");
        missing.delete();
        ws.setPropertyValue("status/properties.txt", "name", "value");
        ws.delete("status/deleted.txt");
        
        // make remote changes
        File remote = AllTests.createPlayground();
        ISVNWorkspace ws2 = createWorkspace(remote);
        ws2.checkout(SVNRepositoryLocation.parseURL(getRepositoryURL()), -1, false);
        
        modifyFile(new File(remote, "status/remoteModified.txt"), "modification");
        modifyFile(new File(remote, "status/conflict.txt"), "conflict modification");
        createFile(new File(remote, "status/remoteAdded.txt"), "added remotely");
        ws2.add("status/remoteAdded.txt", false, false);
        ws2.delete("status/replaced.txt");
        createFile(new File(remote, "status/replaced.txt"), "replaced");
        ws2.add("status/replaced.txt", false, false);
        ws2.delete("status/remoteDeleted.txt");
        ws2.setPropertyValue("status/propsConflict.txt", "name", "conflict");
        ws2.commit("remote commit");
        
        // make conflicts        
        ws.setPropertyValue("status/propsConflict.txt", "name", "value");
        modifyFile(conflict, "modified file");
        ws.update(-1);

        // get statuses now!
        ws.status("status", true, new ISVNStatusHandler() {
            public void handleStatus(String path, SVNStatus status) {
//                System.err.println(path + " : " + status.getContentsStatus() + " : " + status.getRepositoryRevision());
            }
        }, false, true, true);
    }

}
