/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.db;

import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public abstract class SVNSqlJetTableStatement extends SVNSqlJetStatement {

    protected ISqlJetTable table;
    protected String tableName;
    
    private Map<String, Integer> checksumTriggerValues;

    public SVNSqlJetTableStatement(SVNSqlJetDb sDb, Enum<?> tableName) throws SVNException {
        this(sDb, tableName.toString());
    }

    public SVNSqlJetTableStatement(SVNSqlJetDb sDb, String tableName) throws SVNException {
        super(sDb);
        this.tableName = tableName;
        try {
            table = sDb.getDb().getTable(tableName);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
        }
        if (isNodesTable()) {
            checksumTriggerValues = new HashMap<String, Integer>();
        }
    }

    public ISqlJetTable getTable() {
        return table;
    }
    
    private boolean isNodesTable() {
        return SVNWCDbSchema.NODES.toString().equals(tableName);
    }
    
    public void updatePristine() throws SqlJetException {
        if (!isNodesTable()) {
            return;
        }
        try {
            if (checksumTriggerValues != null && !checksumTriggerValues.isEmpty()) {
                Map<String, Object> values = new HashMap<String, Object>();
                ISqlJetTable pristineTable = sDb.getDb().getTable(SVNWCDbSchema.PRISTINE.toString());
                for (String checksum : checksumTriggerValues.keySet()) {
                    ISqlJetCursor cursor = pristineTable.lookup(null, checksum);
                    long delta = checksumTriggerValues.get(checksum); 
                    if (delta == 0) {
                        continue;
                    }
                    if (cursor != null && !cursor.eof()) {                        
                        long refcount = cursor.getInteger(SVNWCDbSchema.PRISTINE__Fields.refcount.toString());
                        refcount += delta;
                        if (refcount < 0) {
                            refcount = 0;
                        }
                        values.put(SVNWCDbSchema.PRISTINE__Fields.refcount.toString(), refcount);
                        cursor.updateByFieldNames(values);
                    }
                    cursor.close();
                }
            }
        } finally {
            checksumTriggerValues = new HashMap<String, Integer>();
        }
    }
    
    protected void aboutToDeleteRow() throws SqlJetException {
        if (!isNodesTable()) {
            return;
        }
        String checksumValue = getCursor().getString(NODES__Fields.checksum.toString());
        changeRefCount(checksumValue, -1);
    }

    protected void aboutToInsertRow(SqlJetConflictAction onConflict, Map<String, Object> values) throws SqlJetException {
        if (!isNodesTable()) {
            return;
        }
        if (onConflict == SqlJetConflictAction.REPLACE) {
            Object o1 = values.get(NODES__Fields.wc_id.toString());
            Object o2 = values.get(NODES__Fields.local_relpath.toString());
            Object o3 = values.get(NODES__Fields.op_depth.toString());
            ISqlJetCursor cursor = getTable().lookup(null, new Object[] {o1, o2, o3});
            try { 
                if (!cursor.eof()) {
                    changeRefCount(cursor.getString(NODES__Fields.checksum.toString()), -1);
                }
            } finally {
                cursor.close();
            }
        }
        String newChecksumValue = (String) values.get(NODES__Fields.checksum.toString());
        changeRefCount(newChecksumValue, 1);
    }
    
    protected void aboutToUpdateRow(Map<String, Object> values) throws SqlJetException, SVNException {
        if (!isNodesTable()) {
            return;
        }
        if (values.containsKey(NODES__Fields.checksum.toString())) {
            Map<String, Object> existingValues = getRowValues();
            
            String newChecksum = (String) values.get(NODES__Fields.checksum.toString());
            String oldChecksum = (String) existingValues.get(NODES__Fields.checksum.toString());
            
            changeRefCount(oldChecksum,-1);
            changeRefCount(newChecksum, 1);
        }
    }

    private void changeRefCount(String checksum, int delta) {
        if (checksum != null) {
            if (!checksumTriggerValues.containsKey(checksum)) {
                checksumTriggerValues.put(checksum, delta);
            } else {
                checksumTriggerValues.put(checksum, checksumTriggerValues.get(checksum) + delta);
            }
        }
    }
}
