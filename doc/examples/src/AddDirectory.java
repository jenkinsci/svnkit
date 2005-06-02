
import java.io.File;
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
 * 
 * 
 */
public class AddDirectory {
    private static SVNRepositoryLocation location;

    private static SVNRepository repository;

    private static ISVNEditor editor;

    public static void main(String[] args) {
        /*
         * Default values:
         */
        String url = "http://72.9.228.230:8080/svn/jsvn/branches/jorunal";
        String name = "anonymous";
        String password = "anonymous";
        String dirPath = "tempDir";
        String fileName = "myTemp.txt";
        String commitMessage = "adding a new directory with a file";

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
        try {
            editor = repository.getCommitEditor(commitMessage,
                    new ISVNWorkspaceMediator() {
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
                    });
        } catch (SVNException svne) {
            System.err
                    .println("error while getting a commit editor for location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }

        SVNCommitInfo commitInfo = null;

        try {
            editor.openRoot(-1);
            editor.addDir(dirPath, null, -1);

            editor.addFile(dirPath + "/" + fileName, null, -1);
            File file = new File(dirPath + "/" + fileName);
            long fileLength = file.length();
            SVNDiffWindow diffWindow = SVNDiffWindowBuilder
                    .createReplacementDiffWindow(fileLength);
            OutputStream os = editor.textDeltaChunk(diffWindow);
            if (fileLength == 0) {
                try {
                    os.close();
                } catch (IOException e1) {
                } finally {
                    editor.textDeltaEnd();
                }
            } else {
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(file);
                } catch (FileNotFoundException fnfe) {
                    System.err.println("The file wasn't found:"
                            + fnfe.getMessage());
                    if (fis != null) {
                        try {
                            fis.close();
                        } catch (IOException ioe) {

                        }
                    }
                }
                byte[] myBinaryBuffer = new byte[8192 * 4];
                while (true) {
                    try {
                        int read = fis.read(myBinaryBuffer);
                        if (read < 0) {
                            break;
                        }
                        os.write(myBinaryBuffer, 0, read);
                    } catch (IOException ioe) {
                        System.err.println("An i/o error:" + ioe.getMessage());
                        if (fis != null) {
                            try {
                                fis.close();
                            } catch (IOException ioeInternal) {
                            }
                        }
                        if (os != null) {
                            try {
                                os.close();
                            } catch (IOException ioeInternal) {
                            }
                        }
                        break;
                    }
                }
                editor.textDeltaEnd();
            }
            editor.applyTextDelta(null);
            editor.closeFile(null);
            editor.closeDir();
            editor.closeDir();
            editor.closeDir();
            commitInfo = editor.closeEdit();
        } catch (SVNException svne) {
            try {
                editor.abortEdit();
            } catch (SVNException inner) {
            }
            System.err.println("error while committing a new directory:"
                    + svne.getMessage());
            System.exit(1);
        }
        System.out.println("The last author:" + commitInfo.getAuthor());
        System.out.println("Date:" + commitInfo.getDate().toString());
        System.out.println("Committed to revision "
                + commitInfo.getNewRevision());
        System.exit(0);
    }

    /*
     * Initializes the library to work with a repository either via svn:// 
     * (and svn+ssh://) or via http:// (and https://)
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
}