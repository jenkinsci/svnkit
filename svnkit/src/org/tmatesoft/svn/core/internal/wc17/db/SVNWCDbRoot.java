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
package org.tmatesoft.svn.core.internal.wc17.db;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbLock;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 */
public class SVNWCDbRoot {

    private SVNWCDb db;

    /** Location of this wcroot in the filesystem. */
    private File absPath;

    /**
     * The SQLite database containing the metadata for everything in this
     * wcroot.
     */
    private SVNSqlJetDb sDb;

    /** The WCROOT.id for this directory (and all its children). */
    private long wcId;

    /**
     * The format of this wcroot's metadata storage (see wc.h). If the format
     * has not (yet) been determined, this will be UNKNOWN_FORMAT.
     */
    private int format;

    /**
     * Array of SVNWCDbLock fields. Typically just one or two locks maximum.
     */
    private List<SVNWCDbLock> ownedLocks = new ArrayList<ISVNWCDb.SVNWCDbLock>();

    public SVNWCDbRoot(SVNWCDb db, File absPath, SVNSqlJetDb sDb, long wcId, int format, boolean autoUpgrade, boolean enforceEmptyWQ) throws SVNException {
        if (sDb != null) {
            try {
                format = sDb.getDb().getOptions().getSchemaVersion();
            } catch (SqlJetException e) {
                SVNSqlJetDb.createSqlJetError(e);
            }
        }

        /* If we construct a wcroot, then we better have a format. */
        assert (format >= 1);

        /* If this working copy is PRE-1.0, then simply bail out. */
        if (format < 4) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, "Working copy format of ''{0}'' is too old '{1}'", new Object[] {
                    absPath, format
            });
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        /* If this working copy is from a future version, then bail out. */
        if (format > ISVNWCDb.WC_FORMAT_17) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, "This client is too old to work with the working copy at\n" + "''{0}'' (format '{1}').", new Object[] {
                    absPath, format
            });
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        /* Auto-upgrade the SDB if possible. */
        if (format < ISVNWCDb.WC_FORMAT_17 && autoUpgrade) {
            if (autoUpgrade) {
                format = upgrade(absPath, format);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, "Working copy format of ''{0}'' is too old '{1}'", new Object[] {
                        absPath, format
                });
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }

        /*
         * Verify that no work items exists. If they do, then our integrity is
         * suspect and, thus, we cannot use this database.
         */
        if (format >= ISVNWCDb.WC_HAS_WORK_QUEUE && enforceEmptyWQ) {
            sDb.verifyNoWork();
        }

        this.db = db;
        this.absPath = absPath;
        this.sDb = sDb;
        this.wcId = wcId;
        this.format = format;

    }

    public SVNWCDb getDb() {
        return db;
    }

    public File getAbsPath() {
        return absPath;
    }

    public SVNSqlJetDb getSDb() {
        return sDb;
    }

    public long getWcId() {
        return wcId;
    }

    public int getFormat() {
        return format;
    }

    public List<SVNWCDbLock> getOwnedLocks() {
        return ownedLocks;
    }

    public void close() throws SVNException {
        assert (sDb != null);
        try {
            sDb.close();
        } finally {
            sDb = null;
        }
    }

    public static int upgrade(File absPath, int format) {
        // TODO
        return 0;
    }

}
