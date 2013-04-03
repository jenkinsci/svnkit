package org.tmatesoft.svn.core.internal.wc17.db;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnChecksum;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnDate;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnInt64;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnKind;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPath;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPresence;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnProperties;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnText;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.reset;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCConflictDescription17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb.DirParsedInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.WalkerChildInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.ACTUAL_NODE__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.wc.SVNConflictAction;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNOperation;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnWcDbReader extends SvnWcDbShared {
    
    public enum ReplaceInfo {
        replaced,
        baseReplace,
        replaceRoot
    }

    public enum InstallInfo {
        wcRootAbsPath,
        sha1Checksum,
        pristineProps,
        changedDate,
    }

    public enum ConflictInfo {
        conflictOperation,
        locations,
        textConflicted,
        propConflicted,
        treeConflicted,
    }

    public enum ConflictKind {
        text, prop, tree, reject, obstructed;
    }

    public enum PropertyConflictInfo {
        markerAbspath, 
        mineProps,
        theirOldProps,
        theirProps,
        conflictedPropNames,
    }

    public enum TextConflictInfo {
        mineAbsPath,
        theirOldAbsPath,
        theirAbsPath,
    }

    public enum TreeConflictInfo {
        localChange,
        incomingChange,
        moveSrcOpRootAbsPath,
    }
    
    
    public static Collection<File> getServerExcludedNodes(SVNWCDb db, File path) throws SVNException {
        DirParsedInfo dirInfo = db.obtainWcRoot(path);
        SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        File localRelPath = dirInfo.localRelPath;
        
        Collection<File> result = new ArrayList<File>();
        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_ALL_SERVER_EXCLUDED_NODES);
        try {
            stmt.bindf("isi", wcId, localRelPath, 0);
            while(stmt.next()) {
                final File localPath = getColumnPath(stmt, NODES__Fields.local_relpath);
                final File absPath = dirInfo.wcDbDir.getWCRoot().getAbsPath(localPath);
                result.add(absPath);
            }
        } finally {
            reset(stmt);
        }
        return result;
        
    }

    public static Collection<File> getNotPresentDescendants(SVNWCDb db, File parentPath) throws SVNException {
        DirParsedInfo dirInfo = db.obtainWcRoot(parentPath);
        SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        File localRelPath = dirInfo.localRelPath;
        
        Collection<File> result = new ArrayList<File>();
        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NOT_PRESENT_DESCENDANTS);
        try {
            stmt.bindf("isi", wcId, localRelPath, SVNWCUtils.relpathDepth(localRelPath));
            while(stmt.next()) {
                result.add(new File(SVNWCUtils.getPathAsChild(localRelPath, getColumnPath(stmt, NODES__Fields.local_relpath))));
            }
        } finally {
            reset(stmt);
        }
        return result;
        
    }
    
    public static Structure<ReplaceInfo> readNodeReplaceInfo(SVNWCDb db, File localAbspath, ReplaceInfo... fields) throws SVNException {
        
        Structure<ReplaceInfo> result = Structure.obtain(ReplaceInfo.class, fields);
        result.set(ReplaceInfo.replaced, false);
        if (result.hasField(ReplaceInfo.baseReplace)) {
            result.set(ReplaceInfo.baseReplace, false);
        }
        if (result.hasField(ReplaceInfo.replaceRoot)) {
            result.set(ReplaceInfo.replaceRoot, false);
        }
        
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbspath);
        SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        File localRelPath = dirInfo.localRelPath;
        
        SVNSqlJetStatement stmt = null;
        begingReadTransaction(dirInfo.wcDbDir.getWCRoot());
        try {
            stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
            stmt.bindf("is", wcId, localRelPath);
            if (!stmt.next()) {
                nodeNotFound(localAbspath);
            }
            if (getColumnPresence(stmt) != SVNWCDbStatus.Normal) {
                return result;
            }
            if (!stmt.next()) {
                return result;
            }
            
            SVNWCDbStatus replacedStatus = getColumnPresence(stmt);
            if (replacedStatus != SVNWCDbStatus.NotPresent
                    && replacedStatus != SVNWCDbStatus.Excluded
                    && replacedStatus != SVNWCDbStatus.ServerExcluded
                    && replacedStatus != SVNWCDbStatus.BaseDeleted) {
                result.set(ReplaceInfo.replaced, true);
            }
            long replacedOpDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
            if (result.hasField(ReplaceInfo.baseReplace)) {
                long opDepth = replacedOpDepth;
                boolean haveRow = true;
                while (opDepth != 0 && haveRow) {
                    haveRow = stmt.next();
                    if (haveRow) {
                        opDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
                    }
                }
                if (haveRow && opDepth == 0) {
                    SVNWCDbStatus baseStatus = getColumnPresence(stmt);
                    result.set(ReplaceInfo.baseReplace, baseStatus != SVNWCDbStatus.NotPresent);
                }
            }
            reset(stmt);
            
            if (!result.is(ReplaceInfo.replaced) || !result.hasField(ReplaceInfo.replaceRoot)) {
                return result;
            }
            if (replacedStatus != SVNWCDbStatus.BaseDeleted) {
                stmt.bindf("is", wcId, SVNFileUtil.getFileDir(localRelPath));
                if (stmt.next()) {
                    long parentOpDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
                    if (parentOpDepth >= replacedOpDepth) {
                        result.set(ReplaceInfo.replaceRoot, parentOpDepth == replacedOpDepth);
                        return result;
                    }
                    boolean haveRow = stmt.next();
                    if (haveRow) {
                        parentOpDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
                    }
                    if (!haveRow) {
                        result.set(ReplaceInfo.replaceRoot, true);
                    } else if (parentOpDepth < replacedOpDepth) {
                        result.set(ReplaceInfo.replaceRoot, true);
                    }
                    reset(stmt);
                }
            }
        } finally {
            reset(stmt);
            commitTransaction(dirInfo.wcDbDir.getWCRoot());
        }
        return result;
    }

    public static Structure<InstallInfo> readNodeInstallInfo(SVNWCDb db, File localAbspath, InstallInfo... fields) throws SVNException {
        final Structure<InstallInfo> result = Structure.obtain(InstallInfo.class, fields);
        
        final DirParsedInfo dirInfo = db.obtainWcRoot(localAbspath);
        final SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        final long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        final File localRelPath = dirInfo.localRelPath;
        
        if (result.hasField(InstallInfo.wcRootAbsPath)) {
            result.set(InstallInfo.wcRootAbsPath, dirInfo.wcDbDir.getWCRoot().getAbsPath());
        }
        
        begingReadTransaction(dirInfo.wcDbDir.getWCRoot());
        SVNSqlJetStatement stmt = null;
        try {
            stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
            stmt.bindf("is", wcId, localRelPath);
            if (!stmt.next()) {
                nodeIsNotInstallable(localAbspath);
            } else {
                if (result.hasField(InstallInfo.changedDate)) {
                    result.set(InstallInfo.changedDate, getColumnDate(stmt, NODES__Fields.changed_date));
                }
                if (result.hasField(InstallInfo.sha1Checksum)) {
                    result.set(InstallInfo.sha1Checksum, getColumnChecksum(stmt, NODES__Fields.checksum));
                }
                if (result.hasField(InstallInfo.pristineProps)) {
                    result.set(InstallInfo.pristineProps, getColumnProperties(stmt, NODES__Fields.properties));
                }
            }
            reset(stmt);
        } finally {
            reset(stmt);
            commitTransaction(dirInfo.wcDbDir.getWCRoot());
        }
        
        return result;
    }
    
    public static long[] getMinAndMaxRevisions(SVNWCDb db, File localAbsPath) throws SVNException {

        DirParsedInfo dirInfo = db.obtainWcRoot(localAbsPath);
        SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        final long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        final File localRelpath = dirInfo.localRelPath;
        final long[] revs = new long[] { -1, -1, -1, -1 };

        SVNSqlJetSelectStatement stmt = new SVNSqlJetSelectStatement(sdb, SVNWCDbSchema.NODES) {
            
            @Override
            protected Object[] getWhere() throws SVNException {
                return new Object[] {wcId};
            }

            @Override
            protected boolean isFilterPassed() throws SVNException {
                
                String path = getColumnString(SVNWCDbSchema.NODES__Fields.local_relpath);
                if ("".equals(localRelpath.getPath()) || path.equals(localRelpath.getPath()) || path.startsWith(localRelpath.getPath() + "/")) {
                    long depth = getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth);
                    if (depth != 0) {
                        return false;
                    }
                    String presence = getColumnString(SVNWCDbSchema.NODES__Fields.presence);
                    if (!("normal".equals(presence) || "incomplete".equals(presence))) {
                        return false;
                    }
                    long rev = getColumnLong(SVNWCDbSchema.NODES__Fields.revision);
                    long changedRev = getColumnLong(SVNWCDbSchema.NODES__Fields.revision);
                    if (getColumnBoolean(SVNWCDbSchema.NODES__Fields.file_external)) {
                        return false;
                    }
                    if (revs[0] < 0 || revs[0] > rev) {
                        revs[0] = rev;
                    }
                    if (revs[1] < 0 || revs[1] < rev) {
                        revs[1] = rev;
                    }
                    if (revs[2] < 0 || revs[2] > changedRev) {
                        revs[2] = changedRev;
                    }
                    if (revs[3] < 0 || revs[3] < changedRev) {
                        revs[3] = changedRev;
                    }
                }
                return false;
            }
        };
        try {
            while(stmt.next()) {}
        } finally {
            reset(stmt);
        }
                
        return revs;
    }
    
    public static Map<String, Structure<WalkerChildInfo>> readWalkerChildrenInfo(SVNWCDb db, File localAbspath, Map<String, Structure<WalkerChildInfo>> children) throws SVNException {
        
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbspath);
        SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        
        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_CHILDREN_WALKER_INFO);
        if (children == null) {
            children = new HashMap<String, Structure<WalkerChildInfo>>();
        }
        
        try {
            stmt.bindf("is", wcId, dirInfo.localRelPath);
            while(stmt.next()) {
                File childPath = SVNFileUtil.createFilePath(getColumnText(stmt, NODES__Fields.local_relpath));
                String childName = SVNFileUtil.getFileName(childPath);
                
                Structure<WalkerChildInfo> childInfo = children.get(childName);
                if (childInfo == null) {
                    childInfo = Structure.obtain(WalkerChildInfo.class);
                    children.put(childName, childInfo);
                }
                long opDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
                if (opDepth > 0) {
                    childInfo.set(WalkerChildInfo.status, SVNWCDb.getWorkingStatus(getColumnPresence(stmt)));
                } else {
                    childInfo.set(WalkerChildInfo.status, getColumnPresence(stmt));
                }
                childInfo.set(WalkerChildInfo.kind, getColumnKind(stmt, NODES__Fields.kind));            
            }
        } finally {
            reset(stmt);
        }
        
        return children;
    }

    public static boolean hasSwitchedSubtrees(SVNWCDb db, File localAbspath) throws SVNException {
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbspath);
        SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        String localRelPathStr = dirInfo.localRelPath.getPath().replace(File.separatorChar, '/');
        
        SqlJetDb sqljetDb = sdb.getDb();
        String parentReposRelpath = ""; 

        ISqlJetCursor cursor = null;
        try {
            sqljetDb.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            ISqlJetTable nodesTable = sqljetDb.getTable(SVNWCDbSchema.NODES.toString());
            String parentRelPath = null;
            Map<String, String> parents = new HashMap<String, String>();
            cursor = nodesTable.scope(null, new Object[] {wcId, localRelPathStr}, null);
            if ("".equals(localRelPathStr)) {
                if (!cursor.eof()) {
                    parentReposRelpath = cursor.getString(SVNWCDbSchema.NODES__Fields.repos_path.toString());
                    parents.put("", parentReposRelpath);
                    cursor.next();
                } 
            } else if (!"".equals(localRelPathStr)) {
                parentRelPath = localRelPathStr;
            }
            boolean matched = false;
            while(!cursor.eof()) {
                String rowRelPath = cursor.getString(SVNWCDbSchema.NODES__Fields.local_relpath.toString());
                boolean fileExternal = cursor.getBoolean(SVNWCDbSchema.NODES__Fields.file_external.toString());
                if (fileExternal) {
                } else if (rowRelPath.equals(parentRelPath)) {
                    long opDepth = cursor.getInteger(SVNWCDbSchema.NODES__Fields.op_depth.toString());
                    if (opDepth == 0) {
                        parents.put(rowRelPath, cursor.getString(SVNWCDbSchema.NODES__Fields.repos_path.toString()));
                    }
                } else if ("".equals(localRelPathStr) || rowRelPath.startsWith(localRelPathStr + "/")) {
                    matched = true;
                    long opDepth = cursor.getInteger(SVNWCDbSchema.NODES__Fields.op_depth.toString());
                    if (opDepth == 0) {
                        String rowReposRelpath = cursor.getString(SVNWCDbSchema.NODES__Fields.repos_path.toString());
                        String rowParentRelpath = cursor.getString(SVNWCDbSchema.NODES__Fields.parent_relpath.toString());
                        if ("dir".equals(cursor.getString(SVNWCDbSchema.NODES__Fields.kind.toString()))) {
                            parents.put(rowRelPath, rowReposRelpath);
                        }
                        parentReposRelpath = parents.get(rowParentRelpath);
                        String expectedReposRelpath = SVNPathUtil.append(parentReposRelpath, SVNPathUtil.tail(rowRelPath));
                        if (!rowReposRelpath.equals(expectedReposRelpath)) {
                            return true;
                        }
                    }
                } else if (matched) {
                    matched = false;
                    break;
                }
                cursor.next();
            }
        } catch (SqlJetException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
            SVNErrorManager.error(err, SVNLogType.WC);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (SqlJetException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            try {
                sqljetDb.commit();
            } catch (SqlJetException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        return false;
    }

    public static boolean hasLocalModifications(SVNWCContext context, File localAbspath) throws SVNException {
        SVNWCDb db = (SVNWCDb) context.getDb();
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbspath);
        SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        String localRelPathStr = dirInfo.localRelPath.getPath().replace(File.separatorChar, '/');
        
        SqlJetDb sqljetDb = sdb.getDb();
        ISqlJetCursor cursor = null;
        boolean matched = false;
        try {
            sqljetDb.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            ISqlJetTable nodesTable = sqljetDb.getTable(SVNWCDbSchema.NODES.toString());
            cursor = nodesTable.scope(null, new Object[] {wcId, localRelPathStr}, null);
            // tree modifications
            while(!cursor.eof()) {
                String rowRelPath = cursor.getString(SVNWCDbSchema.NODES__Fields.local_relpath.toString());
                if ("".equals(localRelPathStr) || rowRelPath.equals(localRelPathStr) || rowRelPath.startsWith(localRelPathStr + "/")) {
                    matched = true;
                    long opDepth = cursor.getInteger(SVNWCDbSchema.NODES__Fields.op_depth.toString());
                    if (opDepth > 0) {
                        return true;
                    }
                } else if (matched) {
                    matched = false;
                    break;
                }
                cursor.next();
            }
            cursor.close();
            ISqlJetTable actualNodesTable = sqljetDb.getTable(SVNWCDbSchema.ACTUAL_NODE.toString());
            
            // prop mods
            cursor = actualNodesTable.scope(null, new Object[] {wcId, localRelPathStr}, null);
            while(!cursor.eof()) {
                String rowRelPath = cursor.getString(SVNWCDbSchema.NODES__Fields.local_relpath.toString());
                if ("".equals(localRelPathStr) || rowRelPath.equals(localRelPathStr) || rowRelPath.startsWith(localRelPathStr + "/")) {
                    matched = true;
                    if (cursor.getBlobAsArray(SVNWCDbSchema.ACTUAL_NODE__Fields.properties.toString()) != null) {
                        return true;
                    }
                } else if (matched) {
                    matched = false;
                    break;
                }
                cursor.next();
            }
            cursor.close();
            
            // text mods.
            cursor = nodesTable.scope(null, new Object[] {wcId, localRelPathStr}, null);
            while(!cursor.eof()) {
                String rowRelPath = cursor.getString(SVNWCDbSchema.NODES__Fields.local_relpath.toString());
                if ("".equals(localRelPathStr) || rowRelPath.equals(localRelPathStr) || rowRelPath.startsWith(localRelPathStr + "/")) {
                    matched = true;
                    String kind = cursor.getString(SVNWCDbSchema.NODES__Fields.kind.toString());
                    if ("file".equals(kind)) {
                        String presence = cursor.getString(SVNWCDbSchema.NODES__Fields.presence.toString());
                        if ("normal".equals(presence) && !cursor.getBoolean(SVNWCDbSchema.NODES__Fields.file_external.toString())) {
                            File localFile = dirInfo.wcDbDir.getWCRoot().getAbsPath(new File(rowRelPath));
                            SVNFileType ft = SVNFileType.getType(localFile);
                            if (!(ft == SVNFileType.FILE || ft == SVNFileType.SYMLINK)) {
                                return true;
                            }
                            long size = cursor.getInteger(SVNWCDbSchema.NODES__Fields.translated_size.toString());
                            long date = cursor.getInteger(SVNWCDbSchema.NODES__Fields.last_mod_time.toString());
                            if (size != -1 && date != 0) {
                                if (size != SVNFileUtil.getFileLength(localFile)) {
                                    return true;
                                }
                                if (date/1000 == SVNFileUtil.getFileLastModified(localFile)) {
                                    cursor.next();
                                    continue;
                                }
                            }
                            if (context.isTextModified(localFile, false)) {
                                return true;
                            }
                        }
                    }
                } else if (matched) {
                    matched = false;
                    break;
                }
                cursor.next();
            }
            cursor.close();

        } catch (SqlJetException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
            SVNErrorManager.error(err, SVNLogType.WC);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (SqlJetException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            try {
                sqljetDb.commit();
            } catch (SqlJetException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        return false;
    }

    public static boolean isSparseCheckout(SVNWCDb db, File localAbspath) throws SVNException {
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbspath);
        SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        String localRelPathStr = dirInfo.localRelPath.getPath().replace(File.separatorChar, '/');
        
        SqlJetDb sqljetDb = sdb.getDb();

        ISqlJetCursor cursor = null;
        try {
            sqljetDb.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            ISqlJetTable nodesTable = sqljetDb.getTable(SVNWCDbSchema.NODES.toString());
            cursor = nodesTable.scope(null, new Object[] {wcId, localRelPathStr}, null);
            boolean matched = false;
            while(!cursor.eof()) {
                String rowRelPath = cursor.getString(SVNWCDbSchema.NODES__Fields.local_relpath.toString());
                boolean fileExternal = cursor.getBoolean(SVNWCDbSchema.NODES__Fields.file_external.toString());
                if (fileExternal) {
                } else if ("".equals(localRelPathStr) || rowRelPath.equals(localRelPathStr) || rowRelPath.startsWith(localRelPathStr + "/")) {
                    matched = true;
                    long opDepth = cursor.getInteger(SVNWCDbSchema.NODES__Fields.op_depth.toString());
                    if (opDepth == 0) {
                        SVNWCDbStatus presence = SvnWcDbStatementUtil.parsePresence(cursor.getString(SVNWCDbSchema.NODES__Fields.presence.toString()));
                        if (presence == SVNWCDbStatus.Excluded || presence == SVNWCDbStatus.ServerExcluded) {
                            return true;
                        }
                        SVNDepth depth = SvnWcDbStatementUtil.parseDepth(cursor.getString(SVNWCDbSchema.NODES__Fields.depth.toString()));
                        if (depth != SVNDepth.UNKNOWN && depth != SVNDepth.INFINITY) {
                            return true;
                        }
                    }
                } else if (matched) {
                    matched = false;
                    break;
                }
                cursor.next();
            }
        } catch (SqlJetException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
            SVNErrorManager.error(err, SVNLogType.WC);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (SqlJetException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
            try {
                sqljetDb.commit();
            } catch (SqlJetException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SQLITE_ERROR, e);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }
        
        return false;
    }
    
    public static SVNSkel readConflict(SVNWCDb db, File localAbspath) throws SVNException {
        final DirParsedInfo dirInfo = db.obtainWcRoot(localAbspath);
        final SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        final long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        final String localRelPathStr = dirInfo.localRelPath.getPath().replace(File.separatorChar, '/');
        
        final SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);
        try {
            stmt.bindf("is", wcId, localRelPathStr);
            if (!stmt.next()) {
                final SVNSqlJetStatement stmtNode = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
                try {
                    stmtNode.bindf("is", wcId, localRelPathStr);
                    if (stmtNode.next()) {
                        return null;
                    }
                } finally {
                    reset(stmtNode);
                }
                final SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "The node ''{0}'' was not found.", localAbspath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            final byte[] conflictData = stmt.getColumnBlob(ACTUAL_NODE__Fields.conflict_data);
            if (conflictData != null) {
                return SVNSkel.parse(conflictData);
            }
        } finally {
            reset(stmt);
        }
        return null;
    }
    
    public static Structure<ConflictInfo> readConflictInfo(SVNSkel conflictSkel) throws SVNException {
        final Structure<ConflictInfo> result = Structure.obtain(ConflictInfo.class);
        SVNSkel c;
        final SVNSkel operation = readConflictOperation(conflictSkel);
        if (operation == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Not a completed conflict skel");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        // operation
        c = operation.first();
        result.set(ConflictInfo.conflictOperation, SVNOperation.fromString(c.getValue()));
        
        // location
        c = c.next();
        final Collection<SVNConflictVersion> locations = new ArrayList<SVNConflictVersion>();
        result.set(ConflictInfo.locations, locations);
        for(int i = 0; i < c.getListSize(); i++) {
            final SVNConflictVersion location = readConflictLocation(c.getChild(i));
            if (location != null) {
                locations.add(location);
            }
        }
        result.set(ConflictInfo.textConflicted, hasConflictKind(conflictSkel, ConflictKind.text));
        result.set(ConflictInfo.propConflicted, hasConflictKind(conflictSkel, ConflictKind.prop));
        result.set(ConflictInfo.treeConflicted, hasConflictKind(conflictSkel, ConflictKind.tree));
        return result;
    }
    
    public static void readPropertyConflicts(List<SVNWCConflictDescription17> target, 
            SVNWCDb db, 
            File localAbsPath, 
            SVNSkel conflictSkel, 
            boolean createTempFiles,
            SVNOperation operation,
            SVNConflictVersion leftVersion,
            SVNConflictVersion rightVersion) throws SVNException {
        
        final Structure<PropertyConflictInfo> propertyConflictInfo = readPropertyConflict(db, localAbsPath, conflictSkel);
        final Set<String> conflictedProps = propertyConflictInfo.get(PropertyConflictInfo.conflictedPropNames);
        if (!createTempFiles || conflictedProps.isEmpty()) {
            final SVNWCConflictDescription17 description = SVNWCConflictDescription17.createProp(localAbsPath, SVNNodeKind.UNKNOWN, "");
            description.setTheirFile((File) propertyConflictInfo.get(PropertyConflictInfo.markerAbspath));
            description.setOperation(operation);
            description.setSrcLeftVersion(leftVersion);
            description.setSrcRightVersion(rightVersion);
            target.add(description);
            return;
        }
        //
        final File tmpFileRoot = db.getWCRootTempDir(localAbsPath); 
        for(String propertyName : conflictedProps) {
            final SVNWCConflictDescription17 description = SVNWCConflictDescription17.createProp(localAbsPath, SVNNodeKind.UNKNOWN, propertyName);
            description.setOperation(operation);
            description.setSrcLeftVersion(leftVersion);
            description.setSrcRightVersion(rightVersion);
            description.setPropertyName(propertyName);
            
            final Map<String, byte[]> mineProps = propertyConflictInfo.get(PropertyConflictInfo.mineProps); 
            final Map<String, byte[]> theirProps = propertyConflictInfo.get(PropertyConflictInfo.theirProps); 
            final Map<String, byte[]> oldProps = propertyConflictInfo.get(PropertyConflictInfo.theirOldProps); 
            
            final byte[] mineValue = mineProps.get(propertyName);
            final byte[] theirValue = theirProps.get(propertyName);
            final byte[] oldValue = oldProps.get(propertyName);
            if (theirValue == null) {
                description.setAction(SVNConflictAction.DELETE);
            } else if (mineValue == null) {
                description.setAction(SVNConflictAction.ADD);
            } else {
                description.setAction(SVNConflictAction.EDIT);
            }
            
            if (mineValue == null) {
                description.setReason(SVNConflictReason.DELETED);
            } else if (theirValue == null) {
                description.setReason(SVNConflictReason.ADDED);
            } else {
                description.setReason(SVNConflictReason.EDITED);
            }
            
            description.setTheirFile((File) propertyConflictInfo.get(PropertyConflictInfo.markerAbspath));
            if (mineValue != null) {
                final File tempFile = SVNFileUtil.createUniqueFile(tmpFileRoot, "svn.", ".prop.tmp", false);
                description.setMyFile(tempFile);
                SVNFileUtil.writeToFile(tempFile, mineValue);
            }
            if (theirValue != null) {
                final File tempFile = SVNFileUtil.createUniqueFile(tmpFileRoot, "svn.", ".prop.tmp", false);
                description.setMergedFile(tempFile);
                SVNFileUtil.writeToFile(tempFile, theirValue);
            }
            if (oldValue != null) {
                final File tempFile = SVNFileUtil.createUniqueFile(tmpFileRoot, "svn.", ".prop.tmp", false);
                description.setBaseFile(tempFile);
                SVNFileUtil.writeToFile(tempFile, oldValue);
            }
            target.add(description);
         }
    }
    
    public static Structure<PropertyConflictInfo> readPropertyConflict(SVNWCDb db, File wriAbsPath, SVNSkel conflictSkel) throws SVNException {
        final SVNSkel propConflict = getConflict(conflictSkel, ConflictKind.prop);
        if (propConflict == null) {
            final SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_MISSING, "Conflict not set");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        SVNSkel c;
        final Structure<PropertyConflictInfo> result = Structure.obtain(PropertyConflictInfo.class);
        c = propConflict.first().next();
        if (c.first() != null && c.first().isAtom()) {
            result.set(PropertyConflictInfo.markerAbspath, new File(c.first().getValue()));
        }
        c = c.next();
        final Set<String> conflictedPropertyNames = new HashSet<String>();
        for(int i = 0; i < c.getListSize(); i++) {
            conflictedPropertyNames.add(c.getChild(i).getValue());
        }
        result.set(PropertyConflictInfo.conflictedPropNames, conflictedPropertyNames);
        c = c.next();
        if (c.isValidPropList()) {
            result.set(PropertyConflictInfo.theirOldProps, c.parsePropList());
        } else {
            result.set(PropertyConflictInfo.theirOldProps, new HashMap<String, byte[]>());
        }
        c = c.next();
        if (c.isValidPropList()) {
            result.set(PropertyConflictInfo.mineProps, c.parsePropList());
        } else {
            result.set(PropertyConflictInfo.mineProps, new HashMap<String, byte[]>());
        }
        c = c.next();
        if (c.isValidPropList()) {
            result.set(PropertyConflictInfo.theirProps, c.parsePropList());
        } else {
            result.set(PropertyConflictInfo.theirProps, new HashMap<String, byte[]>());
        }

        return result;
    }

    public static Structure<TextConflictInfo> readTextConflict(SVNWCDb db, File wriAbsPath, SVNSkel conflictSkel) throws SVNException {
        final SVNSkel textConflict = getConflict(conflictSkel, ConflictKind.text);
        if (textConflict == null) {
            final SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_MISSING, "Conflict not set");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        final Structure<TextConflictInfo> result = Structure.obtain(TextConflictInfo.class);
        SVNSkel m = textConflict.first().next().first();
        if (m.isAtom()) {
            final File path = db.fromRelPath(db.getWCRoot(wriAbsPath), new File(m.getValue()));
            result.set(TextConflictInfo.theirOldAbsPath, path);
        }
        m = m.next();
        if (m.isAtom()) {
            final File path = db.fromRelPath(db.getWCRoot(wriAbsPath), new File(m.getValue()));
            result.set(TextConflictInfo.mineAbsPath, path);
        }
        m = m.next();
        if (m.isAtom()) {
            final File path = db.fromRelPath(db.getWCRoot(wriAbsPath), new File(m.getValue()));
            result.set(TextConflictInfo.theirAbsPath, path);
        }
        
        return result;
    }

    public static Structure<TreeConflictInfo> readTreeConflict(SVNWCDb db, File wriAbsPath, SVNSkel conflictSkel) throws SVNException {
        final SVNSkel treeConflict = getConflict(conflictSkel, ConflictKind.tree);
        if (treeConflict == null) {
            final SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_MISSING, "Conflict not set");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        final Structure<TreeConflictInfo> result = Structure.obtain(TreeConflictInfo.class);
        SVNSkel c = treeConflict.first().next().next();
        
        SVNConflictReason reason = SVNConflictReason.fromString(c.getValue());
        if (reason == null) {
            reason = SVNConflictReason.EDITED;
        }
        boolean movedAway = reason == SVNConflictReason.MOVED_AWAY;
        result.set(TreeConflictInfo.localChange, reason);
        c = c.next();
        
        SVNConflictAction incoming = SVNConflictAction.fromString(c.getValue());
        if (incoming == null) {
            incoming = SVNConflictAction.EDIT;
        }
        result.set(TreeConflictInfo.incomingChange, incoming);
        c = c.next();
        if (c != null && movedAway) {
            result.set(TreeConflictInfo.moveSrcOpRootAbsPath, db.fromRelPath(db.getWCRoot(wriAbsPath), new File(c.getValue())));
        }
        return result;
    }

    private static SVNSkel readConflictOperation(SVNSkel conflictSkel) {
        return conflictSkel.first();
    }
    
    private static boolean hasConflictKind(SVNSkel conflictSkel, ConflictKind kind) {
        return getConflict(conflictSkel, kind) != null;
    }

    private static SVNSkel getConflict(SVNSkel conflictSkel, ConflictKind kind) {
        SVNSkel c = conflictSkel.first().next().first();
        while(c != null) {
            if (kind.name().equalsIgnoreCase(c.first().getValue())) {
                return c;
            }
            c = c.next();
        }
        return null;
    }

    private static SVNConflictVersion readConflictLocation(SVNSkel locationSkel) throws SVNException {
        SVNSkel c = locationSkel.first();
        if (c == null || !c.contentEquals("subversion")) {
            return null;
        }
        c = c.next();
        final SVNURL repositoryRootURL = SVNURL.parseURIEncoded(c.getValue());
        c = c.next();
        // TODO UUID
        c = c.next();
        final String reposRelPath = c.getValue();
        c = c.next();
        final long revision = Long.parseLong(c.getValue());
        c = c.next();
        final SVNNodeKind nodeKind = SVNNodeKind.parseKind(c.getValue());
        return new SVNConflictVersion(repositoryRootURL, reposRelPath, revision, nodeKind);
    }

}
