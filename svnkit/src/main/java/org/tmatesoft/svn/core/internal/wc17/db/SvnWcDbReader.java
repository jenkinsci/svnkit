package org.tmatesoft.svn.core.internal.wc17.db;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnInt64;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnKind;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPresence;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnText;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.reset;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb.DirParsedInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.WalkerChildInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;

public class SvnWcDbReader extends SvnWcDbShared {
    
    public static Map<String, Structure<WalkerChildInfo>> readWalkerChildrenInfo(SVNWCDb db, File localAbspath, Map<String, Structure<WalkerChildInfo>> children) throws SVNException {
        
        DirParsedInfo dirInfo = db.obtainWcRoot(localAbspath);
        SVNSqlJetDb sdb = dirInfo.wcDbDir.getWCRoot().getSDb();
        long wcId = dirInfo.wcDbDir.getWCRoot().getWcId();
        
        SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NODE_CHILDREN_WALKER_INFO);
        if (children == null) {
            children = new HashMap<String, Structure<WalkerChildInfo>>();
        }
        
        try {
            stmt.bindf("is", wcId, SVNFileUtil.getParentFile(localAbspath));
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
