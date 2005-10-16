/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNStatusEditor;
import org.tmatesoft.svn.core.internal.wc.SVNStatusReporter;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
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
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see		ISVNStatusHandler
 * @see		SVNStatus
 * @see     <a target="_top" href="http://tmate.org/svn/kb/examples/">Examples</a>
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
     * @throws SVNException
     * @see	                    ISVNStatusHandler
     */
    public void doStatus(File path, boolean recursive, boolean remote,
                         boolean reportAll, boolean includeIgnored, ISVNStatusHandler handler)
            throws SVNException {
        doStatus(path, recursive, remote, reportAll, includeIgnored, false,
                 handler);
    }
    
    /**
     * Collects status information on Working Copy items and passes
     * it to a <code>handler</code> . 
     *
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
    public long doStatus(File path, boolean recursive, boolean remote,
                         boolean reportAll, boolean includeIgnored,
                         boolean collectParentExternals, final ISVNStatusHandler handler)
            throws SVNException {
        if (handler == null) {
            return -1;
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        wcAccess.open(false, recursive);
        Map parentExternals = new HashMap();
        if (collectParentExternals) {
            parentExternals = collectParentExternals(path, wcAccess.getAnchor() != wcAccess.getTarget());
            SVNExternalInfo thisExternal = (SVNExternalInfo) parentExternals.remove("");
            if (thisExternal != null) {
                // report this as external first.
                handler.handleStatus(new SVNStatus(null, path, SVNNodeKind.DIR,
                                                   SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, null,
                                                   null, SVNStatusType.STATUS_EXTERNAL,
                                                   SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE,
                                                   SVNStatusType.STATUS_NONE, false, false, false, null,
                                                   null, null, null, null, SVNRevision.UNDEFINED, null,
                                                   null, null));
            }
        }
        final boolean[] deletedInRepos = new boolean[] {false};
        ISVNStatusHandler realHandler = new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) {
                if (deletedInRepos[0]) {
                    status.setRemoteStatus(SVNStatusType.STATUS_DELETED, null, null, null);
                }
                handler.handleStatus(status);
            }
        };
        SVNStatusEditor statusEditor = new SVNStatusEditor(getOptions(), wcAccess, realHandler, parentExternals, includeIgnored, reportAll, recursive);
        if (remote) {
            SVNURL url = wcAccess.getAnchor().getEntries().getEntry("", true).getSVNURL();
            SVNRepository repos = createRepository(url, true);
            SVNNodeKind kind = repos.checkPath("", -1);
            if (kind == SVNNodeKind.NONE) {
                deletedInRepos[0] = true;
            } else {
                SVNRepository locksRepos = createRepository(url, false);
    
                SVNReporter reporter = new SVNReporter(wcAccess, false, recursive);
                SVNStatusReporter statusReporter = new SVNStatusReporter(locksRepos, reporter, statusEditor);
                String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
    
                repos.status(-1, target, recursive, statusReporter, SVNCancellableEditor.newInstance(statusEditor, this));
            }
        }
        // to report all when there is completely no changes
        statusEditor.closeEdit();
        if (remote && statusEditor.getTargetRevision() >= 0) {
            SVNEvent event = SVNEventFactory.createStatusCompletedEvent(wcAccess, statusEditor.getTargetRevision());
            handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        wcAccess.close(false);
        if (!isIgnoreExternals() && recursive) {
            Map externals = statusEditor.getCollectedExternals();
            for (Iterator paths = externals.keySet().iterator(); paths.hasNext();) {
                String externalPath = (String) paths.next();
                File externalFile = new File(wcAccess.getAnchor().getRoot(),externalPath);
                if (!externalFile.exists() || !externalFile.isDirectory() || !SVNWCUtil.isWorkingCopyRoot(externalFile, true)) {
                    continue;
                }
                handleEvent(SVNEventFactory.createStatusExternalEvent(wcAccess, externalPath), ISVNEventHandler.UNKNOWN);
                setEventPathPrefix(externalPath);
                try {
                    doStatus(externalFile, recursive, remote, reportAll, includeIgnored, false, handler);
                } catch (SVNException e) {
                    // fire error event.
                } finally {
                    setEventPathPrefix(externalPath);
                }
            }
        }
        return statusEditor.getTargetRevision();
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
    public SVNStatus doStatus(final File path, boolean remote, boolean collectParentExternals) throws SVNException {
        final SVNStatus[] result = new SVNStatus[] { null };
        ISVNStatusHandler handler = new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) {
                if (path.equals(status.getFile())) {
                    if (result[0] != null
                        && result[0].getContentsStatus() == SVNStatusType.STATUS_EXTERNAL
                        && path.isDirectory()) {
                        result[0] = status;
                        result[0].markExternal();
                    } else if (result[0] == null) {
                        result[0] = status;
                    }
                }
            }
        };
        if (!remote) {
            // faster status.
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNStatusEditor statusEditor = new SVNStatusEditor(getOptions(),
                                                               wcAccess, handler, new HashMap(), true, true, false);
            String name = wcAccess.getTargetName();
            if (wcAccess.getAnchor() != wcAccess.getTarget()) {
                name = "";
            }
            if (collectParentExternals && path.isDirectory()) {
                Map parentExternals = collectParentExternals(path, wcAccess.getAnchor() != wcAccess.getTarget());
                SVNExternalInfo thisExternal = (SVNExternalInfo) parentExternals.remove("");
                if (thisExternal != null) {
                    handler.handleStatus(new SVNStatus(null, path, SVNNodeKind.DIR,
                                                       SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, null,
                                                       null, SVNStatusType.STATUS_EXTERNAL,
                                                       SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE,
                                                       SVNStatusType.STATUS_NONE, false, false, false, null,
                                                       null, null, null, null, SVNRevision.UNDEFINED, null,
                                                       null, null));
                }
            }
            statusEditor.reportStatus(wcAccess.getTarget(), name, false, false);
            return result[0];
        }
        doStatus(path, false, remote, true, true, collectParentExternals, handler);
        return result[0];
    }

    private Map collectParentExternals(File path, boolean asTarget) throws SVNException {
        Map externals = new HashMap();
        if (path.isFile()) {
            return externals;
        }
        // get non-real wc root, just last one with .svn directory in it
        File wcRoot = path.getAbsoluteFile();
        if (wcRoot.getParentFile() == null) {
          return externals;
        }
        wcRoot = wcRoot.getParentFile().getAbsoluteFile();
        if (wcRoot == null || !new File(wcRoot, SVNFileUtil.getAdminDirectoryName()).isDirectory()) {
          // parent is not versioned.
          return externals;
        }
        Stack dirs = new Stack();
        String currentPath = path.getName();
        String baseName = path.getName();
        while(wcRoot.getParentFile() != null && new File(wcRoot.getParentFile(), SVNFileUtil.getAdminDirectoryName()).isDirectory()) {
          dirs.push(currentPath);
          currentPath = SVNPathUtil.append(wcRoot.getName(), currentPath);
          wcRoot = wcRoot.getParentFile();
        }
        dirs.push(currentPath);

        // now go back.
        while(!dirs.isEmpty()) {
          currentPath = (String) dirs.pop();
          SVNProperties props = new SVNProperties(new File(wcRoot, SVNFileUtil.getAdminDirectoryName() + "/dir-props"), "");

          String externalsProperty = props.getPropertyValue(SVNProperty.EXTERNALS);
          if (externalsProperty != null) {
            SVNExternalInfo[] infos = SVNWCAccess.parseExternals("", externalsProperty);
            for (int i = 0; i < infos.length; i++) {
                SVNExternalInfo info = infos[i];
                String infoPath = info.getPath();
                if (infoPath.equals(currentPath)) {
                    info.setPath("");
                    externals.put(info.getPath(), info);
                } else if (infoPath.startsWith(currentPath + "/")) {
                    info.setPath(infoPath.substring((currentPath + "/").length()));
                    if (asTarget) {
                        info.setPath(baseName + "/" + info.getPath());
                    }
                    externals.put(info.getPath(), info);
                }
            }

          }
          wcRoot = new File(wcRoot, SVNPathUtil.head(currentPath));
        }
        return externals;
    }
}
