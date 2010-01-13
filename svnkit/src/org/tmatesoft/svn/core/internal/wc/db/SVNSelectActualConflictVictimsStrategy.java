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


import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSelectActualConflictVictimsStrategy extends SVNAbstractDbStrategy {
    
    private long myWCId;
    private String myParentRelativePath;
    
    public SVNSelectActualConflictVictimsStrategy(long wcId, String parentRelativePath) {
        super();
        myWCId = wcId;
        myParentRelativePath = parentRelativePath;
    }

    public void reset(long wcId, String parentRelativePath) {
        myWCId = wcId;
        myParentRelativePath = parentRelativePath;
    }

    @Override
    public Object runSelect(ISqlJetTable table) throws SqlJetException {
        Map<String, String> foundVictims = new HashMap<String, String>();
        ISqlJetCursor actualNodeCursor = getCursor(table);
        try {
            if (!actualNodeCursor.eof()) {
                do {
                    if (actualNodeCursor.isNull(SVNDbTableField.prop_reject.toString()) && 
                            actualNodeCursor.isNull(SVNDbTableField.conflict_old.toString()) && 
                            actualNodeCursor.isNull(SVNDbTableField.conflict_new.toString()) && 
                            actualNodeCursor.isNull(SVNDbTableField.conflict_working.toString())) {
                        continue;
                    }
                    
                    String childRelPath = actualNodeCursor.getString(SVNDbTableField.local_relpath.toString());
                    String childName = SVNPathUtil.tail(childRelPath);
                    foundVictims.put(childName, childName);
                } while (actualNodeCursor.next());
            } 
        } finally {
            actualNodeCursor.close();
        }
        return foundVictims;
    }

    @Override
    protected ISqlJetCursor getCursor(ISqlJetTable table) throws SqlJetException {
        return table.lookup(SVNDbIndexes.i_actual_parent.toString(), myWCId, myParentRelativePath);
    }

    @Override
    protected SVNDbTableField[] getFieldNames() {
        //don't need this here
        return null;
    }

}
