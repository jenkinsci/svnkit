package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc16.SVNBasicDelegate;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * The <b>SVNStatusClient</b> class provides methods for obtaining information
 * on the status of Working Copy items. The functionality of
 * <b>SVNStatusClient</b> corresponds to the <code>'svn status'</code> command of the native SVN
 * command line client.
 * <p>
 * One of the main advantages of <b>SVNStatusClient</b> lies in that fact that
 * for each processed item the status information is collected and put into an
 * <b>SVNStatus</b> object. Further there are two ways how this object can be
 * passed to a developer (depending on the version of the <b>doStatus()</b>
 * method that was invoked):
 * <ol>
 * <li>the <b>SVNStatus</b> can be passed to a developer's status handler (that
 * should implement <b>ISVNStatusHandler</b>) in which the developer retrieves
 * status information and decides how to interprete that info;
 * <li>another way is that an appropriate <b>doStatus()</b> method just returns
 * that <b>SVNStatus</b> object.
 * </ol>
 * Those methods that match the first variant can be called recursively -
 * obtaining status information for all child entries, the second variant just
 * the reverse - methods are called non-recursively and allow to get status info
 * on a single item.
 *
 * @version 1.3
 * @author TMate Software Ltd.
 * @since 1.2
 * @see ISVNStatusHandler
 * @see SVNStatus
 * @see <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNStatusClient17 extends SVNBasicDelegate {

    private ISVNStatusFileProvider myFilesProvider;

    /**
     * Constructs and initializes an <b>SVNStatusClient</b> object with the
     * specified run-time configuration and authentication drivers.
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>, then
     * this <b>SVNStatusClient</b> will be using a default run-time
     * configuration driver which takes client-side settings from the default
     * SVN's run-time configuration area but is not able to change those
     * settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNStatusClient</b> will be using a default authentication
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
    public SVNStatusClient17(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    /**
     * Constructs and initializes an <b>SVNStatusClient</b> object with the
     * specified run-time configuration and repository pool object.
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>, then
     * this <b>SVNStatusClient</b> will be using a default run-time
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
    public SVNStatusClient17(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    /**
     * Collects status information on Working Copy items and passes it to a
     * <code>handler</code>.
     *
     * @param path
     *            local item's path
     * @param recursive
     *            relevant only if <code>path</code> denotes a directory: <span
     *            class="javakeyword">true</span> to obtain status info
     *            recursively for all child entries, <span
     *            class="javakeyword">false</span> only for items located
     *            immediately in the directory itself
     * @param remote
     *            <span class="javakeyword">true</span> to check up the status
     *            of the item in the repository, that will tell if the local
     *            item is out-of-date (like <i>'-u'</i> option in the SVN
     *            client's <code>'svn status'</code> command), otherwise <span
     *            class="javakeyword">false</span>
     * @param reportAll
     *            <span class="javakeyword">true</span> to collect status
     *            information on those items that are in a <i>'normal'</i> state
     *            (unchanged), otherwise <span class="javakeyword">false</span>
     * @param includeIgnored
     *            <span class="javakeyword">true</span> to force the operation
     *            to collect information on items that were set to be ignored
     *            (like <i>'--no-ignore'</i> option in the SVN client's <i>'svn
     *            status'</i> command to disregard default and
     *            <i>'svn:ignore'</i> property ignores), otherwise <span
     *            class="javakeyword">false</span>
     * @param handler
     *            a caller's status handler that will be involved in processing
     *            status information
     * @return the revision number the status information was collected against
     * @throws SVNException
     * @see ISVNStatusHandler
     * @deprecated use
     *             {@link #doStatus(File,SVNRevision,SVNDepth,boolean,boolean,boolean,boolean,ISVNStatusHandler,Collection)}
     *             instead
     */
    public long doStatus(File path, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, ISVNStatusHandler handler) throws SVNException {
        return doStatus(path, SVNRevision.HEAD, SVNDepth.fromRecurse(recursive), remote, reportAll, includeIgnored, false, handler, null);
    }

    /**
     * Collects status information on Working Copy items and passes it to a
     * <code>handler</code>.
     * <p>
     * Calling this method is equivalent to
     *
     * <code>doStatus(path, SVNRevision.HEAD, recursive, remote, reportAll, includeIgnored, collectParentExternals, handler)</code>.
     *
     * @param path
     *            local item's path
     * @param recursive
     *            relevant only if <code>path</code> denotes a directory: <span
     *            class="javakeyword">true</span> to obtain status info
     *            recursively for all child entries, <span
     *            class="javakeyword">false</span> only for items located
     *            immediately in the directory itself
     * @param remote
     *            <span class="javakeyword">true</span> to check up the status
     *            of the item in the repository, that will tell if the local
     *            item is out-of-date (like <i>'-u'</i> option in the SVN
     *            client's <code>'svn status'</code> command), otherwise <span
     *            class="javakeyword">false</span>
     * @param reportAll
     *            <span class="javakeyword">true</span> to collect status
     *            information on all items including those ones that are in a
     *            <i>'normal'</i> state (unchanged), otherwise <span
     *            class="javakeyword">false</span>
     * @param includeIgnored
     *            <span class="javakeyword">true</span> to force the operation
     *            to collect information on items that were set to be ignored
     *            (like <i>'--no-ignore'</i> option in the SVN client's <code>'svn status'</code>
     *            command to disregard default and <i>'svn:ignore'</i> property
     *            ignores), otherwise <span class="javakeyword">false</span>
     * @param collectParentExternals
     *            <span class="javakeyword">false</span> to make the operation
     *            ignore information on externals definitions (like
     *            <i>'--ignore-externals'</i> option in the SVN client's <code>'svn status'</code>
     *            command), otherwise <span class="javakeyword">true</span>
     * @param handler
     *            a caller's status handler that will be involved in processing
     *            status information
     * @return the revision number the status information was collected against
     * @throws SVNException
     * @deprecated use
     *             {@link #doStatus(File,SVNRevision,SVNDepth,boolean,boolean,boolean,boolean,ISVNStatusHandler,Collection)}
     *             instead
     */
    public long doStatus(File path, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, boolean collectParentExternals, final ISVNStatusHandler handler) throws SVNException {
        return doStatus(path, SVNRevision.HEAD, SVNDepth.fromRecurse(recursive), remote, reportAll, includeIgnored, collectParentExternals, handler, null);
    }

    /**
     * Collects status information on Working Copy items and passes it to a
     * <code>handler</code>.
     *
     * @param path
     *            local item's path
     * @param revision
     *            if <code>remote</code> is <span
     *            class="javakeyword">true</span> this revision is used to
     *            calculate status against
     * @param recursive
     *            relevant only if <code>path</code> denotes a directory: <span
     *            class="javakeyword">true</span> to obtain status info
     *            recursively for all child entries, <span
     *            class="javakeyword">false</span> only for items located
     *            immediately in the directory itself
     * @param remote
     *            <span class="javakeyword">true</span> to check up the status
     *            of the item in the repository, that will tell if the local
     *            item is out-of-date (like <i>'-u'</i> option in the SVN
     *            client's <code>'svn status'</code> command), otherwise <span
     *            class="javakeyword">false</span>
     * @param reportAll
     *            <span class="javakeyword">true</span> to collect status
     *            information on all items including those ones that are in a
     *            <i>'normal'</i> state (unchanged), otherwise <span
     *            class="javakeyword">false</span>
     * @param includeIgnored
     *            <span class="javakeyword">true</span> to force the operation
     *            to collect information on items that were set to be ignored
     *            (like <i>'--no-ignore'</i> option in the SVN client's <code>'svn status'</code>
     *            command to disregard default and <i>'svn:ignore'</i> property
     *            ignores), otherwise <span class="javakeyword">false</span>
     * @param collectParentExternals
     *            <span class="javakeyword">false</span> to make the operation
     *            ignore information on externals definitions (like
     *            <i>'--ignore-externals'</i> option in the SVN client's <code>'svn status'</code>
     *            command), otherwise <span class="javakeyword">true</span>
     * @param handler
     *            a caller's status handler that will be involved in processing
     *            status information
     * @return the revision number the status information was collected against
     * @throws SVNException
     * @deprecated use
     *             {@link #doStatus(File,SVNRevision,SVNDepth,boolean,boolean,boolean,boolean,ISVNStatusHandler,Collection)}
     *             instead
     */
    public long doStatus(File path, SVNRevision revision, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, boolean collectParentExternals, final ISVNStatusHandler handler)
            throws SVNException {
        return doStatus(path, revision, SVNDepth.fromRecurse(recursive), remote, reportAll, includeIgnored, collectParentExternals, handler, null);
    }

    /**
     * Given a <code>path</code> to a working copy directory (or single file),
     * calls <code>handler</code> with a set of {@link SVNStatus} objects which
     * describe the status of the <code>path</code>, and its children (recursing
     * according to <code>depth</code>).
     * <p/>
     * If <code>reportAll</code> is set, retrieves all entries; otherwise,
     * retrieves only "interesting" entries (local modifications and/or out of
     * date).
     * <p/>
     * If <code>remote</code> is set, contacts the repository and augments the
     * status objects with information about out-of-dateness (with respect to
     * <code>revision</code>).
     * <p/>
     * If {@link #isIgnoreExternals()} returns <span
     * class="javakeyword">false</span>, then recurses into externals
     * definitions (if any exist and <code>depth</code> is either
     * {@link SVNDepth#INFINITY} or {@link SVNDepth#UNKNOWN}) after handling the
     * main target. This calls the client notification handler (
     * {@link ISVNEventHandler}) with the {@link SVNEventAction#STATUS_EXTERNAL}
     * action before handling each externals definition, and with
     * {@link SVNEventAction#STATUS_COMPLETED} after each.
     * <p/>
     * <code>changeLists</code> is a collection of <code>String</code>
     * changelist names, used as a restrictive filter on items whose statuses
     * are reported; that is, doesn't report status about any item unless it's a
     * member of one of those changelists. If <code>changeLists</code> is empty
     * (or <span class="javakeyword">null</span>), no changelist filtering
     * occurs.
     *
     * @param path
     *            working copy path
     * @param revision
     *            if <code>remote</code> is <span
     *            class="javakeyword">true</span>, status is calculated against
     *            this revision
     * @param depth
     *            tree depth to process
     * @param remote
     *            <span class="javakeyword">true</span> to check up the status
     *            of the item in the repository, that will tell if the local
     *            item is out-of-date (like <i>'-u'</i> option in the SVN
     *            client's <code>'svn status'</code> command), otherwise <span
     *            class="javakeyword">false</span>
     * @param reportAll
     *            <span class="javakeyword">true</span> to collect status
     *            information on all items including those ones that are in a
     *            <i>'normal'</i> state (unchanged), otherwise <span
     *            class="javakeyword">false</span>
     * @param includeIgnored
     *            <span class="javakeyword">true</span> to force the operation
     *            to collect information on items that were set to be ignored
     *            (like <i>'--no-ignore'</i> option in the SVN client's <code>'svn status'</code>
     *            command to disregard default and <i>'svn:ignore'</i> property
     *            ignores), otherwise <span class="javakeyword">false</span>
     * @param collectParentExternals
     *            obsolete (not used)
     * @param handler
     *            a caller's status handler that will be involved in processing
     *            status information
     * @param changeLists
     *            collection with changelist names
     * @return returns the actual revision against which the working copy was
     *         compared; the return value is not meaningful (-1) unless
     *         <code>remote</code> is set
     * @throws SVNException
     * @since 1.2, SVN 1.5
     */
    public long doStatus(File path, SVNRevision revision, SVNDepth depth, boolean remote, boolean reportAll, boolean includeIgnored, boolean collectParentExternals, final ISVNStatusHandler handler,
            final Collection changeLists) throws SVNException {

        if (handler == null) {
            return -1;
        }
        depth = depth == null ? SVNDepth.UNKNOWN : depth;
        final SVNWCContext wcContext = new SVNWCContext(this.getOptions(), getEventDispatcher());
        SVNStatusEditor17 editor = null;
        final boolean[] deletedInRepository = new boolean[] {
            false
        };
        ISVNStatusHandler realHandler = new ISVNStatusHandler() {

            public void handleStatus(SVNStatus status) throws SVNException {
                if (deletedInRepository[0] && status.getEntry() != null) {
                    status.setRemoteStatus(SVNStatusType.STATUS_DELETED, null, null, null);
                }
                if (!SVNWCAccess.matchesChangeList(changeLists, status.getEntry())) {
                    return;
                }
                handler.handleStatus(status);
            }
        };

        try {

            final SVNWCContextInfo info = new SVNWCContextInfo();
            info.setTargetAbsPath(SVNPathUtil.canonicalizeAbsolutePath(path.getPath()));

            {
                SVNNodeKind diskKind = SVNFileType.getNodeKind(SVNFileType.getType(info.getTargetAbsFile()));
                SVNNodeKind kind = wcContext.getNodeKind(info.getTargetAbsFile(), false);

                /* Dir must be an existing directory or the status editor fails */
                if (kind == SVNNodeKind.DIR && diskKind == SVNNodeKind.DIR) {
                    info.setDirAbsPath(info.getTargetAbsPath());
                    info.setTargetBaseName("");
                    info.setDir(path.getPath());
                } else {
                    info.setDirAbsPath(SVNPathUtil.getDirName(info.getTargetAbsPath()));
                    info.setTargetBaseName(SVNPathUtil.getBaseName(info.getTargetAbsPath()));
                    info.setDir(SVNPathUtil.getDirName(path.getPath()));
                    if (kind != SVNNodeKind.FILE) {
                        kind = wcContext.getNodeKind(info.getDirAbsFile(), false);
                        /*
                         * Check for issue #1617 and stat_tests.py 14
                         * "status on '..' where '..' is not versioned".
                         */
                        if (kind != SVNNodeKind.DIR || "..".equals(path.getPath())) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_WORKING_COPY, "'%s' is not a working copy", path.getPath());
                            SVNErrorManager.error(err, SVNLogType.CLIENT);
                        }
                    }
                }
            }

            if (!SVNPathUtil.isAbsolute(info.getDir())) {
                info.setAnchorAbsPath(info.getDirAbsPath());
                info.setAnchorRelPath(info.getDir());
            }

            if (remote) {
                SVNURL url = SVNURL.fromFile(new File(info.getDirAbsPath()));
                if (url == null) {
                    SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Entry ''{0}'' has no URL", info.getDir());
                    SVNErrorManager.error(error, SVNLogType.WC);
                }
                SVNRepository repository = createRepository(url, true);
                long rev;
                if (revision == SVNRevision.HEAD) {
                    rev = -1;
                } else {
                    rev = wcContext.getRevisionNumber(revision, null, repository, path);
                }
                SVNNodeKind kind = repository.checkPath("", -1);
                checkCancelled();
                SVNReporter17 reporter = null;
                if (kind == SVNNodeKind.NONE) {
                    boolean added = wcContext.isNodeAdded(info.getDirAbsFile());
                    if (added) {
                        boolean replaced = wcContext.isNodeReplaced(info.getDirAbsFile());
                        if (replaced) {
                            added = false;
                        }
                    }
                    if (!added) {
                        deletedInRepository[0] = true;
                    }
                    editor = new SVNStatusEditor17(getOptions(), wcContext, info, includeIgnored, reportAll, depth, realHandler);
                    checkCancelled();
                    editor.closeEdit();
                } else {
                    editor = new SVNRemoteStatusEditor17(getOptions(), wcContext, info, includeIgnored, reportAll, depth, realHandler);
                    SVNRepository locksRepos = createRepository(url, false);
                    checkCancelled();
                    boolean serverSupportsDepth = repository.hasCapability(SVNCapability.DEPTH);
                    reporter = new SVNReporter17(info, path, false, !serverSupportsDepth, depth, false, true, true, getDebugLog());
                    SVNStatusReporter17 statusReporter = new SVNStatusReporter17(locksRepos, reporter, editor);
                    String target = "".equals(info.getTargetName()) ? null : info.getTargetName();
                    repository.status(rev, target, depth, statusReporter, SVNCancellableEditor.newInstance((ISVNEditor) editor, getEventDispatcher(), getDebugLog()));
                }
                if (getEventDispatcher() != null) {
                    long reportedFiles = reporter != null ? reporter.getReportedFilesCount() : 0;
                    long totalFiles = reporter != null ? reporter.getTotalFilesCount() : 0;
                    SVNEvent event = SVNEventFactory.createSVNEvent(info.getTargetAbsFile(), SVNNodeKind.NONE, null, editor.getTargetRevision(), SVNEventAction.STATUS_COMPLETED, null, null, null,
                            reportedFiles, totalFiles);
                    getEventDispatcher().handleEvent(event, ISVNEventHandler.UNKNOWN);
                }
            } else {
                editor = new SVNStatusEditor17(getOptions(), wcContext, info, includeIgnored, reportAll, depth, handler);
                if (myFilesProvider != null) {
                    editor.setFileProvider(myFilesProvider);
                }
                editor.closeEdit();
            }
            if (!isIgnoreExternals() && (depth == SVNDepth.INFINITY || depth == SVNDepth.UNKNOWN)) {
                Map externalsMap = info.getNewExternals();
                for (Iterator paths = externalsMap.keySet().iterator(); paths.hasNext();) {
                    String ownerPath = (String) paths.next();
                    String externalValue = (String) externalsMap.get(ownerPath);
                    SVNExternal[] externals = SVNExternal.parseExternals(ownerPath, externalValue);
                    for (int i = 0; i < externals.length; i++) {
                        SVNExternal external = externals[i];
                        String externalPath = SVNPathUtil.append(ownerPath, external.getPath());
                        File externalFile = new File(SVNPathUtil.append(info.getDirAbsPath(), externalPath));
                        if (SVNFileType.getType(externalFile) != SVNFileType.DIRECTORY) {
                            continue;
                        }
                        try {
                            int format = SVNAdminAreaFactory.checkWC(externalFile, true);
                            if (format == 0) {
                                continue;
                            }
                        } catch (SVNException e) {
                            continue;
                        }
                        handleEvent(SVNEventFactory.createSVNEvent(externalFile, SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNEventAction.STATUS_EXTERNAL, null, null, null),
                                ISVNEventHandler.UNKNOWN);
                        setEventPathPrefix(externalPath);
                        try {
                            doStatus(externalFile, SVNRevision.HEAD, depth, remote, reportAll, includeIgnored, false, handler, null);
                        } catch (SVNException e) {
                            if (e instanceof SVNCancelException) {
                                throw e;
                            }
                        } finally {
                            setEventPathPrefix(null);
                        }
                    }
                }
            }
        } finally {
            wcContext.close();
        }
        return editor.getTargetRevision();

    }

    private SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException {
        SVNRepository repository = null;
        if (getRepositoryPool() == null) {
            repository = SVNRepositoryFactory.create(url, null);
        } else {
            repository = getRepositoryPool().createRepository(url, mayReuse);
        }
        repository.setDebugLog(getDebugLog());
        repository.setCanceller(getEventDispatcher());
        return repository;
    }

    /**
     * Collects status information on a single Working Copy item.
     *
     * @param path
     *            local item's path
     * @param remote
     *            <span class="javakeyword">true</span> to check up the status
     *            of the item in the repository, that will tell if the local
     *            item is out-of-date (like <i>'-u'</i> option in the SVN
     *            client's <code>'svn status'</code> command), otherwise <span
     *            class="javakeyword">false</span>
     * @return an <b>SVNStatus</b> object representing status information for
     *         the item
     * @throws SVNException
     */
    public SVNStatus doStatus(final File path, boolean remote) throws SVNException {
        return doStatus(path, remote, false);
    }

    /**
     * Collects status information on a single Working Copy item.
     *
     * @param path
     *            local item's path
     * @param remote
     *            <span class="javakeyword">true</span> to check up the status
     *            of the item in the repository, that will tell if the local
     *            item is out-of-date (like <i>'-u'</i> option in the SVN
     *            client's <code>'svn status'</code> command), otherwise <span
     *            class="javakeyword">false</span>
     * @param collectParentExternals
     *            <span class="javakeyword">false</span> to make the operation
     *            ignore information on externals definitions (like
     *            <i>'--ignore-externals'</i> option in the SVN client's <code>'svn status'</code>
     *            command), otherwise <span class="javakeyword">false</span>
     * @return an <b>SVNStatus</b> object representing status information for
     *         the item
     * @throws SVNException
     */
    public SVNStatus doStatus(File path, boolean remote, boolean collectParentExternals) throws SVNException {
        final SVNStatus[] result = new SVNStatus[] {
            null
        };
        final File absPath = path.getAbsoluteFile();
        ISVNStatusHandler handler = new ISVNStatusHandler() {

            public void handleStatus(SVNStatus status) {
                if (absPath.equals(status.getFile())) {
                    if (result[0] != null && result[0].getContentsStatus() == SVNStatusType.STATUS_EXTERNAL && absPath.isDirectory()) {
                        result[0] = status;
                        result[0].markExternal();
                    } else if (result[0] == null) {
                        result[0] = status;
                    }
                }
            }
        };
        doStatus(absPath, SVNRevision.HEAD, SVNDepth.EMPTY, remote, true, true, collectParentExternals, handler, null);
        return result[0];
    }

    public void setFilesProvider(ISVNStatusFileProvider filesProvider) {
        myFilesProvider = filesProvider;
    }
}
