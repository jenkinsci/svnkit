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
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowApplyBaton;
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
 * 1)an SVNRepository is created to the location (represented by a URL string) that 
 * will be the root of the repository tree to be exported;
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
        String url = "http://svn.tmate.org:8080/svn/jsvn/branches/0.9.0/doc";
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

        SVNRepository repository = null;
        try {
            /*
             * Creates an instance of SVNRepository to work with the repository.
             * All user's requests to the repository are relative to the
             * repository location used to create this SVNRepository.
             * SVNURL is a wrapper for URL strings that refer to repository locations.
             */
            repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
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
         * User's authentication information is provided via an ISVNAuthenticationManager
         * instance. SVNWCUtil creates a default usre's authentication manager given user's
         * name and password.
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
                    new ExportReporterBaton(latestRevision), new ExportEditor(
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

    private static class ExportReporterBaton implements ISVNReporterBaton {
        private long exportRevision;
        public ExportReporterBaton(long revision){
            exportRevision = revision;
        }
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
		 * rev=0 - revision being described is actually irrelevant (therefore it can
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
	            reporter.setPath("", null, exportRevision, true);
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
     * ExportEditor is a simple ISVNEditor implementation that stores exported 
     * directories and files to a client's filesystem (not creating .svn directories
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
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "error: failed to add the directory ''{0}''.", newDir);
                    throw new SVNException(err);
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
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "error: exported file ''{0}'' already exists!", file);
                throw new SVNException(err);
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
                return myMediator.createTemporaryLocation(myCurrentPath, diffWindow);
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, ioe.getLocalizedMessage());
                throw new SVNException(err, ioe);
            }
        }
        
        /*
         * All delta is transmitted. Now it's time to apply it.
         */
        public void textDeltaEnd(String path) throws SVNException {
            File sourceFile = new File(myRootDirectory, myCurrentPath);
            try {
                sourceFile.createNewFile();
            } catch (IOException ioe) {
                sourceFile.delete();
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, ioe.getLocalizedMessage());
                throw new SVNException(err, ioe);
            }
            File targetFile = new File(myRootDirectory, myCurrentPath + ".tmp");

            /*
             * if myDiffWindows is null - the file is empty.
             */
            if (myDiffWindows != null) {
                SVNDiffWindowApplyBaton applyBaton = SVNDiffWindowApplyBaton.create(sourceFile, targetFile, null);
                try {
                    for (Iterator windows = myDiffWindows.iterator(); windows.hasNext();) {
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
                            window.apply(applyBaton, newData);
                        } catch (IOException ioe) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "error while fetching a temporary delta storage.");
                            throw new SVNException(err, ioe);
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
                    applyBaton.close();
                    if (targetFile.exists()) {
                        sourceFile.delete();
                        targetFile.renameTo(sourceFile);
                    }                    
                    myDiffWindows.clear();
                    myDiffWindows = null;
                }
            }
        }
        /*
         * Saves a file property.
         */
        public void changeFileProperty(String path, String name, String value) throws SVNException {
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