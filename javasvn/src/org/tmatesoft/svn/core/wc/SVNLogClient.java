package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.SVNAnnotationGenerator;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.util.DebugLog;

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

    public void doAnnotate(File path, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        if (startRevision == null || !startRevision.isValid()) {
            startRevision = SVNRevision.create(1);
        }
        SVNRepository repos = createRepository(path, null, pegRevision, endRevision, null);
        DebugLog.log("end revision: " + endRevision);
        long endRev = getRevisionNumber(path, null, repos, endRevision);
        long startRev = getRevisionNumber(path, null, repos, startRevision);
        if (endRev < startRev) {
            SVNErrorManager.error("svn: Start revision must precede end revision (" + startRev + ":" + endRev + ")");
        }
//        SVNNodeKind nodeKind = repos.checkPath("", endRev);
//        if (nodeKind == SVNNodeKind.DIR) {
//            SVNErrorManager.error("svn: URL '" + path + "' refers to a directory");
//        }
        File tmpFile = new File(path.getParentFile(), ".svn/tmp/text-base");
        if (!tmpFile.exists()) {
            tmpFile = new File(System.getProperty("user.home"), ".javasvn");
            tmpFile.mkdirs();
        }
        SVNAnnotationGenerator generator = new SVNAnnotationGenerator(path.getAbsolutePath(), startRev, tmpFile);
        try {
            repos.getFileRevisions("", startRev, endRev, generator);
            generator.reportAnnotations(handler, null);
        } finally {
            generator.dispose();
            SVNFileUtil.deleteAll(tmpFile, false);
        }
    }

    public void doAnnotate(String url, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        if (startRevision == null || !startRevision.isValid()) {
            startRevision = SVNRevision.create(1);
        }
        SVNRepository repos = createRepository(null, url, pegRevision, endRevision, null);
        long endRev = getRevisionNumber(null, url, repos, endRevision);
        long startRev = getRevisionNumber(null, url, repos, startRevision);
        if (endRev < startRev) {
            SVNErrorManager.error("svn: Start revision must precede end revision (" + startRev + ":" + endRev + ")");
        }
        File tmpFile = new File(System.getProperty("user.home"), ".javasvn");
        if (!tmpFile.exists()) {
            tmpFile.mkdirs();
        }
        SVNNodeKind nodeKind = repos.checkPath("", endRev);
        if (nodeKind == SVNNodeKind.DIR) {
            SVNErrorManager.error("svn: URL '" + url + "' refers to a directory");
        }
        SVNAnnotationGenerator generator = new SVNAnnotationGenerator(url, startRev, tmpFile);
        try {
            repos.getFileRevisions("", startRev, endRev, generator);
            generator.reportAnnotations(handler, null);
        } finally {
            generator.dispose();
            SVNFileUtil.deleteAll(tmpFile, false);
        }
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
