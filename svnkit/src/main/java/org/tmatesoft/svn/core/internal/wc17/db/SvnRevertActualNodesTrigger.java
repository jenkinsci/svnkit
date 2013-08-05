package org.tmatesoft.svn.core.internal.wc17.db;

import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.internal.db.ISVNSqlJetTrigger;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.ACTUAL_NODE__Fields;

public class SvnRevertActualNodesTrigger implements ISVNSqlJetTrigger {
    
    private SVNSqlJetDb db;

    public SvnRevertActualNodesTrigger(SVNSqlJetDb db) {
        this.db = db;
    }
    
    public void beforeDelete(ISqlJetCursor cursor) throws SqlJetException {
        final SvnWcDbRevertList revertList = db.getRevertList();
        final long notify;
        if (!cursor.isNull(ACTUAL_NODE__Fields.properties.toString()) 
                || !cursor.isNull(ACTUAL_NODE__Fields.tree_conflict_data.toString())) {
            notify = 1;
        } else {
            notify = 0;
        }
        
        revertList.insertRow(cursor.getString(ACTUAL_NODE__Fields.local_relpath.toString()), 
                1, 
                cursor.getString(ACTUAL_NODE__Fields.conflict_old.toString()), 
                cursor.getString(ACTUAL_NODE__Fields.conflict_new.toString()), 
                cursor.getString(ACTUAL_NODE__Fields.conflict_working.toString()), 
                cursor.getString(ACTUAL_NODE__Fields.prop_reject.toString()), 
                notify);
        
    }

    public void beforeUpdate(ISqlJetCursor cursor, Map<String, Object> newValues) throws SqlJetException {
        final SvnWcDbRevertList revertList = db.getRevertList();
        final long notify;
        if (!cursor.isNull(ACTUAL_NODE__Fields.properties.toString()) 
                || !cursor.isNull(ACTUAL_NODE__Fields.tree_conflict_data.toString())) {
            notify = 1;
        } else {
            notify = 0;
        }
        revertList.insertRow(cursor.getString(ACTUAL_NODE__Fields.local_relpath.toString()), 
                1, 
                cursor.getString(ACTUAL_NODE__Fields.conflict_old.toString()), 
                cursor.getString(ACTUAL_NODE__Fields.conflict_new.toString()), 
                cursor.getString(ACTUAL_NODE__Fields.conflict_working.toString()), 
                cursor.getString(ACTUAL_NODE__Fields.prop_reject.toString()), 
                notify);
    }

    public void beforeInsert(SqlJetConflictAction conflictAction, ISqlJetTable table, Map<String, Object> newValues) throws SqlJetException {
    }

    public void statementStarted(SqlJetDb db) throws SqlJetException {
        this.db.getDb().getTemporaryDatabase().beginTransaction(SqlJetTransactionMode.WRITE);
    }

    public void statementCompleted(SqlJetDb db, SqlJetException error) throws SqlJetException {
        if (error == null) {
            this.db.getDb().getTemporaryDatabase().commit();
        } else {
            this.db.getDb().getTemporaryDatabase().rollback();
        }
    }

}
