package org.tmatesoft.svn.core.internal.wc17.db;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnInt64;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnKind;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPresence;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnText;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPath;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.reset;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb.DirParsedInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.WalkerChildInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;

public class SvnWcDbReader extends SvnWcDbShared {
    
    public enum ReplaceInfo {
        replaced,
        baseReplace,
        replaceRoot
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
            stmt.bindf("is", wcId, dirInfo.localRelPath); //SVNFileUtil.getFileDir(dirInfo.localRelPath));
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

}
