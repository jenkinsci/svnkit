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
import org.tmatesoft.svn.core.internal.wc17.db.sqljet.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.wc17.db.sqljet.SVNSqlJetSelectStatement;
import org.tmatesoft.svn.core.internal.wc17.db.sqljet.SVNSqlJetStatement;

/**
 * select base_node.presence, working_node.presence, moved_to from working_node
 * left outer join base_node on base_node.wc_id = working_node.wc_id and
 * base_node.local_relpath = working_node.local_relpath where working_node.wc_id
 * = ?1 and working_node.local_relpath = ?2;
 *
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectDeletionInfo extends SVNSqlJetSelectStatement {

    public SVNWCDbSelectDeletionInfo(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.WORKING_NODE);
    }

    public SVNSqlJetStatement getJoinedStatement(String joinedTable) throws SVNException {
        if (!eof() && SVNWCDbSchema.BASE_NODE.toString().equals(joinedTable)) {
            SVNSqlJetSelectStatement baseNodeStmt = new SVNSqlJetSelectStatement(sDb, SVNWCDbSchema.BASE_NODE);
            baseNodeStmt.bindLong(1, getColumnLong(SVNWCDbSchema.WORKING_NODE__Fields.wc_id));
            baseNodeStmt.bindString(2, getColumnString(SVNWCDbSchema.WORKING_NODE__Fields.local_relpath));
            return baseNodeStmt;
        }
        return super.getJoinedStatement(joinedTable);
    }

}
