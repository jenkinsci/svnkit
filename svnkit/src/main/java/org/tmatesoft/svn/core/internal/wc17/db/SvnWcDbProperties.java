package org.tmatesoft.svn.core.internal.wc17.db;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPresence;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnProperties;
import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.isColumnNull;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetInsertStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbCreateSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbNodesBase;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbNodesCurrent;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODE_PROPS_CACHE__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.TARGETS_LIST__Fields;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.ACTUAL_NODE__Fields;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.*;

public class SvnWcDbProperties extends SvnWcDbShared {
    
    public static SVNProperties readProperties(SVNWCDbRoot root, File relpath) throws SVNException {
        SVNProperties props = null;
        SVNSqlJetStatement stmt = null;
        try {
            stmt = root.getSDb().getStatement(SVNWCDbStatements.SELECT_ACTUAL_PROPS);
            stmt.bindf("is", root.getWcId(), relpath);
            if (stmt.next() && !isColumnNull(stmt, ACTUAL_NODE__Fields.properties)) {
                props = getColumnProperties(stmt, ACTUAL_NODE__Fields.properties);
            } 
            if (props != null) {
                return props;
            }
            props = readPristineProperties(root, relpath);
            if (props == null) {
                return new SVNProperties();
            }
        } finally {
            reset(stmt);
        }        return props;
    }
    
    public static SVNProperties readPristineProperties(SVNWCDbRoot root, File relpath) throws SVNException {
        SVNSqlJetStatement stmt = root.getSDb().getStatement(SVNWCDbStatements.SELECT_NODE_PROPS);
        try {
            stmt.bindf("is", root.getWcId(), relpath);
            if (!stmt.next()) {
                nodeNotFound(root.getAbsPath(relpath));
            }
            SVNWCDbStatus presence = getColumnPresence(stmt);
            if (presence == SVNWCDbStatus.BaseDeleted) {
                boolean haveRow = stmt.next();
                assert (haveRow);
                presence = getColumnPresence(stmt);
            }
            if (presence == SVNWCDbStatus.Normal || presence == SVNWCDbStatus.Incomplete) {
                SVNProperties props = getColumnProperties(stmt, SVNWCDbSchema.NODES__Fields.properties);
                if (props == null) {
                    props = new SVNProperties();
                }
                return props;
            }
            return null;
        } finally {
            reset(stmt);
        }
    }
    
    public static void readPropertiesRecursively(SVNWCDbRoot root, File relpath, SVNDepth depth, boolean baseProperties, boolean pristineProperties, Collection<String> changelists,
            ISvnObjectReceiver<SVNProperties> receiver) throws SVNException {
        SVNSqlJetSelectStatement stmt = null;

        root.getSDb().getTemporaryDb().beginTransaction(SqlJetTransactionMode.WRITE);        
        try {
            try {
                cacheProperties(root, relpath, depth, baseProperties, pristineProperties, changelists);            
                stmt = new SVNSqlJetSelectStatement(root.getSDb().getTemporaryDb(), SVNWCDbSchema.NODE_PROPS_CACHE);
                while(stmt.next()) {
                    SVNProperties props = getColumnProperties(stmt, NODE_PROPS_CACHE__Fields.properties);
                    File target = getColumnPath(stmt, NODE_PROPS_CACHE__Fields.local_Relpath);
                    
                    File absolutePath = root.getAbsPath(target);
                    receiver.receive(SvnTarget.fromFile(absolutePath), props);
                }            
            } finally {        
                reset(stmt);
                SVNSqlJetStatement dropCache = new SVNWCDbCreateSchema(root.getSDb().getTemporaryDb(), SVNWCDbCreateSchema.DROP_NODE_PROPS_CACHE, -1);
                dropCache.done();
            }
            root.getSDb().getTemporaryDb().commit();
        } catch (SVNException e) {
            root.getSDb().getTemporaryDb().rollback();
            throw e;
        } 
    }
    
    private static void cacheProperties(SVNWCDbRoot root, File relpath, SVNDepth depth, boolean baseProperties, boolean pristineProperties, Collection<String> changelists) throws SVNException {
        SVNSqlJetStatement stmt = null;
        InsertIntoPropertiesCache insertStmt = null;
        SVNSqlJetSelectStatement propertiesSelectStmt = null;
        
        root.getSDb().beginTransaction(SqlJetTransactionMode.READ_ONLY);
        try {
            collectTargets(root, relpath, depth, changelists);
            stmt = new SVNWCDbCreateSchema(root.getSDb().getTemporaryDb(), SVNWCDbCreateSchema.NODE_PROPS_CACHE, -1);
            stmt.done();
            
            if (baseProperties) {
                propertiesSelectStmt = new SVNWCDbNodesBase(root.getSDb());
            } else if (pristineProperties) {
                propertiesSelectStmt = new SVNWCDbNodesCurrent(root.getSDb());
            } else {
                propertiesSelectStmt = new SVNWCDbNodesCurrent(root.getSDb());
            }
            
            insertStmt = new InsertIntoPropertiesCache(root.getSDb().getTemporaryDb());
            
            stmt = new SVNSqlJetSelectStatement(root.getSDb().getTemporaryDb(), SVNWCDbSchema.TARGETS_LIST);
            stmt.bindf("i", root.getWcId());
            
            while(stmt.next()) {
                String localRelpath = getColumnText(stmt, TARGETS_LIST__Fields.local_relpath);
                long wcId = getColumnInt64(stmt, TARGETS_LIST__Fields.wc_id);
                String kind = getColumnText(stmt, TARGETS_LIST__Fields.kind);
                
                propertiesSelectStmt.bindf("is", wcId, localRelpath);
                byte[] props = null;
                try {
                    if (propertiesSelectStmt.next()) {
                        SVNWCDbStatus presence = getColumnPresence(propertiesSelectStmt);
                        if (baseProperties) {
                            if (presence == SVNWCDbStatus.Normal || presence == SVNWCDbStatus.Incomplete) {
                                props = getColumnBlob(propertiesSelectStmt, SVNWCDbSchema.NODES__Fields.properties);
                            }
                        } else if (pristineProperties) {
                            if (presence == SVNWCDbStatus.Normal || presence == SVNWCDbStatus.Incomplete || presence == SVNWCDbStatus.BaseDeleted) {
                                props = getColumnBlob(propertiesSelectStmt, NODES__Fields.properties);
                            }
                            if (props == null) {
                                long rowOpDepth = getColumnInt64(propertiesSelectStmt, NODES__Fields.op_depth);
                                if (rowOpDepth > 0) {
                                    SelectRowWithMaxOpDepth query = new SelectRowWithMaxOpDepth(root.getSDb(), rowOpDepth);
                                    try {
                                        query.bindf("is", wcId, localRelpath);
                                        if (query.next()) {
                                            props = getColumnBlob(query, NODES__Fields.properties);
                                        }
                                    } finally {
                                        reset(query);
                                    }
                                }
                            }
                        } else {
                            if (presence == SVNWCDbStatus.Normal || presence == SVNWCDbStatus.Incomplete) {
                                props = getColumnBlob(propertiesSelectStmt, NODES__Fields.properties);
                            }
                            SVNSqlJetSelectStatement query = new SVNSqlJetSelectStatement(root.getSDb(), SVNWCDbSchema.ACTUAL_NODE);
                            byte[] actualProps = null;
                            try {
                                query.bindf("is", wcId, localRelpath);
                                if (query.next()) {
                                    actualProps = getColumnBlob(query, ACTUAL_NODE__Fields.properties);
                                }
                            } finally {
                                reset(query);
                            }
                            props = actualProps != null ? actualProps : props;
                        }
                    }
                } finally {
                    reset(propertiesSelectStmt);
                }
                
                if (props != null && props.length > 2) {
                    try {
                        insertStmt.putInsertValue(NODE_PROPS_CACHE__Fields.local_Relpath, localRelpath);
                        insertStmt.putInsertValue(NODE_PROPS_CACHE__Fields.kind, kind);
                        insertStmt.putInsertValue(NODE_PROPS_CACHE__Fields.properties, props);
                        
                        insertStmt.exec();
                    } finally {
                        insertStmt.reset();
                    }
                }
            }
        } finally {
            try {
                reset(stmt);
                reset(insertStmt);
                reset(propertiesSelectStmt);

                SVNSqlJetStatement dropTargets = new SVNWCDbCreateSchema(root.getSDb().getTemporaryDb(), SVNWCDbCreateSchema.DROP_TARGETS_LIST, -1);
                dropTargets.done();
            } finally {
                root.getSDb().commit();
            }
        }
    }
    
    /*
     * SELECT properties FROM nodes nn
                 WHERE n.presence = 'base-deleted'
                   AND nn.wc_id = n.wc_id
                   AND nn.local_relpath = n.local_relpath
                   AND nn.op_depth < n.op_depth
                 ORDER BY op_depth DESC
     */
    private static class SelectRowWithMaxOpDepth extends SVNSqlJetSelectStatement {

        private long opDepth;

        public SelectRowWithMaxOpDepth(SVNSqlJetDb sDb, long opDepth) throws SVNException {
            super(sDb, SVNWCDbSchema.NODES);
            this.opDepth = opDepth;
        }
        @Override
        protected ISqlJetCursor openCursor() throws SVNException {
            try {
                return super.openCursor().reverse();
            } catch (SqlJetException e) {
                return null;
            }
        }
        @Override
        protected boolean isFilterPassed() throws SVNException {
            
            long rowOpDepth = getColumnLong(NODES__Fields.op_depth);
            String precense = getColumnString(NODES__Fields.presence);
            
            return rowOpDepth < opDepth && "base-deleted".equals(precense); 
        }
    }
    
    private static class InsertIntoPropertiesCache extends SVNSqlJetInsertStatement {
        
        private HashMap<String, Object> insertValues;

        public InsertIntoPropertiesCache(SVNSqlJetDb sDb) throws SVNException {
            super(sDb, SVNWCDbSchema.NODE_PROPS_CACHE);
            insertValues = new HashMap<String, Object>();
        }
        
        public void putInsertValue(Enum<?> f, Object value) {
            insertValues.put(f.toString(), value);
        }
        
        @Override
        public void reset() throws SVNException {
            super.reset();
            insertValues.clear();
        }

        @Override
        protected Map<String, Object> getInsertValues() throws SVNException {
            return insertValues;
        }
    }
}
