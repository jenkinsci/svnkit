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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.internal.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.sqljet.core.table.SqlJetDefaultBusyHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWorkingCopyDB17 implements ISVNWorkingCopyDB {
    
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
    
    private static final String BASE_NODE_TABLE = "BASE_NODE";
    private static final String PARENT_INDEX = "I_PARENT";
    private static final String WORKING_NODE_TABLE = "WORKING_NODE";
    private static final String WORKING_PARENT_INDEX = "I_WORKING_PARENT";
    private static final String ROOT_INDEX = "I_ROOT";
    private static final String UUID_INDEX = "I_UUID";
    private static final String REPOSITORY_TABLE = "REPOSITORY";
    private static final String WCROOT_TABLE = "WCROOT";
    private static final String LOCAL_ABSOLUTE_PATH_INDEX = "I_LOCAL_ABSPATH";
    private static final String PRISTINE_TABLE = "PRISTINE";
    private static final String ACTUAL_NODE_TABLE = "ACTUAL_NODE";
    private static final String ACTUAL_PARENT_INDEX = "I_ACTUAL_PARENT";
    private static final String ACTUAL_CHANGELIST_INDEX = "I_ACTUAL_CHANGELIST";
    private static final String LOCK_TABLE = "LOCK";
    private static final String WORK_QUEUE_TABLE = "WORK_QUEUE";
    private static final String WC_LOCK = "WC_LOCK";
    
    private static final String[] SELECT_BASE_NODE_FIELDS = { "repos_id", "repos_relpath", "presence", "kind", "checksum", "translated_size", "changed_rev", 
            "changed_date", "changed_author", "depth", "symlink_target", "last_mod_time", "properties" };
    
    private static final String[] SELECT_LOCK_FIELDS = { "lock_token", "lock_owner", "lock_comment", "lock_date" };
    
    private static final String[] SELECT_WORKING_NODE_FIELDS = { "presence", "kind", "checksum", "translated_size", "changed_rev", "changed_date", 
        "changed_author", "depth", "symlink_target", "copyfrom_repos_id", "copyfrom_repos_path", "copyfrom_revnum", "moved_here", "moved_to", "last_mod_time", "properties" };
    
    private static final String[] SELECT_ACTUAL_NODE_FIELDS = { "prop_reject", "changelist", "conflict_old", "conflict_new", "conflict_working", "tree_conflict_data", 
        "properties" };
    
    private Map myPathsToPristineDirs;
    private boolean myIsAutoUpgrade;
    private boolean myIsEnforceEmptyWorkQueue;

    //these temp fields are set within this class only
    //and must be used to pass values between internal methods only
    private long myCurrentReposId;
    private long myCurrentWCId;
    
    public SVNWorkingCopyDB17() {
    }
    
    public void readInfo(File path, SVNWCDbLock[] lock, Map entryAttrs, SVNWCDbStatus[] status, SVNWCDbKind[] kind, boolean[] isTextMode, 
            boolean[] isPropsMode, boolean[] isBaseShadowed, boolean[] isConflicted) throws SVNException {
        SVNPristineDirectory[] pristineDir = new SVNPristineDirectory[1];
        String localRelPath = parseLocalAbsPath(path, pristineDir, SqlJetTransactionMode.READ_ONLY);
        verifyPristineDirectoryIsUsable(pristineDir[0]);
        SVNWCRoot wcRoot = pristineDir[0].getWCRoot();
        SqlJetDb sdb = wcRoot.getStorage();
        Map baseNodeResult = selectBaseNode(sdb, wcRoot.getWCId(), localRelPath);
        if (!baseNodeResult.isEmpty() && lock != null) {
            baseNodeResult = selectLockForBase(sdb, baseNodeResult);
        }
        Map workingNodeResult = selectWorkingNode(sdb, wcRoot.getWCId(), localRelPath);
        Map actualNodeResult = selectActualNode(sdb, wcRoot.getWCId(), localRelPath);

        boolean haveBase = !baseNodeResult.isEmpty();
        boolean haveWorking = !workingNodeResult.isEmpty();
        boolean haveActual = !actualNodeResult.isEmpty();
        
        String reposRootURL = null;
        String reposUUID = null;
        long revision = SVNRepository.INVALID_REVISION;
        long changedRevision = SVNRepository.INVALID_REVISION;
        long changedDateMillis = -1;
        long lastModTimeMillis = -1;
        long translatedSize = -1;
        long originalRevision = SVNRepository.INVALID_REVISION;
        String reposRelPath = null;
        String changedAuthor = null;
        String checksum = null;
        String target = null;
        String changeList = null;
        String originalReposRelPath = null;
        String originalRootURL = null;
        String originalUUID = null;
        SVNDepth depth = null;
        if (haveBase || haveWorking) {
            SVNWCDbKind nodeKind = null;
            String kindStr = null;
            if (haveWorking) {
                kindStr = (String) workingNodeResult.get("kind");
            } else {
                kindStr = (String) baseNodeResult.get("kind");
            }
            
            nodeKind = SVNWCDbKind.parseKind(kindStr);
            if (status != null) {
                if (haveBase) {
                    String statusStr = (String) baseNodeResult.get("presence");
                    status[0] = SVNWCDbStatus.parseStatus(statusStr);
                    
                    if ((status[0] == SVNWCDbStatus.ABSENT || status[0] == SVNWCDbStatus.EXCLUDED) && haveWorking) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #1 in SVNWorkingCopyDB17.readInfo()");
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                    
                    if (nodeKind == SVNWCDbKind.SUBDIR && status[0] == SVNWCDbStatus.NORMAL) {
                        status[0] = SVNWCDbStatus.OBSTRUCTED;
                    }
                }
                
                if (haveWorking) {
                    String statusStr = (String) workingNodeResult.get("presence");
                    SVNWCDbStatus workStatus = SVNWCDbStatus.parseStatus(statusStr);
                    if (workStatus != SVNWCDbStatus.NORMAL && workStatus != SVNWCDbStatus.NOT_PRESENT && workStatus != SVNWCDbStatus.BASE_DELETED && 
                            workStatus != SVNWCDbStatus.INCOMPLETE) {
                        if (workStatus == SVNWCDbStatus.INCOMPLETE) {
                            status[0] = SVNWCDbStatus.INCOMPLETE;
                        } else if (workStatus == SVNWCDbStatus.NOT_PRESENT || workStatus == SVNWCDbStatus.BASE_DELETED) {
                            if (nodeKind == SVNWCDbKind.SUBDIR) {
                                status[0] = SVNWCDbStatus.OBSTRUCTED_DELETE;
                            } else {
                                status[0] = SVNWCDbStatus.DELETED;
                            }
                        } else {
                            if (nodeKind == SVNWCDbKind.SUBDIR) {
                                status[0] = SVNWCDbStatus.OBSTRUCTED_ADD;
                            } else {
                                status[0] = SVNWCDbStatus.ADDED;
                            }
                        }
                    }
                }
            }
            
            if (kind != null) {
                if (nodeKind == SVNWCDbKind.SUBDIR) {
                    kind[0] = SVNWCDbKind.DIR;
                } else {
                    kind[0] = nodeKind;
                }
            }
            
            //repository root url, repository uuid
            Long reposId = (Long) baseNodeResult.get("repos_id");

            if (!haveWorking) {
                //revision
                revision = (Long) baseNodeResult.get("revnum");
            
                //repository relative path
                reposRelPath = (String) baseNodeResult.get("repos_relpath");
                
                if (reposId != null) {
                    RepositoryInfo reposInfo = fetchRepositoryInfo(sdb, reposId);
                    reposRootURL = reposInfo.myRoot;
                    reposUUID = reposInfo.myUUID;
                }

                changedRevision = (Long) baseNodeResult.get("changed_rev");
                changedDateMillis = (Long) baseNodeResult.get("changed_date");
                changedAuthor = (String) baseNodeResult.get("changed_author");
                lastModTimeMillis = (Long) baseNodeResult.get("last_mod_time");
                
            } else {
                changedRevision = (Long) workingNodeResult.get("changed_rev");
                changedDateMillis = (Long) workingNodeResult.get("changed_date");
                changedAuthor = (String) workingNodeResult.get("changed_author");
                lastModTimeMillis = (Long) workingNodeResult.get("last_mod_time");
                originalReposRelPath = (String) workingNodeResult.get("copyfrom_repos_path");
                Long copyFromReposIdObj = (Long) workingNodeResult.get("copyfrom_repos_id");
                if (copyFromReposIdObj != null) {
                    RepositoryInfo reposInfo = fetchRepositoryInfo(sdb, copyFromReposIdObj.longValue());
                    originalRootURL = reposInfo.myRoot;
                    originalUUID = reposInfo.myUUID;
                }
                originalRevision = (Long) workingNodeResult.get("copyfrom_revnum");
            }
            
            if (nodeKind != SVNWCDbKind.DIR && nodeKind != SVNWCDbKind.SUBDIR) {
                depth = SVNDepth.UNKNOWN;
            } else {
                String depthStr = null;
                if (haveWorking) {
                    depthStr = (String) workingNodeResult.get("depth");
                } else {
                    depthStr = (String) baseNodeResult.get("depth");
                }
                
                if (depthStr == null) {
                    depth = SVNDepth.UNKNOWN;
                } else {
                    depth = SVNDepth.fromString(depthStr);
                }
            }
            
            if (nodeKind == SVNWCDbKind.FILE) {
                if (haveWorking) {
                    checksum = (String) workingNodeResult.get("checksum");
                } else {
                    checksum = (String) baseNodeResult.get("checksum");
                }
                
                //TODO: parse checksum?
            }
            
            Long translatedSizeObj = null;
            if (haveWorking) {
                translatedSizeObj = (Long) workingNodeResult.get("translated_size");
            } else {
                translatedSizeObj = (Long) baseNodeResult.get("translated_size");
            }

            if (translatedSizeObj == null) {
                translatedSize = -1;
            } else {
                translatedSize = translatedSizeObj.longValue();
            }

            if (nodeKind == SVNWCDbKind.SYMLINK) {
                if (haveWorking) {
                    target = (String) workingNodeResult.get("symlink_target");
                } else {
                    target = (String) baseNodeResult.get("symlink_target");
                }
            }
            
            if (haveActual) {
                changeList = (String) actualNodeResult.get("changelist");
            } 
            
            if (isTextMode != null) {
                //TODO: ?
                isTextMode[0] = false;
            }
            
            if (isPropsMode != null) {
                isPropsMode[0] = haveActual && actualNodeResult.get("properties") != null;
            }
            
            if (isBaseShadowed != null) {
                isBaseShadowed[0] = haveBase && haveWorking;
            }
            
            if (isConflicted != null) {
                if (haveActual) {
                    isConflicted[0] = actualNodeResult.get("conflict_old") != null || actualNodeResult.get("conflict_new") != null ||
                                      actualNodeResult.get("conflict_working") != null || actualNodeResult.get("prop_reject") != null;
                    
                } else {
                    isConflicted[0] = false;
                }
            }
            
            if (lock != null) {
                String lockToken = (String) baseNodeResult.get("lock_token");
                if (lockToken == null) {
                    lock[0] = null;
                } else {
                    String owner = (String) baseNodeResult.get("lock_owner");
                    String comment = (String) baseNodeResult.get("lock_comment");
                    Long dateMillis = (Long) baseNodeResult.get("lock_date");
                    lock[0] = new SVNWCDbLock(lockToken, owner, comment, new Date(dateMillis));
                }
            }
        } else if (haveActual) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Corrupt data for ''{0}''", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", path);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (isConflicted != null && !isConflicted[0]) {
            SVNTreeConflictDescription treeConflict = readTreeConflict(path);
            isConflicted[0] = treeConflict != null;
        }
        
        entryAttrs.put(SVNProperty.REVISION, String.valueOf(revision));
        entryAttrs.put(SVNProperty.REPOS, reposRootURL);
        
    }
    
    public SVNTreeConflictDescription readTreeConflict(File path) throws SVNException {
        SVNPristineDirectory[] pristineDir = new SVNPristineDirectory[1];
        File parent = path.getParentFile();
        String localRelPath = null;
        try {
            localRelPath = parseLocalAbsPath(parent, pristineDir, SqlJetTransactionMode.WRITE);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage();
            if (err.getErrorCode() == SVNErrorCode.WC_NOT_WORKING_COPY) {
                return null;
            }
            throw svne;
        }
        
        verifyPristineDirectoryIsUsable(pristineDir[0]);
        SVNWCRoot wcRoot = pristineDir[0].getWCRoot(); 
        Map actualNodeResult = selectActualNode(wcRoot.getStorage(), wcRoot.getWCId(), localRelPath);
        if (actualNodeResult.isEmpty()) {
            return null;
        }
        
        String treeConflictData = (String) actualNodeResult.get("tree_conflict_data");
        if (treeConflictData == null) {
            return null;
        }
        
        Map treeConflicts = SVNTreeConflictUtil.readTreeConflicts(parent, treeConflictData);
        return (SVNTreeConflictDescription) treeConflicts.get(path.getName());
    }
    
    //TODO: temporary API
    public SqlJetDb getDBTemp(File dirPath, boolean alwaysOpen) throws SVNException {
        if (!alwaysOpen) {
            SVNPristineDirectory pristineDir = getOrCreatePristineDirectory(dirPath, false);
            if (pristineDir != null && pristineDir.getWCRoot() != null && pristineDir.getWCRoot().getStorage() != null && 
                    dirPath.equals(pristineDir.getWCRoot().getPath())) {
                return pristineDir.getWCRoot().getStorage();
            }
        }
        
        return openDB(dirPath, SqlJetTransactionMode.WRITE);
    }
    
    public SVNPristineDirectory getOrCreatePristineDirectory(File path, boolean create) {
        SVNPristineDirectory pristineDir = getPristineDirectory(path);
        if (pristineDir == null && create) {
            pristineDir = new SVNPristineDirectory(null, null, false, false, path);
            setPristineDir(path, pristineDir);
        }
        return pristineDir;
    }
    
    public void initDB(File path, String reposRoot, String reposRelativePath, String reposUUID, long initialRevision, SVNDepth depth) throws SVNException {
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
        
        SVNBaseNode baseNode = new SVNBaseNode(status, SVNWCDbKind.DIR, wcId, reposId, reposRelativePath, "", initialRevision, null, SVNRepository.INVALID_REVISION, 
                null, null, depth, null, null, -1, null);
        insertBaseNode(baseNode, db);
    }
    
    public String parseLocalAbsPath(File path, SVNPristineDirectory[] pristineDir, SqlJetTransactionMode mode) throws SVNException {
        mode = SqlJetTransactionMode.WRITE;
        pristineDir[0] = getPristineDirectory(path);
        if (pristineDir[0] != null && pristineDir[0].getWCRoot() != null) {
            String relPath = pristineDir[0].computeRelPath();
            return relPath;
        }
        
        boolean alwaysCheck = false;
        boolean isObstructionPossible = false;
        boolean movedUpwards = false;
        String buildRelPath = null;
        String localRelPath = null;        
        SVNFileType type = SVNFileType.getType(path);
        if (type != SVNFileType.DIRECTORY) {
            File parent = path.getParentFile();
            String name = path.getName();
            pristineDir[0] = getPristineDirectory(parent);
            if (pristineDir[0] != null && pristineDir[0].getWCRoot() != null) {
                String dirRelPath = pristineDir[0].computeRelPath();
                localRelPath = SVNPathUtil.append(dirRelPath, name);
                return localRelPath;
            }
            
            if (type == SVNFileType.NONE) {
                alwaysCheck = true;
            }
        } else {
            buildRelPath = "";
            isObstructionPossible = true;
        }
        
        if (pristineDir[0] == null) {
            pristineDir[0] = new SVNPristineDirectory(null, null, false, false, path);
        } else {
            if (!pristineDir[0].getPath().equals(path)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                        "Assertion failure #1 in SVNWokringCopyDB17.parseLocalAbsPath()");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
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
            pristineDir[0].setWCRoot(foundDir.getWCRoot());
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
            pristineDir[0].setWCRoot(wcRoot);
        } else {
            SVNWCRoot wcRoot = createWCRoot(path, sdb, wcFormat, UNKNOWN_WC_ID, myIsAutoUpgrade, myIsEnforceEmptyWorkQueue);
            pristineDir[0].setWCRoot(wcRoot);
            isObstructionPossible = false;
        }
            
        String dirRelPath = pristineDir[0].computeRelPath();
        localRelPath = SVNPathUtil.append(dirRelPath, buildRelPath);
        
        if (isObstructionPossible) {
            if (movedUpwards) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                        "Assertion failure #2 in SVNWorkingCopyDB17.parseLocalAbsPath()");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
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
                        if (!parentPath.equals(parentDir.getPath())) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                                    "Assertion failure #3 in SVNWorkingCopyDB17.parseLocalAbsPath()");
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }
                    }
                    
                    SVNWCRoot wcRoot = createWCRoot(parentDir.getPath(), sdb, 1, FORMAT_FROM_SDB, myIsAutoUpgrade, myIsEnforceEmptyWorkQueue);
                    parentDir.setWCRoot(wcRoot);
                    setPristineDir(parentDir.getPath(), parentDir);
                    pristineDir[0].setParentDirectory(parentDir);
                }
            }
            
            if (parentDir != null) {
                String lookForRelPath = path.getName();
                boolean isObstructedFile = isObstructedFile(parentDir.getWCRoot(), lookForRelPath);
                pristineDir[0].setIsObstructedFile(isObstructedFile);
                if (isObstructedFile) {
                    pristineDir[0] = parentDir;
                    localRelPath = lookForRelPath;
                    return localRelPath;
                }
            }
        }
        
        setPristineDir(pristineDir[0].getPath(), pristineDir[0]);
        if (!movedUpwards) {
            return localRelPath;
        }
        
        SVNPristineDirectory childDir = pristineDir[0];
        do {
            File parentPath = childDir.getPath().getParentFile();
            SVNPristineDirectory parentDir = getPristineDirectory(parentPath);
            if (parentDir == null) {
                parentDir = new SVNPristineDirectory(pristineDir[0].getWCRoot(), null, false, false, parentPath);
                setPristineDir(parentDir.getPath(), parentDir);
            } else if (parentDir.getWCRoot() == null) {
                parentDir.setWCRoot(pristineDir[0].getWCRoot());
            }
            
            childDir.setParentDirectory(parentDir);
            childDir = parentDir;
        } while (childDir != foundDir && !childDir.getPath().equals(path));
            
        return localRelPath;
    }
    
    public SVNPristineDirectory navigateToParent(File path, SVNPristineDirectory child, SqlJetTransactionMode mode) throws SVNException {
        SVNPristineDirectory[] parentDir = new SVNPristineDirectory[1];
        parentDir[0] = child.getParentDirectory() ;
        if (parentDir[0] != null && parentDir[0].getWCRoot() != null) {
            return parentDir[0];
        }
        File parentPath = child.getPath().getParentFile();
        
        parseLocalAbsPath(parentPath, parentDir, mode);
        verifyPristineDirectoryIsUsable(parentDir[0]);
        child.setParentDirectory(parentDir[0]);
        return parentDir[0];
    }
    
    public List gatherChildren(File path, boolean baseOnly) throws SVNException {
        SVNPristineDirectory[] pristineDir = new SVNPristineDirectory[1];
        String localRelPath = parseLocalAbsPath(path, pristineDir, SqlJetTransactionMode.READ_ONLY);
        verifyPristineDirectoryIsUsable(pristineDir[0]);
        SVNWCRoot wcRoot = pristineDir[0].getWCRoot(); 
        SqlJetDb db = wcRoot.getStorage();
        Collection childNames = selectChildrenUsingWCIdAndParentRelPath(BASE_NODE_TABLE, PARENT_INDEX, wcRoot.getWCId(), localRelPath, db, null);
        if (!baseOnly) {
            childNames = selectChildrenUsingWCIdAndParentRelPath(WORKING_NODE_TABLE, WORKING_PARENT_INDEX, wcRoot.getWCId(), localRelPath, db, childNames);
        }
        return new LinkedList(childNames);
    }
    
    public void addBaseDirectory(File path, String reposRelPath, String reposRootURL, String reposUUID, long revision, SVNProperties props, long changedRevision, 
            Date changedDate, String changedAuthor, List children, SVNDepth depth) throws SVNException {        
        SVNErrorMessage err = null;
        if (reposRelPath == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #1 in SVNWorkingCopyDB17.addBaseDirectory()");
        }
        if (reposUUID == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #2 in SVNWorkingCopyDB17.addBaseDirectory()");
        }
        if (!SVNRevision.isValidRevisionNumber(revision)) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #3 in SVNWorkingCopyDB17.addBaseDirectory()");
        }
        if (!SVNRevision.isValidRevisionNumber(changedRevision)) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #4 in SVNWorkingCopyDB17.addBaseDirectory()");
        }
        if (props == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #5 in SVNWorkingCopyDB17.addBaseDirectory()");
        }
        if (children == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #6 in SVNWorkingCopyDB17.addBaseDirectory()");
        }
        
        if (err != null) {
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNPristineDirectory[] pristineDir = new SVNPristineDirectory[1];
        String localRelPath = parseLocalAbsPath(path, pristineDir, SqlJetTransactionMode.WRITE);
        verifyPristineDirectoryIsUsable(pristineDir[0]);
        SVNWCRoot wcRoot = pristineDir[0].getWCRoot(); 
        long reposId = createReposId(wcRoot.getStorage(), reposRootURL, reposUUID);
        SVNBaseNode baseNode = new SVNBaseNode(SVNWCDbStatus.NORMAL, SVNWCDbKind.DIR, wcRoot.getWCId(), reposId, reposRelPath, localRelPath, revision, props, 
                changedRevision, changedDate, changedAuthor, depth, children, null, -1, null);
        insertBaseNode(baseNode, wcRoot.getStorage());
    }
    
    public void addBaseFile(File path, String reposRelPath, String reposRootURL, String reposUUID, long revision, SVNProperties props, long changedRevision, 
            Date changedDate, String changedAuthor, SVNChecksum checksum, long translatedSize) throws SVNException {
        SVNErrorMessage err = null;
        if (reposRelPath == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #1 in SVNWorkingCopyDB17.addBaseFile()");
        }
        if (reposUUID == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #2 in SVNWorkingCopyDB17.addBaseFile()");
        }
        if (!SVNRevision.isValidRevisionNumber(revision)) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #3 in SVNWorkingCopyDB17.addBaseFile()");
        }
        if (!SVNRevision.isValidRevisionNumber(changedRevision)) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #4 in SVNWorkingCopyDB17.addBaseFile()");
        }
        if (checksum == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #5 in SVNWorkingCopyDB17.addBaseFile()");
        }
        if (props == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #6 in SVNWorkingCopyDB17.addBaseFile()");
        }
        if (err != null) {
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNPristineDirectory[] pristineDir = new SVNPristineDirectory[1];
        String localRelPath = parseLocalAbsPath(path, pristineDir, SqlJetTransactionMode.WRITE);
        verifyPristineDirectoryIsUsable(pristineDir[0]);
        SVNWCRoot wcRoot = pristineDir[0].getWCRoot();
        long reposId = createReposId(wcRoot.getStorage(), reposRootURL, reposUUID);
        SVNBaseNode baseNode = new SVNBaseNode(SVNWCDbStatus.NORMAL, SVNWCDbKind.FILE, wcRoot.getWCId(), reposId, reposRelPath, localRelPath, revision, props, changedRevision, 
                changedDate, changedAuthor, null, null, checksum, translatedSize, null);
        insertBaseNode(baseNode, wcRoot.getStorage());
    }
    
    public void addBaseSymlink(File path, String reposRelPath, String reposRootURL, String reposUUID, long revision, SVNProperties props, long changedRevision, 
            Date changedDate, String changedAuthor, String target) throws SVNException {
        SVNErrorMessage err = null;
        if (reposRelPath == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #1 in SVNWorkingCopyDB17.addBaseSymlink()");
        }
        if (reposUUID == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #2 in SVNWorkingCopyDB17.addBaseSymlink()");
        }
        if (!SVNRevision.isValidRevisionNumber(revision)) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #3 in SVNWorkingCopyDB17.addBaseSymlink()");
        }
        if (!SVNRevision.isValidRevisionNumber(changedRevision)) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #4 in SVNWorkingCopyDB17.addBaseSymlink()");
        }
        if (props == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #5 in SVNWorkingCopyDB17.addBaseSymlink()");
        }
        if (target == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #6 in SVNWorkingCopyDB17.addBaseSymlink()");
        }
        if (err != null) {
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNPristineDirectory[] pristineDir = new SVNPristineDirectory[1];
        String localRelPath = parseLocalAbsPath(path, pristineDir, SqlJetTransactionMode.WRITE);
        verifyPristineDirectoryIsUsable(pristineDir[0]);
        SVNWCRoot wcRoot = pristineDir[0].getWCRoot();
        long reposId = createReposId(wcRoot.getStorage(), reposRootURL, reposUUID);
        SVNBaseNode baseNode = new SVNBaseNode(SVNWCDbStatus.NORMAL, SVNWCDbKind.SYMLINK, wcRoot.getWCId(), reposId, reposRelPath, localRelPath, revision, 
                props, changedRevision, changedDate, changedAuthor, null, null, null, -1, target);
        insertBaseNode(baseNode, wcRoot.getStorage());
    }
    
    public void addBaseAbsentNode(File path, String reposRelPath, String reposRootURL, String reposUUID, long revision, SVNWCDbKind kind, SVNWCDbStatus status) throws SVNException {
        SVNErrorMessage err = null;
        if (reposRelPath == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #1 in SVNWorkingCopyDB17.addBaseAbsentNode()");
        }
        if (reposUUID == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #2 in SVNWorkingCopyDB17.addBaseAbsentNode()");
        }
        if (!SVNRevision.isValidRevisionNumber(revision)) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #3 in SVNWorkingCopyDB17.addBaseAbsentNode()");
        }
        if (status != SVNWCDbStatus.ABSENT && status != SVNWCDbStatus.EXCLUDED && status != SVNWCDbStatus.NOT_PRESENT) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #4 in SVNWorkingCopyDB17.addBaseAbsentNode()");
        }
        if (err != null) {
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNPristineDirectory[] pristineDir = new SVNPristineDirectory[1];
        String localRelPath = parseLocalAbsPath(path, pristineDir, SqlJetTransactionMode.WRITE);
        verifyPristineDirectoryIsUsable(pristineDir[0]);
        SVNWCRoot wcRoot = pristineDir[0].getWCRoot();
        long reposId = createReposId(wcRoot.getStorage(), reposRootURL, reposUUID);
        
        SVNBaseNode baseNode = new SVNBaseNode(status, kind, wcRoot.getWCId(), reposId, reposRelPath, localRelPath, revision, null, SVNRepository.INVALID_REVISION, SVNDate.NULL, 
                null, null, null, null, -1, null);
        insertBaseNode(baseNode, wcRoot.getStorage());
    }

    //TODO: this must be removed by the release
    public void addTmpBaseSubDirectory(File path, String reposRelPath, String reposRootURL, String reposUUID, long revision, 
            long changedRevision, Date changedDate, String changedAuthor, SVNDepth depth) throws SVNException {
        SVNErrorMessage err = null;
        if (reposRelPath == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #1 in SVNWorkingCopyDB17.addTmpBaseSubDirectory()");
        }
        if (reposUUID == null) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #2 in SVNWorkingCopyDB17.addTmpBaseSubDirectory()");
        }
        if (!SVNRevision.isValidRevisionNumber(revision)) {
            err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #3 in SVNWorkingCopyDB17.addTmpBaseSubDirectory()");
        }
        if (err != null) {
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        SVNPristineDirectory[] pristineDir = new SVNPristineDirectory[1];
        String localRelPath = parseLocalAbsPath(path, pristineDir, SqlJetTransactionMode.WRITE);
        verifyPristineDirectoryIsUsable(pristineDir[0]);
        SVNWCRoot wcRoot = pristineDir[0].getWCRoot();
        long reposId = createReposId(wcRoot.getStorage(), reposRootURL, reposUUID);

        SVNBaseNode baseNode = new SVNBaseNode(SVNWCDbStatus.NORMAL, SVNWCDbKind.SUBDIR, wcRoot.getWCId(), reposId, reposRelPath, localRelPath, revision, null, changedRevision, 
                changedDate, changedAuthor, depth, null, null, -1, null);
        insertBaseNode(baseNode, wcRoot.getStorage());
    }
    
    public void removeFromBase(File path) throws SVNException {
        SVNPristineDirectory[] pristineDir = new SVNPristineDirectory[1];
        String localRelPath = parseLocalAbsPath(path, pristineDir, SqlJetTransactionMode.WRITE);
        verifyPristineDirectoryIsUsable(pristineDir[0]);
        SVNWCRoot wcRoot = pristineDir[0].getWCRoot(); 
        SqlJetDb db = wcRoot.getStorage();
        try {
            db.beginTransaction(SqlJetTransactionMode.WRITE);
            try {
                ISqlJetTable table = db.getTable(BASE_NODE_TABLE);
                ISqlJetCursor cursor = table.lookup(table.getPrimaryKeyIndexName(), wcRoot.getWCId(), localRelPath);
                try {
                    while (!cursor.eof()) {
                        cursor.delete();
                    }
                } finally {
                    cursor.close();
                }
            
            } finally {
                db.commit();
            }    
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
        //TODO: flush entries;
    }
    
    public RepositoryInfo fetchRepositoryInfo(SqlJetDb sdb, long reposId) throws SVNException {
        try {
            sdb.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetTable repositoryTable = sdb.getTable(REPOSITORY_TABLE);
                ISqlJetCursor cursor = repositoryTable.lookup(repositoryTable.getPrimaryKeyIndexName(), reposId);
                try {
                    if (!cursor.eof()) {
                        String root = (String) cursor.getValue("root");
                        String uuid = (String) cursor.getValue("uuid");
                        return new RepositoryInfo(root, uuid); 
                    }
                } finally {
                    cursor.close();
                }
            } finally {
                sdb.commit();
            }    
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
        return null;
    }
    
    private Map selectActualNode(SqlJetDb sdb, long wcId, String localRelPath) throws SVNException {
        Map actualNodeResult = null;
        try {
            sdb.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetTable actualNodeTable = sdb.getTable(ACTUAL_NODE_TABLE);
                ISqlJetCursor actualNodeCursor = actualNodeTable.lookup(actualNodeTable.getPrimaryKeyIndexName(), wcId, localRelPath);
                try {
                    if (!actualNodeCursor.eof()) {
                        actualNodeResult = new HashMap();
                        for (String field : SELECT_ACTUAL_NODE_FIELDS) {
                            actualNodeResult.put(field, actualNodeCursor.getValue(field));
                        }
                    } else {
                        actualNodeResult = Collections.EMPTY_MAP;
                    }
                } finally {
                    actualNodeCursor.close();
                }
            } finally {
                sdb.commit();
            }    
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
        return actualNodeResult;
    }
    
    private Map selectLockForBase(SqlJetDb sdb, Map baseNodeResult) throws SVNException {
        try {
            sdb.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetTable lockTable = sdb.getTable(LOCK_TABLE);
                ISqlJetCursor lockCursor = lockTable.lookup(lockTable.getPrimaryKeyIndexName(), baseNodeResult.get("repos_id"), 
                        baseNodeResult.get("repos_relpath"));
                try {
                    if (!lockCursor.eof()) {
                        for (String field : SELECT_LOCK_FIELDS) {
                            baseNodeResult.put(field, lockCursor.getValue(field));
                        }
                    }
                } finally {
                    lockCursor.close();
                }
            } finally {
                sdb.commit();
            }
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }        
        return baseNodeResult;
    }
    
    private Map selectBaseNode(SqlJetDb sdb, long wcId, String localRelPath) throws SVNException {
        Map baseNodeResult = null;
        try {
            sdb.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetTable baseNodeTable = sdb.getTable(BASE_NODE_TABLE);
                ISqlJetCursor baseNodeCursor = baseNodeTable.lookup(baseNodeTable.getPrimaryKeyIndexName(), wcId, localRelPath);
                try {
                    if (!baseNodeCursor.eof()) {
                        baseNodeResult = new HashMap();
                        for(String field : SELECT_BASE_NODE_FIELDS) {
                            baseNodeResult.put(field, baseNodeCursor.getValue(field));
                        }            
                    } else {
                        baseNodeResult = Collections.EMPTY_MAP; 
                    }
                } finally {
                    baseNodeCursor.close();
                }
            } finally {
                sdb.commit();
            }
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }        
        return baseNodeResult;
    }
    
    private Map selectWorkingNode(SqlJetDb sdb, long wcId, String localRelPath) throws SVNException {
        Map workingNodeResult = null;
        try {
            sdb.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetTable workingNodeTable = sdb.getTable(WORKING_NODE_TABLE);
                ISqlJetCursor workingNodeCursor = workingNodeTable.lookup(workingNodeTable.getPrimaryKeyIndexName(), wcId, localRelPath);
                try {
                    if (!workingNodeCursor.eof()) {
                        workingNodeResult = new HashMap();
                        for (String field : SELECT_WORKING_NODE_FIELDS) {
                            workingNodeResult.put(field, workingNodeCursor.getValue(field));
                        }
                    } else {
                        workingNodeResult = Collections.EMPTY_MAP;
                    }
                } finally {
                    workingNodeCursor.close();
                }
            } finally {
                sdb.commit();
            }    
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
        return workingNodeResult;
    }
    
    private Collection selectChildrenUsingWCIdAndParentRelPath(String tableName, String cursorName, long wcId, String parentRelPath, SqlJetDb db, Collection childNames) throws SVNException {
        childNames = childNames == null ? new HashSet() : childNames;
        try {
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetTable table = db.getTable(tableName);
                ISqlJetCursor cursor = table.lookup(cursorName, wcId, parentRelPath);
                try {
                    while (!cursor.eof()) {
                        String childRelPath = cursor.getString("local_relpath");
                        String childName = SVNPathUtil.tail(childRelPath); 
                        childNames.add(childName);
                    }
                } finally {
                    cursor.close();
                }
            } finally {
                db.commit();
            }    
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
        return childNames;
    }
    
    private void verifyPristineDirectoryIsUsable(SVNPristineDirectory pristineDirectory) throws SVNException {
        if (pristineDirectory == null || pristineDirectory.getWCRoot() == null || pristineDirectory.getWCRoot().getFormat() != SVNAdminAreaFactory.SVN_WC_VERSION) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failed: unusable SVNPristineDirectory object met");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
    }
    
    private boolean isObstructedFile(SVNWCRoot wcRoot, String localRelativePath) throws SVNException {
        if (wcRoot == null || wcRoot.getStorage() == null || wcRoot.getFormat() == FORMAT_FROM_SDB) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure #1 in SVNWorkingCopyDB17.isObstructedFile()");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SqlJetDb db = wcRoot.getStorage();
        try {
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetTable table = db.getTable(WORKING_NODE_TABLE);
                ISqlJetCursor cursor = table.lookup(table.getPrimaryKeyIndexName(), wcRoot.getWCId(), localRelativePath);
                try {
                    if (!cursor.eof()) {
                        String kindStr = cursor.getString("kind");
                        return SVNWCDbKind.parseKind(kindStr) == SVNWCDbKind.FILE;
                    }
                } finally {
                    cursor.close();
                }
                
                table = db.getTable(BASE_NODE_TABLE);
                cursor = table.lookup(table.getPrimaryKeyIndexName(), wcRoot.getWCId(), localRelativePath);
                if (!cursor.eof()) {
                    String kindStr = cursor.getString("kind");
                    return SVNWCDbKind.parseKind(kindStr) == SVNWCDbKind.FILE;
                }
            } finally {
                db.commit();
            }    
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
        return false;
    }
    
    private long fetchWCId(SqlJetDb db) throws SVNException {
        try {
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetTable table = db.getTable(WCROOT_TABLE);
                ISqlJetCursor cursor = table.lookup(LOCAL_ABSOLUTE_PATH_INDEX, new Object[] { null });
                try {
                    if (!cursor.eof()) {
                        return cursor.getInteger("id");
                    }
                } finally {
                    cursor.close();
                }
                
            } finally {
                db.commit();
            }    
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
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
            if (format < 1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Assertion failure: wc format could not be less than 1");
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (format < 4) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                        "Working copy format of ''{0}'' is too old ({1}); please check out your working copy again", new Object[] { wcRoot, String.valueOf(format) });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (format > SVNAdminAreaFactory.SVN_WC_VERSION) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                        "This client is too old to work with the working copy at\n''{0}'' (format {1}).\nYou need to get a newer Subversion client.", 
                        new Object[] { wcRoot, String.valueOf(format) });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (format < SVNAdminAreaFactory.SVN_WC_VERSION && autoUpgrade) {
                //TODO: this feature will come here later..
            }
            if (format >= SVNAdminAreaFactory.SVN_WC__HAS_WORK_QUEUE && enforceEmptyWorkQueue) {
                verifyThereIsNoWork(db);
            }
            
            return new SVNWCRoot(format, db, wcRoot, wcId); 
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
        return null;
    }
    
    public void insertBaseNode(SVNBaseNode node, SqlJetDb db) throws SVNException {
        try {
            db.beginTransaction(SqlJetTransactionMode.WRITE);
            try {
                ISqlJetTable table = db.getTable(BASE_NODE_TABLE);
                
                Map fieldsToValues = new HashMap();
                String localRelativePath = node.getLocalRelativePath();
                
                fieldsToValues.put("wc_id", node.getWCId());
                fieldsToValues.put("local_relpath", localRelativePath);
                fieldsToValues.put("repos_id", node.getReposId());
                fieldsToValues.put("repos_relpath", node.getReposRelativePath());

                if (localRelativePath != null && !"".equals(localRelativePath)) {
                    fieldsToValues.put("parent_relpath", SVNPathUtil.removeTail(localRelativePath));
                }
                
                fieldsToValues.put("presence", node.getStatus().toString());
                fieldsToValues.put("kind", node.getKind().toString());
                fieldsToValues.put("revnum", node.getRevision());
                
                SVNProperties props = node.getProperties();
                byte[] propsBlob = null;
                if (props != null) {
                    SVNSkel skel = SVNSkel.createPropList(props.asMap());
                    propsBlob = skel.unparse();
                }
                
                fieldsToValues.put("properties", propsBlob);
                if (SVNRevision.isValidRevisionNumber(node.getChangedRevision())) {
                    fieldsToValues.put("changed_rev", node.getChangedRevision());
                }
                if (node.getChangedDate() != null) {
                    fieldsToValues.put("changed_date", node.getChangedDate().getTime());
                }

                fieldsToValues.put("changed_author", node.getChangedAuthor());
                
                if (node.getKind() == SVNWCDbKind.DIR) {
                    fieldsToValues.put("depth", SVNDepth.asString(node.getDepth()));
                } else if (node.getKind() == SVNWCDbKind.FILE) {
                    fieldsToValues.put("checksum", node.getChecksum().toString());
                    if (node.getTranslatedSize() > 0) {
                        fieldsToValues.put("translated_size", node.getTranslatedSize());
                    }
                } else if (node.getKind() == SVNWCDbKind.SYMLINK) {
                    if (node.getTarget() != null) {
                        fieldsToValues.put("symlink_target", node.getTarget());
                    }
                }
                
                table.insertByFieldNamesOr(SqlJetConflictAction.REPLACE, fieldsToValues);
                
                if (node.getKind() == SVNWCDbKind.DIR && node.hasChildren()) {
                    List children = node.getChildren();
                    for (ListIterator childIter = children.listIterator(children.size()); childIter.hasPrevious();) {
                        String childName = (String) childIter.previous();
                        fieldsToValues.clear();
                        fieldsToValues.put("wc_id", node.getWCId());
                        fieldsToValues.put("local_relpath", SVNPathUtil.append(node.getLocalRelativePath(), childName));
                        fieldsToValues.put("presence", "incomplete");
                        fieldsToValues.put("kind", "unknown");
                        fieldsToValues.put("revnum", node.getRevision());
                        fieldsToValues.put("parent_relpath", node.getLocalRelativePath());
                        table.insertByFieldNamesOr(SqlJetConflictAction.IGNORE, fieldsToValues);
                    }
                }
            } finally {
                db.commit();
            }    
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
    }
    
    private void verifyThereIsNoWork(SqlJetDb db) throws SVNException {
        try {
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetTable table = db.getTable(WORK_QUEUE_TABLE);
                ISqlJetCursor cursor = table.open();
                try {
                    if (!cursor.eof()) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CLEANUP_REQUIRED);
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                } finally {
                    cursor.close();
                }
                
            } finally {
                db.commit();
            }    
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
    }
    
    private SqlJetDb createDB(File dirPath, String reposRoot, String reposUUID) throws SVNException {
        SqlJetDb db = openDB(dirPath, SqlJetTransactionMode.WRITE);
        myCurrentReposId = createReposId(db, reposRoot, reposUUID);

        try {
            db.beginTransaction(SqlJetTransactionMode.WRITE);
            try {
                ISqlJetTable table = db.getTable(WCROOT_TABLE);
                Map fieldsToValues = new HashMap();
                //TODO: this may require a review later
                fieldsToValues.put("local_abspath", null);
                myCurrentWCId = table.insertByFieldNames(fieldsToValues);
            } finally {
                db.commit();
            }
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
        
        return db;
    }
    
    private long createReposId(SqlJetDb db, String reposRoot, String reposUUID) throws SVNException {
        try {
            db.beginTransaction(SqlJetTransactionMode.WRITE);
            try {
                ISqlJetTable table = db.getTable(REPOSITORY_TABLE);
                ISqlJetCursor cursor = table.lookup(ROOT_INDEX, reposRoot);
                try {
                    if (!cursor.eof()) {
                        return cursor.getInteger("id");
                    }
                } finally {
                    cursor.close();
                }
                
                return table.insert(reposRoot, reposUUID);
            } finally {
                db.commit();
            }    
        } catch (SqlJetException e) {
            SVNSqlJetUtil.convertException(e);
        }
        return -1;
    }
    
    private SqlJetDb openDB(File dirPath, SqlJetTransactionMode mode) throws SVNException {
        File sdbFile = SVNAdminUtil.getSDBFile(dirPath); 

        ISqlJetTransaction openTxn = new ISqlJetTransaction() {
          
            public Object run(SqlJetDb db) throws SqlJetException {
                
                int version = db.getOptions().getUserVersion();
                if (version < SVNAdminAreaFactory.SVN_WC_VERSION) {
                    db.getOptions().setAutovacuum(true);
                    db.runWriteTransaction(new ISqlJetTransaction() {
                        public Object run(SqlJetDb db) throws SqlJetException {
                            //TODO: correct this later when SQLJet dependency gets updated.
                            SqlJetDefaultBusyHandler busyHandler = new SqlJetDefaultBusyHandler(10, 1000);
                            db.setBusyHandler(busyHandler);
                            
                            db.getOptions().setUserVersion(SVNAdminAreaFactory.SVN_WC_VERSION);
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
                } else if (version > SVNAdminAreaFactory.SVN_WC_VERSION) {
                    throw new SqlJetException("Schema format " + version + " not recognized");   
                }
                return null;
            }
        };

        return SVNSqlJetUtil.openDB(sdbFile, openTxn, mode, SVNAdminAreaFactory.SVN_WC_VERSION);
    }
    
    private SVNPristineDirectory getPristineDirectory(File path) {
        if (myPathsToPristineDirs != null) {
            return (SVNPristineDirectory) myPathsToPristineDirs.get(path);
        }
        return null;
    }

    private Map getPathsToPristineDirs() {
        if (myPathsToPristineDirs == null) {
            myPathsToPristineDirs = new HashMap();
        }
        return myPathsToPristineDirs;
    }
    
    private void setPristineDir(File path, SVNPristineDirectory dir) {
        Map pathsToPristineDirs = getPathsToPristineDirs();
        pathsToPristineDirs.put(path, dir);
    }

    private static class RepositoryInfo {
        private String myRoot;
        private String myUUID;
        public RepositoryInfo(String root, String uuid) {
            myRoot = root;
            myUUID = uuid;
        }
        
    }
}
