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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumInputStream;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCSchedule;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDeletionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbLock;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbOpenMode;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDeletionInfo.DeletionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNWCContext {

    public static class SVNEolStyleInfo {

        public static final byte[] NATIVE_EOL_STR = System.getProperty("line.separator").getBytes();
        public static final byte[] LF_EOL_STR = {
            '\n'
        };
        public static final byte[] CR_EOL_STR = {
            '\r'
        };
        public static final byte[] CRLF_EOL_STR = {
                '\r', '\n'
        };

        public SVNEolStyle eolStyle;
        public byte[] eolStr;

        public SVNEolStyleInfo(SVNEolStyle style, byte[] str) {
            this.eolStyle = style;
            this.eolStr = str;
        }

        public static SVNEolStyleInfo fromValue(String value) {
            if (value == null) {
                /* property doesn't exist. */
                return new SVNEolStyleInfo(SVNEolStyle.None, null);
            } else if ("native".equals(value)) {
                return new SVNEolStyleInfo(SVNEolStyle.Native, NATIVE_EOL_STR);
            } else if ("LF".equals(value)) {
                return new SVNEolStyleInfo(SVNEolStyle.Fixed, LF_EOL_STR);
            } else if ("CR".equals(value)) {
                return new SVNEolStyleInfo(SVNEolStyle.Fixed, CR_EOL_STR);
            } else if ("CRLF".equals(value)) {
                return new SVNEolStyleInfo(SVNEolStyle.Fixed, CRLF_EOL_STR);
            } else {
                return new SVNEolStyleInfo(SVNEolStyle.Unknown, null);
            }
        }

    }

    public enum SVNEolStyle {
        /** An unrecognized style */
        Unknown,
        /** EOL translation is "off" or ignored value */
        None,
        /** Translation is set to client's native eol */
        Native,
        /** Translation is set to one of LF, CR, CRLF */
        Fixed
    }

    private ISVNWCDb db;
    private boolean closeDb;
    private ISVNEventHandler eventHandler;

    public SVNWCContext(ISVNOptions config, ISVNEventHandler eventHandler) throws SVNException {
        this(WCDbOpenMode.ReadWrite, config, true, true, eventHandler);
    }

    public SVNWCContext(WCDbOpenMode mode, ISVNOptions config, boolean autoUpgrade, boolean enforceEmptyWQ, ISVNEventHandler eventHandler) throws SVNException {
        this.db = new SVNWCDb();
        this.db.open(mode, config, autoUpgrade, enforceEmptyWQ);
        this.closeDb = true;
        this.eventHandler = eventHandler;
    }

    public SVNWCContext(ISVNWCDb db, ISVNEventHandler eventHandler) {
        this.db = db;
        this.closeDb = false;
        this.eventHandler = eventHandler;
    }

    public void close() throws SVNException {
        if (closeDb) {
            db.close();
        }
    }

    public ISVNWCDb getDb() {
        return db;
    }

    public void checkCancelled() throws SVNCancelException {
        if (eventHandler != null) {
            eventHandler.checkCancelled();
        }
    }

    private boolean isAbsolute(File localAbsPath) {
        return localAbsPath != null && localAbsPath.isAbsolute();
    }

    private ISVNOptions getOptions() {
        return db.getConfig();
    }

    public SVNNodeKind getNodeKind(File path, boolean showHidden) throws SVNException {
        try {
            /* Make sure hidden nodes return SVNNodeKind.NONE. */
            if (!showHidden) {
                final boolean hidden = db.isNodeHidden(path);
                if (hidden) {
                    return SVNNodeKind.NONE;
                }
            }
            final WCDbKind kind = db.readKind(path, false);
            if (kind == null) {
                return SVNNodeKind.NONE;
            }
            return kind.toNodeKind();
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                return SVNNodeKind.NONE;
            }
            throw e;
        }
    }

    public boolean isNodeAdded(File path) throws SVNException {
        final WCDbInfo info = db.readInfo(path, InfoField.status);
        final WCDbStatus status = info.status;
        return status == WCDbStatus.Added || status == WCDbStatus.ObstructedAdd;
    }

    /** Equivalent to the old notion of "entry->schedule == schedule_replace" */
    public boolean isNodeReplaced(File path) throws SVNException {
        final WCDbInfo info = db.readInfo(path, InfoField.status, InfoField.baseShadowed);
        final WCDbStatus status = info.status;
        final boolean baseShadowed = info.baseShadowed;
        WCDbStatus baseStatus = null;
        if (baseShadowed) {
            final WCDbBaseInfo baseInfo = db.getBaseInfo(path, BaseInfoField.status);
            baseStatus = baseInfo.status;
        }
        return ((status == WCDbStatus.Added || status == WCDbStatus.ObstructedAdd) && baseShadowed && baseStatus != WCDbStatus.NotPresent);
    }

    public long getRevisionNumber(SVNRevision revision, long[] latestRevisionNumber, SVNRepository repository, File path) throws SVNException {
        if (repository == null && (revision == SVNRevision.HEAD || revision.getDate() != null)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_RA_ACCESS_REQUIRED);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (revision.getNumber() >= 0) {
            return revision.getNumber();
        } else if (revision.getDate() != null) {
            return repository.getDatedRevision(revision.getDate());
        } else if (revision == SVNRevision.HEAD) {
            if (latestRevisionNumber != null && latestRevisionNumber.length > 0 && SVNRevision.isValidRevisionNumber(latestRevisionNumber[0])) {
                return latestRevisionNumber[0];
            }
            long latestRevision = repository.getLatestRevision();
            if (latestRevisionNumber != null && latestRevisionNumber.length > 0) {
                latestRevisionNumber[0] = latestRevision;
            }
            return latestRevision;
        } else if (!revision.isValid()) {
            return -1;
        } else if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
            if (path == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_VERSIONED_PATH_REQUIRED);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            long revnum = -1;
            try {
                revnum = getNodeCommitBaseRev(path);
            } catch (SVNException e) {
                /*
                 * Return the same error as older code did (before and at
                 * r935091). At least svn_client_proplist3 promises
                 * SVN_ERR_ENTRY_NOT_FOUND.
                 */
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND);
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else {
                    throw e;
                }
            }
            if (!SVNRevision.isValidRevisionNumber(revnum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Path ''{0}'' has no committed revision", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            return revnum;
        } else if (revision == SVNRevision.COMMITTED || revision == SVNRevision.PREVIOUS) {
            if (path == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_VERSIONED_PATH_REQUIRED);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            final WCDbInfo info = getNodeChangedInfo(path);
            if (!SVNRevision.isValidRevisionNumber(info.changedRev)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Path ''{0}'' has no committed revision", path);
                SVNErrorManager.error(err, SVNLogType.WC);

            }
            return revision == SVNRevision.PREVIOUS ? info.changedRev - 1 : info.changedRev;
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Unrecognized revision type requested for ''{0}''", path != null ? path : (Object) repository.getLocation());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return -1;
    }

    private WCDbInfo getNodeChangedInfo(File path) throws SVNException {
        return db.readInfo(path, InfoField.changedRev, InfoField.changedDate, InfoField.changedAuthor);
    }

    private long getNodeCommitBaseRev(File local_abspath) throws SVNException {
        final WCDbInfo info = db.readInfo(local_abspath, InfoField.status, InfoField.revision, InfoField.baseShadowed);
        /*
         * If this returned a valid revnum, there is no WORKING node. The node
         * is cleanly checked out, no modifications, copies or replaces.
         */
        long commitBaseRevision = info.revision;
        if (SVNRevision.isValidRevisionNumber(commitBaseRevision)) {
            return commitBaseRevision;
        }
        final WCDbStatus status = info.status;
        final boolean baseShadowed = info.baseShadowed;
        if (status == WCDbStatus.Added) {
            /*
             * If the node was copied/moved-here, return the copy/move source
             * revision (not this node's base revision). If it's just added,
             * return INVALID_REVNUM.
             */
            final WCDbAdditionInfo addInfo = db.scanAddition(local_abspath, AdditionInfoField.originalRevision);
            commitBaseRevision = addInfo.originalRevision;
            if (!SVNRevision.isValidRevisionNumber(commitBaseRevision) && baseShadowed) {
                /*
                 * It is a replace that does not feature a copy/move-here.
                 * Return the revert-base revision.
                 */
                commitBaseRevision = getNodeBaseRev(local_abspath);
            }
        } else if (status == WCDbStatus.Deleted) {
            final WCDbDeletionInfo delInfo = db.scanDeletion(local_abspath, DeletionInfoField.workDelAbsPath);
            final File workDelAbspath = delInfo.workDelAbsPath;
            if (workDelAbspath != null) {
                /*
                 * This is a deletion within a copied subtree. Get the
                 * copied-from revision.
                 */
                final File parentAbspath = workDelAbspath.getParentFile();
                final WCDbInfo parentInfo = db.readInfo(parentAbspath, InfoField.status);
                final WCDbStatus parentStatus = parentInfo.status;
                assert (parentStatus == WCDbStatus.Added || parentStatus == WCDbStatus.ObstructedAdd);
                final WCDbAdditionInfo parentAddInfo = db.scanAddition(parentAbspath, AdditionInfoField.originalRevision);
                commitBaseRevision = parentAddInfo.originalRevision;
            } else
                /* This is a normal delete. Get the base revision. */
                commitBaseRevision = getNodeBaseRev(local_abspath);
        }
        return commitBaseRevision;
    }

    private long getNodeBaseRev(File local_abspath) throws SVNException {
        final WCDbInfo info = db.readInfo(local_abspath, InfoField.status, InfoField.revision, InfoField.baseShadowed);
        long baseRevision = info.revision;
        if (SVNRevision.isValidRevisionNumber(baseRevision)) {
            return baseRevision;
        }
        final boolean baseShadowed = info.baseShadowed;
        if (baseShadowed) {
            /* The node was replaced with something else. Look at the base. */
            final WCDbBaseInfo baseInfo = db.getBaseInfo(local_abspath, BaseInfoField.revision);
            baseRevision = baseInfo.revision;
        }
        return baseRevision;
    }

    public SVNStatus assembleStatus(File path, SVNURL parentReposRootUrl, File parentReposRelPath, SVNNodeKind pathKind, boolean pathSpecial, boolean getAll, boolean isIgnored,
            SVNLock repositoryLock, SVNURL repositoryRoot, SVNWCContext wCContext) throws SVNException {

        boolean locked_p = false;

        /* Defaults for two main variables. */
        SVNStatusType final_text_status = SVNStatusType.STATUS_NORMAL;
        SVNStatusType final_prop_status = SVNStatusType.STATUS_NONE;
        /* And some intermediate results */
        SVNStatusType pristine_text_status = SVNStatusType.STATUS_NONE;
        SVNStatusType pristine_prop_status = SVNStatusType.STATUS_NONE;

        SVNEntry entry = getEntry(path, false, SVNNodeKind.UNKNOWN, false);

        /*
         * Find out whether the path is a tree conflict victim. This function
         * will set tree_conflict to NULL if the path is not a victim.
         */
        SVNTreeConflictDescription tree_conflict = db.opReadTreeConflict(path);
        WCDbInfo info = db.readInfo(path, InfoField.status, InfoField.kind, InfoField.revision, InfoField.reposRelPath, InfoField.reposRootUrl, InfoField.changedRev, InfoField.changedDate,
                InfoField.changedAuthor, InfoField.changelist, InfoField.baseShadowed, InfoField.conflicted, InfoField.lock);

        SVNURL url = getNodeUrl(path);
        boolean file_external_p = isFileExternal(path);

        /*
         * File externals are switched files, but they are not shown as such. To
         * be switched it must have both an URL and a parent with an URL, at the
         * very least.
         */
        // boolean switched_p = !file_external_p ? isSwitched(path) : false;
        boolean switched_p = false;
        if (!file_external_p) {
            if (parentReposRootUrl != null && info.reposRootUrl != null && parentReposRootUrl.equals(info.reposRootUrl)) {
                String base = path.getName();

                if (info.reposRelPath == null) {
                    info.reposRelPath = new File(parentReposRelPath, base);
                    /*
                     * If _read_info() doesn't give us a repos_relpath, it means
                     * that it is implied by the parent, thus the path can not
                     * be switched.
                     */
                    switched_p = false;
                } else {
                    switched_p = new File(parentReposRelPath, base).equals(info.reposRelPath);
                }
            }
        }

        /*
         * Examine whether our directory metadata is present, and compensate if
         * it is missing.
         * 
         * There are a several kinds of obstruction that we detect here:
         * 
         * - versioned subdir is missing - the versioned subdir's admin area is
         * missing - the versioned subdir has been replaced with a file/symlink
         * 
         * Net result: the target is obstructed and the metadata is unavailable.
         * 
         * Note: wc_db can also detect a versioned file that has been replaced
         * with a versioned subdir (moved from somewhere). We don't look for
         * that right away because the file's metadata is still present, so we
         * can examine properties and conflicts and whatnot.
         * 
         * ### note that most obstruction concepts disappear in single-db mode
         */
        if (info.kind == WCDbKind.Dir) {
            if (info.status == WCDbStatus.Incomplete) {
                /* Highest precedence. */
                final_text_status = SVNStatusType.STATUS_INCOMPLETE;
            } else if (info.status == WCDbStatus.ObstructedDelete) {
                /* Deleted directories are never reported as missing. */
                if (pathKind == SVNNodeKind.NONE)
                    final_text_status = SVNStatusType.STATUS_DELETED;
                else
                    final_text_status = SVNStatusType.STATUS_OBSTRUCTED;
            } else if (info.status == WCDbStatus.Obstructed || info.status == WCDbStatus.ObstructedAdd) {
                /*
                 * A present or added directory should be on disk, so it is
                 * reported missing or obstructed.
                 */
                if (pathKind == SVNNodeKind.NONE)
                    final_text_status = SVNStatusType.STATUS_MISSING;
                else
                    final_text_status = SVNStatusType.STATUS_OBSTRUCTED;
            }
        }

        /*
         * If FINAL_TEXT_STATUS is still normal, after the above checks, then we
         * should proceed to refine the status.
         * 
         * If it was changed, then the subdir is incomplete or
         * missing/obstructed. It means that no further information is
         * available, and we should skip all this work.
         */
        SVNProperties pristineProperties = null;
        SVNProperties actualProperties = null;
        if (final_text_status == SVNStatusType.STATUS_NORMAL) {
            boolean has_props;
            boolean prop_modified_p = false;
            boolean text_modified_p = false;
            boolean wc_special;

            /* Implement predecence rules: */

            /*
             * 1. Set the two main variables to "discovered" values first (M,
             * C). Together, these two stati are of lowest precedence, and C has
             * precedence over M.
             */

            /* Does the entry have props? */
            pristineProperties = db.readPristineProperties(path);
            actualProperties = db.readProperties(path);
            has_props = ((pristineProperties != null && !pristineProperties.isEmpty()) || (actualProperties != null && !actualProperties.isEmpty()));
            if (has_props) {
                final_prop_status = SVNStatusType.STATUS_NORMAL;
                /*
                 * If the entry has a property file, see if it has local
                 * changes.
                 */
                prop_modified_p = isPropertiesDiff(pristineProperties, actualProperties);
            }

            /* Record actual property status */
            pristine_prop_status = prop_modified_p ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NORMAL;

            if (has_props) {
                wc_special = isSpecial(path);
            } else {
                wc_special = false;
            }

            /* If the entry is a file, check for textual modifications */
            if ((info.kind == WCDbKind.File) && (wc_special == pathSpecial)) {

                text_modified_p = isTextModified(path, false, true);
                /* Record actual text status */
                pristine_text_status = text_modified_p ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NORMAL;
            }

            if (text_modified_p)
                final_text_status = SVNStatusType.STATUS_MODIFIED;

            if (prop_modified_p)
                final_prop_status = SVNStatusType.STATUS_MODIFIED;

            if (entry.getPropRejectFile() != null || entry.getConflictOld() != null || entry.getConflictNew() != null || entry.getConflictWorking() != null) {
                boolean[] text_conflict_p = {
                    false
                };
                boolean[] prop_conflict_p = {
                    false
                };

                /*
                 * The entry says there was a conflict, but the user might have
                 * marked it as resolved by deleting the artifact files, so
                 * check for that.
                 */
                getIsConflicted(path, text_conflict_p, prop_conflict_p, null);

                if (text_conflict_p[0])
                    final_text_status = SVNStatusType.STATUS_CONFLICTED;
                if (prop_conflict_p[0])
                    final_prop_status = SVNStatusType.STATUS_CONFLICTED;
            }

            /*
             * 2. Possibly overwrite the text_status variable with "scheduled"
             * states from the entry (A, D, R). As a group, these states are of
             * medium precedence. They also override any C or M that may be in
             * the prop_status field at this point, although they do not
             * override a C text status.
             */

            /*
             * ### db_status, base_shadowed, and fetching base_status can ###
             * fully replace entry->schedule here.
             */

            if (SVNWCSchedule.ADD.name().equalsIgnoreCase(entry.getSchedule()) && final_text_status != SVNStatusType.STATUS_CONFLICTED) {
                final_text_status = SVNStatusType.STATUS_ADDED;
                final_prop_status = SVNStatusType.STATUS_NONE;
            }

            else if (SVNWCSchedule.REPLACE.name().equalsIgnoreCase(entry.getSchedule()) && final_text_status != SVNStatusType.STATUS_CONFLICTED) {
                final_text_status = SVNStatusType.STATUS_REPLACED;
                final_prop_status = SVNStatusType.STATUS_NONE;
            }

            else if (SVNWCSchedule.DELETE.name().equalsIgnoreCase(entry.getSchedule()) && final_text_status != SVNStatusType.STATUS_CONFLICTED) {
                final_text_status = SVNStatusType.STATUS_DELETED;
                final_prop_status = SVNStatusType.STATUS_NONE;
            }

            /*
             * 3. Highest precedence:
             * 
             * a. check to see if file or dir is just missing, or incomplete.
             * This overrides every possible stateexcept* deletion. (If
             * something is deleted or scheduled for it, we don't care if the
             * working file exists.)
             * 
             * b. check to see if the file or dir is present in the file system
             * as the same kind it was versioned as.
             * 
             * 4. Check for locked directory (only for directories).
             */

            if (entry.isIncomplete() && (final_text_status != SVNStatusType.STATUS_DELETED) && (final_text_status != SVNStatusType.STATUS_ADDED)) {
                final_text_status = SVNStatusType.STATUS_INCOMPLETE;
            } else if (pathKind == SVNNodeKind.NONE) {
                if (final_text_status != SVNStatusType.STATUS_DELETED)
                    final_text_status = SVNStatusType.STATUS_MISSING;
            }
            /*
             * ### We can do this db_kind to node_kind translation since the
             * cases where db_kind would have been unknown are treated as
             * unversioned paths and thus have already returned.
             */
            else if (pathKind != (info.kind == WCDbKind.Dir ? SVNNodeKind.DIR : SVNNodeKind.FILE)) {
                final_text_status = SVNStatusType.STATUS_OBSTRUCTED;
            }

            else if (wc_special != pathSpecial) {
                final_text_status = SVNStatusType.STATUS_OBSTRUCTED;
            }

            if (pathKind == SVNNodeKind.DIR && info.kind == WCDbKind.Dir) {
                locked_p = db.isWCLocked(path);
            }
        }

        /*
         * 5. Easy out: unless we're fetching -every- entry, don't bother to
         * allocate a struct for an uninteresting entry.
         */

        if (!getAll)
            if (((final_text_status == SVNStatusType.STATUS_NONE) || (final_text_status == SVNStatusType.STATUS_NORMAL))
                    && ((final_prop_status == SVNStatusType.STATUS_NONE) || (final_prop_status == SVNStatusType.STATUS_NORMAL)) && (!locked_p) && (!switched_p) && (!file_external_p)
                    && (info.lock == null) && (repositoryLock == null) && (info.changelist == null) && (tree_conflict == null)) {
                return null;
            }

        /* 6. Build and return a status structure. */

        SVNLock lock = null;
        if (info.lock != null) {
            final WCDbLock wcdbLock = info.lock;
            lock = new SVNLock(path.toString(), wcdbLock.token, wcdbLock.owner, wcdbLock.comment, wcdbLock.date, null);
        }

        SVNStatus status = new SVNStatus(url, path, info.kind.toNodeKind(), SVNRevision.create(info.revision), SVNRevision.create(info.changedRev), info.changedDate, info.changedAuthor,
                final_text_status, final_prop_status, SVNStatusType.STATUS_NONE, SVNStatusType.STATUS_NONE, locked_p, entry.isCopied(), switched_p, file_external_p, new File(entry.getConflictNew()),
                new File(entry.getConflictOld()), new File(entry.getConflictWorking()), new File(entry.getPropRejectFile()), entry.getCopyFromURL(), SVNRevision.create(entry.getCopyFromRevision()),
                repositoryLock, lock, actualProperties.asMap(), info.changelist, ISVNWCDb.WC_FORMAT_17, tree_conflict);
        status.setEntry(entry);

        return status;

    }

    private boolean isSpecial(File path) {
        final String property = getProperty(path, SVNProperty.SPECIAL);
        return property != null;
    }

    private boolean isPropertiesDiff(SVNProperties pristineProperties, SVNProperties actualProperties) {
        if (pristineProperties == null && actualProperties == null) {
            return false;
        }
        if (pristineProperties == null || actualProperties == null) {
            return true;
        }
        final SVNProperties diff = actualProperties.compareTo(pristineProperties);
        return diff != null && !diff.isEmpty();
    }

    private boolean isTextModified(File local_abspath, boolean force_comparison, boolean compare_textbases) throws SVNException {

        /* No matter which way you look at it, the file needs to exist. */
        if (!local_abspath.exists() || !local_abspath.isFile()) {
            /*
             * There is no entity, or, the entity is not a regular file or link.
             * So, it can't be modified.
             */
            return false;
        }

        if (!force_comparison) {

            /*
             * We're allowed to use a heuristic to determine whether files may
             * have changed. The heuristic has these steps:
             * 
             * 
             * 1. Compare the working file's size with the size cached in the
             * entries file 2. If they differ, do a full file compare 3. Compare
             * the working file's timestamp with the timestamp cached in the
             * entries file 4. If they differ, do a full file compare 5.
             * Otherwise, return indicating an unchanged file.
             * 
             * There are 2 problematic situations which may occur:
             * 
             * 1. The cached working size is missing --> In this case, we forget
             * we ever tried to compare and skip to the timestamp comparison.
             * This is because old working copies do not contain cached sizes
             * 
             * 2. The cached timestamp is missing --> In this case, we forget we
             * ever tried to compare and skip to full file comparison. This is
             * because the timestamp will be removed when the library updates a
             * locally changed file. (ie, this only happens when the file was
             * locally modified.)
             */

            boolean compare_them = false;

            /* Read the relevant info */
            WCDbInfo readInfo = null;
            try {
                readInfo = db.readInfo(local_abspath, InfoField.lastModTime, InfoField.translatedSize);
            } catch (SVNException e) {
                compare_them = true;
            }

            if (!compare_them) {
                /* Compare the sizes, if applicable */
                if (readInfo.translatedSize != ISVNWCDb.ENTRY_WORKING_SIZE_UNKNOWN && local_abspath.length() != readInfo.translatedSize) {
                    compare_them = true;
                }
            }

            if (!compare_them) {
                /*
                 * Compare the timestamps
                 * 
                 * Note: text_time == 0 means absent from entries, which also
                 * means the timestamps won't be equal, so there's no need to
                 * explicitly check the 'absent' value.
                 */
                if (readInfo.lastModTime != local_abspath.lastModified()) {
                    compare_them = true;
                }
            }

            if (!compare_them) {
                return false;
            }
        }

        // compare_them:
        /*
         * If there's no text-base file, we have to assume the working file is
         * modified. For example, a file scheduled for addition but not yet
         * committed.
         */
        /*
         * We used to stat for the working base here, but we just give
         * compare_and_verify a try; we'll check for errors afterwards
         */
        InputStream pristine_stream;
        try {
            pristine_stream = getPristineContents(local_abspath);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                return true;
            }
            throw e;
        }

        if (pristine_stream == null) {
            return true;
        }

        /* Check all bytes, and verify checksum if requested. */
        return compareAndVerify(local_abspath, pristine_stream, compare_textbases, force_comparison);
    }

    private InputStream getPristineContents(File localAbspath) throws SVNException {

        final WCDbInfo readInfo = db.readInfo(localAbspath, InfoField.status, InfoField.kind, InfoField.checksum);

        /* Sanity */
        if (readInfo.kind != WCDbKind.File) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "Can only get the pristine contents of files;" + "  '{0}' is not a file", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (readInfo.status == WCDbStatus.Added) {
            /*
             * For an added node, we return "no stream". Make sure this is not
             * copied-here or moved-here, in which case we return the copy/move
             * source's contents.
             */
            final WCDbAdditionInfo scanAddition = db.scanAddition(localAbspath, AdditionInfoField.status);

            if (scanAddition.status == WCDbStatus.Added) {
                /* Simply added. The pristine base does not exist. */
                return null;
            }
        } else if (readInfo.status == WCDbStatus.NotPresent) {
            /*
             * We know that the delete of this node has been committed. This
             * should be the same as if called on an unknown path.
             */
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Cannot get the pristine contents of '{0}' " + "because its delete is already committed", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (readInfo.status == WCDbStatus.Absent || readInfo.status == WCDbStatus.Excluded || readInfo.status == WCDbStatus.Incomplete) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Cannot get the pristine contents of '{0}' " + "because it has an unexpected status", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else {
            /*
             * We know that it is a file, so we can't hit the _obstructed stati.
             * Also, we should never see _base_deleted here.
             */
            SVNErrorManager.assertionFailure(readInfo.status != WCDbStatus.Obstructed && readInfo.status != WCDbStatus.ObstructedAdd && readInfo.status != WCDbStatus.ObstructedDelete
                    && readInfo.status != WCDbStatus.BaseDeleted, null, SVNLogType.WC);
        }

        if (readInfo.checksum == null) {
            return null;
        }

        return db.readPristine(localAbspath, readInfo.checksum);
    }

    /**
     * Return TRUE if (after translation) VERSIONED_FILE_ABSPATH differs from
     * PRISTINE_STREAM, else to FALSE if not. Also verify that PRISTINE_STREAM
     * matches the stored checksum for VERSIONED_FILE_ABSPATH, if
     * verify_checksum is TRUE. If checksum does not match, throw the error
     * SVN_ERR_WC_CORRUPT_TEXT_BASE.
     * <p>
     * If COMPARE_TEXTBASES is true, translate VERSIONED_FILE_ABSPATH's EOL
     * style and keywords to repository-normal form according to its properties,
     * and compare the result with PRISTINE_STREAM. If COMPARE_TEXTBASES is
     * false, translate PRISTINE_STREAM's EOL style and keywords to working-copy
     * form according to VERSIONED_FILE_ABSPATH's properties, and compare the
     * result with VERSIONED_FILE_ABSPATH.
     * <p>
     * PRISTINE_STREAM will be closed before a successful return.
     * 
     */
    private boolean compareAndVerify(File versioned_file_abspath, InputStream pristine_stream, boolean compare_textbases, boolean verify_checksum) throws SVNException {
        boolean same = false;

        assert (versioned_file_abspath != null && versioned_file_abspath.isAbsolute());

        final SVNEolStyleInfo eolStyle = getEolStyle(versioned_file_abspath);
        final Map keywords = getKeyWords(versioned_file_abspath, null);
        final boolean special = isSpecial(versioned_file_abspath);
        final boolean need_translation = isTranslationRequired(eolStyle.eolStyle, eolStyle.eolStr, keywords, special, true);

        if (verify_checksum || need_translation) {
            /* Reading files is necessary. */
            SVNChecksum checksum = null;
            InputStream v_stream = null; /* versioned_file */
            SVNChecksum node_checksum = null;

            try {
                if (verify_checksum) {
                    /*
                     * Need checksum verification, so read checksum from entries
                     * file and setup checksummed stream for base file.
                     */
                    final WCDbInfo nodeInfo = db.readInfo(versioned_file_abspath, InfoField.checksum);
                    /*
                     * SVN_EXPERIMENTAL_PRISTINE: node_checksum is originally
                     * MD-5 but will later be SHA-1. To allow for this, we
                     * calculate CHECKSUM as the same kind so that we can
                     * compare them.
                     */
                    if (nodeInfo.checksum != null) {
                        pristine_stream = new SVNChecksumInputStream(pristine_stream, nodeInfo.checksum.getKind().toString());
                    }
                }

                if (special) {
                    v_stream = readSpecialFile(versioned_file_abspath);
                } else {
                    v_stream = SVNFileUtil.openFileForReading(versioned_file_abspath);

                    if (compare_textbases && need_translation) {
                        if (eolStyle.eolStyle == SVNEolStyle.Native)
                            eolStyle.eolStr = SVNEolStyleInfo.NATIVE_EOL_STR;
                        else if (eolStyle.eolStyle != SVNEolStyle.Fixed && eolStyle.eolStyle != SVNEolStyle.None) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_UNKNOWN_EOL);
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }

                        /*
                         * Wrap file stream to detranslate into normal form,
                         * "repairing" the EOL style if it is inconsistent.
                         */
                        v_stream = SVNTranslator.getTranslatingInputStream(v_stream, null, eolStyle.eolStr, true, keywords, false);
                    } else if (need_translation) {
                        /*
                         * Wrap base stream to translate into working copy form,
                         * and arrange to throw an error if its EOL style is
                         * inconsistent.
                         */
                        pristine_stream = SVNTranslator.getTranslatingInputStream(pristine_stream, null, eolStyle.eolStr, false, keywords, true);
                    }
                }

                try {
                    same = isSameContents(pristine_stream, v_stream);
                } catch (IOException e) {
                    SVNTranslator.translationError(versioned_file_abspath, e);
                }

            } finally {
                SVNFileUtil.closeFile(pristine_stream);
                SVNFileUtil.closeFile(v_stream);
            }

            if (verify_checksum && node_checksum != null) {
                if (checksum != null && !isChecksumMatch(checksum, node_checksum)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch indicates corrupt text base for file: "
                            + "'{0}':\n   expected:  {1}\n     actual:  {2}\n", new Object[] {
                            versioned_file_abspath, node_checksum, checksum
                    });
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        } else {
            /* Translation would be a no-op, so compare the original file. */
            InputStream v_stream; /* versioned_file */

            v_stream = SVNFileUtil.openFileForReading(versioned_file_abspath);
            try {
                same = isSameContents(v_stream, pristine_stream);
            } catch (IOException e) {
                SVNTranslator.translationError(versioned_file_abspath, e);
            }

        }

        return (!same);

    }

    private boolean isChecksumMatch(SVNChecksum checksum1, SVNChecksum checksum2) {
        if (checksum1 == null || checksum2 == null)
            return true;
        if (checksum1.getKind() != checksum2.getKind())
            return false;
        return checksum1.getDigest() == null || checksum2.getDigest() == null || checksum1.getDigest() == checksum2.getDigest();
    }

    private boolean isSameContents(InputStream stream1, InputStream stream2) throws IOException {
        byte[] buffer1 = new byte[8192];
        byte[] buffer2 = new byte[8192];
        while (true) {
            int r1 = SVNFileUtil.readIntoBuffer(stream1, buffer1, 0, buffer1.length);
            int r2 = SVNFileUtil.readIntoBuffer(stream2, buffer2, 0, buffer2.length);
            r1 = r1 == -1 ? 0 : r1;
            r2 = r2 == -1 ? 0 : r2;
            if (r1 != r2) {
                return true;
            } else if (r1 == 0) {
                return false;
            }
            for (int i = 0; i < r1; i++) {
                if (buffer1[i] != buffer2[i]) {
                    return true;
                }
            }
        }
    }

    private InputStream readSpecialFile(File path) throws SVNException {
        /*
         * First determine what type of special file we are detranslating.
         */
        final SVNFileType filetype = SVNFileType.getType(path);
        if (SVNFileType.FILE == filetype) {
            /*
             * Nothing special to do here, just create stream from the original
             * file's contents.
             */
            return SVNFileUtil.openFileForReading(path, SVNLogType.WC);
        } else if (SVNFileType.SYMLINK == filetype) {
            /* Determine the destination of the link. */
            String linkPath = SVNFileUtil.getSymlinkName(path);
            if (linkPath == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot detranslate symbolic link ''{0}''; file does not exist or not a symbolic link", path);
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            String symlinkContents = "link " + linkPath;
            return new ByteArrayInputStream(symlinkContents.getBytes());
        } else {
            SVNErrorManager.assertionFailure(false, "ERR_MALFUNCTION", SVNLogType.WC);
        }
        return null;
    }

    private boolean isTranslationRequired(SVNEolStyle style, byte[] eol, Map keywords, boolean special, boolean force_eol_check) {
        return (special || keywords != null || (style != SVNEolStyle.None && force_eol_check)
        // || (style == SVNEolStyle.Native && strcmp(APR_EOL_STR,
        // SVN_SUBST_NATIVE_EOL_STR) != 0)
        || (style == SVNEolStyle.Fixed && !Arrays.equals(SVNEolStyleInfo.NATIVE_EOL_STR, eol)));

    }

    private SVNEolStyleInfo getEolStyle(File localAbsPath) {
        assert (isAbsolute(localAbsPath));

        /* Get the property value. */
        final String propVal = getProperty(localAbsPath, SVNProperty.EOL_STYLE);

        /* Convert it. */
        return SVNEolStyleInfo.fromValue(propVal);
    }

    private Map getKeyWords(File local_abspath, String force_list) throws SVNException {
        assert (isAbsolute(local_abspath));

        String list;

        /*
         * Choose a property list to parse: either the one that came into this
         * function, or the one attached to PATH.
         */
        if (force_list == null) {
            list = getProperty(local_abspath, SVNProperty.KEYWORDS);

            /* The easy answer. */
            if (list == null) {
                return null;
            }

        } else
            list = force_list;

        final SVNURL url = getNodeUrl(local_abspath);
        final WCDbInfo readInfo = db.readInfo(local_abspath, InfoField.changedRev, InfoField.changedDate, InfoField.changedAuthor);
        return SVNTranslator.computeKeywords(list, url.toString(), Long.toString(readInfo.changedRev), readInfo.changedDate.toString(), readInfo.changedAuthor, getOptions());
    }

    private boolean isFileExternal(File path) {
        final String serialized = db.getFileExternalTemp(path);
        return serialized != null;
    }

    private SVNURL getNodeUrl(File path) throws SVNException {

        final WCDbInfo readInfo = db.readInfo(path, InfoField.status, InfoField.reposRelPath, InfoField.reposRootUrl, InfoField.baseShadowed);

        if (readInfo.reposRelPath == null) {
            if (readInfo.status == WCDbStatus.Normal || readInfo.status == WCDbStatus.Incomplete
                    || (readInfo.baseShadowed && (readInfo.status == WCDbStatus.Deleted || readInfo.status == WCDbStatus.ObstructedDelete))) {
                final WCDbRepositoryInfo repos = db.scanBaseRepository(path, RepositoryInfoField.relPath, RepositoryInfoField.rootUrl);
                readInfo.reposRelPath = repos.relPath;
                readInfo.reposRootUrl = repos.rootUrl;
            } else if (readInfo.status == WCDbStatus.Added) {
                final WCDbAdditionInfo scanAddition = db.scanAddition(path, AdditionInfoField.reposRelPath, AdditionInfoField.reposRootUrl);
                readInfo.reposRelPath = scanAddition.reposRelPath;
                readInfo.reposRootUrl = scanAddition.reposRootUrl;
            } else if (readInfo.status == WCDbStatus.Absent || readInfo.status == WCDbStatus.Excluded || readInfo.status == WCDbStatus.NotPresent
                    || (!readInfo.baseShadowed && (readInfo.status == WCDbStatus.Deleted || readInfo.status == WCDbStatus.ObstructedDelete))) {
                File parent_abspath = path.getParentFile();
                readInfo.reposRelPath = new File(path.getName());
                readInfo.reposRootUrl = getNodeUrl(parent_abspath);
            } else {
                /* Status: obstructed, obstructed_add */
                return null;
            }
        }

        assert (readInfo.reposRootUrl != null && readInfo.reposRelPath != null);
        return SVNURL.parseURIDecoded(SVNPathUtil.append(readInfo.reposRootUrl.toDecodedString(), readInfo.reposRelPath.toString()));
    }

    private void getIsConflicted(File path, boolean[] textConflictP, boolean[] propConflictP, Object object) {
        // TODO
    }

    public boolean isAdminDirectory(String name) {
        return name != null && (SVNFileUtil.isWindows) ? SVNFileUtil.getAdminDirectoryName().equalsIgnoreCase(name) : SVNFileUtil.getAdminDirectoryName().equals(name);
    }

    public String getProperty(File path, String propertyName) {
        // TODO
        return null;
    }

    public SVNStatus assembleUnversioned(File nodeAbsPath, SVNNodeKind pathKind, boolean isIgnored) {
        return null;
    }

    public SVNEntry getEntry(File nodeAbsPath, boolean b, SVNNodeKind unknown, boolean c) throws SVNException {
        return null;
    }

}
