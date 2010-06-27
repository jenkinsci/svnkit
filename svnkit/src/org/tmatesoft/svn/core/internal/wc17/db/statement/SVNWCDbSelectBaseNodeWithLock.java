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

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetSelectFieldsStatement;
import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDbSchema;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * select base_node.repos_id, base_node.repos_relpath, presence, kind, revnum,
 * checksum, translated_size, changed_rev, changed_date, changed_author, depth,
 * symlink_target, last_mod_time, properties, lock_token, lock_owner,
 * lock_comment, lock_date from base_node left outer join lock on
 * base_node.repos_id = lock.repos_id and base_node.repos_relpath =
 * lock.repos_relpath where wc_id = ?1 and local_relpath = ?2;
 * 
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectBaseNodeWithLock extends SVNWCDbSelectBaseNodeStatement {

    private static class LockStatement extends SVNSqlJetSelectFieldsStatement<SVNWCDbSchema.LOCK__Fields> {

        public LockStatement(SVNSqlJetDb sDb) throws SVNException {
            super(sDb, SVNWCDbSchema.LOCK);
        }

        protected void defineFields() {
            fields.add(SVNWCDbSchema.LOCK__Fields.lock_token);
            fields.add(SVNWCDbSchema.LOCK__Fields.lock_owner);
            fields.add(SVNWCDbSchema.LOCK__Fields.lock_comment);
            fields.add(SVNWCDbSchema.LOCK__Fields.lock_date);
        }

        public boolean isColumnNull(String f) throws SVNException {
            return super.isColumnNull(f);
        }

    }

    private LockStatement lockStatement;

    public SVNWCDbSelectBaseNodeWithLock(SVNSqlJetDb sDb) throws SVNException {
        super(sDb);
        lockStatement = new LockStatement(sDb);
    }

    public boolean next() throws SVNException {
        lockStatement.reset();
        final boolean next = super.next();
        if (next) {
            lockStatement.bindLong(0, getColumnLong(SVNWCDbSchema.BASE_NODE__Fields.repos_id.toString()));
            lockStatement.bindString(1, getColumnString(SVNWCDbSchema.BASE_NODE__Fields.repos_relpath.toString()));
            lockStatement.next();
        }
        return next;
    }

    public void reset() throws SVNException {
        try {
            lockStatement.reset();
        } finally {
            super.reset();
        }
    }

    public SVNSqlJetStatement getJoinedStatement(String joinedTable) throws SVNException {
        if (SVNWCDbSchema.LOCK.toString().equalsIgnoreCase(joinedTable)) {
            return lockStatement;
        }
        return super.getJoinedStatement(joinedTable);
    }

}
