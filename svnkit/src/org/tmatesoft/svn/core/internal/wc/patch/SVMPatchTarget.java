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
package org.tmatesoft.svn.core.internal.wc.patch;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import org.tmatesoft.svn.core.SVNNodeKind;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVMPatchTarget {

    public SVMPatchTarget(SVNPatch svnPatch, File targetPath, long stripCount) {
    }

    public boolean isSkipped() {
        return false;
    }

    public void maybeSendPatchNotification() {
    }

    public List getHunks() {
        return null;
    }

    public void rejectHunk(SVNPatchHunkInfo hi) {
    }

    public void applyHunk(SVNPatchHunkInfo hi) {
    }

    public SVNNodeKind getKind() {
        return null;
    }

    public void copyLinesToTarget(long i) {
    }

    public boolean isEOF() {
        return false;
    }

    public void setModified(boolean b) {
    }

    public void setSkipped(boolean b) {
    }

    public SVNPatchFileStream getStream() {
        return null;
    }

    public SVNPatchFileStream getPatched() {
        return null;
    }

    public SVNPatchFileStream getReject() {
        return null;
    }

    public File getPatchedPath() {
        return null;
    }

    public File getPath() {
        return null;
    }

    public void setDeleted(boolean b) {
    }

    public boolean isAdded() {
        return false;
    }

    public boolean isParentDirExists() {
        return false;
    }

    public boolean isDeleted() {
        return false;
    }

    public boolean isExecutable() {
        return false;
    }

    public boolean isHadRejects() {
        return false;
    }

    public File getRejectPath() {
        return null;
    }

}
