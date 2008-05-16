/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc.admin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.io.fs.FSEntry;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSID;
import org.tmatesoft.svn.core.internal.io.fs.FSNodeHistory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryUtil;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNGNUDiffGenerator;
import org.tmatesoft.svn.core.internal.wc.SVNAdminHelper;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNNodeEditor;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNBasicClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * The <b>SVNLookClient</b> class provides API for examining 
 * different aspects of a Subversion repository. Its functionality 
 * is similar to the one of the Subversion command-line utility 
 * called <i>svnlook</i>. The following table matches methods of 
 * <b>SVNLookClient</b> to the corresponding commands of the 
 * <i>svnlook</i> utility (to make sense what its different methods
 * are for):
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="50%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNLookClient</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doCat()</td><td>'svnlook cat'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetAuthor()</td><td>'svnlook author'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetChanged()</td><td>'svnlook changed'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetChangedDirectories()</td><td>'svnlook dirs-changed'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetDate()</td><td>'svnlook date'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetDiff()</td><td>'svnlook diff'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetHistory()</td><td>'svnlook history'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetInfo()</td><td>'svnlook info'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetLock()</td><td>'svnlook lock'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetLog()</td><td>'svnlook log'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetProperties()</td><td>'svnlook proplist'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetProperty()</td><td>'svnlook propget'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetRevisionProperties()</td><td>'svnlook proplist --revprop'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetRevisionProperty()</td><td>'svnlook propget --revprop'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetTree()</td><td>'svnlook tree'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetUUID()</td><td>'svnlook uuid'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doGetYoungestRevision()</td><td>'svnlook youngest'</td>
 * </tr>
 * </table>
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @since   1.1.1
 */
public class SVNLookClient extends SVNBasicClient {
    private ISVNGNUDiffGenerator myDiffGenerator;
    
    /**
     * Creates a new instance of <b>SVNLookClient</b>
     * given an authentication manager and global 
     * options keeper.
     * 
     * @param authManager  a manager which provides authentication
     *                     credentials
     * @param options      a global config options provider
     */
    public SVNLookClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    /**
     * Creates a new instance of <b>SVNLookClient</b>
     * given an {@link org.tmatesoft.svn.core.io.SVNRepository}} 
     * drivers provider and global options keeper.
     * 
     * @param repositoryPool a repository connectors keeper 
     * @param options        a global config options provider
     */
    public SVNLookClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    /**
     * Retrieves author, timestamp and log message information from 
     * the repository for the given revision. This information is 
     * provided in a single {@link org.tmatesoft.svn.core.SVNLogEntry} 
     * object, that is only the following methods of <b>SVNLogEntry</b> 
     * return valid information:
     * 
     * <ul>
     * <li>
     * {@link org.tmatesoft.svn.core.SVNLogEntry#getAuthor() getAuthor()}
     * </li>
     * <li>
     * {@link org.tmatesoft.svn.core.SVNLogEntry#getDate() getDate()}
     * </li>
     * <li>
     * {@link org.tmatesoft.svn.core.SVNLogEntry#getMessage() getMessage()}
     * </li>
     * <li>
     * {@link org.tmatesoft.svn.core.SVNLogEntry#getRevision() getRevision()}
     * </li>
     * </ul>  
     * 
     * @param   repositoryRoot  a repository root directory path
     * @param   revision        a revision number
     * @return                  revision info
     * @throws  SVNException    no repository is found at 
     *                          <code>repositoryRoot</code> 
     */
    public SVNLogEntry doGetInfo(File repositoryRoot, SVNRevision revision) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        Map revProps = fsfs.getRevisionProperties(revNum);
        String date = (String) revProps.get(SVNRevisionProperty.DATE);
        String author = (String) revProps.get(SVNRevisionProperty.AUTHOR);
        String logMessage = (String) revProps.get(SVNRevisionProperty.LOG);
        return new SVNLogEntry(null, revNum, author, SVNTimeUtil.parseDateString(date), logMessage);
    }

    /**
     * Retrieves author, timestamp and log message information from 
     * the repository for the given transaction name. This information is 
     * provided in a single {@link org.tmatesoft.svn.core.SVNLogEntry} 
     * object, that is only the following methods of <b>SVNLogEntry</b> 
     * return valid information:
     * <ul>
     * <li>
     * {@link org.tmatesoft.svn.core.SVNLogEntry#getAuthor() getAuthor()}
     * </li>
     * <li>
     * {@link org.tmatesoft.svn.core.SVNLogEntry#getDate() getDate()}
     * </li>
     * <li>
     * {@link org.tmatesoft.svn.core.SVNLogEntry#getMessage() getMessage()}
     * </li>
     * </ul>  
     * 
     * @param   repositoryRoot   a repository root directory path
     * @param   transactionName  a transaction name
     * @return                   transaction info
     * @throws SVNException      <ul>
     *                           <li>no repository is found at 
     *                           <code>repositoryRoot</code></li>
     *                           <li>if the specified transaction is not found</li>
     *                           </ul>
     */
    public SVNLogEntry doGetInfo(File repositoryRoot, String transactionName) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);

        Map txnProps = fsfs.getTransactionProperties(txn.getTxnId());
        String date = (String) txnProps.get(SVNRevisionProperty.DATE);
        String author = (String) txnProps.get(SVNRevisionProperty.AUTHOR);
        String logMessage = (String) txnProps.get(SVNRevisionProperty.LOG);
        return new SVNLogEntry(null, -1, author, SVNTimeUtil.parseDateString(date), logMessage);
    }

    /**
     * Returns the latest revision of the repository.
     * 
     * @param  repositoryRoot   a repository root directory path
     * @return                  a revision number
     * @throws SVNException     no repository is found at 
     *                          <code>repositoryRoot</code>
     */
    public long doGetYoungestRevision(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        return fsfs.getYoungestRevision();
    }

    /**
     * Returns the uuid of the repository.
     * 
     * @param  repositoryRoot  a repository root directory path
     * @return                 an uuid
     * @throws SVNException    no repository is found at 
     *                         <code>repositoryRoot</code>
     */
    public String doGetUUID(File repositoryRoot) throws SVNException {
        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        return fsfs.getUUID();
    }

    /**
     * Returns author information for the given revision. 
     * 
     * @param  repositoryRoot a repository root directory path
     * @param  revision       a revision number
     * @return                a revision author 
     * @throws SVNException   no repository is found at 
     *                        <code>repositoryRoot</code>
     */
    public String doGetAuthor(File repositoryRoot, SVNRevision revision) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        Map revProps = fsfs.getRevisionProperties(revNum);
        return (String) revProps.get(SVNRevisionProperty.AUTHOR);
    }

    /**
     * Returns author information for the given transaction. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  transactionName  a transaction name
     * @return                  a transaction owner
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code></li>
     *                          <li>if the specified transaction is not found</li>
     *                          </ul> 
     */
    public String doGetAuthor(File repositoryRoot, String transactionName) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        Map txnProps = fsfs.getTransactionProperties(txn.getTxnId());
        return (String) txnProps.get(SVNRevisionProperty.AUTHOR);
    }
    
    /**
     * Fetches file contents for the specified revision and path. 
     * <code>path</code> must be absolute, that is it must 
     * start with <code>'/'</code>. The provided output stream is
     * not closed within this method.
     * 
     * @param  repositoryRoot  a repository root directory path
     * @param  path            an absolute file path
     * @param  revision        a revision number
     * @param  out             an output stream to write contents to
     * @throws SVNException    <ul>
     *                         <li>no repository is found at 
     *                         <code>repositoryRoot</code>
     *                         </li>
     *                         <li>if <code>path</code> is not found or
     *                         is not a file
     *                         </li>
     *                         </ul>
     */
    public void doCat(File repositoryRoot, String path, SVNRevision revision, OutputStream out) throws SVNException {
        if (path == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Missing repository path argument");
            SVNErrorManager.error(err);
        }

        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        FSRoot root = fsfs.createRevisionRoot(revNum);
        catFile(root, path, out);
    }

    /**
     * Fetches file contents for the specified path in the given 
     * transaction. <code>path</code> must be absolute, that is it 
     * must start with <code>'/'</code>. The provided output stream 
     * is not closed within this method.
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  path             an absolute file path
     * @param  transactionName  a transaction name
     * @param  out              an output stream to write contents to
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if <code>path</code> is not found or
     *                          is not a file
     *                          </li>
     *                          <li>if the specified transaction is not found
     *                          </li>
     *                          </ul>
     */
    public void doCat(File repositoryRoot, String path, String transactionName, OutputStream out) throws SVNException {
        if (path == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Missing repository path argument");
            SVNErrorManager.error(err);
        }
        
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        FSRoot root = fsfs.createTransactionRoot(txn.getTxnId());
        catFile(root, path, out);
    }

    /**
     * Returns datestamp information for the given revision. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  revision         a revision number
     * @return                  a datestamp
     * @throws SVNException     no repository is found at 
     *                          <code>repositoryRoot</code>
     */
    public Date doGetDate(File repositoryRoot, SVNRevision revision) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        Map revProps = fsfs.getRevisionProperties(revNum);
        String date = (String) revProps.get(SVNRevisionProperty.DATE);
        if (date != null) {
            return SVNTimeUtil.parseDate(date);
        }
        return null;
    }

    /**
     * Returns datestamp information for the given transaction. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  transactionName  a transaction name
     * @return                  a datestamp
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if the specified transaction is not found
     *                          </li>
     *                          </ul>
     */
    public Date doGetDate(File repositoryRoot, String transactionName) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        Map txnProps = fsfs.getTransactionProperties(txn.getTxnId());
        String date = (String) txnProps.get(SVNRevisionProperty.DATE);
        if (date != null) {
            return SVNTimeUtil.parseDate(date);
        }
        return null;
    }

    /**
     * Returns log information for the given revision. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  revision         a revision number
     * @return                  a log message
     * @throws SVNException     no repository is found at 
     *                          <code>repositoryRoot</code>
     */
    public String doGetLog(File repositoryRoot, SVNRevision revision) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        Map revProps = fsfs.getRevisionProperties(revNum);
        return (String) revProps.get(SVNRevisionProperty.LOG);
    }

    /**
     * Returns log information for the given transaction. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  transactionName  a transaction name
     * @return                  a log message
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if the specified transaction is not found
     *                          </li>
     *                          </ul>
     */
    public String doGetLog(File repositoryRoot, String transactionName) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        Map txnProps = fsfs.getTransactionProperties(txn.getTxnId());
        return (String) txnProps.get(SVNRevisionProperty.LOG);
    }

    /**
     * Traverses changed paths for the given revision invoking 
     * the passed handler on each changed path. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  revision         a revision number
     * @param  handler          a changed path handler
     * @param  includeCopyInfo  if <span class="javakeyword">true</span> copy-from 
     *                          information is also provided for copied paths
     * @throws SVNException     no repository is found at 
     *                          <code>repositoryRoot</code>
     */
    public void doGetChanged(File repositoryRoot, SVNRevision revision, ISVNChangeEntryHandler handler, boolean includeCopyInfo) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        FSRoot root = fsfs.createRevisionRoot(revNum);
        long baseRevision = revNum - 1;
        SVNNodeEditor editor = generateDeltaTree(fsfs, root, baseRevision);
        editor.traverseTree(includeCopyInfo, handler);
    }

    /**
     * Traverses changed paths for the given transaction invoking 
     * the passed handler on each changed path. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  transactionName  a transaction name
     * @param  handler          a changed path handler
     * @param  includeCopyInfo  if <span class="javakeyword">true</span> copy-from 
     *                          information is also provided for copied paths
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if the specified transaction is not found
     *                          </li>
     *                          </ul>
     */
    public void doGetChanged(File repositoryRoot, String transactionName, ISVNChangeEntryHandler handler, boolean includeCopyInfo) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        FSRoot root = fsfs.createTransactionRoot(txn.getTxnId());
        long baseRevision = txn.getBaseRevision();

        if (!SVNRevision.isValidRevisionNumber(baseRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Transaction ''{0}'' is not based on a revision; how odd", transactionName);
            SVNErrorManager.error(err);
        }
        SVNNodeEditor editor = generateDeltaTree(fsfs, root, baseRevision);
        editor.traverseTree(includeCopyInfo, handler);
    }
    
    /**
     * Passes paths of directories changed in the given revision to the provided handler. 
     * Paths are absolute (start with <code>'/'</code>).
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  revision         a revision number
     * @param  handler          a path handler
     * @throws SVNException     no repository is found at 
     *                          <code>repositoryRoot</code>
     */
    public void doGetChangedDirectories(File repositoryRoot, SVNRevision revision, ISVNChangedDirectoriesHandler handler) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        FSRoot root = fsfs.createRevisionRoot(revNum);
        long baseRevision = revNum - 1;
        SVNNodeEditor editor = generateDeltaTree(fsfs, root, baseRevision);
        editor.traverseChangedDirs(handler);
    }

    /**
     * Passes paths of directories changed in the given transaction to the provided handler. 
     * Paths are absolute (start with <code>'/'</code>).
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  transactionName  a transaction name
     * @param  handler          a path handler
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if the specified transaction is not found
     *                          </li>
     *                          </ul>
     */
    public void doGetChangedDirectories(File repositoryRoot, String transactionName, ISVNChangedDirectoriesHandler handler) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        FSRoot root = fsfs.createTransactionRoot(txn.getTxnId());
        long baseRevision = txn.getBaseRevision();

        if (!SVNRevision.isValidRevisionNumber(baseRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Transaction ''{0}'' is not based on a revision; how odd", transactionName);
            SVNErrorManager.error(err);
        }
        SVNNodeEditor editor = generateDeltaTree(fsfs, root, baseRevision);
        editor.traverseChangedDirs(handler);
    }

    /**
     * Passes history information for the specified path and revision to the provided handler.
     * This information is provided as {@link SVNAdminPath} objects and include the following 
     * pieces: 
     * <ul>
     * <li>path (use {@link SVNAdminPath#getPath()} to retrieve it)</li>
     * <li>revision (use {@link SVNAdminPath#getRevision()} to retrieve it)</li>
     * <li>node id (optional, use {@link SVNAdminPath#getNodeID()} to retrieve it)</li>
     * </ul>
     * For history retrieval only these listed <code>get</code> methods of <b>SVNAdminPath</b> are 
     * relevant. 
     * 
     * <p>
     * <code>path</code> must be absolute, that is it must start with <code>'/'</code>.     
     * If <code>path</code> is <span class="javakeyword">null</span> it defaults to 
     * <code>"/"</code>.
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  path             an absolute path
     * @param  revision         a revision number
     * @param  includeIDs       if <span class="javakeyword">true</span> a node 
     *                          revision id is also included for each path 
     * @param  handler          a history handler
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if <code>path</code> is not found 
     *                          </li>
     *                          </ul>
     */
    public void doGetHistory(File repositoryRoot, String path, SVNRevision revision, boolean includeIDs, ISVNHistoryHandler handler) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        path = path == null ? "/" : path;
        getHistory(fsfs, path, 0, revNum, true, includeIDs, handler);
    }

    /**
     * Retrieves lock information for the specified path.
     * <code>path</code> must be absolute, that is it must start with <code>'/'</code>.
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  path             an absolute path            
     * @return                  an object containing details of a lock or 
     *                          <span class="javakeyword">null</span> if the 
     *                          path is not locked
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if <code>path</code> is not found 
     *                          </li>
     *                          </ul>
     */
    public SVNLock doGetLock(File repositoryRoot, String path) throws SVNException {
        if (path == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Missing path argument");
            SVNErrorManager.error(err);
        }
        FSFS fsfs = open(repositoryRoot, SVNRevision.HEAD);
        return fsfs.getLockHelper(path, false);
    }

    /**
     * Traverses repository tree starting at the specified path in the 
     * given revision and invoking the provided handler on each path.
     * Path information is provided as {@link SVNAdminPath} objects and 
     * include the following pieces: 
     * <ul>
     * <li>path (use {@link SVNAdminPath#getPath()} to retrieve it)</li>
     * <li>tree depth starting from <code>0</code> at <code>path</code> 
     * (use {@link SVNAdminPath#getTreeDepth()} to retrieve it)</li>
     * <li>node id (optional, use {@link SVNAdminPath#getNodeID()} to retrieve it)</li>
     * <li>file/dir information (use {@link SVNAdminPath#isDir()} to retrieve it)</li>
     * </ul>
     * 
     * For tree retrieval only these listed <code>get</code> methods of <b>SVNAdminPath</b> are 
     * relevant. 
     * 
     * <p>
     * <code>path</code> must be absolute, that is it must start with <code>'/'</code>.     
     * If <code>path</code> is <span class="javakeyword">null</span> it defaults to 
     * <code>"/"</code>.
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  path             an absolute path            
     * @param  revision         a revision number
     * @param  includeIDs       if <span class="javakeyword">true</span> a node 
     *                          revision id is also included for each path
     * @param  handler          a tree handler
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if <code>path</code> is not found 
     *                          </li>
     *                          </ul>
     */
    public void doGetTree(File repositoryRoot, String path, SVNRevision revision, boolean includeIDs, ISVNTreeHandler handler) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        FSRoot root = fsfs.createRevisionRoot(revNum);
        path = path == null ? "/" : path;
        FSRevisionNode node = root.getRevisionNode(path);
        FSID id = includeIDs ? node.getId() : null;
        SVNNodeKind kind = root.checkNodeKind(path);
        getTree(fsfs, root, path, kind, id, includeIDs, 0, handler);
    }

    /**
     * Traverses repository tree starting at the specified path in the 
     * given transaction and invoking the provided handler on each path.
     * Path information is provided as {@link SVNAdminPath} objects and 
     * include the following pieces: 
     * <ul>
     * <li>path (use {@link SVNAdminPath#getPath()} to retrieve it)</li>
     * <li>tree depth starting from <code>0</code> at <code>path</code> 
     * (use {@link SVNAdminPath#getTreeDepth()} to retrieve it)</li>
     * <li>node id (optional, use {@link SVNAdminPath#getNodeID()} to retrieve it)</li>
     * <li>file/dir information (use {@link SVNAdminPath#isDir()} to retrieve it)</li>
     * </ul>
     * 
     * For tree retrieval only these listed <code>get</code> methods of <b>SVNAdminPath</b> are 
     * relevant. 
     * 
     * <p>
     * <code>path</code> must be absolute, that is it must start with <code>'/'</code>.     
     * If <code>path</code> is <span class="javakeyword">null</span> it defaults to 
     * <code>"/"</code>.
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  path             an absolute path            
     * @param  transactionName  a transaction name
     * @param  includeIDs       if <span class="javakeyword">true</span> a node 
     *                          revision id is also included for each path
     * @param  handler          a tree handler
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if <code>path</code> is not found 
     *                          </li>
     *                          <li>if the specified transaction is not found
     *                          </li>
     *                          </ul>
     */
    public void doGetTree(File repositoryRoot, String path, String transactionName, boolean includeIDs, ISVNTreeHandler handler) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        FSRoot root = fsfs.createTransactionRoot(txn.getTxnId());
        path = path == null ? "/" : path;
        FSRevisionNode node = root.getRevisionNode(path);
        FSID id = includeIDs ? node.getId() : null;
        SVNNodeKind kind = root.checkNodeKind(path);
        getTree(fsfs, root, path, kind, id, includeIDs, 0, handler);
    }
    
    /**
     * Writes differences of changed files and properties for the 
     * given revision to the provided output stream. If no special diff generator 
     * {@link #setDiffGenerator(ISVNGNUDiffGenerator) was provided} to 
     * this client a default GNU-style diff generator is used (which 
     * writes differences just like the <code>'svnlook diff'</code> command). 
     * 
     * <p>
     * The provided output stream is not closed within this method.
     *      
     * @param  repositoryRoot   a repository root directory path
     * @param  revision         a revision number
     * @param  diffDeleted      if <span class="javakeyword">true</span> 
     *                          differences for deleted files are included, 
     *                          otherwise not
     * @param  diffAdded        if <span class="javakeyword">true</span> 
     *                          differences for added files are included, 
     *                          otherwise not
     * @param  diffCopyFrom     if <span class="javakeyword">true</span> 
     *                          writes differences against the copy source 
     *                          (if any), otherwise not     
     * @param  os               an output stream to write differences to
     * @throws SVNException     no repository is found at 
     *                          <code>repositoryRoot</code>
     */
    public void doGetDiff(File repositoryRoot, SVNRevision revision, boolean diffDeleted, boolean diffAdded, boolean diffCopyFrom, OutputStream os) throws SVNException {
        FSFS fsfs = open(repositoryRoot, revision);
        long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
        FSRoot root = fsfs.createRevisionRoot(revNum);
        long baseRevision = revNum - 1;
        if (!SVNRevision.isValidRevisionNumber(baseRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Invalid base revision {0,number,integer}", new Long(baseRevision));
            SVNErrorManager.error(err);
        }
        SVNNodeEditor editor = generateDeltaTree(fsfs, root, baseRevision);
        ISVNGNUDiffGenerator generator = getDiffGenerator();
        generator.setDiffAdded(diffAdded);
        generator.setDiffCopied(diffCopyFrom);
        generator.setDiffDeleted(diffDeleted);
        editor.diff(root, baseRevision, generator, os);
    }

    /**
     * Writes differences of changed files and properties for the 
     * given transaction to the provided output stream. If no special diff generator 
     * {@link #setDiffGenerator(ISVNGNUDiffGenerator) was provided} to 
     * this client a default GNU-style diff generator is used (which 
     * writes differences just like the <code>'svnlook diff'</code> command). 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  transactionName  a transaction name
     * @param  diffDeleted      if <span class="javakeyword">true</span> 
     *                          differences for deleted files are included, 
     *                          otherwise not
     * @param  diffAdded        if <span class="javakeyword">true</span> 
     *                          differences for added files are included, 
     *                          otherwise not
     * @param  diffCopyFrom     if <span class="javakeyword">true</span> 
     *                          writes differences against the copy source 
     *                          (if any), otherwise not     
     * @param  os               an output stream to write differences to
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if the specified transaction is not found
     *                          </li>
     *                          </ul>
     */
    public void doGetDiff(File repositoryRoot, String transactionName, boolean diffDeleted, boolean diffAdded, boolean diffCopyFrom, OutputStream os) throws SVNException {
        FSFS fsfs = open(repositoryRoot, transactionName);
        FSTransactionInfo txn = fsfs.openTxn(transactionName);
        FSRoot root = fsfs.createTransactionRoot(txn.getTxnId());
        long baseRevision = txn.getBaseRevision();

        if (!SVNRevision.isValidRevisionNumber(baseRevision)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Transaction ''{0}'' is not based on a revision; how odd", transactionName);
            SVNErrorManager.error(err);
        }
        SVNNodeEditor editor = generateDeltaTree(fsfs, root, baseRevision);
        ISVNGNUDiffGenerator generator = getDiffGenerator();
        generator.setDiffAdded(diffAdded);
        generator.setDiffCopied(diffCopyFrom);
        generator.setDiffDeleted(diffDeleted);
        editor.diff(root, baseRevision, generator, os);
    }

    /**
     * Returns the value of a versioned property for the specified path in the 
     * given revision. 
     * 
     * <p>
     * <code>path</code> must be absolute, that is it must start with <code>'/'</code>.     
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  propName         a property name
     * @param  path             an absolute path          
     * @param  revision         a revision number
     * @return                  the value of a property
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if <code>path</code> is not found 
     *                          </li>
     *                          </ul>
     */
    public String doGetProperty(File repositoryRoot, String propName, String path, SVNRevision revision) throws SVNException {
        Map props = getProperties(repositoryRoot, propName, path, revision, null, true, false);
        return (String) props.get(propName);
    }
    
    /**
     * Returns versioned properties for the specified path in the 
     * given revision. 
     * 
     * <p>
     * <code>path</code> must be absolute, that is it must start with <code>'/'</code>.     
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  path             an absolute path          
     * @param  revision         a revision number
     * @return                  name (String) to value (String) mappings 
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if <code>path</code> is not found 
     *                          </li>
     *                          </ul>
     */
    public Map doGetProperties(File repositoryRoot, String path, SVNRevision revision) throws SVNException {
        return getProperties(repositoryRoot, null, path, revision, null, false, false);
    }
    
    /**
     * Returns the value of a versioned property for the specified path in the 
     * given transaction. 
     * 
     * <p>
     * <code>path</code> must be absolute, that is it must start with <code>'/'</code>.     
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  propName         a property name
     * @param  path             an absolute path          
     * @param  transactionName  a transaction name
     * @return                  the value of a property
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if <code>path</code> is not found 
     *                          </li>
     *                          <li>if the specified transaction is not found
     *                          </li>
     *                          </ul>
     */
    public String doGetProperty(File repositoryRoot, String propName, String path, String transactionName) throws SVNException {
        Map props = getProperties(repositoryRoot, propName, path, null, transactionName, true, false);
        return (String) props.get(propName);
    }

    /**
     * Returns versioned properties for the specified path in the 
     * given transaction. 
     * 
     * <p>
     * <code>path</code> must be absolute, that is it must start with <code>'/'</code>.     
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  path             an absolute path          
     * @param  transactionName  a transaction name
     * @return                  name (String) to value (String) mappings 
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if <code>path</code> is not found 
     *                          </li>
     *                          <li>if the specified transaction is not found
     *                          </li>
     *                          </ul>
     */
    public Map doGetProperties(File repositoryRoot, String path, String transactionName) throws SVNException {
        return getProperties(repositoryRoot, null, path, null, transactionName, false, false);
    }

    /**
     * Returns the value of a revision property in the given revision. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  propName         a property name
     * @param  revision         a revision number
     * @return                  the value of a revision property
     * @throws SVNException     no repository is found at 
     *                          <code>repositoryRoot</code>
     */
    public String doGetRevisionProperty(File repositoryRoot, String propName, SVNRevision revision) throws SVNException {
        Map revProps = getProperties(repositoryRoot, propName, null, revision, null, true, true);
        return (String) revProps.get(propName);
    }

    /**
     * Returns revision properties in the given revision. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  revision         a revision number
     * @return                  name (String) to value (String) mappings 
     * @throws SVNException     no repository is found at 
     *                          <code>repositoryRoot</code>
     */
    public Map doGetRevisionProperties(File repositoryRoot, SVNRevision revision) throws SVNException {
        return getProperties(repositoryRoot, null, null, revision, null, false, true);
    }
    
    /**
     * Returns the value of a revision property for the given transaction. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  propName         a property name
     * @param  transactionName  a transaction name
     * @return                  the value of a revision property
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if the specified transaction is not found
     *                          </li>
     *                          </ul>
     */
    public String doGetRevisionProperty(File repositoryRoot, String propName, String transactionName) throws SVNException {
        Map revProps = getProperties(repositoryRoot, propName, null, null, transactionName, true, true);
        return (String) revProps.get(propName);
    }

    /**
     * Returns revision properties for the given transaction. 
     * 
     * @param  repositoryRoot   a repository root directory path
     * @param  transactionName  a transaction name
     * @return                  name (String) to value (String) mappings 
     * @throws SVNException     <ul>
     *                          <li>no repository is found at 
     *                          <code>repositoryRoot</code>
     *                          </li>
     *                          <li>if the specified transaction is not found
     *                          </li>
     *                          </ul>
     */
    public Map doGetRevisionProperties(File repositoryRoot, String transactionName) throws SVNException {
        return getProperties(repositoryRoot, null, null, null, transactionName, false, true);
    }
    
    /**
     * Sets a diff generator to be used in <code>doGetDiff()</code> methods of this class.
     * 
     * @param diffGenerator
     * @see   #getDiffGenerator()
     */
    public void setDiffGenerator(ISVNGNUDiffGenerator diffGenerator) {
        myDiffGenerator = diffGenerator;
    }

    /**
     * Returns a diff generator to be used in <code>doGetDiff()</code> methods of this class. 
     * If no generator was provided by a caller, <b>SVNLookClient</b> uses a default one 
     * that prints differences in a GNU-style.
     * 
     * @return  a diff generator
     * @see     #setDiffGenerator(ISVNGNUDiffGenerator)
     */
    public ISVNGNUDiffGenerator getDiffGenerator() {
        if (myDiffGenerator == null) {
            myDiffGenerator = new DefaultSVNGNUDiffGenerator();
        }
        return myDiffGenerator;
    }

    private void getTree(FSFS fsfs, FSRoot root, String path, SVNNodeKind kind, FSID id, boolean includeIDs, int depth, ISVNTreeHandler handler) throws SVNException {
        checkCancelled();
        if (handler != null) {
            handler.handlePath(new SVNAdminPath(path, includeIDs ? id.toString() : null, depth, kind == SVNNodeKind.DIR));
        }
        
        if (kind != SVNNodeKind.DIR) {
            return;
        }
        
        FSRevisionNode node = root.getRevisionNode(path);
        Map entries = node.getDirEntries(fsfs);
        for (Iterator names = entries.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            FSEntry entry = (FSEntry) entries.get(name);
            getTree(fsfs, root, SVNPathUtil.concatToAbs(path, entry.getName()), entry.getType(), includeIDs ? entry.getId() : null, includeIDs, depth + 1, handler);
        }
    }
    
    private Map getProperties(File repositoryRoot, String propName, String path, SVNRevision revision, String txnName, boolean singleProp, boolean revProps) throws SVNException {
        if (propName == null && singleProp) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Missing propname argument");
            SVNErrorManager.error(err);
        }
        if (path == null && !revProps) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Missing repository path argument");
            SVNErrorManager.error(err);
        }

        FSFS fsfs = txnName == null ? open(repositoryRoot, revision) : open(repositoryRoot, txnName);
        FSRoot root = null;
        if (txnName == null) {
            long revNum = SVNAdminHelper.getRevisionNumber(revision, fsfs.getYoungestRevision(), fsfs);
            if (revProps) {
                return fsfs.getRevisionProperties(revNum);
            }
            root = fsfs.createRevisionRoot(revNum);
        } else {
            FSTransactionInfo txn = fsfs.openTxn(txnName);
            if (revProps) {
                return fsfs.getTransactionProperties(txn.getTxnId());
            }
            root = fsfs.createTransactionRoot(txn.getTxnId());
        }

        verifyPath(root, path);
        FSRevisionNode node = root.getRevisionNode(path);
        return node.getProperties(fsfs);
    }

    private void getHistory(FSFS fsfs, String path, long start, long end, boolean crossCopies, boolean includeIDs, ISVNHistoryHandler handler) throws SVNException {
        if (!SVNRevision.isValidRevisionNumber(start)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Invalid start revision {0,number,integer}", new Long(start));
            SVNErrorManager.error(err);
        }
        if (!SVNRevision.isValidRevisionNumber(end)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NO_SUCH_REVISION, "Invalid end revision {0,number,integer}", new Long(end));
            SVNErrorManager.error(err);
        }

        if (start > end) {
            long tmpRev = start;
            start = end;
            end = tmpRev;
        }

        FSRevisionRoot root = fsfs.createRevisionRoot(end);
        FSNodeHistory history = FSNodeHistory.getNodeHistory(root, path);

        do {
            history = history.fsHistoryPrev(crossCopies, fsfs);
            if (history == null) {
                break;
            }

            long revision = history.getHistoryEntry().getRevision();
            if (revision < start) {
                break;
            }

            String id = null;
            if (includeIDs) {
                FSRevisionRoot revRoot = fsfs.createRevisionRoot(revision);
                FSRevisionNode node = revRoot.getRevisionNode(history.getHistoryEntry().getPath());
                id = node.getId().toString();
            }

            if (handler != null) {
                handler.handlePath(new SVNAdminPath(history.getHistoryEntry().getPath(), id, revision));
            }
        } while (history != null);

    }

    private SVNNodeEditor generateDeltaTree(FSFS fsfs, FSRoot root, long baseRevision) throws SVNException {
        FSRevisionRoot baseRoot = fsfs.createRevisionRoot(baseRevision);
        SVNNodeEditor editor = new SVNNodeEditor(fsfs, baseRoot, this);
        FSRepositoryUtil.replay(fsfs, root, "", -1, false, editor);
        return editor;
    }

    private void catFile(FSRoot root, String path, OutputStream out) throws SVNException {
        SVNNodeKind kind = verifyPath(root, path);
        if (kind != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Path ''{0}'' is not a file", path);
            SVNErrorManager.error(err);
        }

        if (out != null) {
            InputStream contents = null;
            try {
                contents = root.getFileStreamForPath(new SVNDeltaCombiner(), path);
                byte[] buffer = new byte[SVNAdminHelper.STREAM_CHUNK_SIZE];
                int len = 0;
                do {
                    checkCancelled();
                    len = contents.read(buffer);
                    out.write(buffer, 0, len);
                } while (len == SVNAdminHelper.STREAM_CHUNK_SIZE);
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe);
            } finally {
                SVNFileUtil.closeFile(contents);
            }
        }
    }

    private SVNNodeKind verifyPath(FSRoot root, String path) throws SVNException {
        SVNNodeKind kind = root.checkNodeKind(path);
        if (kind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FOUND, "Path ''{0}'' does not exist", path);
            SVNErrorManager.error(err);
        }
        return kind;
    }

    private FSFS open(File repositoryRoot, SVNRevision revision) throws SVNException {
        if (revision == null || !revision.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Invalid revision number supplied");
            SVNErrorManager.error(err);
        }

        FSFS fsfs = SVNAdminHelper.openRepository(repositoryRoot);
        return fsfs;
    }

    private FSFS open(File repositoryRoot, String transactionName) throws SVNException {
        if (transactionName == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Missing transaction name");
            SVNErrorManager.error(err);
        }

        return SVNAdminHelper.openRepository(repositoryRoot);
    }
}
