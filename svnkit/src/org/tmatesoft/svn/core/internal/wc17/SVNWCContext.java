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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNChecksumKind;
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
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDirDeletedInfo;
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
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
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

    private static final long INVALID_REVNUM = -1;

    public static boolean isAdminDirectory(String name) {
        return name != null && (SVNFileUtil.isWindows) ? SVNFileUtil.getAdminDirectoryName().equalsIgnoreCase(name) : SVNFileUtil.getAdminDirectoryName().equals(name);
    }

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

    public SVNStatus assembleUnversioned(File localAbspath, SVNNodeKind pathKind, boolean isIgnored) throws SVNException {

        /*
         * Find out whether the path is a tree conflict victim. This function
         * will set tree_conflict to NULL if the path is not a victim.
         */
        SVNTreeConflictDescription tree_conflict = db.opReadTreeConflict(localAbspath);

        SVNNodeKind kind = SVNNodeKind.UNKNOWN; /* not versioned */
        SVNStatusType text_status = SVNStatusType.STATUS_NONE;
        SVNStatusType prop_status = SVNStatusType.STATUS_NONE;
        SVNStatusType repos_text_status = SVNStatusType.STATUS_NONE;
        SVNStatusType repos_prop_status = SVNStatusType.STATUS_NONE;

        /*
         * If this path has no entry, but IS present on disk, it's unversioned.
         * If this file is being explicitly ignored (due to matching an
         * ignore-pattern), the text_status is set to svn_wc_status_ignored.
         * Otherwise the text_status is set to svn_wc_status_unversioned.
         */
        if (pathKind != SVNNodeKind.NONE) {
            if (isIgnored)
                text_status = SVNStatusType.STATUS_IGNORED;
            else
                text_status = SVNStatusType.STATUS_UNVERSIONED;
        } else if (tree_conflict != null) {
            /*
             * If this path has no entry, is NOT present on disk, and IS a tree
             * conflict victim, count it as missing.
             */
            text_status = SVNStatusType.STATUS_MISSING;
        }

        SVNRevision revision = SVNRevision.UNDEFINED;
        SVNRevision changed_rev = SVNRevision.UNDEFINED;

        /*
         * For the case of an incoming delete to a locally deleted path during
         * an update, we get a tree conflict.
         */

        SVNStatus status = new SVNStatus(null, localAbspath, kind, revision, changed_rev, null, null, text_status, prop_status, repos_text_status, repos_prop_status, false, false, false, false, null,
                null, null, null, null, SVNRevision.UNDEFINED, null, null, null, null, -1, tree_conflict);

        return status;
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
                final ConflictedInfo isConflicted = getIsConflicted(path, true, true, false);
                if (isConflicted.textConflicted)
                    final_text_status = SVNStatusType.STATUS_CONFLICTED;
                if (isConflicted.propConflicted)
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

    private boolean isSpecial(File path) throws SVNException {
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

    private boolean isTextModified(File localAbsPath, boolean forceComparison, boolean compareTextBases) throws SVNException {

        /* No matter which way you look at it, the file needs to exist. */
        if (!localAbsPath.exists() || !localAbsPath.isFile()) {
            /*
             * There is no entity, or, the entity is not a regular file or link.
             * So, it can't be modified.
             */
            return false;
        }

        if (!forceComparison) {

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

            boolean compareThem = false;

            /* Read the relevant info */
            WCDbInfo readInfo = null;
            try {
                readInfo = db.readInfo(localAbsPath, InfoField.lastModTime, InfoField.translatedSize);
            } catch (SVNException e) {
                compareThem = true;
            }

            if (!compareThem) {
                /* Compare the sizes, if applicable */
                if (readInfo.translatedSize != ISVNWCDb.ENTRY_WORKING_SIZE_UNKNOWN && localAbsPath.length() != readInfo.translatedSize) {
                    compareThem = true;
                }
            }

            if (!compareThem) {
                /*
                 * Compare the timestamps
                 * 
                 * Note: text_time == 0 means absent from entries, which also
                 * means the timestamps won't be equal, so there's no need to
                 * explicitly check the 'absent' value.
                 */
                if (readInfo.lastModTime != localAbsPath.lastModified()) {
                    compareThem = true;
                }
            }

            if (!compareThem) {
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
        InputStream pristineStream;
        try {
            pristineStream = getPristineContents(localAbsPath);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                return true;
            }
            throw e;
        }

        if (pristineStream == null) {
            return true;
        }

        /* Check all bytes, and verify checksum if requested. */
        return compareAndVerify(localAbsPath, pristineStream, compareTextBases, forceComparison);
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
    private boolean compareAndVerify(File versionedFileAbsPath, InputStream pristineStream, boolean compareTextBases, boolean verifyChecksum) throws SVNException {
        boolean same = false;

        assert (versionedFileAbsPath != null && versionedFileAbsPath.isAbsolute());

        final SVNEolStyleInfo eolStyle = getEolStyle(versionedFileAbsPath);
        final Map keywords = getKeyWords(versionedFileAbsPath, null);
        final boolean special = isSpecial(versionedFileAbsPath);
        final boolean needTranslation = isTranslationRequired(eolStyle.eolStyle, eolStyle.eolStr, keywords, special, true);

        if (verifyChecksum || needTranslation) {
            /* Reading files is necessary. */
            SVNChecksum checksum = null;
            InputStream vStream = null; /* versioned_file */
            SVNChecksum nodeChecksum = null;

            try {
                if (verifyChecksum) {
                    /*
                     * Need checksum verification, so read checksum from entries
                     * file and setup checksummed stream for base file.
                     */
                    final WCDbInfo nodeInfo = db.readInfo(versionedFileAbsPath, InfoField.checksum);
                    /*
                     * SVN_EXPERIMENTAL_PRISTINE: node_checksum is originally
                     * MD-5 but will later be SHA-1. To allow for this, we
                     * calculate CHECKSUM as the same kind so that we can
                     * compare them.
                     */
                    if (nodeInfo.checksum != null) {
                        pristineStream = new SVNChecksumInputStream(pristineStream, nodeInfo.checksum.getKind().toString());
                    }
                }

                if (special) {
                    vStream = readSpecialFile(versionedFileAbsPath);
                } else {
                    vStream = SVNFileUtil.openFileForReading(versionedFileAbsPath);

                    if (compareTextBases && needTranslation) {
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
                        vStream = SVNTranslator.getTranslatingInputStream(vStream, null, eolStyle.eolStr, true, keywords, false);
                    } else if (needTranslation) {
                        /*
                         * Wrap base stream to translate into working copy form,
                         * and arrange to throw an error if its EOL style is
                         * inconsistent.
                         */
                        pristineStream = SVNTranslator.getTranslatingInputStream(pristineStream, null, eolStyle.eolStr, false, keywords, true);
                    }
                }

                try {
                    same = isSameContents(pristineStream, vStream);
                } catch (IOException e) {
                    SVNTranslator.translationError(versionedFileAbsPath, e);
                }

            } finally {
                SVNFileUtil.closeFile(pristineStream);
                SVNFileUtil.closeFile(vStream);
            }

            if (verifyChecksum && nodeChecksum != null) {
                if (checksum != null && !isChecksumMatch(checksum, nodeChecksum)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch indicates corrupt text base for file: "
                            + "'{0}':\n   expected:  {1}\n     actual:  {2}\n", new Object[] {
                            versionedFileAbsPath, nodeChecksum, checksum
                    });
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        } else {
            /* Translation would be a no-op, so compare the original file. */
            InputStream vStream; /* versioned_file */

            vStream = SVNFileUtil.openFileForReading(versionedFileAbsPath);
            try {
                same = isSameContents(vStream, pristineStream);
            } catch (IOException e) {
                SVNTranslator.translationError(versionedFileAbsPath, e);
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

    private InputStream readSpecialFile(File localAbsPath) throws SVNException {
        /*
         * First determine what type of special file we are detranslating.
         */
        final SVNFileType filetype = SVNFileType.getType(localAbsPath);
        if (SVNFileType.FILE == filetype) {
            /*
             * Nothing special to do here, just create stream from the original
             * file's contents.
             */
            return SVNFileUtil.openFileForReading(localAbsPath, SVNLogType.WC);
        } else if (SVNFileType.SYMLINK == filetype) {
            /* Determine the destination of the link. */
            String linkPath = SVNFileUtil.getSymlinkName(localAbsPath);
            if (linkPath == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot detranslate symbolic link ''{0}''; file does not exist or not a symbolic link", localAbsPath);
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

    private SVNEolStyleInfo getEolStyle(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));

        /* Get the property value. */
        final String propVal = getProperty(localAbsPath, SVNProperty.EOL_STYLE);

        /* Convert it. */
        return SVNEolStyleInfo.fromValue(propVal);
    }

    private Map getKeyWords(File localAbsPath, String forceList) throws SVNException {
        assert (isAbsolute(localAbsPath));

        String list;
        /*
         * Choose a property list to parse: either the one that came into this
         * function, or the one attached to PATH.
         */
        if (forceList == null) {
            list = getProperty(localAbsPath, SVNProperty.KEYWORDS);

            /* The easy answer. */
            if (list == null) {
                return null;
            }

        } else
            list = forceList;

        final SVNURL url = getNodeUrl(localAbsPath);
        final WCDbInfo readInfo = db.readInfo(localAbsPath, InfoField.changedRev, InfoField.changedDate, InfoField.changedAuthor);
        return SVNTranslator.computeKeywords(list, url.toString(), Long.toString(readInfo.changedRev), readInfo.changedDate.toString(), readInfo.changedAuthor, getOptions());
    }

    private boolean isFileExternal(File path) throws SVNException {
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

    private static class ConflictedInfo {

        public boolean textConflicted;
        public boolean propConflicted;
        public boolean treeConflicted;
    }

    private ConflictedInfo getIsConflicted(File localAbsPath, boolean isTextNeed, boolean isPropNeed, boolean isTreeNeed) throws SVNException {
        final WCDbInfo readInfo = db.readInfo(localAbsPath, InfoField.kind, InfoField.conflicted);
        final ConflictedInfo info = new ConflictedInfo();
        if (!readInfo.conflicted) {
            return info;
        }
        final File dir_path = (readInfo.kind == WCDbKind.Dir) ? localAbsPath : localAbsPath.getParentFile();
        final List<SVNTreeConflictDescription> conflicts = db.readConflicts(localAbsPath);
        for (final SVNTreeConflictDescription cd : conflicts) {
            final SVNMergeFileSet cdf = cd.getMergeFiles();
            if (isTextNeed && cd.isTextConflict()) {
                /*
                 * Look for any text conflict, exercising only as much effort as
                 * necessary to obtain a definitive answer. This only applies to
                 * files, but we don't have to explicitly check that entry is a
                 * file, since these attributes would never be set on a
                 * directory anyway. A conflict file entry notation only counts
                 * if the conflict file still exists on disk.
                 */
                if (cdf.getBasePath() != null) {
                    final File path = new File(dir_path, cdf.getBasePath());
                    final SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path));
                    info.textConflicted = (kind == SVNNodeKind.FILE);
                    if (info.textConflicted)
                        continue;
                }
                if (cdf.getRepositoryPath() != null) {
                    final File path = new File(dir_path, cdf.getRepositoryPath());
                    final SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path));
                    info.textConflicted = (kind == SVNNodeKind.FILE);
                    if (info.textConflicted)
                        continue;
                }
                if (cdf.getLocalPath() != null) {
                    final File path = new File(dir_path, cdf.getLocalPath());
                    final SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path));
                    info.textConflicted = (kind == SVNNodeKind.FILE);
                }
            } else if (isPropNeed && cd.isPropertyConflict()) {
                if (cdf.getRepositoryPath() != null) {
                    final File path = new File(dir_path, cdf.getRepositoryPath());
                    final SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path));
                    info.propConflicted = (kind == SVNNodeKind.FILE);
                }
            } else if (isTreeNeed && cd.isTreeConflict()) {
                info.treeConflicted = true;
            }
        }
        return info;
    }

    public String getProperty(File localAbsPath, String name) throws SVNException {
        assert (isAbsolute(localAbsPath));
        assert (!SVNProperty.isEntryProperty(name));

        SVNProperties properties = null;

        final WCDbKind wcKind = db.readKind(localAbsPath, true);

        if (wcKind == WCDbKind.Unknown) {
            /*
             * The node is not present, or not really "here". Therefore, the
             * property is not present.
             */
            return null;
        }

        final boolean hidden = db.isNodeHidden(localAbsPath);
        if (hidden) {
            /*
             * The node is not present, or not really "here". Therefore, the
             * property is not present.
             */
            return null;
        }

        if (SVNProperty.isWorkingCopyProperty(name)) {
            /*
             * If no dav cache can be found, just set VALUE to NULL (for
             * compatibility with pre-WC-NG code).
             */
            try {
                properties = db.getBaseDavCache(localAbsPath);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    return null;
                }
                throw e;
            }
        } else {
            /* regular prop */
            properties = getActialProperties(localAbsPath);
        }

        if (properties != null) {
            return properties.getStringValue(name);
        }

        return null;

    }

    private SVNProperties getActialProperties(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));
        /*
         * ### perform some state checking. for example, locally-deleted nodes
         * ### should not have any ACTUAL props.
         */
        return db.readProperties(localAbsPath);
    }

    public SVNEntry getEntry(File localAbsPath, boolean allowUnversioned, SVNNodeKind kind, boolean needParentStub) throws SVNException {

        /* Can't ask for the parent stub if the node is a file. */
        assert (!needParentStub || kind != SVNNodeKind.FILE);

        final EntryAccessInfo entryAccessInfo = getEntryAccessInfo(localAbsPath, kind, needParentStub);

        /*
         * NOTE: if KIND is UNKNOWN and we decided to examine the *parent*
         * directory, then it is possible we moved out of the working copy. If
         * the on-disk node is a DIR, and we asked for a stub, then we obviously
         * can't provide that (parent has no info). If the on-disk node is a
         * FILE/NONE/UNKNOWN, then it is obstructing the real LOCAL_ABSPATH (or
         * it was never a versioned item). In all these cases, the
         * read_entries() will (properly) throw an error.
         * 
         * NOTE: if KIND is a DIR and we asked for the real data, but it is
         * obstructed on-disk by some other node kind (NONE, FILE, UNKNOWN),
         * then this will throw an error.
         */

        SVNEntry entry = null;

        try {
            EntryPair entryPair = readEntryPair(entryAccessInfo.dirAbspath, entryAccessInfo.entryName);
            entry = entryPair.entry;
        } catch (SVNException e) {

            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_MISSING || kind != SVNNodeKind.UNKNOWN || !"".equals(entryAccessInfo.entryName)) {
                throw e;
            }

            /*
             * The caller didn't know the node type, we saw a directory there,
             * we attempted to read IN that directory, and then wc_db reports
             * that it is NOT a working copy directory. It is possible that one
             * of two things has happened:
             * 
             * 1) a directory is obstructing a file in the parent 2) the
             * (versioned) directory's contents have been removed
             * 
             * Let's assume situation (1); if that is true, then we can just
             * return the newly-found data.
             * 
             * If we assumed (2), then a valid result still won't help us since
             * the caller asked for the actual contents, not the stub (which is
             * why we read *into* the directory). However, if we assume (1) and
             * get back a stub, then we have verified a missing, versioned
             * directory, and can return an error describing that.
             * 
             * Redo the fetch, but "insist" we are trying to find a file. This
             * will read from the parent directory of the "file".
             */

            try {
                entry = getEntry(localAbsPath, allowUnversioned, SVNNodeKind.FILE, false);
                return entry;
            } catch (SVNException e1) {
                if (e1.getErrorMessage().getErrorCode() != SVNErrorCode.NODE_UNEXPECTED_KIND) {
                    throw e;
                }

                /*
                 * We asked for a FILE, but the node found is a DIR. Thus, we
                 * are looking at a stub. Originally, we tried to read into the
                 * subdir because NEED_PARENT_STUB is FALSE. The stub we just
                 * read is not going to work for the caller, so inform them of
                 * the missing subdirectory.
                 */
                // assert (entry != null && entry.getKind() == SVNNodeKind.DIR);

                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Admin area of '{0}' is missing", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

        }

        if (entry == null) {
            if (allowUnversioned)
                return null;

            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "'{0}' is not under version control", localAbsPath);
            SVNErrorManager.error(err, SVNLogType.WC);

        }

        /* The caller had the wrong information. */
        if ((kind == SVNNodeKind.FILE && entry.getKind() != SVNNodeKind.FILE) || (kind == SVNNodeKind.DIR && entry.getKind() != SVNNodeKind.DIR)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "'{0}' is not of the right kind", localAbsPath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (kind == SVNNodeKind.UNKNOWN) {
            /* They wanted a (directory) stub, but this isn't a directory. */
            if (needParentStub && entry.getKind() != SVNNodeKind.DIR) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "'{0}' is not of the right kind", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            /* The actual (directory) information was wanted, but we got a stub. */
            if (!needParentStub && entry.getKind() == SVNNodeKind.DIR && !"".equals(entry.getName())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "'{0}' is not of the right kind", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }

        return entry;
    }

    private class EntryAccessInfo {

        public File dirAbspath;
        public String entryName;
    }

    private EntryAccessInfo getEntryAccessInfo(File localAbsPath, SVNNodeKind kind, boolean needParentStub) {
        boolean read_from_subdir = false;
        EntryAccessInfo info = new EntryAccessInfo();

        /* Can't ask for the parent stub if the node is a file. */
        assert (!needParentStub || kind != SVNNodeKind.FILE);

        /*
         * If the caller didn't know the node kind, then stat the path. Maybe it
         * is really there, and we can speed up the steps below.
         */
        if (kind == SVNNodeKind.UNKNOWN) {
            /* What's on disk? */
            SVNNodeKind on_disk = SVNFileType.getNodeKind(SVNFileType.getType(localAbsPath));

            if (on_disk != SVNNodeKind.DIR) {
                /*
                 * If this is *anything* besides a directory (FILE, NONE, or
                 * UNKNOWN), then we cannot treat it as a versioned directory
                 * containing entries to read. Leave READ_FROM_SUBDIR as FALSE,
                 * so that the parent will be examined.
                 * 
                 * For NONE and UNKNOWN, it may be that metadata exists for the
                 * node, even though on-disk is unhelpful.
                 * 
                 * If NEED_PARENT_STUB is TRUE, and the entry is not a
                 * DIRECTORY, then we'll error.
                 * 
                 * If NEED_PARENT_STUB if FALSE, and we successfully read a
                 * stub, then this on-disk node is obstructing the read.
                 */
            } else {
                /*
                 * We found a directory for this UNKNOWN node. Determine whether
                 * we need to read inside it.
                 */
                read_from_subdir = !needParentStub;
            }
        } else if (kind == SVNNodeKind.DIR && !needParentStub) {
            read_from_subdir = true;
        }

        if (read_from_subdir) {
            /*
             * KIND must be a DIR or UNKNOWN (and we found a subdir). We want
             * the "real" data, so treat LOCAL_ABSPATH as a versioned directory.
             */
            info.dirAbspath = localAbsPath;
            info.entryName = "";
        } else {
            /*
             * FILE node needs to read the parent directory. Or a DIR node needs
             * to read from the parent to get at the stub entry. Or this is an
             * UNKNOWN node, and we need to examine the parent.
             */
            info.dirAbspath = SVNFileUtil.getParentFile(localAbsPath);
            info.entryName = SVNFileUtil.getBasePath(localAbsPath);
        }
        return info;
    }

    private static class EntryPair {

        public SVNEntry parentEntry;
        public SVNEntry entry;
    }

    private EntryPair readEntryPair(File dirAbspath, String name) throws SVNException {

        EntryPair pair = new EntryPair();

        pair.parentEntry = readOneEntry(dirAbspath, "" /* name */, null /* parent_entry */);

        /*
         * If we need the entry for "this dir", then return the parent_entry in
         * both outputs. Otherwise, read the child node.
         */
        if ("".equals(name)) {
            /*
             * If the retrieved node is a FILE, then we have a problem. We asked
             * for a directory. This implies there is an obstructing,
             * unversioned directory where a FILE should be. We navigated from
             * the obstructing subdir up to the parent dir, then returned the
             * FILE found there.
             * 
             * Let's return WC_MISSING cuz the caller thought we had a dir, but
             * that (versioned subdir) isn't there.
             */
            if (pair.parentEntry.getKind() == SVNNodeKind.FILE) {
                pair.parentEntry = null;
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_MISSING, "'{0}' is not a versioned working copy", dirAbspath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            pair.entry = pair.parentEntry;
        } else {
            /* Default to not finding the child. */
            pair.entry = null;

            /*
             * Determine whether the parent KNOWS about this child. If it does
             * not, then we should not attempt to look for it.
             * 
             * For example: the parent doesn't "know" about the child, but the
             * versioned directory *does* exist on disk. We don't want to look
             * into that subdir.
             */
            for (File child : db.readChildren(dirAbspath)) {
                if (name.equals(child)) {
                    try {
                        pair.entry = readOneEntry(dirAbspath, name, pair.parentEntry);
                        /* Found it. No need to keep searching. */
                        break;
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                            throw e;
                        }
                        /*
                         * No problem. Clear the error and leave the default
                         * value of "missing".
                         */
                    }
                }
            }
            /*
             * if the loop ends without finding a child, then we have the
             * default ENTRY value of NULL.
             */
        }

        return pair;
    }

    private SVNEntry readOneEntry(File dirAbsPath, String name, SVNEntry parentEntry) throws SVNException {

        final File entry_abspath = (name != null && !"".equals(name)) ? new File(dirAbsPath, name) : dirAbsPath;

        final WCDbInfo info = db.readInfo(entry_abspath, InfoField.values());

        final SVNEntry17 entry = new SVNEntry17(entry_abspath);
        entry.setName(name);
        entry.setRevision(info.revision);
        entry.setRepositoryRootURL(info.reposRootUrl);
        entry.setUUID(info.reposUuid);
        entry.setCommittedRevision(info.changedRev);
        entry.setCommittedDate(info.changedDate != null ? info.changedDate.toString() : null);
        entry.setAuthor(info.changedAuthor);
        entry.setTextTime(Long.toString(info.lastModTime));
        entry.setDepth(info.depth);
        entry.setChangelistName(info.changelist);
        entry.setCopyFromRevision(info.originalRevision);

        if (entry.isThisDir()) {
            /* get the tree conflict data. */
            Map<File, SVNTreeConflictDescription> tree_conflicts = null;

            final List<File> conflict_victims = db.readConflictVictims(dirAbsPath);

            for (File child_name : conflict_victims) {

                File child_abspath = new File(dirAbsPath, child_name.toString());

                final List<SVNTreeConflictDescription> child_conflicts = db.readConflicts(child_abspath);

                for (SVNTreeConflictDescription conflict : child_conflicts) {
                    if (conflict.isTreeConflict()) {
                        if (tree_conflicts == null) {
                            tree_conflicts = new HashMap<File, SVNTreeConflictDescription>();
                        }
                        tree_conflicts.put(child_name, conflict);
                    }
                }
            }

            if (tree_conflicts != null) {
                entry.setTreeConflicts(tree_conflicts);
            }
        }

        if (info.status == WCDbStatus.Normal || info.status == WCDbStatus.Incomplete) {
            boolean have_row = false;

            /*
             * Ugh. During a checkout, it is possible that we are constructing a
             * subdirectory "over" a not-present directory. The read_info() will
             * return information out of the wc.db in the subdir. We need to
             * detect this situation and create a DELETED entry instead.
             */
            if (info.kind == WCDbKind.Dir) {
                // TODO
                /*
                 * svn_sqlite__db_t *sdb; svn_sqlite__stmt_t *stmt;
                 * 
                 * SVN_ERR(svn_wc__db_temp_borrow_sdb( &sdb, db, dir_abspath,
                 * svn_wc__db_openmode_readonly, scratch_pool));
                 * 
                 * SVN_ERR(svn_sqlite__get_statement(&stmt, sdb,
                 * STMT_SELECT_NOT_PRESENT)); SVN_ERR(svn_sqlite__bindf(stmt,
                 * "is", wc_id, entry->name));
                 * SVN_ERR(svn_sqlite__step(&have_row, stmt));
                 * SVN_ERR(svn_sqlite__reset(stmt));
                 */
            }

            if (have_row) {
                /*
                 * Just like a normal "not-present" node: schedule=normal and
                 * DELETED.
                 */
                entry.setSchedule(null);
                entry.setDeleted(true);
            } else {
                /* Plain old BASE node. */
                entry.setSchedule(null);

                /* Grab inherited repository information, if necessary. */
                if (info.reposRelPath == null) {
                    final WCDbRepositoryInfo baseRepos = db.scanBaseRepository(entry_abspath, RepositoryInfoField.values());
                    info.reposRelPath = baseRepos.relPath;
                    entry.setRepositoryRootURL(baseRepos.rootUrl);
                    entry.setUUID(baseRepos.uuid);
                }

                entry.setIncomplete(info.status == WCDbStatus.Incomplete);
            }
        } else if (info.status == WCDbStatus.Deleted || info.status == WCDbStatus.ObstructedDelete) {
            /* ### we don't have to worry about moves, so this is a delete. */
            entry.scheduleForDeletion();

            /* ### keep_local ... ugh. hacky. */
            /*
             * We only read keep_local in the directory itself, because we can't
             * rely on the actual record being available in the parent stub when
             * the directory is recorded as deleted in the directory itself.
             * (This last value is the status that brought us in this if block).
             * 
             * This is safe because we will only write this flag in the
             * directory itself (see mark_deleted() in adm_ops.c), and also
             * because we will never use keep_local in the final version of
             * WC-NG. With a central db and central pristine store we can remove
             * working copy directories directly. So any left over directories
             * after the delete operation are always kept locally.
             */
            if ("".equals(entry.getName())) {
                entry.setKeepLocal(db.determineKeepLocalTemp(entry_abspath));
            }
        } else if (info.status == WCDbStatus.Added || info.status == WCDbStatus.ObstructedAdd) {
            WCDbStatus work_status;
            File op_root_abspath = null;
            File scanned_original_relpath;
            long original_revision = INVALID_REVNUM;

            /* For child nodes, pick up the parent's revision. */
            if (!"".equals(entry.getName())) {
                assert (parentEntry != null);
                assert (!SVNRevision.isValidRevisionNumber(entry.getRevision()));

                entry.setRevision(parentEntry.getRevision());
            }

            if (info.baseShadowed) {
                WCDbStatus base_status;

                /*
                 * ENTRY->REVISION is overloaded. When a node is schedule-add or
                 * -replace, then REVISION refers to the BASE node's revision
                 * that is being overwritten. We need to fetch it now.
                 */

                WCDbBaseInfo baseInfo = db.getBaseInfo(entry_abspath, BaseInfoField.status, BaseInfoField.revision);
                base_status = baseInfo.status;
                entry.setRevision(baseInfo.revision);

                if (base_status == WCDbStatus.NotPresent) {
                    /* The underlying node is DELETED in this revision. */
                    entry.setDeleted(true);

                    /* This is an add since there isn't a node to replace. */
                    entry.scheduleForAddition();
                } else {
                    entry.scheduleForReplacement();
                }
            } else {
                /*
                 * If we are reading child directories, then we need to
                 * correctly populate the DELETED flag. WC_DB normally wants to
                 * provide all of a directory's metadata from its own area. But
                 * this information is stored only in the parent directory, so
                 * we need to call a custom API to fetch this value.
                 * 
                 * ### we should start generating BASE_NODE rows for THIS_DIR
                 * ### in the subdir. future step because it is harder.
                 */
                if (info.kind == WCDbKind.Dir && !"".equals(entry.getName())) {
                    WCDbDirDeletedInfo deletedInfo = db.isDirDeletedTem(entry_abspath);
                    entry.setDeleted(deletedInfo.notPresent);
                    entry.setRevision(deletedInfo.baseRevision);
                }
                if (entry.isDeleted()) {
                    /*
                     * There was a DELETED marker in the parent, meaning that we
                     * truly are shadowing a base node. It isn't called a
                     * 'replace' though (the BASE is pretending not to exist).
                     */
                    entry.scheduleForAddition();
                } else {
                    /*
                     * There was NOT a 'not-present' BASE_NODE in the parent
                     * directory. And there is no BASE_NODE in this directory.
                     * Therefore, we are looking at some kind of add/copy rather
                     * than a replace.
                     */

                    /* ### if this looks like a plain old add, then rev=0. */
                    if (!SVNRevision.isValidRevisionNumber(entry.getCopyFromRevision()) && !SVNRevision.isValidRevisionNumber(entry.getCommittedRevision())) {
                        entry.setRevision(0);
                    }

                    if (info.status == WCDbStatus.ObstructedAdd) {
                        entry.setRevision(INVALID_REVNUM);
                    }

                    /*
                     * ### when we're reading a directory that is not present,
                     * ### then it must be "normal" rather than "add".
                     */
                    if ("".equals(entry.getName()) && info.status == WCDbStatus.ObstructedAdd) {
                        entry.unschedule();
                    } else {
                        entry.scheduleForAddition();
                    }
                }
            }

            /*
             * If we don't have "real" data from the entry (obstruction), then
             * we cannot begin a scan for data. The original node may have
             * important data. Set up stuff to kill that idea off, and finish up
             * this entry.
             */
            if (info.status == WCDbStatus.ObstructedAdd) {
                entry.setCommittedRevision(INVALID_REVNUM);
                work_status = WCDbStatus.Normal;
                scanned_original_relpath = null;
            } else {
                final WCDbAdditionInfo additionInfo = db.scanAddition(entry_abspath, AdditionInfoField.status, AdditionInfoField.opRootAbsPath, AdditionInfoField.reposRelPath,
                        AdditionInfoField.reposRootUrl, AdditionInfoField.reposUuid, AdditionInfoField.originalReposRelPath, AdditionInfoField.originalRevision);
                work_status = additionInfo.status;
                op_root_abspath = additionInfo.opRootAbsPath;
                info.reposRelPath = additionInfo.reposRelPath;
                entry.setRepositoryRootURL(additionInfo.reposRootUrl);
                entry.setUUID(additionInfo.reposUuid);
                scanned_original_relpath = additionInfo.originalReposRelPath;
                original_revision = additionInfo.originalRevision;

                /*
                 * In wc.db we want to keep the valid revision of the
                 * not-present BASE_REV, but when we used entries we set the
                 * revision to 0 when adding a new node over a not present base
                 * node.
                 */
                if (work_status == WCDbStatus.Added && entry.isDeleted()) {
                    entry.setRevision(0);
                }
            }

            if (!SVNRevision.isValidRevisionNumber(entry.getCommittedRevision()) && scanned_original_relpath == null) {
                /*
                 * There is NOT a last-changed revision (last-changed date and
                 * author may be unknown, but we can always check the rev). The
                 * absence of a revision implies this node was added WITHOUT any
                 * history. Avoid the COPIED checks in the else block.
                 */
                /*
                 * ### scan_addition may need to be updated to avoid returning
                 * ### status_copied in this case.
                 */
            } else if (work_status == WCDbStatus.Copied) {
                entry.setCopied(true);

                /*
                 * If this is a child of a copied subtree, then it should be
                 * schedule_normal.
                 */
                if (info.originalReposRelpath == null) {
                    /* ### what if there is a BASE node under there? */
                    entry.unschedule();
                }

                /*
                 * Copied nodes need to mirror their copyfrom_rev, if they don't
                 * have a revision of their own already.
                 */
                if (!SVNRevision.isValidRevisionNumber(entry.getRevision()) || entry.getRevision() == 0 /* added */)
                    entry.setRevision(original_revision);
            }

            /* Does this node have copyfrom_* information? */
            if (scanned_original_relpath != null) {
                boolean is_copied_child;
                boolean is_mixed_rev = false;

                assert (work_status == WCDbStatus.Copied);

                /*
                 * If this node inherits copyfrom information from an ancestor
                 * node, then it must be a copied child.
                 */
                is_copied_child = (info.originalReposRelpath == null);

                /*
                 * If this node has copyfrom information on it, then it may be
                 * an actual copy-root, or it could be participating in a
                 * mixed-revision copied tree. So if we don't already know this
                 * is a copied child, then we need to look for this
                 * mixed-revision situation.
                 */
                if (!is_copied_child) {
                    File parent_abspath;
                    File parent_repos_relpath;
                    SVNURL parent_root_url;

                    /*
                     * When we insert entries into the database, we will
                     * construct additional copyfrom records for mixed-revision
                     * copies. The old entries would simply record the different
                     * revision in the entry->revision field. That is not
                     * available within wc-ng, so additional copies are made
                     * (see the logic inside write_entry()). However, when
                     * reading these back *out* of the database, the additional
                     * copies look like new "Added" nodes rather than a simple
                     * mixed-rev working copy.
                     * 
                     * That would be a behavior change if we did not compensate.
                     * If there is copyfrom information for this node, then the
                     * code below looks at the parent to detect if it *also* has
                     * copyfrom information, and if the copyfrom_url would align
                     * properly. If it *does*, then we omit storing copyfrom_url
                     * and copyfrom_rev (ie. inherit the copyfrom info like a
                     * normal child), and update entry->revision with the
                     * copyfrom_rev in order to (re)create the mixed-rev copied
                     * subtree that was originally presented for storage.
                     */

                    /*
                     * Get the copyfrom information from our parent.
                     * 
                     * Note that the parent could be added/copied/moved-here.
                     * There is no way for it to be deleted/moved-away and have
                     * *this* node appear as copied.
                     */
                    parent_abspath = SVNFileUtil.getParentFile(entry_abspath);

                    try {

                        final WCDbAdditionInfo additionInfo = db.scanAddition(parent_abspath, AdditionInfoField.opRootAbsPath, AdditionInfoField.reposRelPath, AdditionInfoField.reposRootUrl);

                        op_root_abspath = additionInfo.opRootAbsPath;
                        parent_repos_relpath = additionInfo.originalReposRelPath;
                        parent_root_url = additionInfo.originalRootUrl;

                        if (parent_root_url != null && info.originalRootUrl.equals(parent_root_url)) {
                            String relpath_to_entry = SVNPathUtil.getRelativePath(op_root_abspath.toString(), entry_abspath.toString());
                            String entry_repos_relpath = SVNPathUtil.append(parent_repos_relpath.toString(), relpath_to_entry);

                            /*
                             * The copyfrom repos roots matched.
                             * 
                             * Now we look to see if the copyfrom path of the
                             * parent would align with our own path. If so, then
                             * it means this copyfrom was spontaneously created
                             * and inserted for mixed-rev purposes and can be
                             * eliminated without changing the semantics of a
                             * mixed-rev copied subtree.
                             * 
                             * See notes/api-errata/wc003.txt for some
                             * additional detail, and potential issues.
                             */
                            if (entry_repos_relpath.equals(info.originalReposRelpath.toString())) {
                                is_copied_child = true;
                                is_mixed_rev = true;
                            }
                        }

                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                            throw e;
                        }
                    }

                }

                if (is_copied_child) {
                    /*
                     * We won't be settig the copyfrom_url, yet need to clear
                     * out the copyfrom_rev. Thus, this node becomes a child of
                     * a copied subtree (rather than its own root).
                     */
                    entry.setCopyFromRevision(INVALID_REVNUM);

                    /*
                     * Children in a copied subtree are schedule normal since we
                     * don't plan to actually *do* anything with them. Their
                     * operation is implied by ancestors.
                     */
                    entry.unschedule();

                    /*
                     * And *finally* we turn this entry into the mixed revision
                     * node that it was intended to be. This node's revision is
                     * taken from the copyfrom record that we spontaneously
                     * constructed.
                     */
                    if (is_mixed_rev)
                        entry.setRevision(original_revision);
                } else if (info.originalReposRelpath != null) {
                    entry.setCopyFromURL(SVNPathUtil.append(info.originalRootUrl.toString(), info.originalReposRelpath.toString()));
                } else {
                    /*
                     * NOTE: if original_repos_relpath == NULL, then the second
                     * call to scan_addition() will not have occurred. Thus,
                     * this use of OP_ROOT_ABSPATH still contains the original
                     * value where we fetched a value for SCANNED_REPOS_RELPATH.
                     */
                    String relpath_to_entry = SVNPathUtil.getRelativePath(op_root_abspath.toString(), entry_abspath.toString());
                    String entry_repos_relpath = SVNPathUtil.append(scanned_original_relpath.toString(), relpath_to_entry);
                    entry.setCopyFromURL(SVNPathUtil.append(info.originalRootUrl.toString(), entry_repos_relpath));
                }
            }
        } else if (info.status == WCDbStatus.NotPresent) {
            /*
             * ### buh. 'deleted' nodes are actually supposed to be ### schedule
             * "normal" since we aren't going to actually *do* ### anything to
             * this node at commit time.
             */
            entry.unschedule();
            entry.setDeleted(true);
        } else if (info.status == WCDbStatus.Obstructed) {
            /*
             * ### set some values that should (hopefully) let this directory
             * ### be usable.
             */
            entry.setRevision(INVALID_REVNUM);
        } else if (info.status == WCDbStatus.Absent) {
            entry.setAbsent(true);
        } else if (info.status == WCDbStatus.Excluded) {
            entry.unschedule();
            entry.setDepth(SVNDepth.EXCLUDE);
        } else {
            /* ### we should have handled all possible status values. */
            SVNErrorManager.assertionFailure(false, "MALFUNCTION", SVNLogType.WC);
        }

        /*
         * ### higher levels want repos information about deleted nodes, even
         * ### tho they are not "part of" a repository any more.
         */
        if (entry.isScheduledForDeletion()) {
            final DeletedBaseInfo deletedBaseInfo = getBaseInfoForDeleted(entry, entry_abspath, parentEntry);
            info.kind = deletedBaseInfo.kind;
            info.reposRelPath = deletedBaseInfo.reposRelPath;
            info.checksum = deletedBaseInfo.checksum;
        }

        /* ### default to the infinite depth if we don't know it. */
        if (entry.getDepth() == SVNDepth.UNKNOWN) {
            entry.setDepth(SVNDepth.INFINITY);
        }

        if (info.kind == WCDbKind.Dir) {
            entry.setKind(SVNNodeKind.DIR);
        } else if (info.kind == WCDbKind.File) {
            entry.setKind(SVNNodeKind.FILE);
        } else if (info.kind == WCDbKind.Symlink) {
            entry.setKind(SVNNodeKind.FILE); /* ### no symlink kind */
        } else {
            entry.setKind(SVNNodeKind.UNKNOWN);
        }

        /*
         * We should always have a REPOS_RELPATH, except for: - deleted nodes -
         * certain obstructed nodes - not-present nodes - absent nodes -
         * excluded nodes
         * 
         * ### the last three should probably have an "implied" REPOS_RELPATH
         */
        assert (info.reposRelPath != null || entry.isScheduledForDeletion() || info.status == WCDbStatus.Obstructed || info.status == WCDbStatus.ObstructedAdd
                || info.status == WCDbStatus.ObstructedDelete || info.status == WCDbStatus.NotPresent || info.status == WCDbStatus.Absent || info.status == WCDbStatus.Excluded);
        if (info.reposRelPath != null)
            entry.setURL(SVNPathUtil.append(entry.getRepositoryRoot(), info.reposRelPath.toString()));

        if (info.checksum != null) {
            /*
             * SVN_EXPERIMENTAL_PRISTINE: If we got a SHA-1, get the
             * corresponding MD-5.
             */
            if (info.checksum.getKind() != SVNChecksumKind.MD5) {
                info.checksum = db.getPristineMD5(entry_abspath, info.checksum);
            }

            assert (info.checksum.getKind() == SVNChecksumKind.MD5);
            entry.setChecksum(info.checksum.toString());
        }

        if (info.conflicted) {

            final List<SVNTreeConflictDescription> conflicts = db.readConflicts(entry_abspath);

            for (SVNTreeConflictDescription cd : conflicts) {

                final SVNMergeFileSet cdf = cd.getMergeFiles();
                if (cd.isTextConflict()) {
                    entry.setConflictOld(cdf.getBasePath());
                    entry.setConflictNew(cdf.getRepositoryPath());
                    entry.setConflictWorking(cdf.getLocalPath());
                } else if (cd.isPropertyConflict()) {
                    entry.setPropRejectFile(cdf.getRepositoryPath());
                }
            }
        }

        if (info.lock != null) {
            entry.setLockToken(info.lock.token);
            entry.setLockOwner(info.lock.owner);
            entry.setLockComment(info.lock.comment);
            entry.setLockCreationDate(info.lock.date != null ? info.lock.date.toString() : null);
        }

        /*
         * Let's check for a file external. ### right now this is ugly, since we
         * have no good way querying ### for a file external OR retrieving
         * properties. ugh.
         */
        if (entry.getKind() == SVNNodeKind.FILE)
            checkFileExternal(entry, entry_abspath);

        entry.setWorkingSize(info.translatedSize);

        return entry;

    }

    private void checkFileExternal(SVNEntry17 entry, File localAbsPath) throws SVNException {
        final String serialized = db.getFileExternalTemp(localAbsPath);
        if (serialized != null) {
            final UnserializedFileExternalInfo info = unserializeFileExternal(serialized);
            entry.setExternalFilePath(info.path);
            entry.setExternalFilePegRevision(info.pegRevision);
            entry.setExternalFileRevision(info.revision);
        }
    }

    private UnserializedFileExternalInfo unserializeFileExternal(String str) throws SVNException {
        final UnserializedFileExternalInfo info = new UnserializedFileExternalInfo();
        if (str != null) {
            StringBuffer buffer = new StringBuffer(str);
            info.pegRevision = SVNAdminUtil.parseRevision(buffer);
            info.revision = SVNAdminUtil.parseRevision(buffer);
            info.path = buffer.toString();
        }
        return info;
    }

    private class UnserializedFileExternalInfo {

        public String path = null;
        public SVNRevision pegRevision = SVNRevision.UNDEFINED;
        public SVNRevision revision = SVNRevision.UNDEFINED;
    }

    private class DeletedBaseInfo {

        public WCDbKind kind;
        public File reposRelPath;
        public SVNChecksum checksum;
    }

    private DeletedBaseInfo getBaseInfoForDeleted(SVNEntry17 entry, File entryAbsPath, SVNEntry parentEntry) throws SVNException {

        DeletedBaseInfo resInfo = new DeletedBaseInfo();

        SVNException err = null;

        try {

            /* Get the information from the underlying BASE node. */

            final WCDbBaseInfo baseInfo = db.getBaseInfo(entryAbsPath, BaseInfoField.kind, BaseInfoField.revision, BaseInfoField.changedRev, BaseInfoField.changedDate, BaseInfoField.changedAuthor,
                    BaseInfoField.lastModTime, BaseInfoField.depth, BaseInfoField.checksum, BaseInfoField.translatedSize);

            resInfo.kind = baseInfo.kind;
            resInfo.checksum = baseInfo.checksum;
            entry.setRevision(baseInfo.revision);
            entry.setCommittedRevision(baseInfo.changedRev);
            entry.setCommittedDate(baseInfo.changedDate == null ? null : baseInfo.changedDate.toString());
            entry.setAuthor(baseInfo.changedAuthor);
            entry.setTextTime(baseInfo.lastModTime == null ? null : baseInfo.lastModTime.toString());
            entry.setDepth(baseInfo.depth);
            entry.setWorkingSize(baseInfo.translatedSize);

        } catch (SVNException e) {
            err = e;
        }

        /*
         * SVN_EXPERIMENTAL_PRISTINE:checksum is originally MD-5 but will later
         * be SHA-1. That's OK here - we are just returning what is stored.
         */

        if (err != null) {
            if (err.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw err;
            }

            /*
             * No base node? This is a deleted child of a copy/move-here, so we
             * need to scan up the WORKING tree to find the root of the
             * deletion. Then examine its parent to discover its future location
             * in the repository.
             */

            final WCDbDeletionInfo deletionInfo = db.scanDeletion(entryAbsPath, DeletionInfoField.workDelAbsPath);

            assert (deletionInfo.workDelAbsPath != null);
            final File parent_abspath = SVNFileUtil.getParentFile(deletionInfo.workDelAbsPath);

            /*
             * During post-commit the parent that was previously added may have
             * been moved from the WORKING tree to the BASE tree.
             */

            final WCDbInfo parentInfo = db.readInfo(parent_abspath, InfoField.status, InfoField.reposRelPath, InfoField.reposRootUrl, InfoField.reposUuid);
            File parent_repos_relpath = parentInfo.reposRelPath;
            entry.setRepositoryRootURL(parentInfo.reposRootUrl);
            entry.setUUID(parentInfo.reposUuid);

            if (parentInfo.status == WCDbStatus.Added || parentInfo.status == WCDbStatus.ObstructedAdd) {
                final WCDbAdditionInfo additionInfo = db.scanAddition(parent_abspath, AdditionInfoField.reposRelPath, AdditionInfoField.reposRootUrl, AdditionInfoField.reposUuid);
                parent_repos_relpath = additionInfo.reposRelPath;
                entry.setRepositoryRootURL(additionInfo.reposRootUrl);
                entry.setUUID(additionInfo.reposUuid);
            }

            /* Now glue it all together */
            resInfo.reposRelPath = new File(parent_repos_relpath, SVNPathUtil.getRelativePath(parent_abspath.toString(), entryAbsPath.toString()));
        } else {
            final WCDbRepositoryInfo baseReposInfo = db.scanBaseRepository(entryAbsPath, RepositoryInfoField.values());
            resInfo.reposRelPath = baseReposInfo.relPath;
            entry.setRepositoryRootURL(baseReposInfo.rootUrl);
            entry.setUUID(baseReposInfo.uuid);
        }

        /* Do some extra work for the child nodes. */
        if (parentEntry != null) {
            /*
             * For child nodes without a revision, pick up the parent's
             * revision.
             */
            if (!SVNRevision.isValidRevisionNumber(entry.getRevision()))
                entry.setRevision(parentEntry.getRevision());
        }

        /*
         * For deleted nodes, our COPIED flag has a rather complex meaning.
         * 
         * In general, COPIED means "an operation on an ancestor took care of
         * me." This typically refers to a copy of an ancestor (and this node
         * just came along for the ride). However, in certain situations the
         * COPIED flag is set for deleted nodes.
         * 
         * First off, COPIED will *never* be set for nodes/subtrees that are
         * simply deleted. The deleted node/subtree *must* be under an ancestor
         * that has been copied. Plain additions do not count; only copies
         * (add-with-history).
         * 
         * The basic algorithm to determine whether we live within a copied
         * subtree is as follows:
         * 
         * 1) find the root of the deletion operation that affected us (we may
         * be that root, or an ancestor was deleted and took us with it)
         * 
         * 2) look at the root's *parent* and determine whether that was a copy
         * or a simple add.
         * 
         * It would appear that we would be done at this point. Once we
         * determine that the parent was copied, then we could just set the
         * COPIED flag.
         * 
         * Not so fast. Back to the general concept of "an ancestor operation
         * took care of me." Further consider two possibilities:
         * 
         * 1) this node is scheduled for deletion from the copied subtree, so at
         * commit time, we copy then delete
         * 
         * 2) this node is scheduled for deletion because a subtree was deleted
         * and then a copied subtree was added (causing a replacement). at
         * commit time, we delete a subtree, and then copy a subtree. we do not
         * need to specifically touch this node -- all operations occur on
         * ancestors.
         * 
         * Given the "ancestor operation" concept, then in case (1) we must
         * *clear* the COPIED flag since we'll have more work to do. In case
         * (2), we *set* the COPIED flag to indicate that no real work is going
         * to happen on this node.
         * 
         * Great fun. And just maybe the code reading the entries has no bugs in
         * interpreting that gobbledygook... but that *is* the expectation of
         * the code. Sigh.
         * 
         * We can get a little bit of shortcut here if THIS_DIR is also schduled
         * for deletion.
         */
        if (parentEntry != null && parentEntry.isScheduledForDeletion()) {
            /*
             * ### not entirely sure that we can rely on the parent. for ###
             * example, what if we are a deletion of a BASE node, but ### the
             * parent is a deletion of a copied subtree? sigh.
             */

            /* Child nodes simply inherit the parent's COPIED flag. */
            entry.setCopied(parentEntry.isCopied());
        } else {

            /* Find out details of our deletion. */
            final WCDbDeletionInfo deletionInfo = db.scanDeletion(entryAbsPath, DeletionInfoField.baseReplaced, DeletionInfoField.workDelAbsPath);

            /*
             * If there is no deletion in the WORKING tree, then the node is a
             * child of a simple explicit deletion of the BASE tree. It
             * certainly isn't copied. If we *do* find a deletion in the WORKING
             * tree, then we need to discover information about the parent.
             */
            if (deletionInfo.workDelAbsPath != null) {
                File parent_abspath;
                WCDbStatus parent_status;

                /*
                 * The parent is in WORKING except during post-commit when it
                 * may have been moved from the WORKING tree to the BASE tree.
                 */
                parent_abspath = SVNFileUtil.getParentFile(deletionInfo.workDelAbsPath);
                parent_status = db.readInfo(parent_abspath, InfoField.status).status;

                if (parent_status == WCDbStatus.Added || parent_status == WCDbStatus.ObstructedAdd) {
                    parent_status = db.scanAddition(parent_abspath, AdditionInfoField.status).status;

                }
                if (parent_status == WCDbStatus.Copied || parent_status == WCDbStatus.MovedHere || parent_status == WCDbStatus.Normal) {
                    /*
                     * The parent is copied/moved here, so WORK_DEL_ABSPATH is
                     * the root of a deleted subtree. Our COPIED status is now
                     * dependent upon whether the copied root is replacing a
                     * BASE tree or not.
                     * 
                     * But: if we are schedule-delete as a result of being a
                     * copied DELETED node, then *always* mark COPIED. Normal
                     * copies have cmt_* data; copied DELETED nodes are missing
                     * this info.
                     * 
                     * Note: MOVED_HERE is a concept foreign to this old
                     * interface, but it is best represented as if a copy had
                     * occurred, so we'll model it that way to old clients.
                     * 
                     * Note: svn_wc__db_status_normal corresponds to the
                     * post-commit parent that was copied or moved in WORKING
                     * but has now been converted to BASE.
                     */
                    if (SVNRevision.isValidRevisionNumber(entry.getCommittedRevision())) {
                        /*
                         * The scan_deletion call will tell us if there was an
                         * explicit move-away of an ancestor (which also means a
                         * replacement has occurred since there is a WORKING
                         * tree that isn't simply BASE deletions). The call will
                         * also tell us if there was an implicit deletion caused
                         * by a replacement. All stored in BASE_REPLACED.
                         */
                        entry.setCopied(deletionInfo.baseReplaced);
                    } else {
                        entry.setCopied(true);
                    }
                } else {
                    assert (parent_status == WCDbStatus.Added);

                    /*
                     * Whoops. WORK_DEL_ABSPATH is scheduled for deletion, yet
                     * the parent is scheduled for a plain addition. This can
                     * occur when a subtree is deleted, and then nodes are added
                     * *later*. Since the parent is a simple add, then nothing
                     * has been copied. Nothing more to do.
                     * 
                     * Note: if a subtree is added, *then* deletions are made,
                     * the nodes should simply be removed from version control.
                     */
                }
            }
        }

        return resInfo;
    }

}
