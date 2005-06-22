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
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
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
 * familiar with the Subversion command line client it won't take much time to match a 
 * method and an appropriate Subversion client's command.
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
 * to be a directory that will be created (it should consist of the URL of an existing
 * repository and the name of the directory itself that will be created just under
 * the repository directory - that is like 'svn mkdir URL' which creates a new 
 * directory given all the intermediate directories created); this operation is based
 * upon an URL - so, it's immediately committed to the repository;
 * 
 * 4)the next operation - checking out the directory created in the previous 
 * step to a local directory defined by myWorkspacePath; 
 * 
 * 5)the next operation - recursively getting and displaying info for each item  at 
 * the working revision in the working copy that was checked out in the previous 
 * step;
 * 
 * 6)the next operation - creating a new directory (newDir) with a file (newFile) in
 * the working copy and then recursively scheduling (if any subdirictories existed 
 * they would be also added) the created directory for addition;
 * 
 * 7)the next operation - recursively getting and displaying the working copy status
 * not including unchanged (normal) paths; the result must include those paths which
 * were scheduled for addition in the previous step; 
 * 
 * 8)the next operation - recursively updating the working copy; if any local items
 * are out of date they will be updated to the latest revision;
 * 
 * 9)the next operation - committing local changes to the repository; this operation
 * will add the directory with the file that were scheduled for addition to the 
 * repository;
 * 
 * 10)the next operation - locking the file added in the previous step (for example, 
 * if you temporarily need to keep a file locked to prevent someone's else 
 * modifications);
 * 
 * 11)the next operation - showing status once again (for example, to see that the 
 * file was locked);
 * 
 * 12)the next operation - copying with history one URL (url) to another one (copyURL)
 * within the same repository;
 * 
 * 13)the next operation - switching the working copy to a different URL - to copyURL
 * where url was copied to in the previous step;
 * 
 * 14)the next operation - recursively getting and displaying info on the root 
 * directory of the working copy to demonstrate that the working copy is now really
 * switched against copyURL;
 * 
 * 15)the next operation - scheduling the directory (newDir) for deletion;
 * 
 * 16)the next operation - showing status once again (for example, to see that the 
 * directory with all its entries were scheduled for deletion);
 * 
 * 17)the next operation - committing local changes to the repository; this operation
 * will delete the directory (newDir) with the file (newFile) that were scheduled for
 * deletion from the repository;
 * 
 * This example can be run for a locally installed Subversion repository via the
 * svn:// protocol. This is how you can do it:
 * 
 * 1)after you install the Subversion (available for download at 
 * http://subversion.tigris.org/) you should create a new repository in a
 * directory, like this (in a command line under Windows OS):
 * 
 * >svnadmin create X:\path\to\rep
 * 
 * 2)after the repository is created you can add a new account: open
 * X:\path\to\rep\, then move to \conf and open the file - passwd. In the file you'll
 * see the section [users]. Uncomment it and add a new account below the section
 * name, like:
 * 
 * [users] 
 * userName = userPassword.
 * 
 * In the program you may further use this account as user's credentials.
 * 
 * 3)the next step is to launch the custom Subversion server - svnserve - in a
 * background mode for the just created repository:
 * 
 * >svnserve -d -r X:\path\to
 * 
 * That's all. The repository is now available via svn://localhost/rep.
 * As it has been already mentioned url parameter should not be an existing path 
 * within the directory where you have created your repository (svn://localhost/rep)
 * but one level below (for example, svn://localhost/rep/MyRepos which is used by
 * default in the program) since the program starts with creating a new directory in
 * a repository. If this directory already exists the program will certainly fail.
 * 
 * While the program is running you'll see something like this:
 * 
	Making a new directory at 'svn://localhost/rep/MyRepos'...
	Committed to revision 165
	
	Checking out a working copy from 'svn://localhost/rep/MyRepos'...
	Checked out revision 165
	
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy
	URL: svn://localhost/rep/MyRepos
	Repository UUID: 466bc291-b22d-3743-ba76-018ba5011628
	Revision: 165
	Node Kind: dir
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 165
	Last Changed Date: Wed Jun 22 16:51:34 NOVST 2005
	
	Recursively scheduling a new directory 'N:\MyWorkingCopy\newDir' for addition...
	
	Status for 'N:\MyWorkingCopy':
	A          0     ?    ?                       N:\MyWorkingCopy\newDir
	A          0     ?    ?                       N:\MyWorkingCopy\newDir\newFile.txt
	
	Updating 'N:\MyWorkingCopy'...
	Updated to revision 165
	
	Committing changes for 'N:\MyWorkingCopy'...
	Committed to revision 166
	
	Locking (with stealing if the entry is already locked) 'N:\MyWorkingCopy\newDir\newFile.txt'.
	
	Status for 'N:\MyWorkingCopy':
	     K     166   166   userName               N:\MyWorkingCopy\newDir\newFile.txt
	
	Copying 'svn://localhost/rep/MyRepos' to 'svn://localhost/rep/MyReposCopy'...
	Committed to revision 167
	
	Switching 'N:\MyWorkingCopy' to 'svn://localhost/rep/MyReposCopy'...
	Updated to revision 167
	
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy
	URL: svn://localhost/rep/MyReposCopy
	Repository UUID: 466bc291-b22d-3743-ba76-018ba5011628
	Revision: 167
	Node Kind: dir
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 167
	Last Changed Date: Wed Jun 22 16:51:39 NOVST 2005
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy\newDir
	URL: svn://localhost/rep/MyReposCopy/newDir
	Repository UUID: 466bc291-b22d-3743-ba76-018ba5011628
	Revision: 167
	Node Kind: dir
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 166
	Last Changed Date: Wed Jun 22 16:51:38 NOVST 2005
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy\newDir\newFile.txt
	URL: svn://localhost/rep/MyReposCopy/newDir/newFile.txt
	Repository UUID: 466bc291-b22d-3743-ba76-018ba5011628
	Revision: 167
	Node Kind: file
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 166
	Last Changed Date: Wed Jun 22 16:51:38 NOVST 2005
	Properties Last Updated: Wed Jun 22 16:51:40 NOVST 2005
	Text Last Updated: Wed Jun 22 16:51:38 NOVST 2005
	Checksum: 023b67e9660b2faabaf84b10ba32c6cf
	
	Scheduling 'N:\MyWorkingCopy\newDir' for deletion ...
	
	Status for 'N:\MyWorkingCopy':
	D          167   166   userName               N:\MyWorkingCopy\newDir
	D          167   166   userName               N:\MyWorkingCopy\newDir\newFile.txt
	
	Committing changes for 'N:\MyWorkingCopy'...
	Committed to revision 168
 * 
 */
public class WorkingCopy {

    private static SVNCommitClient myCommitClient;
    private static SVNCopyClient myCopyClient;
    private static SVNWCClient myWCClient;
    private static SVNStatusClient myStatusClient;
    private static SVNUpdateClient myUpdateClient;
    
    public static void main(String[] args) {
        /*
         * Default values:
         */
        /*
         * Assuming that svn://localhost/rep already exists while
         * MyRepos is to be created
         */
        String url = "svn://localhost/rep/MyRepos";
        String copyURL = "svn://localhost/rep/MyReposCopy";
        String myWorkingCopyPath = "/MyWorkingCopy";
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
             * Obtains a URL that represents a new directory relative to an existing 
             * path in a repository
             */
            url = (args.length >= 1) ? args[0] : url;
            /*
             * Obtains a URL where a copy of the previuos url will be created (with
             * history)
             */
            copyURL = (args.length >= 2) ? args[1] : copyURL;
            /*
             * Obtains a path to be a working copy root directory
             */
            myWorkingCopyPath = (args.length >= 3) ? args[2] : myWorkingCopyPath;
            /*
             * Obtains an account name 
             */
            name = (args.length >= 4) ? args[3] : name;
            /*
             * Obtains a password
             */
            password = (args.length >= 5) ? args[4] : password;
        }
        /*
         * Creates a usre's credentials provider.
         */
        ISVNCredentialsProvider scp = new SVNSimpleCredentialsProvider(name,
                password);
        
        /*
         * The following 'SVN*Client' objects come from org.tmatesoft.svn.core.io
         * package and are only a part of that client's high-level API indended for 
         * managing working copies.    
         */
        
        /*
         * passing credentials provider when creating an instance of
         * SVNCommitClient
         */
        myCommitClient = new SVNCommitClient(scp, new CommitEventListener());
        /*
         * passing credentials provider when creating an instance of
         * SVNCopyClient
         */
        myCopyClient = new SVNCopyClient(scp);
        /*
         * passing credentials provider when creating an instance of
         * SVNWCClient
         */
        myWCClient = new SVNWCClient(scp);
        /*
         * passing credentials provider when creating an instance of
         * SVNStatusClient
         */
        myStatusClient = new SVNStatusClient(scp);
        /*
         * passing credentials provider when creating an instance of
         * SVNUpdateClient
         */
        myUpdateClient = new SVNUpdateClient(scp, new UpdateEventListener());

        long committedRevision = -1;
        System.out.println("Making a new directory at '" + url + "'...");
        try{
            /*
             * creating a new version comtrolled directory in a repository and 
             * displaying what revision the repository was committed to
             */
            committedRevision = makeDirectory(url, "making a new directory at '" + url + "'").getNewRevision();
        }catch(SVNException svne){
            error("error while making a new directory at '" + url + "'", svne);
        }
        System.out.println("Committed to revision " + committedRevision);
        System.out.println();
        
        /*
         * creates a local directory where the working copy will be checked out
         */
        File wcDir = new File(myWorkingCopyPath);
        if (wcDir.exists()) {
            error("the destination directory '"
                    + wcDir.getAbsolutePath() + "' already exists!", null);
        }
        wcDir.mkdirs();

        System.out.println("Checking out a working copy from '" + url + "'...");
        long checkoutRevision = -1;
        try {
            /*
             * recursively checking out a working copy from url into wcDir,
             * SVNRevision.HEAD means the latest revision to be checked out 
             */
            checkoutRevision = checkout(url, SVNRevision.HEAD, wcDir, true);
        } catch (SVNException svne) {
            error("error while checking out a working copy for the location '"
                            + url + "'", svne);
        }
        System.out.println("Checked out revision " + checkoutRevision);
        System.out.println();
        
        /*
         * recursively displaying info for wcDir at the current working revision 
         * in the manner of 'svn info -R' command
         */
        try {
            showInfo(wcDir, SVNRevision.WORKING, true);
        } catch (SVNException svne) {
            error("error while recursively getting info for the working copy at'"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        /*
         * creating a new local directory - "wcDir/newDir"
         */
        File aNewDir = new File(wcDir, newDir);
        if (!aNewDir.mkdirs()) {
            error("failed to create a new directory '" + aNewDir.getAbsolutePath() + "'.", null);
        }
        /*
         * creating a new file - "/MyWorkspace/newDir/newFile.txt"
         */
        File aNewFile = new File(aNewDir, PathUtil.tail(newFile));
        try {
            if (!aNewFile.createNewFile()) {
                error("failed to create a new file '"
                        + aNewFile.getAbsolutePath() + "'.", null);
            }
        } catch (IOException ioe) {
            aNewFile.delete();
            error("error while creating a new file '"
                    + aNewFile.getAbsolutePath() + "'", ioe);
        }

        /*
         * writing a text into the file
         */
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(aNewFile);
            fos.write(fileText);
        } catch (FileNotFoundException fnfe) {
            error("the file '" + aNewFile.getAbsolutePath() + "' is not found", fnfe);
        } catch (IOException ioe) {
            error("error while writing into the file '"
                    + aNewFile.getAbsolutePath() + "'", ioe);
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
            /*
             * recursively scheduling aNewDir for addition
             */
            addEntry(aNewDir);
        } catch (SVNException svne) {
            error("error while recursively adding the directory '"
                    + aNewDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        boolean isRecursive = true;
        boolean isRemote = false;
        boolean isReportAll = false;
        boolean isIncludeIgnored = true;
        boolean isCollectParentExternals = true;
        System.out.println("Status for '" + wcDir.getAbsolutePath() + "':");
        try {
            /*
             * status will be recursive on wcDir, won't cover the repository 
             * (only local status), won't cover unmodified entries, will disregard
             * svn:ignore property ignores (if any), won't ignore externals definitions
             * (if any as well)
             */
            showStatus(wcDir, isRecursive, isRemote, isReportAll,
                    isIncludeIgnored, isCollectParentExternals);
        } catch (SVNException svne) {
            error("error while recursively performing status for '"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        System.out.println("Updating '" + wcDir.getAbsolutePath() + "'...");
        long updatedRevision = -1;
        try {
            /*
             * recursively updates wcDir to the latest revision (SVNRevision.HEAD);
             * it's useful to do in real life as a working copy may contain out
             * of date entries:) 
             */
            updatedRevision = update(wcDir, SVNRevision.HEAD, true);
        } catch (SVNException svne) {
            error("error while recursively updating the working copy at '"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println("Updated to revision " + updatedRevision);
        System.out.println();

        System.out.println("Committing changes for '" + wcDir.getAbsolutePath() + "'...");
        try {
            /*
             * commiting changes in wcDir to the repository with saving items locked
             * after the commit succeeds (if they were locked); this will add
             * aNewDir & aNewFile to the repository. 
             */
            committedRevision = commit(wcDir, true,
                    "'/newDir' with '/newDir/newFile.txt' were added")
                    .getNewRevision();
        } catch (SVNException svne) {
            error("error while committing changes to the working copy at '"
                    + wcDir.getAbsolutePath()
                    + "'", svne);
        }
        System.out.println("Committed to revision " + committedRevision);
        System.out.println();

        System.out
                .println("Locking (with stealing if the entry is already locked) '"
                        + aNewFile.getAbsolutePath() + "'.");
        try {
            /*
             * locking aNewFile with stealing (if it has been already locked by someone
             * else) and a lock comment
             */
            lock(aNewFile, true, "locking '/newDir'");
        } catch (SVNException svne) {
            error("error while locking the working copy file '"
                    + aNewFile.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        System.out.println("Status for '" + wcDir.getAbsolutePath() + "':");
        try {
            /*
             * displaying status once again to see the file is really locked
             */
            showStatus(wcDir, isRecursive, isRemote, isReportAll,
                    isIncludeIgnored, isCollectParentExternals);
        } catch (SVNException svne) {
            error("error while recursively performing status for '"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        System.out.println("Copying '" + url + "' to '" + copyURL + "'...");
        try {
            /*
             * making a branch of url at copyURL - that is URL->URL copying
             * with history; not moving url, only copying;
             * 
             * checkoutRevision is to concretize url
             */
            committedRevision = copy(url,
                    SVNRevision.create(checkoutRevision), copyURL, false,
                    "remotely copying '" + url + "' to '" + copyURL + "'")
                    .getNewRevision();
        } catch (SVNException svne) {
            error("error while copying '" + url + "' to '"
                    + copyURL + "'", svne);
        }
       /*
        * displaying what revision the repository was committed to
        */
        System.out.println("Committed to revision " + committedRevision);
        System.out.println();

        System.out.println("Switching '" + wcDir.getAbsolutePath() + "' to '"
                + copyURL + "'...");
        try {
            /*
             * recursively switching wcDir to copyURL to the latest revision 
             * (SVNRevision.HEAD)
             */
            updatedRevision = switchToURL(wcDir, copyURL,
                    SVNRevision.HEAD, true);
        } catch (SVNException svne) {
            error("error while switching '"
                    + wcDir.getAbsolutePath() + "' to '" + copyURL + "'", svne);
        }
        System.out.println("Updated to revision " + updatedRevision);
        System.out.println();

        /*
         * recursively displaying info for the working copy once again to see
         * it was really switched to a new URL
         */
        try {
            showInfo(wcDir, SVNRevision.WORKING, true);
        } catch (SVNException svne) {
            error("error while recursively getting info for the working copy at'"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        System.out.println("Scheduling '" + aNewDir.getAbsolutePath() + "' for deletion ...");
        try {
            /*
             * forcing aNewDir to be scheduled for deletion
             */
            delete(aNewDir, true);
        } catch (SVNException svne) {
            error("error while schediling '"
                    + wcDir.getAbsolutePath() + "' for deletion", svne);
        }
        System.out.println();

        System.out.println("Status for '" + wcDir.getAbsolutePath() + "':");
        try {
            /*
             * recursively displaying status once more to see whether aNewDir
             * was really scheduled for deletion  
             */
            showStatus(wcDir, isRecursive, isRemote, isReportAll,
                    isIncludeIgnored, isCollectParentExternals);
        } catch (SVNException svne) {
            error("error while recursively performing status for '"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        System.out.println("Committing changes for '" + wcDir.getAbsolutePath() + "'...");
        try {
            /*
             * lastly committing changes in wcDir to the repository; all locked items
             * will be unlocked after the commit succeeds; this commit will remove
             * aNewDir from the repository. 
             */
            committedRevision = commit(
                    wcDir,
                    false,
                    "deleting '" + aNewDir.getAbsolutePath()
                            + "' from the filesystem as well as from the repository").getNewRevision();
        } catch (SVNException svne) {
            error("error while committing changes to the working copy '"
                    + wcDir.getAbsolutePath()
                    + "'", svne);
        }
        System.out.println("Committed to revision " + committedRevision);
        System.exit(0);
    }

    /*
     * Initializes the library to work with a repository either via svn:// (and
     * svn+ssh://) or via http:// (and https://)
     */
    private final static void setupLibrary() {
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

    /*
     * Creates a new version controlled directory (doesn't create any intermediate
     * directories) right in a repository. Like 'svn mkdir URL -m "some comment"' 
     * command. It's done by invoking 
     * SVNCommitClient.doMkDir(String[] urls, String commitMessage) which takes the 
     * following parameters:
     * 
     * urls - a String array of URL strings that are to be created;
     * 
     * commitMessage - a commit log message since a URL-based directory creation is 
     * immediately committed to a repository.
     */
    private final static SVNCommitInfo makeDirectory(String url, String commitMessage) throws SVNException{
        /*
         * Returns SVNCommitInfo containing information on the commit (revision number, 
         * etc.) 
         */
        return myCommitClient.doMkDir(new String[]{url}, commitMessage);
    }

    /*
     * Committs changes in a working copy to the repository. Like 
     * 'svn commit PATH -m "some comment"' command. It's done by invoking 
     * SVNCommitClient.doCommit(File[] paths, boolean keepLocks, String commitMessage,
     * boolean recursive) which takes the following parameters:
     * 
     * paths - working copy paths which changes are to be committed;
     * 
     * keepLocks - if true then doCommit(..) won't unlock locked paths; otherwise they will
     * be unlocked after a successful commit; 
     * 
     * commitMessage - a commit log message;
     * 
     * recursive - if true and a path corresponds to a directory then doCommit(..) recursively 
     * committs changes for the entire directory, otherwise - only for child entries of the 
     * directory;
     */
    private final static SVNCommitInfo commit(File wcPath, boolean keepLocks, String commitMessage)
            throws SVNException {
        /*
         * Recursive commit on wcPath.
         * Returns SVNCommitInfo containing information on the commit (revision number, 
         * etc.) 
         */
        return myCommitClient.doCommit(new File[] { wcPath }, keepLocks,
                commitMessage, false, true);
    }

    /*
     * Checks out a working copy from a repository. Like 'svn checkout URL[@REV] PATH (-r..)'
     * command; It's done by invoking 
     * SVNUpdateClient.doCheckout(String url, File dstPath, SVNRevision pegRevision, 
     * SVNRevision revision, boolean recursive) which takes the following parameters:
     * 
     * url - a URL - repository location where a working copy is to be checked out from;
     * 
     * dstPath - a local path where the working copy will be fetched into;
     * 
     * pegRevision - an SVNRevision representing a revision to concretize
     * url (what exactly URL a user means and is sure of being the URL he needs); in other
     * words that is the revision in which the URL is first looked up;
     * 
     * revision - a revision at which a working copy being checked out is to be; 
     * 
     * recursive - if true and url corresponds to a directory then doCheckout(..) recursively 
     * fetches out the entire directory, otherwise - only child entries of the directory;   
     */
    private final static long checkout(String url,
            SVNRevision revision, File destPath, boolean isRecursive)
            throws SVNException {
        /*
         * sets externals not to be ignored during the checkout
         */
        myUpdateClient.setIgnoreExternals(false);
        /*
         * returns the number of the revision at which the working copy is 
         */
        return myUpdateClient.doCheckout(url, destPath, revision, revision,
                isRecursive);
    }
    
    /*
     * Updates a working copy (brings changes from the repository into the working copy). 
     * Like 'svn update PATH' command; It's done by invoking 
     * SVNUpdateClient.doUpdate(File file, SVNRevision revision, boolean recursive) which 
     * takes the following parameters:
     * 
     * file - a working copy entry that is to be updated;
     * 
     * revision - a revision to which a working copy is to be updated;
     * 
     * recursive - if true and an entry (FILE) is a directory then doUpdate(..) recursively 
     * updates the entire directory, otherwise - only child entries of the directory;   
     */
    private final static long update(File wcPath,
            SVNRevision updateToRevision, boolean isRecursive)
            throws SVNException {
        /*
         * sets externals not to be ignored during the update
         */
        myUpdateClient.setIgnoreExternals(false);
        /*
         * returns the number of the revision wcPath was updated to
         */
        return myUpdateClient.doUpdate(wcPath, updateToRevision, isRecursive);
    }
    
    /*
     * Updates a working copy to a different URL. Like 'svn switch URL' command;
     * It's done by invoking SVNUpdateClient.doSwitch(File file, String url, 
     * SVNRevision revision, boolean recursive) which takes the following parameters:
     * 
     * file - a working copy entry that is to be switched to a new url;
     * 
     * url - a target url a working copy is to be updated against;
     * 
     * revision - a revision to which a working copy is to be updated;
     * 
     * recursive - if true and an entry (FILE) is a directory then doSwitch(..) recursively 
     * switches the entire directory, otherwise - only child entries of the directory;   
     */
    private final static long switchToURL(File wcPath,
            String url, SVNRevision updateToRevision, boolean isRecursive)
            throws SVNException {
        /*
         * sets externals not to be ignored during the switch
         */
        myUpdateClient.setIgnoreExternals(false);
        /*
         * returns the number of the revision wcPath was updated to
         */
        return myUpdateClient.doSwitch(wcPath, url, updateToRevision,
                isRecursive);
    }

    /*
     * Collects status information on local path(s). Like 'svn status (-u) (-N)'. 
     * command. It's done by invoking 
     * SVNStatusClient.doStatus(File path, boolean recursive, 
     * boolean remote, boolean reportAll, boolean includeIgnored, 
     * boolean collectParentExternals, ISVNStatusHandler handler) which takes the following 
     * parameters:
     * 
     * path - an entry which status info to be gathered;
     * 
     * recursive - if true and an entry is a directory then doStatus(..) collects status 
     * info not only for that directory but for each item inside stepping down recursively;
     * 
     * remote - if true then doStatus(..) will cover the repository (not only the working copy)
     * as well to find out what entries are out of date;
     * 
     * reportAll - if true then doStatus(..) will also include unmodified entries;
     * 
     * includeIgnored - if true then doStatus(..) will also include entries set to ignore 
     * (disregarding svn:ignore property ignores);
     * 
     * collectParentExternals - if true then externals definitions won't be ignored;
     * 
     * handler - an implementation of ISVNStatusHandler to process status info per each entry
     * doStatus(..) traverses; such info is incapsulated in an SVNStatus instance and
     * is passed to a handler's handleStatus(SVNStatus status) method where an implementor
     * decides what to do with it.  
     */
    private final static void showStatus(File wcPath, boolean isRecursive, boolean isRemote, boolean isReportAll,
            boolean isIncludeIgnored, boolean isCollectParentExternals)
            throws SVNException {
        /*
         * StatusHandler displays status information for each entry in the console (in the 
         * manner of the native Subversion command line client)
         */
        myStatusClient.doStatus(wcPath, isRecursive, isRemote, isReportAll,
                isIncludeIgnored, isCollectParentExternals, new StatusHandler(
                        isRemote));
    }

    /*
     * Collects information on local path(s). Like 'svn info (-R)' command.
     * It's done by invoking SVNWCClient.doInfo(File path, SVNRevision revision,
     * boolean recursive, ISVNInfoHandler handler) which takes the following 
     * parameters:
     * 
     * path - a local entry which info will be collected;
     * 
     * revision - a revision of an entry which info is interested in;
     * 
     * recursive - if true and an entry is a directory then doInfo(..) collects info 
     * not only for that directory but for each item inside stepping down recursively;
     * 
     * handler - an implementation of ISVNInfoHandler to process info per each entry
     * doInfo(..) traverses; such info is incapsulated in an SVNInfo instance and
     * is passed to a handler's handleInfo(SVNInfo info) method where an implementor
     * decides what to do with it.     
     */
    private final static void showInfo(File wcPath, SVNRevision revision, boolean isRecursive) throws SVNException {
        /*
         * InfoHandler displays information for each entry in the console (in the manner of
         * the native Subversion command line client)
         */
        myWCClient.doInfo(wcPath, revision, isRecursive, new InfoHandler());
    }
    
    /*
     * Puts directories and files under version control scheduling them for addition
     * to a repository. They will be added in a next commit. Like 'svn add PATH' 
     * command. It's done by invoking SVNWCClient.doAdd(File path, boolean force, 
     * boolean mkdir, boolean climbUnversionedParents, boolean recursive) which takes
     * the following parameters:
     * 
     * path - an entry to be scheduled for addition;
     * 
     * force - set to true to force an addition of an entry anyway;
     * 
     * mkdir - if true doAdd(..) creates an empty directory at path and schedules
     * it for addition, like 'svn mkdir PATH' command;
     * 
     * climbUnversionedParents - if true and the parent of the entry to be scheduled
     * for addition is not under version control doAdd(..) automatically schedules
     * the parent for addition, too;
     * 
     * recursive - if true and an entry is a directory then doAdd(..) recursively 
     * schedules all its inner entries for addition as well. 
     */
    private final static void addEntry(File wcPath) throws SVNException {
        myWCClient.doAdd(wcPath, false, false, false, true);
    }
    
    /*
     * Locks working copy paths, so that no other user can commit changes to them.
     * Like 'svn lock PATH' command. It's done by invoking 
     * SVNWCClient.doLock(File[] paths, boolean stealLock, String lockMessage) which
     * takes the following parameters:
     * 
     * paths - an array of local entries to be locked;
     * 
     * stealLock - set to true to steal the lock from another user or working copy;
     * 
     * lockMessage - an optional lock comment string.
     */
    private final static void lock(File wcPath, boolean isStealLock, String lockComment) throws SVNException {
        myWCClient.doLock(new File[] { wcPath }, isStealLock, lockComment);
    }
    
    /*
     * Schedules directories and files for deletion from version control upon the next
     * commit (locally). Like 'svn delete PATH' command. It's done by invoking 
     * SVNWCClient.doDelete(File path, boolean force, boolean dryRun) which takes the
     * following parameters:
     * 
     * path - an entry to be scheduled for deletion;
     * 
     * force - a boolean flag which is set to true to force a deletion even if an entry
     * has local modifications;
     * 
     * dryRun - set to true not to delete an entry but to check if it can be deleted;
     * if false - then it's a deletion itself.  
     */
    private final static void delete(File wcPath, boolean force) throws SVNException {
        myWCClient.doDelete(wcPath, force, false);
    }
    
    /*
     * Duplicates srcURL to dstURL (URL->URL)in a repository remembering history.
     * Like 'svn copy srcURL dstURL -m "some comment"' command. It's done by
     * invoking SVNCopyClient.doCopy(String srcURL, SVNRevision srcPegRevision, 
     * SVNRevision srcRevision, String dstURL, SVNRevision dstPegRevision,
     * boolean move, String commitMessage) which takes the following parameters:
     * 
     * srcURL - a URL that is to be copied;
     * 
     * srcPegRevision - an SVNRevision representing a revision to concretize
     * srcURL (what exactly URL a user means and is sure of being the URL he needs);
     * 
     * srcRevision - while the previous parameter is only used to concretize the URL
     * this one is provided to define what revision of srcURL exactly must be
     * duplicated;
     * 
     * dstURL - a URL where srcURL will be copied; if srcURL & dstURL are both 
     * directories then there are two cases: 
     * a) dstURL already exists - then doCopy(..) will duplicate the entire source 
     * directory and put it inside dstURL (for example, 
     * consider srcURL = svn://localhost/rep/MyRepos, 
     * dstURL = svn://localhost/rep/MyReposCopy, in this case if doCopy(..) succeeds 
     * MyRepos will be in MyReposCopy - svn://localhost/rep/MyReposCopy/MyRepos); 
     * b) dstURL doesn't exist yet - then doCopy(..) will create a directory and
     * recursively copy entries from srcURL into dstURL (for example, consider 
     * srcURL = svn://localhost/rep/MyRepos, dstURL = svn://localhost/rep/MyReposCopy, 
     * in this case if doCopy(..) succeeds MyRepos entries will be in MyReposCopy, like:
     * svn://localhost/rep/MyRepos/Dir1 -> svn://localhost/rep/MyReposCopy/Dir1...);  
     * 
     * dstPegRevision - like srcPegRevision but concretizes the destination URL (if
     * it exists, of course); if dstURL does not exist yet dstPegRevision is set to 
     * null;
     * 
     * move - if false (as in this example) then srcURL is only copied to dstURL what
     * corresponds to 'svn copy srcURL dstURL -m "some comment"'; but if it's true then
     * srcURL will be copied and deleted - 'svn move srcURL dstURL -m "some comment"' 
     * per se; 
     * 
     * commitMessage - a commit log message since URL->URL copying is immediately 
     * committed to a repository.
     */
    private final static SVNCommitInfo copy(String srcURL, SVNRevision srcPegRevision, String dstURL,
            boolean isMove, String commitMessage) throws SVNException {
        /*
         * SVNRevision.HEAD means the latest revision.
         * Returns SVNCommitInfo containing information on the commit (revision number, 
         * etc.) 
         */
        return myCopyClient.doCopy(srcURL, srcPegRevision, SVNRevision.HEAD,
                dstURL, null, isMove, commitMessage);
    }
    
    /*
     * Displays error information and exits. 
     */
    private final static void error(String message, Exception e){
        System.err.println(message+(e!=null ? ": "+e.getMessage() : ""));
        System.exit(1);
    }

}