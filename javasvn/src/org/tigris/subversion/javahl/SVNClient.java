/*
 * Created on 16.06.2005
 */
package org.tigris.subversion.javahl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.TreeSet;

import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.internal.io.svn.SVNJSchSession;
import org.tmatesoft.svn.core.internal.wc.SVNOptions;
import org.tmatesoft.svn.core.io.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.io.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLogEntry;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;
import org.tmatesoft.svn.core.wc.ISVNEventListener;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author evgeny
 */
public class SVNClient implements SVNClientInterface {

    private String myConfigDir;
    private PromptUserPassword myPrompt;
    private String myUserName;
    private String myPassword;
    private ISVNEventListener mySVNEventListener;
    private Notify myNotify;
    private Notify2 myNotify2;
    private CommitMessage myMessageHandler;
    private ISVNOptions myOptions;

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
       
        final Collection statuses = new ArrayList();
        SVNStatusClient stClient = createSVNStatusClient();
        try {
            stClient.doStatus(new File(path).getAbsoluteFile(), descend, onServer, getAll, noIgnore, !ignoreExternals, new ISVNStatusHandler(){
                public void handleStatus(SVNStatus status) {
                    statuses.add(SVNConverterUtil.createStatus(status.getFile().getPath(), status));
                    /*try {
                        statuses.add(SVNConverterUtil.createStatus(status.getFile().getCanonicalPath(), status));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/
                }
            });
        } catch (SVNException e) {
            return new Status[] {};
        }
        return (Status[]) statuses.toArray(new Status[statuses.size()]);
    }

    public DirEntry[] list(String url, Revision revision, boolean recurse) throws ClientException {
        return list(url, revision, null, recurse);
    }
    
    public DirEntry[] list(String url, Revision revision, Revision pegRevision, boolean recurse) throws ClientException {
        final TreeSet allEntries = new TreeSet(new Comparator() {
            public int compare(Object o1, Object o2) {
                DirEntry d1 = (DirEntry) o1;
                DirEntry d2 = (DirEntry) o2;
                if (d1 == null || d1.getPath() == null) {
                    return d2 == null || d2.getPath() == null ? 0 : -1;
                } else if (d2 == null || d2.getPath() == null) {
                    return 1;
                }                
                return d1.getPath().toLowerCase().compareTo(d2.getPath().toLowerCase());
            }
        });
        SVNLogClient client = createSVNLogClient();
        DebugLog.log("LIST is called for " + url);
        try {
            client.doList(url, SVNConverterUtil.getSVNRevision(pegRevision),
                    SVNConverterUtil.getSVNRevision(revision), recurse, new ISVNDirEntryHandler(){
                        public void handleDirEntry(SVNDirEntry dirEntry) {
                            allEntries.add(SVNConverterUtil.createDirEntry(dirEntry));
                        }
            });
        } catch (SVNException e) {
            throwException(e);
        }
        return (DirEntry[]) allEntries.toArray(new DirEntry[allEntries.size()]);
    }

    public Status singleStatus(final String path, boolean onServer) throws ClientException {
        if (path == null) {
            return null;
        }
        DebugLog.log("IO fetching 'single' status for: " + path);
        
        SVNStatusClient client = createSVNStatusClient();
        SVNSingleStatusRetriever retriever = new SVNSingleStatusRetriever();
        try {
            client.doStatus(new File(path).getAbsoluteFile(), false, onServer, false, false, false, retriever);
        } catch (SVNException e) {
            throwException(e);
        }
        return SVNConverterUtil.createStatus(path, retriever.getStatus());
    }

    public void username(String username) {
        myUserName = username;
        getSVNOptions();
    }

    public void password(String password) {
        myPassword = password;
        getSVNOptions();
    }

    public void setPrompt(PromptUserPassword prompt) {
        DebugLog.log("prompt set: " + prompt);
        myPrompt = prompt;
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd) throws ClientException {
        return logMessages(path, revisionStart, revisionEnd, true, false, 0);
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy) throws ClientException {
        return logMessages(path, revisionStart, revisionEnd, stopOnCopy, false, 0);
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy, boolean discoverPath) throws ClientException {
        return logMessages(path, revisionStart, revisionEnd, stopOnCopy, discoverPath, 0);
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy, boolean discoverPath, long limit) throws ClientException {
        SVNLogClient client = createSVNLogClient();
        final Collection entries = new ArrayList();
        try {
            client.doLog(
                    new File[]{new File(path).getAbsoluteFile()},
                    SVNConverterUtil.getSVNRevision(revisionStart),
                    SVNConverterUtil.getSVNRevision(revisionEnd),
                    stopOnCopy, discoverPath, limit, new ISVNLogEntryHandler(){
                        public void handleLogEntry(SVNLogEntry logEntry) {
                            entries.add(SVNConverterUtil.createLogMessage(logEntry));
                        }
                    }
                    );
        } catch (SVNException e) {
            throwException(e);
        }
        return (LogMessage[]) entries.toArray(new LogMessage[entries.size()]);
    }

    public long checkout(String moduleName, String destPath, Revision revision, Revision pegRevision, boolean recurse, boolean ignoreExternals) throws ClientException {
        try {
            File path = new File(destPath).getAbsoluteFile();

            SVNUpdateClient updater = createSVNUpdateClient();
            updater.setIgnoreExternals(ignoreExternals);
            return updater.doCheckout(moduleName, path, SVNConverterUtil.getSVNRevision(pegRevision),
                    SVNConverterUtil.getSVNRevision(revision), recurse);
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    public long checkout(String moduleName, String destPath, Revision revision, boolean recurse) throws ClientException {
        return checkout(moduleName, destPath, revision, null, recurse, false);
    }

    public void notification(Notify notify) {
        myNotify = notify;
    }

    public void notification2(Notify2 notify) {
        myNotify2 = notify;
    }

    public void commitMessageHandler(CommitMessage messageHandler) {
        myMessageHandler = messageHandler;
    }

    public void remove(String[] path, String message, boolean force) throws ClientException {
        // XXX: force ?
        SVNCommitClient client = createSVNCommitClient();
        try {
            client.doDelete(path, message);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void revert(String path, boolean recurse) throws ClientException {
        SVNWCClient client = createSVNWCClient();
        try {
            client.doRevert(new File(path).getAbsoluteFile(), recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void add(String path, boolean recurse) throws ClientException {
        add(path, recurse, false);
    }

    public void add(String path, boolean recurse, boolean force) throws ClientException {
        SVNWCClient wcClient = createSVNWCClient();
        try {
            wcClient.doAdd(new File(path).getAbsoluteFile(), force, true, false, recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public long update(String path, Revision revision, boolean recurse) throws ClientException {
        SVNUpdateClient client = createSVNUpdateClient();
        try {
            return client.doUpdate(new File(path).getAbsoluteFile(), SVNConverterUtil.getSVNRevision(revision), recurse);
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    public long[] update(String[] path, Revision revision, boolean recurse, boolean ignoreExternals) throws ClientException {
        if(path == null || path.length == 0){
            return new long[]{};
        }
        long[] updated = new long[path.length];
        for (int i = 0; i < updated.length; i++) {
            updated[i] = update(path[i], revision, recurse);
        }
        return updated;
    }

    public long commit(String[] path, String message, boolean recurse) throws ClientException {
        // XXX: noUnlock : do NOT remove any locks, default == false
        return commit(path, message, recurse, false);
    }

    public long commit(String[] path, String message, boolean recurse, boolean noUnlock) throws ClientException {
        if(path == null || path.length == 0){
            return 0;
        }
        SVNCommitClient client = createSVNCommitClient();
        File[] files = new File[path.length];
        for (int i = 0; i < path.length; i++) {
            files[i] = new File(path[i]).getAbsoluteFile();
        }
        try {
            if(myMessageHandler != null){
                client.setCommitHander(new ISVNCommitHandler(){
                    public String getCommitMessage(String cmessage, SVNCommitItem[] commitables) throws SVNException {
                        CommitItem[] items = SVNConverterUtil.getCommitItems(commitables);
                        return myMessageHandler.getLogMessage(items);
                    }
                });
            }
            return client.doCommit(files, noUnlock, message, false, recurse).getNewRevision();
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    public void copy(String srcPath, String destPath, String message, Revision revision) throws ClientException {
        SVNCopyClient client = createSVNCopyClient();
        try {
            client.doCopy(new File(srcPath).getAbsoluteFile(), null, SVNConverterUtil.getSVNRevision(revision),
                    new File(destPath), null, SVNRevision.WORKING, false, false, null);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void move(String srcPath, String destPath, String message, Revision revision, boolean force) throws ClientException {
        SVNCopyClient updater = createSVNCopyClient();
        try {
            updater.doCopy(srcPath, null, SVNConverterUtil.getSVNRevision(revision),
                    destPath, null, true, message);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void move(String srcPath, String destPath, String message, boolean force) throws ClientException {
        move(srcPath, destPath, message, Revision.WORKING, force);
    }

    public void mkdir(String[] path, String message) throws ClientException {
        SVNCommitClient client = createSVNCommitClient();
        try {
            client.doMkDir(path, message);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void cleanup(String path) throws ClientException {
        SVNWCClient client = createSVNWCClient();
        try {
            client.doCleanup(new File(path).getAbsoluteFile());
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void resolved(String path, boolean recurse) throws ClientException {
        SVNWCClient client = createSVNWCClient();
        try {
            client.doResolve(new File(path).getAbsoluteFile(), recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public long doExport(String srcPath, String destPath, Revision revision, boolean force) throws ClientException {
        return doExport(srcPath, destPath, revision, null, force, false, true, "");
    }

    public long doExport(String srcPath, String destPath, Revision revision, Revision pegRevision, boolean force, boolean ignoreExternals, boolean recurse, String nativeEOL) throws ClientException {
        SVNUpdateClient updater = createSVNUpdateClient();
        try {
            return updater.doExport(srcPath, new File(destPath).getAbsoluteFile(),
                    SVNConverterUtil.getSVNRevision(revision), SVNConverterUtil.getSVNRevision(pegRevision), nativeEOL, force, recurse);
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    public long doSwitch(String path, String url, Revision revision, boolean recurse) throws ClientException {
        SVNUpdateClient updater = createSVNUpdateClient();
        try {
            return updater.doSwitch(new File(path).getAbsoluteFile(), url, SVNConverterUtil.getSVNRevision(revision), recurse);
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    public void doImport(String path, String url, String message, boolean recurse) throws ClientException {
        SVNCommitClient commitClient = createSVNCommitClient();
        try {
            commitClient.doImport(new File(path), url, message, recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void merge(String path1, Revision revision1, String path2, Revision revision2, String localPath, boolean force, boolean recurse) throws ClientException {
        merge(path1, revision1, path2, revision2, localPath, force, recurse, false, false);
    }

    public void merge(String path1, Revision revision1, String path2, Revision revision2, String localPath, boolean force, boolean recurse, boolean ignoreAncestry, boolean dryRun) throws ClientException {
        SVNDiffClient differ = createSVNDiffClient();
        try {
            differ.doMerge(path1, path2, SVNConverterUtil.getSVNRevision(revision1), SVNConverterUtil.getSVNRevision(revision2), new File(localPath).getAbsoluteFile(), recurse, !ignoreAncestry, force, dryRun);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void merge(String path, Revision pegRevision, Revision revision1, Revision revision2, String localPath, boolean force, boolean recurse, boolean ignoreAncestry, boolean dryRun) throws ClientException {
        SVNDiffClient differ = createSVNDiffClient();
        try {
            differ.doMerge(path, SVNConverterUtil.getSVNRevision(pegRevision), SVNConverterUtil.getSVNRevision(revision1), SVNConverterUtil.getSVNRevision(revision2), new File(localPath).getAbsoluteFile(), recurse, !ignoreAncestry, force, dryRun);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void diff(String target1, Revision revision1, String target2, Revision revision2, String outFileName, boolean recurse) throws ClientException {
        // TODO Auto-generated method stub
        
    }

    public void diff(String target1, Revision revision1, String target2, Revision revision2, String outFileName, boolean recurse, boolean ignoreAncestry, boolean noDiffDeleted, boolean force) throws ClientException {
        SVNDiffClient differ = createSVNDiffClient();
        differ.setDiffGenerator(new DefaultSVNDiffGenerator() {            
            public String getDisplayPath(File file) {
                return SVNCommand.getPath(file).replace(File.separatorChar, '/');
            }
            public void displayFileDiff(String path, File file1, File file2,
                    String rev1, String rev2, String mimeType1, String mimeType2,
                    OutputStream result) throws SVNException {
                super.displayFileDiff(path, file1, file2, rev1, rev2, mimeType1, mimeType2, result);
            }
            public void displayPropDiff(String path, Map baseProps, Map diff, OutputStream result) throws SVNException {
                super.displayPropDiff(path.replace('/', File.separatorChar), baseProps, diff, result);
            }
        });
        differ.getDiffGenerator().setDiffDeleted(!noDiffDeleted);
        differ.getDiffGenerator().setForcedBinaryDiff(force);
        SVNRevision peg1 = SVNRevision.UNDEFINED;
        SVNRevision peg2 = SVNRevision.UNDEFINED;
        SVNRevision rev1 = SVNConverterUtil.getSVNRevision(revision1);
        SVNRevision rev2 = SVNConverterUtil.getSVNRevision(revision2);
        try {
            FileOutputStream fos = new FileOutputStream(new File(outFileName));
            differ.doDiff(target1, peg1, target2, peg2, rev1, rev2, recurse, !ignoreAncestry, fos);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void diff(String target, Revision pegRevision, Revision startRevision, Revision endRevision, String outFileName, boolean recurse, boolean ignoreAncestry, boolean noDiffDeleted, boolean force) throws ClientException {
        // TODO Auto-generated method stub
        SVNDiffClient differ = createSVNDiffClient();
        differ.setDiffGenerator(new DefaultSVNDiffGenerator() {            
            public String getDisplayPath(File file) {
                return SVNCommand.getPath(file).replace(File.separatorChar, '/');
            }
            public void displayFileDiff(String path, File file1, File file2,
                    String rev1, String rev2, String mimeType1, String mimeType2,
                    OutputStream result) throws SVNException {
                super.displayFileDiff(path, file1, file2, rev1, rev2, mimeType1, mimeType2, result);
            }
            public void displayPropDiff(String path, Map baseProps, Map diff, OutputStream result) throws SVNException {
                super.displayPropDiff(path.replace('/', File.separatorChar), baseProps, diff, result);
            }
        });
        differ.getDiffGenerator().setDiffDeleted(!noDiffDeleted);
        differ.getDiffGenerator().setForcedBinaryDiff(force);
        SVNRevision peg = SVNRevision.UNDEFINED;
        SVNRevision rev1 = SVNConverterUtil.getSVNRevision(startRevision);
        SVNRevision rev2 = SVNConverterUtil.getSVNRevision(endRevision);
        try {
            FileOutputStream fos = new FileOutputStream(new File(outFileName));
            differ.doDiff(target, peg, null/*XXX: File path?*/, rev1, rev2, recurse, !ignoreAncestry, fos);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public PropertyData[] properties(String path) throws ClientException {
        return properties(path, null, null);
    }

    public PropertyData[] properties(String path, Revision revision) throws ClientException {
        return properties(path, revision, null);
    }

    public PropertyData[] properties(String path, Revision revision, Revision pegRevision) throws ClientException {
        if(path == null){
            return null;
        }
        SVNWCClient client = createSVNWCClient();
        SVNRevision svnRevision = SVNConverterUtil.getSVNRevision(revision);
        SVNRevision svnPegRevision = SVNConverterUtil.getSVNRevision(pegRevision);
        final Collection properties = new ArrayList();
        try {
            client.doGetProperty(new File(path).getAbsoluteFile(), null, svnPegRevision, svnRevision, false, new ISVNPropertyHandler(){
                public void handleProperty(File fpath, SVNPropertyData property) throws SVNException {
                    properties.add(new PropertyData(SVNClient.this, fpath.getAbsolutePath(),
                            property.getName(), property.getValue(), property.getValue().getBytes()));
                }
                public void handleProperty(String url, SVNPropertyData property) throws SVNException {
                    properties.add(new PropertyData(SVNClient.this, url,
                            property.getName(), property.getValue(), property.getValue().getBytes()));
                }
            });
        } catch (SVNException e) {
            throwException(e);
        }
        return (PropertyData[]) properties.toArray(new PropertyData[properties.size()]);
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
        SVNWCClient client = createSVNWCClient();
        try {
            client.doSetProperty(new File(path).getAbsoluteFile(), name, value, force, recurse, new ISVNPropertyHandler(){
                public void handleProperty(File fpath, SVNPropertyData property) throws SVNException {}
                public void handleProperty(String url, SVNPropertyData property) throws SVNException {}
            });
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void propertyRemove(String path, String name, boolean recurse) throws ClientException {
        SVNWCClient client = createSVNWCClient();
        try {
            client.doSetProperty(new File(path).getAbsoluteFile(), name, null, false, recurse, new ISVNPropertyHandler(){
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
        SVNWCClient client = createSVNWCClient();
        try {
            client.doSetProperty(new File(path).getAbsoluteFile(), name, value, force, recurse, new ISVNPropertyHandler(){
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
        if(name == null || name.equals("")){
            return null;
        }
        SVNWCClient client = createSVNWCClient();
        SVNRevision svnRevision = SVNConverterUtil.getSVNRevision(rev);
        SVNPropertyRetriever retriever = new SVNPropertyRetriever(this);
        try {
            client.doGetRevisionProperty(path, name, svnRevision, retriever);
        } catch (SVNException e) {
            throwException(e);
        }
        return retriever.getPropertyData();
    }

    public PropertyData[] revProperties(String path, Revision rev) throws ClientException {
        if(path == null){
            return null;
        }
        SVNWCClient client = createSVNWCClient();
        SVNRevision svnRevision = SVNConverterUtil.getSVNRevision(rev);
        final Collection properties = new ArrayList();
        try {
            client.doGetRevisionProperty(path, null, svnRevision, new ISVNPropertyHandler(){
                public void handleProperty(File fpath, SVNPropertyData property) throws SVNException {
                    properties.add(new PropertyData(SVNClient.this, fpath.getAbsolutePath(),
                            property.getName(), property.getValue(), property.getValue().getBytes()));
                }
                public void handleProperty(String url, SVNPropertyData property) throws SVNException {
                    properties.add(new PropertyData(SVNClient.this, url,
                            property.getName(), property.getValue(), property.getValue().getBytes()));
                }
            });
        } catch (SVNException e) {
            throwException(e);
        }
        return (PropertyData[]) properties.toArray(new PropertyData[properties.size()]);
    }

    public void setRevProperty(String path, String name, Revision rev, String value, boolean force) throws ClientException {
        if(name == null || name.equals("")){
            return;
        }
        SVNWCClient client = createSVNWCClient();
        SVNRevision svnRevision = SVNConverterUtil.getSVNRevision(rev);
        try {
            client.doSetRevisionProperty(new File(path).getAbsoluteFile(),
                    svnRevision, name, value, force, new ISVNPropertyHandler(){
                        public void handleProperty(File fpath, SVNPropertyData property) throws SVNException {}
                        public void handleProperty(String url, SVNPropertyData property) throws SVNException {}
            });
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public PropertyData propertyGet(String path, String name) throws ClientException {
        return propertyGet(path, name, new Revision(RevisionKind.unspecified, true));
    }

    public PropertyData propertyGet(String path, String name, Revision revision) throws ClientException {
        return propertyGet(path, name, revision, null);
    }
    
    public PropertyData propertyGet(String path, String name, Revision revision, Revision pegRevision) throws ClientException {
        if(name == null || name.equals("")){
            return null;
        }
        SVNWCClient client = createSVNWCClient();
        SVNRevision svnRevision = SVNConverterUtil.getSVNRevision(revision);
        SVNRevision svnPegRevision = SVNConverterUtil.getSVNRevision(pegRevision);
        SVNPropertyRetriever retriever = new SVNPropertyRetriever(this);
        try {
            client.doGetProperty(path, name, svnPegRevision, svnRevision, false, retriever);
        } catch (SVNException e) {
            throwException(e);
        }
        return retriever.getPropertyData();
    }

    public byte[] fileContent(String path, Revision revision) throws ClientException {
        return fileContent(path, revision, null);
    }

    public byte[] fileContent(String path, Revision revision, Revision pegRevision) throws ClientException {
        SVNWCClient client = createSVNWCClient();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            client.doGetFileContents(new File(path).getAbsoluteFile(),
                    SVNConverterUtil.getSVNRevision(pegRevision),
                    SVNConverterUtil.getSVNRevision(revision),
                    true,
                    baos
                    );
            return baos.toByteArray();
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
    }

    public void relocate(String from, String to, String path, boolean recurse) throws ClientException {
        SVNUpdateClient client = createSVNUpdateClient();
        try {
            client.doRelocate(new File(path).getAbsoluteFile(), from, to, recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public byte[] blame(String path, Revision revisionStart, Revision revisionEnd) throws ClientException {
        SVNLogClient client = createSVNLogClient();
        try {
            client.doAnnotate(new File(path).getAbsoluteFile(),
                    SVNRevision.UNDEFINED,
                    SVNConverterUtil.getSVNRevision(revisionStart),
                    SVNConverterUtil.getSVNRevision(revisionEnd),
                    new ISVNAnnotateHandler(){
                        public void handleLine(Date date, long revision, String author, String line) {
                            // TODO
                        }
                    }
                    );
        } catch (SVNException e) {
            throwException(e);
        }
        return new byte[]{};
    }

    public void blame(String path, Revision revisionStart, Revision revisionEnd, BlameCallback callback) throws ClientException {
        blame(path, null, revisionStart, revisionEnd, callback);
    }

    public void blame(String path, Revision pegRevision, Revision revisionStart, Revision revisionEnd, final BlameCallback callback) throws ClientException {
        SVNLogClient client = createSVNLogClient();
        try {
            client.doAnnotate(new File(path).getAbsoluteFile(),
                    SVNConverterUtil.getSVNRevision(pegRevision),
                    SVNConverterUtil.getSVNRevision(revisionStart),
                    SVNConverterUtil.getSVNRevision(revisionEnd),
                    new ISVNAnnotateHandler(){
                        public void handleLine(Date date, long revision, String author, String line) {
                            if(callback!=null){
                                callback.singleLine(date, revision, author, line);
                            }
                        }
                    }
                    );
        } catch (SVNException e) {
            throwException(e);
        }
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
        SVNWCClient client = createSVNWCClient();
        try {
            return SVNConverterUtil.createInfo(client.doInfo(new File(path).getAbsoluteFile(), SVNRevision.UNDEFINED));
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
    }

    public void lock(String[] path, String comment, boolean force) throws ClientException {
        try {
            createSVNWCClient().doLock(path, force, comment);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void unlock(String[] path, boolean force) throws ClientException {
        try {
            createSVNWCClient().doUnlock(path, force);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public Info2[] info2(String pathOrUrl, Revision revision, Revision pegRevision, boolean recurse) throws ClientException {
        SVNWCClient client = createSVNWCClient();
        try {
            final Collection infos = new ArrayList(); 
            client.doInfo(pathOrUrl,
                    SVNConverterUtil.getSVNRevision(pegRevision),
                    SVNConverterUtil.getSVNRevision(revision),
                    recurse, new ISVNInfoHandler(){
                        public void handleInfo(SVNInfo info) {
                            infos.add(SVNConverterUtil.createInfo2(info));
                        }
            });
            return (Info2[]) infos.toArray(new Info2[infos.size()]);
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
    }

    public String getVersionInfo(String path, String trailUrl, boolean lastChanged) throws ClientException {
        // TODO Auto-generated method stub
        return null;
    }

    protected ISVNOptions getSVNOptions(){
        if (myOptions == null) {
            File dir = myConfigDir == null ? null : new File(myConfigDir);
            myOptions = new SVNOptions(dir, false, myUserName, myPassword);
        } else {
            myOptions.setDefaultAuthentication(myUserName, myPassword);
        }
        return myOptions;
    }
    
    protected ISVNEventListener getEventListener(){
        if(mySVNEventListener == null){
            mySVNEventListener = new ISVNEventListener(){
                public void svnEvent(SVNEvent event, double progress) {
                    if(myNotify != null){
                        myNotify.onNotify(
                                event.getPath(),
                                SVNConverterUtil.getNotifyActionValue(event.getAction()),
                                SVNConverterUtil.getNodeKind(event.getNodeKind()), 
                                event.getMimeType(),
                                SVNConverterUtil.getStatusValue(event.getContentsStatus()),
                                SVNConverterUtil.getStatusValue(event.getPropertiesStatus()),
                                event.getRevision()
                                );
                    }
                    if(myNotify2 != null){
                        NotifyInformation info = new NotifyInformation(
                                event.getFile() == null ? event.getPath() : event.getFile().getAbsolutePath(),
                                SVNConverterUtil.getNotifyActionValue(event.getAction()),
                                SVNConverterUtil.getNodeKind(event.getNodeKind()), 
                                event.getMimeType(),
                                SVNConverterUtil.createLock(event.getLock()),
                                event.getErrorMessage(),
                                SVNConverterUtil.getStatusValue(event.getContentsStatus()),
                                SVNConverterUtil.getStatusValue(event.getPropertiesStatus()),
                                SVNConverterUtil.getStatusValue(event.getLockStatus()),
                                event.getRevision()
                                );
                        myNotify2.onNotify(info);
                    }
                }
                public void checkCancelled() throws SVNCancelException {
                }
            };
        }
        return mySVNEventListener;
    }
    
    protected SVNCommitClient createSVNCommitClient(){
        return new SVNCommitClient(getSVNOptions(), getEventListener());
    }
    
    protected SVNUpdateClient createSVNUpdateClient(){
        return new SVNUpdateClient(getSVNOptions(), getEventListener());
    }
    
    protected SVNStatusClient createSVNStatusClient(){
        return new SVNStatusClient(getSVNOptions(), getEventListener());
    }
    
    protected SVNWCClient createSVNWCClient(){
        return new SVNWCClient(getSVNOptions(), getEventListener());
    }
    
    protected SVNDiffClient createSVNDiffClient(){
        return new SVNDiffClient(getSVNOptions(), getEventListener());
    }
    
    protected SVNCopyClient createSVNCopyClient(){
        return new SVNCopyClient(getSVNOptions(), getEventListener());
    }
    
    protected SVNLogClient createSVNLogClient(){
        return new SVNLogClient(getSVNOptions(), getEventListener());
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
