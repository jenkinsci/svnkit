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
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSelectParentStubInfoStrategy extends SVNAbstractSelectStrategy {
    private static final SVNDbTableField[] OUR_SELECT_PARENT_STUB_INFO_FIELDS = { 
        SVNDbTableField.presence, 
        SVNDbTableField.revnum 
    };

    private long myWCId;
    private String myLocalRelativePath;
    
    public SVNSelectParentStubInfoStrategy(long wcId, String localRelativePath) {
        myWCId = wcId;
        myLocalRelativePath = localRelativePath;
    }

    public void reset(long wcId, String localRelativePath) {
        myWCId = wcId;
        myLocalRelativePath = localRelativePath;
    }

    public Map<SVNDbTableField, Object> runSelect(ISqlJetTable table) throws SqlJetException {
        Map<SVNDbTableField, Object> result = super.runSelect(table);
        String presence = (String) result.get(SVNDbTableField.presence);
        SVNWCDbStatus status = SVNWCDbStatus.parseStatus(presence);
        if (status == SVNWCDbStatus.NOT_PRESENT) {
            return Collections.EMPTY_MAP;
        }
        result.put(SVNDbTableField.presence, status);
        return result;
    }
    
    protected ISqlJetCursor getCursor(ISqlJetTable table) throws SqlJetException {
        return table.lookup(table.getPrimaryKeyIndexName(), myWCId, myLocalRelativePath);
    }

    protected SVNDbTableField[] getFieldNames() {
        return OUR_SELECT_PARENT_STUB_INFO_FIELDS;
    }

}
