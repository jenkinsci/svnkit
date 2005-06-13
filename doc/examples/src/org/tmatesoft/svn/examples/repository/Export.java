package org.tmatesoft.svn.examples.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.diff.ISVNRAData;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.ws.fs.SVNRAFileData;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.util.PathUtil;
/*
 * This example program illustrates how you can export a clean directory tree 
 * from a repository using the SVNRepository.update method. Actually, checkout works in 
 * the similar way but additionally administrative directories are created (one .svn 
 * directory per each exported directory).
 * 
 * Basic aspects of this example:
 * 
 * 0)first of all the library is initialized (setupLibrary() method) - it must be
 * done prior to using the library;
 * 
 * 1)an SVNRepository is created to the location (represented by an
 * SVNRepositoryLocation) that will be the root of the repository tree to be exported;
 * 
 * 2)user's credentials are usually non-necessary for reading operations however the
 * repository may have a restriction to accept requests of only authenticated users;
 * the example shows how to provide user's credentials;
 * 
 * 3)INTRO: you have to implement ISVNReporterBaton and ISVNEditor to affect the 
 * behaviour of the SVNRepository.update method (it depends on whether you want to 
 * update your copy of a repository node that may have been changed since you last 
 * updated it (or checked out) or you have no copy yet but want to check it out). 
 * The aim of ISVNReporterBaton is to report to the repository server about the state 
 * of the user's working files and directories when they are updated as well as report
 * that there are no directories and files yet, they are to be exported - in the case of
 * an export and checkout. Then having got the description information the server sends
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
 * 
 * 
 * If the program succeeds you'll see something like this:
 * 
 * Exported revision: 82   
 */
public class Export {

    public static void main(String[] args) {
        /*
         * Default values:
         */
        String url = "svn://localhost/rep";//"http://72.9.228.230:8080/svn/jsvn/branches/0.9.0/doc";
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

        SVNRepositoryLocation location = null;
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
         * Creates a usre's credentials provider.
         */
        ISVNCredentialsProvider scp = new SVNSimpleCredentialsProvider(name,
                password);

        /*
         * Sets the provider of the user's credentials that will be used to
         * authenticate the user to the server (if needed) during operations
         * handled by the SVNRepository.
         */
        repository.setCredentialsProvider(scp);

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
             * 
             */
            repository.update(latestRevision, null, true,
                    new UpdateReporterBaton(), new UpdateEditor(
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
     *		//for /dirA
     *		reporter.setPath("/dirA", null, 5, false);
     *		
     *		//for /dirA/dirB
     *		reporter.setPath("/dirA/dirB", null, 6, false);
     *		
     *		//for /dirA/dirB/file1.txt
     *		reporter.setPath("/dirA/dirB/file1.txt", null, 6, false);
     *		
     *		//for /dirA/dirC
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
     * - if a user's local working copy is updated against a URL different from that
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
     * a repository. So, UpdateReporterBaton is a simple implementation of 
     * ISVNReporterBaton that describes that intention.
	 */ 
    private static class UpdateReporterBaton implements ISVNReporterBaton {
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
		 * rev=0 - revision being described is also irrelevant (therefore it can be any
		 * non-negative value); the revision of the export scope is passed to
		 * SVNRepository.update.
		 *  
		 * The last flag (startEmpty) is set to true says that there are no local 
		 * copies yet and they are to be exported from the repository.     
         */
        public void report(ISVNReporter reporter) throws SVNException {
            reporter.setPath("", null, 100, true);
            /*
             * Don't forget to finish the report!
             */
            reporter.finishReport();
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
     * ISVNEditor methods :
     * 		
     * 		//sets the target revision the copy is ubeing updated to.
     * 		editor.targetRevision(revision)
     * 		
     * 		//processing starts with the parent directory the update was run for - "/" 
     * 		editor.openRoot(revision);
     * 
     * 		//opens /dirA.
     * 		editor.openDir("/dirA", revision);

     * 		//now modifications can be applied to the opened directory. For example,
     * 		//changing its properties.
     *  	//Also all further calls like editor.openFile(...)/editor.addFile(...)
     * 		//or editor.openDir(...)/editor.addDir(...) are relative to the currently
     * 		//opened directory.
     * 		editor.changeDirProperty(propertyName, propertyValue);
     *       
     * 		//opens file "file1.txt" to modify it
     * 		editor.openFile("/dirA/file1.txt", revision);
     * 		
     * 		//changing file properties. 
     * 		editor.changeFileProperty(propertyName, propertyValue);
     * 		
     * 		//file contents are out of date - the server sends delta
     * 		//(the difference between the local copy and the file in the repository)
     * 		//baseChecksum is provided by the server to make certain of the delta
     * 		//will be applied correctly - the client should compare it
     * 		//with the one evaluated upon copy
     * 		editor.applyTextDelta(baseChecksum);
     * 		
     * 		//
     * 		OutputStream os=editor.textDeltaChunk(diffWindow);
     */
    private static class UpdateEditor implements ISVNEditor {
        private String myRootDirectory;

        private ISVNWorkspaceMediator myMediator;

        private String myCurrentPath;

        private List myDiffWindows;

        private Map myFileProperties;

        public UpdateEditor(String root, ISVNWorkspaceMediator mediator) {
            myRootDirectory = (root != null) ? root : myRootDirectory;
            myMediator = (mediator != null) ? mediator
                    : new WorkspaceMediator();
        }

        public void targetRevision(long revision) throws SVNException {
        }

        public void addDir(String path, String copyFromPath,
                long copyFromRevision) throws SVNException {
            path = PathUtil.removeLeadingSlash(path);
            path = PathUtil.removeTrailingSlash(path);
            File newDir = new File(myRootDirectory, path);
            if (newDir.isFile()) {
                // export is obstructed.
                throw new SVNException(
                        "error: a file is received while a directory was expected.");
            }
            if (!newDir.exists()) {
                if (!newDir.mkdirs()) {
                    throw new SVNException(
                            "error: failed to add the directory '"
                                    + newDir.getAbsolutePath() + "'.");
                }
            }
        }

        public void addFile(String path, String copyFromPath,
                long copyFromRevision) throws SVNException {
            path = PathUtil.removeLeadingSlash(path);
            path = PathUtil.removeTrailingSlash(path);
            File file = new File(myRootDirectory, path);

            if (file.exists()) {
                throw new SVNException("error: exported file '"
                        + file.getAbsolutePath() + "' already exists!");
            }
            myCurrentPath = path;
            myFileProperties = new HashMap();
        }

        public OutputStream textDeltaChunk(SVNDiffWindow diffWindow)
                throws SVNException {
            if (myDiffWindows == null) {
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

        public void textDeltaEnd() throws SVNException {
            File newFile = new File(myRootDirectory, myCurrentPath);
            try {
                newFile.createNewFile();
            } catch (IOException ioe) {
                newFile.delete();
                throw new SVNException(ioe);
            }
            ISVNRAData target = new SVNRAFileData(newFile, false);

            if (myDiffWindows != null) {
                try {
                    for (Iterator windows = myDiffWindows.iterator(); windows
                            .hasNext();) {
                        SVNDiffWindow window = (SVNDiffWindow) windows.next();

                        if (window == null)
                            continue;

                        InputStream newData = null;
                        long offset = newFile.length();
                        try {
                            newData = myMediator.getTemporaryLocation(window);
                            window.apply(target, target, newData, offset);
                        } catch (IOException ioe) {
                            throw new SVNException(
                                    "error while fetching a temporary delta storage.",
                                    ioe);
                        } finally {
                            if (newData != null) {
                                try {
                                    newData.close();
                                } catch (IOException e1) {
                                }
                            }
                            myMediator.deleteTemporaryLocation(window);
                        }
                    }
                } finally {
                    try {
                        target.close();
                    } catch (IOException ioe) {
                    }
                    myDiffWindows.clear();
                    myDiffWindows = null;
                }
            }
        }

        public void changeFileProperty(String name, String value)
                throws SVNException {
            myFileProperties.put(name, value);
        }

        public void closeFile(String textChecksum) throws SVNException {
            File file = new File(myRootDirectory, myCurrentPath);
            if (textChecksum == null) {
                textChecksum = (String) myFileProperties
                        .get(SVNProperty.CHECKSUM);
            }

            try {
                if (textChecksum != null
                        && !textChecksum.equals(SVNFileUtil
                                .computeChecksum(file))) {
                    throw new SVNException("error: the file '"
                            + file.getAbsolutePath() + "' is corrupted!");
                }
            } catch (IOException ioe) {
                throw new SVNException(
                        "error while evaluating the checksum for the file '"
                                + file.getAbsolutePath() + "'.", ioe);
            } finally {
                myCurrentPath = null;
                myFileProperties.clear();
                myFileProperties = null;
            }
        }

        public SVNCommitInfo closeEdit() throws SVNException {
            return null;
        }

        public void openRoot(long revision) throws SVNException {
        }

        public void deleteEntry(String path, long revision) throws SVNException {
        }

        public void absentDir(String path) throws SVNException {
        }

        public void absentFile(String path) throws SVNException {
        }

        public void openFile(String path, long revision) throws SVNException {
        }

        public void applyTextDelta(String baseChecksum) throws SVNException {
        }

        public void closeDir() throws SVNException {
        }

        public void openDir(String path, long revision) throws SVNException {
        }

        public void changeDirProperty(String name, String value)
                throws SVNException {
        }

        public void abortEdit() throws SVNException {
        }
    }

    /*
     * This class is to be used for temporary storage allocations needed by an
     * ISVNEditor to write file delta received from the server. 
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
            ByteArrayInputStream tempStorageIS = new ByteArrayInputStream(
                    ((ByteArrayOutputStream) myTmpFiles.get(id)).toByteArray());
            return tempStorageIS;
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
            ByteArrayOutputStream tempStorageOS = (ByteArrayOutputStream) myTmpFiles
                    .remove(id);
            if (tempStorageOS != null) {
                tempStorageOS = null;
            }
        }

        public void deleteAdminFiles(String path) {
        }
    }
}