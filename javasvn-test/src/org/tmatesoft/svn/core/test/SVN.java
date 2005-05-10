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

import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNWorkspaceManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class SVN {

    public static void main(String[] args) {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSEntryFactory.setup();
        
        String url = "svn://80.188.80.120/test3/folder";
//        String copyFromPath = "source";
//        String copyFromPath2 = "source.file.txt";
//        String copyToURL = "svn://localhost/target modified(6)";
//        String copyToURL2 = "svn://localhost/target.file modified(6).txt";
        String path = "c:/bug/switch/wc7";
        try {
            /*
            SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);
            SVNRepository repos = SVNRepositoryFactory.create(location);
            repos.log(new String[] {""}, -1, -1, false, false, new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) {
                    System.out.println("entry: " + logEntry);
                }
            });*/
            
            ISVNWorkspace ws = SVNWorkspaceManager.createWorkspace("file", path);
            ws.setCredentials(new SVNSimpleCredentialsProvider("alex", "cvs"));
            ws.update("folder/alpha.txt", -1, false);
            
//            ws.update(SVNRepositoryLocation.parseURL("http://80.188.80.120/svn/repos/test3/branches/branch4"), "", -1, true);
//            long rev = ws.commit("test commit");
//            System.out.println("revision: " + rev);
//            long rev = ws.commit("test commit");
//            System.out.println("rev: " + rev);
            //ws.update(SVNRepositoryLocation.parseURL("http://80.188.80.120/svn/repos/test3/branches/branch2"), "", -1, true);
/*            
            OutputStream os = new FileOutputStream(new File(path, "source.file.txt"));
            os.write("modified (1)".getBytes());
            os.close();

            os = new FileOutputStream(new File(path, "source/source.file.txt"));
            os.write("modified (2)".getBytes());
            os.close();
            
            ws.copy(copyFromPath2, SVNRepositoryLocation.parseURL(copyToURL2), "WC->URL copy test");
            ws.copy(copyFromPath, SVNRepositoryLocation.parseURL(copyToURL), "WC->URL copy test");
            */
        } catch (Throwable e) {
            e.printStackTrace();
            DebugLog.error(e);
        }
        
    }
}
