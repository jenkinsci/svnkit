/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SpecialTest {

    private static SVNClientManager ourClientManager;

    public static void main(String[] args) {
        setup();
        
        try {
            SVNURL url = SVNURL.parseURIEncoded(args[0]);
            File wc = new File(args[1]).getAbsoluteFile();

            test(url, wc);
        } catch (SVNException e) {
            e.printStackTrace();
            System.err.println(e.getErrorMessage().getFullMessage());
            System.exit(1);
        }
        System.out.println("OK");
        System.exit(0);
    }
    
    public static void test(SVNURL url, File wc) throws SVNException {
        createFixture(url, wc);
        System.out.println("FIXTURE CREATED");
        createSymlink(wc, "trunk/link", "../linked");
        createSymlink(wc, "trunk/link2", "../linked");
        createSymlink(wc, "trunk/empty-link", "../missing");
        System.out.println("SYMLINKS CREATED");
        
        addSymlink(wc, "trunk/link");
        System.out.println("SYMLINK ADDED");
        addSymlink(wc, "trunk/link2");
        System.out.println("ANOTHER SYMLINK ADDED");
        addSymlink(wc, "trunk/empty-link");
        System.out.println("SYMLINK TO MISSING TARGET ADDED");
        
        commitLink(new File(wc, "trunk/link"));
        System.out.println("SYMLINK ADDITION COMMITTED");
        
        commitWC(wc);
        System.out.println("WC COMMITTED");
        
        remove(new File(wc, "trunk/link"));
        System.out.println("LINK REMOVED");
        remove(new File(wc, "trunk/link2"));
        System.out.println("ANOTHER LINK REMOVED");
        remove(new File(wc, "trunk/empty-link"));
        System.out.println("EMPTY LINK REMOVED");
    }
    
    private static void remove(File link) throws SVNException {
        getClientManager().getWCClient().doDelete(link, false, false);
    }
    
    private static void commitWC(File wc) throws SVNException {
        getClientManager().getCommitClient().doCommit(new File[] {wc}, false, "commit", false, true);
    }

    private static void commitLink(File file) throws SVNException {
        getClientManager().getCommitClient().doCommit(new File[] {file}, false, "commit", false, false);
    }
    
    private static void addSymlink(File wc, String linkPath) throws SVNException {
        getClientManager().getWCClient().doAdd(new File(wc, linkPath), false, false, false, false);
    }

    private static void createSymlink(File wc, String filePath, String target) {
        SVNFileUtil.createSymlink(new File(wc, filePath), target);
    }
    
    private static void createFixture(SVNURL url, File wc) throws SVNException {
        // checkout from repository, create directories and commit.
        getClientManager().getUpdateClient().doCheckout(url, wc, SVNRevision.UNDEFINED, SVNRevision.HEAD, true);
        getClientManager().getWCClient().doAdd(new File(wc, "trunk"), false, true, false, false);
        getClientManager().getWCClient().doAdd(new File(wc, "linked"), false, true, false, false);
        getClientManager().getCommitClient().doCommit(new File[] {wc}, false, "import", false, true);        
    }
    
    private static SVNClientManager getClientManager() {
        if (ourClientManager == null) {
            ourClientManager = SVNClientManager.newInstance();
        }
        return ourClientManager;
    }
    
    private static void setup() {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
    }

}
