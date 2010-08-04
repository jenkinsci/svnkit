/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbLock;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDirDeletedInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.ISVNDebugLog;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNReporter17 implements ISVNReporterBaton {

    private final File path;
    private SVNWCContext wcContext;
    private final SVNDepth depth;
    private final boolean isRestoreFiles;
    private final boolean isUdeDepthCompatibilityTrick;
    private final boolean lockOnDemand;
    private final boolean isStatus;
    private final boolean isHonorDepthExclude;
    private final ISVNDebugLog log;
    private boolean isUseCommitTimes;
    private int reportedFilesCount;
    private int totalFilesCount;

    public SVNReporter17(File path, SVNWCContext wcContext, boolean restoreFiles, boolean useDepthCompatibilityTrick, SVNDepth depth, boolean lockOnDemand, boolean isStatus,
            boolean isHonorDepthExclude, boolean isUseCommitTimes, ISVNDebugLog log) {
        this.path = path;
        this.wcContext = wcContext;
        this.isRestoreFiles = restoreFiles;
        this.isUdeDepthCompatibilityTrick = useDepthCompatibilityTrick;
        this.depth = depth;
        this.lockOnDemand = lockOnDemand;
        this.isStatus = isStatus;
        this.isHonorDepthExclude = isHonorDepthExclude;
        this.isUseCommitTimes = isUseCommitTimes;
        this.log = log;
    }

    public int getReportedFilesCount() {
        return reportedFilesCount;
    }

    public int getTotalFilesCount() {
        return totalFilesCount;
    }

    public void report(ISVNReporter reporter) throws SVNException {

        assert (SVNWCDb.isAbsolute(path));

        /*
         * The first thing we do is get the base_rev from the working copy's
         * ROOT_DIRECTORY. This is the first revnum that entries will be
         * compared to.
         */

        boolean has_base = true;

        SVNWCDbStatus status;
        SVNWCDbKind target_kind;
        long target_rev = 0;
        File repos_relpath = null;
        SVNURL repos_root = null;
        SVNDepth target_depth = null;
        SVNWCDbLock target_lock = null;
        boolean explicit_rev, start_empty;

        try {

            final WCDbBaseInfo baseInfo = wcContext.getDb().getBaseInfo(path, BaseInfoField.status, BaseInfoField.kind, BaseInfoField.revision, BaseInfoField.reposRelPath, BaseInfoField.reposRootUrl,
                    BaseInfoField.depth, BaseInfoField.lock);

            status = baseInfo.status;
            target_kind = baseInfo.kind;
            target_rev = baseInfo.revision;
            repos_relpath = baseInfo.reposRelPath;
            repos_root = baseInfo.reposRootUrl;
            target_depth = baseInfo.depth;
            target_lock = baseInfo.lock;

        } catch (SVNException e) {

            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND)
                throw e;

            has_base = false;
            target_kind = wcContext.getDb().readKind(path, true);

            if (target_kind == SVNWCDbKind.File || target_kind == SVNWCDbKind.Symlink)
                status = SVNWCDbStatus.Absent; /* Crawl via parent dir */
            else
                status = SVNWCDbStatus.NotPresent; /* As checkout */

        }

        /*
         * ### Check the parentstub if we don't find a BASE. But don't do this
         * if we already have the info we want or we break some copy scenarios.
         */
        if (!has_base && target_kind == SVNWCDbKind.Dir) {
            boolean not_present = false;
            long rev = SVNWCContext.INVALID_REVNUM;

            try {
                final WCDbDirDeletedInfo dirDeleted = wcContext.getDb().isDirDeletedTemp(path);
                not_present = dirDeleted.notPresent;
                rev = dirDeleted.baseRevision;
            } catch (SVNException e) {

                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND || e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_WORKING_COPY) {
                    not_present = false;
                } else
                    throw e;

            }

            if (not_present)
                status = SVNWCDbStatus.NotPresent;

            if (!SVNRevision.isValidRevisionNumber(target_rev))
                target_rev = rev;
        }

        if ((status == SVNWCDbStatus.NotPresent) || (target_kind == SVNWCDbKind.Dir && status != SVNWCDbStatus.Normal && status != SVNWCDbStatus.Incomplete)) {
            /* The target does not exist or is a local addition */

            if (!SVNRevision.isValidRevisionNumber(target_rev))
                target_rev = 0;

            if (target_depth == SVNDepth.UNKNOWN)
                target_depth = SVNDepth.INFINITY;

            reporter.setPath("", null, target_rev, depth, false);
            reporter.deletePath("");

            /*
             * Finish the report, which causes the update editor to be driven.
             */
            reporter.finishReport();

            return;
        }

        if (repos_root == null || repos_relpath == null) {
            try {
                final WCDbRepositoryInfo baseRep = wcContext.getDb().scanBaseRepository(path, RepositoryInfoField.relPath, RepositoryInfoField.rootUrl);
                repos_root = baseRep.rootUrl;
                repos_relpath = baseRep.relPath;
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND)
                    throw e;
            }

            /*
             * Ok, that leaves a local addition. Deleted and not existing nodes
             * are already handled.
             */
            if (repos_root == null || repos_relpath == null) {
                final WCDbAdditionInfo addition = wcContext.getDb().scanAddition(path, AdditionInfoField.reposRelPath, AdditionInfoField.reposRootUrl);
                repos_root = addition.reposRootUrl;
                repos_relpath = addition.reposRelPath;
            }
        }

        if (!SVNRevision.isValidRevisionNumber(target_rev)) {
            target_rev = findBaseRev(path, path);
            explicit_rev = true;
        } else
            explicit_rev = false;

        start_empty = (status == SVNWCDbStatus.Incomplete);
        if (isUdeDepthCompatibilityTrick && target_depth.compareTo(SVNDepth.IMMEDIATES) <= 0 && depth.compareTo(target_depth) > 0) {
            start_empty = true;
        }

        if (target_depth == SVNDepth.UNKNOWN)
            target_depth = SVNDepth.INFINITY;

        /*
         * The first call to the reporter merely informs it that the top-level
         * directory being updated is at BASE_REV. Its PATH argument is ignored.
         */
        reporter.setPath("", null, target_rev, target_depth, start_empty);

        /*
         * ### status can NEVER be deleted. should examine why this was ### ever
         * here. we may have remapped into wc-ng incorrectly.
         */
        boolean missing = false;
        if (status != SVNWCDbStatus.Deleted) {
            SVNFileType fileType = SVNFileType.getType(path);
            missing = fileType == SVNFileType.NONE;
        }

        try {

            if (missing && isRestoreFiles) {
                boolean restored = restoreNode(path, target_kind, target_rev);
                if (restored)
                    missing = false;
            }

            if (target_kind == SVNWCDbKind.Dir) {
                if (missing) {
                    /*
                     * Report missing directories as deleted to retrieve them
                     * from the repository.
                     */
                    reporter.deletePath("");
                } else if (depth != SVNDepth.EMPTY) {
                    /*
                     * Recursively crawl ROOT_DIRECTORY and report differing
                     * revisions.
                     */
                    reportRevisionsAndDepths(path, "", target_rev, reporter);
                }
            }

            else if (target_kind == SVNWCDbKind.File || target_kind == SVNWCDbKind.Symlink) {
                boolean skip_set_path = false;

                File parent_abspath = SVNFileUtil.getFileDir(path);
                String base = SVNFileUtil.getFileName(path);

                /*
                 * We can assume a file is in the same repository as its parent
                 * directory, so we only look at the relpath.
                 */
                final WCDbBaseInfo baseInfo = wcContext.getDb().getBaseInfo(parent_abspath, BaseInfoField.status, BaseInfoField.reposRelPath);
                SVNWCDbStatus parent_status = baseInfo.status;
                File parent_repos_relpath = baseInfo.reposRelPath;

                if (parent_repos_relpath == null) {
                    parent_repos_relpath = wcContext.getDb().scanBaseRepository(parent_abspath, RepositoryInfoField.relPath).relPath;
                }

                if (!repos_relpath.equals(SVNFileUtil.createFilePath(parent_repos_relpath, base))) {
                    /*
                     * This file is disjoint with respect to its parent
                     * directory. Since we are looking at the actual target of
                     * the report (not some file in a subdirectory of a target
                     * directory), and that target is a file, we need to pass an
                     * empty string to link_path.
                     */
                    reporter.linkPath(SVNURL.parseURIDecoded(SVNPathUtil.append(repos_root.toDecodedString(), repos_relpath.getPath())), "", target_lock != null ? target_lock.token : null,
                            target_rev, target_depth, false);
                    skip_set_path = true;
                }

                if (!skip_set_path && (explicit_rev || target_lock != null)) {
                    /*
                     * If this entry is a file node, we just want to report that
                     * node's revision. Since we are looking at the actual
                     * target of the report (not some file in a subdirectory of
                     * a target directory), and that target is a file, we need
                     * to pass an empty string to set_path.
                     */
                    reporter.setPath("", target_lock != null ? target_lock.token : null, target_rev, target_depth, false);
                }
            }

            /* Finish the report, which causes the update editor to be driven. */
            reporter.finishReport();

        } catch (SVNException e) {
            // abort_report:
            /* Clean up the fs transaction. */
            reporter.abortReport();
        }

    }

    private boolean restoreNode(File local_abspath, SVNWCDbKind kind, long target_rev) throws SVNException {
        boolean restored = false;

        /*
         * Currently we can only restore files, but we will be able to restore
         * directories after we move to a single database and pristine store.
         */
        if (kind == SVNWCDbKind.File || kind == SVNWCDbKind.Symlink) {
            /* ... recreate file from text-base, and ... */
            restoreFile(local_abspath);
            restored = true;
            /* ... report the restoration to the caller. */
            if (wcContext.getEventHandler() != null) {
                wcContext.getEventHandler().handleEvent(SVNEventFactory.createSVNEvent(local_abspath, SVNNodeKind.FILE, null, target_rev, SVNEventAction.RESTORE, null, null, null), 0);
            }
        }
        return restored;
    }

    /**
     * Helper for report_revisions_and_depths().
     *
     * Perform an atomic restoration of the file LOCAL_ABSPATH; that is, copy
     * the file's text-base to the administrative tmp area, and then move that
     * file to LOCAL_ABSPATH with possible translations/expansions. If
     * USE_COMMIT_TIMES is set, then set working file's timestamp to
     * last-commit-time. Either way, set entry-timestamp to match that of the
     * working file when all is finished.
     *
     * Not that a valid access baton with a write lock to the directory of
     * LOCAL_ABSPATH must be available in DB.
     */
    private void restoreFile(File localAbsPath) throws SVNException {
        throw new UnsupportedOperationException();
    }

    /**
     * Helper for svn_wc_crawl_revisions5() that finds a base revision for a
     * node that doesn't have one itself.
     */
    private long findBaseRev(File path, File topPath) throws SVNException {
        throw new UnsupportedOperationException();
    }

    private void reportRevisionsAndDepths(File localAbsPath, String dirPath, long target_rev, ISVNReporter reporter) {
        throw new UnsupportedOperationException();
    }
}
