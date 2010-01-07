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

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSelectActualNodeStrategy extends SVNAbstractSelectStrategy {
    
    private static final SVNDbTableField[] OUR_ACTUAL_NODE_FIELDS = { 
        SVNDbTableField.prop_reject, 
        SVNDbTableField.changelist, 
        SVNDbTableField.conflict_old, 
        SVNDbTableField.conflict_new, 
        SVNDbTableField.conflict_working, 
        SVNDbTableField.tree_conflict_data, 
        SVNDbTableField.properties 
    };

    private long myWCId;
    private String myLocalRelativePath;

    public SVNSelectActualNodeStrategy(long wcId, String localRelativePath) {
        myWCId = wcId;
        myLocalRelativePath = localRelativePath;
    }
    
    public void reset(long wcId, String localRelativePath) {
        myWCId = wcId;
        myLocalRelativePath = localRelativePath;
    }

    protected ISqlJetCursor getCursor(ISqlJetTable table) throws SqlJetException {
        return table.lookup(table.getPrimaryKeyIndexName(), myWCId, myLocalRelativePath);
    }

    protected SVNDbTableField[] getFieldNames() {
        return OUR_ACTUAL_NODE_FIELDS;
    }

}
