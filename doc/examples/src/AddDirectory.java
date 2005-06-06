
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
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
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.diff.SVNDiffWindowBuilder;

/*
 * This is an example of how to commit several types of changes to a repository:
 * a new directory with a file, modification to a file, deletion of the directory
 * and its entries.
 * 
 * Main steps of this commit example in brief: 0)initialize the library;
 * 
 * 1)create an SVNRepository to that location that will be the root for
 * committing (that is all paths that are being committed will be below that
 * root);
 * 
 * 2)provide your credentials (committing generally requires authentication);
 * 
 * 3)"ask" your SVNRepository for a commit editor (use
 * SVNRepository.getCommitEditor);
 * 
 * 4)perform a sequence of descriptions of changes to the repository server
 * (here you "say" to the server that you have added such-and-such a new
 * directory at such-and-such a path as well as a new file with). These
 * descriptions are calls to ISVNEditor's methods.
 * 
 * 5)At last you close the editor with the ISVNEditor.closeEdit method that
 * fixes your modificaions in the repository and provides new commit
 * information.
 * 
 * As an example here's one of the program layouts:
 * 
 * Repository latest revision (before committing): 669
 * 
 * The last author:sa 
 * Date:Fri Jun 03 22:36:01 NOVST 2005 
 * Committed to revision 670
 */
public class AddDirectory {


    public static void main(String[] args) {
        /*
         * Default values:
         */
        String url = "svn://localhost/materials/rep/testDir";//"http://72.9.228.230:8080/svn/jsvn/branches/jorunal";
        String name = "anonymous";
        String password = "anonymous";
        String dirPath = "test";
        String fileName = "myTemp.txt";
        String commitMessage = "adding a new directory with a file";
        byte[] binaryData = "This is a new file".getBytes();
        byte[] changedBinaryData = "This is the same file but modified a little ".getBytes();
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
            fileName = (args.length >= 5) ? args[4] : fileName;
            /*
             * Obtains a commit message
             */
            commitMessage = (args.length >= 6) ? args[5] : commitMessage;
        }
        
        SVNRepositoryLocation location = null;
        SVNRepository repository = null;
        try {
            /*
             * Parses the URL string and creates an SVNRepositoryLocation which
             * represents the repository location (you can think of this
             * location as of a current repository session directory; it can be
             * any versioned directory inside the repository).
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
             * Perhaps a malformed URL is the cause of this exception
             */
            System.err
                    .println("error while creating SVNRepository for location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }

        /*
         * Creates a usre's credentials provider
         */
        ISVNCredentialsProvider scp = new SVNSimpleCredentialsProvider(name,
                password);

        /*
         * Sets the provider of the user's credentials that will be used to
         * authenticate the user to the server (if needed) during operations
         * handled by SVNRepository
         */
        repository.setCredentialsProvider(scp);

        long latestRevision = -1;
        try {
            /*
             * Gets the latest revision number of the repository
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
         * Gets an editor for committing the changes to the repository.
         * 
         * commitMessage will be applied as a log message of the commit.
         * 
         * ISVNWorkspaceMediator will be used by the editor to store any
         * file delta (that is to be sent to the server) in an intermediate
         * file.
         */
        try {
            editor = repository.getCommitEditor(commitMessage,
                    new MySVNWorkspaceMediator());
        } catch (SVNException svne) {
            System.err
                    .println("error while getting a commit editor for location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }

        SVNCommitInfo commitInfo = null;
        /*
         * Adding a new directory containing a file to the repository. 
         */
        try {
            commitInfo = addDir(editor, dirPath, dirPath+"/"+fileName, binaryData);
        } catch (SVNException svne) {
            try {
                /*
                 * An exception was thrown during the work of the editor. The
                 * editor must be aborted to behave in a right way in order to the
                 * breakdown won't cause any unstability.
                 */
                System.err.println("aborting the editor due to errors:"
                        + svne.getMessage());
                svne.printStackTrace();
                editor.abortEdit();
            } catch (SVNException inner) {
                inner.printStackTrace();
                System.err.println("failed to abort the editor:"
                        + inner.getMessage());
            }
            System.exit(1);
        }
        printCommitInfo(commitInfo);

        try {
            editor = repository.getCommitEditor(commitMessage,
                    new MySVNWorkspaceMediator());
        } catch (SVNException svne) {
            System.err
                    .println("error while getting a commit editor for location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }
        /*
         * Changing the file contents.
         */
        try {
            commitInfo = modifyFile(editor, dirPath, dirPath+"/"+fileName, changedBinaryData);
        } catch (SVNException svne) {
            try {
                editor.abortEdit();
                System.err.println("aborting the editor due to errors:"
                        + svne.getMessage());
            } catch (SVNException inner) {
                System.err.println("failed to abort the editor:"
                        + svne.getMessage());
            }
            System.exit(1);
        }
        printCommitInfo(commitInfo);

        try {
            editor = repository.getCommitEditor(commitMessage,
                    new MySVNWorkspaceMediator());
        } catch (SVNException svne) {
            System.err
                    .println("error while getting a commit editor for location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }

        /*
         * Deleting the directory
         */
        try {
            commitInfo = deleteDir(editor, dirPath);
        } catch (SVNException svne) {
            try {
                editor.abortEdit();
                System.err.println("aborting the editor due to errors:"
                        + svne.getMessage());
            } catch (SVNException inner) {
                System.err.println("failed to abort the editor:"
                        + svne.getMessage());
            }
            System.exit(1);
        }
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
    private static SVNCommitInfo addDir(ISVNEditor editor, String dirPath, String filePath, byte[] data) throws SVNException{
        /*
         * Opens the current root directory. It means all modifications will
         * be applied to this directory until a next entry is opened.
         */
        editor.openRoot(-1);
        /*
         * Adds a new directory (not a copy of an existing one) to the
         * currently opened directory (in this case - to the root directory
         * for which the SVNRepository was created). Since this moment all
         * changes will be applied to this new directory.
         * 
         * dirPath is relative to the root directory.
         */
        editor.addDir(dirPath, null, -1);
        /*
         * Adds a new file (not a copy) to the just added directory. The
         * file path is also defined as relative to the root directory.
         */
        editor.addFile(filePath, null, -1);
        /*
         * The next steps are directed to obtaining and applying the file
         * delta (that is the full contents of the file in this case).
         */
        long fileLength = data.length;
        /*
         * Creating a new diff window that will contain instructions of
         * applying the delta (contents in this case) to the file in the
         * repository.
         */
        SVNDiffWindow diffWindow = SVNDiffWindowBuilder
                .createReplacementDiffWindow(fileLength);
        /*
         * Gets an OutputStream where the delta will be written to.
         */
        OutputStream os = editor.textDeltaChunk(diffWindow);
        if (fileLength == 0) {
            /*
             * If the file is empty - close the OutputStream since there's no text delta
             * for the file. And mark the end of the delta calling textDeltaEnd(). 
             * 
             */
            try {
                os.close();
            } catch (IOException e1) {
            } finally {
                editor.textDeltaEnd();
            }
        } else {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            byte[] myBinaryBuffer = new byte[8192 * 4];
            /*
             * If the file is not empty this code writes the file contents
             * to the OutputStream intended for the delta.
             */
            while (true) {
                try {
                    int read = bais.read(myBinaryBuffer,0,myBinaryBuffer.length);
                    if (read < 0) {
                        break;
                    }
                    os.write(myBinaryBuffer, 0, read);
                } catch (IOException ioe) {
                    System.err.println("An i/o error while writing the delta bytes:" + ioe.getMessage());
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException ioeInternal) {
                        }
                    }
                    break;
                }
            }
            /*
             * Finally closes the delta when all the contents is already
             * written. From this point early defined diff window knows how
             * to apply the delta for the file (that will be created in the
             * repository).
             */
            editor.textDeltaEnd();
        }
        /*
         * Here the delta is applied to the file.
         */
        editor.applyTextDelta(null);
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
         * This is the final point in all editor handling. Only now all that
         * new information previously described with the editor's methods is
         * sent to the server for committing. As a result the server sends
         * the new commit info that is displayed in the console.
         */
        return editor.closeEdit();
    }
    
    private static SVNCommitInfo modifyFile(ISVNEditor editor, String dirPath, String filePath, byte[] newData) throws SVNException{
        /*
         * Opens the root directory (for which the SVNRepository was created).
         * It means all modifications will be applied to this directory until a next
         * entry is opened.
         * 
         * -1 means the last (HEAD) revision.
         */
        editor.openRoot(-1);
        /*
         * Opens a next subdirectory (in this example program it's the directory added
         * in the last commit). Since this moment all changes will be applied to this
         * directory.
         * 
         * dirPath is relative to the root directory.
         * -1 means the last (HEAD) revision.
         */
        editor.openDir(dirPath, -1);
        /*
         * Adds a new file (not a copy) to the just added directory. The
         * 
         * file path is also defined as a relative path to the root directory.
         * -1 means the last (HEAD) revision.
         */
        editor.openFile(filePath, -1);
        /*
         * The next steps are directed to obtaining and applying the file
         * delta (that is the full contents of the file in this case).
         */
        long fileLength = newData.length;
        /*
         * Creating a new diff window that will contain instructions of
         * applying the delta (contents in this case) to the file in the
         * repository.
         */
        SVNDiffWindow diffWindow = SVNDiffWindowBuilder
                .createReplacementDiffWindow(fileLength);
        /*
         * Gets an OutputStream where the delta will be written to.
         */
        OutputStream os = editor.textDeltaChunk(diffWindow);
        if (fileLength == 0) {
            /*
             * If the file is empty - close the OutputStream since there's no text delta
             * for the file. And mark the end of the delta calling textDeltaEnd(). 
             * 
             */
            try {
                os.close();
            } catch (IOException e1) {
            } finally {
                editor.textDeltaEnd();
            }
        } else {
            ByteArrayInputStream bais = new ByteArrayInputStream(newData);
            byte[] myBinaryBuffer = new byte[8192 * 4];
            /*
             * If the file is not empty this code writes the file contents
             * to the OutputStream intended for the delta.
             */
            while (true) {
                try {
                    int read = bais.read(myBinaryBuffer,0,myBinaryBuffer.length);
                    if (read < 0) {
                        break;
                    }
                    os.write(myBinaryBuffer, 0, read);
                } catch (IOException ioe) {
                    System.err.println("An i/o error while writing the delta bytes:" + ioe.getMessage());
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException ioeInternal) {
                        }
                    }
                    break;
                }
            }
            /*
             * Finally closes the delta when all the contents is already
             * written. From this point early defined diff window knows how
             * to apply the delta for the file (that will be created in the
             * repository).
             */
            editor.textDeltaEnd();
        }
        /*
         * Here the delta is applied to the file.
         */
        editor.applyTextDelta(null);
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
         * This is the final point in all editor handling. Only now all that
         * new information previously described with the editor's methods is
         * sent to the server for committing. As a result the server sends
         * the new commit info that is displayed in the console.
         */
        return editor.closeEdit();    
    }
    
    private static SVNCommitInfo deleteDir(ISVNEditor editor, String dirPath) throws SVNException{
        /*
         * Opens the current root directory. It means all modifications will
         * be applied to this directory until a next entry is opened.
         */
        editor.openRoot(-1);
        /*
         * Deletes the subdirectory.
         *  
         * dirPath is relative to the root directory.
         */
        editor.deleteEntry(dirPath,-1);
        /*
         * Closes the root directory.
         */
        editor.closeDir();
        /*
         * This is the final point in all editor handling. Only now all that
         * new information previously described with the editor's methods is
         * sent to the server for committing. As a result the server sends
         * the new commit info that is displayed in the console.
         */
        return editor.closeEdit();
    }
    
    private static void printCommitInfo(SVNCommitInfo commitInfo){
        System.out.println("");
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
    }
    
    /*
     * 
     */
    private  static class MySVNWorkspaceMediator implements ISVNWorkspaceMediator{
        private Map myTmpFiles = new HashMap();

        public String getWorkspaceProperty(String path,
                String name) throws SVNException {
            return null;
        }

        public void setWorkspaceProperty(String path,
                String name, String value) throws SVNException {
        }

        public OutputStream createTemporaryLocation(
                String path, Object id) throws IOException {
            File tmpFile = File.createTempFile("javasvn.",
                    ".tmp");
            myTmpFiles.put(id, tmpFile);
            tmpFile.deleteOnExit();
            return new FileOutputStream(tmpFile);
        }

        public InputStream getTemporaryLocation(Object id)
                throws IOException {
            File file = (File) myTmpFiles.get(id);
            if (file != null) {
                return new FileInputStream(file);
            }
            return null;
        }

        public long getLength(Object id) throws IOException {
            File file = (File) myTmpFiles.get(id);
            if (file != null) {
                return file.length();
            }
            return 0;
        }

        public void deleteTemporaryLocation(Object id) {
            File file = (File) myTmpFiles.remove(id);
            if (file != null) {
                file.delete();
            }
        }

        public void deleteAdminFiles(String path) {
        }
    }
}