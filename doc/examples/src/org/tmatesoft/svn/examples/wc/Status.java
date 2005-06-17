package org.tmatesoft.svn.examples.wc;

import java.io.File;

import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNWorkspaceManager;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;

public class Status {

    public static void main(String[] args) {
        /*
         * Default values:
         */
        String url = "svn://localhost/rep";//"http://72.9.228.230:8080/svn/jsvn/branches/0.9.0/doc";
        String myWorkspacePath = "/MyWorkspace";
        long revision = ISVNWorkspace.HEAD;//HEAD (the latest) revision
        String name = "anonymous";
        String password = "anonymous";
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
             * Obtains a path for a workspace
             */
            myWorkspacePath = (args.length >= 2) ? args[1] : myWorkspacePath;
            /*
             * Obtains a revision
             */
            revision = (args.length >= 3) ? Long.parseLong(args[2]) : revision;
            /*
             * Obtains an account name (will be used to authenticate the user to
             * the server)
             */
            name = (args.length >= 4) ? args[3] : name;
            /*
             * Obtains a password
             */
            password = (args.length >= 5) ? args[4] : password;
        }
        File wsDir = new File(myWorkspacePath);
/*        if (wsDir.exists()) {
            System.err.println("the destination directory '"
                    + wsDir.getAbsolutePath() + "' already exists!");
            System.exit(1);
        }*/
//        wsDir.mkdirs();
        SVNRepositoryLocation location = null;
        ISVNWorkspace myWorkspace = null;
        try {
            /*
             * Parses the URL string and creates an SVNRepositoryLocation which
             * represents the repository location - it can be any versioned
             * entry inside the repository.
             */
            location = SVNRepositoryLocation.parseURL(url);

            myWorkspace = SVNWorkspaceManager.createWorkspace("file", wsDir
                    .getAbsolutePath());
            //checkout
//            long wcRevision = myWorkspace.checkout(location,
//                    revision, false);
//            System.out.println("Checked out revision " + wcRevision + ".");
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
        
        SVNStatusClient myStatusClient = new SVNStatusClient(scp);

        boolean isRecursive=true;
        boolean isRemote=false;
        boolean isReportAll=true;
        boolean isIncludeIgnored=true;
        boolean isCollectParentExternals=false;
        try {
            myStatusClient.doStatus(wsDir, isRecursive, isRemote, isReportAll, isIncludeIgnored, isCollectParentExternals, new StatusHandler(isRemote));
        } catch (SVNException svne) {
            System.err
                    .println("error while performing status for '"
                            + wsDir.getAbsolutePath() + "': " + svne.getMessage());
            System.exit(1);
        }

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

    private static class StatusHandler implements ISVNStatusHandler {
        private boolean myIsRemote;

        public StatusHandler(boolean isRemote) {
            myIsRemote = isRemote;
        }

        public void handleStatus(SVNStatus status) {

            SVNStatusType contentsStatus = status.getContentsStatus();
            String pathChangeType = " ";
            boolean isAddedWithHistory = status.isCopied();
            if (contentsStatus == SVNStatusType.STATUS_MODIFIED) {
                pathChangeType = "M";
            } else if (contentsStatus == SVNStatusType.STATUS_CONFLICTED) {
                pathChangeType = "C";
            } else if (contentsStatus == SVNStatusType.STATUS_MERGED) {
                pathChangeType = "G";
            } else if (contentsStatus == SVNStatusType.STATUS_DELETED) {
                pathChangeType = "D";
            } else if (contentsStatus == SVNStatusType.STATUS_ADDED) {
                pathChangeType = "A";
            } else if (contentsStatus == SVNStatusType.STATUS_UNVERSIONED) {
                pathChangeType = "?";
            } else if (contentsStatus == SVNStatusType.STATUS_EXTERNAL) {
                pathChangeType = "X";
            } else if (contentsStatus == SVNStatusType.STATUS_IGNORED) {
                pathChangeType = "I";
            } else if (contentsStatus == SVNStatusType.STATUS_MISSING
                    || contentsStatus == SVNStatusType.STATUS_INCOMPLETE) {
                pathChangeType = "!";
            } else if (contentsStatus == SVNStatusType.STATUS_OBSTRUCTED) {
                pathChangeType = "~";
            } else if (contentsStatus == SVNStatusType.STATUS_REPLACED) {
                pathChangeType = "R";
            } else if (contentsStatus == SVNStatusType.STATUS_NONE) {
                //what mark??
                pathChangeType = "N";
            }

            SVNStatusType propertiesStatus = status.getPropertiesStatus();
            String propertiesChangeType = " ";
            if (propertiesStatus == SVNStatusType.STATUS_MODIFIED) {
                propertiesChangeType = "M";
            } else if (propertiesStatus == SVNStatusType.STATUS_CONFLICTED) {
                propertiesChangeType = "C";
            }

            boolean isLocked = status.isLocked();
            boolean isSwitched = status.isSwitched();
            SVNLock localLock = status.getLocalLock();
            SVNLock remoteLock = status.getRemoteLock();
            String lockLabel = " ";
            if (!myIsRemote) {
                if (localLock != null) {
                    /*
                     * finds out if the file is locally locKed or not
                     */
                    lockLabel = (localLock.getID() != null && !localLock
                            .getID().equals("")) ? "K" : " ";
                }
            } else {
                if (localLock != null) {
                    /*
                     * at first suppose the file is locally locKed as well as in
                     * the repository
                     */
                    lockLabel = "K";
                    if (remoteLock != null) {
                        /*
                         * author of the local lock differs from the author of
                         * the remote lock - the lock was sTolen!
                         */
                        if (!remoteLock.getOwner().equals(localLock.getOwner())) {
                            lockLabel = "T";
                        }
                    } else {
                        /*
                         * the local lock presents but there's no lock in the
                         * repository - the lock was Broken.
                         */
                        lockLabel = "B";
                    }
                } else if (remoteLock != null) {
                    /*
                     * the file is not locally locked but locked in the
                     * repository - the lock token is in some Other working
                     * copy.
                     */
                    lockLabel = "O";
                }
            }
            long workingRevision = status.getRevision().getNumber();
            long lastChangedRevision = status.getCommittedRevision().getNumber();
            String offset = "                    ";
            String[] offsets = new String[3];
            offsets[0]=offset.substring(0, 6-String.valueOf(workingRevision).length());
            offsets[1]=offset.substring(0, 6-String.valueOf(lastChangedRevision).length());
            offsets[2]=offset.substring(0, offset.length()-status.getAuthor().length());
            System.out.println(pathChangeType + propertiesChangeType
                    + (isLocked ? "L" : " ") + (isAddedWithHistory ? "+" : " ")
                    + (isSwitched ? "S" : " ") + lockLabel + "  "
                    + ((myIsRemote && pathChangeType.equals("M")) ? "*" : " ")+"  "
                    + workingRevision + offsets[0] + lastChangedRevision + offsets[1]
                    + status.getAuthor() + offsets[2] + status.getFile().getPath());
        }
    }
}