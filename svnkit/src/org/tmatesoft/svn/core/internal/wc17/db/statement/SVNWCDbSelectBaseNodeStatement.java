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
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDbSchema;

/**
 * select repos_id, repos_relpath, presence, kind, revnum, checksum,
 * translated_size, changed_rev, changed_date, changed_author, depth,
 * symlink_target, last_mod_time, properties from base_node where wc_id = ?1 and
 * local_relpath = ?2;
 * 
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectBaseNodeStatement extends SVNSqlJetSelectFieldsStatement<SVNWCDbSchema.BASE_NODE__Fields> {

    public SVNWCDbSelectBaseNodeStatement(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.BASE_NODE);
    }

    protected void defineFields() {
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.repos_id);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.repos_relpath);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.presence);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.kind);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.revnum);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.checksum);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.translated_size);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.changed_rev);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.changed_date);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.changed_author);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.depth);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.symlink_target);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.last_mod_time);
        fields.add(SVNWCDbSchema.BASE_NODE__Fields.properties);
    }

}
