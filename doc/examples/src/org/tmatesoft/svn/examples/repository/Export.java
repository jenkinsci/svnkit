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
package org.tmatesoft.svn.examples.repository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.diff.ISVNRAData;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNRAFileData;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/*
 * This example program illustrates how you can export a clean directory tree 
 * from a repository using the SVNRepository.update method. Actually, a checkout 
 * (compatible with the native Subversion command line client) works in a similar 
 * way but additionally administrative directories are created (one .svn directory
 * per each exported directory).
 * 
 * Basic aspects of this example:
 * 
 * 0)first of all the library is initialized (setupLibrary() method) - it must be
 * done prior to using the library;
 * 
 * 1)an SVNRepository is created to the location (represented by an
 * SVNRepositoryLocation) that will be the root of the repository tree to be exported;
 * 
 * 2)user's authentication is usually non-necessary for reading operations however the
 * repository may have a restriction to accept requests of only authenticated users;
 * the example shows how to provide user's authentication info;
 * 
 * 3)INTRO: you have to implement ISVNReporterBaton and ISVNEditor to affect the 
 * behaviour of the SVNRepository.update method (for ISVNReporterBaton an 
 * implementation depends on whether you want to update your copy of a repository 
 * node that may have been changed since you last updated it (or checked out) or you
 * have no copy yet but want to check it out; for ISVNEditor an implementation 
 * depends on how exactly you would like either to save the exported copy - if you 
 * perform a checkout, - or bring  the copy up to date if you already have the copy). 
 * The aim of ISVNReporterBaton is to report to the repository server about the state 
 * of the user's working files and directories when they are updated as well as report
 * that there are no directories and files yet, they are to be exported - in the case 
 * of a checkout. Then having got this description information the server sends
 * commands which are translated into calls to your ISVNEditor implementation methods 
 * where you define how exactly files and directories to be updated/exported.
 * 
 * Calling SVNRepository.update you also provide the number of the revision you wish to
 * be updated to. As for an export/checkout - this will be the revision of the 
 * exported/checked out repository tree copy.
 * 
 * For more details see descriptions of UpdateReporterBaton, UpdateEditor in this 
 * program code.
 * 
 * If the program succeeds you'll see something like this:
 * 
 * Exported revision: 82   
 */
public class Export {
    /*
     * args parameter is used to obtain a repository location URL, a local path 
     * (relative or absolute) where a copy will be created, user's account name &
     * password to authenticate him to the server.
     */
    public static void main(String[] args) {
        /*
         * Default values:
         */
        String url = "http://72.9.228.230:8080/svn/jsvn/branches/0.9.0/doc";
        String name = "anonymous";
        String password = "anonymous";
        String exportDirPath = "/export";
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
             * Obtains a local dir path where the repository tree
             * will be exported to
             */
            exportDirPath = (args.length >= 2) ? args[1] : exportDirPath;
            /*
             * Obtains an account name (will be used to authenticate the user to
             * the server)
             */
            name = (args.length >= 3) ? args[2] : name;
            /*
             * Obtains a password
             */
            password = (args.length >= 4) ? args[3] : password;
        }

        File exportDir = new File(exportDirPath);
        if (exportDir.exists()) {
            System.err.println("the destination directory '"
                    + exportDir.getAbsolutePath() + "' already exists!");
            System.exit(1);
        }
        exportDir.mkdirs();

        SVNRepositoryLocation location;
        SVNRepository repository = null;
        try {
            /*
             * Parses the URL string and creates an SVNRepositoryLocation which
             * represents the repository location - it can be any versioned
             * entry inside the repository.
             */
            location = SVNRepositoryLocation.parseURL(url);
            /*
             * Creates an instance of SVNRepository to work with the repository.
             * All user's requests to the repository are relative to the
             * repository location used to create this SVNRepository.
             */
            repository = SVNRepositoryFactory.create(location);
        } catch (SVNException svne) {
            /*
             * Perhaps a malformed URL is the cause of this exception.
             */
            System.err
                    .println("error while creating an SVNRepository for the location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }

        /*
         * Creates a usre's authentication manager.
         * readonly=true - should be always true when providing options to 
         * SVNRepository since this low-level class is not intended to work
         * with working copy config files
         */
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(name, password);

        /*
         * Sets the manager of the user's authentication information that will 
         * be used to authenticate the user to the server (if needed) during 
         * operations handled by the SVNRepository.
         */
        repository.setAuthenticationManager(authManager);

        SVNNodeKind nodeKind = null;
        try {
            /*
             * Checks up if the current path really corresponds to a directory.
             * If doesn't the program exits. SVNNodeKind is that one who says what
             * is located at a path in a revision. -1 means the latest revision.
             */
            nodeKind = repository.checkPath("", -1);
        } catch (SVNException svne) {
            System.err
                    .println("error while getting the node kind of the repository location dir: "
                            + svne.getMessage());
            System.exit(1);
        }

        if (nodeKind == SVNNodeKind.NONE) {
            System.err.println("There is no entry at '" + url + "'.");
            System.exit(1);
        } else if (nodeKind == SVNNodeKind.FILE) {
            System.err.println("The entry at '" + url
                    + "' is a file while a directory was expected.");
            System.exit(1);
        }

        long latestRevision = -1;
        try {
            /*
             * Gets the latest revision number of the repository.
             */
            latestRevision = repository.getLatestRevision();
        } catch (SVNException svne) {
            System.err
                    .println("error while fetching the latest repository revision: "
                            + svne.getMessage());
            System.exit(1);
        }

        try {
            /*
             * This call will make a simple checkout (export) of the entire directory
             * "/doc" being at the latest revision from the repository to your 
             * exportDirPath.
             * 
             * latestRevision is the repository revision itself we're interested in.
             * 
             * the 2nd parameter - target - is null as we are not restricting the
             * checkout scope to only this entry, we need the entire directory.
             * 
             * the 3rd parameter - recursive - is true as we're not restricting
             * the checkout scope to only entries located in "/doc" directory,
             * we need the full tree.
             * 
             * the 4th parameter is an ISVNReporterBaton who will report to the 
             * server that we need to checkout the "/doc" directory.
             * 
             * the 5th parameter is an ISVNEditor who will "make" the work - create
             * the local copy of "/doc". 
             */
            repository.update(latestRevision, null, true,
                    new ExportReporterBaton(), new ExportEditor(
                            exportDirPath, new WorkspaceMediator()));
        } catch (SVNException svne) {
            System.err.println("error while exporting '" + url + "': "
                    + svne.getMessage());
            svne.printStackTrace();
            System.exit(1);
        }
        System.out.println("Exported revision: " + latestRevision);
        System.exit(0);
    }

    /*
     * Initializes the library to work with a repository either via 
     * svn:// (and svn+ssh://) or via http:// (and https://)
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
    }

    /*
     * When SVNRepository.update is called ISVNReporterBaton.report is invoked
     * to make reports (with the help of ISVNReporter) of the working files/directories
     * state. For example, if the scope is to update existing local copies to some 
     * definite revision (that is passed to SVNRepository.update) 
     * ISVNReporterBaton.report should describe to the server revisions of all the 
     * copies if their revisions differ from the update target revision. 
     * Suppose you have got the following tree you have somewhen checked out from a
     * repository:
     *  
     *  /(4)
     *  /dirA(5)/
     *   	    /dirB(6)/
     *   	   		    /file1.txt(6)
     *          /dirC(4)/
     *           	    /file2.txt(5)
     *          /file3.txt(7)
     *           
     * Numbers in brackets correspond to revisions. Well, you see that the root 
     * directory has the revision number=4, dirA - 5, dirB - 6 and so on. When you
     * call SVNRepository.update to bring the root directory up to date say to revision
     * 8 you should describe the revisions of all the entries top-down for each 
     * directory (starting with the root):
     *	
     *	public void report(ISVNReporter reporter) throws SVNException {
     *		//for the root directory
     *		reporter.setPath("", null, 4, false);
     *		
     *		//for "/dirA"
     *		reporter.setPath("/dirA", null, 5, false);
     *		
     *		//for "/dirA/dirB"
     *		reporter.setPath("/dirA/dirB", null, 6, false);
     *		
     *		//for "/dirA/dirB/file1.txt"
     *		reporter.setPath("/dirA/dirB/file1.txt", null, 6, false);
     *		
     *		//for "/dirA/dirC"
     *		reporter.setPath("/dirA/dirC", null, 4, false);
     *		
     *		....//and so on for all entries which revisions differ from 8
     *		
     *		//always called at the end of the report - when the state of the 
     *		//entire tree is described.
     *		reporter.finishReport();
     *	}
     *
     * Several significant moments:
     * - if some file was locked then you should provide the lock-token for
     *   that entry as the 2nd parameter of ISVNReporter.setPath(). For example:
     *
     *		/(4)
     *	    /file.txt(7, locked)
     *	 
     *   file.txt is at the 7th revision and was locked.
     *
     *		reporter.setPath("/file.txt", lockToken, 7, false);
     *
     *   Even though the revision of the locked file is the same as the target update
     *   revision ISVNReporter.setPath is called for such file anyway.  
     *  
     * - The last parameter of ISVNReporter.setPath - boolean flag startEmpty - must be
     *   true for a checkout. Also it's set to true for those directories that were not 
     *   updated at a previous time due to errors (if such situation had a place). So,
     * 	 startEmpty=true means the directory is still empty.
     * 	 In other cases it must be false.
     * 
     * - If a user's local working copy is updated against a URL different from that
     * 	 it was checked out from, then instead of calling reporter.setPath you should
     * 	 call:
     *      
     * 		reporter.linkPath(newRepositoryLocation, path, lockToken, revision, false);   
     * 	 
     * 	 newRepositoryLocation is an SVNRepositoryLocation which is the new parent root
     * 	 (meaning path is relative to this root since this moment). That's the only
     * 	 difference between just an update and a switch.
     * 
     * - If an entry was scheduled for deletion or if it's a missing directory (that was
     * 	 accidentally deleted) then you should call:
     * 		
     * 		//report that the path was deleted
     * 		reporter.deletePath(path);
     * 
     * - At the end of the report call ISVNReporter.finishReport() that denotes the end
     * 	 of the report.  
     * - One more important moment: if during a report an exception occured - the report
     * 	 failed - call ISVNReporter.abortReport() that properly finishes the report in a
     * 	 case of a fault.
     * 
     * Well, that's about how it works in common words. As for this example program 
     * there's no need in a full report since we're only going to export entry(ies) from 
     * a repository. So, ExportReporterBaton is a simple implementation of 
     * ISVNReporterBaton that describes that intention.
	 */ 
    private static class ExportReporterBaton implements ISVNReporterBaton {
        /*
		 * Reports that the repository node tree (the location to which the 
		 * SVNRepository is created) is to be exported.
		 *  
		 * "" - if the target parameter in the SVNRepository.update call is null then 
		 * "" means the directory that the SVNRepository object was created for; 
		 * otherwise if the target parameter is non-null then "" means target itself.
		 * In this example program target is null and therefore "" means the directory 
		 * the SVNRepository is created to. 
		 * 
		 * lockToken is null as this parameter is irrelevant in an export.
		 * 
		 * rev=0 - revision being described is practically irrelevant (therefore it can
		 * be any non-negative value that doesn't exceed the real latest revision of the
		 * repository); significant is the revision of the export scope being  passed to 
		 * SVNRepository.update. (But if this is an update of an existing copy, 
		 * certainly you are to provide the real revision for each entry).
		 *  
		 * The last flag (startEmpty) is set to true says that there are no local 
		 * copies yet and they are to be exported from the repository.     
         */
        public void report(ISVNReporter reporter) throws SVNException {
            try{
	            reporter.setPath("", null, 0, true);
	            /*
	             * Don't forget to finish the report!
	             */
	            reporter.finishReport();
            }catch(SVNException svne){
                try{
                    reporter.abortReport();
                }catch(SVNException svneInner){
                    //
                }
                throw svne;
            }
        }
    }

    /*
     * When ISVNReporterBaton has finished his work and the server knows everything
     * of the user's copy (dirs and files) state it sends to the client commands as
     * well as data (file/dir properties and file delta) to bring his copy up to date.
     * These commands are translated into calls to ISVNEditor methods.
     * 
     * Suppose there's the following working copy tree which is being updated 
     * recursively (starting with the directory the update was initiated on and 
     * moving deep down) to 8th revision:
     *  
     *  /(5)
     *  /dirA(5)/
     *      	/file1.txt(5)
     *          /file2.txt(5)
     *        
     * Assume that only file1.txt is out of date and must be updated. The server sends 
     * commands to update the file which are translated into series of calls to 
     * ISVNEditor methods. Here is the scheme of this process (an implementor himself
     * doesn't make these calls, his aim is only to provide an ISVNEditor 
     * implementation; the following is only an illustration describing how the JavaSVN
     * library invokes ISVNEditor methods):
     * 		
     * 		//sets the target revision the copy is being updated to.
     * 		editor.targetRevision(revision)
     * 		
     * 		//processing starts with the parent directory the update was
     * 		//run for - "/"; now modifications can be applied to the opened directory.
     * 		editor.openRoot(revision);
     * 		
     * 		// changing root directory properties
     * 		editor.changeDirProperty(propertyName1, propertyValue1);
     * 		editor.changeDirProperty(propertyName2, propertyValue2);
     *      .....................................
     * 		
     * 		//opens "/dirA".
     * 		editor.openDir("/dirA", revision);
     *
     * 		//now modifications can be applied to the opened directory. For example,
     * 		//changing its properties.
     *  	//Also all further calls like editor.openFile(...)/editor.addFile(...)
     * 		//or editor.openDir(...)/editor.addDir(...) are relative to the currently
     * 		//opened directory.
     * 		editor.changeDirProperty(propertyName1, propertyValue1);
     * 		editor.changeDirProperty(propertyName2, propertyValue2);
     *      .....................................
     *       
     * 		//opens file "file1.txt" to modify it
     * 		editor.openFile("/dirA/file1.txt", revision);
     * 		
     * 		//changing properties of "file1.txt"
     * 		editor.changeFileProperty(propertyName1, propertyValue1);
     * 		editor.changeFileProperty(propertyName2, propertyValue2);
     * 		.....................................
     * 
     * 		//file contents are out of date - the server sends delta
     * 		//(the difference between the local BASE-revision copy and the file in 
     * 		//the repository).
     * 		//baseChecksum is provided by the server to make certain of the delta
     * 		//will be applied correctly - the client should compare it
     * 		//with his own one evaluated upon the contents of the file at the BASE
     * 		//revision - that is the state of the file it had just after the previous
     * 		//update (or checkout). If both checksums match each other - it's ok, the 
     * 		//delta can be applied correctly, if don't - may be the local file is
     * 		//corrupted, that's an error.
     * 		editor.applyTextDelta(baseChecksum);
     * 		
     * 		//well, if the previous step was ok, the next step is to receive the delta
     * 		//itself. ISVNEditor.textDeltaChunk(SVNDiffWindow) receives an 
     * 		//SVNDiffWindow - this is an object which contains instructions on how the
     * 		//delta (the entire delta or a part of it when the delta is too big) must 
     * 		//be applied. If the delta is too big ISVNEditor.textDeltaChunk is called 
     * 		//several times to pass all parts of the delta; in this case all passed 
     * 		//diffWindows should be accumulated and associated with their OutputStreams
     * 		//(each call to ISVNEditor.textDeltaChunk returns an OutputStream as a 
     * 		//storage where delta is written).
     * 		OutputStream os=editor.textDeltaChunk(diffWindow);
     * 
     * 		//the following is called when all the delta is received.
     * 		//that is where it's applied to a local file; in this illustration
     * 		//"file1.txt" is modified.
     * 		editor.textDeltaEnd();
     * 		
     * 		//the final point of the file modification: once again the server sends 
     * 		//a checksum to control if the resultant file ("file1.txt") was modified 
     * 		//correctly; the client repeats the operation of comparing the got checksum
     * 		//with the own one evaluated upon the resultant file contents. 
     * 		editor.closeFile(textChecksum);
     * 		
     * 		//closes the directory  - "/dirA"
     * 		editor.closeDir();
     * 
     * 		//closes the root directory - "/"
     * 		editor.closeDir();
     * 		
     * 		//editing ends with a call to ISVNEditor.closeEdit() which returns
     * 		//the commit information (SVNCommitInfo) - what revision the copy is
     * 		//updated to, who is the author of the changes, when the changes were
     * 		//committed.   
     * 		SVNCommitInfo commitInfo = editor.closeEdit();
     * 
     * That is how an update runs in common words. The described scheme is analogous for
     * the case when more than one file is out of date (what is more actual in reality), 
     * the local copy tree is processed (by an ISVNEditor) top-down for each directory 
     * and file that must be updated.
     * 
     * Well, the case of a checkout is a little bit different. ISVNEditor.openDir, 
     * ISVNEditor.openFile are not called, instead ISVNEditor.addDir, ISVNEditor.addFile
     * are called for each directory and file to be checked out. This is a principal 
     * model of how ISVNEditor methods are invoked during a checkout(suppose we are 
     * checking out some node tree from a repository what will lead us to the previous
     * illustrartion when the local copy already exists):
     * 
     * 		//sets the target revision of the copy being checked out.
     * 		editor.targetRevision(revision)
     * 		
     * 		//ISVNEditor.openRoot is not called;
     * 		//setting root directory properties.
     * 		editor.changeDirProperty(propertyName, propertyValue);
     * 		
     * 		//adds "/dirA". copyDirFromPath & copyFromRevision - are irrelevant in
     * 		//an update editor.
     * 		//If you want to have a compatibility with the native Subversion command
     * 		//line client in the case of a checkout (not simply an export) an 
     * 		//implementation of this method should create an administrative area 
     * 		//(.svn directory) for the directory being added ("/dirA"). 
     * 		editor.addDir("/dirA", copyDirFromPath, CopyFromRevision);
     *
     * 		//setting the directory properties - they may be stored in the 
     * 		//previously created .svn directory (SVN command line client 
     * 		//compatibility).
     *  	//Also all further calls like editor.addFile(...)/editor.addDir(...)
     * 		//are relative to the added directory.
     * 		editor.changeDirProperty(propertyName1, propertyValue1);
     * 		editor.changeDirProperty(propertyName2, propertyValue2);
     *      .....................................
     *  
     * 		//adds file "file1.txt". copyFileFromPath & copyFromRevision - are irrelevant
     * 		//in an update editor.
     * 		editor.addFile("/dirA/file1.txt", copyFileFromPath, CopyFromRevision);
     * 		
     * 		//setting properties of "file1.txt". May be saved them in .svn directory
     * 		//for compatibility with the native SVN command line client.
     * 		editor.changeFileProperty(propertyName1, propertyValue1);
     * 		editor.changeFileProperty(propertyName2, propertyValue2);
     * 		.....................................
     * 
     * 		//if the file is empty  - only ISVNEditor.applyTextDelta and 
     * 		//ISVNEditor.textDeltaEnd are called. Otherwise ISVNEditor.textDeltaChunk
     * 		//is also invoked.
     *  
     * 		//baseChecksum is irrelevant for a checkout.
     * 	    editor.applyTextDelta(baseChecksum);
     * 		
     * 		//writing file contents (delta is contents in this case).
     * 		OutputStream os1=editor.textDeltaChunk(diffWindow1);
     *		//if "file1.txt" is too big...
     * 		OutputStream os2=editor.textDeltaChunk(diffWindow2);
     * 		.....................................
     * 		
     * 		//all contents are received, "file1.txt" can be created
     * 		editor.textDeltaEnd();
     * 		
     * 		//the final point of the file modification: once again the server sends 
     * 		//a checksum to control if the resultant file ("file1.txt") was transmitted 
     * 		// and constructed correctly; the client compares the got checksum
     * 		//with the own one evaluated upon the resultant file contents.
     * 		//It may be this method implementation where a copy of the file ("file1.txt")
     * 		// - the BASE revision file copy - is stored in .svn directory (for 
     * 		//compatibility with the native SVN command line client). 
     * 		editor.closeFile(textChecksum);
     * 
     *		//again if you want to have a compatibility with the native SVN command line
     *		//client each entry (wheteher it's a file or a directory) that is added 
     *		//should be reflected in its parent's administrative directory - .svn
     * 
     * 		//adds file "file2.txt".  
     * 		editor.addFile("/dirA/file2.txt", copyFileFromPath, CopyFromRevision);
     * 		
     * 		............................................//so on
     * 		
     * 		//closes the directory  - "/dirA"
     * 		editor.closeDir();
     * 		
     * 		//closes the root directory - "/"
     * 		editor.closeDir();
     * 		
     * 		SVNCommitInfo commitInfo = editor.closeEdit();
     * 
     * ExportEditor is a simple ISVNEditor implementation that stores exported 
     * directories and files to a client's fylesystem (not creating .svn directories
     * for them). ExportEditor implements the following methods of the ISVNEditor
     * interface: addDir, addFile, textDeltaChunk, textDeltaEnd, closeFile, 
     * changeFileProperty. The rest ISVNEditor methods are empty - not used in this
     * example program.
     */
    private static class ExportEditor implements ISVNEditor {
        private String myRootDirectory;

        private ISVNWorkspaceMediator myMediator;

        private String myCurrentPath;

        private List myDiffWindows;

        private Map myFileProperties;
        /*
         * root - the local directory where the node tree is to be exported into.
         * mediator - used for temporary delta storage allocations.
         */
        public ExportEditor(String root, ISVNWorkspaceMediator mediator) {
            myRootDirectory = (root != null) ? root : myRootDirectory;
            myMediator = (mediator != null) ? mediator
                    : new WorkspaceMediator();
        }

        /*
         * Called when a new directory is to be added.
         * path - relative to the root directory that is being exported.
         * Creates a directory in a filesystem (relative to myRootDirectory).
         */
        public void addDir(String path, String copyFromPath,
                long copyFromRevision) throws SVNException {
            File newDir = new File(myRootDirectory, path);
            if (!newDir.exists()) {
                if (!newDir.mkdirs()) {
                    throw new SVNException(
                            "error: failed to add the directory '"
                                    + newDir.getAbsolutePath() + "'.");
                }
            }
        }

        /*
         * Called when a new file is to be added.
         * path is relative to the root directory that is being exported 
         * (myRootDirectory).
         */
        public void addFile(String path, String copyFromPath,
                long copyFromRevision) throws SVNException {
            File file = new File(myRootDirectory, path);

            if (file.exists()) {
                throw new SVNException("error: exported file '"
                        + file.getAbsolutePath() + "' already exists!");
            }
            /*
             * remember the path
             */
            myCurrentPath = path;
            myFileProperties = new HashMap();
        }

        /*
         * Provides an OutputStream where file delta (contents) sent by the server
         * will be written. diffWindow will be associated with this OutputStream
         * because exactly this diffWindow has got instructions on how to apply 
         * the delta that will be written exactly in this OutputStream.
         * For too "weighty" files delta may be devided into parts, therefore
         * ISVNEditor.textDeltaChunk will be called a number of times.   
         */
        public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow)
                throws SVNException {
            if (myDiffWindows == null) {
                /*
                 * this is the collection for all diffWindows (if any)
                 * which will be supplied
                 */
                myDiffWindows = new LinkedList();
            }
            myDiffWindows.add(diffWindow);
            try {
                return myMediator.createTemporaryLocation(myCurrentPath,
                        diffWindow);
            } catch (IOException ioe) {
                throw new SVNException(ioe);
            }
        }
        
        /*
         * All delta is transmitted. Now it's time to apply it.
         */
        public void textDeltaEnd(String path) throws SVNException {
            File newFile = new File(myRootDirectory, myCurrentPath);
            try {
                newFile.createNewFile();
            } catch (IOException ioe) {
                newFile.delete();
                throw new SVNException(ioe);
            }
            /*
             * The file should be preliminary wrapped in an ISVNRAData since
             * the SVNDiffWindow doesn't work directly with the file itself
             * but by means of the ISVNRAData interface.  
             */
            ISVNRAData target = new SVNRAFileData(newFile, false);

            /*
             * if myDiffWindows is null - the file is empty.
             */
            if (myDiffWindows != null) {
                try {
                    for (Iterator windows = myDiffWindows.iterator(); windows
                            .hasNext();) {
                        /*
                         * obtains a next SVNDiffWindow. 
                         */
                        SVNDiffWindow window = (SVNDiffWindow) windows.next();
                        if (window == null)
                            continue;

                        InputStream newData = null;
                        
                        try {
                            /*
                             * Gets the InputStream based upon the OutpruStream (where
                             * the delta was written to) that is associated with the
                             * current window
                             */
                            newData = myMediator.getTemporaryLocation(window);
                            /*
                             * Here the delta is applied.
                             * target is the file where the dleta is being written.
                             * 
                             * newData - source bytes stored in a temporary delta 
                             * storage.
                             * 
                             * The last parameter - offset in the file - is to be the 
                             * current file size. If some delta chunk consists of bytes
                             * of the same value the server won't transmit the full 
                             * chunk but provide one byte and an instruction how many
                             * times it should be repeated. These bytes are written to
                             * the current end of the file.  
                             */
                            window.apply(target, target, newData, target.length());
                        } catch (IOException ioe) {
                            throw new SVNException(
                                    "error while fetching a temporary delta storage.");
                        } finally {
                            if (newData != null) {
                                try {
                                    newData.close();
                                } catch (IOException e1) {
                                    //
                                }
                            }
                            /*
                             * doesn't need the temporary storage anymore.
                             */
                            myMediator.deleteTemporaryLocation(window);
                        }
                    }
                } finally {
                    try {
                        target.close();
                    } catch (IOException ioe) {
                        //
                    }
                    myDiffWindows.clear();
                    myDiffWindows = null;
                }
            }
        }
        /*
         * Saves a file property.
         */
        public void changeFileProperty(String path, String name, String value)
                throws SVNException {
            myFileProperties.put(name, value);
        }
        /*
         * File update completed.
         */
        public void closeFile(String path, String textChecksum) throws SVNException {
            myCurrentPath = null;
            myFileProperties = null;
        }
        
        /*
         * The rest ISVNEditor methods are left empty since there's no work for them.
         */
        
        /*
         * Should be implemented to receive the revision a copy is being 
         * updated to.
         */
        public void targetRevision(long revision) throws SVNException {
        }
        
        /*
         * Should be implemented to perform actions after an update finishes. 
         */
        public SVNCommitInfo closeEdit() throws SVNException {
            return null;
        }

        /*
         * Should be implemented to perform actions when the root directory
         * is opened. Not called during a checkout.
         */
        public void openRoot(long revision) throws SVNException {
        }
        
        /*
         * Should be implemented to delete a local entry as it was deleted in
         * a repository.
         */
        public void deleteEntry(String path, long revision) throws SVNException {
        }
        
        /*
         * Should be implemented to perform actions on that fact that 
         * a directory can not be checked out from a repository - will
         * be absent in a local copy.
         */
        public void absentDir(String path) throws SVNException {
        }

        /*
         * Should be implemented to perform actions on that fact that 
         * a file can not be checked out from a repository - will
         * be absent in a local copy.
         */
        public void absentFile(String path) throws SVNException {
        }
        
        /*
         * Should be implemented to perform actions on opening a file
         * for further modifications.
         */
        public void openFile(String path, long revision) throws SVNException {
        }
        
        /*
         * Should be implemented to compare the received baseChecksum with the
         * one which an implementor evaluates upon the BASE revision copy of the
         * file. baseChecksum is irrelevant in a checkout.
         */
        public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        }
        
        /*
         * Should be implemented to perform actions on closing the currently
         * opened directory.
         */
        public void closeDir() throws SVNException {
        }
        
        /*
         * Should be implemented to perform actions on opening a directory
         * that is to be modified.
         */
        public void openDir(String path, long revision) throws SVNException {
        }
        
        /*
         * Should be implemented to change/save directory properties.
         */
        public void changeDirProperty(String name, String value)
                throws SVNException {
        }
        
        /*
         * Should be implemented to perform actions if an editor fails.
         */
        public void abortEdit() throws SVNException {
        }
    }

    /*
     * This class is to be used for temporary storage allocations needed by an
     * ISVNEditor to store file delta received from the server. 
     */
    private static class WorkspaceMediator implements ISVNWorkspaceMediator {
        private Map myTmpFiles = new HashMap();

        public String getWorkspaceProperty(String path, String name)
                throws SVNException {
            return null;
        }

        public void setWorkspaceProperty(String path, String name, String value)
                throws SVNException {
        }

        /*
         * Creates a temporary file delta storage. id will be used as the
         * temporary storage identifier. Returns an OutputStream to write the
         * delta data into the temporary storage.
         */
        public OutputStream createTemporaryLocation(String path, Object id)
                throws IOException {
            ByteArrayOutputStream tempStorageOS = new ByteArrayOutputStream();
            myTmpFiles.put(id, tempStorageOS);
            return tempStorageOS;
        }

        /*
         * Returns an InputStream of the temporary file delta storage identified
         * by id to read the delta.
         */
        public InputStream getTemporaryLocation(Object id) throws IOException {
            return new ByteArrayInputStream(
                    ((ByteArrayOutputStream) myTmpFiles.get(id)).toByteArray());
        }

        /*
         * Gets the length of the delta that was written to the temporary storage 
         * identified by id.
         */
        public long getLength(Object id) throws IOException {
            ByteArrayOutputStream tempStorageOS = (ByteArrayOutputStream) myTmpFiles
                    .get(id);
            if (tempStorageOS != null) {
                return tempStorageOS.size();
            }
            return 0;
        }

        /*
         * Deletes the temporary file delta storage identified by id.
         */
        public void deleteTemporaryLocation(Object id) {
            myTmpFiles.remove(id);
        }
    }
}