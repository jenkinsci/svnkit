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

public class WorkingCopy {
    
    public static void main(String[] args) {
        /*
         * Default values:
         */
        String url = "svn://localhost/rep";//"http://72.9.228.230:8080/svn/jsvn/branches/0.9.0/doc";
        String myWorkspacePath = "/MyWorkspace";
        SVNRevision revision = SVNRevision.HEAD;//HEAD (the latest) revision
        String name = "userName";
        String password = "userPassword";

        String newDir  = "/newDir"; 
        String newFile = newDir+"/"+"newFile.txt";
        byte[] fileText = "This is a new file added to the working copy".getBytes();
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
            myWorkspacePath = (args.length >= 2) ? args[1]
                    : myWorkspacePath;
            /*
             * Obtains a revision
             */
            revision = (args.length >= 3) ? SVNRevision.create(Long.parseLong(args[2]))
                    : revision;
            /*
             * Obtains an account name (will be used to authenticate the
             * user to the server)
             */
            name = (args.length >= 4) ? args[3] : name;
            /*
             * Obtains a password
             */
            password = (args.length >= 5) ? args[4] : password;
        }
        File wsDir = new File(myWorkspacePath);
        if (wsDir.exists()) {
            System.err.println("the destination directory '" + wsDir.getAbsolutePath() + "' already exists!");
            System.exit(1); 
        }
        wsDir.mkdirs();
        /*
         * Creates a usre's credentials provider.
         */
        ISVNCredentialsProvider scp = new SVNSimpleCredentialsProvider(
                name, password);

        UpdateClient myUpdateClient = new UpdateClient();
        long checkoutRevision=-1;
        try{
            checkoutRevision = myUpdateClient.doCheckout(scp, url, revision, wsDir, true);
        }catch(SVNException svne){
            /*
             * Perhaps a malformed URL is the cause of this exception.
             */
            System.err
                    .println("error while checking out a working copy for the location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }
        System.out.println("Checked out revision "+checkoutRevision);
        
        /*
         * show info for the working copy
         */
        WorkingCopyClient myWCClient = new WorkingCopyClient();
        try{
            myWCClient.doInfo(scp, wsDir, SVNRevision.WORKING, true);
        }catch(SVNException svne){
            System.err
                    .println("error while getting info for the working copy at'"
                            + wsDir.getAbsolutePath() + "': " + svne.getMessage());
            System.exit(1);
        }
        
        /*
         * creating a new directory
         */
        File aNewDir = new File(wsDir, newDir);
        if(!aNewDir.mkdirs()){
            System.err.println("failed to create a new directory '"+aNewDir.getAbsolutePath()+"'.");
            System.exit(1);
        }
        /*
         * creating a new file in "/MyWorkspace/newDir/"
         */
        File aNewFile = new File(aNewDir, PathUtil.tail(newFile));
        try{
            if(!aNewFile.createNewFile()){
                System.err.println("failed to create a new file '"+aNewFile.getAbsolutePath()+"'.");
                System.exit(1);
            }
        }catch(IOException ioe){
            aNewFile.delete();
            System.err.println("error while creating a new file '"+aNewFile.getAbsolutePath()+"': "+ioe.getMessage());
            System.exit(1);
        }
        
        FileOutputStream fos=null;
        try{
            fos = new FileOutputStream(aNewFile);
            fos.write(fileText);
        }catch(FileNotFoundException fnfe){
            System.err.println("the file '"+aNewFile.getAbsolutePath()+"' is not found: "+fnfe.getMessage());
            System.exit(1);
        }catch(IOException ioe){
            System.err.println("error while writing into the file '"+aNewFile.getAbsolutePath()+"' is not found: "+ioe.getMessage());
            System.exit(1);
        }finally{
            if(fos!=null){
                try{
                    fos.close();
                }catch(IOException ioe){
                    //
                }
            }
        }
        
        try{
            myWCClient.addDirectory(scp, aNewDir);
        }catch(SVNException svne){
            System.err.println("error while recursively adding the directory '"+aNewDir.getAbsolutePath()+"': "+svne.getMessage());
            System.exit(1);
        }

        boolean isRecursive = true;
        boolean isRemote = false;
        boolean isReportAll = false;
        boolean isIncludeIgnored = true;
        boolean isCollectParentExternals = true;
        StatusClient myStatusClient = new StatusClient(isRecursive, isRemote, isReportAll, isIncludeIgnored, isCollectParentExternals);
        System.out.println();
        System.out.println("Status for '"+wsDir.getAbsolutePath()+"':");
        try{
            myStatusClient.doStatus(scp, wsDir);
        } catch (SVNException svne) {
            System.err.println("error while performing status for '"
                    + wsDir.getAbsolutePath() + "': " + svne.getMessage());
            System.exit(1);
        }

        long updatedRevision=-1;
        try{
            updatedRevision = myUpdateClient.doUpdate(scp, wsDir, SVNRevision.HEAD, true);
        }catch(SVNException svne){
            System.err.println("error while recursively updating the working copy '"+wsDir.getAbsolutePath()+"': "+svne.getMessage());
            System.exit(1);
        }
        System.out.println();
        System.out.println("Updating '"+wsDir.getAbsolutePath()+"'...");
        try{
            myStatusClient.doStatus(scp, wsDir);
        } catch (SVNException svne) {
            System.err.println("error while performing status for '"
                    + wsDir.getAbsolutePath() + "': " + svne.getMessage());
            System.exit(1);
        }
        System.out.println("Updated to revision "+updatedRevision);
        
        long committedRevision = -1;
        CommitClient myCommitClient = new CommitClient();
        try{
            committedRevision = myCommitClient.doCommit(scp, wsDir, true, "'/newDir' with '/newDir/newFile.txt' were added").getNewRevision();
        }catch(SVNException svne){
            System.err.println("error while recursively updating the working copy '"+wsDir.getAbsolutePath()+"': "+svne.getMessage());
            System.exit(1);
        }
        System.out.println();
        System.out.println("Committing '"+wsDir.getAbsolutePath()+"'...");
        System.out.println("Committed to revision "+committedRevision);
        
        try{
            myWCClient.doLock(scp, aNewDir, true, "locking '/newDir'");
        }catch(SVNException svne){
            System.err.println("error while locking the working copy directory '"+aNewDir.getAbsolutePath()+"': "+svne.getMessage());
            System.exit(1);
        }
        System.out.println();
        System.out.println("Locking (with stealing if the entry is already locked) '"+aNewDir.getAbsolutePath()+"'.");

        System.out.println();
        System.out.println("Status for '"+wsDir.getAbsolutePath()+"':");
        try{
            myStatusClient.doStatus(scp, wsDir);
        } catch (SVNException svne) {
            System.err.println("error while performing status for '"
                    + wsDir.getAbsolutePath() + "': " + svne.getMessage());
            System.exit(1);
        }
        
        
/*        
        ISVNWorkspace myWorkspace = null;
        try {

            myWorkspace = SVNWorkspaceManager.createWorkspace("file", wsDir
                    .getAbsolutePath());
            //checkout
            //            long wcRevision = myWorkspace.checkout(location,
            //                    revision, false);
            //            System.out.println("Checked out revision " + wcRevision +
            // ".");
        } catch (SVNException svne) {
            System.err
                    .println("error while creating an SVNRepository for the location '"
                            + url + "': " + svne.getMessage());
            System.exit(1);
        }
*/
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
        /*
         * Working copy storage (default is file system).
         */
        FSEntryFactory.setup();
    }
    
    private static class CommitClient{
        public SVNCommitInfo doCommit(ISVNCredentialsProvider cp, File path, boolean keepLocks, String commitMessage) throws SVNException{
            SVNCommitClient myCommitClient = new SVNCommitClient(cp);
            return myCommitClient.doCommit(new File[]{path}, keepLocks, commitMessage, true);
        }
    }
    
    private static class UpdateClient{
        public long doCheckout(ISVNCredentialsProvider cp, String url, SVNRevision revision, File destPath, boolean isRecursive)throws SVNException{
            SVNUpdateClient myUpdateClient = new SVNUpdateClient(cp);
            myUpdateClient.setIgnoreExternals(false);
            return myUpdateClient.doCheckout(url, destPath, revision, revision, isRecursive);
        }

        public long doUpdate(ISVNCredentialsProvider cp, File updatePath, SVNRevision updateToRevision, boolean isRecursive) throws SVNException{
            SVNUpdateClient myUpdateClient = new SVNUpdateClient(cp);
            myUpdateClient.setIgnoreExternals(false);
            return myUpdateClient.doUpdate(updatePath, updateToRevision, isRecursive);
        }
    }
    /*
     * Responsible for a performance of the working copy status. 
     */
    private static class StatusClient implements ISVNStatusHandler{
        private boolean myIsRecursive;
        private boolean myIsRemote;
        private boolean myIsReportAll;
        private boolean myIsIncludeIgnored;
        private boolean myIsCollectParentExternals;
        
        public StatusClient(boolean isRecursive, boolean isRemote, boolean isReportAll, boolean isIncludeIgnored, boolean isCollectParentExternals) {
            myIsRecursive = isRecursive;
            myIsRemote = isRemote;
            myIsReportAll = isReportAll;
            myIsIncludeIgnored = isIncludeIgnored;
            myIsCollectParentExternals = isCollectParentExternals;
        }
        
        public void doStatus(ISVNCredentialsProvider cp, File wcPath) throws SVNException{
            SVNStatusClient myStatusClient = new SVNStatusClient(cp);
                
            myStatusClient.doStatus(wcPath, myIsRecursive, myIsRemote,
                    myIsReportAll, myIsIncludeIgnored,
                    myIsCollectParentExternals, this);
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
            } else if (contentsStatus == SVNStatusType.STATUS_NONE || contentsStatus == SVNStatusType.STATUS_NORMAL) {
                pathChangeType = " ";
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
                     * at first suppose the file is locally locKed as well
                     * as in the repository
                     */
                    lockLabel = "K";
                    if (remoteLock != null) {
                        /*
                         * author of the local lock differs from the author
                         * of the remote lock - the lock was sTolen!
                         */
                        if (!remoteLock.getOwner().equals(
                                localLock.getOwner())) {
                            lockLabel = "T";
                        }
                    } else {
                        /*
                         * the local lock presents but there's no lock in
                         * the repository - the lock was Broken.
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
            long lastChangedRevision = status.getCommittedRevision()
                    .getNumber();
            String offset = "                    ";
            String[] offsets = new String[3];
            offsets[0] = offset.substring(0, 6 - String.valueOf(
                    workingRevision).length());
            offsets[1] = offset.substring(0, 6 - String.valueOf(
                    lastChangedRevision).length());
            //status
            offsets[2] = offset.substring(0, offset.length()
                    - (status.getAuthor()!=null ? status.getAuthor().length() : 1));
            /*
             * status is shown in the manner of the native Subversion command
             * line client's command "svn status"
             */
            System.out.println(pathChangeType
                    + propertiesChangeType
                    + (isLocked ? "L" : " ")
                    + (isAddedWithHistory ? "+" : " ")
                    + (isSwitched ? "S" : " ")
                    + lockLabel
                    + "  "
                    + ((myIsRemote && pathChangeType.equals("M")) ? "*"
                            : " ") + "  " + workingRevision + offsets[0]
                    + lastChangedRevision + offsets[1] + (status.getAuthor()!=null ? status.getAuthor() : "?")
                    + offsets[2] + status.getFile().getPath());
        }        
    }
    /*
     * Info 
     */
    private static class WorkingCopyClient implements ISVNInfoHandler{
        /*
         * This is an implementation of ISVNHandler.handleInfo(SVNInfo info)
         */
        public void handleInfo(SVNInfo info){
            System.out.println("-----------------INFO-----------------");
            System.out.println("Repository Root URL: "+info.getRepositoryRootURL());
            System.out.println("Path: "+info.getPath());
            System.out.println("URL: "+info.getURL());
            System.out.println("Repository UUID: "+info.getRepositoryUUID());
            System.out.println("Revision: "+info.getRevision().getNumber());
            System.out.println("Node Kind: "+info.getKind().toString());
            System.out.println("Schedule: "+info.getSchedule());
            System.out.println("Last Changed Author: "+info.getAuthor());
            System.out.println("Last Changed Revision: "+info.getCommittedRevision().getNumber());
            System.out.println("Last Changed Date: "+info.getCommittedRevision().getDate());
            System.out.println("Properties Last Updated: "+info.getPropTime());
        }
        
        public void doInfo(ISVNCredentialsProvider cp, File wcPath, SVNRevision revision, boolean isRecursive) throws SVNException{
            SVNWCClient myWCClient = new SVNWCClient(cp);
            myWCClient.doInfo(wcPath, revision, isRecursive, this);
            myWCClient=null;
        }
        public void addDirectory(ISVNCredentialsProvider cp, File newDir) throws SVNException{
            SVNWCClient myWCClient = new SVNWCClient(cp);
            myWCClient.doAdd(newDir, false, false, false, true);
            myWCClient=null;
        }
        public void doLock(ISVNCredentialsProvider cp, File wcPath, boolean isStealLock, String lockComment) throws SVNException{
            SVNWCClient myWCClient = new SVNWCClient(cp);
            myWCClient.doLock(new File[]{wcPath}, isStealLock, lockComment);
            myWCClient=null;
        }
        public void doDelete(ISVNCredentialsProvider cp, File wcPath, boolean force) throws SVNException{
            SVNWCClient myWCClient = new SVNWCClient(cp);
            myWCClient.doDelete(wcPath, force, false);
            myWCClient=null;
        }
    }
    private static class CopyClient{
        public void doServerSideCopy(ISVNCredentialsProvider cp, String srcUrl, String dstUrl) throws SVNException{
            SVNCopyClient myCopyClient = new SVNCopyClient(cp);
        }
    }
}