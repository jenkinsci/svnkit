package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNStatusEditor;
import org.tmatesoft.svn.core.internal.wc.SVNStatusReporter;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SVNStatusClient extends SVNBasicClient {
    public SVNStatusClient() {
    }

    public SVNStatusClient(ISVNEventHandler eventDispatcher) {
        super(eventDispatcher);
    }

    public SVNStatusClient(ISVNOptions options, ISVNEventHandler eventDispatcher) {
        super(options, eventDispatcher);
    }

    public SVNStatusClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options, ISVNEventHandler eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }

    public void doStatus(File path, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, ISVNStatusHandler handler) throws SVNException {
        doStatus(path, recursive, remote, reportAll, includeIgnored, false, handler);
    }


    public long doStatus(File path, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, boolean collectParentExternals, ISVNStatusHandler handler) throws SVNException {
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
                handler.handleStatus(new SVNStatus(null, path, SVNNodeKind.DIR, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED, null, null, SVNStatusType.STATUS_EXTERNAL,
                        SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE, false, false, false, null, null, null, null, null, SVNRevision.UNDEFINED,
                        null, null, null));
            }
        }
        SVNStatusEditor statusEditor = new SVNStatusEditor(getOptions(), wcAccess, handler, parentExternals, includeIgnored, reportAll, recursive);
        if (remote) {
            String url = wcAccess.getAnchor().getEntries().getEntry("", true).getURL();
            SVNRepository repos = createRepository(url);
            SVNRepository locksRepos = createRepository(url);

            SVNReporter reporter = new SVNReporter(wcAccess, false, recursive);
            SVNStatusReporter statusReporter = new SVNStatusReporter(locksRepos, reporter, statusEditor);
            String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();

            repos.status(-1, target, recursive, statusReporter, statusEditor);
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
                File externalFile = new File(wcAccess.getAnchor().getRoot(), externalPath);
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

    public SVNStatus doStatus(final File path, boolean remote) throws SVNException {
        final SVNStatus[] result = new SVNStatus[]{null};
        ISVNStatusHandler handler = new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) {
                if (path.equals(status.getFile())) {
                    if (result[0] != null && result[0].getContentsStatus() == SVNStatusType.STATUS_EXTERNAL && path.isDirectory()) {
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
            SVNStatusEditor statusEditor = new SVNStatusEditor(getOptions(), wcAccess, handler, new HashMap(), true, true, false);
            String name = wcAccess.getTargetName();
            if (wcAccess.getAnchor() != wcAccess.getTarget()) {
                name = "";
            }
            statusEditor.reportStatus(wcAccess.getTarget(), name, false, false);
            return result[0];
        }
        doStatus(path, false, remote, true, true, true, handler);
        return result[0];
    }

    private Map collectParentExternals(File path, boolean asTarget) throws SVNException {
        Map externals = new HashMap();
        if (path.isFile()) {
            return externals;
        }
        File wcRoot = SVNWCUtil.getWorkingCopyRoot(path, false);
        if (wcRoot == null || wcRoot.equals(path)) {
            return externals;
        }
        String currentPath = path.getName();
        String baseName = path.getName();
        do {
            path = path.getParentFile();
            SVNWCAccess wcAccess = createWCAccess(path);
            // get externals from anchor.
            String externalsProperty = wcAccess.getAnchor().getProperties("", false).getPropertyValue(SVNProperty.EXTERNALS);
            if (externalsProperty != null) {
                SVNExternalInfo[] infos = SVNWCAccess.parseExternals("", externalsProperty);
                for (int i = 0; i < infos.length; i++) {
                    SVNExternalInfo info = infos[i];
                    String infoPath = info.getPath();
                    if (infoPath.equals(currentPath)) {
                        // target dir.
                        info.setPath("");
                        externals.put(info.getPath(), info);
                    } else if (infoPath.startsWith(currentPath + "/")) {
                        // below target dir.
                        info.setPath(infoPath.substring((currentPath + "/").length()));
                        if (asTarget) {
                            info.setPath(baseName + "/" + info.getPath());
                        }
                        externals.put(info.getPath(), info);
                    }
                }

            }
            currentPath = path.getName() + "/" + currentPath;
        } while (!wcRoot.equals(path));
        // collect up to real wc root
        return externals;
    }
}
