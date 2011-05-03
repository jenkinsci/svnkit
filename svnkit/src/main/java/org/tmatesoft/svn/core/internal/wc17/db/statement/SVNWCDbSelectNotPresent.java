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
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectStatement;

/**
 * SELECT 1 FROM nodes WHERE wc_id = ?1 AND local_relpath = ?2 AND presence =
 * 'not-present' AND op_depth = 0;
 *
 *
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectNotPresent extends SVNSqlJetSelectStatement {

    public SVNWCDbSelectNotPresent(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.NODES);
    }

    protected Object[] getWhere() throws SVNException {
        bindLong(3, 0);
        return super.getWhere();
    }

    protected boolean isFilterPassed() throws SVNException {
        return "not-present".equals(getColumnString(SVNWCDbSchema.NODES__Fields.presence));
    }

}
