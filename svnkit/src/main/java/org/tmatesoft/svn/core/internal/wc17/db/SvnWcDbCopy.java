package org.tmatesoft.svn.core.internal.wc17.db;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.AdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.DeletionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.*;

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

    private static void doCopy(SVNWCDbDir srcPdh, File localSrcRelpath, SVNWCDbDir dstPdh, File localDstRelpath, SVNSkel workItems) {
        
    }

    private static Structure<CopyInfo> getCopyInfo(SVNWCDbRoot wcRoot, File localRelPath) throws SVNException {
        Structure<CopyInfo> result = Structure.obtain(CopyInfo.class);
        result.set(CopyInfo.haveWork, false);
        
        Structure<NodeInfo> nodeInfo = SvnWcDbReader.readInfo(wcRoot, localRelPath, NodeInfo.status, NodeInfo.kind, NodeInfo.revision, NodeInfo.reposRelPath,
                NodeInfo.reposId, NodeInfo.opRoot, NodeInfo.haveWork);

        nodeInfo.from(NodeInfo.kind, NodeInfo.status, NodeInfo.reposId, NodeInfo.haveWork)
            .into(result, CopyInfo.kind, CopyInfo.status, CopyInfo.copyFromId, CopyInfo.haveWork);
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
}
