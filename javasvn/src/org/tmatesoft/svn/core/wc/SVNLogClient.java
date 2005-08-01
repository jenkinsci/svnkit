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
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNAnnotationGenerator;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNLogClient extends SVNBasicClient {

    public SVNLogClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNLogClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options) {
        super(repositoryFactory, options);
    }

    public void doAnnotate(File path, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        if (startRevision == null || !startRevision.isValid()) {
            startRevision = SVNRevision.create(1);
        }
        SVNRepository repos = createRepository(null, path, pegRevision, endRevision);
        long endRev = getRevisionNumber(endRevision, repos, path);
        long startRev = getRevisionNumber(startRevision, repos, path);
        if (endRev < startRev) {
            SVNErrorManager.error("svn: Start revision must precede end revision (" + startRev + ":" + endRev + ")");
        }
        File tmpFile = new File(path.getParentFile(), ".svn/tmp/text-base");
        if (!tmpFile.exists()) {
            tmpFile = new File(System.getProperty("user.home"), ".javasvn");
            tmpFile.mkdirs();
        }
        doAnnotate(path.getAbsolutePath(), startRev, tmpFile, repos, endRev, handler);
    }

    public void doAnnotate(SVNURL url, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        if (startRevision == null || !startRevision.isValid()) {
            startRevision = SVNRevision.create(1);
        }
        SVNRepository repos = createRepository(url, null, pegRevision, endRevision);
        long endRev = getRevisionNumber(endRevision, repos, null);
        long startRev = getRevisionNumber(startRevision, repos, null);
        if (endRev < startRev) {
            SVNErrorManager.error("svn: Start revision must precede end revision (" + startRev + ":" + endRev + ")");
        }
        File tmpFile = new File(System.getProperty("user.home"), ".javasvn");
        if (!tmpFile.exists()) {
            tmpFile.mkdirs();
        }
        doAnnotate(repos.getRepositoryPath(""), startRev, tmpFile, repos, endRev, handler);
    }

    private void doAnnotate(String path, long startRev, File tmpFile, SVNRepository repos, long endRev, ISVNAnnotateHandler handler) throws SVNException {
        SVNAnnotationGenerator generator = new SVNAnnotationGenerator(path, startRev, tmpFile, this);
        try {
            repos.getFileRevisions("", startRev, endRev, generator);
            generator.reportAnnotations(handler, null);
        } finally {
            generator.dispose();
            SVNFileUtil.deleteAll(tmpFile, false, null);
        }
    }

    public void doLog(File[] paths, SVNRevision startRevision,
            SVNRevision endRevision, boolean stopOnCopy, boolean reportPaths,
            long limit, ISVNLogEntryHandler handler) throws SVNException {
        if (paths == null || paths.length == 0) {
            return;
        }
        if (startRevision.isValid() && !endRevision.isValid()) {
            endRevision = startRevision;
        } else if (!startRevision.isValid()) {
            startRevision = SVNRevision.BASE;
            if (!endRevision.isValid()) {
                endRevision = SVNRevision.create(0);
            }
        }
        String[] urls = new String[paths.length];
        for (int i = 0; i < paths.length; i++) {
            File path = paths[i];
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNEntry entry = wcAccess.getTargetEntry();
            if (entry == null) {
                SVNErrorManager.error("svn: '" + path + "' is not under version control");
                return;
            }
            if (entry.getURL() == null) {
                SVNErrorManager.error("svn: '" + path + "' has no URL");
            }
            urls[i] = entry.getURL();
        }
        if (urls.length == 0) {
            return;
        }
        Collection targets = new TreeSet();
        String baseURL = SVNPathUtil.condenceURLs(urls, targets, true);
        if (baseURL == null || "".equals(baseURL)) {
            SVNErrorManager.error("svn: Entries belongs to different repositories");
        }
        if (targets.isEmpty()) {
            targets.add("");
        }
        SVNRepository repos = createRepository(baseURL);
        String[] targetPaths = (String[]) targets.toArray(new String[targets.size()]);
        for (int i = 0; i < targetPaths.length; i++) {
            targetPaths[i] = SVNEncodingUtil.uriDecode(targetPaths[i]);
        }
        if (startRevision.isLocal() || endRevision.isLocal()) {
            for (int i = 0; i < paths.length; i++) {
                long startRev = getRevisionNumber(startRevision, repos, paths[i]);
                long endRev = getRevisionNumber(endRevision, repos, paths[i]);
                repos.log(targetPaths, startRev, endRev, reportPaths, stopOnCopy, limit, handler);
            }
        } else {
            long startRev = getRevisionNumber(startRevision, repos, null);
            long endRev = getRevisionNumber(endRevision, repos, null);
            repos.log(targetPaths, startRev, endRev, reportPaths, stopOnCopy, limit, handler);
        }
    }

    public void doLog(SVNURL url, String[] paths, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean reportPaths, long limit, ISVNLogEntryHandler handler) throws SVNException {
        if (startRevision.isValid() && !endRevision.isValid()) {
            endRevision = startRevision;
        } else if (!startRevision.isValid()) {
            startRevision = SVNRevision.HEAD;
            if (!endRevision.isValid()) {
                endRevision = SVNRevision.create(0);
            }
        }
        paths = paths == null || paths.length == 0 ? new String[] {""} : paths;
        SVNRepository repos = createRepository(url);
        long startRev = getRevisionNumber(startRevision, repos, null);
        long endRev = getRevisionNumber(endRevision, repos, null);
        repos.log(paths, startRev, endRev, reportPaths, stopOnCopy, limit, handler);
    }

    public void doList(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler)
            throws SVNException {
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.BASE;
        }
        SVNRepository repos = createRepository(null, path, pegRevision, revision);
        long rev = getRevisionNumber(revision, repos, path);
        doList(repos, rev, handler, recursive);
    }

    public void doList(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        SVNRepository repos = createRepository(url, null, pegRevision, revision);
        long rev = getRevisionNumber(revision, repos, null);
        doList(repos, rev, handler, recursive);
    }

    private void doList(SVNRepository repos, long rev, ISVNDirEntryHandler handler, boolean recursive) throws SVNException {
        if (repos.checkPath("", rev) == SVNNodeKind.FILE) {
            SVNDirEntry entry = repos.info("", rev);
            String name = SVNPathUtil.tail(repos.getLocation().getPath());
            entry.setPath(name);
            handler.handleDirEntry(entry);
        } else {
            list(repos, "", rev, recursive, handler);
        }
    }

    private static void list(SVNRepository repository, String path, long rev,
            boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        Collection entries = new TreeSet();
        entries = repository.getDir(path, rev, null, entries);

        for (Iterator iterator = entries.iterator(); iterator.hasNext();) {
            SVNDirEntry entry = (SVNDirEntry) iterator.next();
            String childPath = SVNPathUtil.append(path, entry.getName());
            entry.setPath(childPath);
            handler.handleDirEntry(entry);
            if (entry.getKind() == SVNNodeKind.DIR && entry.getDate() != null && recursive) {
                list(repository, childPath, rev, recursive, handler);
            }
        }
    }
}