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

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDeleteStatement;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema.NODES__Fields;

/**
 * DELETE FROM nodes
 * WHERE wc_id = ?1
 *  AND (?2 = ''
 *      OR local_relpath = ?2
 *      OR (local_relpath > ?2 || '/' AND local_relpath < ?2 || '0'))
 *  AND op_depth >= ?3
 *
 *
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNWCDbDeleteNodesRecursive extends SVNSqlJetDeleteStatement {

    private Collection<File> paths;
    private Set<String> excludedPresences;

    public SVNWCDbDeleteNodesRecursive(SVNSqlJetDb sDb) throws SVNException {
        super(sDb, SVNWCDbSchema.NODES);
    }

    protected Object[] getWhere() throws SVNException {
        return new Object[] {getBind(1)};
    }

    protected boolean isFilterPassed() throws SVNException {
        final long rowDepth = getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth);
        if (paths != null) {
            final long collectDepth = (Long) getBind(4);
            if (rowDepth >= collectDepth) {
                final String presence = getColumnString(NODES__Fields.presence);
                if (!excludedPresences.contains(presence)) {
                    paths.add(SVNFileUtil.createFilePath(getColumnString(NODES__Fields.local_relpath)));
                }
            }
        } 
        final long selectDepth = (Long) getBind(3);
        if (getColumnLong(SVNWCDbSchema.NODES__Fields.op_depth) < selectDepth) {
            return false;
        }
        return true;
    }

    @Override
    protected String getPathScope() {
        return (String) getBind(2);
    }
    
    public void setCollectPaths(Collection<File> paths) {
        this.paths = paths;
        if (excludedPresences == null) {
            excludedPresences = new HashSet<String>();
            excludedPresences.add("base-deleted");
            excludedPresences.add("not-present");
            excludedPresences.add("excluded");
            excludedPresences.add("absent");
        }
    }

    @Override
    public void reset() throws SVNException {
        paths = null;
        super.reset();
    }
}
