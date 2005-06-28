/*
 * Created on 16.06.2005
 */
package org.tigris.subversion.javahl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.TreeSet;

import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNJSchSession;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.io.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.io.SVNDirEntry;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNLogEntry;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
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
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNUtil;
import org.tmatesoft.svn.util.Version;

/**
 * @author evgeny
 */
public class SVNClient implements SVNClientInterface {

    private String myConfigDir;
    private PromptUserPassword myPrompt;
    private String myUserName;
    private String myPassword;
    private ISVNEventHandler mySVNEventListener;
    private Notify myNotify;
    private Notify2 myNotify2;
    private CommitMessage myMessageHandler;
    private ISVNOptions myOptions;
    private boolean myCancelOperation = false;
    private SVNCommitClient mySVNCommitClient;
    private SVNUpdateClient mySVNUpdateClient;
    private SVNStatusClient mySVNStatusClient;
    private SVNWCClient mySVNWCClient;
    private SVNDiffClient mySVNDiffClient;
    private SVNCopyClient mySVNCopyClient;
    private SVNLogClient mySVNLogClient;

    private static Map ourCredentialsCache = new Hashtable();

    public static final class LogLevel implements SVNClientLogLevel {

    }

    public SVNClient() {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
    }

    public String getLastPath() {
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
            return null;
        }
        final Collection statuses = new ArrayList();
        SVNStatusClient stClient = getSVNStatusClient();
        try {
            stClient.doStatus(new File(path).getAbsoluteFile(), descend, onServer, getAll, noIgnore, !ignoreExternals, new ISVNStatusHandler(){
                public void handleStatus(SVNStatus status) {
                    statuses.add(SVNConverterUtil.createStatus(status.getFile().getPath(), status));
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
        SVNLogClient client = getSVNLogClient();
        DebugLog.log("LIST is called for " + url);
        try {
            if(isURL(url)){
                client.doList(url, SVNConverterUtil.getSVNRevision(pegRevision),
                        SVNConverterUtil.getSVNRevision(revision), recurse, new ISVNDirEntryHandler(){
                    public void handleDirEntry(SVNDirEntry dirEntry) {
                        allEntries.add(SVNConverterUtil.createDirEntry(dirEntry));
                    }
                });
            }else{
                client.doList(new File(url).getAbsoluteFile(), SVNConverterUtil.getSVNRevision(pegRevision),
                        SVNConverterUtil.getSVNRevision(revision), recurse, new ISVNDirEntryHandler(){
                    public void handleDirEntry(SVNDirEntry dirEntry) {
                        allEntries.add(SVNConverterUtil.createDirEntry(dirEntry));
                    }
                });
            }
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

        SVNStatusClient client = getSVNStatusClient();
        SVNStatus status = null;
        try {
            status = client.doStatus(new File(path).getAbsoluteFile(), onServer);
        } catch (SVNException e) {
            throwException(e);
        }
        return SVNConverterUtil.createStatus(path, status);
    }

    public void username(String username) {
        myUserName = username;
        getSVNOptions().setDefaultAuthentication(myUserName, myPassword);
    }

    public void password(String password) {
        myPassword = password;
        getSVNOptions().setDefaultAuthentication(myUserName, myPassword);
    }

    public void setPrompt(PromptUserPassword prompt) {
        DebugLog.log("prompt set: " + prompt);
        myPrompt = prompt;
        getSVNOptions().setAuthenticationProvider(
                myPrompt != null ? new PromptAuthenticationProvider(myPrompt) : null);
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
        SVNLogClient client = getSVNLogClient();
        final Collection entries = new ArrayList();
        try {
            if(isURL(path)){
                client.doLog(
                        path, new String[]{""},
                        SVNConverterUtil.getSVNRevision(revisionStart),
                        SVNConverterUtil.getSVNRevision(revisionEnd),
                        stopOnCopy, discoverPath, limit, new ISVNLogEntryHandler(){
                            public void handleLogEntry(SVNLogEntry logEntry) {
                                entries.add(SVNConverterUtil.createLogMessage(logEntry));
                            }
                        }
                        );
            }else{
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
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return (LogMessage[]) entries.toArray(new LogMessage[entries.size()]);
    }

    public long checkout(String moduleName, String destPath, Revision revision, Revision pegRevision, boolean recurse, boolean ignoreExternals) throws ClientException {
        try {
            File path = new File(destPath).getAbsoluteFile();

            SVNUpdateClient updater = getSVNUpdateClient();
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
        boolean areURLs = false;
        for (int i = 0; i < path.length; i++) {
            areURLs = areURLs || isURL(path[i]);
        }
        if(areURLs){
            SVNCommitClient client = getSVNCommitClient();
            try {
                client.doDelete(path, message);
            } catch (SVNException e) {
                throwException(e);
            }
        }else{
            SVNWCClient client = getSVNWCClient();
            for (int i = 0; i < path.length; i++) {
                try {
                    client.doDelete(new File(path[i]).getAbsoluteFile(), force, false);
                } catch (SVNException e) {
                    throwException(e);
                }
            }
        }
    }

    public void revert(String path, boolean recurse) throws ClientException {
        SVNWCClient client = getSVNWCClient();
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
        SVNWCClient wcClient = getSVNWCClient();
        try {
            wcClient.doAdd(new File(path).getAbsoluteFile(), force, true, false, recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public long update(String path, Revision revision, boolean recurse) throws ClientException {
        SVNUpdateClient client = getSVNUpdateClient();
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
        return commit(path, message, recurse, false);
    }

    public long commit(String[] path, String message, boolean recurse, boolean noUnlock) throws ClientException {
        if(path == null || path.length == 0){
            return 0;
        }
        SVNCommitClient client = getSVNCommitClient();
        File[] files = new File[path.length];
        for (int i = 0; i < path.length; i++) {
            files[i] = new File(path[i]).getAbsoluteFile();
        }
        try {
            if(myMessageHandler != null){
                client.setCommitHander(new ISVNCommitHandler(){
                    public String getCommitMessage(String cmessage, SVNCommitItem[] commitables) {
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
        SVNCopyClient client = getSVNCopyClient();
        try {
            if(isURL(srcPath)){
                SVNRevision srcRevision = revision == null || revision.getKind() == Revision.Kind.unspecified ?
                        SVNRevision.HEAD : SVNConverterUtil.getSVNRevision(revision);
                if(isURL(destPath)){
                    // url->url copy
                    client.doCopy(srcPath, SVNRevision.UNDEFINED, srcRevision,
                            destPath, SVNRevision.UNDEFINED, false, message);
                }else{
                    // url->wc copy
                    client.doCopy(srcPath, SVNRevision.UNDEFINED, srcRevision,
                            new File(destPath), SVNRevision.UNDEFINED, SVNRevision.WORKING, false, null);
                }
            }else{
                SVNRevision srcRevision = revision == null || revision.getKind() == Revision.Kind.unspecified ?
                        SVNRevision.WORKING : SVNConverterUtil.getSVNRevision(revision);
                if(isURL(destPath)){
                    // wc->url copy
                    client.doCopy(new File(srcPath).getAbsoluteFile(), SVNRevision.UNDEFINED, srcRevision,
                            destPath, SVNRevision.UNDEFINED, false, message);
                }else{
                    // wc->wc copy 
                    client.doCopy(new File(srcPath).getAbsoluteFile(), SVNRevision.UNDEFINED, srcRevision,
                            new File(destPath), SVNRevision.UNDEFINED, SVNRevision.WORKING, false, false, null);
                }
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void move(String srcPath, String destPath, String message, Revision revision, boolean force) throws ClientException {
        SVNCopyClient updater = getSVNCopyClient();
        try {
            if(isURL(srcPath)){
                SVNRevision srcRevision = revision == null || revision.getKind() == Revision.Kind.unspecified ?
                        SVNRevision.HEAD : SVNConverterUtil.getSVNRevision(revision);
                if(isURL(destPath)){
                    // url->url move.
                    updater.doCopy(srcPath, SVNRevision.UNDEFINED, srcRevision,
                            destPath, SVNRevision.UNDEFINED, true, message);
                }else{
                    // url->wc move (not supported).
                    updater.doCopy(srcPath, SVNRevision.UNDEFINED,
                            srcRevision,
                            new File(destPath).getAbsoluteFile(), SVNRevision.UNDEFINED,
                            SVNRevision.WORKING,
                            true, message);
                }
            }else{
                SVNRevision srcRevision = SVNRevision.WORKING;
                if(isURL(destPath)){
                    // wc->url move(?), not supported
                    updater.doCopy(new File(srcPath).getAbsoluteFile(), SVNRevision.UNDEFINED,
                            srcRevision,
                            destPath, SVNRevision.UNDEFINED,
                            true, message);
                }else{
                    // working->working only
                    updater.doCopy(new File(srcPath), SVNRevision.UNDEFINED, srcRevision,
                            new File(destPath), SVNRevision.UNDEFINED, SVNRevision.WORKING, force, true, message);
                }
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void move(String srcPath, String destPath, String message, boolean force) throws ClientException {
        move(srcPath, destPath, message, Revision.WORKING, force);
    }

    public void mkdir(String[] path, String message) throws ClientException {
        SVNCommitClient client = getSVNCommitClient();
        try {
            client.doMkDir(path, message);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void cleanup(String path) throws ClientException {
        SVNWCClient client = getSVNWCClient();
        try {
            client.doCleanup(new File(path).getAbsoluteFile());
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void resolved(String path, boolean recurse) throws ClientException {
        SVNWCClient client = getSVNWCClient();
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
        SVNUpdateClient updater = getSVNUpdateClient();
        try {
            if(isURL(srcPath)){
                return updater.doExport(srcPath, new File(destPath).getAbsoluteFile(),
                        SVNConverterUtil.getSVNRevision(revision), SVNConverterUtil.getSVNRevision(pegRevision), nativeEOL, force, recurse);
            }
            return updater.doExport(new File(srcPath).getAbsoluteFile(), new File(destPath).getAbsoluteFile(),
                    SVNConverterUtil.getSVNRevision(revision), SVNConverterUtil.getSVNRevision(pegRevision), nativeEOL, force, recurse);
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    public long doSwitch(String path, String url, Revision revision, boolean recurse) throws ClientException {
        SVNUpdateClient updater = getSVNUpdateClient();
        try {
            return updater.doSwitch(new File(path).getAbsoluteFile(), url, SVNConverterUtil.getSVNRevision(revision), recurse);
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    public void doImport(String path, String url, String message, boolean recurse) throws ClientException {
        SVNCommitClient commitClient = getSVNCommitClient();
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
        SVNDiffClient differ = getSVNDiffClient();
        try {
            if(isURL(path1) && isURL(path2)){
                differ.doMerge(path1, path2, SVNConverterUtil.getSVNRevision(revision1),
                        SVNConverterUtil.getSVNRevision(revision2), new File(localPath).getAbsoluteFile(),
                        recurse, !ignoreAncestry, force, dryRun);
            }else{
                differ.doMerge(new File(path1).getAbsoluteFile(), new File(path2).getAbsoluteFile(),
                        SVNConverterUtil.getSVNRevision(revision1), SVNConverterUtil.getSVNRevision(revision2),
                        new File(localPath).getAbsoluteFile(), recurse, !ignoreAncestry, force, dryRun);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void merge(String path, Revision pegRevision, Revision revision1, Revision revision2, String localPath, boolean force, boolean recurse, boolean ignoreAncestry, boolean dryRun) throws ClientException {
        SVNDiffClient differ = getSVNDiffClient();
        try {
            if(isURL(path)){
                differ.doMerge(path, SVNConverterUtil.getSVNRevision(pegRevision),
                        SVNConverterUtil.getSVNRevision(revision1),
                        SVNConverterUtil.getSVNRevision(revision2),
                        new File(localPath).getAbsoluteFile(), recurse, !ignoreAncestry, force, dryRun);
            }else{
                differ.doMerge(new File(path).getAbsoluteFile(),
                        SVNConverterUtil.getSVNRevision(pegRevision),
                        SVNConverterUtil.getSVNRevision(revision1),
                        SVNConverterUtil.getSVNRevision(revision2),
                        new File(localPath).getAbsoluteFile(), recurse, !ignoreAncestry, force, dryRun);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void diff(String target1, Revision revision1, String target2, Revision revision2, String outFileName, boolean recurse) throws ClientException {
        diff(target1, revision1, target2, revision2, outFileName, recurse, false, false, false);
    }

    public void diff(String target1, Revision revision1, String target2, Revision revision2, String outFileName, boolean recurse, boolean ignoreAncestry, boolean noDiffDeleted, boolean force) throws ClientException {
        SVNDiffClient differ = getSVNDiffClient();
        differ.setDiffGenerator(new DefaultSVNDiffGenerator() {
            public String getDisplayPath(File file) {
                return SVNUtil.getPath(file).replace(File.separatorChar, '/');
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
        SVNRevision peg = SVNRevision.UNDEFINED;
        SVNRevision rev1 = SVNConverterUtil.getSVNRevision(revision1);
        SVNRevision rev2 = SVNConverterUtil.getSVNRevision(revision2);
        try {
            OutputStream out = SVNFileUtil.openFileForWriting(new File(outFileName));
            if(!isURL(target1)&&!isURL(target2)){
                differ.doDiff(new File(target1).getAbsoluteFile(),
                        new File(target2).getAbsoluteFile(),
                        rev1, rev2, recurse, !ignoreAncestry, out);
            }else if(isURL(target1)&&isURL(target2)){
                differ.doDiff(target1, peg1, target2, peg2, rev1, rev2, recurse, !ignoreAncestry, out);
            }else if(!isURL(target1)&&isURL(target2)){
                differ.doDiff(new File(target1).getAbsoluteFile(), target2,
                        peg, rev1, rev2, recurse, !ignoreAncestry, out);
            }else if(isURL(target1)&&!isURL(target2)){
                differ.doDiff(target1, peg, new File(target2), rev1, rev2, recurse, !ignoreAncestry, out);
            }
            SVNFileUtil.closeFile(out);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void diff(String target, Revision pegRevision, Revision startRevision, Revision endRevision, String outFileName, boolean recurse, boolean ignoreAncestry, boolean noDiffDeleted, boolean force) throws ClientException {
        SVNDiffClient differ = getSVNDiffClient();
        differ.setDiffGenerator(new DefaultSVNDiffGenerator() {
            public String getDisplayPath(File file) {
                return SVNUtil.getPath(file).replace(File.separatorChar, '/');
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
        SVNRevision peg = SVNConverterUtil.getSVNRevision(pegRevision);
        SVNRevision rev1 = SVNConverterUtil.getSVNRevision(startRevision);
        SVNRevision rev2 = SVNConverterUtil.getSVNRevision(endRevision);
        try {
            OutputStream out = SVNFileUtil.openFileForWriting(new File(outFileName));
            if(isURL(target)){
                differ.doDiff(target, peg, target, peg, rev1, rev2, recurse, !ignoreAncestry, out);
            }else{
                differ.doDiff(new File(target).getAbsoluteFile(), new File(target).getAbsoluteFile(),
                        rev1, rev2, recurse, !ignoreAncestry, out);
            }
            SVNFileUtil.closeFile(out);
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
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = SVNConverterUtil.getSVNRevision(revision);
        SVNRevision svnPegRevision = SVNConverterUtil.getSVNRevision(pegRevision);
        final Collection properties = new ArrayList();
        ISVNPropertyHandler propHandler = new ISVNPropertyHandler(){
            public void handleProperty(File fpath, SVNPropertyData property) {
                properties.add(new PropertyData(SVNClient.this, fpath.getAbsolutePath(),
                        property.getName(), property.getValue(), property.getValue().getBytes()));
            }
            public void handleProperty(String url, SVNPropertyData property) {
                properties.add(new PropertyData(SVNClient.this, url,
                        property.getName(), property.getValue(), property.getValue().getBytes()));
            }
        };
        try {
            if(isURL(path)){
                client.doGetProperty(path, null, svnPegRevision, svnRevision, false, propHandler);
            }else{
                client.doGetProperty(new File(path).getAbsoluteFile(), null, svnPegRevision, svnRevision, false, propHandler);
            }
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
        SVNWCClient client = getSVNWCClient();
        try {
            client.doSetProperty(new File(path).getAbsoluteFile(), name, value, force, recurse, ISVNPropertyHandler.NULL);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void propertyRemove(String path, String name, boolean recurse) throws ClientException {
        SVNWCClient client = getSVNWCClient();
        try {
            client.doSetProperty(new File(path).getAbsoluteFile(), name, null, false, recurse, ISVNPropertyHandler.NULL);
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
        SVNWCClient client = getSVNWCClient();
        try {
            client.doSetProperty(new File(path).getAbsoluteFile(), name, value, force, recurse, ISVNPropertyHandler.NULL);
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
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = SVNConverterUtil.getSVNRevision(rev);
        SVNRevision svnPegRevision = SVNConverterUtil.getSVNRevision(null);
        SVNPropertyRetriever retriever = new SVNPropertyRetriever(this);
        try {
            if(isURL(path)){
                client.doGetRevisionProperty(path, name, svnRevision, retriever);
            }else{
                client.doGetRevisionProperty(new File(path).getAbsoluteFile(), name,
                        svnPegRevision, svnRevision, retriever);
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return retriever.getPropertyData();
    }

    public PropertyData[] revProperties(String path, Revision rev) throws ClientException {
        if(path == null){
            return null;
        }
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = SVNConverterUtil.getSVNRevision(rev);
        SVNRevision svnPegRevision = SVNConverterUtil.getSVNRevision(null);
        final Collection properties = new ArrayList();
        ISVNPropertyHandler propHandler = new ISVNPropertyHandler(){
            public void handleProperty(File fpath, SVNPropertyData property) {
                properties.add(new PropertyData(SVNClient.this, fpath.getAbsolutePath(),
                        property.getName(), property.getValue(), property.getValue().getBytes()));
            }
            public void handleProperty(String url, SVNPropertyData property) {
                properties.add(new PropertyData(SVNClient.this, url,
                        property.getName(), property.getValue(), property.getValue().getBytes()));
            }
        };
        try {
            if(isURL(path)){
                client.doGetRevisionProperty(path, null, svnRevision, propHandler);
            }else{
                client.doGetRevisionProperty(new File(path).getAbsoluteFile(), null,
                        svnPegRevision, svnRevision, propHandler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return (PropertyData[]) properties.toArray(new PropertyData[properties.size()]);
    }

    public void setRevProperty(String path, String name, Revision rev, String value, boolean force) throws ClientException {
        if(name == null || name.equals("")){
            return;
        }
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = SVNConverterUtil.getSVNRevision(rev);
        SVNRevision svnPegRevision = SVNConverterUtil.getSVNRevision(null);
        try {
            if(isURL(path)){
                client.doSetRevisionProperty(path,
                        svnPegRevision, svnRevision, name, value, force, ISVNPropertyHandler.NULL);
            }else{
                client.doSetRevisionProperty(new File(path).getAbsoluteFile(),
                        svnRevision, name, value, force, ISVNPropertyHandler.NULL);
            }
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
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = SVNConverterUtil.getSVNRevision(revision);
        SVNRevision svnPegRevision = SVNConverterUtil.getSVNRevision(pegRevision);
        SVNPropertyRetriever retriever = new SVNPropertyRetriever(this);
        try {
            if(isURL(path)){
                client.doGetProperty(path, name, svnPegRevision, svnRevision, false, retriever);
            }else{
                client.doGetProperty(new File(path).getAbsoluteFile(), name, svnPegRevision, svnRevision, false, retriever);
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return retriever.getPropertyData();
    }

    public byte[] fileContent(String path, Revision revision) throws ClientException {
        return fileContent(path, revision, null);
    }

    public byte[] fileContent(String path, Revision revision, Revision pegRevision) throws ClientException {
        SVNWCClient client = getSVNWCClient();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if(isURL(path)){
                client.doGetFileContents(path,
                        SVNConverterUtil.getSVNRevision(pegRevision),
                        SVNConverterUtil.getSVNRevision(revision), true, baos);
            }else{
                client.doGetFileContents(new File(path).getAbsoluteFile(),
                        SVNConverterUtil.getSVNRevision(pegRevision),
                        SVNConverterUtil.getSVNRevision(revision), true, baos);
            }
            return baos.toByteArray();
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
    }

    public void relocate(String from, String to, String path, boolean recurse) throws ClientException {
        SVNUpdateClient client = getSVNUpdateClient();
        try {
            client.doRelocate(new File(path).getAbsoluteFile(), from, to, recurse);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public byte[] blame(String path, Revision revisionStart, Revision revisionEnd) throws ClientException {
        SVNLogClient client = getSVNLogClient();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ISVNAnnotateHandler handler = new ISVNAnnotateHandler(){
            public void handleLine(Date date, long revision, String author, String line) {
                StringBuffer result = new StringBuffer();
                result.append(Long.toString(revision));
                result.append(author != null ? SVNUtil.formatString(author, 10, false) : "         -");
                result.append(' ');
                result.append(line);
                try {
                    baos.write(result.toString().getBytes());
                    baos.write('\n');
                } catch (IOException e) {
                }
            }
        };
        try {
            if(isURL(path)){
                client.doAnnotate(path,
                        SVNRevision.UNDEFINED,
                        SVNConverterUtil.getSVNRevision(revisionStart),
                        SVNConverterUtil.getSVNRevision(revisionEnd),
                        handler);
            }else{
                client.doAnnotate(new File(path).getAbsoluteFile(),
                        SVNRevision.UNDEFINED,
                        SVNConverterUtil.getSVNRevision(revisionStart),
                        SVNConverterUtil.getSVNRevision(revisionEnd),
                        handler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return new byte[]{};
    }

    public void blame(String path, Revision revisionStart, Revision revisionEnd, BlameCallback callback) throws ClientException {
        blame(path, null, revisionStart, revisionEnd, callback);
    }

    public void blame(String path, Revision pegRevision, Revision revisionStart, Revision revisionEnd, final BlameCallback callback) throws ClientException {
        SVNLogClient client = getSVNLogClient();
        ISVNAnnotateHandler handler = new ISVNAnnotateHandler(){
            public void handleLine(Date date, long revision, String author, String line) {
                if(callback!=null){
                    callback.singleLine(date, revision, author, line);
                }
            }
        };
        try {
            if(isURL(path)){
                client.doAnnotate(path,
                        SVNConverterUtil.getSVNRevision(pegRevision),
                        SVNConverterUtil.getSVNRevision(revisionStart),
                        SVNConverterUtil.getSVNRevision(revisionEnd),
                        handler);
            }else{
                client.doAnnotate(new File(path).getAbsoluteFile(),
                        SVNConverterUtil.getSVNRevision(pegRevision),
                        SVNConverterUtil.getSVNRevision(revisionStart),
                        SVNConverterUtil.getSVNRevision(revisionEnd),
                        handler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void dispose() {
        SVNJSchSession.shutdown();
    }

    public void setConfigDirectory(String configDir) throws ClientException {
        myConfigDir = configDir;
        DebugLog.log("config directory set: " + configDir);
        myOptions = null;
        mySVNCommitClient = null;
        mySVNUpdateClient = null;
        mySVNStatusClient = null;
        mySVNWCClient = null;
        mySVNDiffClient = null;
        mySVNCopyClient = null;
        mySVNLogClient = null;
    }

    public String getConfigDirectory() throws ClientException {
        return myConfigDir;
    }

    public void cancelOperation() throws ClientException {
        myCancelOperation = true;
    }

    public Info info(String path) throws ClientException {
        SVNWCClient client = getSVNWCClient();
        try {
            if(isURL(path)){
                return SVNConverterUtil.createInfo(client.doInfo(path, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED));
            }
            return SVNConverterUtil.createInfo(client.doInfo(new File(path).getAbsoluteFile(), SVNRevision.UNDEFINED));
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
    }

    public void lock(String[] path, String comment, boolean force) throws ClientException {
        boolean allFiles = true;
        for (int i = 0; i < path.length; i++) {
            allFiles = allFiles && !isURL(path[i]);
        }
        try {
            if(allFiles){
                File[] files = new File[path.length];
                for (int i = 0; i < files.length; i++) {
                    files[i] = new File(path[i]).getAbsoluteFile();
                }
                getSVNWCClient().doLock(files, force, comment);
            }else{
                getSVNWCClient().doLock(path, force, comment);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void unlock(String[] path, boolean force) throws ClientException {
        boolean allFiles = true;
        for (int i = 0; i < path.length; i++) {
            allFiles = allFiles && !isURL(path[i]);
        }
        try {
            if(allFiles){
                File[] files = new File[path.length];
                for (int i = 0; i < files.length; i++) {
                    files[i] = new File(path[i]).getAbsoluteFile();
                }
                getSVNWCClient().doUnlock(files, force);
            }else{
                getSVNWCClient().doUnlock(path, force);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public Info2[] info2(String pathOrUrl, Revision revision, Revision pegRevision, boolean recurse) throws ClientException {
        SVNWCClient client = getSVNWCClient();
        final Collection infos = new ArrayList();
        ISVNInfoHandler handler = new ISVNInfoHandler(){
            public void handleInfo(SVNInfo info) {
                infos.add(SVNConverterUtil.createInfo2(info));
            }
        };
        try {
            if(isURL(pathOrUrl)){
                client.doInfo(pathOrUrl,
                        SVNConverterUtil.getSVNRevision(pegRevision),
                        SVNConverterUtil.getSVNRevision(revision),
                        recurse, handler);
            }else{
                client.doInfo(new File(pathOrUrl).getAbsoluteFile(),
                        SVNConverterUtil.getSVNRevision(revision),
                        recurse, handler);
            }
            return (Info2[]) infos.toArray(new Info2[infos.size()]);
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
    }

    public String getVersionInfo(String path, String trailUrl, boolean lastChanged) throws ClientException {
        return getSVNWCClient().doGetWorkingCopyID(new File(path).getAbsoluteFile(), trailUrl, lastChanged);
    }

    public static void enableLogging(int logLevel, String logFilePath) {

    }

    public static String version() {
        return Version.getVersionString();
    }

    public static int versionMajor() {
        return Version.getMajorVersion();
    }

    public static int versionMinor() {
        return Version.getMinorVersion();
    }

    public static int versionMicro() {
        return Version.getMicroVersion();
    }

    protected ISVNOptions getSVNOptions(){
        if (myOptions == null) {
            File dir = myConfigDir == null ? null : new File(myConfigDir);
            myOptions = SVNWCUtil.createDefaultOptions(dir, true);
            if (myUserName != null && myPassword != null) {
                myOptions.setDefaultAuthentication(myUserName, myPassword);
            }
            if(myPrompt != null){
                myOptions.setAuthenticationProvider(new PromptAuthenticationProvider(myPrompt));
            }
            myOptions.setRuntimeAuthenticationCache(ourCredentialsCache);
        }
        return myOptions;
    }

    protected Notify getNotify() {
        return myNotify;
    }

    protected Notify2 getNotify2() {
        return myNotify2;
    }

    protected ISVNEventHandler getEventListener(){
        if(mySVNEventListener == null){
            mySVNEventListener = new ISVNEventHandler(){

                public void handleEvent(SVNEvent event, double progress) {
                    String path = event.getFile() == null ? event.getPath() : event.getFile().getAbsolutePath();
                    if(myNotify != null){
                        myNotify.onNotify(
                                path,
                                SVNConverterUtil.getNotifyActionValue(event.getAction()),
                                SVNConverterUtil.getNodeKind(event.getNodeKind()),
                                event.getMimeType(),
                                SVNConverterUtil.getStatusValue(event.getContentsStatus()),
                                SVNConverterUtil.getStatusValue(event.getPropertiesStatus()),
                                event.getRevision()
                                );
                    }
                    if(myNotify2 != null){
                        NotifyInformation info = createNotifyInformation(event, path);
                        myNotify2.onNotify(info);
                    }
                }

                public void checkCancelled() throws SVNCancelException {
                    if(myCancelOperation){
                        myCancelOperation = false;
                        throw new SVNCancelException();
                    }
                }
            };
        }
        return mySVNEventListener;
    }

    protected SVNCommitClient getSVNCommitClient(){
        if(mySVNCommitClient == null){
            mySVNCommitClient = new SVNCommitClient(getSVNOptions(), getEventListener());
        }
        return mySVNCommitClient;
    }

    protected SVNUpdateClient getSVNUpdateClient(){
        if(mySVNUpdateClient == null){
            mySVNUpdateClient = new SVNUpdateClient(getSVNOptions(), getEventListener());
        }
        return mySVNUpdateClient;
    }

    protected SVNStatusClient getSVNStatusClient(){
        if(mySVNStatusClient == null){
            mySVNStatusClient = new SVNStatusClient(getSVNOptions(), getEventListener());
        }
        return mySVNStatusClient;
    }

    protected SVNWCClient getSVNWCClient(){
        if(mySVNWCClient == null){
            mySVNWCClient = new SVNWCClient(getSVNOptions(), getEventListener());
        }
        return mySVNWCClient;
    }

    protected SVNDiffClient getSVNDiffClient(){
        if(mySVNDiffClient == null){
            mySVNDiffClient = new SVNDiffClient(getSVNOptions(), getEventListener());
        }
        return mySVNDiffClient;
    }

    protected SVNCopyClient getSVNCopyClient(){
        if(mySVNCopyClient == null){
            mySVNCopyClient = new SVNCopyClient(getSVNOptions(), getEventListener());
        }
        return mySVNCopyClient;
    }

    protected SVNLogClient getSVNLogClient(){
        if(mySVNLogClient == null){
            mySVNLogClient = new SVNLogClient(getSVNOptions(), getEventListener());
        }
        return mySVNLogClient;
    }

    protected CommitMessage getCommitMessage() {
        return myMessageHandler;
    }

    protected static void throwException(SVNException e) throws ClientException {
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

    protected static boolean isURL(String path){
        return PathUtil.isURL(path);
    }

    protected static NotifyInformation createNotifyInformation(SVNEvent event, String path) {
        return new NotifyInformation(
                path,
                SVNConverterUtil.getNotifyActionValue(event.getAction()),
                SVNConverterUtil.getNodeKind(event.getNodeKind()),
                event.getMimeType(),
                SVNConverterUtil.createLock(event.getLock()),
                event.getErrorMessage(),
                SVNConverterUtil.getStatusValue(event.getContentsStatus()),
                SVNConverterUtil.getStatusValue(event.getPropertiesStatus()),
                SVNConverterUtil.getLockStatusValue(event.getLockStatus()),
                event.getRevision()
                );
    }
}
