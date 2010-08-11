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
import org.tmatesoft.svn.core.internal.db.SVNSqlJetSelectFieldsStatement;

/**
 * select kind == 'file' from working_node where wc_id = ?1 and local_relpath =
 * ?2;
 *
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectWorkingIsFile extends SVNSqlJetSelectFieldsStatement<SVNWCDbSchema.WORKING_NODE__Fields> {

    public SVNWCDbSelectWorkingIsFile(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.WORKING_NODE);
    }

    protected void defineFields() {
        fields.add(SVNWCDbSchema.WORKING_NODE__Fields.kind);
    }

    public boolean getColumnBoolean(int f) throws SVNException {
        return !isColumnNull(SVNWCDbSchema.WORKING_NODE__Fields.kind) && "file".equals(getColumnString(SVNWCDbSchema.WORKING_NODE__Fields.kind));
    }

}
