package org.tmatesoft.svn.examples.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.diff.ISVNRAData;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
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

public class Export {

    public static void main(String[] args) {
        /*
         * Default values:
         */
        String url = "svn://localhost/rep";
        String name = "userName";
        String password = "userPassword";
        String dirPath = "N:\\materials\\test";
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
        }

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
             * If don't the program exits. SVNNodeKind is that one who says what
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
        System.out.println("Repository latest revision: " + latestRevision);

        File exportDir = new File(dirPath);
        if (exportDir.exists()) {
            System.err.println("the destination directory already exists!");
            System.exit(1);
        }
        exportDir.mkdirs();
        try {
            repository.update(latestRevision, null, true,
                    new MyUpdateReporterBaton(latestRevision),
                    new MyUpdateEditor(dirPath, new MySVNWorkspaceMediator()));
        } catch (SVNException svne) {
            System.err.println("error while exporting '" + url + "': "
                    + svne.getMessage());
            System.exit(1);
        }
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
     *  
     */
    private static class MyUpdateReporterBaton implements ISVNReporterBaton {
        private long revision;

        public MyUpdateReporterBaton(long rev) {
            revision = rev;
        }

        public void report(ISVNReporter reporter) throws SVNException {
            reporter.setPath("", null, revision, true);
            reporter.finishReport();
        }
    }

    /*
     *  
     */
    private static class MyUpdateEditor implements ISVNEditor {
        private long myTargetRevision;

        private String rootPath;

        private ISVNWorkspaceMediator myMediator;

        private String myCurrentFile;

        private List myDiffWindow;

        public MyUpdateEditor(String root, ISVNWorkspaceMediator mediator) {
            rootPath = (root != null) ? root : rootPath;
            myMediator = (mediator != null) ? mediator
                    : new MySVNWorkspaceMediator();
        }

        public void targetRevision(long revision) throws SVNException {
            myTargetRevision = revision;
        }

        public void openRoot(long revision) throws SVNException {
        }

        public void deleteEntry(String path, long revision) throws SVNException {
        }

        public void absentDir(String path) throws SVNException {
        }

        public void absentFile(String path) throws SVNException {
        }

        public void addDir(String path, String copyFromPath,
                long copyFromRevision) throws SVNException {
            path = PathUtil.removeLeadingSlash(path).replace('/',
                    File.separatorChar);
            File newDir = new File(rootPath + File.separatorChar + path);
            newDir.mkdir();
        }

        public void openDir(String path, long revision) throws SVNException {
        }

        public void changeDirProperty(String name, String value)
                throws SVNException {
        }

        public void closeDir() throws SVNException {
        }

        public void addFile(String path, String copyFromPath,
                long copyFromRevision) throws SVNException {
            myCurrentFile = PathUtil.removeLeadingSlash(path).replace('/',
                    File.separatorChar);
            File newFile = new File(rootPath + File.separatorChar
                    + myCurrentFile);
            try {
                newFile.createNewFile();
            } catch (IOException ioe) {
                newFile.delete();
                throw new SVNException(ioe);
            }
        }

        public void openFile(String path, long revision) throws SVNException {
            //            myCurrentFile = PathUtil.removeLeadingSlash(path).replace('/',
            //                    File.separatorChar);
        }

        public void applyTextDelta(String baseChecksum) throws SVNException {
        }

        public OutputStream textDeltaChunk(SVNDiffWindow diffWindow)
                throws SVNException {
            if (myDiffWindow == null) {
                myDiffWindow = new LinkedList();
            }
            myDiffWindow.add(diffWindow);
            try {
                return myMediator.createTemporaryLocation(myCurrentFile,
                        diffWindow);
            } catch (Throwable e) {
                throw new SVNException(e);
            }
        }

        public void textDeltaEnd() throws SVNException {
            File newFile = new File(rootPath + File.separatorChar
                    + myCurrentFile);
            if (!newFile.exists()) {
                try {
                    newFile.createNewFile();
                } catch (IOException ioe) {
                    newFile.delete();
                    throw new SVNException(ioe);
                }
            }

            if ((myDiffWindow != null)) {
                for (int i = 0; i < myDiffWindow.size(); i++) {
                    SVNDiffWindow window = (SVNDiffWindow) myDiffWindow.get(i);
                    InputStream newData = null;
                    try {
                        newData = myMediator.getTemporaryLocation(window);
                        ISVNRAData source = new SVNRAFileData(newFile, false);
                        long offset = newFile.length();
                        window.apply(source, source, newData, offset);
                    } catch (IOException e) {
                        throw new SVNException(e);
                    } catch (SVNException e) {
                        throw e;
                    } catch (Throwable th) {
                        throw new SVNException(th);
                    } finally {
                        if (newData != null) {
                            try {
                                newData.close();
                            } catch (IOException e1) {
                            }
                        }
                    }
                    myMediator.deleteTemporaryLocation(window);
                }
            }
            myDiffWindow = null;
        }

        public void changeFileProperty(String name, String value)
                throws SVNException {
        }

        public void closeFile(String textChecksum) throws SVNException {
            myCurrentFile = null;
        }

        public SVNCommitInfo closeEdit() throws SVNException {
            return new SVNCommitInfo(myTargetRevision, null, null);
        }

        public void abortEdit() throws SVNException {
        }
    }

    /*
     * This class is to be used for temporary storage allocations needed by an
     * ISVNEditor to write file delta that will be supplied to the repository
     * server.
     */
    private static class MySVNWorkspaceMediator implements
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
            File tmpFile = File.createTempFile("javasvn.", ".tmp");
            myTmpFiles.put(id, tmpFile);
            tmpFile.deleteOnExit();
            return new FileOutputStream(tmpFile);
        }

        /*
         * Returns an InputStream of the temporary file delta storage identified
         * by id to read the delta.
         */
        public InputStream getTemporaryLocation(Object id) throws IOException {
            File file = (File) myTmpFiles.get(id);
            if (file != null) {
                return new FileInputStream(file);
            }
            return null;
        }

        /*
         * Gets the length of the temporary file delta storage identified by id.
         */
        public long getLength(Object id) throws IOException {
            File file = (File) myTmpFiles.get(id);
            if (file != null) {
                return file.length();
            }
            return 0;
        }

        /*
         * Deletes the temporary file delta storage identified by id.
         */
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