package org.tmatesoft.svn.examples.repository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.util.PathUtil;
/*
 * This is an example of how to commit several types of changes to a repository:
 * a new directory with a file, modification to a file, copying a directory into
 * a branch, deletion of the directory and its entries.
 * 
 * Main aspects of performing a commit with the help of ISVNEditor:
 * 0)initialize the library (this is done in setupLibrary() method);
 * 
 * 1)create an SVNRepository to the location (represented by an
 * SVNRepositoryLocation) that will be the root for committing - that is all
 * paths that are being committed will be below that root;
 * 
 * 2)provide user's credentials (committing generally requires authentication);
 * 
 * 3)"ask" your SVNRepository for a commit editor (use an
 * SVNRepository.getCommitEditor method);
 * 
 * 4)perform a sequence of descriptions of changes to the repository server (for
 * example, here you "say" to the server that you have added such-and-such a new
 * directory at such-and-such a path as well as a new file). These descriptions
 * are calls to ISVNEditor's methods. First of all ISVNEditor.openRoot() is
 * called as a start point for further processing.
 * 
 * 5)At last you close the editor with the ISVNEditor.closeEdit method that
 * fixes your modificaions in the repository and provides new commit
 * information.
 * 
 * For each commit a new ISVNEditor is required - that is after having been
 * closed the editor can no longer be used!
 * 
 * This example can be run for a locally installed Subversion repository via the
 * svn:// protocol. This is how you can do it:
 * 
 * 1)after you install the Subversion you should create a new repository in a
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
 * 
 * 
 * As an example here's one of the program layouts:
 * 
 * Repository latest revision (before committing): 48
 * 
 * The directory was added. 
 * The last author:userName 
 * Date:Thu Jun 09 13:22:40 NOVST 2005 
 * Committed to revision 49
 * 
 * The file was changed. 
 * The last author:userName 
 * Date:Thu Jun 09 13:22:40 NOVST 2005 
 * Committed to revision 50
 * 
 * The directory was copied to a branch. 
 * The last author:userName 
 * Date:Thu Jun 09 13:22:40 NOVST 2005 
 * Committed to revision 51
 * 
 * The directory was deleted. 
 * The last author:userName 
 * Date:Thu Jun 09 13:22:40 NOVST 2005 
 * Committed to revision 52
 */
public class Commit {

    /*
     * args parameter is used to obtain a repository location URL, user's
     * account name & password to authenticate him to the server, new directory
     * path (should be relative to the /rep), new file name, branch directory path
     * and a commit message.
     */
    public static void main(String[] args) {
        /*
         * Default values:
         */
        String url = "svn://localhost/rep";
        String name = "userName";
        String password = "userPassword";
        String dirPath = "/test";
        String filePath = "/test/myTemp.txt";
        String copyPath = "/testCopy";
        String commitMessage = "adding a new directory with a file!";
        /*
         * This is the text of the file to be created in the repository.
         */
        byte[] binaryData = "This is a new file".getBytes();
        /*
         * This is a new text to overwrite the contents of the created file.
         */
        byte[] changedBinaryData = "This is the same file but modified a little."
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
             * Obtains an account name (will be used to authenticate the user to
             * the server)
             */
            name = (args.length >= 2) ? args[1] : name;
            /*
             * Obtains a password
             */
            password = (args.length >= 3) ? args[2] : password;
            /*
             * Obtains a new dir path
             */
            dirPath = (args.length >= 4) ? args[3] : dirPath;
            /*
             * Obtains a file name
             */
            filePath = (args.length >= 5) ? args[4] : filePath;
            /*
             * Obtains a path where the existing directory will be copied to 
             * (branched).
             */
            copyPath = (args.length >= 6) ? args[5] : copyPath;
            /*
             * Obtains a commit message.
             */
            commitMessage = (args.length >= 7) ? args[6] : commitMessage;
        }

        SVNRepositoryLocation location = null;
        SVNRepository repository = null;
        try {
            /*
             * Parses the URL string and creates an SVNRepositoryLocation which
             * represents the repository location - it can be
             * any versioned entry inside the repository.
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
        System.out.println("Repository latest revision (before committing): "
                + latestRevision);
        System.out.println("");
        
        ISVNEditor editor = null;

        /*
         * Gets an editor for committing the changes to the repository. NOTE:
         * you must not invoke methods of the SVNRepository until you close the
         * editor with the ISVNEditor.closeEdit method.
         * 
         * commitMessage will be applied as a log message of the commit.
         * 
         * ISVNWorkspaceMediator will be used by the editor for inermediate file
         * delta storing.
         */
        try {
            editor = repository.getCommitEditor(commitMessage,
                    new WorkspaceMediator());
        } catch (SVNException svne) {
            System.err
                    .println("error while getting a commit editor for the location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }

        SVNCommitInfo commitInfo = null;
        /*
         * Adding a new directory containing a file to the repository.
         */
        try {
            commitInfo = addDir(editor, dirPath, filePath, binaryData);
        } catch (SVNException svne) {
            try {
                System.err
                        .println("failed to add the directory with the file due to errors: "
                                + svne.getMessage());
                /*
                 * An exception was thrown during the work of the editor. The
                 * editor must be aborted to behave in a right way in order to
                 * the breakdown won't cause any unstability.
                 */
                editor.abortEdit();
            } catch (SVNException inner) {
            }
            System.exit(1);
        }
        System.out.println("The directory was added.");
        /*
         * Displaying the commit info.
         */
        printCommitInfo(commitInfo);

        /*
         * Obtains a new editor for the next commit.
         */
        try {
            editor = repository.getCommitEditor(commitMessage,
                    new WorkspaceMediator());
        } catch (SVNException svne) {
            System.err
                    .println("error while getting a commit editor for the location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }

        /*
         * Changing the file contents.
         */
        try {
            commitInfo = modifyFile(editor, dirPath, filePath, changedBinaryData);
        } catch (SVNException svne) {
            try {
                System.err.println("failed to modify the file due to errors: "
                        + svne.getMessage());
                /*
                 * An exception was thrown during the work of the editor. The
                 * editor must be aborted to behave in a right way in order to
                 * the breakdown won't cause any unstability.
                 */
                editor.abortEdit();
            } catch (SVNException inner) {
            }
            System.exit(1);
        }
        System.out.println("The file was changed.");
        /*
         * Displaying the commit info.
         */
        printCommitInfo(commitInfo);

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

        /*
         * Obtains a new editor for the next commit.
         */
        try {
            editor = repository.getCommitEditor(commitMessage,
                    new WorkspaceMediator());
        } catch (SVNException svne) {
            System.err
                    .println("error while getting a commit editor for the location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }

        /*
         * Making a branch in the repository  - copying the existing directory
         * and all its entries to another location under the common root directory.
         * dirPath  - the source path in the latestRevision.
         * copyPath - the destination path.
         */
        try {
            commitInfo = copyDir(editor, copyPath, dirPath, latestRevision);
        } catch (SVNException svne) {
            try {
                System.err
                        .println("failed to copy the directory due to errors: "
                                + svne.getMessage());
                /*
                 * An exception was thrown during the work of the editor. The
                 * editor must be aborted to behave in a right way in order to
                 * the breakdown won't cause any unstability.
                 */
                editor.abortEdit();
            } catch (SVNException inner) {
            }
            System.exit(1);
        }
        System.out.println("The directory was copied to a branch.");
        /*
         * Displaying the commit info.
         */
        printCommitInfo(commitInfo);

        /*
         * Obtains a new editor for the next commit.
         */
        try {
            editor = repository.getCommitEditor(commitMessage,
                    new WorkspaceMediator());
        } catch (SVNException svne) {
            System.err
                    .println("error while getting a commit editor for the location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }

        /*
         * Deleting the directory.
         */
        try {
            commitInfo = deleteDir(editor, dirPath);
        } catch (SVNException svne) {
            try {
                System.err
                        .println("failed to delete the directory due to errors: "
                                + svne.getMessage());
                /*
                 * An exception was thrown during the work of the editor. The
                 * editor must be aborted to behave in a right way in order to
                 * the breakdown won't cause any unstability.
                 */
                editor.abortEdit();
            } catch (SVNException inner) {
            }
            System.exit(1);
        }
        System.out.println("The directory was deleted.");
        /*
         * Displaying the commit info.
         */
        printCommitInfo(commitInfo);

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
    }

    /*
     * This method performs commiting an addition of a directory containing a
     * file.
     */
    private static SVNCommitInfo addDir(ISVNEditor editor, String dirPath,
            String filePath, byte[] data) throws SVNException {
        /*
         * Always called first. Opens the current root directory. It means all
         * modifications will be applied to this directory until a next entry
         * (located inside the root) is opened.
         */
        editor.openRoot(-1);
        /*
         * Adds a new directory (not a copy of an existing one in the
         * repository) to the currently opened directory (in this case - to the
         * root directory for which the SVNRepository was created). Since this
         * moment all changes will be applied to this new directory.
         * 
         * dirPath is relative to the root directory.
         */
        editor.addDir(dirPath, null, -1);
        /*
         * Adds a new file (not a copy) to the just added directory. The file
         * path is also defined as relative to the root directory.
         *  
         */
        editor.addFile(filePath, null, -1);
        /*
         * The next steps are directed to applying and writing the file delta
         * (that is the full contents of the file in this case).
         */
        editor.applyTextDelta(null);
        long deltaLength = data.length;
        /*
         * Creating a new diff window (provided the size of the delta -
         * deltaLength) that will contain instructions of applying the delta to
         * the file in the repository.
         */
        SVNDiffWindow diffWindow = SVNDiffWindowBuilder
                .createReplacementDiffWindow(deltaLength);
        /*
         * Gets an OutputStream where the delta will be written to.
         */
        OutputStream os = editor.textDeltaChunk(diffWindow);

        try {
            /*
             * If the file is not empty this code writes the file delta to the
             * OutputStream.
             */
            for (int i = 0; i < deltaLength; i++) {
                os.write(data[i]);
            }
            /*
             * Don't forget to close the stream after you have written the
             * delta!
             */
            os.close();

        } catch (IOException ioe) {
            System.err.println("An i/o error while writing the delta bytes: "
                    + ioe.getMessage());
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ioeInternal) {
                }
            }
        }

        /*
         * Finally closes the delta when all the bytes are already written. From
         * this point the previously defined diff window knows how to apply the
         * delta to the file (that will be created in the repository).
         */
        editor.textDeltaEnd();
        /*
         * Closes the new added file.
         */
        editor.closeFile(null);
        /*
         * Closes the new added directory.
         */
        editor.closeDir();
        /*
         * Closes the root directory.
         */
        editor.closeDir();
        /*
         * This is the final point in all editor handling. Only now all that new
         * information previously described with the editor's methods is sent to
         * the server for committing. As a result the server sends the new
         * commit information.
         */
        return editor.closeEdit();
    }

    /*
     * This method performs committing file modifications.
     */
    private static SVNCommitInfo modifyFile(ISVNEditor editor, String dirPath,
            String filePath, byte[] newData) throws SVNException {
        /*
         * Always called first. Opens the current root directory. It means all
         * modifications will be applied to this directory until the next entry
         * (located inside the root) is opened.
         */
        editor.openRoot(-1);
        /*
         * Opens a next subdirectory (in this example program it's the directory
         * added in the last commit). Since this moment all changes will be
         * applied to this directory.
         * 
         * dirPath is relative to the root directory.
         */
        editor.openDir(dirPath, -1);
        /*
         * Opens the file added in the previous commit.
         * 
         * filePath is also defined as a relative path to the root directory.
         */
        editor.openFile(filePath, -1);
        /*
         * The next steps are directed to applying and writing the file delta.
         */
        editor.applyTextDelta(null);
        long deltaLength = newData.length;
        /*
         * Creating a new diff window (provided the size of the delta -
         * deltaLength) that will contain instructions of applying the delta to
         * the file in the repository.
         */
        SVNDiffWindow diffWindow = SVNDiffWindowBuilder
                .createReplacementDiffWindow(deltaLength);
        /*
         * Gets an OutputStream where the delta will be written to.
         */
        OutputStream os = editor.textDeltaChunk(diffWindow);
        try {
            /*
             * If there's non-empty file delta this code writes the delta to the
             * OutputStream.
             */
            for (int i = 0; i < deltaLength; i++) {
                os.write(newData[i]);
            }
            /*
             * Don't forget to close the stream after you have written the
             * delta!
             */
            os.close();
        } catch (IOException ioe) {
            System.err.println("An i/o error while writing the delta bytes: "
                    + ioe.getMessage());
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ioeInternal) {
                }
            }
        }

        /*
         * Finally closes the delta when all the bytes are already written. From
         * this point the previously defined diff window knows how to apply the
         * delta to the file.
         */
        editor.textDeltaEnd();

        /*
         * Closes the file.
         */
        editor.closeFile(null);

        /*
         * Closes the directory.
         */
        editor.closeDir();

        /*
         * Closes the root directory.
         */
        editor.closeDir();

        /*
         * This is the final point in all editor handling. Only now all that new
         * information previously described with the editor's methods is sent to
         * the server for committing. As a result the server sends the new
         * commit information.
         */
        return editor.closeEdit();
    }

    /*
     * This method performs committing a deletion of a directory.
     */
    private static SVNCommitInfo deleteDir(ISVNEditor editor, String dirPath)
            throws SVNException {
        /*
         * Always called first. Opens the current root directory. It means all
         * modifications will be applied to this directory until the next entry
         * (located inside the root) is opened.
         */
        editor.openRoot(-1);
        /*
         * Deletes the subdirectory with all its contents.
         * 
         * dirPath is relative to the root directory.
         */
        editor.deleteEntry(dirPath, -1);
        /*
         * Closes the root directory.
         */
        editor.closeDir();
        /*
         * This is the final point in all editor handling. Only now all that new
         * information previously described with the editor's methods is sent to
         * the server for committing. As a result the server sends the new
         * commit information.
         */
        return editor.closeEdit();
    }

    /*
     * This method performs how a directory in the repository can be copied to
     * branch.
     */
    private static SVNCommitInfo copyDir(ISVNEditor editor, String destDirPath,
            String srcDirPath, long revision) throws SVNException {
        /*
         * Always called first. Opens the current root directory. It means all
         * modifications will be applied to this directory until the next entry
         * (located inside the root) is opened.
         */
        editor.openRoot(-1);
        /*
         * Adds a new directory that is a copy of the existing one.
         * 
         * srcDirPath  - the source directory path (relative to the root directory).
         * 
         * destDirPath - the destination directory path where the source will be
         * copied to (relative to the root directory).
         * 
         * revision    - the number of the source directory revision. 
         */
        editor.addDir(destDirPath, srcDirPath, revision);
        /*
         * Closes the just added copy of the directory.
         */
        editor.closeDir();
        /*
         * Closes the root directory.
         */
        editor.closeDir();
        /*
         * This is the final point in all editor handling. Only now all that new
         * information previously described with the editor's methods is sent to
         * the server for committing. As a result the server sends the new
         * commit information.
         */
        return editor.closeEdit();
    }

    /*
     * This method is used to print out new information about the last
     * successful commit.
     */
    private static void printCommitInfo(SVNCommitInfo commitInfo) {
        /*
         * The author of the last commit.
         */
        System.out.println("The last author:" + commitInfo.getAuthor());
        /*
         * The time moment when the changes were committed.
         */
        System.out.println("Date:" + commitInfo.getDate().toString());
        /*
         * And the new committed revision.
         */
        System.out.println("Committed to revision "
                + commitInfo.getNewRevision());
        System.out.println("");
    }

    /*
     * This class is to be used for temporary storage allocations needed by an
     * ISVNEditor to write file delta that will be supplied to the repository
     * server.
     */
    private static class WorkspaceMediator implements
            ISVNWorkspaceMediator {
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
            ByteArrayInputStream tempStorageIS = new ByteArrayInputStream(((ByteArrayOutputStream)myTmpFiles.get(id)).toByteArray());
            return tempStorageIS;
        }

        /*
         * Gets the length of the delta that was written to the temporary storage 
         * identified by id.
         */
        public long getLength(Object id) throws IOException {
            ByteArrayOutputStream tempStorageOS = (ByteArrayOutputStream)myTmpFiles.get(id);
            if (tempStorageOS != null) {
                return tempStorageOS.size();
            }
            return 0;
        }

        /*
         * Deletes the temporary file delta storage identified by id.
         */
        public void deleteTemporaryLocation(Object id) {
            ByteArrayOutputStream tempStorageOS = (ByteArrayOutputStream)myTmpFiles.remove(id);
            if (tempStorageOS != null) {
                tempStorageOS = null;
            }
        }

        public void deleteAdminFiles(String path) {
        }
    }
}