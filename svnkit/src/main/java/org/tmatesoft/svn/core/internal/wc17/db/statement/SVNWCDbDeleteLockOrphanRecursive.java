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
package org.tmatesoft.svn.core.internal.wc17.db.statement;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDeleteStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectStatement;

/**
 * DELETE FROM wc_lock WHERE wc_id = ?1 AND local_dir_relpath = ?2;
 *
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNWCDbDeleteLockOrphanRecursive extends SVNSqlJetDeleteStatement {
    
    SVNSqlJetSelectStatement select;

    public SVNWCDbDeleteLockOrphanRecursive(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.WC_LOCK);
        select = new SVNSqlJetSelectStatement(sDb, SVNWCDbSchema.NODES);
    }

    @Override
    protected Object[] getWhere() throws SVNException {
        return new Object[] {getBind(1)};
    }

    @Override
    protected boolean isFilterPassed() throws SVNException {
        if (isColumnNull(SVNWCDbSchema.WC_LOCK__Fields.local_dir_relpath)) {
            return false;
        }
        
        String rowPath = getColumnString(SVNWCDbSchema.WC_LOCK__Fields.local_dir_relpath);
        String selectPath = getBind(2).toString();
        
        if (rowPath.equals(selectPath) || rowPath.startsWith(selectPath + '/')) {            
            select.reset();
            select.bindf("is", 
                    getColumnLong(SVNWCDbSchema.WC_LOCK__Fields.wc_id), 
                    rowPath);
            return !select.next();
        }
        return false;
    }
    
    
    
    

}
