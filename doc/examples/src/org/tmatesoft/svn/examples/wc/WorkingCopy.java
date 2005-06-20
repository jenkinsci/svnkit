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
package org.tmatesoft.svn.examples.wc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNWorkspaceManager;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
import org.tmatesoft.svn.util.PathUtil;
/*
 * This is a complex example program that demonstrates how you can manage local
 * working copies as well as URLs by means of the API provided in the 
 * org.tmatesoft.svn.core.wc package. The package itself represents a high-level API
 * (consisting of classes and interfaces) which allows to perform commands compatible
 * with commands of the native Subversion command line client. Most of the  methods 
 * of those classes are named like 'doSomething(...)' where 'Something' corresponds 
 * to the name of a Subversion command line client's command. So, for users 
 * familiar with the Subversion command line client it's quite easy to match a method
 * and an appropriate client's command.
 * 
 * The program illustrates a number of main operations usually carried out upon 
 * working copies and URLs. A brief description of what the program does:
 * 
 * 0)first of all initializes the JavaSVN library (it must be done prior to using
 * the library);
 * 
 * 1)if the program was run with some in parameters it fetches them out of the args 
 * parameter;
 * 
 * 2)next a user's credentials provider is created - almost all methods require
 * an ISVNCredentialsProvider to authenticate the user when it becomes necessary
 * during an access to a repository server;
 * 
 * 3)the first operation - making an empty directory in a repository; String url is 
 * to be a directory that will be created (it should consist of the URL of anexisting
 * repository and the name of the directory itself that will be created just under
 * the repository directory - that is like 'svn mkdir URL' which creates a new 
 * directory given all the intermediate directories created); this operation is based
 * upon an URL - so, it's immediately committed to the repository;
 * 
 * 4)the next operation - checking out the directory created in the previous 
 * step to a local directory defined by myWorkspacePath; analogous to 
 * 'svn co URL PATH';
 * 
 * 5)the next operation - recursively getting and displaying info for each item  at 
 * the working revision in the working copy that was checked out in the previous 
 * step; analogous to 'svn info -R' (certainly not the method itself but how the info
 * is shown);
 * 
 * 6)the next operation - creating a new directory with a file in the working copy
 * and then recursively scheduling (if any subdirictories existed they would be also
 * added) the created directory for addition; analogous to 'svn add newDir';
 * 
 * 7)the next operation - recursively getting and displaying the working copy status
 * not including unchanged (normal) paths; the result must include those paths which
 * were scheduled for addition in the previous step; similar to 
 * 'svn status --no-ignore --show-updates --verbose' (certainly not the method itself
 * but how the status info is shown);
 * 
 * 8)the next operation - recursively updating the working copy; if any local items
 * are out of date they will be updated to the latest revision;
 * 
 * 9)the next operation - showing status once again (for example, to see whether 
 * there're any conflicts);
 * 
 * 10)the next operation - committing local changes to the repository; this operation
 * will add the directory with the file that were scheduled for addition to the 
 * repository;
 * 
 * 11)the next operation - locking the file added in the previous step (for example, 
 * if you temporarily need to keep a file locked to prevent someone's else 
 * modifications);
 * 
 * 12)the next operation - showing status once again (for example, to see that the 
 * file was locked);
 * 
 * 13)the next operation - copying one URL - url -  to another one - copyURL - 
 * within the same repository;
 * 
 * 14)  
 */
public class WorkingCopy {

    public static void main(String[] args) {
        /*
         * Default values:
         */
        String url = "svn://localhost/rep/MyRepos";
        String copyURL = "svn://localhost/rep/MyReposCopy";
        String myWorkspacePath = "/MyWorkingCopy";
        SVNRevision revision = SVNRevision.HEAD;//HEAD (the latest) revision
        String name = "userName";
        String password = "userPassword";

        String newDir = "/newDir";
        String newFile = newDir + "/" + "newFile.txt";
        byte[] fileText = "This is a new file added to the working copy"
                .getBytes();
        /*
         * Initializes the library (it must be done before ever using the
         * library itself)
         */
        setupLibrary();

        if (args != null) {
            /*
             * Obtains a repository location URL
             */
            url = (args.length >= 1) ? args[0] : url;
            /*
             * Obtains a URL where a copy of the previuos url will be created
             */
            copyURL = (args.length >= 2) ? args[1] : copyURL;
            /*
             * Obtains a path for a workspace
             */
            myWorkspacePath = (args.length >= 3) ? args[2] : myWorkspacePath;
            /*
             * Obtains a revision
             */
            revision = (args.length >= 4) ? SVNRevision.create(Long
                    .parseLong(args[3])) : revision;
            /*
             * Obtains an account name (will be used to authenticate the user to
             * the server)
             */
            name = (args.length >= 5) ? args[4] : name;
            /*
             * Obtains a password
             */
            password = (args.length >= 6) ? args[5] : password;
        }
        /*
         * Creates a usre's credentials provider.
         */
        ISVNCredentialsProvider scp = new SVNSimpleCredentialsProvider(name,
                password);
        
        long committedRevision = -1;
        System.out.println("Making a new directory at '" + url + "'...");
        try{
            committedRevision = makeDirectory(scp, url, "making a new directory at '" + url + "'").getNewRevision();
        }catch(SVNException svne){
            System.err.println("error while making a new directory at '" + url + "': " + svne.getMessage());
            System.exit(1);
        }
        System.out.println("Committed to revision " + committedRevision);
        System.out.println();
        
        File wcDir = new File(myWorkspacePath);
        if (wcDir.exists()) {
            System.err.println("the destination directory '"
                    + wcDir.getAbsolutePath() + "' already exists!");
            System.exit(1);
        }
        wcDir.mkdirs();

        System.out.println("Checking out a working copy from '" + url + "'...");
        long checkoutRevision = -1;
        try {
            checkoutRevision = checkout(scp, url, revision, wcDir, true);
        } catch (SVNException svne) {
            /*
             * Perhaps a malformed URL is the cause of this exception.
             */
            System.err
                    .println("error while checking out a working copy for the location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }
        System.out.println("Checked out revision " + checkoutRevision);
        System.out.println();
        
        /*
         * show info for the working copy
         */
        try {
            showInfo(scp, wcDir, SVNRevision.WORKING, true);
        } catch (SVNException svne) {
            System.err
                    .println("error while getting info for the working copy at'"
                            + wcDir.getAbsolutePath()
                            + "': "
                            + svne.getMessage());
            System.exit(1);
        }
        System.out.println();

        /*
         * creating a new directory
         */
        File aNewDir = new File(wcDir, newDir);
        if (!aNewDir.mkdirs()) {
            System.err.println("failed to create a new directory '"
                    + aNewDir.getAbsolutePath() + "'.");
            System.exit(1);
        }
        /*
         * creating a new file in "/MyWorkspace/newDir/"
         */
        File aNewFile = new File(aNewDir, PathUtil.tail(newFile));
        try {
            if (!aNewFile.createNewFile()) {
                System.err.println("failed to create a new file '"
                        + aNewFile.getAbsolutePath() + "'.");
                System.exit(1);
            }
        } catch (IOException ioe) {
            aNewFile.delete();
            System.err.println("error while creating a new file '"
                    + aNewFile.getAbsolutePath() + "': " + ioe.getMessage());
            System.exit(1);
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(aNewFile);
            fos.write(fileText);
        } catch (FileNotFoundException fnfe) {
            System.err.println("the file '" + aNewFile.getAbsolutePath()
                    + "' is not found: " + fnfe.getMessage());
            System.exit(1);
        } catch (IOException ioe) {
            System.err.println("error while writing into the file '"
                    + aNewFile.getAbsolutePath() + "' is not found: "
                    + ioe.getMessage());
            System.exit(1);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ioe) {
                    //
                }
            }
        }

        System.out.println("Recursively scheduling a new directory '" + aNewDir.getAbsolutePath() + "' for addition...");
        try {
            addDirectory(scp, aNewDir);
        } catch (SVNException svne) {
            System.err.println("error while recursively adding the directory '"
                    + aNewDir.getAbsolutePath() + "': " + svne.getMessage());
            System.exit(1);
        }
        System.out.println();

        boolean isRecursive = true;
        boolean isRemote = false;
        boolean isReportAll = false;
        boolean isIncludeIgnored = true;
        boolean isCollectParentExternals = true;
        System.out.println("Status for '" + wcDir.getAbsolutePath() + "':");
        try {
            showStatus(scp, wcDir, isRecursive, isRemote, isReportAll,
                    isIncludeIgnored, isCollectParentExternals);
        } catch (SVNException svne) {
            System.err.println("error while performing status for '"
                    + wcDir.getAbsolutePath() + "': " + svne.getMessage());
            System.exit(1);
        }
        System.out.println();

        System.out.println("Updating '" + wcDir.getAbsolutePath() + "'...");
        long updatedRevision = -1;
        try {
            updatedRevision = update(scp, wcDir, SVNRevision.HEAD, true);
        } catch (SVNException svne) {
            System.err
                    .println("error while recursively updating the working copy '"
                            + wcDir.getAbsolutePath()
                            + "': "
                            + svne.getMessage());
            System.exit(1);
        }
        
        try {
            showStatus(scp, wcDir, isRecursive, isRemote, isReportAll,
                    isIncludeIgnored, isCollectParentExternals);
        } catch (SVNException svne) {
            System.err.println("error while performing status for '"
                    + wcDir.getAbsolutePath() + "': " + svne.getMessage());
            System.exit(1);
        }
        System.out.println("Updated to revision " + updatedRevision);
        System.out.println();

        System.out.println("Committing '" + wcDir.getAbsolutePath() + "'...");
        try {
            committedRevision = commit(scp, wcDir, true,
                    "'/newDir' with '/newDir/newFile.txt' were added")
                    .getNewRevision();
        } catch (SVNException svne) {
            System.err
                    .println("error while committing changes to the working copy '"
                            + wcDir.getAbsolutePath()
                            + "': "
                            + svne.getMessage());
            System.exit(1);
        }
        System.out.println("Committed to revision " + committedRevision);
        System.out.println();

        System.out
                .println("Locking (with stealing if the entry is already locked) '"
                        + aNewFile.getAbsolutePath() + "'.");
        try {
            lock(scp, aNewFile, true, "locking '/newDir'");
        } catch (SVNException svne) {
            System.err.println("error while locking the working copy file '"
                    + aNewFile.getAbsolutePath() + "': " + svne.getMessage());
            System.exit(1);
        }
        System.out.println();

        System.out.println("Status for '" + wcDir.getAbsolutePath() + "':");
        try {
            showStatus(scp, wcDir, isRecursive, isRemote, isReportAll,
                    isIncludeIgnored, isCollectParentExternals);
        } catch (SVNException svne) {
            System.err.println("error while performing status for '"
                    + wcDir.getAbsolutePath() + "': " + svne.getMessage());
            System.exit(1);
        }
        System.out.println();

        System.out.println("Copying '" + url + "' to '" + copyURL + "'...");
        try {
            committedRevision = copy(scp, url,
                    SVNRevision.create(checkoutRevision), copyURL, false,
                    "remotely copying '" + url + "' to '" + copyURL + "'")
                    .getNewRevision();
        } catch (SVNException svne) {
            System.err.println("error while copying '" + url + "' to '"
                    + copyURL + "': " + svne.getMessage());
            System.exit(1);
        }
        System.out.println("Committed to revision " + committedRevision);
        System.out.println();

        System.out.println("Switching '" + wcDir.getAbsolutePath() + "' to '"
                + copyURL + "'...");
        try {
            updatedRevision = switchToURL(scp, wcDir, copyURL,
                    SVNRevision.HEAD, true);
        } catch (SVNException svne) {
            System.err.println("error while switching '"
                    + wcDir.getAbsolutePath() + "' to '" + copyURL + "': "
                    + svne.getMessage());
            System.exit(1);
        }
        System.out.println("Updated to revision " + updatedRevision);
        System.out.println();

        /*
         * show info for the working copy
         */
        try {
            showInfo(scp, wcDir, SVNRevision.WORKING, true);
        } catch (SVNException svne) {
            System.err
                    .println("error while getting info for the working copy at'"
                            + wcDir.getAbsolutePath()
                            + "': "
                            + svne.getMessage());
            System.exit(1);
        }
        System.out.println();

        System.out.println("Scheduling '" + aNewDir.getAbsolutePath() + "' for deletion ...");
        try {
            delete(scp, aNewDir, true);
        } catch (SVNException svne) {
            System.err.println("error while schediling '"
                    + wcDir.getAbsolutePath() + "' for deletion: "
                    + svne.getMessage());
            System.exit(1);
        }
        System.out.println();

        System.out.println("Status for '" + wcDir.getAbsolutePath() + "':");
        try {
            showStatus(scp, wcDir, isRecursive, isRemote, isReportAll,
                    isIncludeIgnored, isCollectParentExternals);
        } catch (SVNException svne) {
            System.err.println("error while performing status for '"
                    + wcDir.getAbsolutePath() + "': " + svne.getMessage());
            System.exit(1);
        }
        System.out.println();

        System.out.println("Committing '" + wcDir.getAbsolutePath() + "'...");
        try {
            committedRevision = commit(
                    scp,
                    wcDir,
                    false,
                    "deleting '" + aNewDir.getAbsolutePath()
                            + "' from the filesystem as well as from the repository").getNewRevision();
        } catch (SVNException svne) {
            System.err
                    .println("error while committing changes to the working copy '"
                            + wcDir.getAbsolutePath()
                            + "': "
                            + svne.getMessage());
            System.exit(1);
        }
        System.out.println("Committed to revision " + committedRevision);
        System.exit(0);
    }

    /*
     * Initializes the library to work with a repository either via svn:// (and
     * svn+ssh://) or via http:// (and https://)
     */
    private static void setupLibrary() {
        /*
         * for DAV (over http and https)
         */
        DAVRepositoryFactory.setup();
        /*
         * for SVN (over svn and svn+ssh)
         */
        SVNRepositoryFactoryImpl.setup();
        /*
         * Working copy storage (default is file system).
         */
        FSEntryFactory.setup();
    }

    private static SVNCommitInfo commit(ISVNCredentialsProvider cp,
            File wcPath, boolean keepLocks, String commitMessage)
            throws SVNException {
        SVNCommitClient myCommitClient = new SVNCommitClient(cp);
        return myCommitClient.doCommit(new File[] { wcPath }, keepLocks,
                commitMessage, true);
    }

    private static long checkout(ISVNCredentialsProvider cp, String url,
            SVNRevision revision, File destPath, boolean isRecursive)
            throws SVNException {
        SVNUpdateClient myUpdateClient = new SVNUpdateClient(cp);
        myUpdateClient.setIgnoreExternals(false);
        return myUpdateClient.doCheckout(url, destPath, revision, revision,
                isRecursive);
    }

    private static long update(ISVNCredentialsProvider cp, File wcPath,
            SVNRevision updateToRevision, boolean isRecursive)
            throws SVNException {
        SVNUpdateClient myUpdateClient = new SVNUpdateClient(cp);
        myUpdateClient.setIgnoreExternals(false);
        return myUpdateClient.doUpdate(wcPath, updateToRevision, isRecursive);
    }

    private static long switchToURL(ISVNCredentialsProvider cp, File wcPath,
            String url, SVNRevision updateToRevision, boolean isRecursive)
            throws SVNException {
        SVNUpdateClient myUpdateClient = new SVNUpdateClient(cp);
        myUpdateClient.setIgnoreExternals(false);
        return myUpdateClient.doSwitch(wcPath, url, updateToRevision,
                isRecursive);
    }

    /*
     * Responsible for a performance of the working copy status.
     */
    private static void showStatus(ISVNCredentialsProvider cp, File wcPath,
            boolean isRecursive, boolean isRemote, boolean isReportAll,
            boolean isIncludeIgnored, boolean isCollectParentExternals)
            throws SVNException {
        SVNStatusClient myStatusClient = new SVNStatusClient(cp);
        myStatusClient.doStatus(wcPath, isRecursive, isRemote, isReportAll,
                isIncludeIgnored, isCollectParentExternals, new StatusHandler(
                        isRemote));
    }

    /*
     * Info
     */
    private static void showInfo(ISVNCredentialsProvider cp, File wcPath,
            SVNRevision revision, boolean isRecursive) throws SVNException {
        SVNWCClient myWCClient = new SVNWCClient(cp);
        myWCClient.doInfo(wcPath, revision, isRecursive, new InfoHandler());
        myWCClient = null;

    }

    private static void addDirectory(ISVNCredentialsProvider cp, File newDir)
            throws SVNException {
        SVNWCClient myWCClient = new SVNWCClient(cp);
        myWCClient.doAdd(newDir, false, false, false, true);
        myWCClient = null;
    }

    private static void lock(ISVNCredentialsProvider cp, File wcPath,
            boolean isStealLock, String lockComment) throws SVNException {
        SVNWCClient myWCClient = new SVNWCClient(cp);
        myWCClient.doLock(new File[] { wcPath }, isStealLock, lockComment);
        myWCClient = null;
    }

    private static void delete(ISVNCredentialsProvider cp, File wcPath,
            boolean force) throws SVNException {
        SVNWCClient myWCClient = new SVNWCClient(cp);
        myWCClient.doDelete(wcPath, force, false);
        myWCClient = null;
    }

    private static SVNCommitInfo copy(ISVNCredentialsProvider cp,
            String srcURL, SVNRevision srcPegRevision, String dstURL,
            boolean isMove, String commitMessage) throws SVNException {
        SVNCopyClient myCopyClient = new SVNCopyClient(cp);
        return myCopyClient.doCopy(srcURL, srcPegRevision, SVNRevision.HEAD,
                dstURL, null, isMove, commitMessage);
    }
    
    private static SVNCommitInfo makeDirectory(ISVNCredentialsProvider cp,
            String url, String commitMessage) throws SVNException{
        SVNCommitClient myCommitClient = new SVNCommitClient(cp);
        return myCommitClient.doMkDir(new String[]{url}, commitMessage);
    }

}