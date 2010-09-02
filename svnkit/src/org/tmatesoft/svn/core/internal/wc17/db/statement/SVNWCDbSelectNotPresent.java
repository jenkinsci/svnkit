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
 * select 1 from base_node where wc_id = ?1 and local_relpath = ?2 and presence
 * = 'not-present';
 * 
 * 
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectNotPresent extends SVNSqlJetSelectStatement {

    public SVNWCDbSelectNotPresent(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.BASE_NODE);
    }

    protected boolean isFilterPassed() throws SVNException {
        return "not-present".equals(getColumnString(SVNWCDbSchema.BASE_NODE__Fields.presence));
    }

}
