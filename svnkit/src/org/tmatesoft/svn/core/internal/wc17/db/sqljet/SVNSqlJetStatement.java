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
package org.tmatesoft.svn.core.internal.wc17.db.sqljet;

import java.util.ArrayList;
import java.util.List;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 */
public abstract class SVNSqlJetStatement {

    protected SVNSqlJetDb sDb;
    protected ISqlJetCursor cursor;
    protected List binds = new ArrayList();

    protected ISqlJetCursor openCursor() throws SVNException {
        throw new UnsupportedOperationException();
    }

    public long insert(Object... data) {
        throw new UnsupportedOperationException();
    }

    public void exec() {
        throw new UnsupportedOperationException();
    }

    public SVNSqlJetStatement(SVNSqlJetDb sDb) {
        this.sDb = sDb;
        cursor = null;
    }

    public List getBinds() {
        return binds;
    }

    public boolean isNeedsReset() {
        return cursor != null;
    }

    public void reset() throws SVNException {
        binds.clear();
        if (isNeedsReset()) {
            try {
                cursor.close();
            } catch (SqlJetException e) {
                SVNSqlJetDb.createSqlJetError(e);
            } finally {
                cursor = null;
                sDb.commit();
            }
        }
    }

    public boolean next() throws SVNException {
        try {
            if (cursor == null) {
                sDb.beginTransaction(SqlJetTransactionMode.READ_ONLY);
                cursor = openCursor();
                return !cursor.eof();
            }
            return cursor.next();
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return false;
        }
    }

    public boolean eof() throws SVNException {
        try {
            return cursor.eof();
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return false;
        }
    }

    public void bindf(String format, Object... data) throws SVNException {
        // TODO check formats
        // binds.addAll(Arrays.asList(data));

        for (int i = 0; i < format.length(); i++) {
            char fmt = format.charAt(i);

            // const void *blob;
            // apr_size_t blob_size;
            // const svn_token_map_t *map;

            switch (fmt) {
                case 's':
                    bindString(i + 1, data[i].toString());
                    break;

                case 'i':
                    if (data[i] instanceof Number) {
                        bindLong(i + 1, ((Number) data[i]).longValue());
                    } else {
                        SVNErrorManager.assertionFailure(false, "Number argument required", SVNLogType.WC);
                    }
                    break;

                case 'b':
                    if (data[i] instanceof byte[]) {
                        bindBlob(i + 1, (byte[]) data[i]);
                    }
                    break;

                case 't':
                    // map = va_arg(ap, const svn_token_map_t *);
                    // SVN_ERR(svn_sqlite__bind_token(stmt, count, map,
                    // va_arg(ap, int)));
                    break;

                default:
                    SVNErrorManager.assertionFailure(false, null, SVNLogType.WC);
            }
        }

    }

    public void bindLong(int i, long v) {
        binds.add(i - 1, v);
    }

    public void bindString(int i, String string) {
        binds.add(i - 1, string);
    }

    public void bindProperties(int i, SVNProperties props) throws SVNException {
        SVNSkel.createPropList(props.asMap()).getData();
        binds.add(i - 1, SVNSkel.createPropList(props.asMap()).getData());
    }

    public void bindChecksum(int i, SVNChecksum checksum) {
        binds.add(i - 1, checksum.toString());
    }

    public void bindBlob(int i, byte[] serialized) {
        binds.add(i - 1, serialized);
    }

    public long count() throws SVNException {
        try {
            if (cursor == null || cursor.eof())
                return 0;
            return cursor.getRowCount();
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return 0;
        }
    }

    public long getColumnLong(Enum f) throws SVNException {
        return getColumnLong(f.toString());
    }

    public long getColumnLong(String f) throws SVNException {
        try {
            if (cursor == null || cursor.eof())
                return 0;
            return cursor.getInteger(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return 0;
        }
    }

    public String getColumnString(Enum f) throws SVNException {
        return getColumnString(f.toString());
    }

    public String getColumnString(String f) throws SVNException {
        try {
            if (cursor == null || cursor.eof())
                return null;
            return cursor.getString(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    public boolean isColumnNull(Enum f) throws SVNException {
        return isColumnNull(f.toString());
    }

    public boolean isColumnNull(String f) throws SVNException {
        try {
            if (cursor == null || cursor.eof())
                return true;
            return cursor.isNull(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return false;
        }
    }

    public byte[] getColumnBlob(Enum f) throws SVNException {
        return getColumnBlob(f.toString());
    }

    public byte[] getColumnBlob(String f) throws SVNException {
        try {
            if (cursor == null || cursor.eof())
                return null;
            return cursor.getBlobAsArray(f);
        } catch (SqlJetException e) {
            SVNSqlJetDb.createSqlJetError(e);
            return null;
        }
    }

    public long getColumnLong(int f) throws SVNException {
        SVNErrorManager.assertionFailure(false, "unsupported", SVNLogType.WC);
        return 0;
    }

    public String getColumnString(int f) throws SVNException {
        SVNErrorManager.assertionFailure(false, "unsupported", SVNLogType.WC);
        return null;
    }

    public boolean isColumnNull(int f) throws SVNException {
        SVNErrorManager.assertionFailure(false, "unsupported", SVNLogType.WC);
        return true;
    }

    public byte[] getColumnBlob(int f) throws SVNException {
        SVNErrorManager.assertionFailure(false, "unsupported", SVNLogType.WC);
        return null;
    }

    public SVNSqlJetStatement getJoinedStatement(String joinedTable) throws SVNException {
        SVNErrorManager.assertionFailure(false, "unsupported", SVNLogType.WC);
        return null;
    }

    public SVNSqlJetStatement getJoinedStatement(Enum joinedTable) throws SVNException {
        return getJoinedStatement(joinedTable.toString());
    }

}
