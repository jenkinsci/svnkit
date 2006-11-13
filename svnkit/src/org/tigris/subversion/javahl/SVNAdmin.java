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
package org.tigris.subversion.javahl;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.javahl.SVNClientImpl;




/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNAdmin {

    protected long cppAddr;
    private SVNClientImpl myDelegate;

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
    }

    /**
     * @return Version information about the underlying native libraries.
     */
    public Version getVersion()
    {
        return myDelegate.getVersion();
    }

    /**
     * create a subversion repository.
     * @param path                  the path where the repository will been 
     *                              created.
     * @param disableFsyncCommit    disable to fsync at the commit (BDB).
     * @param keepLog               keep the log files (BDB).
     * @param configPath            optional path for user configuration files.
     * @param fstype                the type of the filesystem (BDB or FSFS)
     * @throws ClientException  throw in case of problem
     */
    public void create(String path, boolean disableFsyncCommit, 
                              boolean keepLog, String configPath,
                              String fstype) throws ClientException {
        if (BDB.equalsIgnoreCase(fstype)) {
            notImplementedYet("Only " + FSFS + " type of repositories are supported by " + getVersion().toString());
        }
        try {
            SVNRepositoryFactory.createLocalRepository(new File(path), false, false);
        } catch (SVNException e) {
            JavaHLObjectFactory.throwException(e, myDelegate);
        }
        
    }

    /**
     * deltify the revisions in the repository
     * @param path              the path to the repository
     * @param start             start revision
     * @param end               end revision
     * @throws ClientException  throw in case of problem
     */
    public void deltify(String path, Revision start, Revision end) throws ClientException {
        notImplementedYet();
    }
    
    /**
     * dump the data in a repository
     * @param path              the path to the repository
     * @param dataOut           the data will be outputed here
     * @param errorOut          the messages will be outputed here
     * @param start             the first revision to be dumped
     * @param end               the last revision to be dumped
     * @param incremental       the dump will be incremantal
     * @throws ClientException  throw in case of problem
     */
    public void dump(String path, OutputInterface dataOut, OutputInterface errorOut, Revision start, Revision end, boolean incremental) throws ClientException {
        notImplementedYet();
    }

    /**
     * make a hot copy of the repository
     * @param path              the path to the source repository
     * @param targetPath        the path to the target repository
     * @param cleanLogs         clean the unused log files in the source
     *                          repository
     * @throws ClientException  throw in case of problem
     */
    public void hotcopy(String path, String targetPath, boolean cleanLogs) throws ClientException {
        notImplementedYet();
    }

    /**
     * list all logfiles (BDB) in use or not)
     * @param path              the path to the repository
     * @param receiver          interface to receive the logfile names
     * @throws ClientException  throw in case of problem
     */
    public void listDBLogs(String path, MessageReceiver receiver) throws ClientException {
        notImplementedYet();
    }

    /**
     * list unused logfiles
     * @param path              the path to the repository
     * @param receiver          interface to receive the logfile names
     * @throws ClientException  throw in case of problem
     */
    public void listUnusedDBLogs(String path, MessageReceiver receiver) throws ClientException {
        notImplementedYet();
    }

    /**
     * interface to receive the messages
     */
    public static interface MessageReceiver
    {
        /**
         * receive one message line
         * @param message   one line of message
         */
        public void receiveMessageLine(String message);
    }

    /**
     * load the data of a dump into a repository,
     * @param path              the path to the repository
     * @param dataInput         the data input source
     * @param messageOutput     the target for processing messages
     * @param ignoreUUID        ignore any UUID found in the input stream
     * @param forceUUID         set the repository UUID to any found in the
     *                          stream
     * @param relativePath      the directory in the repository, where the data
     *                          in put optional.
     * @throws ClientException  throw in case of problem
     */
    public void load(String path, InputInterface dataInput, OutputInterface messageOutput, boolean ignoreUUID, boolean forceUUID, String relativePath) throws ClientException {
        notImplementedYet();
    }

    /**
     * list all open transactions in a repository
     * @param path              the path to the repository
     * @param receiver          receives one transaction name per call
     * @throws ClientException  throw in case of problem
     */
    public void lstxns(String path, MessageReceiver receiver) throws ClientException {
        notImplementedYet();
    }
    
    /**
     * recover the berkeley db of a repository, returns youngest revision
     * @param path              the path to the repository
     * @throws ClientException  throw in case of problem
     */
    public long recover(String path) throws ClientException {
        notImplementedYet();
        return -1;
    }

    /**
     * remove open transaction in a repository
     * @param path              the path to the repository
     * @param transactions      the transactions to be removed
     * @throws ClientException  throw in case of problem
     */
    public void rmtxns(String path, String [] transactions) throws ClientException {
        notImplementedYet();
    }

    /**
     * set the log message of a revision
     * @param path              the path to the repository
     * @param rev               the revision to be changed
     * @param message           the message to be set
     * @param bypassHooks       if to bypass all repository hooks
     * @throws ClientException  throw in case of problem
     */
    public void setLog(String path, Revision rev, String message, boolean bypassHooks) throws ClientException {
        notImplementedYet();
    }
    
    /**
     * verify the repository
     * @param path              the path to the repository
     * @param messageOut        the receiver of all messages
     * @param start             the first revision
     * @param end               the last revision
     * @throws ClientException  throw in case of problem
     */
    public void verify(String path,  OutputInterface messageOut,  Revision start, Revision end) throws ClientException {
        notImplementedYet();
    }

    /**
     * list all locks in the repository
     * @param path              the path to the repository
     * @throws ClientException  throw in case of problem
     * @since 1.2
     */ 
    public Lock[] lslocks(String path) throws ClientException {
        notImplementedYet();
        return new Lock[0];
    }

    /**
     * remove multiple locks from the repository
     * @param path              the path to the repository
     * @param locks             the name of the locked items
     * @throws ClientException  throw in case of problem
     * @since 1.2
     */
    public void rmlocks(String path, String [] locks) throws ClientException {
        notImplementedYet();
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
