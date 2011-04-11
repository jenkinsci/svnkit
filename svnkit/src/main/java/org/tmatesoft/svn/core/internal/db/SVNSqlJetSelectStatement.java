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
import java.util.List;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetValueType;
import org.tmatesoft.sqljet.core.schema.ISqlJetColumnDef;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNSqlJetSelectStatement extends SVNSqlJetTableStatement {

    private String indexName;

    public SVNSqlJetSelectStatement(SVNSqlJetDb sDb, Enum fromTable) throws SVNException {
        this(sDb, fromTable.toString());
    }

    public SVNSqlJetSelectStatement(SVNSqlJetDb sDb, Enum fromTable, Enum indexName) throws SVNException {
        this(sDb, fromTable.toString(), indexName.toString());
    }

    public SVNSqlJetSelectStatement(SVNSqlJetDb sDb, String fromTable) throws SVNException {
        super(sDb, fromTable);
    }

    public SVNSqlJetSelectStatement(SVNSqlJetDb sDb, String fromTable, String indexName) throws SVNException {
        this(sDb, fromTable);
        this.indexName = indexName;
    }

    protected ISqlJetCursor openCursor() throws SVNException {
        try {
            return getTable().lookup(getIndexName(), getWhere());
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    protected String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    protected Object[] getWhere() throws SVNException {
        if (binds.size() == 0) {
            return null;
        }
        return binds.toArray();
    }

    public boolean next() throws SVNException {
        boolean next = super.next();
        while (next && !isFilterPassed()) {
            next = super.next();
        }
        return next;
    }

    protected boolean isFilterPassed() throws SVNException {
        return true;
    }

    public boolean eof() throws SVNException {
        boolean eof = super.eof();
        while (!eof && !isFilterPassed()) {
            eof = !super.next();
        }
        return eof;
    }

    public Map<String, Object> getRowValues() throws SVNException {
        HashMap<String, Object> v = new HashMap<String, Object>();
        try {
            List<ISqlJetColumnDef> columns = getTable().getDefinition().getColumns();
            for (ISqlJetColumnDef column : columns) {
                String colName = column.getName();
                SqlJetValueType fieldType = getCursor().getFieldType(colName);
                if (fieldType == SqlJetValueType.NULL) {
                    v.put(colName, null);
                } else if (fieldType == SqlJetValueType.BLOB) {
                    v.put(colName, getCursor().getBlobAsArray(colName));
                } else {
                    v.put(colName, getCursor().getValue(colName));
                }
            }
            return v;
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

}
