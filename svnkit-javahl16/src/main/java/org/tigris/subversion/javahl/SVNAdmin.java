/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tigris.subversion.javahl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.javahl.SVNClientImpl;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAdapter;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;


/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class SVNAdmin {

    protected long cppAddr;
    private SVNClientImpl myDelegate;
    private SVNAdminClient mySVNAdminClient;

    /**
     * Filesystem in a Berkeley DB
     */
    public static final String BDB = "bdb";
    /**
     * Filesystem in the filesystem
     */
    public static final String FSFS = "fsfs";

    public SVNAdmin() {
        myDelegate = SVNClientImpl.newInstance();
    }


    public void dispose() {
        myDelegate.dispose();
        mySVNAdminClient = null;
    }

    /**
     * @return Version information about the underlying native libraries.
     */
    public Version getVersion() {
        return myDelegate.getVersion();
    }

    protected SVNAdminClient getAdminClient() {
        if (mySVNAdminClient == null) {
            mySVNAdminClient = new SVNAdminClient(SVNWCUtil.createDefaultAuthenticationManager(), SVNWCUtil.createDefaultOptions(true));
        }
        return mySVNAdminClient;
    }

    /**
     * create a subversion repository.
     *
     * @param path               the path where the repository will been
     *                           created.
     * @param disableFsyncCommit disable to fsync at the commit (BDB).
     * @param keepLog            keep the log files (BDB).
     * @param configPath         optional path for user configuration files.
     * @param fstype             the type of the filesystem (BDB or FSFS)
     * @throws ClientException throw in case of problem
     */
    public void create(String path, boolean disableFsyncCommit,
                       boolean keepLog, String configPath,
                       String fstype) throws ClientException {
        if (BDB.equalsIgnoreCase(fstype)) {
            notImplementedYet("Only " + FSFS + " type of repositories are supported by " + getVersion().toString());
        }
        try {
            SVNRepositoryFactory.createLocalRepository(new File(path), false, false);
            if (configPath != null) {

            }
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        }

    }

    /**
     * deltify the revisions in the repository
     *
     * @param path  the path to the repository
     * @param start start revision
     * @param end   end revision
     * @throws ClientException throw in case of problem
     */
    public void deltify(String path, Revision start, Revision end) throws ClientException {
        notImplementedYet();
    }

    /**
     * dump the data in a repository
     *
     * @param path        the path to the repository
     * @param dataOut     the data will be outputed here
     * @param errorOut    the messages will be outputed here
     * @param start       the first revision to be dumped
     * @param end         the last revision to be dumped
     * @param incremental the dump will be incremantal
     * @throws ClientException throw in case of problem
     */
    public void dump(String path, OutputInterface dataOut, OutputInterface errorOut, Revision start, Revision end, boolean incremental) throws ClientException {
        dump(path, dataOut, errorOut, start, end, incremental, false);
    }

    /**
     * dump the data in a repository
     *
     * @param path        the path to the repository
     * @param dataOut     the data will be outputed here
     * @param errorOut    the messages will be outputed here
     * @param start       the first revision to be dumped
     * @param end         the last revision to be dumped
     * @param incremental the dump will be incremantal
     * @param useDeltas   the dump will contain deltas between nodes
     * @throws ClientException throw in case of problem
     * @since 1.5
     */
    public void dump(String path, OutputInterface dataOut, final OutputInterface errorOut, Revision start,
                     Revision end, boolean incremental, boolean useDeltas) throws ClientException {
        OutputStream os = createOutputStream(dataOut);
        try {
            getAdminClient().setEventHandler(new SVNAdminEventAdapter() {
                public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
                    if (errorOut != null && event.getAction() == SVNAdminEventAction.REVISION_DUMPED) {
                        try {
                            errorOut.write(event.getMessage().getBytes());
                            errorOut.write(myDelegate.getOptions().getNativeEOL());
                        } catch (IOException e) {
                        }
                    }
                }
            });
            getAdminClient().doDump(new File(path).getAbsoluteFile(), os, JavaHLObjectFactory.getSVNRevision(start), JavaHLObjectFactory.getSVNRevision(end), incremental, useDeltas);
        } catch (SVNException e) {
            try {
                if (errorOut != null) {
                    errorOut.write(e.getErrorMessage().getFullMessage().getBytes("UTF-8"));
                    errorOut.write(myDelegate.getOptions().getNativeEOL());
                }
            } catch (IOException e1) {
                //
            }
            JavaHLObjectFactory.throwException(e, myDelegate);
        } finally {
            getAdminClient().setEventHandler(null);
        }
    }

    /**
     * make a hot copy of the repository
     *
     * @param path       the path to the source repository
     * @param targetPath the path to the target repository
     * @param cleanLogs  clean the unused log files in the source
     *                   repository
     * @throws ClientException throw in case of problem
     */
    public void hotcopy(String path, String targetPath, boolean cleanLogs) throws ClientException {
        try {
            getAdminClient().doHotCopy(new File(path).getAbsoluteFile(), new File(targetPath).getAbsoluteFile());
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        }
    }

    /**
     * list all logfiles (BDB) in use or not)
     *
     * @param path     the path to the repository
     * @param receiver interface to receive the logfile names
     * @throws ClientException throw in case of problem
     */
    public void listDBLogs(String path, MessageReceiver receiver) throws ClientException {
        notImplementedYet("Only " + FSFS + " type of repositories are supported by " + getVersion().toString());
    }

    /**
     * list unused logfiles
     *
     * @param path     the path to the repository
     * @param receiver interface to receive the logfile names
     * @throws ClientException throw in case of problem
     */
    public void listUnusedDBLogs(String path, MessageReceiver receiver) throws ClientException {
        notImplementedYet("Only " + FSFS + " type of repositories are supported by " + getVersion().toString());
    }

    /**
     * interface to receive the messages
     */
    public static interface MessageReceiver {
        /**
         * receive one message line
         *
         * @param message one line of message
         */
        public void receiveMessageLine(String message);
    }

    /**
     * load the data of a dump into a repository,
     *
     * @param path          the path to the repository
     * @param dataInput     the data input source
     * @param messageOutput the target for processing messages
     * @param ignoreUUID    ignore any UUID found in the input stream
     * @param forceUUID     set the repository UUID to any found in the
     *                      stream
     * @param relativePath  the directory in the repository, where the data
     *                      in put optional.
     * @throws ClientException throw in case of problem
     */
    public void load(String path, InputInterface dataInput, final OutputInterface messageOutput, boolean ignoreUUID, boolean forceUUID, String relativePath) throws ClientException {
        load(path, dataInput, messageOutput, ignoreUUID, forceUUID, false, false, relativePath);
    }

    public void load(String path, InputInterface dataInput, final OutputInterface messageOutput, boolean ignoreUUID,
                     boolean forceUUID, boolean usePreCommitHook, boolean usePostCommitHook, String relativePath)
            throws ClientException {
        InputStream is = createInputStream(dataInput);
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
                        message.append(myDelegate.getOptions().getNativeEOL());
                        myIsNodeOpened = false;
                    }
                    if (event.getAction() == SVNAdminEventAction.REVISION_LOADED) {
                        message.append(myDelegate.getOptions().getNativeEOL());
                    }
                    message.append(event.getMessage());
                    message.append(myDelegate.getOptions().getNativeEOL());
                    if (event.getAction() == SVNAdminEventAction.REVISION_LOADED) {
                        message.append(myDelegate.getOptions().getNativeEOL());
                    }
                    myIsNodeOpened = event.getAction() != SVNAdminEventAction.REVISION_LOAD;
                    return message.toString();
                }
            });
            getAdminClient().doLoad(new File(path).getAbsoluteFile(), is, usePreCommitHook, usePostCommitHook, uuidAction, relativePath);
        } catch (SVNException e) {
            if (messageOutput != null) {
                try {
                    messageOutput.write(e.getErrorMessage().getFullMessage().getBytes("UTF-8"));
                    messageOutput.write(myDelegate.getOptions().getNativeEOL());
                } catch (IOException e1) {
                }
            }
            JavaHLObjectFactory.throwException(e, myDelegate);
        } finally {
            getAdminClient().setEventHandler(null);
        }
    }

    /**
     * list all open transactions in a repository
     *
     * @param path     the path to the repository
     * @param receiver receives one transaction name per call
     * @throws ClientException throw in case of problem
     */
    public void lstxns(String path, final MessageReceiver receiver) throws ClientException {
        getAdminClient().setEventHandler(new SVNAdminEventAdapter() {
            public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
                if (receiver != null && event.getTxnName() != null) {
                    receiver.receiveMessageLine(event.getTxnName());
                }
            }
        });
        try {
            getAdminClient().doListTransactions(new File(path).getAbsoluteFile());
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        } finally {
            getAdminClient().setEventHandler(null);
        }
    }

    /**
     * recover the berkeley db of a repository, returns youngest revision
     *
     * @param path the path to the repository
     * @throws ClientException throw in case of problem
     */
    public long recover(String path) throws ClientException {
        try {
            File repositoryRoot = new File(path).getAbsoluteFile();
            getAdminClient().doRecover(repositoryRoot);
            return getAdminClient().getYoungestRevision(repositoryRoot);
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        }
        return -1;
    }

    /**
     * remove open transaction in a repository
     *
     * @param path         the path to the repository
     * @param transactions the transactions to be removed
     * @throws ClientException throw in case of problem
     */
    public void rmtxns(String path, String[] transactions) throws ClientException {
        try {
            getAdminClient().doRemoveTransactions(new File(path).getAbsoluteFile(), transactions);
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        }
    }

    /**
     * set the log message of a revision
     *
     * @param path        the path to the repository
     * @param rev         the revision to be changed
     * @param message     the message to be set
     * @param bypassHooks if to bypass all repository hooks
     * @throws ClientException throw in case of problem
     * @deprecated Use setRevProp() instead.
     */
    public void setLog(String path, Revision rev, String message, boolean bypassHooks) throws ClientException {
        try {
            setRevisionProperty(path, rev, SVNRevisionProperty.LOG, message, bypassHooks, bypassHooks);
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        }
    }

    /**
     * Change the value of the revision property <code>propName</code>
     * to <code>propValue</code>.  By default, does not run
     * pre-/post-revprop-change hook scripts.
     *
     * @param path                     The path to the repository.
     * @param rev                      The revision for which to change a property value.
     * @param propName                 The name of the property to change.
     * @param propValue                The new value to set for the property.
     * @param usePreRevPropChangeHook  Whether to run the
     *                                 <i>pre-revprop-change</i> hook script.
     * @param usePostRevPropChangeHook Whether to run the
     *                                 <i>post-revprop-change</i> hook script.
     * @throws SubversionException If a problem occurs.
     * @since 1.5.0
     */
    public void setRevProp(String path, Revision rev, String propName, String propValue, boolean usePreRevPropChangeHook, boolean usePostRevPropChangeHook) throws SubversionException {
        try {
            setRevisionProperty(path, rev, propName, propValue, !usePreRevPropChangeHook, !usePostRevPropChangeHook);
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        }
    }

    private static void setRevisionProperty(String path, Revision rev, String propName, String propValue, boolean bypassPreRevPropChangeHook, boolean bypassPostRevPropChangeHook) throws SVNException {
        SVNRepository repository = SVNRepositoryFactory.create(SVNURL.fromFile(new File(path).getAbsoluteFile()));
        ((FSRepository) repository).setRevisionPropertyValue(JavaHLObjectFactory.getSVNRevision(rev).getNumber(), propName, SVNPropertyValue.create(propValue), bypassPreRevPropChangeHook, bypassPostRevPropChangeHook);
    }

    /**
     * verify the repository
     *
     * @param path       the path to the repository
     * @param messageOut the receiver of all messages
     * @param start      the first revision
     * @param end        the last revision
     * @throws ClientException throw in case of problem
     */
    public void verify(String path, final OutputInterface messageOut, Revision start, Revision end) throws ClientException {
        try {
            getAdminClient().setEventHandler(new SVNAdminEventAdapter() {
                public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
                    if (messageOut != null && event.getAction() == SVNAdminEventAction.REVISION_DUMPED) {
                        try {
                            messageOut.write(event.getMessage().getBytes());
                            messageOut.write(myDelegate.getOptions().getNativeEOL());
                        } catch (IOException e) {
                        }
                    }
                }
            });
            getAdminClient().doVerify(new File(path).getAbsoluteFile(), JavaHLObjectFactory.getSVNRevision(start), JavaHLObjectFactory.getSVNRevision(end));
        } catch (SVNException e) {
            try {
                if (messageOut != null) {
                    messageOut.write(e.getErrorMessage().getFullMessage().getBytes("UTF-8"));
                    messageOut.write(myDelegate.getOptions().getNativeEOL());
                }
            } catch (IOException e1) {
                //
            }
            JavaHLObjectFactory.throwException(e, myDelegate);
        } finally {
            getAdminClient().setEventHandler(null);
        }
    }

    /**
     * list all locks in the repository
     *
     * @param path the path to the repository
     * @throws ClientException throw in case of problem
     * @since 1.2
     */
    public Lock[] lslocks(String path) throws ClientException {
        final ArrayList locks = new ArrayList();
        getAdminClient().setEventHandler(new SVNAdminEventAdapter() {
            public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
                if (event.getAction() == SVNAdminEventAction.LOCK_LISTED) {
                    SVNLock svnLock = event.getLock();
                    Lock lock = JavaHLObjectFactory.createLock(svnLock);
                    locks.add(lock);
                }
            }
        });

        try {
            getAdminClient().doListLocks(new File(path).getAbsoluteFile());
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        } finally {
            getAdminClient().setEventHandler(null);
        }

        return (Lock[]) locks.toArray(new Lock[locks.size()]);
    }

    /**
     * remove multiple locks from the repository
     *
     * @param path  the path to the repository
     * @param locks the name of the locked items
     * @throws ClientException throw in case of problem
     * @since 1.2
     */
    public void rmlocks(String path, String[] locks) throws ClientException {
        try {
            getAdminClient().doRemoveLocks(new File(path).getAbsoluteFile(), locks);
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        } finally {
            getAdminClient().setEventHandler(null);
        }
    }

    private static OutputStream createOutputStream(final OutputInterface dataOut) {
        if (dataOut == null) {
            return SVNFileUtil.DUMMY_OUT;
        }
        return new OutputStream() {
            public void write(int b) throws IOException {
                dataOut.write(new byte[]{(byte) (b & 0xFF)});
            }

            public void write(byte[] b) throws IOException {
                dataOut.write(b);
            }

            public void close() throws IOException {
                dataOut.close();
            }

            public void write(byte[] b, int off, int len) throws IOException {
                byte[] copy = new byte[len];
                System.arraycopy(b, off, copy, 0, len);
                dataOut.write(copy);
            }
        };
    }

    private static InputStream createInputStream(final InputInterface dataIn) {
        if (dataIn == null) {
            return SVNFileUtil.DUMMY_IN;
        }
        return new InputStream() {

            public int read() throws IOException {
                byte[] b = new byte[1];
                int r = dataIn.read(b);
                if (r <= 0) {
                    return -1;
                }
                return b[0] & 0xFF;
            }

            public void close() throws IOException {
                dataIn.close();
            }

            public int read(byte[] b, int off, int len) throws IOException {
                byte[] copy = new byte[len];
                int realLen = dataIn.read(copy);
                if (realLen <= 0) {
                    return realLen;
                }
                System.arraycopy(copy, 0, b, off, realLen);
                return realLen;
            }

            public int read(byte[] b) throws IOException {
                return dataIn.read(b);
            }
        };
    }

    private void notImplementedYet() throws ClientException {
        notImplementedYet(null);
    }

    private void notImplementedYet(String message) throws ClientException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE,
                message == null ? "Requested SVNAdmin functionality is not yet implemented" : message);
        JavaHLObjectFactory.throwException(new SVNException(err), myDelegate);
    }
}
