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

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.*;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNStatusClient extends SVNBasicClient {
    public SVNStatusClient() {
    }

    public SVNStatusClient(ISVNEventHandler eventDispatcher) {
        super(eventDispatcher);
    }

    public SVNStatusClient(ISVNOptions options, ISVNEventHandler eventDispatcher) {
        super(options, eventDispatcher);
    }

    public SVNStatusClient(ISVNRepositoryFactory repositoryFactory,
                           ISVNOptions options, ISVNEventHandler eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }

    public void doStatus(File path, boolean recursive, boolean remote,
                         boolean reportAll, boolean includeIgnored, ISVNStatusHandler handler)
            throws SVNException {
        doStatus(path, recursive, remote, reportAll, includeIgnored, false,
                 handler);
    }

    public long doStatus(File path, boolean recursive, boolean remote,
                         boolean reportAll, boolean includeIgnored,
                         boolean collectParentExternals, ISVNStatusHandler handler)
            throws SVNException {
        if (handler == null) {
            return -1;
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        wcAccess.open(false, recursive);
        Map parentExternals = new HashMap();
        if (collectParentExternals) {
            parentExternals = collectParentExternals(path,
                                                     wcAccess.getAnchor() != wcAccess.getTarget());
            SVNExternalInfo thisExternal = (SVNExternalInfo) parentExternals
                    .remove("");
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
        SVNStatusEditor statusEditor = new SVNStatusEditor(getOptions(),
                                                           wcAccess, handler, parentExternals, includeIgnored, reportAll,
                                                           recursive);
        if (remote) {
            String url = wcAccess.getAnchor().getEntries().getEntry("", true)
                    .getURL();
            SVNRepository repos = createRepository(url);
            SVNRepository locksRepos = createRepository(url);

            SVNReporter reporter = new SVNReporter(wcAccess, false, recursive);
            SVNStatusReporter statusReporter = new SVNStatusReporter(
                    locksRepos, reporter, statusEditor);
            String target = "".equals(wcAccess.getTargetName()) ? null
                            : wcAccess.getTargetName();

            repos.status(-1, target, recursive, statusReporter, statusEditor);
        }
        // to report all when there is completely no changes
        statusEditor.closeEdit();
        if (remote && statusEditor.getTargetRevision() >= 0) {
            SVNEvent event = SVNEventFactory.createStatusCompletedEvent(
                    wcAccess, statusEditor.getTargetRevision());
            handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        wcAccess.close(false);
        if (!isIgnoreExternals() && recursive) {
            Map externals = statusEditor.getCollectedExternals();
            for (Iterator paths = externals.keySet().iterator(); paths
                    .hasNext();) {
                String externalPath = (String) paths.next();
                File externalFile = new File(wcAccess.getAnchor().getRoot(),
                                             externalPath);
                if (!externalFile.exists() || !externalFile.isDirectory()
                    || !SVNWCUtil.isWorkingCopyRoot(externalFile, true)) {
                    continue;
                }
                handleEvent(SVNEventFactory.createStatusExternalEvent(wcAccess,
                                                                      externalPath), ISVNEventHandler.UNKNOWN);
                setEventPathPrefix(externalPath);
                try {
                    doStatus(externalFile, recursive, remote, reportAll,
                             includeIgnored, false, handler);
                } catch (SVNException e) {
                    // fire error event.
                } finally {
                    setEventPathPrefix(externalPath);
                }
            }
        }
        return statusEditor.getTargetRevision();
    }

    public SVNStatus doStatus(final File path, boolean remote) throws SVNException {
        return doStatus(path, remote, false);
    }

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
        if (wcRoot == null || !new File(wcRoot, ".svn").isDirectory()) {
          // parent is not versioned.
          return externals;
        }
        Stack dirs = new Stack();
        String currentPath = path.getName();
        String baseName = path.getName();
        while(wcRoot.getParentFile() != null && new File(wcRoot.getParentFile(), ".svn").isDirectory()) {
          dirs.push(currentPath);
          currentPath = PathUtil.append(wcRoot.getName(), currentPath);
          wcRoot = wcRoot.getParentFile();
        }
        dirs.push(currentPath);

        // now go back.
        while(!dirs.isEmpty()) {
          currentPath = (String) dirs.pop();
          SVNProperties props = new SVNProperties(new File(wcRoot, ".svn/dir-props"), "");

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
          wcRoot = new File(wcRoot, PathUtil.head(currentPath));
        }
        return externals;
    }
}
