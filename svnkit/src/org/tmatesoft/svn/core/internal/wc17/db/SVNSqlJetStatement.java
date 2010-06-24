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

import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;

/**
 * @author TMate Software Ltd.
 */
public class SVNSqlJetStatement {

    public boolean next() {
        return false;
    }

    public long insert(Object... data) {
        return 0;
    }

    public void bindf(String format, Object... data) {
    }

    public long getLong(int i) {
        return 0;
    }

    public void reset() {
    }

    public void bindLong(int i, long v) {
    }

    public void bindString(int i, String string) {
    }

    public void bindProperties(int i, SVNProperties props) {
    }

    public void bindChecksumm(int i, SVNChecksum checksum) {
    }

    public void bindBlob(int i, byte[] serialized) {
    }

    public String getString(int i) {
        return null;
    }

}
