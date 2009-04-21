/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs.test;

import java.io.File;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;

/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class SVNImportThread extends Thread {

    private File myDirectory;
    private SVNURL myURL;
    private String myCommitMessage;

    public SVNImportThread(File importDir, SVNURL dstURL, String commitMessage) {
        myDirectory = importDir;
        myURL = dstURL;
        myCommitMessage = commitMessage;
    }

    public void run() {
        System.out.println("Importing '" + myDirectory + "' to '" + myURL.toDecodedString() + "':");
        System.out.println(myCommitMessage);
        SVNClientManager manager = SVNClientManager.newInstance();
        SVNCommitInfo info = null;
        try {
            info = manager.getCommitClient().doImport(myDirectory, myURL, myCommitMessage, null, true, false, 
                    SVNDepth.INFINITY);
        } catch (SVNException svne) {
            System.out.println("Import failed: " + svne.getErrorMessage().getFullMessage());
            svne.printStackTrace();
            return;
        }
        System.out.println("Imported '" + myDirectory + "' to '" + myURL.toDecodedString() + "':");
        System.out.println(info);
    }

}
