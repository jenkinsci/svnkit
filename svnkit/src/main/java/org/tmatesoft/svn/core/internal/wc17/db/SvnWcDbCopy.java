package org.tmatesoft.svn.core.internal.wc17.db;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.bindf;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnBlob;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnInt64;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPath;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPresence;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnText;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.reset;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetInsertStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.AdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.DeletionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.ACTUAL_NODE__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnWcDbCopy extends SvnWcDbShared {
    
    private enum CopyInfo {
        copyFromId, 
        copyFromRelpath,
        copyFromRev,
        status,
        kind,
        opRoot,
        haveWork
    }

    public static void copy(SVNWCDbDir srcPdh, File localSrcRelpath, SVNWCDbDir dstPdh, File localDstRelpath, SVNSkel workItems) throws SVNException {
        boolean dstLocked = false;
        begingWriteTransaction(srcPdh.getWCRoot());
        try {
            if (srcPdh.getWCRoot().getSDb() != dstPdh.getWCRoot().getSDb()) {
                begingWriteTransaction(dstPdh.getWCRoot());
                dstLocked = true;
            }
            doCopy(srcPdh, localSrcRelpath, dstPdh, localDstRelpath, workItems);
        } catch (SVNException e) {
            try {
                rollbackTransaction(srcPdh.getWCRoot());
            } finally {
                if (dstLocked) {
                    rollbackTransaction(dstPdh.getWCRoot());
                }
            }
        } finally {
            try {
                commitTransaction(srcPdh.getWCRoot());
            } finally {
                if (dstLocked) {
                    commitTransaction(dstPdh.getWCRoot());
                }
            }
        }
        
    }

    private static void doCopy(SVNWCDbDir srcPdh, File localSrcRelpath, SVNWCDbDir dstPdh, File localDstRelpath, SVNSkel workItems) throws SVNException {
        Structure<CopyInfo> copyInfo = getCopyInfo(srcPdh.getWCRoot(), localSrcRelpath);
        long[] dstOpDepths = getOpDepthForCopy(dstPdh.getWCRoot(), localDstRelpath, 
                copyInfo.lng(CopyInfo.copyFromId), copyInfo.<File>get(CopyInfo.copyFromRelpath), copyInfo.lng(CopyInfo.copyFromRev));
        
        SVNWCDbStatus status = copyInfo.get(CopyInfo.status);
        SVNWCDbStatus dstPresence = null;
        boolean opRoot = copyInfo.is(CopyInfo.opRoot);
        
        switch (status) {
        case Normal:
        case Added:
        case MovedHere:
        case Copied:
            dstPresence = SVNWCDbStatus.Normal;
            break;
        case Deleted:
            if (opRoot) {
                try {
                    Structure<NodeInfo> dstInfo = SvnWcDbReader.readInfo(dstPdh.getWCRoot(), localDstRelpath, NodeInfo.status);
                    SVNWCDbStatus dstStatus = dstInfo.get(NodeInfo.status);
                    dstInfo.release();
                    if (dstStatus == SVNWCDbStatus.Deleted) {
                        dstPdh.getWCRoot().getDb().addWorkQueue(dstPdh.getWCRoot().getAbsPath(), workItems);
                        return;
                    }
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                        throw e;
                    }
                }
            }
        case NotPresent:
        case Excluded:
            if (dstOpDepths[1] > 0) {
                dstOpDepths[0] = dstOpDepths[1];
                dstOpDepths[1] = -1;
            }
            if (status == SVNWCDbStatus.Excluded) {
                dstPresence = SVNWCDbStatus.Excluded;
            } else {
                dstPresence = SVNWCDbStatus.NotPresent;
            }
            break;
        case ServerExcluded:
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Cannot copy ''{0}'' excluded by server", srcPdh.getWCRoot().getAbsPath(localSrcRelpath));
            SVNErrorManager.error(err, SVNLogType.WC);
        default:
            SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Cannot handle status of ''{0}''", srcPdh.getWCRoot().getAbsPath(localSrcRelpath));
            SVNErrorManager.error(err1, SVNLogType.WC);
        }
        
        SVNWCDbKind kind = copyInfo.get(CopyInfo.kind);
        List<String> children = null;
        if (kind == SVNWCDbKind.Dir) {
            long opDepth = getOpDepthOf(srcPdh.getWCRoot(), localSrcRelpath);
            children = srcPdh.getWCRoot().getDb().gatherRepoChildren(srcPdh, localSrcRelpath, opDepth);
        }
        
        if (srcPdh.getWCRoot() == dstPdh.getWCRoot()) {
            File dstParentRelpath = SVNFileUtil.getFileDir(localDstRelpath);
            SVNSqlJetStatement stmt = new InsertWorkingNodeCopy(srcPdh.getWCRoot().getSDb(), copyInfo.is(CopyInfo.haveWork));
            stmt.bindf("issist", srcPdh.getWCRoot().getWcId(), localSrcRelpath, localDstRelpath, dstOpDepths[0], dstParentRelpath, 
                    SvnWcDbStatementUtil.getPresenceText(dstPresence));
            stmt.done();
            
            copyActual(srcPdh, localSrcRelpath, dstPdh, localDstRelpath);
            
            if (dstOpDepths[1] > 0) {
                stmt = srcPdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.INSERT_NODE);
                stmt.bindf("isisisrtnt", 
                        srcPdh.getWCRoot().getWcId(),
                        localDstRelpath,
                        dstOpDepths[1],
                        dstParentRelpath,
                        copyInfo.lng(CopyInfo.copyFromId),
                        copyInfo.get(CopyInfo.copyFromRelpath),
                        copyInfo.lng(CopyInfo.copyFromRev),
                        SvnWcDbStatementUtil.getPresenceText(SVNWCDbStatus.NotPresent),
                        kind);
                stmt.done();                
            }
            if (kind == SVNWCDbKind.Dir && dstPresence == SVNWCDbStatus.Normal) {
                List<File> fileChildren = new LinkedList<File>();
                for (String childName : children) {
                    fileChildren.add(new File(childName));
                }
                srcPdh.getWCRoot().getDb().insertIncompleteChildren(srcPdh.getWCRoot().getSDb(), srcPdh.getWCRoot().getWcId(), 
                        localDstRelpath, copyInfo.lng(CopyInfo.copyFromRev), fileChildren, dstOpDepths[0]);
            }
        } else {
            // TODO cross-wc copy
        }
        dstPdh.getWCRoot().getDb().addWorkQueue(dstPdh.getWCRoot().getAbsPath(), workItems);
    }
    
    private static void copyActual(SVNWCDbDir srcPdh, File localSrcRelpath, SVNWCDbDir dstPdh, File localDstRelpath) throws SVNException {
        SVNSqlJetStatement stmt = srcPdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_NODE);
        stmt.bindf("is", srcPdh.getWCRoot().getWcId(), localSrcRelpath);
        try {
            if (stmt.next()) {
                String changelist = getColumnText(stmt, ACTUAL_NODE__Fields.changelist);
                byte[] properties = getColumnBlob(stmt, ACTUAL_NODE__Fields.properties);
                
                if (changelist != null || properties != null) {
                    reset(stmt);
                    stmt = srcPdh.getWCRoot().getSDb().getStatement(SVNWCDbStatements.INSERT_ACTUAL_NODE);
                    stmt.bindf("issbssssss", 
                            dstPdh.getWCRoot().getWcId(),
                            localDstRelpath,
                            SVNFileUtil.getFileDir(localDstRelpath),
                            properties,
                            null, null, null,
                            null, changelist, null);
                    stmt.done();
                }
            }
        } finally {
            reset(stmt);
        }
    }

    private static Structure<CopyInfo> getCopyInfo(SVNWCDbRoot wcRoot, File localRelPath) throws SVNException {
        Structure<CopyInfo> result = Structure.obtain(CopyInfo.class);
        result.set(CopyInfo.haveWork, false);
        
        Structure<NodeInfo> nodeInfo = SvnWcDbReader.readInfo(wcRoot, localRelPath, NodeInfo.status, NodeInfo.kind, NodeInfo.revision, NodeInfo.reposRelPath,
                NodeInfo.reposId, NodeInfo.opRoot, NodeInfo.haveWork);

        nodeInfo.from(NodeInfo.kind, NodeInfo.status, NodeInfo.reposId, NodeInfo.haveWork, NodeInfo.opRoot)
            .into(result, CopyInfo.kind, CopyInfo.status, CopyInfo.copyFromId, CopyInfo.haveWork, CopyInfo.opRoot);
        SVNWCDbStatus status = nodeInfo.get(NodeInfo.status);
        File reposRelpath = nodeInfo.get(NodeInfo.reposRelPath);
        long revision = nodeInfo.lng(NodeInfo.revision);
        
        nodeInfo.release();

        if (status == SVNWCDbStatus.Excluded) {
            File parentRelpath = SVNFileUtil.getFileDir(localRelPath);
            String name = SVNFileUtil.getFileName(localRelPath);
            
            Structure<CopyInfo> parentCopyInfo = getCopyInfo(wcRoot, parentRelpath);
            parentCopyInfo.from(CopyInfo.copyFromId, CopyInfo.copyFromRev)
                .into(result, CopyInfo.copyFromId, CopyInfo.copyFromRev);
            
            if (parentCopyInfo.get(CopyInfo.copyFromRelpath) != null) {
                result.set(CopyInfo.copyFromRelpath, 
                        SVNFileUtil.createFilePath(parentCopyInfo.<File>get(CopyInfo.copyFromRelpath), name));
            }
            
            parentCopyInfo.release();
        } else if (status == SVNWCDbStatus.Added) {
            Structure<AdditionInfo> additionInfo = scanAddition(wcRoot, localRelPath, AdditionInfo.opRootRelPath, 
                    AdditionInfo.originalReposRelPath, AdditionInfo.originalReposId, AdditionInfo.originalRevision);
            additionInfo.from(AdditionInfo.originalReposRelPath, AdditionInfo.originalReposId, AdditionInfo.originalRevision)
                .into(result, CopyInfo.copyFromRelpath, CopyInfo.copyFromId, CopyInfo.copyFromRev);
            
            if (additionInfo.get(AdditionInfo.originalReposRelPath) != null) {
                File opRootRelPath = additionInfo.get(AdditionInfo.opRootRelPath);
                File copyFromRelPath = additionInfo.get(AdditionInfo.originalReposRelPath); 
                File relPath = SVNFileUtil.createFilePath(copyFromRelPath, SVNWCUtils.skipAncestor(opRootRelPath, localRelPath));
                result.set(CopyInfo.copyFromRelpath, relPath);
            }
            
            additionInfo.release();
        } else if (status == SVNWCDbStatus.Deleted) {
            Structure<DeletionInfo> deletionInfo = scanDeletion(wcRoot, localRelPath);
            if (deletionInfo.get(DeletionInfo.workDelRelPath) != null) {
                File parentDelRelpath = SVNFileUtil.getFileDir(deletionInfo.<File>get(DeletionInfo.workDelRelPath));

                Structure<AdditionInfo> additionInfo = scanAddition(wcRoot, parentDelRelpath, AdditionInfo.opRootRelPath, 
                        AdditionInfo.originalReposRelPath, AdditionInfo.originalReposId, AdditionInfo.originalRevision);
                
                additionInfo.from(AdditionInfo.originalReposRelPath, AdditionInfo.originalReposId, AdditionInfo.originalRevision)
                    .into(result, CopyInfo.copyFromRelpath, CopyInfo.copyFromId, CopyInfo.copyFromRev);
                File opRootRelPath = additionInfo.get(AdditionInfo.opRootRelPath);
                File copyFromRelPath = additionInfo.get(AdditionInfo.originalReposRelPath); 
                File relPath = SVNFileUtil.createFilePath(copyFromRelPath, SVNWCUtils.skipAncestor(opRootRelPath, localRelPath));
                result.set(CopyInfo.copyFromRelpath, relPath);

                additionInfo.release();
            } else if (deletionInfo.get(DeletionInfo.baseDelRelPath) != null) {
                Structure<NodeInfo> baseInfo = getBaseInfo(wcRoot, localRelPath, NodeInfo.revision, NodeInfo.reposRelPath, NodeInfo.reposId);
                baseInfo.from(NodeInfo.revision, NodeInfo.reposRelPath, NodeInfo.reposId).
                    into(result, CopyInfo.copyFromRev, CopyInfo.copyFromRelpath, CopyInfo.copyFromId);
                baseInfo.release();
            }
            deletionInfo.release();
        } else {
            result.set(CopyInfo.copyFromRelpath, reposRelpath);
            result.set(CopyInfo.copyFromRev, revision);
        }
        
        return result;
    }
    
    private static long[] getOpDepthForCopy(SVNWCDbRoot wcRoot, File localRelpath, long copyFromReposId, File copyFromRelpath, long copyFromRevision) throws SVNException {
        long[] result = new long[] {SVNWCUtils.relpathDepth(localRelpath), -1};
        if (copyFromRelpath == null) {
            return result;
        }
        
        long minOpDepth = 1;
        long incompleteOpDepth = -1;
        
        SVNSqlJetStatement stmt = wcRoot.getSDb().getStatement(SVNWCDbStatements.SELECT_WORKING_NODE);
        bindf(stmt, "is", wcRoot.getWcId(), localRelpath);
        if (stmt.next()) {
            SVNWCDbStatus status = getColumnPresence(stmt);
            minOpDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
            if (status == SVNWCDbStatus.Incomplete) {
                incompleteOpDepth = minOpDepth;
            }
        }
        reset(stmt);
        File parentRelpath = SVNFileUtil.getFileDir(localRelpath);
        bindf(stmt, "is", wcRoot.getWcId(), parentRelpath);
        if (stmt.next()) {
            long parentOpDepth = getColumnInt64(stmt, NODES__Fields.op_depth);
            if (parentOpDepth < minOpDepth) {
                reset(stmt);
                return result;
            }
            if (incompleteOpDepth < 0 || incompleteOpDepth == parentOpDepth) {
                long parentCopyFromReposId = getColumnInt64(stmt, NODES__Fields.repos_id);
                File parentCopyFromRelpath = getColumnPath(stmt, NODES__Fields.repos_path);
                long parentCopyFromRevision = getColumnInt64(stmt, NODES__Fields.revision);
                if (parentCopyFromReposId == copyFromReposId) {
                    if (copyFromRevision == parentCopyFromRevision &&
                            copyFromRelpath.equals(SVNFileUtil.createFilePath(parentCopyFromRelpath, localRelpath.getName()))) {
                        result[0] = parentOpDepth;
                    } else if (incompleteOpDepth > 0) {
                        result[1] = incompleteOpDepth;
                    }
                }
            }
        }
        reset(stmt);
        return result;
    }
    
    private static long getOpDepthOf(SVNWCDbRoot wcRoot, File localRelpath) throws SVNException {
        SVNSqlJetStatement stmt = wcRoot.getSDb().getStatement(SVNWCDbStatements.SELECT_NODE_INFO);
        bindf(stmt, "is", wcRoot.getWcId(), localRelpath);
        try {
            if (stmt.next()) {
                return getColumnInt64(stmt, NODES__Fields.op_depth); 
            }
        } finally {
            reset(stmt);
        }        
        return 0;
        
    }

    private static class InsertWorkingNodeCopy extends SVNSqlJetInsertStatement {

        private SelectNodeToCopy select;

        public InsertWorkingNodeCopy(SVNSqlJetDb sDb, boolean base) throws SVNException {
            super(sDb, SVNWCDbSchema.NODES, SqlJetConflictAction.REPLACE);
            select = new SelectNodeToCopy(sDb, base);
        }

        @Override
        protected Map<String, Object> getInsertValues() throws SVNException {
            // run select once and return values.
            select.bindf("is", getBind(1), getBind(2));
            try {
                if (select.next()) {
                    Map<String, Object> values = new HashMap<String, Object>();
                    values.put(NODES__Fields.wc_id.toString(), select.getColumn(NODES__Fields.wc_id));
                    values.put(NODES__Fields.local_relpath.toString(), getBind(3));
                    values.put(NODES__Fields.op_depth.toString(), getBind(4));
                    values.put(NODES__Fields.parent_relpath.toString(), getBind(5));
                    values.put(NODES__Fields.repos_id.toString(), select.getColumn(NODES__Fields.repos_id));
                    values.put(NODES__Fields.repos_path.toString(), select.getColumn(NODES__Fields.repos_path));
                    values.put(NODES__Fields.revision.toString(), select.getColumn(NODES__Fields.revision));
                    values.put(NODES__Fields.presence.toString(), getBind(6));
                    values.put(NODES__Fields.depth.toString(), select.getColumn(NODES__Fields.depth));
                    values.put(NODES__Fields.kind.toString(), select.getColumn(NODES__Fields.kind));
                    
                    values.put(NODES__Fields.changed_revision.toString(), select.getColumn(NODES__Fields.changed_revision));
                    values.put(NODES__Fields.changed_date.toString(), select.getColumn(NODES__Fields.changed_date));
                    values.put(NODES__Fields.changed_author.toString(), select.getColumn(NODES__Fields.changed_author));
                    values.put(NODES__Fields.checksum.toString(), select.getColumn(NODES__Fields.checksum));
                    values.put(NODES__Fields.properties.toString(), select.getColumn(NODES__Fields.properties));
                    values.put(NODES__Fields.translated_size.toString(), select.getColumn(NODES__Fields.translated_size));
                    values.put(NODES__Fields.last_mod_time.toString(), select.getColumn(NODES__Fields.last_mod_time));
                    values.put(NODES__Fields.symlink_target.toString(), select.getColumn(NODES__Fields.symlink_target));
                    return values;
                }                
            } finally {
                select.reset();
            }
            return null;
        }
    }

    /**
     * SELECT wc_id, ?3 (local_relpath), ?4 (op_depth), ?5 (parent_relpath),
     * repos_id, repos_path, revision, ?6 (presence), depth,
     * kind, changed_revision, changed_date, changed_author, checksum, properties,
     * translated_size, last_mod_time, symlink_target
     * FROM nodes
     * 
     * WHERE wc_id = ?1 AND local_relpath = ?2 AND op_depth > 0
     * ORDER BY op_depth DESC
     * LIMIT 1
     * 
     * or for base:
     * 
     * FROM nodes
     * WHERE wc_id = ?1 AND local_relpath = ?2 AND op_depth = 0

     * @author alex
     *
     */
    private static class SelectNodeToCopy extends SVNSqlJetSelectStatement {

        private boolean isBase;
        private long limit;

        public SelectNodeToCopy(SVNSqlJetDb sDb, boolean base) throws SVNException {
            super(sDb, SVNWCDbSchema.NODES);
            isBase = base;
        }

        @Override
        protected Object[] getWhere() throws SVNException {
            if (isBase) {
                return new Object[] {getBind(1), getBind(2), 0};
            } 
            return super.getWhere();
        }
        
        @Override
        protected boolean isFilterPassed() throws SVNException {
            limit++;
            return super.isFilterPassed() && limit == 1;
        }

        @Override
        protected ISqlJetCursor openCursor() throws SVNException {
            if (isBase) {
                return super.openCursor();
            }
            try {
                return super.openCursor().reverse();
            } catch (SqlJetException e) {
                SVNSqlJetDb.createSqlJetError(e);
            }
            return null;
        }
        
        
    }
}
