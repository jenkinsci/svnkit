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
package org.tmatesoft.svn.core.wc.admin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRecoverer;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryUtil;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.internal.wc.DefaultLoadHandler;
import org.tmatesoft.svn.core.internal.wc.ISVNLoadHandler;
import org.tmatesoft.svn.core.internal.wc.SVNAdminDeltifier;
import org.tmatesoft.svn.core.internal.wc.SVNAdminHelper;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNDumpEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNSynchronizeEditor;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.replicator.SVNRepositoryReplicator;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNBasicClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * The <b>SVNAdminClient</b> class provides methods that brings repository-side functionality
 * and repository synchronizing features.
 * 
 * <p>
 * Repository administrative methods are analogues of the corresponding commands of the native 
 * Subversion 'svnadmin' utility, while repository synchronizing methods are the ones for the
 * 'svnsync' utility. 
 * 
 * <p>
 * Here's a list of the <b>SVNAdminClient</b>'s methods 
 * matched against corresponing commands of the Subversion svnsync and svnadmin command-line utilities:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doInitialize()</td><td>'svnsync initialize'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doSynchronize()</td><td>'svnsync synchronize'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doCopyRevisionProperties()</td><td>'svnsync copy-revprops'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doDump()</td><td>'svnadmin dump'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doListTransactions()</td><td>'svnadmin lstxns'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doLoad()</td><td>'svnadmin load'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doRemoveTransactions()</td><td>'svnadmin rmtxns'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doVerify()</td><td>'svnadmin verify'</td>
 * </tr>
 * </table>
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @since   1.1.0
 */
public class SVNAdminClient extends SVNBasicClient {
    private ISVNLogEntryHandler mySyncHandler;
    private ISVNLoadHandler myLoadHandler;
    private ISVNAdminEventHandler myEventHandler;

    /**
     * Creates a new admin client.
     * 
     * @param authManager   an auth manager
     * @param options       an options driver
     */
    public SVNAdminClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    /**
     * Creates a new admin client.
     * 
     * @param repositoryPool a repository pool 
     * @param options        an options driver
     */
    public SVNAdminClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    /**
     * Sets a replication handler that will receive a log entry object 
     * per each replayed revision.
     * 
     * <p>
     * Log entries dispatched to the handler may not contain changed paths and 
     * committed log message until this features are implemented in future releases. 
     * 
     * @param handler a replay handler
     */
    public void setReplayHandler(ISVNLogEntryHandler handler) {
        mySyncHandler = handler;
    }

    /**
     * Sets an event handler for this object. 
     * {@link ISVNAdminEventHandler} should be provided to <b>SVNAdminClent</b>
     * via this method also.
     * 
     * @param handler an event handler
     */
    public void setEventHandler(ISVNEventHandler handler) {
        super.setEventHandler(handler);
        if (handler instanceof ISVNAdminEventHandler) {
            myEventHandler = (ISVNAdminEventHandler) handler;
        }
    }

    /**
     * Creates an FSFS-type repository.
     * 
     * This implementation uses {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory#createLocalRepository(File, String, boolean, boolean)}}.
     * <p>
     * If <code>uuid</code> is <span class="javakeyword">null</span> a new uuid will be generated, otherwise 
     * the specified will be used.
     * 
     * <p>
     * If <code>enableRevisionProperties</code> is <span class="javakeyword">true</span>, an empty 
     * pre-revprop-change hook will be placed into the repository /hooks subdir. This enables changes to 
     * revision properties of the newly created repository. 
     * 
     * <p>
     * If <code>force</code> is <span class="javakeyword">true</span> and <code>path</code> already 
     * exists, deletes that path and creates a repository in its place.
     *  
     * @param  path                        a repository root dir path
     * @param  uuid                        a repository uuid
     * @param  enableRevisionProperties    enables/disables changes to revision properties
     * @param  force                       forces operation to run
     * @return                             a local URL (file:///) of a newly created repository
     * @throws SVNException
     * @see                                #doCreateRepository(File, String, boolean, boolean, boolean)
     * @since                              1.1.0 
     */
    public SVNURL doCreateRepository(File path, String uuid, boolean enableRevisionProperties, boolean force) throws SVNException {
        return SVNRepositoryFactory.createLocalRepository(path, uuid, enableRevisionProperties, force);
    }

    /**
     * Creates an FSFS-type repository.
     * 
     * This implementation uses {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory#createLocalRepository(File, String, boolean, boolean)}}.
     * <p>
     * If <code>uuid</code> is <span class="javakeyword">null</span> a new uuid will be generated, otherwise 
     * the specified will be used.
     * 
     * <p>
     * If <code>enableRevisionProperties</code> is <span class="javakeyword">true</span>, an empty 
     * pre-revprop-change hook will be placed into the repository /hooks subdir. This enables changes to 
     * revision properties of the newly created repository. 
     * 
     * <p>
     * If <code>force</code> is <span class="javakeyword">true</span> and <code>path</code> already 
     * exists, deletes that path and creates a repository in its place.
     * 
     * <p>
     * Set <code>pre14Compatible</code> to <span class="javakeyword">true</span> if you want a new repository 
     * to be compatible with pre-1.4 servers.
     * 
     * @param  path                        a repository root dir path
     * @param  uuid                        a repository uuid
     * @param  enableRevisionProperties    enables/disables changes to revision properties
     * @param  force                       forces operation to run
     * @param  pre14Compatible             <span class="javakeyword">true</span> to 
     *                                     create a repository with pre-1.4 format
     * @return                             a local URL (file:///) of a newly created repository
     * @throws SVNException
     * @since                              1.1.1 
     */
    public SVNURL doCreateRepository(File path, String uuid, boolean enableRevisionProperties, boolean force, boolean pre14Compatible) throws SVNException {
        return SVNRepositoryFactory.createLocalRepository(path, uuid, enableRevisionProperties, force, pre14Compatible);
    }

    /**
     * Copies revision properties from the source repository that the destination one is synchronized with 
     * to the given revision of the destination repository itself.
     * 
     * <p>
     * This method is equivalent to the command 'copy-revprops' of the native Subversion <i>svnsync</i> utility. 
     * Note that the destination repository given as <code>toURL</code> must be synchronized with a source 
     * repository. Please, see {@link #doInitialize(SVNURL, SVNURL)}} how to initialize such a synchronization.  
     * 
     * @param  toURL          a url to the destination repository which must be synchronized
     *                        with another repository 
     * @param  revision       a particular revision of the source repository to copy revision properties
     *                        from 
     * @throws SVNException   
     * @since                 1.1, new in Subversion 1.4
     */
    public void doCopyRevisionProperties(SVNURL toURL, long revision) throws SVNException {
        SVNRepository toRepos = createRepository(toURL, true);
        checkIfRepositoryIsAtRoot(toRepos, toURL);

        SVNException error = null;
        SVNException error2 = null;
        lock(toRepos);
        try {
            SessionInfo info = openSourceRepository(toRepos);
            if (revision > info.myLastMergedRevision) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot copy revprops for a revision that has not been synchronized yet");
                SVNErrorManager.error(err);
            }
            copyRevisionProperties(info.myRepository, toRepos, revision, false);
        } catch (SVNException svne) {
            error = svne;
        } finally {
            try {
                unlock(toRepos);
            } catch (SVNException svne) {
                error2 = svne;
            }
        }

        if (error != null) {
            throw error;
        } else if (error2 != null) {
            throw error2;
        }
    }

    /**
     * Initializes synchronization between source and target repositories.
     * 
     * <p>
     * This method is equivalent to the command 'initialize' ('init') of the native Subversion <i>svnsync</i> 
     * utility. Initialization places information of a source repository to a destination one (setting special 
     * revision properties in revision 0) as well as copies all revision props from revision 0 of the source 
     * repository to revision 0 of the destination one.   
     * 
     * @param  fromURL         a source repository url
     * @param  toURL           a destination repository url
     * @throws SVNException   
     * @since                  1.1, new in Subversion 1.4
     */
    public void doInitialize(SVNURL fromURL, SVNURL toURL) throws SVNException {
        SVNRepository toRepos = createRepository(toURL, true);
        checkIfRepositoryIsAtRoot(toRepos, toURL);

        SVNException error = null;
        SVNException error2 = null;
        lock(toRepos);
        try {
            long latestRevision = toRepos.getLatestRevision();
            if (latestRevision != 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot initialize a repository with content in it");
                SVNErrorManager.error(err);
            }

            SVNPropertyValue fromURLProp = toRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL);
            if (fromURLProp != null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Destination repository is already synchronizing from ''{0}''", fromURLProp);
                SVNErrorManager.error(err);
            }

            // TODO close session.
            SVNRepository fromRepos = createRepository(fromURL, false);
            checkIfRepositoryIsAtRoot(fromRepos, fromURL);

            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL, SVNPropertyValue.create(fromURL.toDecodedString()));
            String uuid = fromRepos.getRepositoryUUID(true);
            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.FROM_UUID, SVNPropertyValue.create(uuid));
            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, SVNPropertyValue.create("0"));

            copyRevisionProperties(fromRepos, toRepos, 0, false);
        } catch (SVNException svne) {
            error = svne;
        } finally {
            try {
                unlock(toRepos);
            } catch (SVNException svne) {
                error2 = svne;
            }
        }

        if (error != null) {
            throw error;
        } else if (error2 != null) {
            throw error2;
        }
    }

    /**
     * Completely synchronizes two repositories.
     * 
     * <p>
     * This method initializes the destination repository and then copies all revision
     * changes (including revision properties) 
     * from the given source repository to the destination one. First it 
     * tries to use synchronization features similar to the native Subversion 
     * 'svnsync' capabilities. But if a server does not support 
     * <code>replay</code> functionality, SVNKit uses its own repository 
     * replication feature (see {@link org.tmatesoft.svn.core.replicator.SVNRepositoryReplicator}})
     * 
     * @param  fromURL        a url of a repository to copy from     
     * @param  toURL          a destination repository url
     * @throws SVNException
     * @since                 1.1
     */
    public void doCompleteSynchronize(SVNURL fromURL, SVNURL toURL) throws SVNException {
        try {
            doInitialize(fromURL, toURL);
            doSynchronize(toURL);
            return;
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.RA_NOT_IMPLEMENTED) {
                throw svne;
            }
        }

        SVNRepositoryReplicator replicator = SVNRepositoryReplicator.newInstance();
        SVNRepository fromRepos = createRepository(fromURL, true);
        // TODO close session
        SVNRepository toRepos = createRepository(toURL, false);
        replicator.replicateRepository(fromRepos, toRepos, 1, -1);
    }

    /**
     * Synchronizes the repository at the given url.
     * 
     * <p>
     * Synchronization means copying revision changes and revision properties from the source 
     * repository (that the destination one is synchronized with) to the destination one starting at 
     * the last merged revision. This method is equivalent to the command 'synchronize' ('sync') of 
     * the native Subversion <i>svnsync</i> utility. 
     * 
     * @param  toURL          a destination repository url
     * @throws SVNException
     * @since                 1.1, new in Subversion 1.4
     */
    public void doSynchronize(SVNURL toURL) throws SVNException {
        SVNRepository toRepos = createRepository(toURL, true);
        checkIfRepositoryIsAtRoot(toRepos, toURL);

        SVNException error = null;
        SVNException error2 = null;

        lock(toRepos);
        try {
            SessionInfo info = openSourceRepository(toRepos);
            SVNRepository fromRepos = info.myRepository;
            long lastMergedRevision = info.myLastMergedRevision;
            SVNPropertyValue currentlyCopying = toRepos.getRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING);
            long toLatestRevision = toRepos.getLatestRevision();

            if (currentlyCopying != null) {
                long copyingRev = Long.parseLong(currentlyCopying.getString());
                if (copyingRev < lastMergedRevision || copyingRev > lastMergedRevision + 1 || (toLatestRevision != lastMergedRevision && toLatestRevision != copyingRev)) {
                    SVNErrorMessage err = SVNErrorMessage
                            .create(
                                    SVNErrorCode.IO_ERROR,
                                    "Revision being currently copied ({0}), last merged revision ({1}), and destination HEAD ({2}) are inconsistent; have you committed to the destination without using svnsync?",
                                    new Long[] {
                                            new Long(copyingRev), new Long(lastMergedRevision), new Long(toLatestRevision)
                                    });
                    SVNErrorManager.error(err);
                } else if (copyingRev == toLatestRevision) {
                    if (copyingRev > lastMergedRevision) {
                        copyRevisionProperties(fromRepos, toRepos, toLatestRevision, true);
                        lastMergedRevision = copyingRev;
                    }
                    toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, SVNPropertyValue.create(SVNProperty.toString(lastMergedRevision)));
                    toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, null);
                } 
            } else {
                if (toLatestRevision != lastMergedRevision) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Destination HEAD ({0}) is not the last merged revision ({1}); have you committed to the destination without using svnsync?", new Long[] {new Long(toLatestRevision), new Long(lastMergedRevision)});
                    SVNErrorManager.error(err);
                }
            }

            long fromLatestRevision = fromRepos.getLatestRevision();
            if (fromLatestRevision < lastMergedRevision) {
                return;
            }

            for (long currentRev = lastMergedRevision + 1; currentRev <= fromLatestRevision; currentRev++) {
                toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, SVNPropertyValue.create(SVNProperty.toString(currentRev)));
                SVNSynchronizeEditor syncEditor = new SVNSynchronizeEditor(toRepos, mySyncHandler, currentRev - 1);
                ISVNEditor cancellableEditor = SVNCancellableEditor.newInstance(syncEditor, this, getDebugLog());
                try {
                    fromRepos.replay(0, currentRev, true, cancellableEditor);
                } catch (SVNException e) {
                    try {
                        cancellableEditor.abortEdit();
                    } catch (SVNException abortError) {}
                    throw e;
                }
                cancellableEditor.closeEdit();
                if (syncEditor.getCommitInfo().getNewRevision() != currentRev) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Commit created rev {0} but should have created {1}", new Long[] {
                            new Long(syncEditor.getCommitInfo().getNewRevision()), new Long(currentRev)
                    });
                    SVNErrorManager.error(err);
                }
                copyRevisionProperties(fromRepos, toRepos, currentRev, true);
                toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, SVNPropertyValue.create(SVNProperty.toString(currentRev)));
                toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, null);
            }
        } catch (SVNException svne) {
            error = svne;
        } finally {
            try {
                unlock(toRepos);
            } catch (SVNException svne) {
                error2 = svne;
            }
        }

        if (error != null) {
            throw error;
        } else if (error2 != null) {
            throw error2;
        }
    }

    public void doListLocks(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        File digestFile = fsfs.getDigestFileFromRepositoryPath("/");
        ISVNLockHandler handler = new ISVNLockHandler() {
            public void handleLock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
                checkCancelled();
                if (myEventHandler != null) {
                    SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.LOCK_LISTED, lock, error, null);
                    myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                }
                
            }
            public void handleUnlock(String path, SVNLock lock, SVNErrorMessage error) throws SVNException {
            }
        };
        fsfs.walkDigestFiles(digestFile, handler, false);
    }

    public void doRemoveLocks(File repositoryRoot, String[] paths) throws SVNException {
        if (paths == null) {
            return;
        }
        
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            if (path == null) {
                continue;
            }
            checkCancelled();
            
            SVNLock lock = null;
            try {
                lock = fsfs.getLockHelper(path, false);
                if (lock == null) {
                    if (myEventHandler != null) {
                        SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.NOT_LOCKED, lock, null, "Path '" + path + "' isn't locked.");
                        myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                    }
                    continue;
                }
                
                fsfs.unlockPath(path, lock.getID(), null, true, false);
                if (myEventHandler != null) {
                    SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.UNLOCKED, lock, null, "Removed lock on '" + path + "'.");
                    myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                }
            } catch (SVNException svne) {
                if (myEventHandler != null) {
                    SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.UNLOCK_FAILED, lock, svne.getErrorMessage(), "svnadmin: " + svne.getErrorMessage().getFullMessage());
                    myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                }
            }
        }
    }
    
    /**
     * Lists all uncommitted transactions.
     * On each uncommetted transaction found this method fires an {@link SVNAdminEvent} 
     * with action set to {@link SVNAdminEventAction#TRANSACTION_LISTED} to the registered 
     * {@link ISVNAdminEventHandler} (if any). To register your <b>ISVNAdminEventHandler</b> 
     * pass it to {@link #setEventHandler(ISVNEventHandler)}. For this operation the following 
     * information can be retrieved out of {@link SVNAdminEvent}:
     * <ul>
     * <li>transaction name - use {@link SVNAdminEvent#getTxnName() SVNAdminEvent.getTxnName()} to get it</li>
     * <li>transaction directory - use {@link SVNAdminEvent#getTxnDir() SVNAdminEvent.getTxnDir()} to get it</li>
     * </ul>
     * 
     * @param  repositoryRoot   a repository root directory path
     * @throws SVNException
     * @since                   1.1.1
     */
    public void doListTransactions(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        Map txns = fsfs.listTransactions();

        for(Iterator names = txns.keySet().iterator(); names.hasNext();) {
            String txnName = (String) names.next();
            File txnDir = (File) txns.get(txnName);
            SVNDebugLog.getDefaultLog().info(txnName + "\n");            
            if (myEventHandler != null) {
                SVNAdminEvent event = new SVNAdminEvent(txnName, txnDir, SVNAdminEventAction.TRANSACTION_LISTED);
                myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
            }
        }
    }
    
    /**
     * Removes the specified outstanding transactions from a repository.
     * On each transaction removed this method fires an {@link SVNAdminEvent} 
     * with action set to {@link SVNAdminEventAction#TRANSACTION_REMOVED} to the registered 
     * {@link ISVNAdminEventHandler} (if any). To register your <b>ISVNAdminEventHandler</b> 
     * pass it to {@link #setEventHandler(ISVNEventHandler)}. For this operation the following 
     * information can be retrieved out of {@link SVNAdminEvent}:
     * <ul>
     * <li>transaction name - use {@link SVNAdminEvent#getTxnName() SVNAdminEvent.getTxnName()} to get it</li>
     * <li>transaction directory - use {@link SVNAdminEvent#getTxnDir() SVNAdminEvent.getTxnDir()} to get it</li>
     * </ul>
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  transactions     an array with transaction names
     * @throws SVNException
     * @since                   1.1.1
     */
    public void doRemoveTransactions(File repositoryRoot, String[] transactions) throws SVNException {
        if (transactions == null) {
            return;
        }

        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        for (int i = 0; i < transactions.length; i++) {
            String txnName = transactions[i];
            fsfs.openTxn(txnName);
            fsfs.purgeTxn(txnName);
            SVNDebugLog.getDefaultLog().info("Transaction '" + txnName + "' removed.\n");
            if (myEventHandler != null) {
                SVNAdminEvent event = new SVNAdminEvent(txnName, fsfs.getTransactionDir(txnName), SVNAdminEventAction.TRANSACTION_REMOVED);
                myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
            }
        }
    }

    /**
     * Verifies the data stored in the repository. This method uses the dump implementation 
     * (non incremental, beginning with revision 0, ending at the latest one) 
     * passing a dummy output stream to it. This allows to check the integrity of the 
     * repository data. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @throws SVNException     verification failed - a repository may be corrupted
     * @since                   1.1.1
     */
    public void doVerify(File repositoryRoot) throws SVNException {
        doVerify(repositoryRoot, SVNRevision.create(0), SVNRevision.HEAD);
    }

    public void doVerify(File repositoryRoot, SVNRevision startRevision, SVNRevision endRevision) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        long startRev = startRevision.getNumber();
        long endRev = endRevision.getNumber();
        if (startRev < 0) {
            startRev = 0;
        }
        if (endRev < 0) {
            endRev = fsfs.getYoungestRevision();
        }
        try {
            dump(fsfs, SVNFileUtil.DUMMY_OUT, startRev, endRev, false, false);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }
    
    /**
     * Dumps contents of the repository to the provided output stream in a 
     * 'dumpfile' portable format.
     * 
     * <p>
     * On each revision dumped this method fires an {@link SVNAdminEvent} 
     * with action set to {@link SVNAdminEventAction#REVISION_DUMPED} to the registered 
     * {@link ISVNAdminEventHandler} (if any). To register your <b>ISVNAdminEventHandler</b> 
     * pass it to {@link #setEventHandler(ISVNEventHandler)}. For this operation the following 
     * information can be retrieved out of {@link SVNAdminEvent}:
     * <ul>
     * <li>dumped revision - use {@link SVNAdminEvent#getRevision() SVNAdminEvent.getRevision()} to get it</li>
     * </ul>
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  dumpStream       an output stream to write dumped contents to
     * @param  startRevision    the first revision to start dumping from
     * @param  endRevision      the last revision to end dumping at
     * @param  isIncremental    if <span class="javakeyword">true</span> 
     *                          then the first revision dumped will be a 
     *                          diff against the previous revision; otherwise 
     *                          the first revision is a fulltext. 
     * @param  useDeltas        if <span class="javakeyword">true</span> 
     *                          deltas will be written instead of fulltexts
     * @throws SVNException
     * @since                   1.1.1
     */
    public void doDump(File repositoryRoot, OutputStream dumpStream, SVNRevision startRevision, SVNRevision endRevision, boolean isIncremental, boolean useDeltas) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        long youngestRevision = fsfs.getYoungestRevision();
        
        long lowerR = SVNAdminHelper.getRevisionNumber(startRevision, youngestRevision, fsfs);
        long upperR = SVNAdminHelper.getRevisionNumber(endRevision, youngestRevision, fsfs);
        
        if (!SVNRevision.isValidRevisionNumber(lowerR)) {
            lowerR = 0;
            upperR = youngestRevision;
        } else if (!SVNRevision.isValidRevisionNumber(upperR)) {
            upperR = lowerR; 
        }
        
        if (lowerR > upperR) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "First revision cannot be higher than second");
            SVNErrorManager.error(err);
        }
        
        try {
            dump(fsfs, dumpStream, lowerR, upperR, isIncremental, useDeltas);
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getMessage());
            SVNErrorManager.error(err, ioe);
        }
    }
    
    /**
     * Reads the provided dump stream committing new revisions to a repository.
     * 
     * <p>
     * On each revision loaded this method fires an {@link SVNAdminEvent} 
     * with action set to {@link SVNAdminEventAction#REVISION_LOADED} to the registered 
     * {@link ISVNAdminEventHandler} (if any). To register your <b>ISVNAdminEventHandler</b> 
     * pass it to {@link #setEventHandler(ISVNEventHandler)}. For this operation the following 
     * information can be retrieved out of {@link SVNAdminEvent}:
     * <ul>
     * <li>original revision - use {@link SVNAdminEvent#getOriginalRevision() SVNAdminEvent.getOriginalRevision()} to get it</li>
     * <li>new committed revision - use {@link SVNAdminEvent#getRevision() SVNAdminEvent.getRevision()} to get it</li>
     * </ul>
     * 
     * <p>
     * A call to this method is equivalent to 
     * <code>doLoad(repositoryRoot, dumpStream, false, false, SVNUUIDAction.DEFAULT, null)</code>.
     * 
     * @param  repositoryRoot   the root directory path of the repository where 
     *                          new revisions will be committed
     * @param  dumpStream       stream with dumped contents of a repository
     * @throws SVNException
     * @see                     #doLoad(File, InputStream, boolean, boolean, SVNUUIDAction, String)                     
     * @since                   1.1.1
     */
    public void doLoad(File repositoryRoot, InputStream dumpStream) throws SVNException {
        doLoad(repositoryRoot, dumpStream, false, false, SVNUUIDAction.DEFAULT, null);
    }

    /**
     * Reads the provided dump stream committing new revisions to a repository.
     * 
     * <p>
     * On each revision loaded this method fires an {@link SVNAdminEvent} 
     * with action set to {@link SVNAdminEventAction#REVISION_LOADED} to the registered 
     * {@link ISVNAdminEventHandler} (if any). To register your <b>ISVNAdminEventHandler</b> 
     * pass it to {@link #setEventHandler(ISVNEventHandler)}. For this operation the following 
     * information can be retrieved out of {@link SVNAdminEvent}:
     * <ul>
     * <li>original revision - use {@link SVNAdminEvent#getOriginalRevision() SVNAdminEvent.getOriginalRevision()} to get it</li>
     * <li>new committed revision - use {@link SVNAdminEvent#getRevision() SVNAdminEvent.getRevision()} to get it</li>
     * </ul>
     * 
     * @param  repositoryRoot    the root directory path of the repository where 
     *                           new revisions will be committed
     * @param  dumpStream        stream with dumped contents of a repository
     * @param  usePreCommitHook  if <span class="javakeyword">true</span> 
     *                           then calls a pre-commit hook before committing 
     * @param  usePostCommitHook if <span class="javakeyword">true</span> 
     *                           then calls a post-commit hook after committing
     * @param  uuidAction        one of the three possible ways to treat uuids 
     * @param  parentDir         if not <span class="javakeyword">null</span> 
     *                           then loads at this directory in the repository
     * @throws SVNException
     * @since                       1.1.1
     */
    public void doLoad(File repositoryRoot, InputStream dumpStream, boolean usePreCommitHook, boolean usePostCommitHook, SVNUUIDAction uuidAction, String parentDir) throws SVNException {
        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
        ISVNLoadHandler handler = getLoadHandler(repositoryRoot, usePreCommitHook, usePostCommitHook, uuidAction, parentDir, decoder);

        String line = null;
        int version = -1;
        StringBuffer buffer = new StringBuffer();
        try {
            line = SVNFileUtil.readLineFromStream(dumpStream, buffer, decoder);
            if (line == null) {
                SVNAdminHelper.generateIncompleteDataError();
            }

            //parse format
            if (!line.startsWith(SVNAdminHelper.DUMPFILE_MAGIC_HEADER + ":")) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpfile header");
                SVNErrorManager.error(err);
            }
            
            try {
                line = line.substring(SVNAdminHelper.DUMPFILE_MAGIC_HEADER.length() + 1);
                line = line.trim();
                version = Integer.parseInt(line);
                if (version > SVNAdminHelper.DUMPFILE_FORMAT_VERSION) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Unsupported dumpfile version: {0}", new Integer(version));
                    SVNErrorManager.error(err);
                }
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpfile header");
                SVNErrorManager.error(err, nfe);
            }
        
            while (true) {
                checkCancelled();
                boolean foundNode = false;
            
                //skip empty lines
                buffer.setLength(0);
                line = SVNFileUtil.readLineFromStream(dumpStream, buffer, decoder);
                if (line == null) {
                    if (buffer.length() > 0) {
                        SVNAdminHelper.generateIncompleteDataError();
                    } else {
                        break;
                    }
                } 

                if (line.length() == 0 || Character.isWhitespace(line.charAt(0))) {
                    continue;
                }
            
                Map headers = readHeaderBlock(dumpStream, line, decoder);
                if (headers.containsKey(SVNAdminHelper.DUMPFILE_REVISION_NUMBER)) {
                    handler.closeRevision();
                    handler.openRevision(headers);
                } else if (headers.containsKey(SVNAdminHelper.DUMPFILE_NODE_PATH)) {
                    handler.openNode(headers);
                    foundNode = true;
                } else if (headers.containsKey(SVNAdminHelper.DUMPFILE_UUID)) {
                    String uuid = (String) headers.get(SVNAdminHelper.DUMPFILE_UUID);
                    handler.parseUUID(uuid);
                } else if (headers.containsKey(SVNAdminHelper.DUMPFILE_MAGIC_HEADER)) {
                    try {
                        version = Integer.parseInt((String) headers.get(SVNAdminHelper.DUMPFILE_MAGIC_HEADER));    
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpfile header");
                        SVNErrorManager.error(err, nfe);
                    }
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Unrecognized record type in stream");
                    SVNErrorManager.error(err);
                }
                
                String contentLength = (String) headers.get(SVNAdminHelper.DUMPFILE_CONTENT_LENGTH);
                String propContentLength = (String) headers.get(SVNAdminHelper.DUMPFILE_PROP_CONTENT_LENGTH);
                String textContentLength = (String) headers.get(SVNAdminHelper.DUMPFILE_TEXT_CONTENT_LENGTH);
                
                boolean isOldVersion = version == 1 && contentLength != null && propContentLength == null && textContentLength == null;
                int actualPropLength = 0;
                if (propContentLength != null || isOldVersion) {
                    String delta = (String) headers.get(SVNAdminHelper.DUMPFILE_PROP_DELTA);
                    boolean isDelta = delta != null && "true".equals(delta);
                    
                    if (foundNode && !isDelta) {
                        handler.removeNodeProperties();
                    }
                    
                    int length = 0;
                    try {
                        length = Integer.parseInt(propContentLength != null ? propContentLength : contentLength);
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpfile header: can't parse property block length header");
                        SVNErrorManager.error(err, nfe);
                    }
                    actualPropLength += handler.parsePropertyBlock(dumpStream, length, foundNode);
                }
                
                if (textContentLength != null) {
                    String delta = (String) headers.get(SVNAdminHelper.DUMPFILE_TEXT_DELTA);
                    boolean isDelta = delta != null && "true".equals(delta);
                    int length = 0;
                    try {
                        length = Integer.parseInt(textContentLength);
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpfile header: can't parse text block length header");
                        SVNErrorManager.error(err, nfe);
                    }
                    handler.parseTextBlock(dumpStream, length, isDelta);
                } else if (isOldVersion) {
                    int length = 0;
                    try {
                        length = Integer.parseInt(contentLength);
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpfile header: can't parse content length header");
                        SVNErrorManager.error(err, nfe);
                    }
                    
                    length -= actualPropLength;
                    
                    if (length > 0 || SVNNodeKind.parseKind((String)headers.get(SVNAdminHelper.DUMPFILE_NODE_KIND)) == SVNNodeKind.FILE) {
                        handler.parseTextBlock(dumpStream, length, false);
                    }
                }
                
                if (contentLength != null && !isOldVersion) {
                    int remaining = 0;
                    try {
                        remaining = Integer.parseInt(contentLength);
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpfile header: can't parse content length header");
                        SVNErrorManager.error(err, nfe);
                    }

                    int propertyContentLength = 0;
                    if (propContentLength != null) {
                        try {
                            propertyContentLength = Integer.parseInt(propContentLength);
                        } catch (NumberFormatException nfe) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpfile header: can't parse property block length header");
                            SVNErrorManager.error(err, nfe);
                        }
                    }
                    remaining -= propertyContentLength; 

                    int txtContentLength = 0;
                    if (textContentLength != null) {
                        try {
                            txtContentLength = Integer.parseInt(textContentLength);
                        } catch (NumberFormatException nfe) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpfile header: can't parse text block length header");
                            SVNErrorManager.error(err, nfe);
                        }
                    }
                    remaining -= txtContentLength; 
                    
                    if (remaining < 0) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Sum of subblock sizes larger than total block content length");
                        SVNErrorManager.error(err);
                    }
                    
                    byte buf[] = new byte[SVNFileUtil.STREAM_CHUNK_SIZE];
                    while (remaining > 0) {
                        int numToRead = remaining >= SVNFileUtil.STREAM_CHUNK_SIZE ? SVNFileUtil.STREAM_CHUNK_SIZE : remaining;
                        int numRead = dumpStream.read(buf, 0, numToRead);
                        
                        remaining -= numRead;
                        if (numRead != numToRead) {
                            SVNAdminHelper.generateIncompleteDataError();
                        }
                    }
                }
                
                if (foundNode) {
                    handler.closeNode();
                    foundNode = false;
                }
            }

            handler.closeRevision();
            
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }

    public void doRecover(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepositoryForRecovery(repositoryRoot);
        if (myEventHandler != null) {
            SVNAdminEvent event = new SVNAdminEvent(SVNAdminEventAction.RECOVERY_STARTED);
            myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
        }
        FSRecoverer recoverer = new FSRecoverer(fsfs, this);
        recoverer.runRecovery();
    }
    
    public long getYoungestRevision(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        return fsfs.getYoungestRevision();
    }
    
    private void dump(FSFS fsfs, OutputStream dumpStream, long start, long end, boolean isIncremental, boolean useDeltas) throws SVNException, IOException {
        boolean isDumping = dumpStream != null && dumpStream != SVNFileUtil.DUMMY_OUT;
        long youngestRevision = fsfs.getYoungestRevision();
        SVNAdminDeltifier deltifier = new SVNAdminDeltifier(fsfs, SVNDepth.INFINITY, 
                false, false, false, null);
            
        if (!SVNRevision.isValidRevisionNumber(start)) {
            start = 0;
        }
        
        if (!SVNRevision.isValidRevisionNumber(end)) {
            end = youngestRevision;
        }
        
        if (dumpStream == null) {
            dumpStream = SVNFileUtil.DUMMY_OUT;
        }
        
        if (start > end) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, "Start revision {0} is greater than end revision {1}", new Object[]{new Long(start), new Long(end)});
            SVNErrorManager.error(err);
        }
        
        if (end > youngestRevision) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_ARGS, "End revision {0} is invalid (youngest revision is {1})", new Object[]{new Long(end), new Long(youngestRevision)});
            SVNErrorManager.error(err);
        }
        
        if (start == 0 && isIncremental) {
            isIncremental = false;
        }
        
        String uuid = fsfs.getUUID();
        int version = SVNAdminHelper.DUMPFILE_FORMAT_VERSION;
        
        if (!useDeltas) {
            //for compatibility with SVN 1.0.x
            version--;
        }
        
        writeDumpData(dumpStream, SVNAdminHelper.DUMPFILE_MAGIC_HEADER + ": " + version + "\n\n");
        writeDumpData(dumpStream, SVNAdminHelper.DUMPFILE_UUID + ": " + uuid + "\n\n");

        for (long i = start; i <= end; i++) {
            long fromRev, toRev;
                
            checkCancelled();

            if (i == start && !isIncremental) {
                if (i == 0) {
                    writeRevisionRecord(dumpStream, fsfs, 0);
                    toRev = 0;
                    String message = (isDumping ? "* Dumped" : "* Verified") + " revision " + toRev + ".";
                    if (myEventHandler != null) {
                        SVNAdminEvent event = new SVNAdminEvent(toRev, SVNAdminEventAction.REVISION_DUMPED, message);
                        myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
                    }
                    continue;
                }
                
                fromRev = 0;
                toRev = i;
            } else {
                fromRev = i - 1;
                toRev = i;
            }
            
            writeRevisionRecord(dumpStream, fsfs, toRev);
            boolean useDeltasForRevision = useDeltas && (isIncremental || i != start);
            FSRevisionRoot toRoot = fsfs.createRevisionRoot(toRev);
            ISVNEditor dumpEditor = new SVNDumpEditor(fsfs, toRoot, toRev, start, "/", dumpStream, useDeltasForRevision);

            if (i == start && !isIncremental) {
                FSRevisionRoot fromRoot = fsfs.createRevisionRoot(fromRev);
                deltifier.setEditor(dumpEditor);
                deltifier.deltifyDir(fromRoot, "/", "", toRoot, "/");
            } else {
                FSRepositoryUtil.replay(fsfs, toRoot, "", -1, false, dumpEditor);
            }
            String message = (isDumping ? "* Dumped" : "* Verified") + " revision " + toRev + ".";
            if (myEventHandler != null) {
                SVNAdminEvent event = new SVNAdminEvent(toRev, SVNAdminEventAction.REVISION_DUMPED, message);
                myEventHandler.handleAdminEvent(event, ISVNEventHandler.UNKNOWN);
            }
        }
    }
    
    private void writeRevisionRecord(OutputStream dumpStream, FSFS fsfs, long revision) throws SVNException, IOException {
        SVNProperties revProps = fsfs.getRevisionProperties(revision);
        
        String revisionDate = revProps.getStringValue(SVNRevisionProperty.DATE);
        if (revisionDate != null) {
            SVNDate date = SVNDate.parseDate(revisionDate);
            revProps.put(SVNRevisionProperty.DATE, date.format());
        }
        
        ByteArrayOutputStream encodedProps = new ByteArrayOutputStream();
        SVNAdminHelper.writeProperties(revProps, null, encodedProps);
        
        writeDumpData(dumpStream, SVNAdminHelper.DUMPFILE_REVISION_NUMBER + ": " + revision + "\n");
        String propContents = new String(encodedProps.toByteArray(), "UTF-8");
        writeDumpData(dumpStream, SVNAdminHelper.DUMPFILE_PROP_CONTENT_LENGTH + ": " + propContents.length() + "\n");
        writeDumpData(dumpStream, SVNAdminHelper.DUMPFILE_CONTENT_LENGTH + ": " + propContents.length() + "\n\n");
        writeDumpData(dumpStream, propContents);
        dumpStream.write('\n');
    }
    
    private void writeDumpData(OutputStream out, String data) throws IOException {
        out.write(data.getBytes("UTF-8"));
    }
    
    private ISVNLoadHandler getLoadHandler(File repositoryRoot, boolean usePreCommitHook, boolean usePostCommitHook, SVNUUIDAction uuidAction, String parentDir, CharsetDecoder decoder) throws SVNException {
        if (myLoadHandler == null) {
            FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
            DefaultLoadHandler handler = new DefaultLoadHandler(usePreCommitHook, usePostCommitHook, uuidAction, 
                    parentDir, myEventHandler, decoder);
            handler.setFSFS(fsfs);
            myLoadHandler = handler;
        } else {
            myLoadHandler.setUsePreCommitHook(usePreCommitHook);
            myLoadHandler.setUsePostCommitHook(usePostCommitHook);
            myLoadHandler.setUUIDAction(uuidAction);
            myLoadHandler.setParentDir(parentDir);
        }
        
        return myLoadHandler;
    }


    private Map readHeaderBlock(InputStream dumpStream, String firstHeader, CharsetDecoder decoder) throws SVNException, IOException {
        Map headers = new SVNHashMap();
        StringBuffer buffer = new StringBuffer();
    
        while (true) {
            String header = null;
            buffer.setLength(0);
            if (firstHeader != null) {
                header = firstHeader;
                firstHeader = null;
            } else {
                header = SVNFileUtil.readLineFromStream(dumpStream, buffer, decoder);
                if (header == null && buffer.length() > 0) {
                    SVNAdminHelper.generateIncompleteDataError();
                } else if (buffer.length() == 0) {
                    break;
                }
            }
        
            int colonInd = header.indexOf(':');
            if (colonInd == -1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Dump stream contains a malformed header (with no '':'') at ''{0}''", header.length() > 20 ? header.substring(0, 19) : header);
                SVNErrorManager.error(err);
            }
        
            String name = header.substring(0, colonInd);
            if (colonInd + 2 > header.length()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Dump stream contains a malformed header (with no value) at ''{0}''", header.length() > 20 ? header.substring(0, 19) : header);
                SVNErrorManager.error(err);
            }
            String value = header.substring(colonInd + 2);
            headers.put(name, value);
        }
    
        return headers;
    }

    private void copyRevisionProperties(SVNRepository fromRepository, SVNRepository toRepository, long revision, boolean sync) throws SVNException {
        SVNProperties existingRevProps = null;
        if (sync) {
            existingRevProps = toRepository.getRevisionProperties(revision, null);
        }
        
        boolean sawSyncProperties = false;
        SVNProperties revProps = fromRepository.getRevisionProperties(revision, null);
        for (Iterator propNames = revProps.nameSet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            SVNPropertyValue propValue = revProps.getSVNPropertyValue(propName);
            if (propName.startsWith("sync-")) {
                sawSyncProperties = true;
            } else {
                toRepository.setRevisionPropertyValue(revision, propName, propValue);
            }
            
            if (sync) {
                existingRevProps.remove(propName);
            }
        }
        
        if (sync) {
            for (Iterator propNames = existingRevProps.nameSet().iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                toRepository.setRevisionPropertyValue(revision, propName, null);
            }            
        }
        
        if (sawSyncProperties) {
            SVNDebugLog.getDefaultLog().info("Copied properties for revision " + revision + " (sync-* properties skipped).\n");
        } else {
            SVNDebugLog.getDefaultLog().info("Copied properties for revision " + revision + ".\n");
        }
    }

    private SessionInfo openSourceRepository(SVNRepository targetRepos) throws SVNException {
        SVNPropertyValue fromURL = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL);
        SVNPropertyValue fromUUID = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_UUID);
        SVNPropertyValue lastMergedRev = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION);

        if (fromURL == null || fromUUID == null || lastMergedRev == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Destination repository has not been initialized");
            SVNErrorManager.error(err);
        }

        SVNURL srcURL = SVNURL.parseURIDecoded(fromURL.getString());
        // TOOD close session.
        SVNRepository srcRepos = createRepository(srcURL, false);

        checkIfRepositoryIsAtRoot(srcRepos, srcURL);

        String reposUUID = srcRepos.getRepositoryUUID(true);
        if (!fromUUID.equals(reposUUID)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "UUID of destination repository ({0}) does not match expected UUID ({1})", new String[] {
                    reposUUID, fromUUID.getString()});
            SVNErrorManager.error(err);
        }

        return new SessionInfo(srcRepos, Long.parseLong(lastMergedRev.getString()));
    }

    private void checkIfRepositoryIsAtRoot(SVNRepository repos, SVNURL url) throws SVNException {
        SVNURL reposRoot = repos.getRepositoryRoot(true);
        if (!reposRoot.equals(url)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Session is rooted at ''{0}'' but the repos root is ''{1}''", new SVNURL[] {
                    url, reposRoot
            });
            SVNErrorManager.error(err);
        }
    }

    private void lock(SVNRepository repos) throws SVNException {
        String hostName = null;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't get local hostname");
            SVNErrorManager.error(err, e);
        }

        if (hostName.length() > 256) {
            hostName = hostName.substring(0, 256);
        }

        String lockToken = hostName + ":" + SVNUUIDGenerator.formatUUID(SVNUUIDGenerator.generateUUID());
        int i = 0;
        for (i = 0; i < 10; i++) {
            SVNPropertyValue reposLockToken = repos.getRevisionPropertyValue(0, SVNRevisionProperty.LOCK);
            if (reposLockToken != null) {
                if (reposLockToken.equals(lockToken)) {
                    return;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //
                }
            } else {
                repos.setRevisionPropertyValue(0, SVNRevisionProperty.LOCK, SVNPropertyValue.create(lockToken));
            }
        }

        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Couldn''t get lock on destination repos after {0} attempts\n", new Integer(i));
        SVNErrorManager.error(err);
    }

    private void unlock(SVNRepository repos) throws SVNException {
        repos.setRevisionPropertyValue(0, SVNRevisionProperty.LOCK, null);
    }

    private class SessionInfo {

        SVNRepository myRepository;
        long myLastMergedRevision;

        public SessionInfo(SVNRepository repos, long lastMergedRev) {
            myRepository = repos;
            myLastMergedRevision = lastMergedRev;
        }
    }
}
