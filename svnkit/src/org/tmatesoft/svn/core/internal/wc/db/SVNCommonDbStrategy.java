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
public class SVNCommonDbStrategy extends SVNAbstractDbStrategy {

    //null index implies a primary index
    private SVNDbIndexes myIndex;
    private Object[] myLookUpObjects;
    private SVNDbTableField[] myFields;
    
    public SVNCommonDbStrategy(SVNDbIndexes index, Object[] lookUpObjects, SVNDbTableField[] fields) {
        super();
        myIndex = index;
        myLookUpObjects = lookUpObjects;
        myFields = fields;
    }

    public SVNCommonDbStrategy(Object[] lookUpObjects, SVNDbTableField[] fields) {
        super();
        myLookUpObjects = lookUpObjects;
        myFields = fields;
    }

    public SVNCommonDbStrategy(Object[] lookUpObjects) {
        super();
        myLookUpObjects = lookUpObjects;
    }

    public void reset(SVNDbIndexes index, Object[] lookUpObjects, SVNDbTableField[] fields) {
        myIndex = index;
        myLookUpObjects = lookUpObjects;
        myFields = fields;
    }

    public void reset(Object[] lookUpObjects, SVNDbTableField[] fields) {
        myLookUpObjects = lookUpObjects;
        myFields = fields;
    }
    
    public void reset(Object[] lookUpObjects) {
        myLookUpObjects = lookUpObjects;
    }

    @Override
    protected ISqlJetCursor getCursor(ISqlJetTable table) throws SqlJetException {
        if (myLookUpObjects == null) {
            return table.open();
        }
        return table.lookup(myIndex == null ? table.getPrimaryKeyIndexName() : myIndex.toString(), myLookUpObjects);
    }

    @Override
    protected SVNDbTableField[] getFieldNames() {
        return myFields;
    }

    protected Object[] getLookUpObjects() {
        return myLookUpObjects;
    }
}
