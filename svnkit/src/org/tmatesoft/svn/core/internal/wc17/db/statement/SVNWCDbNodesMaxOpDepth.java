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
 *
 * @author TMate Software Ltd.
 */
public class SVNWCDbNodesMaxOpDepth extends SVNSqlJetSelectStatement {

    private Long wcId;
    private String localRelpath;

    public SVNWCDbNodesMaxOpDepth(SVNSqlJetDb sDb, Long wcId, String localRelpath) throws SVNException {
        super(sDb, SVNWCDbSchema.NODES);
        this.wcId = wcId;
        this.localRelpath = localRelpath;
    }

    protected boolean isFilterPassed() throws SVNException {
        return getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth) > 0;
    }

    public Long getMaxOpDepth() throws SVNException {
        try {
            bindLong(1, wcId);
            bindString(2, localRelpath);
            long maxOpDepth = Long.MIN_VALUE;
            boolean empty = true;
            while (next()) {
                if (empty) {
                    empty = false;
                }
                long opDepth = getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth);
                if (maxOpDepth < opDepth) {
                    maxOpDepth = opDepth;
                }
            }
            if (empty) {
                return null;
            }
            return maxOpDepth;
        } finally {
            reset();
        }
    }

}
