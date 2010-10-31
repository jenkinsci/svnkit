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
package org.tmatesoft.svn.core.internal.db;

import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.svn.core.SVNException;

/**
 * @author TMate Software Ltd.
 */
public class SVNSqlJetUnionStatement extends SVNSqlJetStatement {

    private SVNSqlJetStatement[] statements;
    private int current = 0;

    public SVNSqlJetUnionStatement(SVNSqlJetDb sDb, SVNSqlJetStatement... statements) {
        super(sDb);
        this.statements = statements;
    }

    public boolean next() throws SVNException {
        if (statements == null) {
            return false;
        }
        boolean next = false;
        while (!next && current < statements.length) {
            SVNSqlJetStatement stmt = statements[current];
            if (stmt != null) {
                next = stmt.next();
            }
            if (!next) {
                current++;
            }
        }
        return next;
    }

    protected ISqlJetCursor getCursor() {
        if (statements == null) {
            return null;
        }
        if (current < statements.length) {
            return statements[current].getCursor();
        }
        return null;
    }

    public void reset() throws SVNException {
        if (statements == null) {
            return;
        }
        for (SVNSqlJetStatement stmt : statements) {
            stmt.reset();
        }
    }
}
