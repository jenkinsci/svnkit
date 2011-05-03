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
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetUnionStatement;

/**
 * SELECT 0 FROM nodes WHERE wc_id = ?1 AND local_relpath = ?2 AND op_depth = 0
 * UNION SELECT 1 FROM nodes WHERE wc_id = ?1 AND local_relpath = ?2 AND
 * op_depth = (SELECT MAX(op_depth) FROM nodes WHERE wc_id = ?1 AND
 * local_relpath = ?2 AND op_depth > 0);
 *
 * @author TMate Software Ltd.
 */
public class SVNWCDbDetermineTreeForRecording extends SVNSqlJetUnionStatement {

    public SVNWCDbDetermineTreeForRecording(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, getSelect0(sDb), getSelect1(sDb));
    }

    private static SVNSqlJetStatement getSelect0(SVNSqlJetDb sDb) throws SVNException {
        return new SVNSqlJetSelectStatement(sDb, SVNWCDbSchema.NODES) {

            public long getColumnLong(Enum f) throws SVNException {
                return 0;
            }

            protected Object[] getWhere() throws SVNException {
                bindLong(3, 0);
                return super.getWhere();
            }

        };
    }

    private static SVNSqlJetStatement getSelect1(SVNSqlJetDb sDb) throws SVNException {
        return new SVNSqlJetSelectStatement(sDb, SVNWCDbSchema.NODES) {

            private SVNWCDbNodesMaxOpDepth myMaxOpDepth = new SVNWCDbNodesMaxOpDepth(sDb);

            public long getColumnLong(Enum f) throws SVNException {
                return 1;
            }

            protected Object[] getWhere() throws SVNException {
                Long maxOpDepth = myMaxOpDepth.getMaxOpDepth((Long) binds.get(0), (String) binds.get(1));
                if (maxOpDepth != null) {
                    bindLong(3, maxOpDepth);
                } else {
                    bindNull(3);
                }
                return super.getWhere();
            }

        };

    }
}
