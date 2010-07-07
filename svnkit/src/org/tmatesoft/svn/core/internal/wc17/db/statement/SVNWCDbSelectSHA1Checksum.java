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
import org.tmatesoft.svn.core.internal.wc17.db.sqljet.SVNSqlJetSelectFieldsStatement;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.PRISTINE__Fields;

/**
 * SELECT checksum FROM pristine WHERE md5_checksum = ?1
 * 
 * @author TMate Software Ltd.
 */
public class SVNWCDbSelectSHA1Checksum extends SVNSqlJetSelectFieldsStatement<SVNWCDbSchema.PRISTINE__Fields> {

    public SVNWCDbSelectSHA1Checksum(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.PRISTINE);
    }

    protected void defineFields() {
        fields.add(PRISTINE__Fields.checksum);
    }

}
