package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc.SVNStatusEditor;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;

import java.io.File;
import java.util.Map;
import java.util.Iterator;

public class SVNStatusClient extends SVNBasicClient {

    public SVNStatusClient(final ISVNCredentialsProvider creds, ISVNEventListener eventDispatcher) {
        this(creds, null, eventDispatcher);        
    }
    public SVNStatusClient(final ISVNCredentialsProvider creds, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(new ISVNRepositoryFactory() {
            public SVNRepository createRepository(String url) throws SVNException {
                SVNRepositoryLocation location = SVNRepositoryLocation.parseURL(url);
                SVNRepository repos = SVNRepositoryFactory.create(location);
                if (creds != null) {
                    repos.setCredentialsProvider(creds);
                }
                return repos;
            }
        }, options, eventDispatcher);
    }

    public SVNStatusClient(ISVNRepositoryFactory repositoryFactory, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }

    public void doStatus(File path, boolean recursive, boolean remote, boolean reportAll, boolean includeIgnored, ISVNStatusHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        wcAccess.open(false, recursive);
        SVNStatusEditor statusEditor = new SVNStatusEditor(getOptions(), wcAccess, handler, includeIgnored, reportAll, recursive);
        if (!remote) {
            statusEditor.closeEdit();
        } else {
            // do report, collect repos locks, and drive the editor.
        }
        wcAccess.close(false, recursive);
        if (!isIgnoreExternals() && recursive) {
            Map externals = statusEditor.getCollectedExternals();
            for (Iterator paths = externals.keySet().iterator(); paths.hasNext();) {
                String externalPath = (String) paths.next();
                File externalFile = new File(wcAccess.getAnchor().getRoot(), externalPath);
                svnEvent(SVNEventFactory.createStatusExternalEvent(wcAccess, externalPath), ISVNEventListener.UNKNOWN);
                setEventPathPrefix(externalPath);
                try {
                    doStatus(externalFile, recursive, remote, reportAll, includeIgnored, handler);
                } catch (SVNException e) {
                    // fire error event.
                } finally {
                    setEventPathPrefix(externalPath);
                }
            }
        }
    }

    public SVNStatus doStatus(final File path, boolean remote) throws SVNException {
        final SVNStatus[] result = new SVNStatus[] {null};
        doStatus(path, false, remote, true, true, new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) {
                if (result[0] == null && path.equals(status.getFile())) {
                    result[0] = status;
                }
            }
        });
        return result[0];
    }
}
