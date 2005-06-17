/*
 * Created on 16.06.2005
 */
package org.tigris.subversion.javahl;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.internal.io.svn.SVNJSchSession;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.SVNUtil;

/**
 * @author evgeny
 */
public class SVNClient implements SVNClientInterface {

    private String myConfigDir;
    private PromptUserPassword myPrompt;
    public void dispose() {
        SVNJSchSession.shutdown();
    }

    public String getLastPath() {
        // TODO Auto-generated method stub
        return null;
    }

    public Status[] status(String path, boolean descend, boolean onServer, boolean getAll) throws ClientException {
        return status(path, descend, onServer, getAll, false);
    }

    public Status[] status(String path, boolean descend, boolean onServer, boolean getAll, boolean noIgnore) throws ClientException {
        return status(path, descend, onServer, getAll, noIgnore, false);
    }

    public Status[] status(final String path, boolean descend, boolean onServer, boolean getAll, boolean noIgnore, boolean ignoreExternals) throws ClientException {
        if (path == null) {
            DebugLog.log("status doesn't accept NULL path");
            return null;
        }        
        DebugLog.log("STATUS PARAMS: "+ descend + "," + onServer + "," + getAll + "," + noIgnore);
        DebugLog.log("IO fetching status for: " + path);
       
        final Collection statuses = new LinkedList();
        SVNStatusClient stClient = new SVNStatusClient();
        try {
            stClient.doStatus(new File(path), descend, onServer, getAll, noIgnore, !ignoreExternals, new ISVNStatusHandler(){
                public void handleStatus(SVNStatus status) {
                    statuses.add(createStatus(path, status));
                }
            });
        } catch (SVNException e) {
            return new Status[] {};
        }
        return (Status[]) statuses.toArray(new Status[statuses.size()]);
    }

    public DirEntry[] list(String url, Revision revision, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public DirEntry[] list(String url, Revision revision, Revision pegRevision, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public Status singleStatus(String path, boolean onServer) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public void username(String username) {
        // TODO Auto-generated method stub
        
    }

    public void password(String password) {
        // TODO Auto-generated method stub
        
    }

    public void setPrompt(PromptUserPassword prompt) {
        DebugLog.log("prompt set: " + prompt);
        myPrompt = prompt;
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy, boolean discoverPath) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy, boolean discoverPath, long limit) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public long checkout(String moduleName, String destPath, Revision revision, Revision pegRevision, boolean recurse, boolean ignoreExternals) throws ClientException {
        // TODO Auto-generated method stub
        return 0;
    }

    public long checkout(String moduleName, String destPath, Revision revision, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        return 0;
    }

    public void notification(Notify notify) {
        // TODO Auto-generated method stub
        
    }

    public void notification2(Notify2 notify) {
        // TODO Auto-generated method stub
        
    }

    public void commitMessageHandler(CommitMessage messageHandler) {
        // TODO Auto-generated method stub
        
    }

    public void remove(String[] path, String message, boolean force) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void revert(String path, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void add(String path, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void add(String path, boolean recurse, boolean force) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public long update(String path, Revision revision, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        return 0;
    }

    public long[] update(String[] path, Revision revision, boolean recurse, boolean ignoreExternals) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public long commit(String[] path, String message, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        return 0;
    }

    public long commit(String[] path, String message, boolean recurse, boolean noUnlock) throws ClientException {
        // TODO Auto-generated method stub
        return 0;
    }

    public void copy(String srcPath, String destPath, String message, Revision revision) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void move(String srcPath, String destPath, String message, Revision revision, boolean force) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void move(String srcPath, String destPath, String message, boolean force) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void mkdir(String[] path, String message) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void cleanup(String path) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void resolved(String path, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public long doExport(String srcPath, String destPath, Revision revision, boolean force) throws ClientException {
        // TODO Auto-generated method stub
        return 0;
    }

    public long doExport(String srcPath, String destPath, Revision revision, Revision pegRevision, boolean force, boolean ignoreExternals, boolean recurse, String nativeEOL) throws ClientException {
        // TODO Auto-generated method stub
        return 0;
    }

    public long doSwitch(String path, String url, Revision revision, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        return 0;
    }

    public void doImport(String path, String url, String message, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void merge(String path1, Revision revision1, String path2, Revision revision2, String localPath, boolean force, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void merge(String path1, Revision revision1, String path2, Revision revision2, String localPath, boolean force, boolean recurse, boolean ignoreAncestry, boolean dryRun) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void merge(String path, Revision pegRevision, Revision revision1, Revision revision2, String localPath, boolean force, boolean recurse, boolean ignoreAncestry, boolean dryRun) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void diff(String target1, Revision revision1, String target2, Revision revision2, String outFileName, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void diff(String target1, Revision revision1, String target2, Revision revision2, String outFileName, boolean recurse, boolean ignoreAncestry, boolean noDiffDeleted, boolean force) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void diff(String target, Revision pegRevision, Revision startRevision, Revision endRevision, String outFileName, boolean recurse, boolean ignoreAncestry, boolean noDiffDeleted, boolean force) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public PropertyData[] properties(String path) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public PropertyData[] properties(String path, Revision revision) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public PropertyData[] properties(String path, Revision revision, Revision pegRevision) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public void propertySet(String path, String name, byte[] value, boolean recurse) throws ClientException {
        propertySet(path, name, new String(value), recurse);
    }

    public void propertySet(String path, String name, byte[] value, boolean recurse, boolean force) throws ClientException {
        propertySet(path, name, new String(value), recurse, force);
    }

    public void propertySet(String path, String name, String value, boolean recurse) throws ClientException {
        propertySet(path, name, value, recurse, false);
    }

    public void propertySet(String path, String name, String value, boolean recurse, boolean force) throws ClientException {
        SVNWCClient client = new SVNWCClient();
        try {
            client.doSetProperty(new File(path), name, value, force, recurse, new ISVNPropertyHandler(){
                public void handleProperty(File fpath, SVNPropertyData property) throws SVNException {}
                public void handleProperty(String url, SVNPropertyData property) throws SVNException {}
            });
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void propertyRemove(String path, String name, boolean recurse) throws ClientException {
        SVNWCClient client = new SVNWCClient();
        try {
            // XXX : force?
            client.doSetProperty(new File(path), name, null, true, recurse, new ISVNPropertyHandler(){
                public void handleProperty(File fpath, SVNPropertyData property) throws SVNException {}
                public void handleProperty(String url, SVNPropertyData property) throws SVNException {}
            });
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void propertyCreate(String path, String name, String value, boolean recurse) throws ClientException {
        propertyCreate(path, name, value, recurse, false);
    }

    public void propertyCreate(String path, String name, String value, boolean recurse, boolean force) throws ClientException {
        if (value == null) {
            value = "";
        }
        SVNWCClient client = new SVNWCClient();
        try {
            client.doSetProperty(new File(path), name, value, force, recurse, new ISVNPropertyHandler(){
                public void handleProperty(File fpath, SVNPropertyData property) throws SVNException {}
                public void handleProperty(String url, SVNPropertyData property) throws SVNException {}
            });
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void propertyCreate(String path, String name, byte[] value, boolean recurse) throws ClientException {
        propertyCreate(path, name, value == null ? null : new String(value), recurse);
    }

    public void propertyCreate(String path, String name, byte[] value, boolean recurse, boolean force) throws ClientException {
        propertyCreate(path, name, value == null ? null : new String(value), recurse, force);
    }

    public PropertyData revProperty(String path, String name, Revision rev) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public PropertyData[] revProperties(String path, Revision rev) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public void setRevProperty(String path, String name, Revision rev, String value, boolean force) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public PropertyData propertyGet(String path, String name) throws ClientException {
        return propertyGet(path, name, new Revision(RevisionKind.unspecified, true));
    }

    public PropertyData propertyGet(String path, String name, Revision revision) throws ClientException {
        return propertyGet(path, name, revision, revision);
    }
    
    private class SVNPropertyRetriever implements ISVNPropertyHandler{
        
        private PropertyData myData = null;

        public void handleProperty(File path, SVNPropertyData property) throws SVNException {
            myData = new PropertyData(SVNClient.this, path.getAbsolutePath(), property.getName(), property.getValue(), property.getValue().getBytes());
        }

        public void handleProperty(String url, SVNPropertyData property) throws SVNException {
            myData = new PropertyData(SVNClient.this, url, property.getName(), property.getValue(), property.getValue().getBytes());
        }
        
        public PropertyData getPropertyData(){
            if(myData.getValue() == null){
                return null;
            }
            return myData;
        }
        
    }

    public PropertyData propertyGet(String path, String name, Revision revision, Revision pegRevision) throws ClientException {
        if(name == null || name.equals("")){
            return null;
        }
        SVNWCClient client = new SVNWCClient();
        SVNRevision svnRevision = (SVNRevision)REVISION_KIND_CONVERSION_MAP.get(new Integer(revision.getKind()));
        SVNRevision svnPegRevision = (SVNRevision)REVISION_KIND_CONVERSION_MAP.get(new Integer(pegRevision.getKind()));
        SVNPropertyRetriever retriever = new SVNPropertyRetriever();
        try {
            client.doGetProperty(path, name, svnPegRevision, svnRevision, false, retriever);
        } catch (SVNException e) {
            throwException(e);
        }
        return retriever.getPropertyData();
    }

    public byte[] fileContent(String path, Revision revision) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public byte[] fileContent(String path, Revision revision, Revision pegRevision) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public void relocate(String from, String to, String path, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public byte[] blame(String path, Revision revisionStart, Revision revisionEnd) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public void blame(String path, Revision revisionStart, Revision revisionEnd, BlameCallback callback) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void blame(String path, Revision pegRevision, Revision revisionStart, Revision revisionEnd, BlameCallback callback) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void setConfigDirectory(String configDir) throws ClientException {
        myConfigDir = configDir;
    }

    public String getConfigDirectory() throws ClientException {
        return myConfigDir;
    }

    public void cancelOperation() throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public Info info(String path) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public void lock(String[] path, String comment, boolean force) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void unlock(String[] path, boolean force) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public Info2[] info2(String pathOrUrl, Revision revision, Revision pegRevision, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    public String getVersionInfo(String path, String trailUrl, boolean lastChanged) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }
    
    private static Status createStatus(String path, SVNStatus status) {
        String url = "";
        int nodeKind = NodeKind.unknown;
        SVNNodeKind svnKind = status.getKind();
        if(svnKind == SVNNodeKind.DIR ){
            nodeKind = NodeKind.dir;
        }else if(svnKind == SVNNodeKind.DIR ){
            nodeKind = NodeKind.dir;
        }else if(svnKind == SVNNodeKind.FILE ){
            nodeKind = NodeKind.file;
        } 
        if (status.getContentsStatus() == SVNStatusType.STATUS_IGNORED) {
            nodeKind = NodeKind.unknown;
        }
        long revision = status.getRevision().getNumber();
        long lastChangedRevision = status.getCommittedRevision().getNumber();
        long lastChangedDate = status.getCommittedDate().getTime();
        String lastCommitAuthor = status.getAuthor();
        int textStatus = ((Integer)STATUS_CONVERSION_MAP.get(status.getContentsStatus())).intValue();
        int propStatus = ((Integer)STATUS_CONVERSION_MAP.get(status.getPropertiesStatus())).intValue();
        int repositoryTextStatus = ((Integer)STATUS_CONVERSION_MAP.get(status.getRemoteContentsStatus())).intValue();
        int repositoryPropStatus = ((Integer)STATUS_CONVERSION_MAP.get(status.getRemotePropertiesStatus())).intValue();
        boolean locked = status.isLocked();
        boolean copied = status.isCopied();
        boolean switched = status.isSwitched();
        
        String conflictOld = status.getConflictOldFile().getAbsolutePath();
        String conflictNew = status.getConflictNewFile().getAbsolutePath();
        String conflictWorking = status.getConflictWrkFile().getAbsolutePath();
        String urlCopiedFrom = status.getCopyFromURL();
        long revisionCopiedFrom = status.getCopyFromRevision().getNumber();
        String lockToken = status.getLocalLock().getID();
        String lockOwner = status.getLocalLock().getOwner();
        String lockComment = status.getLocalLock().getComment();
        long lockCreationDate = status.getLocalLock().getCreationDate().getTime();
        Lock reposLock = new Lock(lockOwner, status.getLocalLock().getPath(), lockToken, lockComment, lockCreationDate, status.getLocalLock().getExpirationDate().getTime());
        
        Status st = new Status(path, url, nodeKind, revision, lastChangedRevision, lastChangedDate, lastCommitAuthor, textStatus, propStatus,
                repositoryTextStatus, repositoryPropStatus, locked, copied, conflictOld, conflictNew, conflictWorking, urlCopiedFrom, revisionCopiedFrom,
                switched, lockToken, lockOwner, lockComment, lockCreationDate, reposLock);
        DebugLog.log(path + ": created status: " + st.getTextStatus() + ":" + st.getPropStatus() + ":" + st.getNodeKind());
        return st;
    }
    
    protected ISVNCredentialsProvider getCredentialsProvider() {
        return new SVNSimpleCredentialsProvider(myPrompt.getUsername(), myPrompt.getPassword());
    }
    
    private static final Map STATUS_CONVERSION_MAP = new HashMap();
    private static final Map REVISION_KIND_CONVERSION_MAP = new HashMap();
    static{
        STATUS_CONVERSION_MAP.put(SVNStatusType.STATUS_NORMAL, new Integer(StatusKind.normal));
        // TODO: !!!
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.base), SVNRevision.BASE);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.committed), SVNRevision.COMMITTED);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.head), SVNRevision.HEAD);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.previous), SVNRevision.PREVIOUS);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.unspecified), SVNRevision.UNDEFINED);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.working), SVNRevision.WORKING);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.number), SVNRevision.UNDEFINED);
        REVISION_KIND_CONVERSION_MAP.put(new Integer(RevisionKind.date), SVNRevision.UNDEFINED);
    }

    private static void throwException(SVNException e) throws ClientException {
        ClientException ec = new ClientException(e.getMessage(), "", 0);
        DebugLog.error(ec);
        DebugLog.error(e);
        if (e.getErrors() != null) {
            for(int i = 0; i < e.getErrors().length; i++) {
                DebugLog.log(e.getErrors()[i].getMessage());
            }
        }
        throw ec;
    }

}
