/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.sqljet.core.table.SqlJetDefaultBusyHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNChecksumKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNSimpleVersionedPropertiesImpl;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNPropertyConflictDescription;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNTextConflictDescription;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWorkingCopyDB17 implements ISVNWorkingCopyDB {

    public static final int WC_FORMAT_17 = 16;
    public static final int SVN_WC__HAS_WORK_QUEUE = 13;

    private static final int FORMAT_FROM_SDB = -1;
    private static final long UNKNOWN_WC_ID = -1;

    private static final String[] WC_METADATA_SQL_12 = {
        "CREATE TABLE REPOSITORY ( id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                  "root TEXT UNIQUE NOT NULL, " +
                                  "uuid TEXT NOT NULL );",
        "CREATE INDEX I_UUID ON REPOSITORY (uuid);",

        "CREATE INDEX I_ROOT ON REPOSITORY (root);",

        "CREATE TABLE WCROOT ( id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                              "local_abspath TEXT UNIQUE );",
        "CREATE UNIQUE INDEX I_LOCAL_ABSPATH ON WCROOT (local_abspath);",

        "CREATE TABLE BASE_NODE ( wc_id INTEGER NOT NULL, " +
                                 "local_relpath TEXT NOT NULL, " +
                                 "repos_id INTEGER, " +
                                 "repos_relpath TEXT, " +
                                 "parent_relpath TEXT, " +
                                 "presence TEXT NOT NULL, " +
                                 "kind TEXT NOT NULL, " +
                                 "revnum INTEGER, " +
                                 "checksum TEXT, " +
                                 "translated_size INTEGER, " +
                                 "changed_rev INTEGER, " +
                                 "changed_date  INTEGER, " +
                                 "changed_author TEXT, " +
                                 "depth TEXT, " +
                                 "symlink_target TEXT, " +
                                 "last_mod_time INTEGER, " +
                                 "last_mod_time INTEGER, " +
                                 "dav_cache BLOB, " +
                                 "incomplete_children INTEGER, " +
                                 "file_external TEXT, " +
                                 "PRIMARY KEY (wc_id, local_relpath) );",

        "CREATE INDEX I_PARENT ON BASE_NODE (wc_id, parent_relpath);",

        "CREATE TABLE PRISTINE ( checksum TEXT NOT NULL PRIMARY KEY, " +
                                "compression INTEGER, " +
                                "size INTEGER, " +
                                "refcount INTEGER NOT NULL ); ",

        "CREATE TABLE WORKING_NODE ( wc_id INTEGER NOT NULL, " +
                                    "local_relpath TEXT NOT NULL, " +
                                    "parent_relpath TEXT, " +
                                    "presence TEXT NOT NULL, " +
                                    "kind TEXT NOT NULL, " +
                                    "checksum TEXT, " +
                                    "translated_size INTEGER, " +
                                    "changed_rev INTEGER, " +
                                    "changed_date INTEGER, " +
                                    "changed_author TEXT, " +
                                    "depth TEXT, " +
                                    "symlink_target TEXT, " +
                                    "copyfrom_repos_id INTEGER, " +
                                    "copyfrom_repos_path TEXT, " +
                                    "copyfrom_revnum INTEGER, " +
                                    "moved_here INTEGER, " +
                                    "moved_to TEXT, " +
                                    "last_mod_time  INTEGER, " +
                                    "properties BLOB, " +
                                    "keep_local INTEGER, " +
                                    "PRIMARY KEY (wc_id, local_relpath) );",

        "CREATE INDEX I_WORKING_PARENT ON WORKING_NODE (wc_id, parent_relpath);",

        "CREATE TABLE ACTUAL_NODE ( wc_id INTEGER NOT NULL, " +
                                   "local_relpath  TEXT NOT NULL, " +
                                   "parent_relpath  TEXT, " +
                                   "properties BLOB, " +
                                   "conflict_old TEXT, " +
                                   "conflict_new TEXT, " +
                                   "conflict_working TEXT, " +
                                   "prop_reject TEXT, " +
                                   "changelist TEXT, " +
                                   "text_mod TEXT, " +
                                   "tree_conflict_data TEXT, " +
                                   "PRIMARY KEY (wc_id, local_relpath) );",

        "CREATE INDEX I_ACTUAL_PARENT ON ACTUAL_NODE (wc_id, parent_relpath);",

        "CREATE INDEX I_ACTUAL_CHANGELIST ON ACTUAL_NODE (changelist);",

        "CREATE TABLE LOCK ( repos_id INTEGER NOT NULL, " +
                            "repos_relpath TEXT NOT NULL, " +
                            "lock_token TEXT NOT NULL, " +
                            "lock_owner TEXT, " +
                            "lock_comment TEXT, " +
                            "lock_date INTEGER, " +
                            "PRIMARY KEY (repos_id, repos_relpath) );"
    };

    private static final String[] WC_METADATA_SQL_13 = {
        "CREATE TABLE WORK_QUEUE ( id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                   "work BLOB NOT NULL );",

        "UPDATE BASE_NODE SET incomplete_children=null, dav_cache=null;"
    };

    private static final String[] WC_METADATA_SQL_14 = {
        "CREATE TABLE WC_LOCK ( wc_id INTEGER NOT NULL, " +
                               "local_dir_relpath TEXT NOT NULL, " +
                               "PRIMARY KEY (wc_id, local_dir_relpath) );",

        "ALTER TABLE ACTUAL_NODE ADD COLUMN conflict_data  BLOB;",

        "ALTER TABLE ACTUAL_NODE ADD COLUMN older_checksum TEXT;",

        "ALTER TABLE ACTUAL_NODE ADD COLUMN left_checksum TEXT;",

        "ALTER TABLE ACTUAL_NODE ADD COLUMN right_checksum TEXT;"
    };

    private static final String[] WC_METADATA_SQL_15 = {
        "UPDATE base_node SET presence = 'excluded', " +
                             "checksum = NULL, " +
                             "translated_size = NULL, " +
                             "changed_rev = NULL, " +
                             "changed_date = NULL, " +
                             "changed_author = NULL, " +
                             "depth = NULL, " +
                             "symlink_target = NULL, " +
                             "last_mod_time = NULL, " +
                             "properties = NULL, " +
                             "incomplete_children = NULL, " +
                             "file_external = NULL WHERE depth = 'exclude';",
        "UPDATE working_node SET presence = 'excluded', " +
                                "checksum = NULL, " +
                                "translated_size = NULL, " +
                                "changed_rev = NULL, " +
                                "changed_date = NULL, " +
                                "changed_author = NULL, " +
                                "depth = NULL, " +
                                "symlink_target = NULL, " +
                                "copyfrom_repos_id = NULL, " +
                                "copyfrom_repos_path = NULL, " +
                                "copyfrom_revnum = NULL, " +
                                "moved_here = NULL, " +
                                "moved_to = NULL, " +
                                "last_mod_time = NULL, " +
                                "properties = NULL, " +
                                "keep_local = NULL WHERE depth = 'exclude';"
    };

    private Map<File, SVNPristineDirectory> myPathsToPristineDirs;
    private boolean myIsAutoUpgrade;
    private boolean myIsEnforceEmptyWorkQueue;

    //these temp fields are set within this class only
    //and must be used to pass values between internal methods only
    private long myCurrentReposId;
    private long myCurrentWCId;

    private SVNCommonDbStrategy myCommonDbStrategy;
    private SVNSelectActualConflictVictimsStrategy mySelectActualConflictVictimsStrategy;

    public SVNWorkingCopyDB17() {
    }

    public SVNEntryInfo readInfo(File path, boolean fetchLock, boolean fetchStatus, boolean fetchKind, boolean fetchIsText,
            boolean fetchIsProp, boolean fetchIsBaseShadowed, boolean fetchIsConflicted, boolean fetchOriginalUUID,
            boolean fetchOriginalRevision, boolean fetchOriginalRootURL, boolean fetchOriginalReposPath, boolean fetchTarget) throws SVNException {
        ParsedPristineDirectory parsedPristineDir = parseLocalAbsPath(path, SqlJetTransactionMode.READ_ONLY);
        String localRelPath = parsedPristineDir.myLocalRelativePath;
        SVNPristineDirectory pristineDir = parsedPristineDir.myPristineDirectory;

        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        SqlJetDb sdb = wcRoot.getStorage();
        Map<SVNDbTableField, Object> baseNodeResult = (Map<SVNDbTableField, Object>) runSelect(sdb, SVNDbTables.base_node,
                getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath}, SVNSqlJetUtil.OUR_BASE_NODE_FIELDS, null));
        if (!baseNodeResult.isEmpty() && fetchLock) {
            Long reposId = (Long) baseNodeResult.get(SVNDbTableField.repos_id);
            String reposPath = (String) baseNodeResult.get(SVNDbTableField.repos_relpath);
            baseNodeResult.putAll((Map<SVNDbTableField, Object>)runSelect(sdb, SVNDbTables.lock,
                    getCommonDbStrategy(new Object[] { reposId, reposPath }, SVNSqlJetUtil.OUR_LOCK_FIELDS, null)));
        }

        Map<SVNDbTableField, Object> workingNodeResult = (Map<SVNDbTableField, Object>) runSelect(sdb, SVNDbTables.working_node,
                getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath }, SVNSqlJetUtil.OUR_WORKING_NODE_FIELDS, null));
        Map<SVNDbTableField, Object> actualNodeResult = (Map<SVNDbTableField, Object>) runSelect(sdb, SVNDbTables.actual_node,
                getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath}, SVNSqlJetUtil.OUR_ACTUAL_NODE_FIELDS, null));

        boolean haveBase = !baseNodeResult.isEmpty();
        boolean haveWorking = !workingNodeResult.isEmpty();
        boolean haveActual = !actualNodeResult.isEmpty();

        String reposRootURL = null;
        String reposUUID = null;
        Long revision = SVNRepository.INVALID_REVISION;
        Long changedRevision = SVNRepository.INVALID_REVISION;
        Long changedDateMillis = -1L;
        Long lastModTimeMillis = -1L;
        Long translatedSize = -1L;
        Long originalRevision = SVNRepository.INVALID_REVISION;
        String reposPath = null;
        String changedAuthor = null;
        SVNChecksum checksum = null;
        String target = null;
        String changeList = null;
        String originalReposPath = null;
        String originalRootURL = null;
        String originalUUID = null;
        SVNDepth depth = null;
        SVNWCDbStatus status = null;
        SVNWCDbKind kind = null;
        boolean isTextMode = false;
        boolean isPropsMode = false;
        boolean isBaseShadowed = false;
        boolean isConflicted = false;
        SVNWCDbLock lock = null;
        if (haveBase || haveWorking) {
            SVNWCDbKind nodeKind = null;
            String kindStr = null;
            if (haveWorking) {
                kindStr = (String) workingNodeResult.get(SVNDbTableField.kind);
            } else {
                kindStr = (String) baseNodeResult.get(SVNDbTableField.kind);
            }

            nodeKind = SVNWCDbKind.parseKind(kindStr);
            if (fetchStatus) {
                if (haveBase) {
                    String statusStr = (String) baseNodeResult.get(SVNDbTableField.presence);
                    status = SVNWCDbStatus.parseStatus(statusStr);

                    SVNErrorManager.assertionFailure((status != SVNWCDbStatus.ABSENT && status != SVNWCDbStatus.EXCLUDED) || !haveWorking, null, SVNLogType.WC);

                    if (nodeKind == SVNWCDbKind.SUBDIR && status == SVNWCDbStatus.NORMAL) {
                        status = SVNWCDbStatus.OBSTRUCTED;
                    }
                }

                if (haveWorking) {
                    String statusStr = (String) workingNodeResult.get(SVNDbTableField.presence);
                    SVNWCDbStatus workStatus = SVNWCDbStatus.parseStatus(statusStr);
                    if (workStatus != SVNWCDbStatus.NORMAL && workStatus != SVNWCDbStatus.NOT_PRESENT && workStatus != SVNWCDbStatus.BASE_DELETED &&
                            workStatus != SVNWCDbStatus.INCOMPLETE) {
                        if (workStatus == SVNWCDbStatus.INCOMPLETE) {
                            status = SVNWCDbStatus.INCOMPLETE;
                        } else if (workStatus == SVNWCDbStatus.NOT_PRESENT || workStatus == SVNWCDbStatus.BASE_DELETED) {
                            if (nodeKind == SVNWCDbKind.SUBDIR) {
                                status = SVNWCDbStatus.OBSTRUCTED_DELETE;
                            } else {
                                status = SVNWCDbStatus.DELETED;
                            }
                        } else {
                            if (nodeKind == SVNWCDbKind.SUBDIR) {
                                status = SVNWCDbStatus.OBSTRUCTED_ADD;
                            } else {
                                status = SVNWCDbStatus.ADDED;
                            }
                        }
                    }
                }
            }

            if (fetchKind) {
                if (nodeKind == SVNWCDbKind.SUBDIR) {
                    kind = SVNWCDbKind.DIR;
                } else {
                    kind = nodeKind;
                }
            }

            //repository root url, repository uuid
            Long reposId = (Long) baseNodeResult.get(SVNDbTableField.repos_id);

            if (!haveWorking) {
                //revision
                revision = (Long) baseNodeResult.get(SVNDbTableField.revnum);

                //repository relative path
                reposPath = (String) baseNodeResult.get(SVNDbTableField.repos_relpath);

                if (reposId != null) {
                    SVNRepositoryInfo reposInfo = fetchRepositoryInfo(sdb, reposId);
                    reposRootURL = reposInfo.getRootURL();
                    reposUUID = reposInfo.getUUID();
                }

                changedRevision = (Long) baseNodeResult.get(SVNDbTableField.changed_rev);
                changedDateMillis = (Long) baseNodeResult.get(SVNDbTableField.changed_date);
                changedAuthor = (String) baseNodeResult.get(SVNDbTableField.changed_author);
                lastModTimeMillis = (Long) baseNodeResult.get(SVNDbTableField.last_mod_time);
            } else {
                changedRevision = (Long) workingNodeResult.get(SVNDbTableField.changed_rev);
                changedDateMillis = (Long) workingNodeResult.get(SVNDbTableField.changed_date);
                changedAuthor = (String) workingNodeResult.get(SVNDbTableField.changed_author);
                lastModTimeMillis = (Long) workingNodeResult.get(SVNDbTableField.last_mod_time);
                if (fetchOriginalReposPath) {
                    originalReposPath = (String) workingNodeResult.get(SVNDbTableField.copyfrom_repos_path);
                }
                Long copyFromReposIdObj = (Long) workingNodeResult.get(SVNDbTableField.copyfrom_repos_id);
                if (copyFromReposIdObj != null && (fetchOriginalRootURL || fetchOriginalUUID)) {
                    SVNRepositoryInfo reposInfo = fetchRepositoryInfo(sdb, copyFromReposIdObj.longValue());
                    originalRootURL = reposInfo.getRootURL();
                    originalUUID = reposInfo.getUUID();
                }
                if (fetchOriginalRevision) {
                    originalRevision = (Long) workingNodeResult.get(SVNDbTableField.copyfrom_revnum);
                }
            }

            if (nodeKind != SVNWCDbKind.DIR && nodeKind != SVNWCDbKind.SUBDIR) {
                depth = SVNDepth.UNKNOWN;
            } else {
                String depthStr = null;
                if (haveWorking) {
                    depthStr = (String) workingNodeResult.get(SVNDbTableField.depth);
                } else {
                    depthStr = (String) baseNodeResult.get(SVNDbTableField.depth);
                }

                if (depthStr == null) {
                    depth = SVNDepth.UNKNOWN;
                } else {
                    depth = SVNDepth.fromString(depthStr);
                }
            }

            if (nodeKind == SVNWCDbKind.FILE) {
                String checksumStr = null;
                if (haveWorking) {
                    checksumStr = (String) workingNodeResult.get(SVNDbTableField.checksum);
                } else {
                    checksumStr = (String) baseNodeResult.get(SVNDbTableField.checksum);
                }

                if (checksumStr != null) {
                    try {
                        checksum = SVNChecksum.deserializeChecksum(checksumStr);
                    } catch (SVNException svne) {
                        SVNErrorMessage err = SVNErrorMessage.create(svne.getErrorMessage().getErrorCode(),
                                "The node ''{0}'' has a corrupt checksum value.", path);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                }
            }

            Long translatedSizeObj = null;
            if (haveWorking) {
                translatedSizeObj = (Long) workingNodeResult.get(SVNDbTableField.translated_size);
            } else {
                translatedSizeObj = (Long) baseNodeResult.get(SVNDbTableField.translated_size);
            }

            if (translatedSizeObj == null) {
                translatedSize = -1L;
            } else {
                translatedSize = translatedSizeObj.longValue();
            }

            if (fetchTarget && nodeKind == SVNWCDbKind.SYMLINK) {
                if (haveWorking) {
                    target = (String) workingNodeResult.get(SVNDbTableField.symlink_target);
                } else {
                    target = (String) baseNodeResult.get(SVNDbTableField.symlink_target);
                }
            }

            if (haveActual) {
                changeList = (String) actualNodeResult.get(SVNDbTableField.changelist);
            }

            if (fetchIsText) {
                isTextMode = false;
            }

            if (fetchIsProp) {
                isPropsMode = haveActual && actualNodeResult.get(SVNDbTableField.properties) != null;
            }

            if (fetchIsBaseShadowed) {
                isBaseShadowed = haveBase && haveWorking;
            }

            if (fetchIsConflicted) {
                if (haveActual) {
                    isConflicted = actualNodeResult.get(SVNDbTableField.conflict_old) != null ||
                    actualNodeResult.get(SVNDbTableField.conflict_new) != null || actualNodeResult.get(SVNDbTableField.conflict_working) != null ||
                    actualNodeResult.get(SVNDbTableField.prop_reject) != null;
                } else {
                    isConflicted = false;
                }
            }

            if (fetchLock) {
                String lockToken = (String) baseNodeResult.get(SVNDbTableField.lock_token);
                if (lockToken == null) {
                    lock = null;
                } else {
                    String owner = (String) baseNodeResult.get(SVNDbTableField.lock_owner);
                    String comment = (String) baseNodeResult.get(SVNDbTableField.lock_comment);
                    Long dateMillis = (Long) baseNodeResult.get(SVNDbTableField.lock_date);
                    lock = new SVNWCDbLock(lockToken, owner, comment, new Date(dateMillis));
                }
            }
        } else if (haveActual) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Corrupt data for ''{0}''", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (fetchIsConflicted && !isConflicted) {
            SVNTreeConflictDescription treeConflict = readTreeConflict(path);
            isConflicted = treeConflict != null;
        }

        SVNEntryInfo info = new SVNEntryInfo();
        info.setRevision(revision != null ? revision : SVNRepository.INVALID_REVISION);
        info.setReposRootURL(reposRootURL);
        info.setUUID(reposUUID);
        info.setCommittedRevision(changedRevision != null ? changedRevision : SVNRepository.INVALID_REVISION);
        info.setCommittedDate(changedDateMillis != null ? new Date(changedDateMillis) : SVNDate.NULL);
        info.setCommittedAuthor(changedAuthor);
        info.setLastTextTime(lastModTimeMillis != null ? new Date(lastModTimeMillis) : SVNDate.NULL);
        info.setDepth(depth);
        info.setChecksum(checksum);
        info.setWorkingSize(translatedSize != null ? translatedSize : -1L);
        info.setTarget(target);
        info.setChangeList(changeList);
        info.setOriginalReposPath(originalReposPath);
        info.setOriginalRevision(originalRevision != null ? originalRevision : SVNRepository.INVALID_REVISION);
        info.setOriginalRootURL(originalRootURL);
        info.setOriginalUUID(originalUUID);
        info.setIsTextMode(isTextMode);
        info.setIsPropsMode(isPropsMode);
        info.setIsBaseShadowed(isBaseShadowed);
        info.setIsConflicted(isConflicted);
        info.setWCDBLock(lock);
        info.setWCDBKind(kind);
        info.setWCDBStatus(status);
        info.setReposPath(reposPath);
        return info;
    }

    public SVNVersionedProperties readProperties(File path) throws SVNException {
        Map<SVNDbTableField, Object> actualResult = runSelectForPath(path, SVNDbTables.actual_node, SVNSqlJetUtil.OUR_PROPERTIES_FIELD);
        if (!actualResult.isEmpty() && actualResult.get(SVNDbTableField.properties) != null) {
            return new SVNSimpleVersionedPropertiesImpl(SVNSqlJetUtil.getPropertiesFromBLOB(actualResult.get(SVNDbTableField.properties)));
        }
        return readPristineProperties(path);
    }

    public SVNVersionedProperties readPristineProperties(File path) throws SVNException {
        Map<SVNDbTableField, Object> workingResult = runSelectForPath(path, SVNDbTables.working_node, SVNSqlJetUtil.OUR_PROPERTIES_FIELD);
        if (!workingResult.isEmpty() && workingResult.get(SVNDbTableField.properties) != null) {
            return new SVNSimpleVersionedPropertiesImpl(SVNSqlJetUtil.getPropertiesFromBLOB(workingResult.get(SVNDbTableField.properties)));
        }

        SVNVersionedProperties props = null;
        try {
            props = getBaseProperties(path);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw svne;
            }
        }
        return props;
    }

    public SVNVersionedProperties getBaseProperties(File path) throws SVNException {
        Map<SVNDbTableField, Object> baseResult = runSelectForPath(path, SVNDbTables.base_node, SVNSqlJetUtil.OUR_PROPERTIES_FIELD);
        if (baseResult.isEmpty()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNProperties props = SVNSqlJetUtil.getPropertiesFromBLOB(baseResult.get(SVNDbTableField.properties));
        return new SVNSimpleVersionedPropertiesImpl(props);
    }

    public SVNEntryInfo getBaseInfo(File path, boolean fetchLock) throws SVNException {
        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(path, SqlJetTransactionMode.READ_ONLY);
        String localRelPath = parsedPristineDirectory.myLocalRelativePath;
        SVNPristineDirectory pristineDirectory = parsedPristineDirectory.getPristineDirectory();
        SVNWCRoot wcRoot = pristineDirectory.getWCRoot();
        SqlJetDb sdb = wcRoot.getStorage();
        verifyPristineDirectoryIsUsable(pristineDirectory);

        Map<SVNDbTableField, Object> baseNodeResult = (Map<SVNDbTableField, Object>) runSelect(sdb, SVNDbTables.base_node,
                getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath }, SVNSqlJetUtil.OUR_BASE_NODE_FIELDS, null));
        if (!baseNodeResult.isEmpty() && fetchLock) {
            long reposId = (Long) baseNodeResult.get(SVNDbTableField.repos_id);
            String reposPath = (String) baseNodeResult.get(SVNDbTableField.repos_relpath);
            baseNodeResult.putAll((Map<SVNDbTableField, Object>) runSelect(sdb, SVNDbTables.lock,
                    getCommonDbStrategy(new Object[] { reposId, reposPath }, SVNSqlJetUtil.OUR_LOCK_FIELDS, null)));
        }

        SVNWCDbKind kind = null;
        SVNWCDbStatus status = null;
        Long revision = SVNRepository.INVALID_REVISION;
        Long changedRevision = SVNRepository.INVALID_REVISION;
        String changedAuthor = null;
        Long changedDateMillis = -1L;
        Long lastModTimeMillis = -1L;
        Long translatedSize = -1L;
        SVNWCDbLock lock = null;
        String reposPath = null;
        String reposRootURL = null;
        String reposUUID = null;
        String target = null;
        SVNDepth depth = null;
        SVNChecksum checksum = null;

        if (!baseNodeResult.isEmpty()) {
            String kindStr = (String) baseNodeResult.get(SVNDbTableField.kind);
            SVNWCDbKind nodeKind = SVNWCDbKind.parseKind(kindStr);
            if (nodeKind == SVNWCDbKind.SUBDIR) {
                kind = SVNWCDbKind.DIR;
            } else {
                kind = nodeKind;
            }

            String statusStr = (String) baseNodeResult.get(SVNDbTableField.presence);
            status = SVNWCDbStatus.parseStatus(statusStr);

            if (nodeKind == SVNWCDbKind.SUBDIR && status == SVNWCDbStatus.NORMAL) {
                status = SVNWCDbStatus.OBSTRUCTED;
            }

            revision = (Long) baseNodeResult.get(SVNDbTableField.revnum);
            reposPath = (String) baseNodeResult.get(SVNDbTableField.repos_relpath);
            if (fetchLock) {
                String lockToken = (String) baseNodeResult.get(SVNDbTableField.lock_token);
                if (lockToken == null) {
                    lock = null;
                } else {
                    String owner = (String) baseNodeResult.get(SVNDbTableField.lock_owner);
                    String comment = (String) baseNodeResult.get(SVNDbTableField.lock_comment);
                    Long dateMillis = (Long) baseNodeResult.get(SVNDbTableField.lock_date);
                    lock = new SVNWCDbLock(lockToken, owner, comment, new Date(dateMillis));
                }
            }

            Long reposId = (Long) baseNodeResult.get(SVNDbTableField.repos_id);
            if (reposId != null) {
                SVNRepositoryInfo reposInfo = fetchRepositoryInfo(sdb, reposId);
                reposRootURL = reposInfo.getRootURL();
                reposUUID = reposInfo.getUUID();
            }

            changedRevision = (Long) baseNodeResult.get(SVNDbTableField.changed_rev);
            changedDateMillis = (Long) baseNodeResult.get(SVNDbTableField.changed_date);
            changedAuthor = (String) baseNodeResult.get(SVNDbTableField.changed_author);
            lastModTimeMillis = (Long) baseNodeResult.get(SVNDbTableField.last_mod_time);

            if (nodeKind != SVNWCDbKind.DIR) {
                depth = SVNDepth.UNKNOWN;
            } else {
                String depthStr = (String) baseNodeResult.get(SVNDbTableField.depth);
                if (depthStr == null) {
                    depth = SVNDepth.UNKNOWN;
                } else {
                    depth = SVNDepth.fromString(depthStr);
                }
            }

            if (nodeKind == SVNWCDbKind.FILE) {
                String checksumStr = (String) baseNodeResult.get(SVNDbTableField.checksum);
                if (checksumStr != null) {
                    try {
                        checksum = SVNChecksum.deserializeChecksum(checksumStr);
                    } catch (SVNException svne) {
                        SVNErrorMessage err = SVNErrorMessage.create(svne.getErrorMessage().getErrorCode(), "The node ''{0}'' has a corrupt checksum value.",
                                path);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                }
            }

            Long translatedSizeObj = null;
            translatedSizeObj = (Long) baseNodeResult.get(SVNDbTableField.translated_size);
            if (translatedSizeObj == null) {
                translatedSize = -1L;
            } else {
                translatedSize = translatedSizeObj.longValue();
            }

            if (nodeKind == SVNWCDbKind.SYMLINK) {
                target = (String) baseNodeResult.get(SVNDbTableField.symlink_target);
            }
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }


        SVNEntryInfo info = new SVNEntryInfo();
        info.setWCDBKind(kind);
        info.setWCDBStatus(status);
        info.setRevision(revision != null ? revision : SVNRepository.INVALID_REVISION);
        info.setReposPath(reposPath);
        info.setReposRootURL(reposRootURL);
        info.setUUID(reposUUID);
        info.setCommittedRevision(changedRevision != null ? changedRevision : SVNRepository.INVALID_REVISION);
        info.setCommittedDate(changedDateMillis != null ? new Date(changedDateMillis) : SVNDate.NULL);
        info.setCommittedAuthor(changedAuthor);
        info.setLastTextTime(lastModTimeMillis != null ? new Date(lastModTimeMillis) : SVNDate.NULL);
        info.setDepth(depth);
        info.setChecksum(checksum);
        info.setWorkingSize(translatedSize != null ? translatedSize : -1L);
        info.setTarget(target);
        info.setWCDBLock(lock);
        info.setWCDBKind(kind);
        info.setWCDBStatus(status);
        return info;
    }

    public void addLock(File path, SVNWCDbLock lock) throws SVNException {
        SVNErrorManager.assertionFailure(lock != null, null, SVNLogType.WC);
        ParsedPristineDirectory parsedPD = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        SVNPristineDirectory pristineDir = parsedPD.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        String localRelPath = parsedPD.getLocalRelativePath();
        RepositoryId scanResult = scanUpwardsForRepository(wcRoot, localRelPath);
        long reposId = scanResult.getReposId();
        String reposPath = scanResult.getReposPath();
        Map<SVNDbTableField, Object> fieldsToValues = new HashMap<SVNDbTableField, Object>();
        fieldsToValues.put(SVNDbTableField.repos_id, reposId);
        fieldsToValues.put(SVNDbTableField.repos_relpath, reposPath);
        fieldsToValues.put(SVNDbTableField.lock_token, lock.getToken());
        if (lock.getOwner() != null) {
            fieldsToValues.put(SVNDbTableField.lock_owner, lock.getOwner());
        }

        if (lock.getComment() != null) {
            fieldsToValues.put(SVNDbTableField.lock_comment, lock.getComment());
        }

        if (lock.getDate() != null) {
            fieldsToValues.put(SVNDbTableField.lock_date, lock.getDate().getTime());
        }

        runInsertByFieldNames(wcRoot.getStorage(), SVNDbTables.lock, SqlJetConflictAction.REPLACE,
                getCommonDbStrategy(null, null, null), fieldsToValues);
        //TODO: flush entries
    }

    public void removeLock(File path) throws SVNException {
        ParsedPristineDirectory parsedPD = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        SVNPristineDirectory pristineDir = parsedPD.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        String localRelPath = parsedPD.getLocalRelativePath();
        RepositoryId scanResult = scanUpwardsForRepository(wcRoot, localRelPath);
        long reposId = scanResult.getReposId();
        String reposPath = scanResult.getReposPath();
        runDelete(wcRoot.getStorage(), SVNDbTables.lock, getCommonDbStrategy(new Object[] { reposId, reposPath }, null, null), null);
        //TODO: flush entries
    }

    public boolean isNodeHidden(File path) throws SVNException {
        ParsedPristineDirectory parsedPD = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        SVNPristineDirectory pristineDir = parsedPD.getPristineDirectory();
        String localRelPath = parsedPD.getLocalRelativePath();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();

        Map<SVNDbTableField, Object> workingResult = (Map<SVNDbTableField, Object>) runSelect(wcRoot.getStorage(), SVNDbTables.working_node,
                getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath }, SVNSqlJetUtil.OUR_WORKING_NODE_FIELDS, null));
        if (workingResult.isEmpty()) {
            return false;
        }

        SVNEntryInfo info = getBaseInfo(path, false);
        SVNWCDbStatus baseStatus = info.getWCDBStatus();
        return baseStatus == SVNWCDbStatus.ABSENT || baseStatus == SVNWCDbStatus.NOT_PRESENT || baseStatus == SVNWCDbStatus.EXCLUDED;
    }

    public Collection readConflictVictims(File path) throws SVNException {
        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(path, SqlJetTransactionMode.READ_ONLY);
        String localRelPath = parsedPristineDirectory.myLocalRelativePath;
        SVNPristineDirectory pristineDirectory = parsedPristineDirectory.myPristineDirectory;
        verifyPristineDirectoryIsUsable(pristineDirectory);
        SVNWCRoot wcRoot = pristineDirectory.getWCRoot();
        SqlJetDb sdb = wcRoot.getStorage();

        String treeConflictData = null;
        Map<String, String> foundVictims = (Map<String, String>) runSelect(sdb, SVNDbTables.actual_node,
                getSelectActualConflictVictimsStrategy(wcRoot.getWCId(), localRelPath));
        Map<SVNDbTableField, Object> actualTreeConflictResult = (Map<SVNDbTableField, Object>) runSelect(sdb, SVNDbTables.actual_node,
                getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath }, SVNSqlJetUtil.OUR_ACTUAL_TREE_CONFLICT_FIELDS, null));
        treeConflictData = (String) actualTreeConflictResult.get(SVNDbTableField.tree_conflict_data);

        if (treeConflictData != null) {
            Map treeConflicts = SVNTreeConflictUtil.readTreeConflicts(path, treeConflictData);
            for (Iterator treeConflictsIter = treeConflicts.keySet().iterator(); treeConflictsIter.hasNext();) {
                File conflictedPath = (File) treeConflictsIter.next();
                foundVictims.put(conflictedPath.getName(), conflictedPath.getName());
            }
        }
        return foundVictims.keySet();
    }

    public SVNEntryInfo scanDeletion(File path, boolean fetchDeletedBasePath, boolean fetchIsBaseReplaced, boolean fetchMovedToPath,
            boolean fetchDeletedWorkPath) throws SVNException {
        boolean childHasBase = false;
        boolean foundMovedTo = false;
        boolean baseIsReplaced = false;
        File deletedBasePath = null;
        File currentPath = path;
        File childPath = null;
        File movedToPath = null;
        File deletedWorkingPath = null;
        SVNWCDbStatus childPresence = SVNWCDbStatus.BASE_DELETED;
        ParsedPristineDirectory parsedPristineDir = parseLocalAbsPath(path, SqlJetTransactionMode.READ_ONLY);
        SVNPristineDirectory pristineDir = parsedPristineDir.getPristineDirectory();
        String currentRelPath = parsedPristineDir.getLocalRelativePath();
        verifyPristineDirectoryIsUsable(pristineDir);
        while (true) {
            Object[] lookUpObjects = new Object[] { pristineDir.getWCRoot().getWCId(), currentRelPath };
            Map<SVNDbTableField, Object> workingNodeResult = (Map<SVNDbTableField, Object>) runSelect(pristineDir.getWCRoot().getStorage(),
                    SVNDbTables.working_node, getCommonDbStrategy(lookUpObjects, SVNSqlJetUtil.OUR_DELETION_INFO_FIELDS, null));
            Map<SVNDbTableField, Object> baseNodeResult = Collections.EMPTY_MAP;
            if (!workingNodeResult.isEmpty()) {
                baseNodeResult = (Map<SVNDbTableField, Object>) runSelect(pristineDir.getWCRoot().getStorage(), SVNDbTables.base_node,
                        getCommonDbStrategy(lookUpObjects, SVNSqlJetUtil.OUR_PRESENCE_FIELD, null));
            }

            if (workingNodeResult.isEmpty() && baseNodeResult.isEmpty()) {
                if (path.equals(currentPath)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }

                if (fetchDeletedWorkPath && childPresence == SVNWCDbStatus.NOT_PRESENT && deletedWorkingPath == null) {
                    deletedWorkingPath = childPath;
                }

                if (fetchDeletedBasePath && childHasBase && deletedBasePath == null) {
                    deletedBasePath = childPath;
                }

                break;
            }

            SVNWCDbStatus workPresence = SVNWCDbStatus.parseStatus((String) workingNodeResult.get(SVNDbTableField.presence));
            if (currentPath.equals(path) && workPresence != SVNWCDbStatus.NOT_PRESENT && workPresence != SVNWCDbStatus.BASE_DELETED) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Expected node ''{0}'' to be deleted.",
                        path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            SVNErrorManager.assertionFailure(workPresence == SVNWCDbStatus.NORMAL || workPresence == SVNWCDbStatus.NOT_PRESENT ||
                    workPresence == SVNWCDbStatus.BASE_DELETED, null, SVNLogType.WC);

            boolean haveBase = !baseNodeResult.isEmpty();
            if (haveBase) {
                SVNWCDbStatus basePresence = SVNWCDbStatus.parseStatus((String) baseNodeResult.get(SVNDbTableField.presence));
                SVNErrorManager.assertionFailure(basePresence == SVNWCDbStatus.NORMAL || basePresence == SVNWCDbStatus.NOT_PRESENT,
                        null, SVNLogType.WC);
                if (fetchIsBaseReplaced && basePresence == SVNWCDbStatus.NORMAL && workPresence != SVNWCDbStatus.BASE_DELETED) {
                    baseIsReplaced = true;
                }
            }

            if (!foundMovedTo && (fetchMovedToPath || fetchDeletedBasePath) && workingNodeResult.get(SVNDbTableField.moved_to) != null) {
                SVNErrorManager.assertionFailure(haveBase, null, SVNLogType.WC);
                foundMovedTo = true;
                if (fetchDeletedBasePath) {
                    deletedBasePath = currentPath;
                }
                if (fetchMovedToPath) {
                    movedToPath = new File(pristineDir.getWCRoot().getPath(), (String) workingNodeResult.get(SVNDbTableField.moved_to));
                }
            }

            if (fetchDeletedWorkPath && workPresence == SVNWCDbStatus.NORMAL && childPresence == SVNWCDbStatus.NOT_PRESENT) {
                deletedWorkingPath = childPath;
            }

            childPath = currentPath;
            childPresence = workPresence;
            childHasBase = haveBase;
            if (currentPath.equals(pristineDir.getPath())) {
                pristineDir = navigateToParent(pristineDir, SqlJetTransactionMode.READ_ONLY);
            }

            currentPath = pristineDir.getPath();
            currentRelPath = pristineDir.computeRelPath();
        }

        SVNEntryInfo info = new SVNEntryInfo();
        info.setDeletedBasePath(deletedBasePath);
        info.setIsBaseReplaced(baseIsReplaced);
        info.setMovedToPath(movedToPath);
        info.setDeletedWorkingPath(deletedWorkingPath);
        return info;
    }

    public void checkFileExternal(SVNEntry entry, SqlJetDb sdb) throws SVNException {
        Map<SVNDbTableField, Object> result = (Map<SVNDbTableField, Object>) runSelect(sdb, SVNDbTables.base_node,
                getCommonDbStrategy(new Object[] { 1, entry.getName() }, SVNSqlJetUtil.OUR_FILE_EXTERNAL_FIELD, null));
        if (!result.isEmpty()) {
            String fileExternalData = (String) result.get(SVNDbTableField.file_external);
            SVNAdminUtil.unserializeExternalFileData(entry.asMap(), fileExternalData);
        }
    }

    public SVNEntryInfo scanAddition(File path, boolean fetchReposPath, boolean fetchReposRootURL, boolean fetchReposUUID,
            boolean fetchOperationRoot, boolean fetchOriginalReposPath, boolean fetchOriginalRootURL, boolean fetchOriginalUUID,
            boolean fetchOriginalRevision, boolean fetchStatus) throws SVNException {
        File currentPath = path;
        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(currentPath, SqlJetTransactionMode.READ_ONLY);
        String currentRelPath = parsedPristineDirectory.getLocalRelativePath();
        SVNPristineDirectory pristineDir = parsedPristineDirectory.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);

        File operationRootPath = null;
        File childPath = null;
        SVNWCDbStatus status = null;
        boolean foundInfo = false;
        String originalReposPath = null;
        String originalRootURL = null;
        String originalUUID = null;
        long originalRevision = -1;
        String reposPath = null;
        String reposRootURL = null;
        String reposUUID = null;
        String buildRelPath = "";
        while (true) {
            Map<SVNDbTableField, Object> result = (Map<SVNDbTableField, Object>) runSelect(pristineDir.getWCRoot().getStorage(), SVNDbTables.working_node,
                    getCommonDbStrategy(new Object[] { pristineDir.getWCRoot().getWCId(), currentRelPath }, SVNSqlJetUtil.OUR_WORKING_NODE_FIELDS, null));
            if (!result.isEmpty()) {
                if (path.equals(currentPath)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
                if (fetchOperationRoot && operationRootPath == null) {
                    SVNErrorManager.assertionFailure(childPath != null, null, SVNLogType.WC);
                    operationRootPath = childPath;
                }
                break;
            }

            SVNWCDbStatus presence = SVNWCDbStatus.parseStatus((String) result.get(SVNDbTableField.presence));
            if (path.equals(currentPath)) {
                if (presence != SVNWCDbStatus.NORMAL) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Expected node ''{0}'' to be added.",
                            path);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }

                if (fetchStatus) {
                    status = SVNWCDbStatus.ADDED;
                }
            }

            if (!foundInfo && presence == SVNWCDbStatus.NORMAL && result.get(SVNDbTableField.copyfrom_repos_id) != null) {
                if (fetchStatus) {
                    boolean movedHere = (Boolean) result.get(SVNDbTableField.moved_here);
                    status = movedHere ? SVNWCDbStatus.MOVED_HERE : SVNWCDbStatus.COPIED;
                }

                if (fetchOperationRoot) {
                    operationRootPath = currentPath;
                }

                if (fetchOriginalReposPath) {
                    originalReposPath = (String) result.get(SVNDbTableField.copyfrom_repos_path);
                }

                if (fetchOriginalRootURL || fetchOriginalUUID) {
                    SVNRepositoryInfo reposInfo = fetchRepositoryInfo(pristineDir.getWCRoot().getStorage(),
                            (Long) result.get(SVNDbTableField.copyfrom_repos_id));
                    originalRootURL = reposInfo.getRootURL();
                    originalUUID = reposInfo.getUUID();
                }

                if (fetchOriginalRevision) {
                    originalRevision = (Long) result.get(SVNDbTableField.copyfrom_revnum);
                }

                if (reposPath == null && reposRootURL == null && reposUUID == null) {
                    SVNEntryInfo info = new SVNEntryInfo();
                    info.setOperationRootPath(operationRootPath);
                    info.setReposPath(reposPath);
                    info.setReposRootURL(reposRootURL);
                    info.setUUID(reposUUID);
                    info.setOriginalReposPath(originalReposPath);
                    info.setOriginalRootURL(originalRootURL);
                    info.setOriginalUUID(originalUUID);
                    info.setOriginalRevision(originalRevision);
                    info.setWCDBStatus(status);
                    return info;
                }

                foundInfo = true;
            }

            if (fetchReposPath) {
                buildRelPath = SVNPathUtil.append(currentPath.getName(), buildRelPath);
            }

            childPath = currentPath;
            if (currentPath.equals(pristineDir.getPath())) {
                pristineDir = navigateToParent(pristineDir, SqlJetTransactionMode.READ_ONLY);
            }

            currentPath = pristineDir.getPath();
            currentRelPath = pristineDir.computeRelPath();
        }

        if (fetchReposPath || fetchReposRootURL || fetchReposUUID) {
            SVNRepositoryScanResult scanResult = scanBaseRepos(currentPath);
            String baseRelPath = scanResult.getReposPath();
            SVNRepositoryInfo reposInfo = scanResult.getReposInfo();
            reposRootURL = reposInfo.getRootURL();
            reposUUID = reposInfo.getUUID();
            if (fetchReposPath) {
                reposPath = SVNPathUtil.append(baseRelPath, buildRelPath);
            }
        }

        SVNEntryInfo info = new SVNEntryInfo();
        info.setOperationRootPath(operationRootPath);
        info.setReposPath(reposPath);
        info.setReposRootURL(reposRootURL);
        info.setUUID(reposUUID);
        info.setOriginalReposPath(originalReposPath);
        info.setOriginalRootURL(originalRootURL);
        info.setOriginalUUID(originalUUID);
        info.setOriginalRevision(originalRevision);
        info.setWCDBStatus(status);
        return info;
    }

    public SVNRepositoryScanResult scanBaseRepos(File path) throws SVNException {
        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(path, SqlJetTransactionMode.READ_ONLY);
        String localRelPath = parsedPristineDirectory.myLocalRelativePath;
        SVNPristineDirectory pristineDir = parsedPristineDirectory.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        RepositoryId reposIdObj = scanUpwardsForRepository(wcRoot, localRelPath);
        SVNRepositoryInfo reposInfo = fetchRepositoryInfo(wcRoot.getStorage(), reposIdObj.getReposId());
        return new SVNRepositoryScanResult(reposInfo, reposIdObj.getReposPath());
    }

    public boolean determineKeepLocal(File path) throws SVNException {
        Map<SVNDbTableField, Object> result = runSelectForPath(path, SVNDbTables.working_node, SVNSqlJetUtil.OUR_KEEP_LOCAL_FIELD);
        if (!result.isEmpty()) {
            return (Boolean) result.get(SVNDbTableField.keep_local);
        }
        return false;
    }

    public boolean checkIfIsNotPresent(SqlJetDb sdb, long wcId, String localRelPath) throws SVNException {
        Map<SVNDbTableField, Object> result = (Map<SVNDbTableField, Object>) runSelect(sdb, SVNDbTables.base_node,
                getCommonDbStrategy(new Object[] { wcId, localRelPath }, SVNSqlJetUtil.OUR_PRESENCE_FIELD, null));
        String presence = (String) result.get(SVNDbTableField.presence);
        return "not-present".equalsIgnoreCase(presence);
    }

    public List<SVNConflictDescription> readConflicts(File path) throws SVNException {
        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(path, SqlJetTransactionMode.READ_ONLY);
        String localRelPath = parsedPristineDirectory.getLocalRelativePath();
        SVNPristineDirectory pristineDir = parsedPristineDirectory.myPristineDirectory;
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();

        List<SVNConflictDescription> conflicts = new LinkedList<SVNConflictDescription>();
        SqlJetDb sdb = wcRoot.getStorage();

        Map<SVNDbTableField, Object> result = (Map<SVNDbTableField, Object>) runSelect(sdb, SVNDbTables.actual_node,
                getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath }, SVNSqlJetUtil.OUR_CONFLICT_DETAILS_FIELDS, null));
        if (!result.isEmpty()) {
            String propRejectFile = (String) result.get(SVNDbTableField.prop_reject);
            if (propRejectFile != null) {
                SVNMergeFileSet fileSet = new SVNMergeFileSet(null, null, null, path, null, new File(propRejectFile), null, null, null);
                SVNPropertyConflictDescription propConflictDescr = new SVNPropertyConflictDescription(fileSet, SVNNodeKind.UNKNOWN, "",
                        null, null);
                conflicts.add(propConflictDescr);
            }

            String conflictOld = (String) result.get(SVNDbTableField.conflict_old);
            String conflictNew = (String) result.get(SVNDbTableField.conflict_new);
            String conflictWrk = (String) result.get(SVNDbTableField.conflict_working);

            if (conflictOld != null || conflictNew != null || conflictWrk != null) {
                SVNMergeFileSet fileSet = new SVNMergeFileSet(null, null, new File(conflictOld), new File(conflictWrk), null,
                        new File(conflictNew), path, null, null);
                SVNTextConflictDescription textConflictDescr = new SVNTextConflictDescription(fileSet, SVNNodeKind.FILE,
                        SVNConflictAction.EDIT, SVNConflictReason.EDITED);
                conflicts.add(textConflictDescr);
            }
        }

        SVNTreeConflictDescription treeConflictDescription = readTreeConflict(path);
        if (treeConflictDescription != null) {
            conflicts.add(treeConflictDescription);
        }
        return conflicts;
    }

    public SVNWCDbKind readKind(File path, boolean allowMissing) throws SVNException {
        SVNEntryInfo info = null;
        try {
            info = readInfo(path, false, false, true, false, false, false, false, false, false, false, false, false);
            return info.getWCDBKind();
        } catch (SVNException svne) {
            if (allowMissing && svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                return SVNWCDbKind.UNKNOWN;
            }
            throw svne;
        }
    }

    public SVNTreeConflictDescription readTreeConflict(File path) throws SVNException {
        File parent = path.getParentFile();
        String localRelPath = null;
        SVNPristineDirectory pristineDirectory = null;
        try {
            ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(parent, SqlJetTransactionMode.WRITE);
            pristineDirectory = parsedPristineDirectory.myPristineDirectory;
            localRelPath = parsedPristineDirectory.myLocalRelativePath;
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage();
            if (err.getErrorCode() == SVNErrorCode.WC_NOT_WORKING_COPY) {
                return null;
            }
            throw svne;
        }

        verifyPristineDirectoryIsUsable(pristineDirectory);
        SVNWCRoot wcRoot = pristineDirectory.getWCRoot();
        Map<SVNDbTableField, Object> actualNodeResult = (Map<SVNDbTableField, Object>) runSelect(wcRoot.getStorage(), SVNDbTables.actual_node,
                getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath }, SVNSqlJetUtil.OUR_ACTUAL_NODE_FIELDS, null));
        if (actualNodeResult.isEmpty()) {
            return null;
        }

        String treeConflictData = (String) actualNodeResult.get(SVNDbTableField.tree_conflict_data);
        if (treeConflictData == null) {
            return null;
        }

        Map treeConflicts = SVNTreeConflictUtil.readTreeConflicts(parent, treeConflictData);
        return (SVNTreeConflictDescription) treeConflicts.get(path.getName());
    }

    //TODO: temporary API
    public DBResult getDBTemp(File dirPath, boolean alwaysOpen) throws SVNException {
        if (!alwaysOpen) {
            SVNPristineDirectory pristineDir = getOrCreatePristineDirectory(dirPath, false);
            if (pristineDir != null && pristineDir.getWCRoot() != null && pristineDir.getWCRoot().getStorage() != null &&
                    dirPath.equals(pristineDir.getWCRoot().getPath())) {

                return new DBResult(pristineDir.getWCRoot().getStorage(), false);
            }
        }

        return new DBResult(openDB(dirPath, SqlJetTransactionMode.WRITE), true);
    }

    //TODO: temporary API
    public IsDirDeletedResult isDirDeleted(File dirPath) throws SVNException {
        File parentPath = dirPath.getParentFile();
        String baseName = dirPath.getName();
        ParsedPristineDirectory parsedPristineDir = parseLocalAbsPath(parentPath, SqlJetTransactionMode.READ_ONLY);
        String localRelPath = parsedPristineDir.getLocalRelativePath();
        SVNPristineDirectory pristineDir = parsedPristineDir.getPristineDirectory();

        verifyPristineDirectoryIsUsable(pristineDir);

        localRelPath = SVNPathUtil.append(localRelPath, baseName);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();

        Map<SVNDbTableField, Object> baseNodeResult = (Map<SVNDbTableField, Object>) runSelect(wcRoot.getStorage(), SVNDbTables.base_node,
                getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath }, SVNSqlJetUtil.OUR_PARENT_STUB_INFO_FIELDS, null));
        String presence = (String) baseNodeResult.get(SVNDbTableField.presence);
        SVNWCDbStatus status = SVNWCDbStatus.parseStatus(presence);
        boolean isNotPresent = status == SVNWCDbStatus.NOT_PRESENT;
        long baseRevision = SVNRepository.INVALID_REVISION;
        if (isNotPresent) {
            baseRevision = (Long) baseNodeResult.get(SVNDbTableField.revnum);
        }

        return new IsDirDeletedResult(isNotPresent, baseRevision);
    }

    public SVNPristineDirectory getOrCreatePristineDirectory(File path, boolean create) {
        SVNPristineDirectory pristineDir = getPristineDirectory(path);
        if (pristineDir == null && create) {
            pristineDir = new SVNPristineDirectory(null, null, false, false, path);
            setPristineDir(path, pristineDir);
        }
        return pristineDir;
    }

    public void closeDB() throws SVNException {
        if (myPathsToPristineDirs != null) {
            for (File path : myPathsToPristineDirs.keySet()) {
                SVNPristineDirectory pristineDir = myPathsToPristineDirs.get(path);
                if (pristineDir.getWCRoot() != null && pristineDir.getWCRoot().getStorage() != null) {
                    pristineDir.getWCRoot().dispose();
                }
            }
            myPathsToPristineDirs.clear();
        }
    }

    public void initDB(File path, String reposRoot, String reposPath, String reposUUID, long initialRevision, SVNDepth depth) throws SVNException {
        if (depth == null || depth == SVNDepth.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "depth must be a valid value");
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SqlJetDb db = createDB(path, reposRoot, reposUUID);
        long wcId = myCurrentWCId;
        long reposId = myCurrentReposId;
        SVNWCRoot wcRoot = createWCRoot(path, db, FORMAT_FROM_SDB, wcId, false, false);

        SVNPristineDirectory pDir = new SVNPristineDirectory(wcRoot, null, false, false, path);
        Map dirData = getPathsToPristineDirs();
        dirData.put(path, pDir);

        SVNWCDbStatus status = SVNWCDbStatus.NORMAL;

        if (initialRevision > 0) {
            status = SVNWCDbStatus.INCOMPLETE;
        }

        SVNBaseNode baseNode = new SVNBaseNode();
        baseNode.setStatus(status);
        baseNode.setWCDbKind(SVNWCDbKind.DIR);
        baseNode.setWCId(wcId);
        baseNode.setReposId(reposId);
        baseNode.setReposPath(reposPath);
        baseNode.setLocalRelativePath("");
        baseNode.setRevision(initialRevision);
        baseNode.setChangedRevision(SVNRepository.INVALID_REVISION);
        baseNode.setDepth(depth);
        insertBaseNode(baseNode, db);
    }

    public ParsedPristineDirectory parseLocalAbsPath(File path, SqlJetTransactionMode mode) throws SVNException {
        mode = SqlJetTransactionMode.WRITE;

        ParsedPristineDirectory result = new ParsedPristineDirectory();
        SVNPristineDirectory pristineDir = getPristineDirectory(path);
        if (pristineDir != null && pristineDir.getWCRoot() != null) {
            String relPath = pristineDir.computeRelPath();
            result.myPristineDirectory = pristineDir;
            result.myLocalRelativePath = relPath;
            return result;
        }

        boolean alwaysCheck = false;
        boolean isObstructionPossible = false;
        boolean movedUpwards = false;
        String buildRelPath = null;

        SVNFileType type = SVNFileType.getType(path);
        if (type != SVNFileType.DIRECTORY) {
            File parent = path.getParentFile();
            String name = path.getName();
            pristineDir = getPristineDirectory(parent);
            if (pristineDir != null && pristineDir.getWCRoot() != null) {
                String dirRelPath = pristineDir.computeRelPath();
                result.myLocalRelativePath = SVNPathUtil.append(dirRelPath, name);
                result.myPristineDirectory = pristineDir;
                return result;
            }

            if (type == SVNFileType.NONE) {
                alwaysCheck = true;
            }
        } else {
            buildRelPath = "";
            isObstructionPossible = true;
        }

        if (pristineDir == null) {
            pristineDir = new SVNPristineDirectory(null, null, false, false, path);
        } else {
            SVNErrorManager.assertionFailure(pristineDir.getPath().equals(path), null, SVNLogType.WC);
        }

        result.myPristineDirectory = pristineDir;

        SqlJetDb sdb = null;
        int wcFormat = 0;
        File originalPath = path;
        SVNPristineDirectory foundDir = null;
        while (true) {
            try {
                sdb = openDB(path, mode);
                break;
            } catch (SVNException svne) {
                SVNErrorMessage err = svne.getErrorMessage();
                if (err.getErrorCode() != SVNErrorCode.SQLITE_ERROR) {
                    throw svne;
                }
            }

            if (!movedUpwards || alwaysCheck) {
                wcFormat = SVNAdminUtil.getVersion(path);
                if (wcFormat != 0) {
                    break;
                }
            }

            if (path.getParentFile() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_WORKING_COPY, "''{0}'' is not a working copy", originalPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            path = path.getParentFile();
            movedUpwards = true;
            isObstructionPossible = false;
            foundDir = getPristineDirectory(path);
            if (foundDir != null) {
                if (foundDir.getWCRoot() != null) {
                    break;
                }
                foundDir = null;
            }
        }

        if (foundDir != null) {
            pristineDir.setWCRoot(foundDir.getWCRoot());
        } else if (wcFormat == 0) {
            long wcId = -1;
            try {
                wcId = fetchWCId(sdb);
            } catch (SVNException e) {
                SVNErrorMessage err = e.getErrorMessage();
                if (err.getErrorCode() == SVNErrorCode.WC_CORRUPT) {
                    SVNErrorManager.error(err.wrap("Missing a row in WCROOT for ''{0}''.", originalPath), SVNLogType.WC);
                }
                throw e;
            }

            SVNWCRoot wcRoot = createWCRoot(path, sdb, FORMAT_FROM_SDB, wcId, myIsAutoUpgrade, myIsEnforceEmptyWorkQueue);
            pristineDir.setWCRoot(wcRoot);
        } else {
            SVNWCRoot wcRoot = createWCRoot(path, sdb, wcFormat, UNKNOWN_WC_ID, myIsAutoUpgrade, myIsEnforceEmptyWorkQueue);
            pristineDir.setWCRoot(wcRoot);
            isObstructionPossible = false;
        }

        String dirRelPath = pristineDir.computeRelPath();
        result.myLocalRelativePath = SVNPathUtil.append(dirRelPath, buildRelPath);

        if (isObstructionPossible) {
            SVNErrorManager.assertionFailure(!movedUpwards, null, SVNLogType.WC);

            File parentPath = path.getParentFile();
            SVNPristineDirectory parentDir = getPristineDirectory(parentPath);
            if (parentDir == null || parentDir.getWCRoot() == null) {
                boolean errorOccured = false;
                try {
                    sdb = openDB(parentPath, mode);
                } catch (SVNException e) {
                    SVNErrorMessage err = e.getErrorMessage();
                    if (err.getErrorCode() != SVNErrorCode.SQLITE_ERROR) {
                        throw e;
                    }
                    parentDir = null;
                    errorOccured = true;
                }

                if (!errorOccured) {
                    if (parentDir == null) {
                        parentDir = new SVNPristineDirectory(null, null, false, false, parentPath);
                    } else {
                        SVNErrorManager.assertionFailure(parentPath.equals(parentDir.getPath()), null, SVNLogType.WC);
                    }

                    SVNWCRoot wcRoot = createWCRoot(parentDir.getPath(), sdb, 1, FORMAT_FROM_SDB, myIsAutoUpgrade, myIsEnforceEmptyWorkQueue);
                    parentDir.setWCRoot(wcRoot);
                    setPristineDir(parentDir.getPath(), parentDir);
                    pristineDir.setParentDirectory(parentDir);
                }
            }

            if (parentDir != null) {
                String lookForRelPath = path.getName();
                boolean isObstructedFile = isObstructedFile(parentDir.getWCRoot(), lookForRelPath);
                pristineDir.setIsObstructedFile(isObstructedFile);
                if (isObstructedFile) {
                    pristineDir = parentDir;
                    result.myLocalRelativePath = lookForRelPath;
                    return result;
                }
            }
        }

        setPristineDir(pristineDir.getPath(), pristineDir);
        if (!movedUpwards) {
            return result;
        }

        SVNPristineDirectory childDir = pristineDir;
        do {
            File parentPath = childDir.getPath().getParentFile();
            SVNPristineDirectory parentDir = getPristineDirectory(parentPath);
            if (parentDir == null) {
                parentDir = new SVNPristineDirectory(pristineDir.getWCRoot(), null, false, false, parentPath);
                setPristineDir(parentDir.getPath(), parentDir);
            } else if (parentDir.getWCRoot() == null) {
                parentDir.setWCRoot(pristineDir.getWCRoot());
            }

            childDir.setParentDirectory(parentDir);
            childDir = parentDir;
        } while (childDir != foundDir && !childDir.getPath().equals(path));

        return result;
    }

    public SVNPristineDirectory navigateToParent(SVNPristineDirectory child, SqlJetTransactionMode mode) throws SVNException {
        SVNPristineDirectory parentDir = child.getParentDirectory() ;
        if (parentDir != null && parentDir.getWCRoot() != null) {
            return parentDir;
        }
        File parentPath = child.getPath().getParentFile();

        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(parentPath, mode);
        parentDir = parsedPristineDirectory.myPristineDirectory;
        verifyPristineDirectoryIsUsable(parentDir);
        child.setParentDirectory(parentDir);
        return parentDir;
    }

    public List gatherChildren(File path, boolean baseOnly) throws SVNException {
        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(path, SqlJetTransactionMode.READ_ONLY);
        String localRelPath = parsedPristineDirectory.getLocalRelativePath();
        SVNPristineDirectory pristineDir = parsedPristineDirectory.myPristineDirectory;
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        SqlJetDb db = wcRoot.getStorage();
        Collection childNames = selectChildrenUsingWCIdAndParentRelPath(SVNDbTables.base_node, SVNDbIndexes.i_parent, wcRoot.getWCId(),
                localRelPath, db, null);
        if (!baseOnly) {
            childNames = selectChildrenUsingWCIdAndParentRelPath(SVNDbTables.working_node, SVNDbIndexes.i_working_parent, wcRoot.getWCId(),
                    localRelPath, db, childNames);
        }
        return new LinkedList(childNames);
    }

    public void addBaseDirectory(File path, String reposPath, String reposRootURL, String reposUUID, long revision, SVNProperties props,
            long changedRevision, Date changedDate, String changedAuthor, List children, SVNDepth depth) throws SVNException {

        SVNErrorManager.assertionFailure(reposPath != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(reposUUID != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(SVNRevision.isValidRevisionNumber(revision), null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(SVNRevision.isValidRevisionNumber(changedRevision), null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(props != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(children != null, null, SVNLogType.WC);

        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        SVNPristineDirectory pristineDir = parsedPristineDirectory.getPristineDirectory();
        String localRelPath = parsedPristineDirectory.getLocalRelativePath();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        long reposId = createReposId(wcRoot.getStorage(), reposRootURL, reposUUID);
        SVNBaseNode baseNode = new SVNBaseNode();
        baseNode.setStatus(SVNWCDbStatus.NORMAL);
        baseNode.setWCDbKind(SVNWCDbKind.DIR);
        baseNode.setWCId(wcRoot.getWCId());
        baseNode.setReposId(reposId);
        baseNode.setReposPath(reposPath);
        baseNode.setLocalRelativePath(localRelPath);
        baseNode.setRevision(revision);
        baseNode.setProps(props);
        baseNode.setChangedRevision(changedRevision);
        baseNode.setChangedDate(changedDate);
        baseNode.setChangedAuthor(changedAuthor);
        baseNode.setDepth(depth);
        baseNode.setChildren(children);

        insertBaseNode(baseNode, wcRoot.getStorage());
    }

    public void addBaseFile(File path, String reposPath, String reposRootURL, String reposUUID, long revision, SVNProperties props,
            long changedRevision, Date changedDate, String changedAuthor, SVNChecksum checksum, long translatedSize) throws SVNException {
        SVNErrorManager.assertionFailure(reposPath != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(reposUUID != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(SVNRevision.isValidRevisionNumber(revision), null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(SVNRevision.isValidRevisionNumber(changedRevision), null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(checksum != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(props != null, null, SVNLogType.WC);

        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        String localRelPath = parsedPristineDirectory.getLocalRelativePath();
        SVNPristineDirectory pristineDir = parsedPristineDirectory.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        long reposId = createReposId(wcRoot.getStorage(), reposRootURL, reposUUID);
        SVNBaseNode baseNode = new SVNBaseNode();
        baseNode.setStatus(SVNWCDbStatus.NORMAL);
        baseNode.setWCDbKind(SVNWCDbKind.FILE);
        baseNode.setWCId(wcRoot.getWCId());
        baseNode.setReposId(reposId);
        baseNode.setReposPath(reposPath);
        baseNode.setLocalRelativePath(localRelPath);
        baseNode.setRevision(revision);
        baseNode.setProps(props);
        baseNode.setChangedRevision(changedRevision);
        baseNode.setChangedDate(changedDate);
        baseNode.setChangedAuthor(changedAuthor);
        baseNode.setChecksum(checksum);
        baseNode.setTranslatedSize(translatedSize);
        insertBaseNode(baseNode, wcRoot.getStorage());
    }

    public void addBaseSymlink(File path, String reposPath, String reposRootURL, String reposUUID, long revision, SVNProperties props,
            long changedRevision, Date changedDate, String changedAuthor, String target) throws SVNException {
        SVNErrorManager.assertionFailure(reposPath != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(reposUUID != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(SVNRevision.isValidRevisionNumber(revision), null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(SVNRevision.isValidRevisionNumber(changedRevision), null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(props != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(target != null, null, SVNLogType.WC);

        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        String localRelPath = parsedPristineDirectory.getLocalRelativePath();
        SVNPristineDirectory pristineDir = parsedPristineDirectory.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        long reposId = createReposId(wcRoot.getStorage(), reposRootURL, reposUUID);
        SVNBaseNode baseNode = new SVNBaseNode();
        baseNode.setStatus(SVNWCDbStatus.NORMAL);
        baseNode.setWCDbKind(SVNWCDbKind.SYMLINK);
        baseNode.setWCId(wcRoot.getWCId());
        baseNode.setReposId(reposId);
        baseNode.setReposPath(reposPath);
        baseNode.setLocalRelativePath(localRelPath);
        baseNode.setRevision(revision);
        baseNode.setProps(props);
        baseNode.setChangedRevision(changedRevision);
        baseNode.setChangedDate(changedDate);
        baseNode.setChangedAuthor(changedAuthor);
        baseNode.setTarget(target);
        insertBaseNode(baseNode, wcRoot.getStorage());
    }

    public void addBaseAbsentNode(File path, String reposPath, String reposRootURL, String reposUUID, long revision, SVNWCDbKind kind,
            SVNWCDbStatus status) throws SVNException {
        SVNErrorManager.assertionFailure(reposPath != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(reposUUID != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(SVNRevision.isValidRevisionNumber(revision), null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(status == SVNWCDbStatus.ABSENT || status == SVNWCDbStatus.EXCLUDED ||
                status == SVNWCDbStatus.NOT_PRESENT, null, SVNLogType.WC);

        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        String localRelPath = parsedPristineDirectory.getLocalRelativePath();
        SVNPristineDirectory pristineDir = parsedPristineDirectory.getPristineDirectory();

        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        long reposId = createReposId(wcRoot.getStorage(), reposRootURL, reposUUID);

        SVNBaseNode baseNode = new SVNBaseNode();
        baseNode.setStatus(status);
        baseNode.setWCDbKind(kind);
        baseNode.setWCId(wcRoot.getWCId());
        baseNode.setReposId(reposId);
        baseNode.setReposPath(reposPath);
        baseNode.setLocalRelativePath(localRelPath);
        baseNode.setRevision(revision);
        baseNode.setChangedRevision(SVNRepository.INVALID_REVISION);
        baseNode.setChangedDate(SVNDate.NULL);
        insertBaseNode(baseNode, wcRoot.getStorage());
    }

    //TODO: this must be removed by the release
    public void addTmpBaseSubDirectory(File path, String reposPath, String reposRootURL, String reposUUID, long revision,
            long changedRevision, Date changedDate, String changedAuthor, SVNDepth depth) throws SVNException {
        SVNErrorManager.assertionFailure(reposPath != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(reposUUID != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(SVNRevision.isValidRevisionNumber(revision), null, SVNLogType.WC);

        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        String localRelPath = parsedPristineDirectory.getLocalRelativePath();
        SVNPristineDirectory pristineDir = parsedPristineDirectory.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        long reposId = createReposId(wcRoot.getStorage(), reposRootURL, reposUUID);

        SVNBaseNode baseNode = new SVNBaseNode();
        baseNode.setStatus(SVNWCDbStatus.NORMAL);
        baseNode.setWCDbKind(SVNWCDbKind.SUBDIR);
        baseNode.setWCId(wcRoot.getWCId());
        baseNode.setReposId(reposId);
        baseNode.setReposPath(reposPath);
        baseNode.setLocalRelativePath(localRelPath);
        baseNode.setRevision(revision);
        baseNode.setChangedRevision(changedRevision);
        baseNode.setChangedDate(changedDate);
        baseNode.setChangedAuthor(changedAuthor);
        baseNode.setDepth(depth);

        insertBaseNode(baseNode, wcRoot.getStorage());
    }

    public void removeFromBase(File path) throws SVNException {
        ParsedPristineDirectory parsedPristineDirectory = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        String localRelPath = parsedPristineDirectory.getLocalRelativePath();
        SVNPristineDirectory pristineDir = parsedPristineDirectory.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        SqlJetDb db = wcRoot.getStorage();
        runDelete(db, SVNDbTables.base_node, getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath }, null, null), null);
        //TODO: flush entries;
    }

    public void installPristine(File tmpPath, SVNChecksum sha1Checksum, SVNChecksum md5Checksum) throws SVNException {
        SVNErrorManager.assertionFailure(sha1Checksum != null, null, SVNLogType.WC);
        SVNErrorManager.assertionFailure(md5Checksum != null, null, SVNLogType.WC);
    }

    public void addWorkQueue(File wriPath, SVNSkel workItem) throws SVNException {
        SVNErrorManager.assertionFailure(workItem != null, null, SVNLogType.WC);
        ParsedPristineDirectory parsedPD = parseLocalAbsPath(wriPath, SqlJetTransactionMode.WRITE);
        SVNPristineDirectory pristineDir = parsedPD.getPristineDirectory();
        String localRelPath = parsedPD.getLocalRelativePath();
        verifyPristineDirectoryIsUsable(pristineDir);
        //TODO: remove later when a single db is left only cause this will always be the case for subdirs, if I'm not mistaken
        if (!"".equals(localRelPath)) {
            SVNWCDbKind kind = readKind(wriPath, true);
            if (kind == SVNWCDbKind.DIR) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "There is no work queue for ''{0}''.", wriPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }

        byte[] serializedItem = workItem.unparse();
        Map<SVNDbTableField, Object> fieldsToValues = new HashMap<SVNDbTableField, Object>();
        fieldsToValues.put(SVNDbTableField.work, serializedItem);
        runInsertByFieldNames(pristineDir.getWCRoot().getStorage(), SVNDbTables.work_queue, null, getCommonDbStrategy(null, null, null),
                fieldsToValues);

    }

    public DBResult fetchWorkQueue(long id, File wriPath) throws SVNException {
        ParsedPristineDirectory parsedPD = parseLocalAbsPath(wriPath, SqlJetTransactionMode.READ_ONLY);
        String localRelPath = parsedPD.getLocalRelativePath();
        SVNPristineDirectory pristineDir = parsedPD.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);
        if (!"".equals(localRelPath)) {
            SVNWCDbKind kind = readKind(wriPath, true);
            if (kind == SVNWCDbKind.DIR) {
                return new DBResult(null, 0);
            }
        }
        //TODO: implement
        return null;
    }

    public void setProps(File path, SVNProperties props) throws SVNException {
        ParsedPristineDirectory parsedPD = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        SVNPristineDirectory pristineDir = parsedPD.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        String localRelPath = parsedPD.getLocalRelativePath();
        Map<SVNDbTableField, Object> fieldsToValues = SVNSqlJetUtil.bindProperties(props, null);
        SVNCommonDbStrategy strategy = getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath },
                SVNSqlJetUtil.OUR_PROPERTIES_FIELD, null);
        long affectedRowsConut = runUpdate(wcRoot.getStorage(), SVNDbTables.actual_node, strategy, fieldsToValues);
        if (affectedRowsConut == 1) {
            return;
        }

        fieldsToValues.put(SVNDbTableField.wc_id, wcRoot.getWCId());
        fieldsToValues.put(SVNDbTableField.local_relpath, localRelPath);
        if (!"".equals(localRelPath)) {
            String parentRelPath = SVNPathUtil.removeTail(localRelPath);
            fieldsToValues.put(SVNDbTableField.parent_relpath, parentRelPath);
        }

        strategy.reset(null, null, null);
        runInsertByFieldNames(wcRoot.getStorage(), SVNDbTables.actual_node, null, strategy, fieldsToValues);
    }

    public SVNRepositoryInfo fetchRepositoryInfo(SqlJetDb sdb, long reposId) throws SVNException {
        Map<SVNDbTableField, Object> result = (Map<SVNDbTableField, Object>) runSelect(sdb, SVNDbTables.repository,
                getCommonDbStrategy(new Object[] { reposId }, SVNSqlJetUtil.OUR_SELECT_REPOSITORY_BY_ID_FIELDS, null));
        if (!result.isEmpty()) {
            String root = (String) result.get(SVNDbTableField.root);
            String uuid = (String) result.get(SVNDbTableField.uuid);
            return new SVNRepositoryInfo(root, uuid);
        }
        return null;
    }

    public SVNProperties getBaseDAVCache(File path) throws SVNException {
        Map<SVNDbTableField, Object> result = runSelectForPath(path, SVNDbTables.base_node, SVNSqlJetUtil.OUR_DAV_CACHE_FIELD);
        if (!result.isEmpty()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        return SVNSqlJetUtil.getPropertiesFromBLOB(result.get(SVNDbTableField.dav_cache));
    }

    public void setBaseDAVCache(File path, SVNProperties props) throws SVNException {
        Map<SVNDbTableField, Object> fieldsToValues = SVNSqlJetUtil.bindProperties(props, null);
        runUpdateForPath(path, SVNDbTables.base_node, SVNSqlJetUtil.OUR_DAV_CACHE_FIELD, fieldsToValues);
    }

    public long ensureRepos(File path, String reposRootURL, String reposUUID) throws SVNException {
        ParsedPristineDirectory parsedPD = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        SVNPristineDirectory pristineDir = parsedPD.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);
        return createReposId(pristineDir.getWCRoot().getStorage(), reposRootURL, reposUUID);
    }

    private RepositoryId scanUpwardsForRepository(SVNWCRoot wcRoot, String localRelPath) throws SVNException {
        SqlJetDb sdb = wcRoot.getStorage();
        SVNErrorManager.assertionFailure(sdb != null && wcRoot.getWCId() >= 0, null, SVNLogType.WC);

        String relPathSuffix = "";
        String currentRelPath = localRelPath;
        while ( true ) {
            Map<SVNDbTableField, Object> baseNodeResult = (Map<SVNDbTableField, Object>) runSelect(sdb, SVNDbTables.base_node,
                    getCommonDbStrategy(new Object[] { wcRoot.getWCId(), currentRelPath } , SVNSqlJetUtil.OUR_BASE_NODE_FIELDS, null));
            if (baseNodeResult.isEmpty()) {
                SVNErrorMessage err = null;
                if (!"".equals(relPathSuffix) || "".equals(localRelPath)) {
                    err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Parent(s) of ''{0}'' should have been present.", localRelPath);
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else {
                    err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", localRelPath);
                }
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            Long reposIdObj = (Long) baseNodeResult.get("repos_id");
            if (reposIdObj != null) {
                long reposId = reposIdObj.longValue();
                String reposPath = (String) baseNodeResult.get("repos_relpath");
                SVNErrorManager.assertionFailure(reposPath != null, null, SVNLogType.WC);

                reposPath = SVNPathUtil.append(reposPath, relPathSuffix);
                return new RepositoryId(reposId, reposPath);
            }

            if ("".equals(currentRelPath)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Parent(s) of ''{0}'' should have repository information.", localRelPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            String currentPathName = SVNPathUtil.tail(currentRelPath);
            currentRelPath = SVNPathUtil.removeTail(currentRelPath);
            relPathSuffix = SVNPathUtil.append(currentPathName, relPathSuffix);
        }
    }

    public Map<SVNDbTableField, Object> runSelectForPath(File path, SVNDbTables table, SVNDbTableField[] fields) throws SVNException {
        ParsedPristineDirectory parsedPD = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        SVNPristineDirectory pristineDir = parsedPD.getPristineDirectory();
        String localRelPath = parsedPD.getLocalRelativePath();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        return (Map<SVNDbTableField, Object>)runSelect(wcRoot.getStorage(), table,
                getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath }, fields, null));
    }

    public long runUpdateForPath(File path, SVNDbTables table, SVNDbTableField[] fields,
            Map<SVNDbTableField, Object> fieldsToValues) throws SVNException {
        ParsedPristineDirectory parsedPD = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        SVNPristineDirectory pristineDir = parsedPD.getPristineDirectory();
        String localRelPath = parsedPD.getLocalRelativePath();
        verifyPristineDirectoryIsUsable(pristineDir);
        SVNWCRoot wcRoot = pristineDir.getWCRoot();
        return runUpdate(wcRoot.getStorage(), table, getCommonDbStrategy(new Object[] { wcRoot.getWCId(), localRelPath }, fields, null),
                fieldsToValues);
    }

    public long countAllRows(SqlJetDb sdb, SVNDbTables tableName, final SVNAbstractDbStrategy dbStrategy) throws SVNException {
        SVNDbCommand command = new SVNDbCommand() {

            protected Object execCommand() throws SqlJetException, SVNException {
                return dbStrategy.getCount(getTable());
            }
        };
        return (Long) command.runDbCommand(sdb, tableName, SqlJetTransactionMode.READ_ONLY, false);
    }

    public Object runSelect(SqlJetDb sdb, SVNDbTables tableName, final SVNAbstractDbStrategy dbStrategy) throws SVNException {
        SVNDbCommand command = new SVNDbCommand() {

            public Object execCommand() throws SqlJetException, SVNException {
                return dbStrategy.runSelect(getTable());
            }
        };

        return command.runDbCommand(sdb, tableName, SqlJetTransactionMode.READ_ONLY, false);
    }

    public void runSelect(SqlJetDb sdb, SVNDbTables tableName, final SVNAbstractDbStrategy dbStrategy, final ISVNRecordHandler handler) throws SVNException {
        SVNDbCommand command = new SVNDbCommand() {

            public Object execCommand() throws SqlJetException, SVNException {
                dbStrategy.runSelect(getTable(), handler);
                return null;
            }
        };
        command.runDbCommand(sdb, tableName, SqlJetTransactionMode.READ_ONLY, false);
    }

    public void runDelete(SqlJetDb sdb, SVNDbTables tableName, final SVNAbstractDbStrategy dbStrategy, final ISVNRecordHandler handler) throws SVNException {

        SVNDbCommand command = new SVNDbCommand() {

            public Object execCommand() throws SqlJetException, SVNException {
                dbStrategy.runDelete(getTable(), handler);
                return null;
            }
        };
        command.runDbCommand(sdb, tableName, SqlJetTransactionMode.WRITE, true);
    }

    public long runInsertByFieldNames(SqlJetDb sdb, SVNDbTables tableName, final SqlJetConflictAction conflictAction,
            final SVNAbstractDbStrategy dbStrategy, final Map<SVNDbTableField, Object> fieldsToValues) throws SVNException {
        SVNDbCommand command = new SVNDbCommand() {

            public Object execCommand() throws SqlJetException, SVNException {
                return dbStrategy.runInsertByFieldNames(getTable(), conflictAction, fieldsToValues);
            }
        };

        return (Long) command.runDbCommand(sdb, tableName, SqlJetTransactionMode.WRITE, true);
    }

    public long runUpdate(SqlJetDb sdb, SVNDbTables tableName, final SVNAbstractDbStrategy dbStrategy,
            final Map<SVNDbTableField, Object> fieldsToValues) throws SVNException {
        SVNDbCommand command = new SVNDbCommand() {

            protected Object execCommand() throws SqlJetException, SVNException {
                return dbStrategy.runUpdate(getTable(), fieldsToValues);
            }
        };

        return (Long) command.runDbCommand(sdb, tableName, SqlJetTransactionMode.WRITE, true);
    }

    private long runInsert(SqlJetDb sdb, SVNDbTables tableName, final SqlJetConflictAction conflictAction,
            final SVNAbstractDbStrategy dbStrategy, final Object... values) throws SVNException {

        SVNDbCommand command = new SVNDbCommand() {

            public Object execCommand() throws SqlJetException, SVNException {
                return dbStrategy.runInsert(getTable(), conflictAction, values);
            }
        };

        return (Long) command.runDbCommand(sdb, tableName, SqlJetTransactionMode.WRITE, true);
    }

    private Collection selectChildrenUsingWCIdAndParentRelPath(SVNDbTables table, SVNDbIndexes index, long wcId, String parentRelPath,
            SqlJetDb db, Collection childNames) throws SVNException {
        childNames = childNames == null ? new HashSet() : childNames;
        final Collection finalChildNames = childNames;

        runSelect(db, table, getCommonDbStrategy(new Object[] { wcId, parentRelPath }, null, index), new ISVNRecordHandler() {

            public void handleRecord(ISqlJetCursor recordCursor) throws SqlJetException {
                String childRelPath = recordCursor.getString(SVNDbTableField.local_relpath.toString());
                String childName = SVNPathUtil.tail(childRelPath);
                finalChildNames.add(childName);
            }
        });

        return childNames;
    }

    private void verifyPristineDirectoryIsUsable(SVNPristineDirectory pristineDirectory) throws SVNException {
        SVNErrorManager.assertionFailure(pristineDirectory != null && pristineDirectory.getWCRoot() != null &&
                pristineDirectory.getWCRoot().getFormat() == WC_FORMAT_17, "an unusable pristine directory object met",
                SVNLogType.WC);
    }

    private void verifyChecksumKind(SVNChecksum checksum) throws SVNException {
        if (checksum.getKind() != SVNChecksumKind.SHA1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_CHECKSUM_KIND,
                    "Only SHA1 checksums can be used as keys in the pristine file storage.");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }

    private boolean isObstructedFile(SVNWCRoot wcRoot, String localRelativePath) throws SVNException {
        SVNErrorManager.assertionFailure(wcRoot != null && wcRoot.getStorage() != null && wcRoot.getFormat() != FORMAT_FROM_SDB, null,
                SVNLogType.WC);

        SqlJetDb db = wcRoot.getStorage();
        Object[] lookUpObjects = new Object[] { wcRoot.getWCId(), localRelativePath };
        Map<SVNDbTableField, Object> result = (Map<SVNDbTableField, Object>) runSelect(db, SVNDbTables.working_node,
                getCommonDbStrategy(lookUpObjects, SVNSqlJetUtil.OUR_KIND_FIELD, null));
        if (!result.isEmpty()) {
            String kindStr = (String) result.get(SVNDbTableField.kind);
            return SVNWCDbKind.parseKind(kindStr) == SVNWCDbKind.FILE;
        }

        result = (Map<SVNDbTableField, Object>) runSelect(db, SVNDbTables.base_node, getCommonDbStrategy(lookUpObjects,
                SVNSqlJetUtil.OUR_KIND_FIELD, null));

        if (!result.isEmpty()) {
            String kindStr = (String) result.get(SVNDbTableField.kind);
            return SVNWCDbKind.parseKind(kindStr) == SVNWCDbKind.FILE;
        }
        return false;
    }

    public long fetchWCId(SqlJetDb db) throws SVNException {
        Map<SVNDbTableField, Object> result = (Map<SVNDbTableField, Object>) runSelect(db, SVNDbTables.wcroot,
                getCommonDbStrategy(new Object[] { null }, SVNSqlJetUtil.OUR_ID_FIELD, SVNDbIndexes.i_local_abspath));
        if (!result.isEmpty()) {
            return (Long) result.get(SVNDbTableField.id);
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Missing a row in WCROOT.");
        SVNErrorManager.error(err, SVNLogType.WC);
        return -1;
    }

    private SVNWCRoot createWCRoot(File wcRoot, SqlJetDb db, int format, long wcId, boolean autoUpgrade, boolean enforceEmptyWorkQueue) throws SVNException {
        try {
            if (db != null) {
                format = db.getOptions().getUserVersion();
            }
            SVNErrorManager.assertionFailure(format >= 1, "wc format could not be less than 1", SVNLogType.WC);
            if (format < 4) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT,
                        "Working copy format of ''{0}'' is too old ({1}); please check out your working copy again", new Object[] { wcRoot, String.valueOf(format) });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (format > WC_FORMAT_17) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT,
                        "This client is too old to work with the working copy at\n''{0}'' (format {1}).\nYou need to get a newer Subversion client.",
                        new Object[] { wcRoot, String.valueOf(format) });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (format < WC_FORMAT_17 && autoUpgrade) {
                //TODO: this feature will come here later..
            }
            if (format >= SVN_WC__HAS_WORK_QUEUE && enforceEmptyWorkQueue) {
                verifyThereIsNoWork(db);
            }

            return new SVNWCRoot(format, db, wcRoot, wcId);
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
        return null;
    }

    public void insertActualNode() {

    }

    public void insertBaseNode(SVNBaseNode node, SqlJetDb db) throws SVNException {
        Map<SVNDbTableField, Object> fieldsToValues = new HashMap<SVNDbTableField, Object>();
        String localRelativePath = node.getLocalRelativePath();

        fieldsToValues.put(SVNDbTableField.wc_id, node.getWCId());
        fieldsToValues.put(SVNDbTableField.local_relpath, localRelativePath);
        fieldsToValues.put(SVNDbTableField.repos_id, node.getReposId());
        fieldsToValues.put(SVNDbTableField.repos_relpath, node.getReposPath());

        if (localRelativePath != null && !"".equals(localRelativePath)) {
            fieldsToValues.put(SVNDbTableField.parent_relpath, SVNPathUtil.removeTail(localRelativePath));
        }

        fieldsToValues.put(SVNDbTableField.presence, node.getStatus().toString());
        fieldsToValues.put(SVNDbTableField.kind, node.getWCDbKind().toString());
        fieldsToValues.put(SVNDbTableField.revnum, node.getRevision());

        SVNProperties props = node.getProperties();
        byte[] propsBlob = null;
        if (props != null) {
            SVNSkel skel = SVNSkel.createPropList(props.asMap());
            propsBlob = skel.unparse();
        }

        fieldsToValues.put(SVNDbTableField.properties, propsBlob);
        if (SVNRevision.isValidRevisionNumber(node.getChangedRevision())) {
            fieldsToValues.put(SVNDbTableField.changed_rev, node.getChangedRevision());
        }

        if (node.getChangedDate() != null) {
            fieldsToValues.put(SVNDbTableField.changed_date, node.getChangedDate().getTime());
        }

        fieldsToValues.put(SVNDbTableField.changed_author, node.getChangedAuthor());

        if (node.getWCDbKind() == SVNWCDbKind.DIR) {
            fieldsToValues.put(SVNDbTableField.depth, SVNDepth.asString(node.getDepth()));
        } else if (node.getWCDbKind() == SVNWCDbKind.FILE) {
            fieldsToValues.put(SVNDbTableField.checksum, node.getChecksum().toString());
            if (node.getTranslatedSize() > 0) {
                fieldsToValues.put(SVNDbTableField.translated_size, node.getTranslatedSize());
            }
        } else if (node.getWCDbKind() == SVNWCDbKind.SYMLINK) {
            if (node.getTarget() != null) {
                fieldsToValues.put(SVNDbTableField.symlink_target, node.getTarget());
            }
        }

        runInsertByFieldNames(db, SVNDbTables.base_node, SqlJetConflictAction.REPLACE, getCommonDbStrategy(null, null, null), fieldsToValues);

        if (node.getWCDbKind() == SVNWCDbKind.DIR && node.hasChildren()) {
            List children = node.getChildren();
            for (ListIterator childIter = children.listIterator(children.size()); childIter.hasPrevious();) {
                String childName = (String) childIter.previous();
                fieldsToValues.clear();
                fieldsToValues.put(SVNDbTableField.wc_id, node.getWCId());
                fieldsToValues.put(SVNDbTableField.local_relpath, SVNPathUtil.append(node.getLocalRelativePath(), childName));
                fieldsToValues.put(SVNDbTableField.presence, "incomplete");
                fieldsToValues.put(SVNDbTableField.kind, "unknown");
                fieldsToValues.put(SVNDbTableField.revnum, node.getRevision());
                fieldsToValues.put(SVNDbTableField.parent_relpath, node.getLocalRelativePath());

                runInsertByFieldNames(db, SVNDbTables.base_node, SqlJetConflictAction.IGNORE, getCommonDbStrategy(null, null, null),
                        fieldsToValues);
            }
        }
    }

    private void verifyThereIsNoWork(SqlJetDb db) throws SVNException {
        Map<SVNDbTableField, Object> result = (Map<SVNDbTableField, Object>) runSelect(db, SVNDbTables.work_queue,
                getCommonDbStrategy(null, SVNSqlJetUtil.OUR_ID_FIELD, null, 1));

        if (!result.isEmpty()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CLEANUP_REQUIRED);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }

    private SqlJetDb createDB(File dirPath, String reposRoot, String reposUUID) throws SVNException {
        SqlJetDb db = openDB(dirPath, SqlJetTransactionMode.WRITE);
        myCurrentReposId = createReposId(db, reposRoot, reposUUID);

        Map<SVNDbTableField, Object> fieldsToValues = new HashMap<SVNDbTableField, Object>();
        //TODO: this may require a review later
        fieldsToValues.put(SVNDbTableField.local_abspath, null);
        myCurrentWCId = runInsertByFieldNames(db, SVNDbTables.wcroot, null, getCommonDbStrategy(null, null, null), fieldsToValues);
        return db;
    }

    private long createReposId(SqlJetDb db, String reposRoot, String reposUUID) throws SVNException {
        Map<SVNDbTableField, Object> result = (Map<SVNDbTableField, Object>) runSelect(db, SVNDbTables.repository,
                getCommonDbStrategy(new Object[] { reposRoot }, SVNSqlJetUtil.OUR_ID_FIELD, SVNDbIndexes.i_root));
        if (!result.isEmpty()) {
            return (Long) result.get(SVNDbTableField.id);
        }
        return runInsert(db, SVNDbTables.repository, null, getCommonDbStrategy(null, null, null), reposRoot, reposUUID);
    }

    private SqlJetDb openDB(File dirPath, SqlJetTransactionMode mode) throws SVNException {
        File sdbFile = SVNAdminUtil.getSDBFile(dirPath);
        if (!sdbFile.isFile()) {
            //hack
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        ISqlJetTransaction openTxn = new ISqlJetTransaction() {

            public Object run(SqlJetDb db) throws SqlJetException {

                int version = db.getOptions().getUserVersion();
                if (version < WC_FORMAT_17) {
                    db.getOptions().setAutovacuum(true);
                    db.runWriteTransaction(new ISqlJetTransaction() {
                        public Object run(SqlJetDb db) throws SqlJetException {
                            //TODO: correct this later when SQLJet dependency gets updated.
                            SqlJetDefaultBusyHandler busyHandler = new SqlJetDefaultBusyHandler(10, 1000);
                            db.setBusyHandler(busyHandler);

                            db.getOptions().setUserVersion(WC_FORMAT_17);
                            InputStream commandsStream = null;
                            try {
                                commandsStream = SVNWorkingCopyDB17.class.getClassLoader().getResourceAsStream("wc-metadata.sql");
                                if (commandsStream != null) {
                                    BufferedReader reader = new BufferedReader(new InputStreamReader(commandsStream));
                                    String line = null;
                                    while ((line = reader.readLine()) != null) {
                                        line = line.trim();
                                        if (line.length() == 0) {
                                            continue;
                                        }

                                        if (line.startsWith("CREATE TABLE")) {
                                            db.createTable(line);
                                        } else if (line.startsWith("CREATE INDEX")) {
                                            db.createIndex(line);
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                throw new SqlJetException(SqlJetErrorCode.IOERR, e);
                            } finally {
                                SVNFileUtil.closeFile(commandsStream);
                            }
                            return null;
                        }
                    });
                } else if (version > WC_FORMAT_17) {
                    throw new SqlJetException("Schema format " + version + " not recognized");
                }
                return null;
            }
        };

        return SVNSqlJetUtil.openDB(sdbFile, openTxn, mode, WC_FORMAT_17);
    }

    private SVNPristineDirectory getPristineDirectory(File path) {
        if (myPathsToPristineDirs != null) {
            return myPathsToPristineDirs.get(path);
        }
        return null;
    }

    private Map getPathsToPristineDirs() {
        if (myPathsToPristineDirs == null) {
            myPathsToPristineDirs = new HashMap();
        }
        return myPathsToPristineDirs;
    }

    private SVNSelectActualConflictVictimsStrategy getSelectActualConflictVictimsStrategy(long wcId, String parentRelPath) {
        if (mySelectActualConflictVictimsStrategy == null) {
            mySelectActualConflictVictimsStrategy = new SVNSelectActualConflictVictimsStrategy(wcId, parentRelPath);
        } else {
            mySelectActualConflictVictimsStrategy.reset(wcId, parentRelPath);
        }
        return mySelectActualConflictVictimsStrategy;
    }

    private SVNWCRoot getWCRootForPath(File path) throws SVNException {
        ParsedPristineDirectory parsedPD = parseLocalAbsPath(path, SqlJetTransactionMode.WRITE);
        SVNPristineDirectory pristineDir = parsedPD.getPristineDirectory();
        verifyPristineDirectoryIsUsable(pristineDir);
        return pristineDir.getWCRoot();
    }

    public SVNCommonDbStrategy getCommonDbStrategy(Object[] lookUpObjects, SVNDbTableField[] fields, SVNDbIndexes index) {
        return getCommonDbStrategy(lookUpObjects, fields, index, -1);
    }

    public SVNCommonDbStrategy getCommonDbStrategy(Object[] lookUpObjects, SVNDbTableField[] fields, SVNDbIndexes index, long limit) {
        return new SVNCommonDbStrategy(index, lookUpObjects, fields, limit);
        /*if (myCommonDbStrategy == null) {
            myCommonDbStrategy = new SVNCommonDbStrategy(index, lookUpObjects, fields);
        } else {
            myCommonDbStrategy.reset(index, lookUpObjects, fields);
        }
        return myCommonDbStrategy;*/
    }

    private void setPristineDir(File path, SVNPristineDirectory dir) {
        Map pathsToPristineDirs = getPathsToPristineDirs();
        pathsToPristineDirs.put(path, dir);
    }

    //TODO: temporary class
    public static class IsDirDeletedResult {
        private boolean myIsDeleted;
        private long myRevision;

        public IsDirDeletedResult(boolean isDeleted, long revision) {
            myIsDeleted = isDeleted;
            myRevision = revision;
        }

        public boolean isDeleted() {
            return myIsDeleted;
        }

        public long getRevision() {
            return myRevision;
        }

    }

    static class ParsedPristineDirectory {
        private SVNPristineDirectory myPristineDirectory;
        private String myLocalRelativePath;

        public SVNPristineDirectory getPristineDirectory() {
            return myPristineDirectory;
        }

        public String getLocalRelativePath() {
            return myLocalRelativePath;
        }
    }

    static class RepositoryId {
        private long myReposId;
        private String myReposPath;

        public RepositoryId(long reposId, String reposPath) {
            myReposId = reposId;
            myReposPath = reposPath;
        }

        public long getReposId() {
            return myReposId;
        }

        public String getReposPath() {
            return myReposPath;
        }

    }

    public boolean isWCLocked(File path) {
        // TODO
        return false;
    }
}
