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
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNRemoteStatusEditor;
import org.tmatesoft.svn.core.internal.wc.SVNStatusEditor;
import org.tmatesoft.svn.core.internal.wc.SVNStatusReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * The <b>SVNStatusClient</b> class provides methods for obtaining information on the 
 * status of Working Copy items.
 * The functionality of <b>SVNStatusClient</b> corresponds to the <code>'svn status'</code> command 
 * of the native SVN command line client. 
 * 
 * <p>
 * One of the main advantages of <b>SVNStatusClient</b> lies in that fact
 * that for each processed item the status information is collected and put into
 * an <b>SVNStatus</b> object. Further there are two ways how this object
 * can be passed to a developer (depending on the version of the <b>doStatus()</b>
 * method that was invoked):
 * <ol>
 * <li>the <b>SVNStatus</b> can be passed to a 
 * developer's status handler (that should implement <b>ISVNStatusHandler</b>)
 * in which the developer retrieves status information and decides how to interprete that
 * info;  
 * <li> another way is that an appropriate <b>doStatus()</b> method
 * just returns that <b>SVNStatus</b> object.
 * </ol>
 * Those methods that match the first variant can be called recursively - obtaining 
 * status information for all child entries, the second variant just the reverse  - 
 * methods are called non-recursively and allow to get status info on a single 
 * item. 
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @see		ISVNStatusHandler
 * @see		SVNStatus
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNStatusClient extends SVNBasicClient {

    /**
     * Constructs and initializes an <b>SVNStatusClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNStatusClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNStatusClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNStatusClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNStatusClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }
    
    /**
     * Collects status information on Working Copy items and passes
     * it to a <code>handler</code>. 
     * 
     * @param  path				local item's path
     * @param  recursive		relevant only if <code>path</code> denotes a directory:
     * 							<span class="javakeyword">true</span> to obtain status info recursively for all
     * 							child entries, <span class="javakeyword">false</span> only for items located immediately
     * 							in the directory itself  
     * @param  remote			<span class="javakeyword">true</span> to check up the status of the item in the repository,
     * 							that will tell if the local item is out-of-date (like <i>'-u'</i> option in the
     * 							SVN client's <code>'svn status'</code> command), otherwise 
     * 							<span class="javakeyword">false</span>
     * @param  reportAll		<span class="javakeyword">true</span> to collect status information on those items that are in a 
     * 							<i>'normal'</i> state (unchanged), otherwise <span class="javakeyword">false</span>
     * @param  includeIgnored	<span class="javakeyword">true</span> to force the operation to collect information
     * 							on items that were set to be ignored (like <i>'--no-ignore'</i> option in the SVN 
     * 							client's <i>'svn status'</i> command to disregard default and <i>'svn:ignore'</i> property
     * 							ignores), otherwise <span class="javakeyword">false</span>  
     * @param  handler			a caller's status handler that will be involved
     * 							in processing status information
     * @return                  the revision number the status information was collected
     *                          against
     * @throws SVNException
     * @see	                    ISVNStatusHandler
     */
    public long doStatus(File path, boolean recursive, boolean remote,
                         boolean reportAll, boolean includeIgnored, ISVNStatusHandler handler) throws SVNException {
        return doStatus(path, recursive, remote, reportAll, includeIgnored, false, handler);
    }
    
    /**
     * Collects status information on Working Copy items and passes
     * it to a <code>handler</code>. 
     *
     * <p>
     * Calling this method is equivalent to 
     * <code>doStatus(path, SVNRevision.HEAD, recursive, remote, reportAll, includeIgnored, collectParentExternals, handler)</code>.
     *  
     * @param  path							local item's path
     * @param  recursive					relevant only if <code>path</code> denotes a directory:
     * 										<span class="javakeyword">true</span> to obtain status info recursively for all
     * 										child entries, <span class="javakeyword">false</span> only for items located 
     * 										immediately in the directory itself
     * @param  remote						<span class="javakeyword">true</span> to check up the status of the item in the repository,
     * 										that will tell if the local item is out-of-date (like <i>'-u'</i> option in the
     * 										SVN client's <code>'svn status'</code> command), 
     * 										otherwise <span class="javakeyword">false</span>
     * @param  reportAll					<span class="javakeyword">true</span> to collect status information on all items including those ones that are in a 
     * 										<i>'normal'</i> state (unchanged), otherwise <span class="javakeyword">false</span>
     * @param  includeIgnored				<span class="javakeyword">true</span> to force the operation to collect information
     * 										on items that were set to be ignored (like <i>'--no-ignore'</i> option in the SVN 
     * 										client's <code>'svn status'</code> command to disregard default and <i>'svn:ignore'</i> property
     * 										ignores), otherwise <span class="javakeyword">false</span>
     * @param  collectParentExternals		<span class="javakeyword">false</span> to make the operation ignore information
     * 										on externals definitions (like <i>'--ignore-externals'</i> option in the SVN
     * 										client's <code>'svn status'</code> command), otherwise <span class="javakeyword">true</span>
     * @param  handler						a caller's status handler that will be involved
     * 										in processing status information
     * @return								the revision number the status information was collected
     * 										against
     * @throws SVNException
     */
    public long doStatus(File path, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, boolean collectParentExternals, final ISVNStatusHandler handler) throws SVNException {
        return doStatus(path, SVNRevision.HEAD, recursive, remote, reportAll, includeIgnored, collectParentExternals, handler);
    }
    
    /**
     * Collects status information on Working Copy items and passes
     * it to a <code>handler</code>. 
     * 
     * @param  path                         local item's path
     * @param  revision                     if <code>remote</code> is <span class="javakeyword">true</span>
     *                                      this revision is used to calculate status against
     * @param  recursive                    relevant only if <code>path</code> denotes a directory:
     *                                      <span class="javakeyword">true</span> to obtain status info recursively for all
     *                                      child entries, <span class="javakeyword">false</span> only for items located 
     *                                      immediately in the directory itself
     * @param  remote                       <span class="javakeyword">true</span> to check up the status of the item in the repository,
     *                                      that will tell if the local item is out-of-date (like <i>'-u'</i> option in the
     *                                      SVN client's <code>'svn status'</code> command), 
     *                                      otherwise <span class="javakeyword">false</span>
     * @param  reportAll                    <span class="javakeyword">true</span> to collect status information on all items including those ones that are in a 
     *                                      <i>'normal'</i> state (unchanged), otherwise <span class="javakeyword">false</span>
     * @param  includeIgnored               <span class="javakeyword">true</span> to force the operation to collect information
     *                                      on items that were set to be ignored (like <i>'--no-ignore'</i> option in the SVN 
     *                                      client's <code>'svn status'</code> command to disregard default and <i>'svn:ignore'</i> property
     *                                      ignores), otherwise <span class="javakeyword">false</span>
     * @param  collectParentExternals       <span class="javakeyword">false</span> to make the operation ignore information
     *                                      on externals definitions (like <i>'--ignore-externals'</i> option in the SVN
     *                                      client's <code>'svn status'</code> command), otherwise <span class="javakeyword">true</span>
     * @param  handler                      a caller's status handler that will be involved
     *                                      in processing status information
     * @return                              the revision number the status information was collected
     *                                      against
     * @throws SVNException
     */
    public long doStatus(File path, SVNRevision revision, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, boolean collectParentExternals, final ISVNStatusHandler handler) throws SVNException {
        if (handler == null) {
            return -1;
        }
        SVNWCAccess wcAccess = createWCAccess();
        SVNStatusEditor editor = null;
        final boolean[] deletedInRepository = new boolean[] {false};
        ISVNStatusHandler realHandler = new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) throws SVNException {
                if (deletedInRepository[0] && status.getEntry() != null) {
                    status.setRemoteStatus(SVNStatusType.STATUS_DELETED, null, null, null);
                } 
                handler.handleStatus(status);
            }
        };
        try {
            SVNAdminAreaInfo info = wcAccess.openAnchor(path, false, recursive ? -1 : 1);
            Map externals = null;
            if (collectParentExternals) {
                // prefetch externals from parent dirs, and pass it to the editor.
                externals = collectParentExternals(path, info.getAnchor().getRoot());
            }
            if (remote) {
                SVNEntry entry = wcAccess.getEntry(info.getAnchor().getRoot(), false);
                if (entry == null) {
                    SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", path);
                    SVNErrorManager.error(error);
                }
                if (entry.getURL() == null) {
                    SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Entry ''{0}'' has no URL", info.getAnchor().getRoot());
                    SVNErrorManager.error(error);
                }
                SVNURL url = entry.getSVNURL();
                SVNRepository repository = createRepository(url, true);
                long rev;
                if (revision == SVNRevision.HEAD) {
                    rev = -1;
                } else {
                    rev = getRevisionNumber(revision, repository, path);
                }
                SVNNodeKind kind = repository.checkPath("", rev);
                checkCancelled();
                if (kind == SVNNodeKind.NONE) {
                    if (!entry.isScheduledForAddition()) {
                        deletedInRepository[0] = true;
                    }
                    editor = new SVNStatusEditor(getOptions(), wcAccess, info, includeIgnored, reportAll, recursive, realHandler);
                    editor.setExternals(externals);
                    checkCancelled();
                    editor.closeEdit();
                } else {
                    editor = new SVNRemoteStatusEditor(getOptions(), wcAccess, info, includeIgnored, reportAll, recursive, realHandler);
                    editor.setExternals(externals);
                    SVNRepository locksRepos = createRepository(url, false);
                    checkCancelled();
                    SVNReporter reporter = new SVNReporter(info, path, false, recursive, getDebugLog());
                    SVNStatusReporter statusReporter = new SVNStatusReporter(locksRepos, reporter, editor);
                    String target = "".equals(info.getTargetName()) ? null : info.getTargetName();
                    repository.status(rev, target, recursive, statusReporter, SVNCancellableEditor.newInstance((ISVNEditor) editor, getEventDispatcher(), getDebugLog()));
                }
                if (getEventDispatcher() != null) {
                    SVNEvent event = SVNEventFactory.createStatusCompletedEvent(info, editor.getTargetRevision());
                    getEventDispatcher().handleEvent(event, ISVNEventHandler.UNKNOWN);
                }
            } else {
                editor = new SVNStatusEditor(getOptions(), wcAccess, info, includeIgnored, reportAll, recursive, handler);
                editor.setExternals(externals);
                editor.closeEdit();
            }         
            if (!isIgnoreExternals() && recursive) {
                externals = editor.getExternals();
                for (Iterator paths = externals.keySet().iterator(); paths.hasNext();) {
                    String externalPath = (String) paths.next();
                    File externalFile = info.getAnchor().getFile(externalPath);
                    if (SVNFileType.getType(externalFile) != SVNFileType.DIRECTORY) {
                        continue;
                    }
                    try {
                        int format = SVNAdminAreaFactory.checkWC(externalFile, true);
                        if (format == 0) {
                            // something unversioned instead of external.
                            continue;
                        }
                    } catch (SVNException e) {
                        continue;
                    }
                    handleEvent(SVNEventFactory.createStatusExternalEvent(info, externalPath), ISVNEventHandler.UNKNOWN);
                    setEventPathPrefix(externalPath);
                    try {
                        doStatus(externalFile, recursive, remote, reportAll, includeIgnored, false, handler);
                    } catch (SVNException e) {
                        if (e instanceof SVNCancelException) {
                            throw e;
                        }
                    } finally {
                        setEventPathPrefix(externalPath);
                    }
                }
            }
        } finally {
            wcAccess.close();
        }
        return editor.getTargetRevision();        
    }
    
    /**
     * Collects status information on a single Working Copy item. 
     * 
     * @param  path				local item's path
     * @param  remote			<span class="javakeyword">true</span> to check up the status of the item in the repository,
     * 							that will tell if the local item is out-of-date (like <i>'-u'</i> option in the
     * 							SVN client's <code>'svn status'</code> command), 
     * 							otherwise <span class="javakeyword">false</span>
     * @return					an <b>SVNStatus</b> object representing status information 
     * 							for the item
     * @throws SVNException
     */
    public SVNStatus doStatus(final File path, boolean remote) throws SVNException {
        return doStatus(path, remote, false);
    }
    
    /**
     * Collects status information on a single Working Copy item. 
     *  
     * @param  path						local item's path
     * @param  remote					<span class="javakeyword">true</span> to check up the status of the item in the repository,
     * 									that will tell if the local item is out-of-date (like <i>'-u'</i> option in the
     * 									SVN client's <code>'svn status'</code> command), 
     * 									otherwise <span class="javakeyword">false</span>
     * @param  collectParentExternals	<span class="javakeyword">false</span> to make the operation ignore information
     * 									on externals definitions (like <i>'--ignore-externals'</i> option in the SVN
     * 									client's <code>'svn status'</code> command), otherwise <span class="javakeyword">false</span>
     * @return							an <b>SVNStatus</b> object representing status information 
     * 									for the item
     * @throws SVNException
     */
    public SVNStatus doStatus(File path, boolean remote, boolean collectParentExternals) throws SVNException {
        final SVNStatus[] result = new SVNStatus[] { null };
        final File absPath = path.getAbsoluteFile();
        ISVNStatusHandler handler = new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) {
                if (absPath.equals(status.getFile())) {
                    if (result[0] != null
                        && result[0].getContentsStatus() == SVNStatusType.STATUS_EXTERNAL
                        && absPath.isDirectory()) {
                        result[0] = status;
                        result[0].markExternal();
                    } else if (result[0] == null) {
                        result[0] = status;
                    }
                }
            }
        };
        doStatus(path, false, remote, true, true, collectParentExternals, handler);
        return result[0];
    }

    private Map collectParentExternals(File path, File root) throws SVNException {
        Map externals = new HashMap();
        SVNFileType type = SVNFileType.getType(path);
        if (type != SVNFileType.DIRECTORY) {
            return externals;
        }
        File target = path;
        SVNWCAccess wcAccess = createWCAccess();
        while(true) {
            path = path.getParentFile();
            if (path == null) {
                break;
            }
            SVNAdminArea area = null;
            try {
                area = wcAccess.open(path, false, 0);
            } catch (SVNException e) {
                break;
            }
            try {
                SVNVersionedProperties properties = area.getProperties("");
                String external = properties.getPropertyValue(SVNProperty.EXTERNALS);
                if (externals != null) {
                    SVNExternalInfo[] infos = SVNWCAccess.parseExternals("", external);
                    for (int i = 0; i < infos.length; i++) {
                        // info's path is relative to path, we should make it relative to the root,
                        // and only if it is child of the root.
                        File ext = new File(path, infos[i].getPath());
                        if (SVNPathUtil.isChildOf(target, ext)) {
                            // put into the map - path relative to root is a key.
                            String extPath = ext.getAbsolutePath().replace(File.separatorChar, '/');
                            String rootPath = root.getAbsolutePath().replace(File.separatorChar, '/');
                            String relativePath = extPath.substring(rootPath.length() + 1);
                            externals.put(relativePath, infos[i]);
                        }
                    }
                }
            } finally {
                wcAccess.closeAdminArea(path);
            }
        }
        return externals;
    }

}
