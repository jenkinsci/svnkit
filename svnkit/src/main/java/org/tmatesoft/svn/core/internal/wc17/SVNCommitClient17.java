package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc17.SVNStatus17.ConflictInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.CheckSpecialInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ISVNWCNodeHandler;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.NodeCopyFromField;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.NodeCopyFromInfo;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.PropDiffs;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbLock;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.DefaultSVNCommitHandler;
import org.tmatesoft.svn.core.wc.DefaultSVNCommitParameters;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNCommitPacket;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * The <b>SVNCommitClient</b> class provides methods to perform operations that
 * relate to committing changes to an SVN repository. These operations are
 * similar to respective commands of the native SVN command line client and
 * include ones which operate on working copy items as well as ones that operate
 * only on a repository.
 * <p>
 * Here's a list of the <b>SVNCommitClient</b>'s commit-related methods matched
 * against corresponing commands of the SVN command line client:
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doCommit()</td>
 * <td>'svn commit'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doImport()</td>
 * <td>'svn import'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doDelete()</td>
 * <td>'svn delete URL'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doMkDir()</td>
 * <td>'svn mkdir URL'</td>
 * </tr>
 * </table>
 *
 * @version 1.3
 * @author TMate Software Ltd.
 * @since 1.2
 * @see <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNCommitClient17 extends SVNBaseClient17 {

    private ISVNCommitHandler myCommitHandler;
    private ISVNCommitParameters myCommitParameters;

    /**
     * Constructs and initializes an <b>SVNCommitClient</b> object with the
     * specified run-time configuration and authentication drivers.
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>, then
     * this <b>SVNCommitClient</b> will be using a default run-time
     * configuration driver which takes client-side settings from the default
     * SVN's run-time configuration area but is not able to change those
     * settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNCommitClient</b> will be using a default authentication
     * and network layers driver (see
     * {@link SVNWCUtil#createDefaultAuthenticationManager()}) which uses
     * server-side settings and auth storage from the default SVN's run-time
     * configuration area (or system properties if that area is not found).
     *
     * @param authManager
     *            an authentication and network layers driver
     * @param options
     *            a run-time configuration options driver
     */
    public SVNCommitClient17(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    /**
     * Constructs and initializes an <b>SVNCommitClient</b> object with the
     * specified run-time configuration and repository pool object.
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>, then
     * this <b>SVNCommitClient</b> will be using a default run-time
     * configuration driver which takes client-side settings from the default
     * SVN's run-time configuration area but is not able to change those
     * settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
     * <p/>
     * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
     * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used
     * to create {@link SVNRepository repository access objects}.
     *
     * @param repositoryPool
     *            a repository pool object
     * @param options
     *            a run-time configuration options driver
     */
    public SVNCommitClient17(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    /**
     * @param handler
     * @deprecated use {@link #setCommitHandler(ISVNCommitHandler)} instead
     */
    public void setCommitHander(ISVNCommitHandler handler) {
        myCommitHandler = handler;
    }

    /**
     * Sets an implementation of <b>ISVNCommitHandler</b> to the commit handler
     * that will be used during commit operations to handle commit log messages.
     * The handler will receive a clien's log message and items (represented as
     * <b>SVNCommitItem</b> objects) that will be committed. Depending on
     * implementor's aims the initial log message can be modified (or something
     * else) and returned back.
     * <p>
     * If using <b>SVNCommitClient</b> without specifying any commit handler
     * then a default one will be used - {@link DefaultSVNCommitHandler}.
     *
     * @param handler
     *            an implementor's handler that will be used to handle commit
     *            log messages
     * @see #getCommitHandler()
     * @see ISVNCommitHandler
     */
    public void setCommitHandler(ISVNCommitHandler handler) {
        myCommitHandler = handler;
    }

    /**
     * Returns the specified commit handler (if set) being in use or a default
     * one (<b>DefaultSVNCommitHandler</b>) if no special implementations of
     * <b>ISVNCommitHandler</b> were previously provided.
     *
     * @return the commit handler being in use or a default one
     * @see #setCommitHander(ISVNCommitHandler)
     * @see ISVNCommitHandler
     * @see DefaultSVNCommitHandler
     */
    public ISVNCommitHandler getCommitHandler() {
        if (myCommitHandler == null) {
            myCommitHandler = new DefaultSVNCommitHandler();
        }
        return myCommitHandler;
    }

    /**
     * Sets commit parameters to use.
     * <p>
     * When no parameters are set {@link DefaultSVNCommitParameters default}
     * ones are used.
     *
     * @param parameters
     *            commit parameters
     * @see #getCommitParameters()
     */
    public void setCommitParameters(ISVNCommitParameters parameters) {
        myCommitParameters = parameters;
    }

    /**
     * Returns commit parameters.
     * <p>
     * If no user parameters were previously specified, once creates and returns
     * {@link DefaultSVNCommitParameters default} ones.
     *
     * @return commit parameters
     * @see #setCommitParameters(ISVNCommitParameters)
     */
    public ISVNCommitParameters getCommitParameters() {
        if (myCommitParameters == null) {
            myCommitParameters = new DefaultSVNCommitParameters();
        }
        return myCommitParameters;
    }

    /**
     * Committs removing specified URL-paths from the repository. This call is
     * equivalent to <code>doDelete(urls, commitMessage, null)</code>.
     *
     * @param urls
     *            an array containing URL-strings that represent repository
     *            locations to be removed
     * @param commitMessage
     *            a string to be a commit log message
     * @return information on a new revision as the result of the commit
     * @throws SVNException
     *             if one of the following is true:
     *             <ul>
     *             <li>a URL does not exist
     *             <li>probably some of URLs refer to different repositories
     *             </ul>
     * @see #doDelete(SVNURL[],String,SVNProperties)
     */
    public SVNCommitInfo doDelete(SVNURL[] urls, String commitMessage) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Deletes items from a repository.
     * <p/>
     * If non-<span class="javakeyword">null</span>,
     * <code>revisionProperties</code> holds additional, custom revision
     * properties (<code>String</code> names mapped to {@link SVNPropertyValue}
     * values) to be set on the new revision. This table cannot contain any
     * standard Subversion properties.
     * <p/>
     * {@link #getCommitHandler() Commit handler} will be asked for a commit log
     * message.
     * <p/>
     * If the caller's {@link ISVNEventHandler event handler} is not <span
     * class="javakeyword">null</span> and if the commit succeeds, the handler
     * will be called with {@link SVNEventAction#COMMIT_COMPLETED} event action.
     *
     * @param urls
     *            repository urls to delete
     * @param commitMessage
     *            commit log message
     * @param revisionProperties
     *            custom revision properties
     * @return information about the new committed revision
     * @throws SVNException
     *             in the following cases:
     *             <ul>
     *             <li/>exception with {@link SVNErrorCode#RA_ILLEGAL_URL} error
     *             code - if cannot compute common root url for <code>urls
     *             </code> <li/>exception with {@link SVNErrorCode#FS_NOT_FOUND}
     *             error code - if some of <code>urls</code> does not exist
     *             </ul>
     * @since 1.2.0, SVN 1.5.0
     */
    public SVNCommitInfo doDelete(SVNURL[] urls, String commitMessage, SVNProperties revisionProperties) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Committs a creation of a new directory/directories in the repository.
     *
     * @param urls
     *            an array containing URL-strings that represent new repository
     *            locations to be created
     * @param commitMessage
     *            a string to be a commit log message
     * @return information on a new revision as the result of the commit
     * @throws SVNException
     *             if some of URLs refer to different repositories
     */
    public SVNCommitInfo doMkDir(SVNURL[] urls, String commitMessage) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Creates directory(ies) in a repository.
     * <p/>
     * If <code>makeParents</code> is <span class="javakeyword">true</span>,
     * creates any non-existent parent directories also.
     * <p/>
     * If non-<span class="javakeyword">null</span>,
     * <code>revisionProperties</code> holds additional, custom revision
     * properties (<code>String</code> names mapped to {@link SVNPropertyValue}
     * values) to be set on the new revision. This table cannot contain any
     * standard Subversion properties.
     * <p/>
     * {@link #getCommitHandler() Commit handler} will be asked for a commit log
     * message.
     * <p/>
     * If the caller's {@link ISVNEventHandler event handler} is not <span
     * class="javakeyword">null</span> and if the commit succeeds, the handler
     * will be called with {@link SVNEventAction#COMMIT_COMPLETED} event action.
     *
     * @param urls
     *            repository locations to create
     * @param commitMessage
     *            commit log message
     * @param revisionProperties
     *            custom revision properties
     * @param makeParents
     *            if <span class="javakeyword">true</span>, creates all
     *            non-existent parent directories
     * @return information about the new committed revision
     * @throws SVNException
     *             in the following cases:
     *             <ul>
     *             <li/>exception with {@link SVNErrorCode#RA_ILLEGAL_URL} error
     *             code - if cannot compute common root url for <code>urls
     *             </code> <li/>exception with {@link SVNErrorCode#FS_NOT_FOUND}
     *             error code - if some of <code>urls</code> does not exist
     *             </ul>
     * @since 1.2.0, SVN 1.5.0
     */
    public SVNCommitInfo doMkDir(SVNURL[] urls, String commitMessage, SVNProperties revisionProperties, boolean makeParents) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Committs an addition of a local unversioned file or directory into the
     * repository.
     * <p/>
     * This method is identical to
     * <code>doImport(path, dstURL, commitMessage, null, true, false, SVNDepth.fromRecurse(recursive))</code>.
     *
     * @param path
     *            a local unversioned file or directory to be imported into the
     *            repository
     * @param dstURL
     *            a URL-string that represents a repository location where the
     *            <code>path</code> will be imported
     * @param commitMessage
     *            a string to be a commit log message
     * @param recursive
     *            this flag is relevant only when the <code>path</code> is a
     *            directory: if <span class="javakeyword">true</span> then the
     *            entire directory tree will be imported including all child
     *            directories, otherwise only items located in the directory
     *            itself
     * @return information on a new revision as the result of the commit
     * @throws SVNException
     *             if one of the following is true:
     *             <ul>
     *             <li><code>dstURL</code> is invalid <li>the path denoted by
     *             <code>dstURL</code> already exists <li><code>path</code>
     *             contains a reserved name - <i>'.svn'</i>
     *             </ul>
     * @deprecated use
     *             {@link #doImport(File,SVNURL,String,SVNProperties,boolean,boolean,SVNDepth)}
     *             instead
     */
    public SVNCommitInfo doImport(File path, SVNURL dstURL, String commitMessage, boolean recursive) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Committs an addition of a local unversioned file or directory into the
     * repository.
     * <p/>
     * This method is identical to
     * <code>doImport(path, dstURL, commitMessage, null, useGlobalIgnores, false, SVNDepth.fromRecurse(recursive))</code>.
     *
     * @param path
     *            a local unversioned file or directory to be imported into the
     *            repository
     * @param dstURL
     *            a URL-string that represents a repository location where the
     *            <code>path</code> will be imported
     * @param commitMessage
     *            a string to be a commit log message
     * @param useGlobalIgnores
     *            if <span class="javakeyword">true</span> then those paths that
     *            match global ignore patterns controlled by a config options
     *            driver (see
     *            {@link org.tmatesoft.svn.core.wc.ISVNOptions#getIgnorePatterns()}
     *            ) will not be imported, otherwise global ignore patterns are
     *            not used
     * @param recursive
     *            this flag is relevant only when the <code>path</code> is a
     *            directory: if <span class="javakeyword">true</span> then the
     *            entire directory tree will be imported including all child
     *            directories, otherwise only items located in the directory
     *            itself
     * @return information on a new revision as the result of the commit
     * @throws SVNException
     *             if one of the following is true:
     *             <ul>
     *             <li><code>dstURL</code> is invalid <li>the path denoted by
     *             <code>dstURL</code> already exists <li><code>path</code>
     *             contains a reserved name - <i>'.svn'</i>
     *             </ul>
     * @deprecated use
     *             {@link #doImport(File,SVNURL,String,SVNProperties,boolean,boolean,SVNDepth)}
     *             instead
     */
    public SVNCommitInfo doImport(File path, SVNURL dstURL, String commitMessage, boolean useGlobalIgnores, boolean recursive) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Imports file or directory <code>path</code> into repository directory
     * <code>dstURL</code> at HEAD revision. If some components of
     * <code>dstURL</code> do not exist, then creates parent directories as
     * necessary.
     * <p/>
     * If <code>path</code> is a directory, the contents of that directory are
     * imported directly into the directory identified by <code>dstURL</code>.
     * Note that the directory <code>path</code> itself is not imported -- that
     * is, the base name of <code>path<code> is not part of the import.
     * <p/>
     * If <code>path</code> is a file, then the parent of <code>dstURL</code> is
     * the directory receiving the import. The base name of <code>dstURL</code>
     * is the filename in the repository. In this case if <code>dstURL</code>
     * already exists, throws {@link SVNException}.
     * <p/>
     * If the caller's {@link ISVNEventHandler event handler} is not <span
     * class="javakeyword">null</span> it will be called as the import
     * progresses with {@link SVNEventAction#COMMIT_ADDED} action. If the commit
     * succeeds, the handler will be called with
     * {@link SVNEventAction#COMMIT_COMPLETED} event action.
     * <p/>
     * If non-<span class="javakeyword">null</span>,
     * <code>revisionProperties</code> holds additional, custom revision
     * properties (<code>String</code> names mapped to {@link SVNPropertyValue}
     * values) to be set on the new revision. This table cannot contain any
     * standard Subversion properties.
     * <p/>
     * {@link #getCommitHandler() Commit handler} will be asked for a commit log
     * message.
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, imports just
     * <code>path</code> and nothing below it. If {@link SVNDepth#FILES},
     * imports <code>path</code> and any file children of <code>path</code>. If
     * {@link SVNDepth#IMMEDIATES}, imports <code>path</code>, any file
     * children, and any immediate subdirectories (but nothing underneath those
     * subdirectories). If {@link SVNDepth#INFINITY}, imports <code>path</code>
     * and everything under it fully recursively.
     * <p/>
     * If <code>useGlobalIgnores</code> is <span
     * class="javakeyword">false</span>, doesn't add files or directories that
     * match ignore patterns.
     * <p/>
     * If <code>ignoreUnknownNodeTypes</code> is <span
     * class="javakeyword">false</span>, ignores files of which the node type is
     * unknown, such as device files and pipes.
     *
     * @param path
     *            path to import
     * @param dstURL
     *            import destination url
     * @param commitMessage
     *            commit log message
     * @param revisionProperties
     *            custom revision properties
     * @param useGlobalIgnores
     *            whether matching against global ignore patterns should take
     *            place
     * @param ignoreUnknownNodeTypes
     *            whether to ignore files of unknown node types or not
     * @param depth
     *            tree depth to process
     * @return information about the new committed revision
     * @throws SVNException
     *             in the following cases:
     *             <ul>
     *             <li/>exception with {@link SVNErrorCode#ENTRY_NOT_FOUND}
     *             error code - if <code>path</code> does not exist <li/>
     *             exception with {@link SVNErrorCode#ENTRY_EXISTS} error code -
     *             if <code>dstURL</code> already exists and <code>path</code>
     *             is a file <li/>exception with
     *             {@link SVNErrorCode#CL_ADM_DIR_RESERVED} error code - if
     *             trying to import an item with a reserved SVN name (like
     *             <code>'.svn'</code> or <code>'_svn'</code>)
     *             </ul>
     * @since 1.2.0, New in SVN 1.5.0
     */
    public SVNCommitInfo doImport(File path, SVNURL dstURL, String commitMessage, SVNProperties revisionProperties, boolean useGlobalIgnores, boolean ignoreUnknownNodeTypes, SVNDepth depth)
            throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Committs local changes to the repository.
     * <p/>
     * This method is identical to
     * <code>doCommit(paths, keepLocks, commitMessage, null, null, false, force, SVNDepth.fromRecurse(recursive))</code>.
     *
     * @param paths
     *            an array of local items which should be traversed to commit
     *            changes they have to the repository
     * @param keepLocks
     *            if <span class="javakeyword">true</span> and there are local
     *            items that were locked then the commit will left them locked,
     *            otherwise the items will be unlocked after the commit succeeds
     * @param commitMessage
     *            a string to be a commit log message
     * @param force
     *            <span class="javakeyword">true</span> to force a non-recursive
     *            commit; if <code>recursive</code> is set to <span
     *            class="javakeyword">true</span> the <code>force</code> flag is
     *            ignored
     * @param recursive
     *            relevant only for directory items: if <span
     *            class="javakeyword">true</span> then the entire directory tree
     *            will be committed including all child directories, otherwise
     *            only items located in the directory itself
     * @return information on a new revision as the result of the commit
     * @throws SVNException
     * @deprecated use
     *             {@link #doCommit(File[],boolean,String,SVNProperties,String[],boolean,boolean,SVNDepth)}
     *             instead
     */
    public SVNCommitInfo doCommit(File[] paths, boolean keepLocks, String commitMessage, boolean force, boolean recursive) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Commits files or directories into repository.
     * <p/>
     * <code>paths</code> need not be canonicalized nor condensed; this method
     * will take care of that. If
     * <code>targets has zero elements, then do nothing and return
     * immediately without error.
     * <p/>
     * If non-<span class="javakeyword">null</span>, <code>revisionProperties</code>
     * holds additional, custom revision properties (<code>String</code> names
     * mapped to {@link SVNPropertyValue} values) to be set on the new revision.
     * This table cannot contain any standard Subversion properties.
     * <p/>
     * If the caller's {@link ISVNEventHandler event handler} is not <span
     * class="javakeyword">null</span> it will be called as the commit
     * progresses with any of the following actions:
     * {@link SVNEventAction#COMMIT_MODIFIED},
     * {@link SVNEventAction#COMMIT_ADDED},
     * {@link SVNEventAction#COMMIT_DELETED},
     * {@link SVNEventAction#COMMIT_REPLACED}. If the commit succeeds, the
     * handler will be called with {@link SVNEventAction#COMMIT_COMPLETED} event
     * action.
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#INFINITY}, commits all changes
     * to and below named targets. If <code>depth</code> is
     * {@link SVNDepth#EMPTY}, commits only named targets (that is, only
     * property changes on named directory targets, and property and content
     * changes for named file targets). If <code>depth</code> is
     * {@link SVNDepth#FILES}, behaves as above for named file targets, and for
     * named directory targets, commits property changes on a named directory
     * and all changes to files directly inside that directory. If
     * {@link SVNDepth#IMMEDIATES}, behaves as for {@link SVNDepth#FILES}, and
     * for subdirectories of any named directory target commits as though for
     * {@link SVNDepth#EMPTY}.
     * <p/>
     * Unlocks paths in the repository, unless <code>keepLocks</code> is <span
     * class="javakeyword">true</span>.
     * <p/>
     * <code>changelists</code> is an array of <code>String</code> changelist
     * names, used as a restrictive filter on items that are committed; that is,
     * doesn't commit anything unless it's a member of one of those changelists.
     * After the commit completes successfully, removes changelist associations
     * from the targets, unless <code>keepChangelist</code> is set. If
     * <code>changelists</code> is empty (or altogether <span
     * class="javakeyword">null</span>), no changelist filtering occurs.
     * <p/>
     * If no exception is thrown and {@link SVNCommitInfo#getNewRevision()} is
     * invalid (<code>&lt;0</code>), then the commit was a no-op; nothing needed
     * to be committed.
     *
     * @param paths
     *            paths to commit
     * @param keepLocks
     *            whether to unlock or not files in the repository
     * @param commitMessage
     *            commit log message
     * @param revisionProperties
     *            custom revision properties
     * @param changelists
     *            changelist names array
     * @param keepChangelist
     *            whether to remove <code>changelists</code> or not
     * @param force
     *            <span class="javakeyword">true</span> to force a non-recursive
     *            commit; if <code>depth</code> is {@link SVNDepth#INFINITY} the
     *            <code>force</code> flag is ignored
     * @param depth
     *            tree depth to process
     * @return information about the new committed revision
     * @throws SVNException
     * @since 1.2.0, New in Subversion 1.5.0
     */
    public SVNCommitInfo doCommit(File[] paths, boolean keepLocks, String commitMessage, SVNProperties revisionProperties, String[] changelists, boolean keepChangelist, boolean force, SVNDepth depth)
            throws SVNException {
        SVNCommitPacket packet = doCollectCommitItems(paths, keepLocks, force, depth, changelists);
        try {
            packet = packet.removeSkippedItems();
            return doCommit(packet, keepLocks, keepChangelist, commitMessage, revisionProperties);
        } finally {
            if (packet != null) {
                packet.unlockWC(getContext());
                packet.dispose();
            }
        }
    }

    /**
     * Committs local changes made to the Working Copy items to the repository.
     * <p>
     * This method is identical to
     * <code>doCommit(commitPacket, keepLocks, false, commitMessage, null)</code>.
     * <p>
     * <code>commitPacket</code> contains commit items ({@link SVNCommitItem})
     * which represent local Working Copy items that were changed and are to be
     * committed. Commit items are gathered into a single
     * {@link SVNCommitPacket}by invoking
     * {@link #doCollectCommitItems(File[],boolean,boolean,boolean)
     * doCollectCommitItems()}.
     *
     * @param commitPacket
     *            a single object that contains items to be committed
     * @param keepLocks
     *            if <span class="javakeyword">true</span> and there are local
     *            items that were locked then the commit will left them locked,
     *            otherwise the items will be unlocked after the commit succeeds
     * @param commitMessage
     *            a string to be a commit log message
     * @return information on a new revision as the result of the commit
     * @throws SVNException
     * @see SVNCommitItem
     */
    public SVNCommitInfo doCommit(SVNCommitPacket commitPacket, boolean keepLocks, String commitMessage) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Commits files or directories into repository.
     * <p/>
     * This method is identical to
     * {@link #doCommit(File[],boolean,String,SVNProperties,String[],boolean,boolean,SVNDepth)}
     * except for it receives a commit packet instead of paths array. The
     * aforementioned method collects commit items into a commit packet given
     * working copy paths. This one accepts already collected commit items
     * provided in <code>commitPacket</code>.
     * <p/>
     * <code>commitPacket</code> contains commit items ({@link SVNCommitItem})
     * which represent local Working Copy items that are to be committed. Commit
     * items are gathered in a single {@link SVNCommitPacket} by invoking either
     * {@link #doCollectCommitItems(File[],boolean,boolean,SVNDepth,String[])}
     * or
     * {@link #doCollectCommitItems(File[],boolean,boolean,SVNDepth,boolean,String[])}.
     * <p/>
     * For more details on parameters, please, refer to
     * {@link #doCommit(File[],boolean,String,SVNProperties,String[],boolean,boolean,SVNDepth)}.
     *
     * @param commitPacket
     *            a single object that contains items to be committed
     * @param keepLocks
     *            if <span class="javakeyword">true</span> and there are local
     *            items that were locked then the commit will left them locked,
     *            otherwise the items will be unlocked after the commit succeeds
     * @param keepChangelist
     *            whether to remove changelists or not
     * @param commitMessage
     *            commit log message
     * @param revisionProperties
     *            custom revision properties
     * @return information about the new committed revision
     * @throws SVNException
     * @since 1.2.0, SVN 1.5.0
     */
    public SVNCommitInfo doCommit(SVNCommitPacket commitPacket, boolean keepLocks, boolean keepChangelist, String commitMessage, SVNProperties revisionProperties) throws SVNException {
        SVNCommitInfo[] info = doCommit(new SVNCommitPacket[] {
            commitPacket
        }, keepLocks, keepChangelist, commitMessage, revisionProperties);
        if (info != null && info.length > 0) {
            if (info[0].getErrorMessage() != null && info[0].getErrorMessage().getErrorCode() != SVNErrorCode.REPOS_POST_COMMIT_HOOK_FAILED) {
                SVNErrorManager.error(info[0].getErrorMessage(), SVNLogType.DEFAULT);
            }
            return info[0];
        }
        return SVNCommitInfo.NULL;
    }

    /**
     * Committs local changes, made to the Working Copy items, to the
     * repository.
     * <p>
     * <code>commitPackets</code> is an array of packets that contain commit
     * items (<b>SVNCommitItem</b>) which represent local Working Copy items
     * that were changed and are to be committed. Commit items are gathered in a
     * single <b>SVNCommitPacket</b> by invoking
     * {@link #doCollectCommitItems(File[],boolean,boolean,boolean)
     * doCollectCommitItems()}.
     * <p>
     * This allows to commit separate trees of Working Copies "belonging" to
     * different repositories. One packet per one repository. If repositories
     * are different (it means more than one commit will be done),
     * <code>commitMessage</code> may be replaced by a commit handler to be a
     * specific one for each commit.
     * <p>
     * This method is identical to
     * <code>doCommit(commitPackets, keepLocks, false, commitMessage, null)</code>.
     *
     * @param commitPackets
     *            logically grouped items to be committed
     * @param keepLocks
     *            if <span class="javakeyword">true</span> and there are local
     *            items that were locked then the commit will left them locked,
     *            otherwise the items will be unlocked after the commit succeeds
     * @param commitMessage
     *            a string to be a commit log message
     * @return committed information
     * @throws SVNException
     */
    public SVNCommitInfo[] doCommit(SVNCommitPacket[] commitPackets, boolean keepLocks, String commitMessage) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Commits files or directories into repository.
     * <p>
     * <code>commitPackets</code> is an array of packets that contain commit
     * items ({@link SVNCommitItem}) which represent local Working Copy items
     * that were changed and are to be committed. Commit items are gathered in a
     * single {@link SVNCommitPacket}by invoking
     * {@link #doCollectCommitItems(File[],boolean,boolean,SVNDepth,String[])}
     * or
     * {@link #doCollectCommitItems(File[],boolean,boolean,SVNDepth,boolean,String[])}.
     * <p>
     * This allows to commit items from separate Working Copies checked out from
     * the same or different repositories. For each commit packet
     * {@link #getCommitHandler() commit handler} is invoked to produce a commit
     * message given the one <code>commitMessage</code> passed to this method.
     * Each commit packet is committed in a separate transaction.
     * <p/>
     * For more details on parameters, please, refer to
     * {@link #doCommit(File[],boolean,String,SVNProperties,String[],boolean,boolean,SVNDepth)}.
     *
     * @param commitPackets
     *            commit packets containing commit commit items per one commit
     * @param keepLocks
     *            if <span class="javakeyword">true</span> and there are local
     *            items that were locked then the commit will left them locked,
     *            otherwise the items will be unlocked by the commit
     * @param keepChangelist
     *            whether to remove changelists or not
     * @param commitMessage
     *            a string to be a commit log message
     * @param revisionProperties
     *            custom revision properties
     * @return information about the new committed revisions
     * @throws SVNException
     * @since 1.2.0, SVN 1.5.0
     */
    public SVNCommitInfo[] doCommit(SVNCommitPacket[] commitPackets, boolean keepLocks, boolean keepChangelist, String commitMessage, SVNProperties revisionProperties) throws SVNException {
        if (commitPackets == null || commitPackets.length == 0) {
            return new SVNCommitInfo[0];
        }
        Collection tmpFiles = null;
        SVNCommitInfo info = null;
        ISVNEditor commitEditor = null;
        Collection infos = new ArrayList();
        for (int p = 0; p < commitPackets.length; p++) {
            SVNCommitPacket commitPacket = commitPackets[p].removeSkippedItems();
            if (commitPacket.getCommitItems().length == 0) {
                continue;
            }
            boolean foundChangedPath = false;
            for (SVNCommitItem item : commitPacket.getCommitItems()) {
                if (item.isAdded() || item.isDeleted() || item.isCopied() || item.isContentsModified() || item.isPropertiesModified()) {
                    foundChangedPath = true;
                }
            }
            if (!foundChangedPath) {
                continue;
            }
            try {
                commitMessage = getCommitHandler().getCommitMessage(commitMessage, commitPacket.getCommitItems());
                if (commitMessage == null) {
                    infos.add(SVNCommitInfo.NULL);
                    continue;
                }
                Map<String, SVNCommitItem> committables = new TreeMap();
                Map<File, SVNChecksum> md5Checksums = new HashMap<File, SVNChecksum>();
                Map<File, SVNChecksum> sha1Checksums = new HashMap<File, SVNChecksum>();
                SVNURL baseURL = SVNCommitUtil.translateCommitables(commitPacket.getCommitItems(), committables);
                Map lockTokens = SVNCommitUtil.translateLockTokens(commitPacket.getLockTokens(), baseURL.toString());
                SVNCommitItem firstItem = commitPacket.getCommitItems()[0];
                SVNRepository repository = createRepository(baseURL, firstItem.getFile(), true);
                SVNCommitMediator17 mediator = new SVNCommitMediator17(getContext(), committables);
                tmpFiles = mediator.getTmpFiles();
                String repositoryRoot = repository.getRepositoryRoot(true).getPath();
                SVNPropertiesManager.validateRevisionProperties(revisionProperties);
                commitEditor = repository.getCommitEditor(commitMessage, lockTokens, keepLocks, revisionProperties, mediator);
                info = SVNCommitter17.commit(getContext(), mediator.getTmpFiles(), committables, repositoryRoot, commitEditor, md5Checksums, sha1Checksums);

                SVNWCCommittedQueue queue = new SVNWCCommittedQueue();
                for (SVNCommitItem item : commitPacket.getCommitItems()) {
                    postProcessCommitItem(queue, item, keepChangelist, keepLocks, md5Checksums.get(item.getFile()), sha1Checksums.get(item.getFile()));
                }

                assert (info != null);
                processCommittedQueue(queue, info.getNewRevision(), info.getDate(), info.getAuthor());

            } catch (SVNException e) {
                if (e instanceof SVNCancelException) {
                    throw e;
                }
                SVNErrorMessage err = e.getErrorMessage().wrap("Commit failed (details follow):");
                infos.add(new SVNCommitInfo(-1, null, null, err));
                dispatchEvent(SVNEventFactory.createErrorEvent(err, SVNEventAction.COMMIT_COMPLETED), ISVNEventHandler.UNKNOWN);
                continue;
            } finally {
                if (info == null && commitEditor != null) {
                    try {
                        commitEditor.abortEdit();
                    } catch (SVNException e) {
                    }
                }
                if (tmpFiles != null) {
                    for (Iterator files = tmpFiles.iterator(); files.hasNext();) {
                        File file = (File) files.next();
                        file.delete();
                    }
                }
                if (commitPacket != null) {
                    commitPacket.dispose();
                }
            }
            infos.add(info != null ? info : SVNCommitInfo.NULL);
        }
        sleepForTimeStamp();
        return (SVNCommitInfo[]) infos.toArray(new SVNCommitInfo[infos.size()]);
    }

    private void postProcessCommitItem(SVNWCCommittedQueue queue, SVNCommitItem item, boolean keepChangelists, boolean keepLocks, SVNChecksum md5Checksum, SVNChecksum sha1Checksum) {
        boolean loopRecurse = false;
        if (item.isAdded() && (item.getKind() == SVNNodeKind.DIR) && (item.getCopyFromURL() != null)) {
            loopRecurse = true;
        }
        boolean removeLock = !keepLocks && item.isLocked();
        queueCommitted(queue, item.getFile(), loopRecurse, item.getIncomingProperties(), removeLock, !keepChangelists, md5Checksum, sha1Checksum);
    }

    private void queueCommitted(SVNWCCommittedQueue queue, File localAbsPath, boolean recurse, Map wcPropChanges, boolean removeLock, boolean removeChangelist, SVNChecksum md5Checksum,
            SVNChecksum sha1Checksum) {
        assert (SVNFileUtil.isAbsolute(localAbsPath));
        queue.haveRecursive |= recurse;
        SVNWCCommittedQueueItem cqi = new SVNWCCommittedQueueItem();
        cqi.localAbspath = localAbsPath;
        cqi.recurse = recurse;
        cqi.noUnlock = !removeLock;
        cqi.keepChangelist = !removeChangelist;
        cqi.md5Checksum = md5Checksum;
        cqi.sha1Checksum = sha1Checksum;
        cqi.newDavCache = wcPropChanges;
        queue.queue.put(localAbsPath, cqi);
    }

    private void processCommittedQueue(SVNWCCommittedQueue queue, long newRevision, Date revDate, String revAuthor) throws SVNException {
        for (SVNWCCommittedQueueItem cqi : queue.queue.values()) {
            if (queue.haveRecursive && haveRecursiveParent(queue.queue, cqi)) {
                continue;
            }
            processCommittedInternal(cqi.localAbspath, cqi.recurse, true, newRevision, new SVNDate(revDate.getTime(), 0), revAuthor, cqi.newDavCache, cqi.noUnlock, cqi.keepChangelist,
                    cqi.md5Checksum, cqi.sha1Checksum, queue);
            getContext().wqRun(cqi.localAbspath);
        }
        queue.queue.clear();
    }

    private boolean haveRecursiveParent(Map<File, SVNWCCommittedQueueItem> queue, SVNWCCommittedQueueItem item) {
        File localAbspath = item.localAbspath;
        for (SVNWCCommittedQueueItem qi : queue.values()) {
            if (qi == item) {
                continue;
            }
            if (qi.recurse && SVNWCUtils.isChild(qi.localAbspath, localAbspath)) {
                return true;
            }
        }
        return false;
    }

    private void processCommittedInternal(File localAbspath, boolean recurse, boolean topOfRecurse, long newRevision, SVNDate revDate, String revAuthor, Map newDavCache, boolean noUnlock,
            boolean keepChangelist, SVNChecksum md5Checksum, SVNChecksum sha1Checksum, SVNWCCommittedQueue queue) throws SVNException {
        SVNWCContext ctx = getContext();
        ISVNWCDb db = ctx.getDb();
        SVNWCDbKind kind = db.readKind(localAbspath, true);
        processCommittedLeaf(localAbspath, !topOfRecurse, newRevision, revDate, revAuthor, newDavCache, noUnlock, keepChangelist, sha1Checksum);
        if (recurse && kind == SVNWCDbKind.Dir) {
            ctx.wqRun(localAbspath);
            kind = db.readKind(localAbspath, true);
            if (kind == SVNWCDbKind.Unknown) {
                return;
            }
            Set<String> children = db.readChildren(localAbspath);
            for (String name : children) {
                File thisAbspath = SVNFileUtil.createFilePath(localAbspath, name);
                WCDbInfo readInfo = db.readInfo(thisAbspath, InfoField.status);
                SVNWCDbStatus status = readInfo.status;
                kind = readInfo.kind;
                if (status == SVNWCDbStatus.Excluded) {
                    continue;
                }
                md5Checksum = null;
                sha1Checksum = null;
                if (kind != SVNWCDbKind.Dir) {
                    if (status == SVNWCDbStatus.Deleted) {
                        boolean replaced = ctx.isNodeReplaced(localAbspath);
                        if (replaced)
                            continue;
                    }
                    if (queue != null) {
                        SVNWCCommittedQueueItem cqi = queue.queue.get(thisAbspath);
                        if (cqi != null) {
                            md5Checksum = cqi.md5Checksum;
                            sha1Checksum = cqi.sha1Checksum;
                        }
                    }
                }
                processCommittedInternal(thisAbspath, true, false, newRevision, revDate, revAuthor, null, true, keepChangelist, md5Checksum, sha1Checksum, queue);
                if (kind == SVNWCDbKind.Dir) {
                    ctx.wqRun(thisAbspath);
                }
            }
        }
    }

    private void processCommittedLeaf(File localAbspath, boolean viaRecurse, long newRevnum, SVNDate newChangedDate, String newChangedAuthor, Map newDavCache, boolean noUnlock,
            boolean keepChangelist, SVNChecksum checksum) throws SVNException {
        long newChangedRev = newRevnum;
        assert (SVNFileUtil.isAbsolute(localAbspath));
        WCDbInfo readInfo = getContext().getDb().readInfo(localAbspath, InfoField.status, InfoField.kind, InfoField.checksum);
        SVNWCDbStatus status = readInfo.status;
        SVNWCDbKind kind = readInfo.kind;
        SVNChecksum copiedChecksum = readInfo.checksum;
        File admAbspath;
        if (kind == SVNWCDbKind.Dir) {
            admAbspath = localAbspath;
        } else {
            admAbspath = SVNFileUtil.getFileDir(localAbspath);
        }
        getContext().writeCheck(admAbspath);
        if (status == SVNWCDbStatus.Deleted) {
            getContext().wqAddDeletionPostCommit(localAbspath, newRevnum, noUnlock);
            return;
        }
        if (kind != SVNWCDbKind.Dir) {
            if (checksum == null) {
                assert (copiedChecksum != null);
                checksum = copiedChecksum;
                if (viaRecurse) {
                    readInfo = getContext().getDb().readInfo(localAbspath, InfoField.changedRev, InfoField.changedDate, InfoField.changedAuthor, InfoField.propsMod);
                    long changedRev = readInfo.changedRev;
                    String changedAuthor = readInfo.changedAuthor;
                    SVNDate changedDate = readInfo.changedDate;
                    boolean propsModified = readInfo.propsMod;
                    if (!propsModified) {
                        newChangedRev = changedRev;
                        newChangedDate = changedDate;
                        newChangedAuthor = changedAuthor;
                    }
                }
            }
        } else {
            /*
             * ### If we can determine that nothing below this node was changed
             * ### via this commit, we should keep new_changed_rev at its old
             * ### value, like how we handle files.
             */
        }
        getContext().wqAddPostCommit(localAbspath, newRevnum, newChangedRev, newChangedDate, newChangedAuthor, checksum, newDavCache, keepChangelist, noUnlock);
    }

    private static class SVNWCCommittedQueue {

        public Map<File, SVNWCCommittedQueueItem> queue = new TreeMap<File, SVNCommitClient17.SVNWCCommittedQueueItem>(SVNCommitUtil.FILE_COMPARATOR);
        public boolean haveRecursive = false;
    };

    private static class SVNWCCommittedQueueItem {

        public File localAbspath;
        public boolean recurse;
        public boolean noUnlock;
        public boolean keepChangelist;
        public SVNChecksum md5Checksum;
        public SVNChecksum sha1Checksum;
        public Map newDavCache;
    };

    /**
     * Collects commit items (containing detailed information on each Working
     * Copy item that was changed and need to be committed to the repository)
     * into a single {@link SVNCommitPacket}.
     * <p/>
     * This method is equivalent to
     * <code>doCollectCommitItems(paths, keepLocks, force, SVNDepth.fromRecurse(recursive), null)</code>.
     *
     * @param paths
     *            an array of local items which should be traversed to collect
     *            information on every changed item (one <b>SVNCommitItem</b>
     *            per each modified local item)
     * @param keepLocks
     *            if <span class="javakeyword">true</span> and there are local
     *            items that were locked then these items will be left locked
     *            after traversing all of them, otherwise the items will be
     *            unlocked
     * @param force
     *            forces collecting commit items for a non-recursive commit
     * @param recursive
     *            relevant only for directory items: if <span
     *            class="javakeyword">true</span> then the entire directory tree
     *            will be traversed including all child directories, otherwise
     *            only items located in the directory itself will be processed
     * @return an <b>SVNCommitPacket</b> containing all Working Copy items
     *         having local modifications and represented as
     *         <b>SVNCommitItem</b> objects; if no modified items were found
     *         then {@link SVNCommitPacket#EMPTY} is returned
     * @throws SVNException
     * @deprecated use
     *             {@link #doCollectCommitItems(File[],boolean,boolean,SVNDepth,String[])}
     *             instead
     */
    public SVNCommitPacket doCollectCommitItems(File[] paths, boolean keepLocks, boolean force, boolean recursive) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Collects commit items (containing detailed information on each Working
     * Copy item that contains changes and need to be committed to the
     * repository) into a single {@link SVNCommitPacket}. Further this commit
     * packet can be passed to
     * {@link #doCommit(SVNCommitPacket,boolean,boolean,String,SVNProperties)}.
     * <p/>
     * For more details on parameters, please, refer to
     * {@link #doCommit(File[],boolean,String,SVNProperties,String[],boolean,boolean,SVNDepth)}.
     *
     * @param paths
     *            an array of local items which should be traversed to collect
     *            information on every changed item (one <b>SVNCommitItem</b>
     *            per each modified local item)
     * @param keepLocks
     *            if <span class="javakeyword">true</span> and there are local
     *            items that were locked then these items will be left locked
     *            after traversing all of them, otherwise the items will be
     *            unlocked
     * @param force
     *            forces collecting commit items for a non-recursive commit
     * @param depth
     *            tree depth to process
     * @param changelists
     *            changelist names array
     * @return commit packet containing commit items
     * @throws SVNException
     * @since 1.2.0
     */
    public SVNCommitPacket doCollectCommitItems(File[] paths, boolean keepLocks, boolean force, SVNDepth depth, String[] changelists) throws SVNException {
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        if (depth == SVNDepth.UNKNOWN) {
            depth = SVNDepth.INFINITY;
        }
        if (paths == null || paths.length == 0) {
            return SVNCommitPacket.EMPTY;
        }
        Collection<String> targets = new ArrayList();

        String[] validatedPaths = new String[paths.length];
        for (int i = 0; i < paths.length; i++) {
            checkCancelled();
            File file = paths[i];
            validatedPaths[i] = file.getAbsolutePath().replace(File.separatorChar, '/');
        }
        String rootPath = SVNPathUtil.condencePaths(validatedPaths, targets, depth == SVNDepth.INFINITY);

        if (rootPath == null) {
            return null;
        }

        File baseDir = new File(rootPath).getAbsoluteFile();
        rootPath = baseDir.getAbsolutePath().replace(File.separatorChar, '/');

        if (targets.isEmpty()) {
            checkCancelled();
            String target = getContext().getActualTarget(baseDir);
            targets.add(target);
            if (!"".equals(target)) {
                baseDir = baseDir.getParentFile();
            }
        }

        getContext().acquireWriteLock(baseDir, false, false);

        for (String targetPath : targets) {
            checkNonrecursiveDirDelete(SVNFileUtil.createFilePath(baseDir, targetPath), depth);
        }

        try {
            Map lockTokens = new SVNHashMap();
            checkCancelled();
            Collection changelistsSet = changelists != null ? new SVNHashSet() : null;
            if (changelists != null) {
                for (int j = 0; j < changelists.length; j++) {
                    changelistsSet.add(changelists[j]);
                }
            }
            SVNCommitItem[] commitItems = harvestCommitables(baseDir, targets, lockTokens, !keepLocks, depth, force, changelistsSet, getCommitParameters());
            boolean hasModifications = false;
            checkCancelled();
            for (int i = 0; commitItems != null && i < commitItems.length; i++) {
                SVNCommitItem commitItem = commitItems[i];
                if (commitItem.isAdded() || commitItem.isDeleted() || commitItem.isContentsModified() || commitItem.isPropertiesModified() || commitItem.isCopied()) {
                    hasModifications = true;
                    break;
                }
            }
            if (!hasModifications) {
                getContext().releaseWriteLock(baseDir);
                return SVNCommitPacket.EMPTY;
            }
            return new SVNCommitPacket(baseDir, commitItems, lockTokens);
        } catch (SVNException e) {
            getContext().releaseWriteLock(baseDir);
            if (e instanceof SVNCancelException) {
                throw e;
            }
            SVNErrorMessage nestedErr = e.getErrorMessage();
            SVNErrorMessage err = SVNErrorMessage.create(nestedErr.getErrorCode(), "Commit failed (details follow):");
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
            return null;
        }
    }

    private SVNCommitItem[] harvestCommitables(File baseDir, Collection paths, Map lockTokens, boolean justLocked, SVNDepth depth, boolean force, Collection changelistsSet,
            ISVNCommitParameters commitParameters) throws SVNException {

        Map<String, SVNCommitItem> committables = new HashMap<String, SVNCommitItem>();
        Map danglers = new SVNHashMap();
        Iterator targets = paths.iterator();

        SVNURL reposRootUrl = null;

        while (targets.hasNext()) {

            File targetAbsPath = SVNFileUtil.createFilePath(baseDir, (String) targets.next());
            SVNNodeKind kind = getContext().readKind(targetAbsPath, false);

            if (kind == SVNNodeKind.NONE) {
                SVNTreeConflictDescription conflict = getContext().getTreeConflict(targetAbsPath);
                if (conflict != null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, "Aborting commit: ''{0}'' remains in conflict", conflict.getPath());
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "''{0}'' is not under version control", targetAbsPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }

            if (reposRootUrl == null) {
                reposRootUrl = getContext().getNodeReposInfo(targetAbsPath, true, true).reposRootUrl;
            }

            File reposRelPath = getContext().getNodeReposRelPath(targetAbsPath);
            if (reposRelPath == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry for ''{0}'' has no URL", targetAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            boolean isAdded = getContext().isNodeAdded(targetAbsPath);
            if (isAdded) {
                File parentAbsPath = SVNFileUtil.getFileDir(targetAbsPath);
                try {
                    isAdded = getContext().isNodeAdded(parentAbsPath);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "''{0}'' is scheduled for addition within unversioned parent", targetAbsPath);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                }
                if (isAdded) {
                    danglers.put(parentAbsPath, targetAbsPath);
                }
            }

            bailOnTreeConflictedAncestor(targetAbsPath);

            harvestCommittables(committables, lockTokens, targetAbsPath, reposRootUrl, null, false, false, false, depth, justLocked, changelistsSet);

        }

        for (Iterator i = danglers.keySet().iterator(); i.hasNext();) {
            File danglingParent = (File) i.next();
            File danglingChild = (File) danglers.get(danglingParent);
            validateDangler(committables, danglingParent, danglingChild);
        }

        return committables.values().toArray(new SVNCommitItem[committables.values().size()]);

    }

    private void checkNonrecursiveDirDelete(File targetAbspath, SVNDepth depth) throws SVNException {
        SVNNodeKind kind = getContext().readKind(targetAbspath, false);
        File lockAbspath;
        if (kind == SVNNodeKind.DIR) {
            lockAbspath = targetAbspath;
        } else {
            lockAbspath = SVNFileUtil.getFileDir(targetAbspath);
        }
        boolean lockedHere = getContext().getDb().isWCLockOwns(lockAbspath, false);
        if (!lockedHere) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Are all targets part of the same working copy?");
            SVNErrorManager.error(err, SVNLogType.WC);
            return;
        }
        if (depth != SVNDepth.INFINITY) {
            if (kind == SVNNodeKind.DIR) {
                /*
                SVNStatus17 status = getContext().internalStatus(targetAbspath);
                if (status.getNodeStatus() == SVNStatusType.STATUS_DELETED || status.getNodeStatus() == SVNStatusType.STATUS_REPLACED) {
                    List<File> children = getContext().getNodeChildren(targetAbspath, true);
                    if (children.size() > 0) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot non-recursively commit a directory deletion of a directory with child nodes");
                        SVNErrorManager.error(err, SVNLogType.CLIENT);
                        return;
                    }
                }*/
            }
        }
    }

    private void validateDangler(Map<String, SVNCommitItem> committables, File danglingParent, File danglingChild) throws SVNException {
        if (!committables.containsKey(SVNFileUtil.getFilePath(danglingParent))) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET,
                    "''{0}'' is not under version control and is not part of the commit, yet its child ''{1}'' is part of the commit", new Object[] {
                            danglingParent, danglingChild
                    });
            SVNErrorManager.error(err, SVNLogType.CLIENT);
            return;
        }
    }

    private void bailOnTreeConflictedAncestor(File firstAbspath) throws SVNException {
        File localAbspath;
        File parentAbspath;
        boolean wcRoot;
        boolean treeConflicted;
        localAbspath = firstAbspath;
        while (true) {
            wcRoot = getContext().checkWCRoot(localAbspath, false).wcRoot;
            if (wcRoot) {
                break;
            }
            parentAbspath = SVNFileUtil.getFileDir(localAbspath);
            treeConflicted = getContext().getConflicted(parentAbspath, false, false, true).treeConflicted;
            if (treeConflicted) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, "Aborting commit: ''{0}'' remains in tree-conflict", localAbspath);
                SVNErrorManager.error(err, SVNLogType.WC);
                return;
            }
            localAbspath = parentAbspath;
        }
    }

    private void harvestCommittables(Map<String, SVNCommitItem> committables, Map lockTokens, File localAbsPath, SVNURL reposRootUrl, File reposRelpath, boolean addsOnly, boolean copyMode,
            boolean copyModeRoot, SVNDepth depth, boolean justLocked, Collection changelistsSet) throws SVNException {
        assert (SVNFileUtil.isAbsolute(localAbsPath));
        if (committables.containsKey(SVNFileUtil.getFilePath(localAbsPath))) {
            return;
        }
        assert ((copyMode && reposRelpath != null) || (!copyMode && reposRelpath == null));
        assert ((copyModeRoot && copyMode) || !copyModeRoot);
        assert ((justLocked && lockTokens != null) || !justLocked);
        checkCancelled();

        SVNNodeKind dbKind = getContext().readKind(localAbsPath, true);
        if ((dbKind != SVNNodeKind.FILE) && (dbKind != SVNNodeKind.DIR)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Unknown entry kind for ''{0}''", localAbsPath);
            SVNErrorManager.error(err, SVNLogType.WC);
            return;
        }
        getContext();
        CheckSpecialInfo checkSpecial = SVNWCContext.checkSpecialPath(localAbsPath);
        SVNNodeKind workingKind = checkSpecial.kind;
        boolean isSpecial = checkSpecial.isSpecial;
        if ((workingKind != SVNNodeKind.FILE) && (workingKind != SVNNodeKind.DIR) && (workingKind != SVNNodeKind.NONE)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Unknown entry kind for ''{0}''", localAbsPath);
            SVNErrorManager.error(err, SVNLogType.WC);
            return;
        }
        boolean matchesChangelists = getContext().isChangelistMatch(localAbsPath, changelistsSet);
        if (workingKind != SVNNodeKind.DIR && workingKind != SVNNodeKind.NONE && !matchesChangelists) {
            return;
        }
        String propval = getContext().getProperty(localAbsPath, SVNProperty.SPECIAL);
        if ((((propval == null) && (isSpecial)) || ((propval != null) && (!isSpecial))) && (workingKind != SVNNodeKind.NONE)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "Entry ''{0}'' has unexpectedly changed special status", localAbsPath);
            SVNErrorManager.error(err, SVNLogType.WC);
            return;
        }
        boolean isFileExternal = getContext().isFileExternal(localAbsPath);
        if (isFileExternal && copyMode) {
            return;
        }
        if (matchesChangelists) {
            ConflictInfo conflicted = getContext().getConflicted(localAbsPath, true, true, true);
            if (conflicted.textConflicted || conflicted.propConflicted || conflicted.treeConflicted) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, "Aborting commit: ''{0}'' remains in conflict", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
                return;
            }
        }
        bailOnTreeConflictedChildren(localAbsPath, dbKind, depth, changelistsSet);
        File entryRelpath = getContext().getNodeReposRelPath(localAbsPath);
        if (!copyMode) {
            reposRelpath = entryRelpath;
        }
        boolean isCommitItemDelete = false;
        if (!addsOnly) {
            boolean isStatusDeleted = getContext().isNodeStatusDeleted(localAbsPath);
            boolean isReplaced = getContext().isNodeReplaced(localAbsPath);
            if (isReplaced) {
                NodeCopyFromInfo copyFromInfo = getContext().getNodeCopyFromInfo(localAbsPath, NodeCopyFromField.url, NodeCopyFromField.isCopyTarget);
                if (copyFromInfo.url != null && !copyFromInfo.isCopyTarget) {
                    isReplaced = false;
                }
            }
            if (isStatusDeleted || isReplaced) {
                isCommitItemDelete = true;
            }
        }
        File cfRelpath = null;
        long cfRev = SVNWCContext.INVALID_REVNUM;
        File nodeCopyFromRelpath = null;
        long nodeCopyFromRev = SVNWCContext.INVALID_REVNUM;
        boolean isCommitItemAdd = false;
        boolean isCommitItemIsCopy = false;
        boolean isAdded = getContext().isNodeAdded(localAbsPath);
        if (isAdded) {
            NodeCopyFromInfo copyFrom = getContext().getNodeCopyFromInfo(localAbsPath, NodeCopyFromField.reposRelPath, NodeCopyFromField.rev, NodeCopyFromField.isCopyTarget);
            boolean isCopyTarget = copyFrom.isCopyTarget;
            nodeCopyFromRelpath = copyFrom.reposRelPath;
            nodeCopyFromRev = copyFrom.rev;
            if (isCopyTarget) {
                isCommitItemAdd = true;
                isCommitItemIsCopy = true;
                cfRelpath = nodeCopyFromRelpath;
                cfRev = nodeCopyFromRev;
                addsOnly = false;
            } else if (copyFrom.reposRelPath == null) {
                isCommitItemAdd = true;
                addsOnly = true;
            } else {
                File parentAbspath = SVNFileUtil.getFileDir(localAbsPath);
                long parentCopyFromRev = getContext().getNodeCopyFromInfo(parentAbspath, NodeCopyFromField.rev).rev;
                if (parentCopyFromRev != nodeCopyFromRev) {
                    isCommitItemAdd = true;
                    isCommitItemIsCopy = true;
                    cfRelpath = nodeCopyFromRelpath;
                    cfRev = nodeCopyFromRev;
                    addsOnly = false;
                }
            }
        } else {
            nodeCopyFromRelpath = null;
            nodeCopyFromRev = SVNWCContext.INVALID_REVNUM;
        }
        long entryRev = getContext().getNodeBaseRev(localAbsPath);
        if (copyMode && !isCommitItemDelete) {
            long pRev = SVNWCContext.INVALID_REVNUM;
            if (!copyModeRoot) {
                pRev = getContext().getNodeBaseRev(SVNFileUtil.getFileDir(localAbsPath));
            }
            if (copyModeRoot || entryRev != pRev) {
                isCommitItemAdd = true;
                if (nodeCopyFromRelpath != null) {
                    isCommitItemIsCopy = true;
                    cfRelpath = nodeCopyFromRelpath;
                    cfRev = nodeCopyFromRev;
                    addsOnly = false;
                } else if (entryRev != SVNWCContext.INVALID_REVNUM) {
                    isCommitItemIsCopy = true;
                    cfRelpath = entryRelpath;
                    cfRev = entryRev;
                    addsOnly = false;
                } else
                    addsOnly = true;
            }
        }
        boolean propMod = false;
        boolean textMod = false;
        if (isCommitItemAdd) {
            if (workingKind == SVNNodeKind.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' is scheduled for addition, but is missing", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
                return;
            }
            PropMods checkPropMods = checkPropMods(localAbsPath);
            propMod = checkPropMods.propsChanged;
            boolean eolPropChanged = checkPropMods.eolPropChanged;
            if (dbKind == SVNNodeKind.FILE) {
                if (isCommitItemIsCopy) {
                    textMod = getContext().isTextModified(localAbsPath, eolPropChanged, true);
                } else {
                    textMod = true;
                }
            }
        } else if (!isCommitItemDelete) {
            PropMods checkPropMods = checkPropMods(localAbsPath);
            propMod = checkPropMods.propsChanged;
            boolean eolPropChanged = checkPropMods.eolPropChanged;
            if (dbKind == SVNNodeKind.FILE) {
                textMod = getContext().isTextModified(localAbsPath, eolPropChanged, true);
            }
        }
        boolean isCommitItemTextMods = textMod;
        boolean isCommitItemPropMods = propMod;
        boolean stateFlags = isCommitItemAdd || isCommitItemDelete || isCommitItemIsCopy || isCommitItemPropMods || isCommitItemTextMods;
        boolean isCommitItemLockToken = false;
        String entryLockToken = null;
        if (lockTokens != null && (stateFlags || justLocked)) {
            SVNWCDbLock entryLock = getContext().getNodeLock(localAbsPath);
            if (entryLock != null && entryLock.token != null) {
                entryLockToken = entryLock.token;
                isCommitItemLockToken = true;
                stateFlags = true;
            }
        }
        if (stateFlags) {
            if (getContext().isChangelistMatch(localAbsPath, changelistsSet)) {
                assert (SVNFileUtil.isAbsolute(localAbsPath));
                assert (reposRootUrl != null && reposRelpath != null);
                SVNURL url = reposRootUrl.appendPath(SVNFileUtil.getFilePath(reposRelpath), false);
                SVNCommitItem item = new SVNCommitItem(localAbsPath, url, cfRelpath != null ? reposRootUrl.appendPath(SVNFileUtil.getFilePath(cfRelpath), false) : null, dbKind,
                        SVNRevision.create(entryRev), SVNRevision.create(cfRev), isCommitItemAdd, isCommitItemDelete, isCommitItemPropMods, isCommitItemTextMods, isCommitItemIsCopy,
                        isCommitItemLockToken);
                String path = SVNFileUtil.getFilePath(localAbsPath);
                item.setPath(path);
                committables.put(path, item);
                if (isCommitItemLockToken) {
                    lockTokens.put(url, entryLockToken);
                }
            }
        }
        if ((dbKind == SVNNodeKind.DIR) && (depth.getId() > SVNDepth.EMPTY.getId()) && (!isCommitItemDelete || isCommitItemAdd)) {
            List<File> children = getContext().getNodeChildren(localAbsPath, copyMode);
            for (File thisAbsPath : children) {
                String name = SVNFileUtil.getFileName(thisAbsPath);
                SVNDepth thisDepth = getContext().getNodeDepth(thisAbsPath);
                if (thisDepth == SVNDepth.EXCLUDE) {
                    continue;
                }
                boolean thisIsDeleted = getContext().isNodeStatusDeleted(thisAbsPath);
                boolean isReplaced = getContext().isNodeReplaced(thisAbsPath);
                if (isReplaced && thisIsDeleted) {
                    continue;
                }
                File thisReposRelpath;
                if (!copyMode) {
                    thisReposRelpath = getContext().getNodeReposRelPath(localAbsPath);
                } else {
                    thisReposRelpath = SVNFileUtil.createFilePath(reposRelpath, name);
                }
                SVNNodeKind thisKind = getContext().readKind(thisAbsPath, true);
                if (thisKind == SVNNodeKind.DIR) {
                    if (depth.getId() <= SVNDepth.FILES.getId()) {
                        continue;
                    }
                }
                {
                    SVNDepth depthBelowHere = depth;
                    if (depth == SVNDepth.IMMEDIATES || depth == SVNDepth.FILES)
                        depthBelowHere = SVNDepth.EMPTY;
                    harvestCommittables(committables, lockTokens, thisAbsPath, reposRootUrl, copyMode ? thisReposRelpath : null, addsOnly, copyMode, false, depthBelowHere, justLocked, changelistsSet);
                }
            }
        }
        if (lockTokens != null && dbKind == SVNNodeKind.DIR && isCommitItemDelete) {
            collectLocks(localAbsPath, lockTokens);
        }
    }

    private void bailOnTreeConflictedChildren(File localAbsPath, SVNNodeKind kind, SVNDepth depth, Collection changelistsSet) throws SVNException {
        if ((depth == SVNDepth.EMPTY) || (kind != SVNNodeKind.DIR)) {
            return;
        }
        Map<String, SVNTreeConflictDescription> conflicts = getContext().getDb().opReadAllTreeConflicts(localAbsPath);
        if (conflicts == null || conflicts.isEmpty()) {
            return;
        }
        for (SVNTreeConflictDescription conflict : conflicts.values()) {
            if ((conflict.getNodeKind() == SVNNodeKind.DIR) && (depth == SVNDepth.FILES)) {
                continue;
            }
            if (!getContext().isChangelistMatch(localAbsPath, changelistsSet)) {
                continue;
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, "Aborting commit: ''{0}'' remains in conflict", conflict.getPath());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }

    private void collectLocks(File path, final Map lockTokens) throws SVNException {

        ISVNWCNodeHandler nodeHandler = new ISVNWCNodeHandler() {

            public void nodeFound(File localAbspath) throws SVNException {
                SVNWCDbLock nodeLock = getContext().getNodeLock(localAbspath);
                if (nodeLock == null || nodeLock.token == null) {
                    return;
                }
                SVNURL url = getContext().getNodeUrl(localAbspath);
                if (url != null) {
                    lockTokens.put(url, nodeLock.token);
                }
            }
        };
        getContext().nodeWalkChildren(path, nodeHandler, false, SVNDepth.INFINITY);
    }

    private static class PropMods {

        public boolean propsChanged = false;
        public boolean eolPropChanged = false;
    }

    private PropMods checkPropMods(File localAbsPath) throws SVNException {
        PropMods propMods = new PropMods();
        PropDiffs propDiffs = getContext().getPropDiffs(localAbsPath);
        if (propDiffs.propChanges == null || propDiffs.propChanges.isEmpty()) {
            return propMods;
        }
        propMods.propsChanged = true;
        for (Object propName : propDiffs.propChanges.nameSet()) {
            if (SVNProperty.EOL_STYLE.equals(propName)) {
                propMods.eolPropChanged = true;
                break;
            }
        }
        return propMods;
    }

    /**
     * Collects commit items (containing detailed information on each Working
     * Copy item that was changed and need to be committed to the repository)
     * into different <b>SVNCommitPacket</b>s.
     * <p/>
     * This method is identical to
     * <code>doCollectCommitItems(paths, keepLocks, force, SVNDepth.fromRecurse(recursive), combinePackets, null)</code>.
     *
     * @param paths
     *            an array of local items which should be traversed to collect
     *            information on every changed item (one <b>SVNCommitItem</b>
     *            per each modified local item)
     * @param keepLocks
     *            if <span class="javakeyword">true</span> and there are local
     *            items that were locked then these items will be left locked
     *            after traversing all of them, otherwise the items will be
     *            unlocked
     * @param force
     *            forces collecting commit items for a non-recursive commit
     * @param recursive
     *            relevant only for directory items: if <span
     *            class="javakeyword">true</span> then the entire directory tree
     *            will be traversed including all child directories, otherwise
     *            only items located in the directory itself will be processed
     * @param combinePackets
     *            if <span class="javakeyword">true</span> then collected commit
     *            packets will be joined into a single one, so that to be
     *            committed in a single transaction
     * @return an array of commit packets
     * @throws SVNException
     * @deprecated use
     *             {@link #doCollectCommitItems(File[],boolean,boolean,SVNDepth,boolean,String[])}
     *             instead
     */
    public SVNCommitPacket[] doCollectCommitItems(File[] paths, boolean keepLocks, boolean force, boolean recursive, boolean combinePackets) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }

    /**
     * Collects commit items (containing detailed information on each Working
     * Copy item that was changed and need to be committed to the repository)
     * into different <code>SVNCommitPacket</code>s. This method may be
     * considered as an advanced version of the
     * {@link #doCollectCommitItems(File[],boolean,boolean,SVNDepth,String[])}
     * method. Its main difference from the aforementioned method is that it
     * provides an ability to collect commit items from different working copies
     * checked out from the same repository and combine them into a single
     * commit packet. This is attained via setting <code>combinePackets</code>
     * into <span class="javakeyword">true</span>. However even if
     * <code>combinePackets</code> is set, combining may only occur if (besides
     * that the paths must be from the same repository) URLs of
     * <code>paths</code> are formed of identical components, that is protocol
     * name, host name, port number (if any) must match for all paths. Otherwise
     * combining will not occur.
     * <p/>
     * Combined items will be committed in a single transaction.
     * <p/>
     * For details on other parameters, please, refer to
     * {@link #doCommit(File[],boolean,String,SVNProperties,String[],boolean,boolean,SVNDepth)}.
     *
     * @param paths
     *            an array of local items which should be traversed to collect
     *            information on every changed item (one <b>SVNCommitItem</b>
     *            per each modified local item)
     * @param keepLocks
     *            if <span class="javakeyword">true</span> and there are local
     *            items that were locked then these items will be left locked
     *            after traversing all of them, otherwise the items will be
     *            unlocked
     * @param force
     *            forces collecting commit items for a non-recursive commit
     * @param depth
     *            tree depth to process
     * @param combinePackets
     *            whether combining commit packets into a single commit packet
     *            is allowed or not
     * @param changelists
     *            changelist names array
     * @return array of commit packets
     * @throws SVNException
     *             in the following cases:
     *             <ul>
     *             <li/>exception with {@link SVNErrorCode#ENTRY_MISSING_URL}
     *             error code - if working copy root of either path has no url
     *             </ul>
     * @since 1.2.0
     */
    public SVNCommitPacket[] doCollectCommitItems(File[] paths, boolean keepLocks, boolean force, SVNDepth depth, boolean combinePackets, String[] changelists) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT);
        SVNErrorManager.error(err, SVNLogType.CLIENT);
        return null;
    }
}
