package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.ISVNDirEntryHandler;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 19.06.2005
 * Time: 0:42:49
 * To change this template use File | Settings | File Templates.
 */
public class SVNLogClient extends SVNBasicClient {

    public SVNLogClient() {
    }

    public SVNLogClient(ISVNEventListener eventDispatcher) {
        super(eventDispatcher);
    }

    public SVNLogClient(ISVNCredentialsProvider credentialsProvider) {
        super(credentialsProvider);
    }

    public SVNLogClient(ISVNCredentialsProvider credentialsProvider, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, eventDispatcher);
    }

    public SVNLogClient(final ISVNCredentialsProvider credentialsProvider, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, options, eventDispatcher);
    }

    public SVNLogClient(ISVNRepositoryFactory repositoryFactory, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }

    public void doAnnotate(File path, SVNRevision pegRevision, SVNRevision revision, ISVNAnnotationHandler handler) throws SVNException {
    }

    public void doAnnotate(String url, SVNRevision pegRevision, SVNRevision revision, ISVNAnnotationHandler handler) throws SVNException {
    }

    public void doLog(File path, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean reportPaths,
                      ISVNLogEntryHandler handler) throws SVNException {
    }

    public void doLog(String url, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean reportPaths,
                      ISVNLogEntryHandler handler) throws SVNException {
    }

    public void doList(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {

    }

    public void doList(String url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {

    }
}
