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
package org.tmatesoft.svn.core.internal.wc.db;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNAbstractSelectStrategy {

    public Map<SVNDbTableField, Object> runSelect(ISqlJetTable table) throws SqlJetException {
        SqlJetDb sdb = table.getDataBase();
        Map<SVNDbTableField, Object> result = Collections.EMPTY_MAP;
        try {
            ISqlJetCursor cursor = getCursor(table);
            try {
                if (!cursor.eof()) {
                    result = new HashMap<SVNDbTableField, Object>();
                    for(SVNDbTableField field : getFieldNames()) {
                        result.put(field, cursor.getValue(field.toString()));
                    }            
                } 
            } finally {
                cursor.close();
            }
        } finally {
            sdb.commit();
        }

        return result;
    }

    protected abstract SVNDbTableField[] getFieldNames(); 
    
    protected abstract ISqlJetCursor getCursor(ISqlJetTable table) throws SqlJetException;
    
}
