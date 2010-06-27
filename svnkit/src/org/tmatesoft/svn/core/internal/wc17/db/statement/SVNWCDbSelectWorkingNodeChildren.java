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
import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetSelectFieldsStatement;
import org.tmatesoft.svn.core.internal.wc17.db.SVNSqlJetSelectStatement;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDbSchema;

/**
 * select local_relpath from working_node where wc_id = ?1 and parent_relpath =
 * ?2;
 * 
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectWorkingNodeChildren extends SVNSqlJetSelectFieldsStatement<SVNWCDbSchema.WORKING_NODE__Fields> {

    public SVNWCDbSelectWorkingNodeChildren(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.WORKING_NODE);
    }

    protected String getIndexName() {
        return SVNWCDbSchema.WORKING_NODE__Indices.I_WORKING_PARENT.toString();
    }
    
    protected void defineFields() {
        fields.add(SVNWCDbSchema.WORKING_NODE__Fields.local_relpath);
    }

}
