package org.tmatesoft.svn.core.internal.wc17.db;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.getColumnPresence;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;

import static org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbStatementUtil.*;

public class SvnWcDbProperties extends SvnWcDbShared {
    
    public static SVNProperties readPristineProperties(SVNWCDbRoot root, File relpath) throws SVNException {
        SVNSqlJetStatement stmt = root.getSDb().getStatement(SVNWCDbStatements.SELECT_NODE_PROPS);
        try {
            stmt.bindf("is", root.getWcId(), relpath);
            if (stmt.next()) {
                nodeNotFound(root.getAbsPath(relpath));
            }
            SVNWCDbStatus presence = getColumnPresence(stmt);
            if (presence == SVNWCDbStatus.BaseDeleted) {
                boolean haveRow = stmt.next();
                assert (haveRow);
                presence = getColumnPresence(stmt);
            }
            if (presence == SVNWCDbStatus.Normal || presence == SVNWCDbStatus.Incomplete) {
                SVNProperties props = getColumnProperties(stmt, SVNWCDbSchema.NODES__Fields.properties);
                if (props == null) {
                    props = new SVNProperties();
                }
                return props;
            }
            return null;
        } finally {
            reset(stmt);
        }
    }
}
