/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.javahl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.tigris.subversion.javahl.BlameCallback;
import org.tigris.subversion.javahl.ClientException;
import org.tigris.subversion.javahl.CommitItem;
import org.tigris.subversion.javahl.CommitMessage;
import org.tigris.subversion.javahl.DirEntry;
import org.tigris.subversion.javahl.Info;
import org.tigris.subversion.javahl.Info2;
import org.tigris.subversion.javahl.JavaHLObjectFactory;
import org.tigris.subversion.javahl.LogMessage;
import org.tigris.subversion.javahl.Notify;
import org.tigris.subversion.javahl.Notify2;
import org.tigris.subversion.javahl.NotifyInformation;
import org.tigris.subversion.javahl.PromptUserPassword;
import org.tigris.subversion.javahl.PropertyData;
import org.tigris.subversion.javahl.Revision;
import org.tigris.subversion.javahl.SVNClient;
import org.tigris.subversion.javahl.SVNClientInterface;
import org.tigris.subversion.javahl.SVNClientLogLevel;
import org.tigris.subversion.javahl.Status;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationStorage;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnectionFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.ISVNConnectorFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNGanymedSession;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.DefaultSVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNCommitPacket;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.Version;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNClientImpl implements SVNClientInterface {

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
    private SVNClientManager myClientManager;
    private SVNClientInterface myOwner;
    
    private ISVNAuthenticationManager myAuthenticationManager;
    private static final ISVNAuthenticationStorage ourAuthStorage = new JavaHLAuthenticationStorage();

    public static final class LogLevel implements SVNClientLogLevel {

    }

    public static SVNClientImpl newInstance() {
        return newInstance(null);
    }
    
    public static SVNClientImpl newInstance(SVNClient owner) {
        return newInstance(owner, null, null);
    }

    public static SVNClientImpl newInstance(SVNClient owner, 
            IHTTPConnectionFactory httpConnectionFactory, ISVNConnectorFactory svnConnectorFactory) {
        return new SVNClientImpl(owner, httpConnectionFactory, svnConnectorFactory);
    }

    protected SVNClientImpl(SVNClient owner) {
        this(owner, null, null);
    }
    
    protected SVNClientImpl(SVNClient owner, IHTTPConnectionFactory httpConnectionFactory,
                ISVNConnectorFactory svnConnectorFactory) { 
        DAVRepositoryFactory.setup(httpConnectionFactory);
        SVNRepositoryFactoryImpl.setup(svnConnectorFactory);
        FSRepositoryFactory.setup();
        myConfigDir = SVNWCUtil.getDefaultConfigurationDirectory().getAbsolutePath();
        myOwner = owner == null ? (SVNClientInterface) this : (SVNClientInterface) owner;
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
        boolean oldIgnoreExternals = stClient.isIgnoreExternals();
        stClient.setIgnoreExternals(ignoreExternals);
        try {
            stClient.doStatus(new File(path).getAbsoluteFile(), descend, onServer, getAll, noIgnore, !ignoreExternals, new ISVNStatusHandler(){
                public void handleStatus(SVNStatus status) {
                    statuses.add(JavaHLObjectFactory.createStatus(status.getFile().getPath(), status));
                }
            });
        } catch (SVNException e) {
            throwException(e);
        } finally {
            stClient.setIgnoreExternals(oldIgnoreExternals);
        }
        return (Status[]) statuses.toArray(new Status[statuses.size()]);
    }

    public DirEntry[] list(String url, Revision revision, boolean recurse) throws ClientException {
        return list(url, revision, null, recurse);
    }

    public DirEntry[] list(String url, Revision revision, Revision pegRevision, boolean recurse) throws ClientException {
        final Collection allEntries = new ArrayList();
        SVNLogClient client = getSVNLogClient();
        ISVNDirEntryHandler handler = new ISVNDirEntryHandler(){
            public void handleDirEntry(SVNDirEntry dirEntry) {
                allEntries.add(JavaHLObjectFactory.createDirEntry(dirEntry));
            }
        };
        try {
            if(isURL(url)){
                client.doList(SVNURL.parseURIEncoded(url), JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision), recurse, handler); 
            } else {
                client.doList(new File(url).getAbsoluteFile(), JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision), recurse, handler);
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
        SVNStatusClient client = getSVNStatusClient();
        SVNStatus status = null;
        try {
            status = client.doStatus(new File(path).getAbsoluteFile(), onServer);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                File file = new File(path).getAbsoluteFile();
                SVNFileType ft = SVNFileType.getType(file);
                status = new SVNStatus(null, file, ft == SVNFileType.NONE ? SVNNodeKind.NONE : SVNNodeKind.UNKNOWN, null, null, null, null,
                        SVNStatusType.STATUS_UNVERSIONED, SVNStatusType.STATUS_NONE,
                        SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE,
                        false, false, false, null, null, null, null, null, null,
                        null, null, null);
            } else {
                throwException(e);
            }
        }
        return JavaHLObjectFactory.createStatus(path, status);
    }

    public void username(String username) {
        myUserName = username;
        updateClientManager();
    }

    public void password(String password) {
        myPassword = password;
        updateClientManager();
    }

    public void setPrompt(PromptUserPassword prompt) {
        myPrompt = prompt;
        updateClientManager();
    }
    
    private void updateClientManager() {
        File configDir = myConfigDir == null ? null : new File(myConfigDir);
        myOptions = SVNWCUtil.createDefaultOptions(configDir, true);
        myAuthenticationManager = SVNWCUtil.createDefaultAuthenticationManager(configDir, myUserName, myPassword, myOptions.isAuthStorageEnabled());
        if (myPrompt != null) {
            myAuthenticationManager.setAuthenticationProvider(new JavaHLAuthenticationProvider(myPrompt));
        } else {
            myAuthenticationManager.setAuthenticationProvider(null);
        }
        myAuthenticationManager.setRuntimeStorage(ourAuthStorage);
        myClientManager = null;
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
                        SVNURL.parseURIEncoded(path), new String[]{""},
                        SVNRevision.HEAD,
                        JavaHLObjectFactory.getSVNRevision(revisionStart),
                        JavaHLObjectFactory.getSVNRevision(revisionEnd),
                        stopOnCopy, discoverPath, limit, new ISVNLogEntryHandler(){
                            public void handleLogEntry(SVNLogEntry logEntry) {
                                entries.add(JavaHLObjectFactory.createLogMessage(logEntry));
                            }
                        }
                        );
            }else{
                client.doLog(
                        new File[]{new File(path).getAbsoluteFile()},
                        JavaHLObjectFactory.getSVNRevision(revisionStart),
                        JavaHLObjectFactory.getSVNRevision(revisionEnd),
                        stopOnCopy, discoverPath, limit, new ISVNLogEntryHandler(){
                            public void handleLogEntry(SVNLogEntry logEntry) {
                                entries.add(JavaHLObjectFactory.createLogMessage(logEntry));
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
        SVNUpdateClient updater = getSVNUpdateClient();
        boolean oldIgnoreExternals = updater.isIgnoreExternals(); 
        updater.setIgnoreExternals(ignoreExternals);
        try {
            File path = new File(destPath).getAbsoluteFile();
            return updater.doCheckout(SVNURL.parseURIEncoded(moduleName), path, JavaHLObjectFactory.getSVNRevision(pegRevision),
                    JavaHLObjectFactory.getSVNRevision(revision), recurse);
        } catch (SVNException e) {
            throwException(e);
        } finally {
            updater.setIgnoreExternals(oldIgnoreExternals);
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
            SVNURL[] urls = new SVNURL[path.length];
            for (int i = 0; i < urls.length; i++) {
                try {
                    urls[i] = SVNURL.parseURIEncoded(path[i]);
                } catch (SVNException e) {
                    throwException(e);
                }
            }
            try {
                client.doDelete(urls, message);
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
            wcClient.doAdd(new File(path).getAbsoluteFile(), force, false, false, recurse, false);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public long update(String path, Revision revision, boolean recurse) throws ClientException {
        SVNUpdateClient client = getSVNUpdateClient();
        try {
            return client.doUpdate(new File(path).getAbsoluteFile(), JavaHLObjectFactory.getSVNRevision(revision), recurse);
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
        SVNUpdateClient updater = getSVNUpdateClient();
        boolean oldIgnore = updater.isIgnoreExternals();
        updater.setIgnoreExternals(ignoreExternals);
        updater.setEventPathPrefix("");
        try {
            for (int i = 0; i < updated.length; i++) {
                updated[i] = update(path[i], revision, recurse);
            }
        } finally {
            updater.setIgnoreExternals(oldIgnore);
            updater.setEventPathPrefix(null);
            SVNFileUtil.sleepForTimestamp();
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
                client.setCommitHandler(new ISVNCommitHandler(){
                    public String getCommitMessage(String cmessage, SVNCommitItem[] commitables) {
                        CommitItem[] items = JavaHLObjectFactory.getCommitItems(commitables);
                        return myMessageHandler.getLogMessage(items);
                    }
                });
            }
            return client.doCommit(files, noUnlock, message, !recurse, recurse).getNewRevision();
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    public long[] commit(String[] path, String message, boolean recurse, boolean noUnlock, boolean atomicCommit) throws ClientException {
        if(path == null || path.length == 0){
            return new long[0];
        }
        SVNCommitClient client = getSVNCommitClient();
        File[] files = new File[path.length];
        for (int i = 0; i < path.length; i++) {
            files[i] = new File(path[i]).getAbsoluteFile();
        }
        SVNCommitPacket[] packets = null;
        SVNCommitInfo[] commitResults = null;
        try {
            if(myMessageHandler != null){
                client.setCommitHandler(new ISVNCommitHandler(){
                    public String getCommitMessage(String cmessage, SVNCommitItem[] commitables) {
                        CommitItem[] items = JavaHLObjectFactory.getCommitItems(commitables);
                        return myMessageHandler.getLogMessage(items);
                    }
                });
            }
            packets = client.doCollectCommitItems(files, noUnlock, !recurse, recurse, atomicCommit);
            commitResults = client.doCommit(packets, noUnlock, message);
        } catch (SVNException e) {
            throwException(e);
        }
        if (commitResults != null && commitResults.length > 0) {
            long[] revisions = new long[commitResults.length];
            for (int i = 0; i < commitResults.length; i++) {
                SVNCommitInfo result = commitResults[i];
                revisions[i] = result.getNewRevision();
            }
            return revisions;
            
        }
        return new long[0];
    }

    public void copy(String srcPath, String destPath, String message, Revision revision) throws ClientException {
        SVNCopyClient client = getSVNCopyClient();
        SVNRevision srcRevision = JavaHLObjectFactory.getSVNRevision(revision);
        try {
            if(isURL(srcPath) && isURL(destPath)){
                client.doCopy(SVNURL.parseURIEncoded(srcPath), srcRevision, SVNURL.parseURIEncoded(destPath), false, message);
            } else if (isURL(srcPath) && !isURL(destPath)) {
                client.doCopy(SVNURL.parseURIEncoded(srcPath), srcRevision, new File(destPath).getAbsoluteFile());
            } else if (!isURL(srcPath) && isURL(destPath)) {
                client.doCopy(new File(srcPath).getAbsoluteFile(), srcRevision, SVNURL.parseURIEncoded(destPath), message);
            } else if (!isURL(srcPath) && !isURL(destPath)) {
                client.doCopy(new File(srcPath).getAbsoluteFile(), srcRevision, new File(destPath).getAbsoluteFile(), false, false);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void move(String srcPath, String destPath, String message, Revision revision, boolean force) throws ClientException {
        SVNCopyClient client = getSVNCopyClient();
        SVNRevision srcRevision = JavaHLObjectFactory.getSVNRevision(revision);
        try {
            if(isURL(srcPath) && isURL(destPath)){
                client.doCopy(SVNURL.parseURIEncoded(srcPath), srcRevision, SVNURL.parseURIEncoded(destPath), true, message);
            } else if (!isURL(srcPath) && !isURL(destPath)) {
                client.doCopy(new File(srcPath).getAbsoluteFile(), srcRevision, new File(destPath).getAbsoluteFile(), force, true);
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
        List urls = new ArrayList();
        List paths = new ArrayList();
        for (int i = 0; i < path.length; i++) {
            if (isURL(path[i])) {
                try {
                    urls.add(SVNURL.parseURIEncoded(path[i]));
                } catch (SVNException e) {
                    throwException(e);
                }
            } else {
                paths.add(new File(path[i]));
            }
        }
        SVNURL[] svnURLs = (SVNURL[]) urls.toArray(new SVNURL[urls.size()]);
        File[] files = (File[]) paths.toArray(new File[paths.size()]);
        if (svnURLs.length > 0) {
            try {
                client.doMkDir(svnURLs, message);
            } catch (SVNException e) {
                throwException(e);
            }
        }
        if (files.length > 0) {
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                try {
                    getSVNWCClient().doAdd(file, false, true, false, false, false);
                } catch (SVNException e) {
                    throwException(e);
                }
            }
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
        boolean oldIgnore = updater.isIgnoreExternals();
        updater.setIgnoreExternals(ignoreExternals);
        try {
            if(isURL(srcPath)){
                return updater.doExport(SVNURL.parseURIEncoded(srcPath), new File(destPath).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNRevision(pegRevision), JavaHLObjectFactory.getSVNRevision(revision), nativeEOL, force, recurse);
            }
            return updater.doExport(new File(srcPath).getAbsoluteFile(), new File(destPath).getAbsoluteFile(),
                    JavaHLObjectFactory.getSVNRevision(pegRevision), JavaHLObjectFactory.getSVNRevision(revision), nativeEOL, force, recurse);
        } catch (SVNException e) {
            throwException(e);
        } finally {
            updater.setIgnoreExternals(oldIgnore);
        }
        return -1;
    }

    public long doSwitch(String path, String url, Revision revision, boolean recurse) throws ClientException {
        SVNUpdateClient updater = getSVNUpdateClient();
        try {
            return updater.doSwitch(new File(path).getAbsoluteFile(), SVNURL.parseURIEncoded(url), JavaHLObjectFactory.getSVNRevision(revision), recurse);
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    public void doImport(String path, String url, String message, boolean recurse) throws ClientException {
        SVNCommitClient commitClient = getSVNCommitClient();
        try {
            commitClient.doImport(new File(path), SVNURL.parseURIEncoded(url), message, recurse);
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
                SVNURL url1 = SVNURL.parseURIEncoded(path1);
                SVNURL url2 = SVNURL.parseURIEncoded(path2);
                differ.doMerge(url1, JavaHLObjectFactory.getSVNRevision(revision1), url2,
                        JavaHLObjectFactory.getSVNRevision(revision2), new File(localPath).getAbsoluteFile(),
                        recurse, !ignoreAncestry, force, dryRun);
            } else if (isURL(path1)) {
                SVNURL url1 = SVNURL.parseURIEncoded(path1);
                File file2 = new File(path2).getAbsoluteFile();
                differ.doMerge(url1, JavaHLObjectFactory.getSVNRevision(revision1), file2,
                        JavaHLObjectFactory.getSVNRevision(revision2), new File(localPath).getAbsoluteFile(),
                        recurse, !ignoreAncestry, force, dryRun);
            } else if (isURL(path2)) {
                SVNURL url2 = SVNURL.parseURIEncoded(path2);
                File file1 = new File(path1).getAbsoluteFile();
                differ.doMerge(file1, JavaHLObjectFactory.getSVNRevision(revision1), url2,
                        JavaHLObjectFactory.getSVNRevision(revision2), new File(localPath).getAbsoluteFile(),
                        recurse, !ignoreAncestry, force, dryRun);
            } else{
                File file1 = new File(path1).getAbsoluteFile();
                File file2 = new File(path2).getAbsoluteFile();
                differ.doMerge(file1, JavaHLObjectFactory.getSVNRevision(revision1), 
                        file2, JavaHLObjectFactory.getSVNRevision(revision2),
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
                SVNURL url = SVNURL.parseURIEncoded(path);
                differ.doMerge(url, 
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision1),
                        JavaHLObjectFactory.getSVNRevision(revision2),
                        new File(localPath).getAbsoluteFile(), recurse, !ignoreAncestry, force, dryRun);
            }else{
                differ.doMerge(new File(path).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision1),
                        JavaHLObjectFactory.getSVNRevision(revision2),
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
        differ.getDiffGenerator().setDiffDeleted(!noDiffDeleted);
        differ.getDiffGenerator().setForcedBinaryDiff(force);
        SVNRevision rev1 = JavaHLObjectFactory.getSVNRevision(revision1);
        SVNRevision rev2 = JavaHLObjectFactory.getSVNRevision(revision2);
        try {
            OutputStream out = SVNFileUtil.openFileForWriting(new File(outFileName));
            if(!isURL(target1)&&!isURL(target2)){
                differ.doDiff(new File(target1).getAbsoluteFile(), rev1,
                        new File(target2).getAbsoluteFile(), rev2,
                        recurse, !ignoreAncestry, out);
            }else if(isURL(target1)&&isURL(target2)){
                SVNURL url1 = SVNURL.parseURIEncoded(target1);
                SVNURL url2 = SVNURL.parseURIEncoded(target2);
                differ.doDiff(url1, rev1, url2, rev2, recurse, !ignoreAncestry, out);
            }else if(!isURL(target1)&&isURL(target2)){
                SVNURL url2 = SVNURL.parseURIEncoded(target2);
                differ.doDiff(new File(target1).getAbsoluteFile(), rev1,
                        url2, rev2, recurse, !ignoreAncestry, out);
            }else if(isURL(target1)&&!isURL(target2)){
                SVNURL url1 = SVNURL.parseURIEncoded(target1);
                differ.doDiff(url1, rev1, new File(target2).getAbsoluteFile(), rev2, recurse, !ignoreAncestry, out);
            }
            SVNFileUtil.closeFile(out);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void diff(String target, Revision pegRevision, Revision startRevision, Revision endRevision, String outFileName, boolean recurse, boolean ignoreAncestry, boolean noDiffDeleted, boolean force) throws ClientException {
        SVNDiffClient differ = getSVNDiffClient();
        differ.getDiffGenerator().setDiffDeleted(!noDiffDeleted);
        differ.getDiffGenerator().setForcedBinaryDiff(force);
        SVNRevision peg = JavaHLObjectFactory.getSVNRevision(pegRevision);
        SVNRevision rev1 = JavaHLObjectFactory.getSVNRevision(startRevision);
        SVNRevision rev2 = JavaHLObjectFactory.getSVNRevision(endRevision);
        try {
            OutputStream out = SVNFileUtil.openFileForWriting(new File(outFileName));
            
            if(isURL(target)){
                SVNURL url = SVNURL.parseURIEncoded(target);
                differ.doDiff(url, peg, rev1, rev2, recurse, !ignoreAncestry, out);
            }else{
                differ.doDiff(new File(target).getAbsoluteFile(), peg, rev1, rev2, recurse, !ignoreAncestry, out);
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
        SVNRevision svnRevision = JavaHLObjectFactory.getSVNRevision(revision);
        SVNRevision svnPegRevision = JavaHLObjectFactory.getSVNRevision(pegRevision);
        JavaHLPropertyHandler propHandler = new JavaHLPropertyHandler(myOwner);
        try {
            if(isURL(path)){
                client.doGetProperty(SVNURL.parseURIEncoded(path), null, svnPegRevision, svnRevision, false, propHandler);
            }else{
                client.doGetProperty(new File(path).getAbsoluteFile(), null, svnPegRevision, svnRevision, false, propHandler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return propHandler.getAllPropertyData();
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
        SVNRevision svnRevision = JavaHLObjectFactory.getSVNRevision(rev);
        JavaHLPropertyHandler retriever = new JavaHLPropertyHandler(myOwner);
        try {
            if(isURL(path)){
                client.doGetRevisionProperty(SVNURL.parseURIEncoded(path), name, svnRevision, retriever);
            }else{
                client.doGetRevisionProperty(new File(path).getAbsoluteFile(), name,
                        svnRevision, retriever);
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
        SVNRevision svnRevision = JavaHLObjectFactory.getSVNRevision(rev);
        JavaHLPropertyHandler propHandler = new JavaHLPropertyHandler(myOwner);
        try {
            if(isURL(path)){
                client.doGetRevisionProperty(SVNURL.parseURIEncoded(path), null, svnRevision, propHandler);
            }else{
                client.doGetRevisionProperty(new File(path).getAbsoluteFile(), null, svnRevision, propHandler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return propHandler.getAllPropertyData();
    }

    public void setRevProperty(String path, String name, Revision rev, String value, boolean force) throws ClientException {
        if(name == null || name.equals("")){
            return;
        }
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = JavaHLObjectFactory.getSVNRevision(rev);
        try {
            if(isURL(path)){
                client.doSetRevisionProperty(SVNURL.parseURIEncoded(path),
                        svnRevision, name, value, force, ISVNPropertyHandler.NULL);
            }else{
                client.doSetRevisionProperty(new File(path).getAbsoluteFile(),
                        svnRevision, name, value, force, ISVNPropertyHandler.NULL);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public PropertyData propertyGet(String path, String name) throws ClientException {
        return propertyGet(path, name, null);
    }

    public PropertyData propertyGet(String path, String name, Revision revision) throws ClientException {
        return propertyGet(path, name, revision, null);
    }

    public PropertyData propertyGet(String path, String name, Revision revision, Revision pegRevision) throws ClientException {
        if(name == null || name.equals("")){
            return null;
        }
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = JavaHLObjectFactory.getSVNRevision(revision);
        SVNRevision svnPegRevision = JavaHLObjectFactory.getSVNRevision(pegRevision);
        JavaHLPropertyHandler retriever = new JavaHLPropertyHandler(myOwner);
        try {
            if(isURL(path)){
                client.doGetProperty(SVNURL.parseURIEncoded(path), name, svnPegRevision, svnRevision, false, retriever);
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
                client.doGetFileContents(SVNURL.parseURIEncoded(path),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision), true, baos);
            }else{
                client.doGetFileContents(new File(path).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision), true, baos);
            }
            return baos.toByteArray();
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
    }

    public void streamFileContent(String path, Revision revision, Revision pegRevision, int bufferSize, OutputStream stream) throws ClientException {
        SVNWCClient client = getSVNWCClient();
        try {
            if(isURL(path)){
                client.doGetFileContents(SVNURL.parseURIEncoded(path),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision), true, stream);
            }else{
                client.doGetFileContents(new File(path).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision), true, stream);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void relocate(String from, String to, String path, boolean recurse) throws ClientException {
        SVNUpdateClient client = getSVNUpdateClient();
        try {
            client.doRelocate(new File(path).getAbsoluteFile(), SVNURL.parseURIEncoded(from), SVNURL.parseURIEncoded(to), recurse);
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
                result.append(author != null ? SVNFormatUtil.formatString(author, 10, false) : "         -");
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
                client.doAnnotate(SVNURL.parseURIEncoded(path),
                        SVNRevision.UNDEFINED,
                        JavaHLObjectFactory.getSVNRevision(revisionStart),
                        JavaHLObjectFactory.getSVNRevision(revisionEnd),
                        handler);
            }else{
                client.doAnnotate(new File(path).getAbsoluteFile(),
                        SVNRevision.UNDEFINED,
                        JavaHLObjectFactory.getSVNRevision(revisionStart),
                        JavaHLObjectFactory.getSVNRevision(revisionEnd),
                        handler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return baos.toByteArray();
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
                client.doAnnotate(SVNURL.parseURIEncoded(path),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revisionStart),
                        JavaHLObjectFactory.getSVNRevision(revisionEnd),
                        handler);
            }else{
                client.doAnnotate(new File(path).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revisionStart),
                        JavaHLObjectFactory.getSVNRevision(revisionEnd),
                        handler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void dispose() {
        SVNGanymedSession.shutdown();
        new DefaultSVNRepositoryPool(null, null).shutdownConnections(true);
    }

    public void setConfigDirectory(String configDir) throws ClientException {
        myConfigDir = configDir;
        updateClientManager();
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
                return JavaHLObjectFactory.createInfo(client.doInfo(SVNURL.parseURIEncoded(path), SVNRevision.UNDEFINED, SVNRevision.UNDEFINED));
            }
            return JavaHLObjectFactory.createInfo(client.doInfo(new File(path).getAbsoluteFile(), SVNRevision.UNDEFINED));
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE) {
                return null;
            }
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
                SVNURL[] urls = new SVNURL[path.length];
                for (int i = 0; i < urls.length; i++) {
                    urls[i] = SVNURL.parseURIEncoded(path[i]);
                }
                getSVNWCClient().doLock(urls, force, comment);
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
                SVNURL[] urls = new SVNURL[path.length];
                for (int i = 0; i < urls.length; i++) {
                    urls[i] = SVNURL.parseURIEncoded(path[i]);
                }
                getSVNWCClient().doUnlock(urls, force);
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
                infos.add(JavaHLObjectFactory.createInfo2(info));
            }
        };
        try {
            if(isURL(pathOrUrl)){
                client.doInfo(SVNURL.parseURIEncoded(pathOrUrl),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision),
                        recurse, handler);
            }else{
                client.doInfo(new File(pathOrUrl).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNRevision(revision),
                        recurse, handler);
            }
            return (Info2[]) infos.toArray(new Info2[infos.size()]);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE) {
                return new Info2[0];
            }
            throwException(e);
        }
        return null;
    }

    public String getVersionInfo(String path, String trailUrl, boolean lastChanged) throws ClientException {
        try {
            return getSVNWCClient().doGetWorkingCopyID(new File(path).getAbsoluteFile(), trailUrl);
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
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
                    if (path != null) {
                        path = path.replace(File.separatorChar, '/');
                    }
                    if(myNotify != null && event.getErrorMessage() == null){
                        myNotify.onNotify(
                                path,
                                JavaHLObjectFactory.getNotifyActionValue(event.getAction()),
                                JavaHLObjectFactory.getNodeKind(event.getNodeKind()),
                                event.getMimeType(),
                                JavaHLObjectFactory.getStatusValue(event.getContentsStatus()),
                                JavaHLObjectFactory.getStatusValue(event.getPropertiesStatus()),
                                event.getRevision()
                                );
                    }
                    if(myNotify2 != null){
                        NotifyInformation info = JavaHLObjectFactory.createNotifyInformation(event, path);
                        myNotify2.onNotify(info);
                    }
                }

                public void checkCancelled() throws SVNCancelException {
                    if(myCancelOperation){
                        myCancelOperation = false;
                        SVNErrorManager.cancel("operation cancelled");
                    }
                }
            };
        }
        return mySVNEventListener;
    }
    
    public SVNClientManager getClientManager() {
        if (myClientManager == null) {
            updateClientManager();
            myClientManager = SVNClientManager.newInstance(myOptions, new DefaultSVNRepositoryPool(myAuthenticationManager, myOptions));
            myClientManager.setEventHandler(getEventListener());
        }
        return myClientManager;
    }

    protected SVNCommitClient getSVNCommitClient(){
        return getClientManager().getCommitClient();
    }

    protected SVNUpdateClient getSVNUpdateClient(){
        return getClientManager().getUpdateClient();
    }

    protected SVNStatusClient getSVNStatusClient(){
        return getClientManager().getStatusClient();
    }

    protected SVNWCClient getSVNWCClient(){
        return getClientManager().getWCClient();
    }

    protected SVNDiffClient getSVNDiffClient(){
        return getClientManager().getDiffClient();
    }

    protected SVNCopyClient getSVNCopyClient(){
        return getClientManager().getCopyClient();
    }

    protected SVNLogClient getSVNLogClient(){
        return getClientManager().getLogClient();
    }

    protected CommitMessage getCommitMessage() {
        return myMessageHandler;
    }

    protected void throwException(SVNException e) throws ClientException {
        JavaHLObjectFactory.throwException(e, this);
    }

    protected static boolean isURL(String pathOrUrl){
        pathOrUrl = pathOrUrl != null ? pathOrUrl.toLowerCase() : null;
        return pathOrUrl != null
                && (pathOrUrl.startsWith("http://")
                        || pathOrUrl.startsWith("https://")
                        || pathOrUrl.startsWith("svn://") 
                        || (pathOrUrl.startsWith("svn+") && pathOrUrl.indexOf("://") > 4)
                        || pathOrUrl.startsWith("file://"));
    }

    public String getAdminDirectoryName() {
        return SVNFileUtil.getAdminDirectoryName();
    }

    public boolean isAdminDirectory(String name) {
        return name != null && (SVNFileUtil.isWindows) ?
                name.equalsIgnoreCase(SVNFileUtil.getAdminDirectoryName()) :
                name.equals(SVNFileUtil.getAdminDirectoryName());
    }

    public org.tigris.subversion.javahl.Version getVersion() {        
        return SVNClientImplVersion.getInstance();
    }
}
