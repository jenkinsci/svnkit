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
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
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
    
    private static final String I_ROOT_INDEX = "I_ROOT";
    private static final String REPOSITORY_TABLE = "REPOSITORY";
    
    private Map myPathsToPristineDirs;
    private boolean myIsAutoUpgrade;
    private boolean myIsEnforceEmptyWorkQueue;

    //these temp fields are set within this class only
    //and must be used to pass values between internal methods only
    private long myCurrentReposId;
    private long myCurrentWCId;
    
    public SVNWorkingCopyDB17() {
    }
    
    public void readInfo(File path) throws SVNException {
        SVNPristineDirectory[] pristineDir = new SVNPristineDirectory[1];
        String localRelPath = parseLocalAbsPath(path, pristineDir, SqlJetTransactionMode.READ_ONLY);
        verifyPristineDirectoryIsUsable(pristineDir[0]);
        
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
        Collection childNames = selectChildrenUsingWCIdAndParentRelPath("BASE_NODE", "I_PARENT", wcRoot.getWCId(), localRelPath, db, null);
        if (!baseOnly) {
            childNames = selectChildrenUsingWCIdAndParentRelPath("WORKING_NODE", "I_WORKING_PARENT", wcRoot.getWCId(), localRelPath, db, childNames);
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
                ISqlJetTable table = db.getTable("BASE_NODE");
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
                ISqlJetTable table = db.getTable("WORKING_NODE");
                ISqlJetCursor cursor = table.lookup(table.getPrimaryKeyIndexName(), wcRoot.getWCId(), localRelativePath);
                try {
                    if (!cursor.eof()) {
                        String kindStr = cursor.getString("kind");
                        return SVNWCDbKind.parseKind(kindStr) == SVNWCDbKind.FILE;
                    }
                } finally {
                    cursor.close();
                }
                
                table = db.getTable("BASE_NODE");
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
                ISqlJetTable table = db.getTable("WCROOT");
                ISqlJetCursor cursor = table.lookup("I_LOCAL_ABSPATH", new Object[] { null });
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
                ISqlJetTable table = db.getTable("BASE_NODE");
                
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
                
                //TODO: insertOrReplace
                table.insertByFieldNames(fieldsToValues);
                
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
                        //TODO: insertOrIgnore
                        table.insertByFieldNames(fieldsToValues);
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
                ISqlJetTable table = db.getTable("WORK_QUEUE");
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
                ISqlJetTable table = db.getTable("WCROOT");
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
                ISqlJetTable table = db.getTable("REPOSITORY");
                ISqlJetCursor cursor = table.lookup("I_ROOT", reposRoot);
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

}
