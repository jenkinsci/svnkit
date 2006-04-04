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
package org.tmatesoft.svn.core.internal.io.fs.test;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;

/**
 * args: reposRootURL importDir1 importDir2 ...
 * 
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNConcurrentCommitTest {

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("args: URL dir1 dir2 ...");
            System.exit(1);
        }
        FSRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();

        try {

            SVNURL reposURL = SVNURL.parseURIDecoded(args[0]);
            SVNImportThread[] threads = new SVNImportThread[args.length - 1];

            for (int i = 1; i < args.length; i++) {
                File dir = new File(args[i]);
                String message = "import #" + i + ": '" + dir + "'";
                SVNURL destination = reposURL.appendPath(dir.getName(), false);
                threads[i - 1] = new SVNImportThread(dir, destination, message);
            }

            for (int i = 0; i < threads.length; i++) {
                threads[i].start();
            }
        } catch (SVNException svne) {
            System.out.println(svne.getMessage());
            System.exit(1);
        }
    }

}
