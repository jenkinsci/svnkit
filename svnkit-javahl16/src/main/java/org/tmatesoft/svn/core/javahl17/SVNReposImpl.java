package org.tmatesoft.svn.core.javahl17;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.ISVNRepos;
import org.apache.subversion.javahl.SubversionException;
import org.apache.subversion.javahl.callback.ReposNotifyCallback;
import org.apache.subversion.javahl.types.Depth;
import org.apache.subversion.javahl.types.Lock;
import org.apache.subversion.javahl.types.Revision;
import org.apache.subversion.javahl.types.Version;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAdapter;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNReposImpl {

    private SVNClientImpl client;
    private SVNAdminClient svnAdminClient;

    /**
     * Filesystem in a Berkeley DB
     */
    public static final String BDB = "bdb";
    /**
     * Filesystem in the filesystem
     */
    public static final String FSFS = "fsfs";

    public SVNReposImpl() {
        client = SVNClientImpl.newInstance();
    }

    public void dispose() {
        client.dispose();
        svnAdminClient = null;
    }

    public Version getVersion() {
        return client.getVersion();
    }

    protected SVNAdminClient getAdminClient() {
        if (svnAdminClient == null) {
            svnAdminClient = new SVNAdminClient(SVNWCUtil.createDefaultAuthenticationManager(), SVNWCUtil.createDefaultOptions(true));
        }
        return svnAdminClient;
    }


    public void create(File path, boolean disableFsyncCommit, boolean keepLog, File configPath, String fstype) throws ClientException {
        if (BDB.equalsIgnoreCase(fstype)) {
            notImplementedYet("Only " + FSFS + " type of repositories are supported by " + getVersion().toString());
        }
        try {
            SVNRepositoryFactory.createLocalRepository(path, false, false);
            if (configPath != null) {

            }
        } catch (SVNException e) {
            throwException(e, client);
        }
    }

    public void deltify(File path, Revision start, Revision end) throws ClientException {
        notImplementedYet();
    }

    public void dump(File path, OutputStream dataOut, Revision start, Revision end, boolean incremental, boolean useDeltas, ReposNotifyCallback callback) throws ClientException {
        final OutputStream errorOut = null;
        OutputStream os = dataOut;
        try {
            getAdminClient().setEventHandler(new SVNAdminEventAdapter() {
                public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
                    if (errorOut != null && event.getAction() == SVNAdminEventAction.REVISION_DUMPED) {
                        try {
                            errorOut.write(event.getMessage().getBytes());
                            errorOut.write(client.getOptions().getNativeEOL());
                        } catch (IOException e) {
                        }
                    }
                }
            });
            getAdminClient().doDump(path.getAbsoluteFile(), os, SVNClientImpl.getSVNRevision(start), SVNClientImpl.getSVNRevision(end), incremental, useDeltas);
        } catch (SVNException e) {
            try {
                if (errorOut != null) {
                    errorOut.write(e.getErrorMessage().getFullMessage().getBytes("UTF-8"));
                    errorOut.write(client.getOptions().getNativeEOL());
                }
            } catch (IOException e1) {
                //
            }
            throwException(e, client);
        } finally {
            getAdminClient().setEventHandler(null);
        }
    }

    public void hotcopy(File path, File targetPath, boolean cleanLogs) throws ClientException {
        try {
            getAdminClient().doHotCopy(path.getAbsoluteFile(), targetPath.getAbsoluteFile());
        } catch (SVNException e) {
            throwException(e, client);
        }
    }

    public void listDBLogs(File path, ISVNRepos.MessageReceiver receiver) throws ClientException {
        notImplementedYet("Only " + FSFS + " type of repositories are supported by " + getVersion().toString());
    }

    public void listUnusedDBLogs(File path, ISVNRepos.MessageReceiver receiver) throws ClientException {
        notImplementedYet("Only " + FSFS + " type of repositories are supported by " + getVersion().toString());
    }

    public void load(File path, InputStream dataInput, boolean ignoreUUID, boolean forceUUID, String relativePath, ReposNotifyCallback callback) throws ClientException {
        load(path, dataInput, ignoreUUID, forceUUID, false, false, relativePath, callback);
    }

    public void load(File path, InputStream dataInput, boolean ignoreUUID, boolean forceUUID, boolean usePreCommitHook, boolean usePostCommitHook, String relativePath, ReposNotifyCallback callback) throws ClientException {
        InputStream is = dataInput;
        final OutputStream messageOutput = null;
        try {
            SVNUUIDAction uuidAction = SVNUUIDAction.DEFAULT;
            if (ignoreUUID) {
                uuidAction = SVNUUIDAction.IGNORE_UUID;
            } else if (forceUUID) {
                uuidAction = SVNUUIDAction.FORCE_UUID;
            }
            getAdminClient().setEventHandler(new SVNAdminEventAdapter() {

                private boolean myIsNodeOpened;

                public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
                    if (messageOutput != null) {
                        try {
                            messageOutput.write(getLoadMessage(event).getBytes("UTF-8"));
                        } catch (IOException e) {
                        }
                    }
                }

                protected String getLoadMessage(SVNAdminEvent event) {
                    StringBuffer message = new StringBuffer();
                    if (event.getAction() != SVNAdminEventAction.REVISION_LOAD && myIsNodeOpened) {
                        message.append(" done.");
                        message.append(client.getOptions().getNativeEOL());
                        myIsNodeOpened = false;
                    }
                    if (event.getAction() == SVNAdminEventAction.REVISION_LOADED) {
                        message.append(client.getOptions().getNativeEOL());
                    }
                    message.append(event.getMessage());
                    message.append(client.getOptions().getNativeEOL());
                    if (event.getAction() == SVNAdminEventAction.REVISION_LOADED) {
                        message.append(client.getOptions().getNativeEOL());
                    }
                    myIsNodeOpened = event.getAction() != SVNAdminEventAction.REVISION_LOAD;
                    return message.toString();
                }
            });
            getAdminClient().doLoad(path.getAbsoluteFile(), is, usePreCommitHook, usePostCommitHook, uuidAction, relativePath);
        } catch (SVNException e) {
            if (messageOutput != null) {
                try {
                    messageOutput.write(e.getErrorMessage().getFullMessage().getBytes("UTF-8"));
                    messageOutput.write(client.getOptions().getNativeEOL());
                } catch (IOException e1) {
                }
            }
            throwException(e, client);
        } finally {
            getAdminClient().setEventHandler(null);
        }
    }

    public void lstxns(File path, final ISVNRepos.MessageReceiver receiver) throws ClientException {
        getAdminClient().setEventHandler(new SVNAdminEventAdapter() {
            public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
                if (receiver != null && event.getTxnName() != null) {
                    receiver.receiveMessageLine(event.getTxnName());
                }
            }
        });
        try {
            getAdminClient().doListTransactions(path.getAbsoluteFile());
        } catch (SVNException e) {
            throwException(e, client);
        } finally {
            getAdminClient().setEventHandler(null);
        }
    }

    public long recover(File path, ReposNotifyCallback callback) throws ClientException {
        try {
            File repositoryRoot = path.getAbsoluteFile();
            getAdminClient().doRecover(repositoryRoot);
            return getAdminClient().getYoungestRevision(repositoryRoot);
        } catch (SVNException e) {
            throwException(e, client);
        }
        return -1;

    }

    public void rmtxns(File path, String[] transactions) throws ClientException {
        try {
            getAdminClient().doRemoveTransactions(path.getAbsoluteFile(), transactions);
        } catch (SVNException e) {
            throwException(e, client);
        }
    }

    public void setRevProp(File path, Revision rev, String propName, String propValue, boolean usePreRevPropChangeHook, boolean usePostRevPropChangeHook) throws SubversionException {
        try {
            setRevisionProperty(path, rev, propName, propValue, !usePreRevPropChangeHook, !usePostRevPropChangeHook);
        } catch (SVNException e) {
            throwException(e, client);
        }
    }

    public void verify(File path, Revision start, Revision end, ReposNotifyCallback callback) throws ClientException {
        final OutputStream messageOut = null;
        try {
            getAdminClient().setEventHandler(new SVNAdminEventAdapter() {
                public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
                    if (messageOut != null && event.getAction() == SVNAdminEventAction.REVISION_DUMPED) {
                        try {
                            messageOut.write(event.getMessage().getBytes());
                            messageOut.write(client.getOptions().getNativeEOL());
                        } catch (IOException e) {
                        }
                    }
                }
            });
            getAdminClient().doVerify(path.getAbsoluteFile(), SVNClientImpl.getSVNRevision(start), SVNClientImpl.getSVNRevision(end));
        } catch (SVNException e) {
            try {
                if (messageOut != null) {
                    messageOut.write(e.getErrorMessage().getFullMessage().getBytes("UTF-8"));
                    messageOut.write(client.getOptions().getNativeEOL());
                }
            } catch (IOException e1) {
                //
            }
            throwException(e, client);
        } finally {
            getAdminClient().setEventHandler(null);
        }
    }

    public Set<Lock> lslocks(File path, Depth depth) throws ClientException {
        final Set<Lock> locks = new HashSet<Lock>();
        getAdminClient().setEventHandler(new SVNAdminEventAdapter() {
            public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
                if (event.getAction() == SVNAdminEventAction.LOCK_LISTED) {
                    SVNLock svnLock = event.getLock();
                    Lock lock = SVNClientImpl.getLock(svnLock);
                    locks.add(lock);
                }
            }
        });

        try {
            getAdminClient().doListLocks(path.getAbsoluteFile());
        } catch (SVNException e) {
            throwException(e, client);
        } finally {
            getAdminClient().setEventHandler(null);
        }

        return locks;
    }

    public void rmlocks(File path, String[] locks) throws ClientException {
        try {
            getAdminClient().doRemoveLocks(path.getAbsoluteFile(), locks);
        } catch (SVNException e) {
            throwException(e, client);
        } finally {
            getAdminClient().setEventHandler(null);
        }
    }

    public void upgrade(File path, ReposNotifyCallback callback) throws ClientException {
        notImplementedYet();
    }

    public void pack(File path, ReposNotifyCallback callback) throws ClientException {
        notImplementedYet();
    }

    public void cancelOperation() throws ClientException {
        notImplementedYet();
    }

    private static void setRevisionProperty(File path, Revision rev, String propName, String propValue, boolean bypassPreRevPropChangeHook, boolean bypassPostRevPropChangeHook) throws SVNException {
        SVNRepository repository = SVNRepositoryFactory.create(SVNURL.fromFile(path.getAbsoluteFile()));
        ((FSRepository) repository).setRevisionPropertyValue(SVNClientImpl.getSVNRevision(rev).getNumber(), propName, SVNPropertyValue.create(propValue), bypassPreRevPropChangeHook, bypassPostRevPropChangeHook);
    }

    private void notImplementedYet() throws ClientException {
        notImplementedYet(null);
    }

    private void notImplementedYet(String message) throws ClientException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                message == null ? "Requested SVNAdmin functionality is not yet implemented" : message);
        throwException(new SVNException(err), client);
    }

    public static void throwException(SVNException e, SVNClientImpl svnClient) throws ClientException {
        ClientException ec = ClientException.fromException(e);
        ec.initCause(e);
        svnClient.getDebugLog().logFine(SVNLogType.DEFAULT, ec);
        svnClient.getDebugLog().logFine(SVNLogType.DEFAULT, e);
        throw ec;
    }
}
