package org.tmatesoft.svn.core.internal.wc17.db;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetInsertStatement;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb.DirParsedInfo;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb.InsertBase;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.ExternalNodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.EXTERNALS__Fields;
import org.tmatesoft.svn.core.wc2.SvnChecksum;
import org.tmatesoft.svn.util.SVNLogType;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.*;

public class SvnWcDbExternals extends SvnWcDbShared {
    
    public static void addExternalDir(SVNWCDb db, File localAbspath, File wriPath, SVNURL reposRootUrl, String reposUuid,
            File recordAncestorAbspath, File recordedReposRelPath, long recordedPegRevision, long recordedRevision,
            SVNSkel workItems) throws SVNException {

        if (wriPath == null) {
            wriPath = SVNFileUtil.getParentFile(localAbspath);
        }
        DirParsedInfo dirInfo = db.obtainWcRoot(wriPath);
        SVNWCDbRoot root = dirInfo.wcDbDir.getWCRoot();
        
        File localRelpath = SVNFileUtil.createFilePath(SVNPathUtil.getRelativePath(root.getAbsPath().getAbsolutePath(), localAbspath.getAbsolutePath()));
        Structure<ExternalNodeInfo> externalInfo = Structure.obtain(ExternalNodeInfo.class);
        
        externalInfo.set(ExternalNodeInfo.revision, SVNWCContext.INVALID_REVNUM);
        externalInfo.set(ExternalNodeInfo.changedRevision, SVNWCContext.INVALID_REVNUM);
        externalInfo.set(ExternalNodeInfo.reposId, SVNWCContext.INVALID_REVNUM);
        externalInfo.set(ExternalNodeInfo.recordedRevision, SVNWCContext.INVALID_REVNUM);
        externalInfo.set(ExternalNodeInfo.recordedPegRevision, SVNWCContext.INVALID_REVNUM);
        
        externalInfo.set(ExternalNodeInfo.kind, SVNWCDbKind.Dir);
        externalInfo.set(ExternalNodeInfo.presence, SVNWCDbStatus.Normal);
        externalInfo.set(ExternalNodeInfo.reposRootUrl, reposRootUrl);
        externalInfo.set(ExternalNodeInfo.reposUuid, reposUuid);
        externalInfo.set(ExternalNodeInfo.recordAncestorRelPath, SVNFileUtil.createFilePath(SVNPathUtil.getRelativePath(root.getAbsPath().getAbsolutePath(), recordAncestorAbspath.getAbsolutePath())));
        externalInfo.set(ExternalNodeInfo.recordedReposRelPath, recordedReposRelPath);
        
        externalInfo.set(ExternalNodeInfo.recordedPegRevision, recordedPegRevision);
        externalInfo.set(ExternalNodeInfo.recordedRevision, recordedRevision);
        
        externalInfo.set(ExternalNodeInfo.workItems, workItems);
        
        begingWriteTransaction(root);
        try {
            insertExternalNode(root, localRelpath, externalInfo);
            commit(root);
        } catch (SVNException e) {
            rollback(root);
            throw e;
        } finally {
            externalInfo.release();
        }

    }
    
    public static void insertExternalNode(SVNWCDbRoot root, File localRelpath, Structure<ExternalNodeInfo> info) throws SVNException {
        long reposId = info.lng(ExternalNodeInfo.reposId);
        if (reposId == SVNWCContext.INVALID_REVNUM) {
            reposId = root.getDb().createReposId(root.getSDb(), info.<SVNURL>get(ExternalNodeInfo.reposRootUrl), info.text(ExternalNodeInfo.reposUuid));
        }
        boolean updateRoot;
        SVNWCDbStatus status;
        try {
            WCDbBaseInfo baseInfo = root.getDb().getBaseInfo(root, localRelpath, BaseInfoField.updateRoot, BaseInfoField.status);
            updateRoot = baseInfo.updateRoot;
            status = baseInfo.status;
            if (status == SVNWCDbStatus.Normal && !updateRoot) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
        }
        
        SVNWCDbKind kind = info.<SVNWCDbKind>get(ExternalNodeInfo.kind);
        if (kind == SVNWCDbKind.File || kind == SVNWCDbKind.Symlink) {
            InsertBase insertBase = root.getDb().new InsertBase();
            insertBase.status = SVNWCDbStatus.Normal;
            insertBase.kind = info.<SVNWCDbKind>get(ExternalNodeInfo.kind);
            
            insertBase.reposId = reposId;
            insertBase.reposRelpath = info.<File>get(ExternalNodeInfo.reposRelPath);
            insertBase.revision = info.lng(ExternalNodeInfo.revision);
            
            insertBase.props = info.<SVNProperties>get(ExternalNodeInfo.properties);
            insertBase.changedRev = info.lng(ExternalNodeInfo.changedRevision);
            insertBase.changedDate = info.<SVNDate>get(ExternalNodeInfo.changedDate);
            insertBase.changedAuthor = info.text(ExternalNodeInfo.changedAuthor);
            
            insertBase.davCache = info.<SVNProperties>get(ExternalNodeInfo.davCache);
            insertBase.checksum = info.<SvnChecksum>get(ExternalNodeInfo.checksum);

            insertBase.target = info.<File>get(ExternalNodeInfo.target);
            insertBase.conflict = info.<SVNSkel>get(ExternalNodeInfo.conflict);
            
            insertBase.updateActualProps = info.is(ExternalNodeInfo.updateActualProperties);
            insertBase.actualProps = info.<SVNProperties>get(ExternalNodeInfo.newActualProperties);
            insertBase.keepRecordedInfo = info.is(ExternalNodeInfo.keepRecordedInfo);
            insertBase.workItems = info.<SVNSkel>get(ExternalNodeInfo.workItems);
            
            insertBase.fileExternal = true;

            try {
                insertBase.transaction(root.getSDb());
            } catch (SqlJetException e) {
                sqliteError(e);
            }
        } else {
            root.getDb().addWorkQueue(root.getAbsPath(), info.<SVNSkel>get(ExternalNodeInfo.workItems));
        }
        //
        SVNSqlJetInsertStatement stmt = new InsertExternalStatement(root.getSDb());
        stmt.bindf("issttsisii", 
                root.getWcId(),
                localRelpath,
                SVNFileUtil.getParentFile(localRelpath),
                getPresenceText(info.<SVNWCDbStatus>get(ExternalNodeInfo.presence)),
                getKindText(info.<SVNWCDbKind>get(ExternalNodeInfo.kind)),
                info.get(ExternalNodeInfo.recordAncestorRelPath),
                reposId,
                info.get(ExternalNodeInfo.recordedReposRelPath),
                info.lng(ExternalNodeInfo.recordedPegRevision),
                info.lng(ExternalNodeInfo.recordedRevision));
        stmt.done();
    }
    
    /**
     * INSERT OR REPLACE INTO externals (
     * wc_id, local_relpath, parent_relpath, presence, kind, def_local_relpath,
     * repos_id, def_repos_relpath, def_operational_revision, def_revision)
     * VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
     */
    private static class InsertExternalStatement extends SVNSqlJetInsertStatement {

        public InsertExternalStatement(SVNSqlJetDb sDb) throws SVNException {
            super(sDb, SVNWCDbSchema.EXTERNALS, SqlJetConflictAction.REPLACE);
        }
        
        @Override
        protected Map<String, Object> getInsertValues() throws SVNException {
            Map<String, Object> values = new HashMap<String, Object>();
            values.put(EXTERNALS__Fields.wc_id.toString(), getBind(1));
            values.put(EXTERNALS__Fields.local_relpath.toString(), getBind(2));
            values.put(EXTERNALS__Fields.parent_relpath.toString(), getBind(3));
            values.put(EXTERNALS__Fields.presence.toString(), getBind(4));
            values.put(EXTERNALS__Fields.kind.toString(), getBind(5));
            values.put(EXTERNALS__Fields.def_local_relpath.toString(), getBind(6));
            values.put(EXTERNALS__Fields.repos_id.toString(), getBind(7));
            values.put(EXTERNALS__Fields.def_repos_relpath.toString(), getBind(8));
            if (((Long) getBind(9)) >= 0) {
                values.put(EXTERNALS__Fields.def_operational_revision.toString(), getBind(9));
            } else {
                values.put(EXTERNALS__Fields.def_operational_revision.toString(), null);
            }
            if (((Long) getBind(10)) >= 0) {
                values.put(EXTERNALS__Fields.def_revision.toString(), getBind(10));
            } else {
                values.put(EXTERNALS__Fields.def_revision.toString(), null);
            }
            return values;
        }
        
    }

}
