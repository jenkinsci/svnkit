/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tigris.subversion.javahl.BlameCallback;
import org.tigris.subversion.javahl.BlameCallback2;
import org.tigris.subversion.javahl.ChangelistCallback;
import org.tigris.subversion.javahl.ClientException;
import org.tigris.subversion.javahl.CommitItem;
import org.tigris.subversion.javahl.CommitMessage;
import org.tigris.subversion.javahl.ConflictDescriptor;
import org.tigris.subversion.javahl.ConflictResolverCallback;
import org.tigris.subversion.javahl.ConflictResult;
import org.tigris.subversion.javahl.CopySource;
import org.tigris.subversion.javahl.DiffSummaryReceiver;
import org.tigris.subversion.javahl.DirEntry;
import org.tigris.subversion.javahl.Info;
import org.tigris.subversion.javahl.Info2;
import org.tigris.subversion.javahl.InfoCallback;
import org.tigris.subversion.javahl.JavaHLObjectFactory;
import org.tigris.subversion.javahl.ListCallback;
import org.tigris.subversion.javahl.LogMessage;
import org.tigris.subversion.javahl.LogMessageCallback;
import org.tigris.subversion.javahl.Mergeinfo;
import org.tigris.subversion.javahl.Notify;
import org.tigris.subversion.javahl.Notify2;
import org.tigris.subversion.javahl.NotifyInformation;
import org.tigris.subversion.javahl.ProgressListener;
import org.tigris.subversion.javahl.PromptUserPassword;
import org.tigris.subversion.javahl.PropertyData;
import org.tigris.subversion.javahl.ProplistCallback;
import org.tigris.subversion.javahl.Revision;
import org.tigris.subversion.javahl.RevisionRange;
import org.tigris.subversion.javahl.SVNClient;
import org.tigris.subversion.javahl.SVNClientInterface;
import org.tigris.subversion.javahl.SVNClientLogLevel;
import org.tigris.subversion.javahl.Status;
import org.tigris.subversion.javahl.StatusCallback;
import org.tigris.subversion.javahl.SubversionException;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationStorage;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnectionFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.ISVNConnectorFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.io.svn.SVNSSHSession;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.DefaultSVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.ISVNChangelistHandler;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.ISVNDiffStatusHandler;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNCommitPacket;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffStatus;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.Version;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNClientImpl implements SVNClientInterface {

    private static int ourInstanceCount;

    private String myConfigDir;
    private PromptUserPassword myPrompt;
    private String myUserName;
    private String myPassword;
    private ISVNEventHandler mySVNEventListener;
    private ISVNConflictHandler mySVNConflictHandler;
    /**
     * @deprecated
     */
    private Notify myNotify;
    private Notify2 myNotify2;
    private ConflictResolverCallback myConflictResolverCallback;
    private CommitMessage myMessageHandler;
    private ISVNOptions myOptions;
    private boolean myCancelOperation = false;
    private SVNClientManager myClientManager;
    private SVNClientInterface myOwner;

    private ISVNAuthenticationManager myAuthenticationManager;

    private ISVNAuthenticationStorage myAuthStorage;
    private static ISVNAuthenticationStorage ourAuthStorage;

    /**
     * @author TMate Software Ltd.
     * @version 1.1.1
     */
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
        return newInstance(owner, httpConnectionFactory, svnConnectorFactory, true);
    }

    public static SVNClientImpl newInstance(SVNClient owner,
                                            IHTTPConnectionFactory httpConnectionFactory, ISVNConnectorFactory svnConnectorFactory, boolean trackClient) {
        SVNClientImpl client = new SVNClientImpl(owner, httpConnectionFactory, svnConnectorFactory);
        if (trackClient) {
            SVNClientImplTracker.registerClient(client);
        }
        return client;
    }

    public static ISVNAuthenticationStorage getRuntimeCredentialsStorage() {
        synchronized (SVNClientImpl.class) {
            if (ourAuthStorage == null) {
                ourAuthStorage = new JavaHLAuthenticationStorage();
            }
            return ourAuthStorage;
        }
    }

    public static void setRuntimeCredentialsStorage(ISVNAuthenticationStorage storage) {
        synchronized (SVNClientImpl.class) {
            ourAuthStorage = storage == null ? new JavaHLAuthenticationStorage() : storage;
        }
    }

    public ISVNAuthenticationStorage getClientCredentialsStorage() {
        if (myAuthStorage != null) {
            return myAuthStorage;
        }
        return getRuntimeCredentialsStorage();
    }

    public void setClientCredentialsStorage(ISVNAuthenticationStorage storage) {
        myAuthStorage = storage;
        updateClientManager();
    }

    protected SVNClientImpl(SVNClient owner) {
        this(owner, null, null);
    }

    protected SVNClientImpl(SVNClient owner, IHTTPConnectionFactory httpConnectionFactory, ISVNConnectorFactory svnConnectorFactory) {
        DAVRepositoryFactory.setup(httpConnectionFactory);
        SVNRepositoryFactoryImpl.setup(svnConnectorFactory);
        FSRepositoryFactory.setup();
        myConfigDir = SVNWCUtil.getDefaultConfigurationDirectory().getAbsolutePath();
        myOwner = owner == null ? (SVNClientInterface) this : (SVNClientInterface) owner;
        synchronized (SVNClientImpl.class) {
            ourInstanceCount++;
        }
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

    public Status[] status(final String path, boolean descend, boolean onServer, boolean getAll, 
            boolean noIgnore, boolean ignoreExternals) throws ClientException {
        if (path == null) {
            return null;
        }
        final Collection statuses = new ArrayList();
        status(path, JavaHLObjectFactory.unknownOrImmediates(descend), onServer, getAll, noIgnore, 
                ignoreExternals, null, new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) {
                statuses.add(JavaHLObjectFactory.createStatus(status.getFile().getPath(), status));
            }
        });
        return (Status[]) statuses.toArray(new Status[statuses.size()]);
    }

    private void status(String path, int depth, boolean onServer, boolean getAll, boolean noIgnore, 
            boolean ignoreExternals, String[] changelists, ISVNStatusHandler handler) throws ClientException {
        if (path == null) {
            return;
        }
        SVNStatusClient stClient = getSVNStatusClient();
        boolean oldIgnoreExternals = stClient.isIgnoreExternals();
        stClient.setIgnoreExternals(ignoreExternals);
        try {
            stClient.doStatus(new File(path).getAbsoluteFile(), SVNRevision.HEAD,
                    JavaHLObjectFactory.getSVNDepth(depth), onServer, getAll, noIgnore,
                    !ignoreExternals, handler, JavaHLObjectFactory.getChangeListsCollection(changelists));
        } catch (SVNException e) {
            throwException(e);
        } finally {
            stClient.setIgnoreExternals(oldIgnoreExternals);
        }
    }

    public void status(String path, int depth, boolean onServer, boolean getAll, boolean noIgnore, 
            boolean ignoreExternals, String[] changelists, StatusCallback callback) throws ClientException {
        final StatusCallback statusCallback = callback;
        status(path, depth, onServer, getAll, noIgnore, ignoreExternals, changelists, new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) {
                if (statusCallback != null) {
                    statusCallback.doStatus(JavaHLObjectFactory.createStatus(status.getFile().getPath(), status));
                }
            }
        });
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
                        null, null, null, null);
            } else {
                throwException(e);
            }
        }
        return JavaHLObjectFactory.createStatus(path, status);
    }

    public DirEntry[] list(String url, Revision revision, boolean recurse) throws ClientException {
        return list(url, revision, revision, recurse);
    }

    public DirEntry[] list(String url, Revision revision, Revision pegRevision, boolean recurse) throws ClientException {
        final Collection allEntries = new ArrayList();
        list(url, revision, pegRevision, JavaHLObjectFactory.infinityOrImmediates(recurse), SVNDirEntry.DIRENT_ALL, false, new ISVNDirEntryHandler() {
            public void handleDirEntry(SVNDirEntry dirEntry) {
                allEntries.add(JavaHLObjectFactory.createDirEntry(dirEntry));
            }
        });
        return (DirEntry[]) allEntries.toArray(new DirEntry[allEntries.size()]);
    }

    public void list(String url, Revision revision, Revision pegRevision, int depth, int direntFields, boolean fetchLocks, ListCallback callback) throws ClientException {
        final ListCallback listCallback = callback;
        list(url, revision, pegRevision, depth, direntFields, fetchLocks, new ISVNDirEntryHandler() {
            public void handleDirEntry(SVNDirEntry dirEntry) {
                if (listCallback != null) {
                    listCallback.doEntry(JavaHLObjectFactory.createDirEntry(dirEntry), JavaHLObjectFactory.createLock(dirEntry.getLock()));
                }
            }
        });
    }

    private void list(String url, Revision revision, Revision pegRevision, int depth, int direntFields, boolean fetchLocks, ISVNDirEntryHandler handler) throws ClientException {
        SVNLogClient client = getSVNLogClient();
        try {
            if (isURL(url)) {
                client.doList(SVNURL.parseURIEncoded(url),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision),
                        fetchLocks,
                        JavaHLObjectFactory.getSVNDepth(depth),
                        direntFields, handler);
            } else {
                client.doList(new File(url).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision),
                        fetchLocks,
                        JavaHLObjectFactory.getSVNDepth(depth),
                        direntFields, handler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
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
        myOptions.setConflictHandler(getConflictHandler());
        myAuthenticationManager = SVNWCUtil.createDefaultAuthenticationManager(configDir, myUserName, myPassword, myOptions.isAuthStorageEnabled());
        if (myPrompt != null) {
            myAuthenticationManager.setAuthenticationProvider(new JavaHLAuthenticationProvider(myPrompt));
        } else {
            myAuthenticationManager.setAuthenticationProvider(null);
        }
        myAuthenticationManager.setRuntimeStorage(getClientCredentialsStorage());
        if (myClientManager != null) {
            myClientManager.shutdownConnections(true);
            myClientManager.setAuthenticationManager(myAuthenticationManager);
            myClientManager.setOptions(myOptions);
        }
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd) throws ClientException {
        return logMessages(path, revisionStart, revisionEnd, true, false);
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy) throws ClientException {
        return logMessages(path, revisionStart, revisionEnd, stopOnCopy, false);
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy, boolean discoverPath) throws ClientException {
        return logMessages(path, revisionStart, revisionEnd, stopOnCopy, discoverPath, 0);
    }

    public LogMessage[] logMessages(String path, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy, boolean discoverPath, long limit) throws ClientException {
        final Collection entries = new ArrayList();
        String[] revisionProperties = new String[]{SVNRevisionProperty.LOG, SVNRevisionProperty.DATE, SVNRevisionProperty.AUTHOR};
        logMessages(path, revisionEnd, revisionStart, revisionEnd, stopOnCopy, discoverPath, false, revisionProperties, limit,
                new ISVNLogEntryHandler() {
                    public void handleLogEntry(SVNLogEntry logEntry) {
                        entries.add(JavaHLObjectFactory.createLogMessage(logEntry));
                    }
                }
        );
        return (LogMessage[]) entries.toArray(new LogMessage[entries.size()]);
    }

    public void logMessages(String path, Revision pegRevision, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy, boolean discoverPath, boolean includeMergedRevisions,
                            String[] revProps, long limit, LogMessageCallback callback) throws ClientException {
        final LogMessageCallback logMessageCallback = callback;
        logMessages(path, pegRevision, revisionStart, revisionEnd, stopOnCopy, discoverPath, includeMergedRevisions, revProps, limit,
                new ISVNLogEntryHandler() {
                    public void handleLogEntry(SVNLogEntry logEntry) {
                        JavaHLObjectFactory.handleLogMessage(logEntry, logMessageCallback);
                    }
                }
        );
    }

    private void logMessages(String path, Revision pegRevision, Revision revisionStart, Revision revisionEnd, boolean stopOnCopy, boolean discoverPath, boolean includeMergeInfo, String[] revisionProperties, long limit, ISVNLogEntryHandler logEntryHandler) throws ClientException {
        SVNLogClient client = getSVNLogClient();
        try {
            if (isURL(path)) {
                client.doLog(
                        SVNURL.parseURIEncoded(path), new String[]{""},
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revisionStart),
                        JavaHLObjectFactory.getSVNRevision(revisionEnd),
                        stopOnCopy, discoverPath, includeMergeInfo, limit, revisionProperties, logEntryHandler);
            } else {
                client.doLog(
                        new File[]{new File(path).getAbsoluteFile()},
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revisionStart),
                        JavaHLObjectFactory.getSVNRevision(revisionEnd),
                        stopOnCopy, discoverPath, includeMergeInfo, limit, revisionProperties, logEntryHandler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    /**
     * @deprecated
     */
    public void notification(Notify notify) {
        myNotify = notify;
    }

    public void notification2(Notify2 notify) {
        myNotify2 = notify;
    }

    public void setProgressListener(ProgressListener listener) {
        //TODO: Implement
    }

    public void commitMessageHandler(CommitMessage messageHandler) {
        myMessageHandler = messageHandler;
    }

    public void setConflictResolver(ConflictResolverCallback listener) {
        myConflictResolverCallback = listener;
    }

    public void remove(String[] path, String message, boolean force) throws ClientException {
        remove(path, message, force, false);
    }

    public void remove(String[] path, String message, boolean force, boolean keepLocal) throws ClientException {
        boolean areURLs = false;
        for (int i = 0; i < path.length; i++) {
            areURLs = areURLs || isURL(path[i]);
        }
        if (areURLs) {
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
        } else {
            SVNWCClient client = getSVNWCClient();
            for (int i = 0; i < path.length; i++) {
                try {
                    client.doDelete(new File(path[i]).getAbsoluteFile(), force, !keepLocal, false);
                } catch (SVNException e) {
                    throwException(e);
                }
            }
        }
    }

    public void revert(String path, boolean recurse) throws ClientException {
        revert(path, JavaHLObjectFactory.infinityOrEmpty(recurse), null);
    }

	public void revert(String path, int depth, String[] changelists)
			throws ClientException {
        SVNWCClient client = getSVNWCClient();
        try {
            client.doRevert(new File[] { new File(path).getAbsoluteFile() }, 
                    JavaHLObjectFactory.getSVNDepth(depth), 
                    JavaHLObjectFactory.getChangeListsCollection(changelists));
        } catch (SVNException e) {
            throwException(e);
        }
	}

    public void add(String path, boolean recurse) throws ClientException {
        add(path, recurse, false);
    }

    public void add(String path, int depth, boolean force, boolean noIgnores, boolean addParents) throws ClientException {
        add(path, SVNDepth.fromID(depth).isRecursive(), force, noIgnores, addParents);
    }

    public void add(String path, boolean recurse, boolean force) throws ClientException {
        add(path, recurse, force, false, false);
    }

    private void add(String path, boolean recurse, boolean force, boolean noIgnores, boolean addParents) throws ClientException {
        SVNWCClient wcClient = getSVNWCClient();
        try {
            wcClient.doAdd(new File(path).getAbsoluteFile(), force, false, addParents, recurse, noIgnores);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public long update(String path, Revision revision, boolean recurse) throws ClientException {
        long[] updated =  update(new String[]{path}, revision, JavaHLObjectFactory.unknownOrFiles(recurse), true, false, false);
        return updated[0];
    }

    public long[] update(String[] path, Revision revision, boolean recurse, boolean ignoreExternals) throws ClientException {
        return update(path, revision, JavaHLObjectFactory.unknownOrFiles(recurse), true, ignoreExternals, false);
    }

    public long update(String path, Revision revision, int depth, boolean depthIsSticky, boolean ignoreExternals, boolean allowUnverObstructions) throws ClientException {
        long[] updated = update(new String[]{path}, revision, depth, depthIsSticky, ignoreExternals, allowUnverObstructions);
        return updated[0];
    }

    public long[] update(String[] path, Revision revision, int depth, boolean depthIsSticky, boolean ignoreExternals, boolean allowUnverObstructions) throws ClientException {
        if (path == null || path.length == 0) {
            return new long[]{};
        }
        long[] updated = new long[path.length];
        SVNUpdateClient updater = getSVNUpdateClient();
        boolean oldIgnore = updater.isIgnoreExternals();
        updater.setIgnoreExternals(ignoreExternals);
        updater.setEventPathPrefix("");
        SVNDepth svnDepth = JavaHLObjectFactory.getSVNDepth(depth);
        SVNRevision rev = JavaHLObjectFactory.getSVNRevision(revision);
        try {
            for (int i = 0; i < updated.length; i++) {
                updated[i] = updater.doUpdate(new File(path[i]).getAbsoluteFile(), rev, svnDepth, 
                        allowUnverObstructions, depthIsSticky);
            }
        } catch (SVNException e) {
            throwException(e);
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
        return commit(path, message, JavaHLObjectFactory.infinityOrEmpty(recurse), noUnlock, false, null);
    }

    public long commit(String[] path, String message, int depth, boolean noUnlock, boolean keepChangelist, String[] changelists) throws ClientException {
        if (path == null || path.length == 0) {
            return 0;
        }
        SVNCommitClient client = getSVNCommitClient();
        File[] files = new File[path.length];
        for (int i = 0; i < path.length; i++) {
            files[i] = new File(path[i]).getAbsoluteFile();
        }
        try {
            if (myMessageHandler != null) {
                client.setCommitHandler(new ISVNCommitHandler() {
                    public String getCommitMessage(String cmessage, SVNCommitItem[] commitables) {
                        CommitItem[] items = JavaHLObjectFactory.getCommitItems(commitables);
                        return myMessageHandler.getLogMessage(items);
                    }

                    public SVNProperties getRevisionProperties(String message, SVNCommitItem[] commitables, SVNProperties revisionProperties) throws SVNException {
                        return revisionProperties == null ? new SVNProperties() : revisionProperties;
                    }
                    
                });
            }
            SVNDepth svnDepth = SVNDepth.fromID(depth);
            boolean recurse = SVNDepth.recurseFromDepth(svnDepth);
            return client.doCommit(files, noUnlock, message, null, changelists, keepChangelist, !recurse, svnDepth).getNewRevision();
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }

    public long[] commit(String[] path, String message, boolean recurse, boolean noUnlock, boolean atomicCommit) throws ClientException {
        if (path == null || path.length == 0) {
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
            if (myMessageHandler != null) {
                client.setCommitHandler(new ISVNCommitHandler() {
                    public String getCommitMessage(String cmessage, SVNCommitItem[] commitables) {
                        CommitItem[] items = JavaHLObjectFactory.getCommitItems(commitables);
                        return myMessageHandler.getLogMessage(items);
                    }

                    public SVNProperties getRevisionProperties(String message, SVNCommitItem[] commitables, SVNProperties revisionProperties) throws SVNException {
                        return revisionProperties == null ? new SVNProperties() : revisionProperties;
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
        copy(new CopySource[]{new CopySource(srcPath, revision, Revision.HEAD)}, destPath, message, true, false);
    }

    public void copy(CopySource[] sources, String destPath, String message, boolean copyAsChild, boolean makeParents) throws ClientException {
        SVNCopySource[] copySources = getCopySources(sources, copyAsChild);
        copyOrMove(copySources, destPath, false, message, copyAsChild, makeParents);
    }

    public void move(String srcPath, String destPath, String message, boolean force) throws ClientException {
        move(new String[]{srcPath}, destPath, message, force, true, false);
    }

    public void move(String srcPath, String destPath, String message, Revision revision, boolean force) throws ClientException {
        move(new String[]{srcPath}, destPath, message, force, true, false);
    }

    public void move(String[] srcPaths, String destPath, String message, boolean force, boolean moveAsChild, boolean makeParents) throws ClientException {
        SVNCopySource[] copySources = getCopySources(srcPaths, moveAsChild);
        copyOrMove(copySources, destPath, true, message, moveAsChild, makeParents);
    }

    private SVNCopySource[] getCopySources(CopySource[] srcs, boolean copyAsChild) throws ClientException {
        if (srcs.length > 1 && !copyAsChild) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MULTIPLE_SOURCES_DISALLOWED);
            SVNException ex = new SVNException(err);
            throwException(ex);
        }
        SVNCopySource[] sources = new SVNCopySource[srcs.length];
        try {
            for (int i = 0; i < srcs.length; i++) {
                if (isURL(srcs[i].getPath())) {
                    sources[i] = new SVNCopySource(JavaHLObjectFactory.getSVNRevision(srcs[i].getPegRevision()),
                            JavaHLObjectFactory.getSVNRevision(srcs[i].getRevision()), SVNURL.parseURIEncoded(srcs[i].getPath()));
                } else {
                    sources[i] = new SVNCopySource(JavaHLObjectFactory.getSVNRevision(srcs[i].getPegRevision()),
                            JavaHLObjectFactory.getSVNRevision(srcs[i].getRevision()), new File(srcs[i].getPath()).getAbsoluteFile());
                }
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return sources;
    }

    private SVNCopySource[] getCopySources(String[] srcPaths, boolean copyAsChild) throws ClientException {
        if (srcPaths.length > 1 && !copyAsChild) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_MULTIPLE_SOURCES_DISALLOWED);
            SVNException ex = new SVNException(err);
            throwException(ex);
        }
        SVNCopySource[] sources = new SVNCopySource[srcPaths.length];
        SVNRevision srcRevision = JavaHLObjectFactory.getSVNRevision(Revision.WORKING);
        try {
            for (int i = 0; i < srcPaths.length; i++) {
                if (isURL(srcPaths[i])) {
                    sources[i] = new SVNCopySource(SVNRevision.UNDEFINED, srcRevision, SVNURL.parseURIEncoded(srcPaths[i]));
                } else {
                    sources[i] = new SVNCopySource(SVNRevision.UNDEFINED, srcRevision, new File(srcPaths[i]).getAbsoluteFile());
                }
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return sources;
    }

    private void copyOrMove(SVNCopySource[] sources, String destPath, boolean isMove, String message, boolean copyAsChild, boolean makeParents) throws ClientException {
        SVNCopyClient client = getSVNCopyClient();
        try {
            if (isURL(destPath)) {
                client.doCopy(sources, SVNURL.parseURIEncoded(destPath), isMove, !copyAsChild, makeParents, message, null);
            } else {
                client.doCopy(sources, new File(destPath).getAbsoluteFile(), isMove, makeParents, !copyAsChild);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void mkdir(String[] path, String message) throws ClientException {
        mkdir(path, message, false);
    }

    public void mkdir(String[] path, String message, boolean makeParents) throws ClientException {
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
                client.doMkDir(svnURLs, message, null, makeParents);
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
        resolved(path, JavaHLObjectFactory.infinityOrEmpty(recurse), ConflictResult.chooseMerged);
    }

    public void resolved(String path, int depth, int conflictResult) throws ClientException {
        SVNWCClient client = getSVNWCClient();
        try {
            client.doResolve(new File(path).getAbsoluteFile(), JavaHLObjectFactory.getSVNDepth(depth), JavaHLObjectFactory.getSVNConflictChoice(conflictResult));
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public long doExport(String srcPath, String destPath, Revision revision, boolean force) throws ClientException {
        return doExport(srcPath, destPath, revision, null, force, false, true, "");
    }

    public long doExport(String srcPath, String destPath, Revision revision, Revision pegRevision, boolean force, boolean ignoreExternals, boolean recurse, String nativeEOL) throws ClientException {
        return doExport(srcPath, destPath, revision, pegRevision, force, ignoreExternals, JavaHLObjectFactory.infinityOrFiles(recurse), nativeEOL);
    }

    public long doExport(String srcPath, String destPath, Revision revision, Revision pegRevision, boolean force, boolean ignoreExternals, int depth, String nativeEOL) throws ClientException {
        SVNUpdateClient updater = getSVNUpdateClient();
        boolean oldIgnore = updater.isIgnoreExternals();
        updater.setIgnoreExternals(ignoreExternals);
        try {
            if (isURL(srcPath)) {
                return updater.doExport(SVNURL.parseURIEncoded(srcPath), new File(destPath).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNRevision(pegRevision), JavaHLObjectFactory.getSVNRevision(revision), nativeEOL, force, JavaHLObjectFactory.getSVNDepth(depth));
            }
            return updater.doExport(new File(srcPath).getAbsoluteFile(), new File(destPath).getAbsoluteFile(),
                    JavaHLObjectFactory.getSVNRevision(pegRevision), JavaHLObjectFactory.getSVNRevision(revision), nativeEOL, force, JavaHLObjectFactory.getSVNDepth(depth));
        } catch (SVNException e) {
            throwException(e);
        } finally {
            updater.setIgnoreExternals(oldIgnore);
        }
        return -1;
    }

    public void doImport(String path, String url, String message, boolean recurse) throws ClientException {
        doImport(path, url, message, JavaHLObjectFactory.infinityOrFiles(recurse), false, false);
    }

    public void doImport(String path, String url, String message, int depth, boolean noIgnore, boolean ignoreUnknownNodeTypes) throws ClientException {
        SVNCommitClient commitClient = getSVNCommitClient();
        try {
            commitClient.doImport(new File(path), SVNURL.parseURIEncoded(url), message, null, !noIgnore, ignoreUnknownNodeTypes, JavaHLObjectFactory.getSVNDepth(depth));
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void merge(String path1, Revision revision1, String path2, Revision revision2, String localPath, boolean force, boolean recurse) throws ClientException {
        merge(path1, revision1, path2, revision2, localPath, force, recurse, false, false);
    }

    public void merge(String path1, Revision revision1, String path2, Revision revision2, String localPath,
                      boolean force, boolean recurse, boolean ignoreAncestry, boolean dryRun) throws ClientException {
        merge(path1, revision1, path2, revision2, localPath, force, JavaHLObjectFactory.infinityOrFiles(recurse),
                ignoreAncestry, dryRun, false);
    }

    public void merge(String path, Revision pegRevision, RevisionRange[] revisions, String localPath, boolean force, int depth, boolean ignoreAncestry, boolean dryRun, boolean recordOnly) throws ClientException {
        for (int i = 0; i < revisions.length; i++) {
            merge(path, pegRevision, revisions[i].getFromRevision(),
                    revisions[i].getToRevision(), localPath, force, depth,
                    ignoreAncestry, dryRun, recordOnly);
        }
    }

    public void merge(String path, Revision pegRevision, Revision revision1, Revision revision2, String localPath, boolean force, boolean recurse, boolean ignoreAncestry, boolean dryRun) throws ClientException {
        merge(path, pegRevision, revision1, revision2, localPath, force, JavaHLObjectFactory.infinityOrFiles(recurse), ignoreAncestry, dryRun, false);
    }

    private void merge(String path, Revision pegRevision, Revision revision1, Revision revision2, String localPath, boolean force, int depth, boolean ignoreAncestry, boolean dryRun, boolean recordOnly) throws ClientException {
        SVNDiffClient differ = getSVNDiffClient();
        List rangesToMerge = new LinkedList();
        rangesToMerge.add(new SVNRevisionRange(JavaHLObjectFactory.getSVNRevision(revision1), 
        		JavaHLObjectFactory.getSVNRevision(revision2)));
        try {
            if (isURL(path)) {
                SVNURL url = SVNURL.parseURIEncoded(path);
                differ.doMerge(url,
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        rangesToMerge,
                        new File(localPath).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNDepth(depth),
                        !ignoreAncestry, force, dryRun, false);
            } else {
                differ.doMerge(new File(path).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        rangesToMerge,
                        new File(localPath).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNDepth(depth),
                        !ignoreAncestry, force, dryRun, false);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void merge(String path1, Revision revision1, String path2, Revision revision2, String localPath, 
            boolean force, int depth, boolean ignoreAncestry, boolean dryRun, boolean recordOnly) throws ClientException {
        SVNDiffClient differ = getSVNDiffClient();
        try {
            if (isURL(path1) && isURL(path2)) {
                SVNURL url1 = SVNURL.parseURIEncoded(path1);
                SVNURL url2 = SVNURL.parseURIEncoded(path2);
                differ.doMerge(url1, JavaHLObjectFactory.getSVNRevision(revision1), url2,
                        JavaHLObjectFactory.getSVNRevision(revision2), new File(localPath).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, force, dryRun, recordOnly);
            } else if (isURL(path1)) {
                SVNURL url1 = SVNURL.parseURIEncoded(path1);
                File file2 = new File(path2).getAbsoluteFile();
                differ.doMerge(url1, JavaHLObjectFactory.getSVNRevision(revision1), file2,
                        JavaHLObjectFactory.getSVNRevision(revision2), new File(localPath).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, force, dryRun, recordOnly);
            } else if (isURL(path2)) {
                SVNURL url2 = SVNURL.parseURIEncoded(path2);
                File file1 = new File(path1).getAbsoluteFile();
                differ.doMerge(file1, JavaHLObjectFactory.getSVNRevision(revision1), url2,
                        JavaHLObjectFactory.getSVNRevision(revision2), new File(localPath).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, force, dryRun, recordOnly);
            } else {
                File file1 = new File(path1).getAbsoluteFile();
                File file2 = new File(path2).getAbsoluteFile();
                differ.doMerge(file1, JavaHLObjectFactory.getSVNRevision(revision1),
                        file2, JavaHLObjectFactory.getSVNRevision(revision2),
                        new File(localPath).getAbsoluteFile(), JavaHLObjectFactory.getSVNDepth(depth),
                        !ignoreAncestry, force, dryRun, recordOnly);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void mergeReintegrate(String path, Revision pegRevision, String localPath, boolean dryRun) throws ClientException {
        SVNDiffClient diffClient = getSVNDiffClient();
        File dstPath = new File(localPath);
        try {
            if (isURL(path)){
                SVNURL url = SVNURL.parseURIEncoded(path);
                diffClient.doMergeReIntegrate(url, JavaHLObjectFactory.getSVNRevision(pegRevision), dstPath, 
                        dryRun);
            } else {
                File file = new File(path);
                diffClient.doMergeReIntegrate(file, JavaHLObjectFactory.getSVNRevision(pegRevision), dstPath, 
                        dryRun);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public PropertyData[] properties(String path) throws ClientException {
        return properties(path, null);
    }

    public PropertyData[] properties(String path, Revision revision) throws ClientException {
        return properties(path, revision, revision);
    }

    public PropertyData[] properties(String path, Revision revision, Revision pegRevision) throws ClientException {
        return properties(path, revision, pegRevision, SVNDepth.EMPTY, null);
    }

	public void properties(String path, Revision revision,
			Revision pegRevision, int depth, String[] changelists,
			ProplistCallback callback) throws ClientException {
        if (path == null || callback == null) {
            return;
        }
        PropertyData[] properties = properties(path, revision, pegRevision, 
                JavaHLObjectFactory.getSVNDepth(depth), changelists);
        Map propsMap = new HashMap();
        for (int i = 0; i < properties.length; i++) {
            propsMap.put(properties[i].getName(), properties[i].getValue());
        }
        callback.singlePath(path, propsMap);
	}

    private PropertyData[] properties(String path, Revision revision, Revision pegRevision, SVNDepth depth, 
            String[] changelists) throws ClientException {
        if (path == null) {
            return null;
        }
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = JavaHLObjectFactory.getSVNRevision(revision);
        SVNRevision svnPegRevision = JavaHLObjectFactory.getSVNRevision(pegRevision);
        JavaHLPropertyHandler propHandler = new JavaHLPropertyHandler(myOwner);
        try {
            if (isURL(path)) {
                client.doGetProperty(SVNURL.parseURIEncoded(path), null, svnPegRevision, svnRevision, depth, propHandler);
            } else {
                client.doGetProperty(new File(path).getAbsoluteFile(), null, svnPegRevision, svnRevision, depth, 
                        propHandler, JavaHLObjectFactory.getChangeListsCollection(changelists));
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return propHandler.getAllPropertyData();
    }

    public void propertySet(String path, String name, byte[] value, boolean recurse) throws ClientException {
        propertySet(path, name, value, recurse, false);
    }

    public void propertySet(String path, String name, byte[] value, boolean recurse, boolean force) throws ClientException {
        propertySet(path, name, SVNPropertyValue.create(name, value), 
                JavaHLObjectFactory.infinityOrEmpty(recurse), force, null);
    }

    public void propertySet(String path, String name, String value, boolean recurse) throws ClientException {
        propertySet(path, name, value, recurse, false);
    }

    public void propertySet(String path, String name, String value, boolean recurse, boolean force) throws ClientException {
        propertySet(path, name, value, JavaHLObjectFactory.infinityOrEmpty(recurse), null, force);
    }

    public void propertySet(String path, String name, String value, int depth,
            String[] changelists, boolean force) throws ClientException {
        propertySet(path, name, SVNPropertyValue.create(value), depth, force, changelists);
    }

    private void propertySet(String path, String name, SVNPropertyValue value, int depth, boolean force, 
            String[] changelists) throws ClientException {
       SVNWCClient client = getSVNWCClient();
        try {
            client.doSetProperty(new File(path).getAbsoluteFile(), name, value, force, 
                    JavaHLObjectFactory.getSVNDepth(depth), ISVNPropertyHandler.NULL, 
                    JavaHLObjectFactory.getChangeListsCollection(changelists));
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void propertyRemove(String path, String name, boolean recurse) throws ClientException {
        propertyRemove(path, name, JavaHLObjectFactory.infinityOrEmpty(recurse), null);
    }

	public void propertyRemove(String path, String name, int depth, String[] changelists) throws ClientException {
        SVNWCClient client = getSVNWCClient();
        try {
            client.doSetProperty(new File(path).getAbsoluteFile(), name, null, false, 
                    JavaHLObjectFactory.getSVNDepth(depth), ISVNPropertyHandler.NULL, 
                    JavaHLObjectFactory.getChangeListsCollection(changelists));
        } catch (SVNException e) {
            throwException(e);
        }
	}

    public PropertyData propertyGet(String path, String name) throws ClientException {
        return propertyGet(path, name, null);
    }

    public PropertyData propertyGet(String path, String name, Revision revision) throws ClientException {
        return propertyGet(path, name, revision, revision);
    }

    public PropertyData propertyGet(String path, String name, Revision revision, Revision pegRevision) throws ClientException {
        if (name == null || name.equals("")) {
            return null;
        }
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = JavaHLObjectFactory.getSVNRevision(revision);
        SVNRevision svnPegRevision = JavaHLObjectFactory.getSVNRevision(pegRevision);
        JavaHLPropertyHandler retriever = new JavaHLPropertyHandler(myOwner);
        try {
            if (isURL(path)) {
                client.doGetProperty(SVNURL.parseURIEncoded(path), name, svnPegRevision, svnRevision, SVNDepth.EMPTY, retriever);
            } else {
                client.doGetProperty(new File(path).getAbsoluteFile(), name, svnPegRevision, svnRevision, SVNDepth.EMPTY, retriever, null);
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return retriever.getPropertyData();
    }

    public void propertyCreate(String path, String name, String value, boolean recurse) throws ClientException {
        propertyCreate(path, name, value, recurse, false);
    }

    public void propertyCreate(String path, String name, byte[] value, boolean recurse) throws ClientException {
        propertyCreate(path, name, value, recurse, false);
    }

    public void propertyCreate(String path, String name, byte[] value, boolean recurse, boolean force) throws ClientException {
        propertyCreate(path, name, SVNPropertyValue.create(name, value), JavaHLObjectFactory.infinityOrEmpty(recurse), null, force);
    }

    public void propertyCreate(String path, String name, String value, boolean recurse, boolean force) throws ClientException {
        propertySet(path, name, value, recurse, force);
    }

	public void propertyCreate(String path, String name, String value,
                               int depth, String[] changelists, boolean force)
            throws ClientException {
        propertyCreate(path, name, SVNPropertyValue.create(value), depth, changelists, force);
    }

	private void propertyCreate(String path, String name, SVNPropertyValue value,
                               int depth, String[] changelists, boolean force)
            throws ClientException {
        SVNWCClient client = getSVNWCClient();
        try {
            client.doSetProperty(new File(path).getAbsoluteFile(), name, value, force, JavaHLObjectFactory.getSVNDepth(depth),
                    ISVNPropertyHandler.NULL, JavaHLObjectFactory.getChangeListsCollection(changelists));
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public PropertyData revProperty(String path, String name, Revision rev) throws ClientException {
        if (name == null || name.equals("")) {
            return null;
        }
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = JavaHLObjectFactory.getSVNRevision(rev);
        JavaHLPropertyHandler retriever = new JavaHLPropertyHandler(myOwner);
        try {
            if (isURL(path)) {
                client.doGetRevisionProperty(SVNURL.parseURIEncoded(path), name, svnRevision, retriever);
            } else {
                client.doGetRevisionProperty(new File(path).getAbsoluteFile(), name,
                        svnRevision, retriever);
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return retriever.getPropertyData();
    }

    public PropertyData[] revProperties(String path, Revision rev) throws ClientException {
        if (path == null) {
            return null;
        }
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = JavaHLObjectFactory.getSVNRevision(rev);
        JavaHLPropertyHandler propHandler = new JavaHLPropertyHandler(myOwner);
        try {
            if (isURL(path)) {
                client.doGetRevisionProperty(SVNURL.parseURIEncoded(path), null, svnRevision, propHandler);
            } else {
                client.doGetRevisionProperty(new File(path).getAbsoluteFile(), null, svnRevision, propHandler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
        return propHandler.getAllPropertyData();
    }

    public void setRevProperty(String path, String name, Revision rev, String value, boolean force) throws ClientException {
        if (name == null || name.equals("")) {
            return;
        }
        SVNWCClient client = getSVNWCClient();
        SVNRevision svnRevision = JavaHLObjectFactory.getSVNRevision(rev);
        SVNPropertyValue propertyValue = SVNPropertyValue.create(value);
        try {
            if (isURL(path)) {
                client.doSetRevisionProperty(SVNURL.parseURIEncoded(path),
                        svnRevision, name, propertyValue, force, ISVNPropertyHandler.NULL);
            } else {
                client.doSetRevisionProperty(new File(path).getAbsoluteFile(),
                        svnRevision, name, propertyValue, force, ISVNPropertyHandler.NULL);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public byte[] fileContent(String path, Revision revision) throws ClientException {
        return fileContent(path, revision, revision);
    }

    public byte[] fileContent(String path, Revision revision, Revision pegRevision) throws ClientException {
        SVNWCClient client = getSVNWCClient();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (isURL(path)) {
                client.doGetFileContents(SVNURL.parseURIEncoded(path),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision), true, baos);
            } else {
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
            if (isURL(path)) {
                client.doGetFileContents(SVNURL.parseURIEncoded(path),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revision), true, stream);
            } else {
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

    public void blame(String path, Revision revisionStart, Revision revisionEnd, BlameCallback callback) throws ClientException {
        blame(path, revisionEnd, revisionStart, revisionEnd, callback);
    }

    public byte[] blame(String path, Revision revisionStart, Revision revisionEnd) throws ClientException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ISVNAnnotateHandler handler = new ISVNAnnotateHandler() {
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

            public void handleLine(Date date, long revision, String author, String line,
                                   Date mergedDate, long mergedRevision, String mergedAuthor,
                                   String mergedPath, int lineNumber) throws SVNException {
                handleLine(mergedDate == null ? date : mergedDate,
                        mergedRevision < 0 ? revision : mergedRevision,
                        mergedAuthor == null ? author : mergedAuthor,
                        line);
            }

            public boolean handleRevision(Date date, long revision, String author, File contents) throws SVNException {
                return false;
            }

            public void handleEOF() {
            }
        };
        blame(path, revisionEnd, revisionStart, revisionEnd, false, false, handler);
        return baos.toByteArray();
    }

    public void blame(String path, Revision pegRevision, Revision revisionStart, Revision revisionEnd, BlameCallback callback) throws ClientException {
        final BlameCallback blameCallback = callback;
        ISVNAnnotateHandler handler = new ISVNAnnotateHandler() {
            public void handleLine(Date date, long revision, String author, String line) {
                if (blameCallback != null) {
                    blameCallback.singleLine(date, revision, author, line);
                }
            }

            public void handleLine(Date date, long revision, String author, String line,
                                   Date mergedDate, long mergedRevision, String mergedAuthor,
                                   String mergedPath, int lineNumber) throws SVNException {
                handleLine(date, revision, author, line);
            }

            public boolean handleRevision(Date date, long revision, String author, File contents) throws SVNException {
                return false;
            }

            public void handleEOF() {
            }
        };
        blame(path, pegRevision, revisionStart, revisionEnd, false, false, handler);
    }

    public void blame(String path, Revision pegRevision, Revision revisionStart, Revision revisionEnd, boolean ignoreMimeType, boolean includeMergedRevisions, BlameCallback2 callback) throws ClientException {
        final BlameCallback2 blameCallback = callback;
        ISVNAnnotateHandler handler = new ISVNAnnotateHandler() {
            public void handleLine(Date date, long revision, String author, String line) {
            }

            public void handleLine(Date date, long revision, String author, String line,
                                   Date mergedDate, long mergedRevision, String mergedAuthor,
                                   String mergedPath, int lineNumber) throws SVNException {
                if (blameCallback != null) {
                    blameCallback.singleLine(date, revision, author, mergedDate, mergedRevision, mergedAuthor, mergedPath, line);
                }
            }

            public boolean handleRevision(Date date, long revision, String author, File contents) throws SVNException {
                return false;
            }

            public void handleEOF() {
            }
        };
        blame(path, pegRevision, revisionStart, revisionEnd, ignoreMimeType, includeMergedRevisions, handler);
    }

    private void blame(String path, Revision pegRevision, Revision revisionStart, Revision revisionEnd, boolean ignoreMimeType, boolean includeMergedRevisions, ISVNAnnotateHandler handler) throws ClientException {
        SVNLogClient client = getSVNLogClient();
        try {
            if (isURL(path)) {
                client.doAnnotate(SVNURL.parseURIEncoded(path),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revisionStart),
                        JavaHLObjectFactory.getSVNRevision(revisionEnd),
                        ignoreMimeType,
                        includeMergedRevisions,
                        handler,
                        null);
            } else {
                client.doAnnotate(new File(path).getAbsoluteFile(),
                        JavaHLObjectFactory.getSVNRevision(pegRevision),
                        JavaHLObjectFactory.getSVNRevision(revisionStart),
                        JavaHLObjectFactory.getSVNRevision(revisionEnd),
                        ignoreMimeType,
                        includeMergedRevisions,
                        handler,
                        null);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void dispose() {
        if (myClientManager != null) {
            myClientManager.dispose();
            myClientManager = null;
        }
        synchronized (SVNClientImpl.class) {
            ourInstanceCount--;
            if (ourInstanceCount <= 0) {
                ourInstanceCount = 0;
                SVNSSHSession.shutdown();
            }
        }
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
            if (isURL(path)) {
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
            if (allFiles) {
                File[] files = new File[path.length];
                for (int i = 0; i < files.length; i++) {
                    files[i] = new File(path[i]).getAbsoluteFile();
                }
                getSVNWCClient().doLock(files, force, comment);
            } else {
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
            if (allFiles) {
                File[] files = new File[path.length];
                for (int i = 0; i < files.length; i++) {
                    files[i] = new File(path[i]).getAbsoluteFile();
                }
                getSVNWCClient().doUnlock(files, force);
            } else {
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

    /**
     * @deprecated
     */
    protected Notify getNotify() {
        return myNotify;
    }

    protected Notify2 getNotify2() {
        return myNotify2;
    }

    protected ISVNEventHandler getEventListener() {
        if (mySVNEventListener == null) {
            mySVNEventListener = new ISVNEventHandler() {

                public void handleEvent(SVNEvent event, double progress) {
                    String path = event.getFile().getAbsolutePath();
                    if (path != null) {
                        path = path.replace(File.separatorChar, '/');
                    }
                    if (myNotify != null && event.getErrorMessage() == null) {
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
                    if (myNotify2 != null) {
                        NotifyInformation info = JavaHLObjectFactory.createNotifyInformation(event, path);
                        myNotify2.onNotify(info);
                    }
                }

                public void checkCancelled() throws SVNCancelException {
                    if (myCancelOperation) {
                        myCancelOperation = false;
                        SVNErrorManager.cancel("operation cancelled");
                    }
                }
            };
        }
        return mySVNEventListener;
    }

    protected ISVNConflictHandler getConflictHandler() {
        if (mySVNConflictHandler == null) {
            mySVNConflictHandler = new ISVNConflictHandler() {

                public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription) throws SVNException {
                    SVNConflictResult result = null;
                    if (myConflictResolverCallback != null) {
                        ConflictDescriptor descriptor = JavaHLObjectFactory.createConflictDescription(conflictDescription);
                        try {
                            result = JavaHLObjectFactory.getSVNConflictResult(myConflictResolverCallback.resolve(descriptor));
                        } catch (SubversionException e) {
                            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, e), e);
                        }
                    }
                    return result;
                }
            };
        }
        return mySVNConflictHandler;
    }

    public ISVNOptions getOptions() {
        if (myOptions == null) {
            File configDir = myConfigDir == null ? null : new File(myConfigDir);
            myOptions = SVNWCUtil.createDefaultOptions(configDir, true);
            myOptions.setConflictHandler(getConflictHandler());
        }        
        return myOptions;
    }

    public SVNClientManager getClientManager() {
        if (myClientManager == null) {
            updateClientManager();
            myClientManager = SVNClientManager.newInstance(myOptions, new DefaultSVNRepositoryPool(myAuthenticationManager, myOptions));
            myClientManager.setEventHandler(getEventListener());
        }
        return myClientManager;
    }

    protected SVNCommitClient getSVNCommitClient() {
        return getClientManager().getCommitClient();
    }

    protected SVNUpdateClient getSVNUpdateClient() {
        return getClientManager().getUpdateClient();
    }

    protected SVNStatusClient getSVNStatusClient() {
        return getClientManager().getStatusClient();
    }

    protected SVNWCClient getSVNWCClient() {
        return getClientManager().getWCClient();
    }

    protected SVNDiffClient getSVNDiffClient() {
        return getClientManager().getDiffClient();
    }

    protected SVNCopyClient getSVNCopyClient() {
        return getClientManager().getCopyClient();
    }

    protected SVNLogClient getSVNLogClient() {
        return getClientManager().getLogClient();
    }

    protected SVNChangelistClient getChangelistClient() {
        return getClientManager().getChangelistClient();
    }

    protected CommitMessage getCommitMessage() {
        return myMessageHandler;
    }

    protected void throwException(SVNException e) throws ClientException {
        JavaHLObjectFactory.throwException(e, this);
    }

    protected static boolean isURL(String pathOrUrl) {
        return SVNPathUtil.isURL(pathOrUrl);
    }

    public String getAdminDirectoryName() {
        return SVNFileUtil.getAdminDirectoryName();
    }

    public boolean isAdminDirectory(String name) {
        return name != null && (SVNFileUtil.isWindows) ?
                SVNFileUtil.getAdminDirectoryName().equalsIgnoreCase(name) :
                SVNFileUtil.getAdminDirectoryName().equals(name);
    }

    public org.tigris.subversion.javahl.Version getVersion() {
        return SVNClientImplVersion.getInstance();
    }

    public long doSwitch(String path, String url, Revision revision, boolean recurse) throws ClientException {
        return doSwitch(path, url, revision, Revision.HEAD, JavaHLObjectFactory.unknownOrFiles(recurse), true, false, false);
    }

    public long doSwitch(String path, String url, Revision revision, Revision pegRevision, int depth, 
            boolean depthIsSticky, boolean ignoreExternals, boolean allowUnverObstructions) throws ClientException {
        SVNUpdateClient updater = getSVNUpdateClient();
        try {
            return updater.doSwitch(new File(path).getAbsoluteFile(), SVNURL.parseURIEncoded(url), 
                    JavaHLObjectFactory.getSVNRevision(pegRevision), 
                    JavaHLObjectFactory.getSVNRevision(revision), JavaHLObjectFactory.getSVNDepth(depth), 
                    allowUnverObstructions, depthIsSticky);
        } catch (SVNException e) {
            throwException(e);
        }
        return -1;
    }


    public void addToChangelist(String[] paths, String changelist, int depth, String[] changelists) throws ClientException {
        if (paths == null || paths.length == 0 ||
                changelist == null || "".equals(changelist)) {
            return;
        }

        SVNChangelistClient changelistClient = getChangelistClient();
        File[] files = new File[paths.length];
        for (int i = 0; i < paths.length; i++) {
            files[i] = new File(paths[i]).getAbsoluteFile();
        }

        try {
            changelistClient.addToChangelist(files, JavaHLObjectFactory.getSVNDepth(depth), changelist, changelists);
        } catch (SVNException e) {
            throwException(e);
        }
    }

	public void removeFromChangelists(String[] paths, int depth, String[] changelists) throws ClientException {
        if (paths == null || paths.length == 0 || changelists == null) {
            return;
        }

        File[] files = new File[paths.length];
        for (int i = 0; i < paths.length; i++) {
            files[i] = new File(paths[i]).getAbsoluteFile();
        }

        SVNChangelistClient changelistClient = getChangelistClient();
        try {
            changelistClient.removeFromChangelist(files, JavaHLObjectFactory.getSVNDepth(depth), changelists);
        } catch (SVNException e) {
            throwException(e);
        }
	}

    public void getChangelists(String rootPath, String[] changelists, int depth, 
            final ChangelistCallback callback) throws ClientException {
        if (changelists == null) {
            return;
        }

        ISVNChangelistHandler handler = new ISVNChangelistHandler() {
            public void handle(File path, String changelistName) {
                if (callback != null) {
                    callback.doChangelist(path.getAbsolutePath(), changelistName);
                }
            }
        };
        
        SVNChangelistClient changelistClient = getChangelistClient();
        try {
            changelistClient.getChangeLists(new File(rootPath).getAbsoluteFile(), 
                    JavaHLObjectFactory.getChangeListsCollection(changelists), 
                    JavaHLObjectFactory.getSVNDepth(depth), handler);
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public long checkout(String moduleName, String destPath, Revision revision, boolean recurse) throws ClientException {
        return checkout(moduleName, destPath, revision, revision, recurse, false);
    }

    public long checkout(String moduleName, String destPath, Revision revision, Revision pegRevision, boolean recurse, boolean ignoreExternals) throws ClientException {
        return checkout(moduleName, destPath, revision, pegRevision, JavaHLObjectFactory.infinityOrFiles(recurse), ignoreExternals, false);
    }

    public long checkout(String moduleName, String destPath, Revision revision, Revision pegRevision, int depth, boolean ignoreExternals, boolean allowUnverObstructions) throws ClientException {
        SVNUpdateClient updater = getSVNUpdateClient();
        boolean oldIgnoreExternals = updater.isIgnoreExternals();
        updater.setIgnoreExternals(ignoreExternals);
        try {
            File path = new File(destPath).getAbsoluteFile();
            return updater.doCheckout(SVNURL.parseURIEncoded(moduleName), path, JavaHLObjectFactory.getSVNRevision(pegRevision),
                    JavaHLObjectFactory.getSVNRevision(revision), JavaHLObjectFactory.getSVNDepth(depth), allowUnverObstructions);
        } catch (SVNException e) {
            throwException(e);
        } finally {
            updater.setIgnoreExternals(oldIgnoreExternals);
        }
        return -1;
    }

    public void diff(String target1, Revision revision1, String target2, Revision revision2, String outFileName, boolean recurse) throws ClientException {
        diff(target1, revision1, target2, revision2, outFileName, recurse, true, false, false);
    }

    public void diff(String target1, Revision revision1, String target2, Revision revision2, String outFileName,
                     boolean recurse, boolean ignoreAncestry, boolean noDiffDeleted, boolean force) throws ClientException {
        diff(target1, revision1, target2, revision2, null, outFileName, JavaHLObjectFactory.infinityOrFiles(recurse), 
                null, ignoreAncestry, noDiffDeleted, force);
    }

    public void diff(String target, Revision pegRevision, Revision startRevision, Revision endRevision,
                     String outFileName, boolean recurse, boolean ignoreAncestry, boolean noDiffDeleted, boolean force) throws ClientException {
        diff(target, pegRevision, startRevision, endRevision, null, outFileName,
                JavaHLObjectFactory.unknownOrFiles(recurse), null, ignoreAncestry, noDiffDeleted, force);
    }

	public void diff(String target1, Revision revision1, String target2,
			Revision revision2, String relativeToDir, String outFileName,
			int depth, String[] changelists, boolean ignoreAncestry,
			boolean noDiffDeleted, boolean force) throws ClientException {
        SVNDiffClient differ = getSVNDiffClient();
        differ.getDiffGenerator().setDiffDeleted(!noDiffDeleted);
        differ.getDiffGenerator().setForcedBinaryDiff(force);
        differ.setOptions(getOptions());
        SVNRevision rev1 = JavaHLObjectFactory.getSVNRevision(revision1);
        SVNRevision rev2 = JavaHLObjectFactory.getSVNRevision(revision2);
        try {
            OutputStream out = SVNFileUtil.openFileForWriting(new File(outFileName));
            if (!isURL(target1) && !isURL(target2)) {
                differ.doDiff(new File(target1).getAbsoluteFile(), rev1,
                        new File(target2).getAbsoluteFile(), rev2,
                        JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, out, 
                        JavaHLObjectFactory.getChangeListsCollection(changelists));
            } else if (isURL(target1) && isURL(target2)) {
                SVNURL url1 = SVNURL.parseURIEncoded(target1);
                SVNURL url2 = SVNURL.parseURIEncoded(target2);
                differ.doDiff(url1, rev1, url2, rev2, JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, out);
            } else if (!isURL(target1) && isURL(target2)) {
                SVNURL url2 = SVNURL.parseURIEncoded(target2);
                differ.doDiff(new File(target1).getAbsoluteFile(), rev1,
                        url2, rev2, JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, out,
                        JavaHLObjectFactory.getChangeListsCollection(changelists));
            } else if (isURL(target1) && !isURL(target2)) {
                SVNURL url1 = SVNURL.parseURIEncoded(target1);
                differ.doDiff(url1, rev1, new File(target2).getAbsoluteFile(), rev2, 
                        JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, out, 
                        JavaHLObjectFactory.getChangeListsCollection(changelists));
            }
            SVNFileUtil.closeFile(out);
        } catch (SVNException e) {
            throwException(e);
        }
	}

	public void diff(String target, Revision pegRevision,
			Revision startRevision, Revision endRevision, String relativeToDir,
			String outFileName, int depth, String[] changelists,
			boolean ignoreAncestry, boolean noDiffDeleted, boolean force)
			throws ClientException {
        SVNDiffClient differ = getSVNDiffClient();
        differ.getDiffGenerator().setDiffDeleted(!noDiffDeleted);
        differ.getDiffGenerator().setForcedBinaryDiff(force);
        differ.setOptions(getOptions());
        SVNRevision peg = JavaHLObjectFactory.getSVNRevision(pegRevision);
        SVNRevision rev1 = JavaHLObjectFactory.getSVNRevision(startRevision);
        SVNRevision rev2 = JavaHLObjectFactory.getSVNRevision(endRevision);
        try {
            OutputStream out = SVNFileUtil.openFileForWriting(new File(outFileName));
            if (isURL(target)) {
                SVNURL url = SVNURL.parseURIEncoded(target);
                differ.doDiff(url, peg, rev1, rev2, JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, out);
            } else {
                differ.doDiff(new File(target).getAbsoluteFile(), peg, rev1, rev2, 
                        JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, out,
                        JavaHLObjectFactory.getChangeListsCollection(changelists));
            }
            SVNFileUtil.closeFile(out);
        } catch (SVNException e) {
            throwException(e);
        }
	}

    public void diffSummarize(String target1, Revision revision1, String target2, Revision revision2, int depth, String[] changelists, boolean ignoreAncestry, final DiffSummaryReceiver receiver) throws ClientException {
        SVNDiffClient differ = getSVNDiffClient();
        SVNRevision rev1 = JavaHLObjectFactory.getSVNRevision(revision1);
        SVNRevision rev2 = JavaHLObjectFactory.getSVNRevision(revision2);
        ISVNDiffStatusHandler handler = new ISVNDiffStatusHandler() {
            public void handleDiffStatus(SVNDiffStatus diffStatus) throws SVNException {
                if (receiver != null) {
                    receiver.onSummary(JavaHLObjectFactory.createDiffSummary(diffStatus));
                }
            }
        };
        try {
            if (!isURL(target1) && !isURL(target2)) {
                differ.doDiffStatus(new File(target1).getAbsoluteFile(), rev1,
                        new File(target2).getAbsoluteFile(), rev2, JavaHLObjectFactory.getSVNDepth(depth),
                        !ignoreAncestry, handler);
            } else if (isURL(target1) && isURL(target2)) {
                SVNURL url1 = SVNURL.parseURIEncoded(target1);
                SVNURL url2 = SVNURL.parseURIEncoded(target2);
                differ.doDiffStatus(url1, rev1, url2, rev2, JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, handler);
            } else if (!isURL(target1) && isURL(target2)) {
                SVNURL url2 = SVNURL.parseURIEncoded(target2);
                differ.doDiffStatus(new File(target1).getAbsoluteFile(), rev1,
                        url2, rev2, JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, handler);
            } else if (isURL(target1) && !isURL(target2)) {
                SVNURL url1 = SVNURL.parseURIEncoded(target1);
                differ.doDiffStatus(url1, rev1, new File(target2).getAbsoluteFile(), rev2, JavaHLObjectFactory.getSVNDepth(depth), !ignoreAncestry, handler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public void diffSummarize(String target, Revision pegRevision, Revision startRevision, Revision endRevision,
                              int depth, String[] changelists, boolean ignoreAncestry, final DiffSummaryReceiver receiver) throws ClientException {
        SVNDiffClient differ = getSVNDiffClient();
        SVNRevision rev1 = JavaHLObjectFactory.getSVNRevision(startRevision);
        SVNRevision rev2 = JavaHLObjectFactory.getSVNRevision(endRevision);
        SVNRevision pegRev = JavaHLObjectFactory.getSVNRevision(pegRevision);

        ISVNDiffStatusHandler handler = new ISVNDiffStatusHandler() {
            public void handleDiffStatus(SVNDiffStatus diffStatus) throws SVNException {
                if (diffStatus != null) {
                    receiver.onSummary(JavaHLObjectFactory.createDiffSummary(diffStatus));
                }
            }
        };
        try {
            if (!isURL(target)) {
                differ.doDiffStatus(new File(target).getAbsoluteFile(),
                        rev1, rev2, pegRev,
                        JavaHLObjectFactory.getSVNDepth(depth),
                        !ignoreAncestry, handler);
            } else {
                SVNURL url = SVNURL.parseURIEncoded(target);
                differ.doDiffStatus(url, rev1, rev2, pegRev,
                        JavaHLObjectFactory.getSVNDepth(depth),
                        !ignoreAncestry, handler);
            }
        } catch (SVNException e) {
            throwException(e);
        }
    }

    public Info2[] info2(String pathOrUrl, Revision revision, Revision pegRevision, boolean recurse) throws ClientException {
        final Collection infos = new ArrayList();
        try {
            info2(pathOrUrl, revision, pegRevision, JavaHLObjectFactory.infinityOrEmpty(recurse), null, new ISVNInfoHandler() {
                public void handleInfo(SVNInfo info) {
                    infos.add(JavaHLObjectFactory.createInfo2(info));
                }
            });
            return (Info2[]) infos.toArray(new Info2[infos.size()]);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE) {
                return new Info2[0];
            }
            throwException(e);
        }
        return null;
    }

	public void info2(String pathOrUrl, Revision revision,
			Revision pegRevision, int depth, String[] changelists,
			InfoCallback callback) throws ClientException {
        final InfoCallback infoCallback = callback;
        try {
            info2(pathOrUrl, revision, pegRevision, depth, changelists, new ISVNInfoHandler() {
                public void handleInfo(SVNInfo info) {
                    if (infoCallback != null) {
                        infoCallback.singleInfo(JavaHLObjectFactory.createInfo2(info));
                    }
                }
            });
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.UNVERSIONED_RESOURCE) {
                throwException(e);
            }
        }
	}

    private void info2(String pathOrUrl, Revision revision, Revision pegRevision, int depth, String[] changelists, ISVNInfoHandler handler) throws SVNException {
        SVNWCClient client = getSVNWCClient();
        if (isURL(pathOrUrl)) {
            client.doInfo(SVNURL.parseURIEncoded(pathOrUrl),
                    JavaHLObjectFactory.getSVNRevision(pegRevision),
                    JavaHLObjectFactory.getSVNRevision(revision),
                    JavaHLObjectFactory.getSVNDepth(depth), handler);
        } else {
            client.doInfo(new File(pathOrUrl).getAbsoluteFile(),
                    JavaHLObjectFactory.getSVNRevision(pegRevision),
                    JavaHLObjectFactory.getSVNRevision(revision),
                    JavaHLObjectFactory.getSVNDepth(depth), changelists, handler);
        }
    }

    public Mergeinfo getMergeinfo(String path, Revision revision) throws SubversionException {
        SVNDiffClient client = getSVNDiffClient();
        Map mergeInfo = null;
        try {
            if (isURL(path)) {
                mergeInfo = client.getMergeInfo(SVNURL.parseURIEncoded(path),
                        JavaHLObjectFactory.getSVNRevision(revision), null);
            } else {
                mergeInfo = client.getMergeInfo(new File(path).getAbsoluteFile(), 
                        JavaHLObjectFactory.getSVNRevision(revision), null);
            }
            return JavaHLObjectFactory.createMergeInfo(mergeInfo);
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
    }

    public String[] suggestMergeSources(String path, Revision pegRevision) throws SubversionException {
        SVNDiffClient client = getSVNDiffClient();
        Collection mergeSrcURLs = null;
        try {
            mergeSrcURLs = client.suggestMergeSources(new File(path).getAbsoluteFile(), 
                    JavaHLObjectFactory.getSVNRevision(pegRevision));
            if (mergeSrcURLs != null) {
                String[] stringURLs = new String[mergeSrcURLs.size()];
                int i = 0;
                for (Iterator urls = mergeSrcURLs.iterator(); urls.hasNext();) {
                    SVNURL mergeSrcURL = (SVNURL) urls.next();
                    stringURLs[i++] = mergeSrcURL.toString();
                }
                return stringURLs; 
            }
            return null;
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
    }

    public RevisionRange[] getAvailableMerges(String path, Revision pegRevision, String mergeSource) throws SubversionException {
        SVNDiffClient client = getSVNDiffClient();
        try {
            SVNMergeRangeList rangeList = null;
            if (isURL(path)) {
                rangeList = client.getAvailableMergeInfo(SVNURL.parseURIEncoded(path), 
                        JavaHLObjectFactory.getSVNRevision(pegRevision), 
                        SVNURL.parseURIEncoded(mergeSource)); 
            } else {
                rangeList = client.getAvailableMergeInfo(new File(path).getAbsoluteFile(), 
                        JavaHLObjectFactory.getSVNRevision(pegRevision), 
                        SVNURL.parseURIEncoded(mergeSource)); 
            }
             
            return JavaHLObjectFactory.createRevisionRanges(rangeList);
        } catch (SVNException e) {
            throwException(e);
        }
        return null;
    }
}
