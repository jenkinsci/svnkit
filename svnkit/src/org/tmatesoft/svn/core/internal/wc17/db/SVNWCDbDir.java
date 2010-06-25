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

import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;

/**
 * This structure records all the information that we need to deal with a given
 * working copy directory.
 * 
 * @author TMate Software Ltd.
 */
public class SVNWCDbDir {

    /**
     * This (versioned) working copy directory is obstructing what *should* be a
     * file in the parent directory (according to its metadata).
     * 
     * Note: this PDH should probably be ignored (or not created).
     * 
     * ### obstruction is only possible with per-dir wc.db databases.
     */
    private boolean obstructedFile;

    /** The absolute path to this working copy directory. */
    private File localAbsPath;

    /** What wcroot does this directory belong to? */
    private SVNWCDbRoot wcRoot;

    /** The parent directory's per-dir information. */
    private SVNWCDbDir parent;

    /** Whether this process owns a write-lock on this directory. */
    boolean locked;

    /** Hold onto the old-style access baton that corresponds to this PDH. */
    private SVNWCAccess admAccess;

    public SVNWCDbDir(File localAbsPath) {
        this.localAbsPath = localAbsPath;
    }

    public boolean isObstructedFile() {
        return obstructedFile;
    }

    public File getLocalAbsPath() {
        return localAbsPath;
    }

    public SVNWCDbRoot getWCRoot() {
        return wcRoot;
    }

    public SVNWCDbDir getParent() {
        return parent;
    }

    public boolean isLocked() {
        return locked;
    }

    public SVNWCAccess getAdmAccess() {
        return admAccess;
    }

    public void setLocalAbsPath(File localAbsPath) {
        this.localAbsPath = localAbsPath;
    }

    public void setWCRoot(SVNWCDbRoot wcRoot) {
        this.wcRoot = wcRoot;
    }

    public void setParent(SVNWCDbDir parent) {
        this.parent = parent;
    }

    public void setObstructedFile(boolean obstructedFile) {
        this.obstructedFile = obstructedFile;
    }

    public static boolean isUsable(SVNWCDbDir pdh) {
        return pdh != null && pdh.getWCRoot() != null && pdh.getWCRoot().getFormat() == ISVNWCDb.WC_FORMAT_17;
    }

    public File computeRelPath() {
        // TODO
        return null;
    }

}
