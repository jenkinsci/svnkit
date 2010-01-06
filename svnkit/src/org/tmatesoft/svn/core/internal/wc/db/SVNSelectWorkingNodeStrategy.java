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
package org.tmatesoft.svn.core.internal.wc.db;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNSelectWorkingNodeStrategy extends SVNAbstractSelectStrategy {

    private static final SVNDbTableField[] OUR_WORKING_NODE_FIELDS = { SVNDbTableField.presence, SVNDbTableField.kind, SVNDbTableField.checksum, 
        SVNDbTableField.translated_size, SVNDbTableField.changed_rev, SVNDbTableField.changed_date, SVNDbTableField.changed_author, 
        SVNDbTableField.depth, SVNDbTableField.symlink_target, SVNDbTableField.copyfrom_repos_id, SVNDbTableField.copyfrom_repos_path, 
        SVNDbTableField.copyfrom_revnum, SVNDbTableField.moved_here, SVNDbTableField.moved_to, SVNDbTableField.last_mod_time, SVNDbTableField.properties};

    private long myWCId;
    private String myLocalRelativePath;
    
    public SVNSelectWorkingNodeStrategy(long wcId, String localRelativePath) {
        myWCId = wcId;
        myLocalRelativePath = localRelativePath;
    }
    
    public void reset(long wcId, String localRelativePath) {
        myWCId = wcId;
        myLocalRelativePath = localRelativePath;
    }
    
    protected ISqlJetCursor getCursor(ISqlJetTable table) throws SqlJetException {
        return table.lookup(table.getPrimaryKeyIndexName(), myWCId, myLocalRelativePath);
    }

    protected SVNDbTableField[] getFieldNames() {
        return OUR_WORKING_NODE_FIELDS;
    }

}
