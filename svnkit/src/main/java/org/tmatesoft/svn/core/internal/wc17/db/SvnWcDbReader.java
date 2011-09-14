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
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb.DirParsedInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.WalkerChildInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;

public class SvnWcDbReader extends SvnWcDbShared {
    
    public enum ReplaceInfo {
        replaced,
        baseReplace,
        replaceRoot
    }
    
    public static Collection<File> getNotPresentDescendants(SVNWCDb db, File parentPath) throws SVNException {
        DirParsedInfo dirInfo = db.obtainWcRoot(parentPath);
        SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        File localRelPath = dirInfo.localRelPath;
        
        Collection<File> result = new ArrayList<File>();
        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NOT_PRESENT_DESCENDANTS);
        try {
            stmt.bindf("isi", wcId, parentPath, SVNWCUtils.relpathDepth(localRelPath));
            while(stmt.next()) {
                result.add(getColumnPath(stmt, NODES__Fields.local_relpath));
            }
        } finally {
            reset(stmt);
        }
        return result;
        
    }
    
    public static Structure<ReplaceInfo> readNodeReplaceInfo(SVNWCDb db, File localAbspath, ReplaceInfo... fields) throws SVNException {
        
        Structure<ReplaceInfo> result = Structure.obtain(ReplaceInfo.class, fields);
        if (result.hasField(ReplaceInfo.replaced)) {
            result.set(ReplaceInfo.replaced, false);
        }
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
                stmt.bindf("is", wcId, SVNFileUtil.getParentFile(localRelPath));
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
    
    public static Map<String, Structure<WalkerChildInfo>> readWalkerChildrenInfo(SVNWCDb db, File localAbspath, Map<String, Structure<WalkerChildInfo>> children) throws SVNException {
        
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbspath);
        SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        
        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_CHILDREN_WALKER_INFO);
        if (children == null) {
            children = new HashMap<String, Structure<WalkerChildInfo>>();
        }
        
        try {
            stmt.bindf("is", wcId, SVNFileUtil.getParentFile(dirInfo.localRelPath));
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
