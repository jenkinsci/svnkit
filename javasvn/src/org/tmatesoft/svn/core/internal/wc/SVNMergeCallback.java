/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry2;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNMergeCallback extends AbstractDiffCallback {

    private boolean myIsDryRun;

    protected SVNMergeCallback(SVNAdminAreaInfo info) {
        super(info);
    }

    public File createTempDirectory() throws SVNException {
        return SVNFileUtil.createTempDirectory("merge");
    }

    public boolean isDiffUnversioned() {
        return false;
    }

    public SVNStatusType propertiesChanged(String path, Map originalProperties, Map diff) throws SVNException {
        Map regularProps = new HashMap();
        categorizeProperties(diff, regularProps, null, null);
        if (regularProps.isEmpty()) {
            return SVNStatusType.UNCHANGED;
        }
        File file = getFile(path);
        SVNEntry2 entry = null;
        try {
            entry = getWCAccess().getEntry(file, false);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE ||
                    e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_NOT_FOUND) {
                return SVNStatusType.MISSING;
            }
            throw e;
        }
        if (entry == null) {
            return SVNStatusType.MISSING;
        }
        SVNAdminArea dir = getWCAccess().probeRetrieve(file);
        String name = file.getName();
        if (entry.isDirectory()) {
            name = "";
        }
        ISVNLog log = null;
        if (!myIsDryRun) {
            log = dir.getLog();
        }
        SVNStatusType result = dir.mergeProperties(name, originalProperties, diff, false, myIsDryRun, log);
        if (!myIsDryRun) {
            log.save();
            dir.runLogs();
        }
        return result;
    }

    public SVNStatusType directoryAdded(String path, long revision) throws SVNException {
        return null;
    }

    public SVNStatusType directoryDeleted(String path) throws SVNException {
        return null;
    }

    public SVNStatusType[] fileAdded(String path, File file1, File file2, long revision1, long revision2, String mimeType1, String mimeType2, Map originalProperties, Map diff) throws SVNException {
        return null;
    }

    public SVNStatusType[] fileChanged(String path, File file1, File file2, long revision1, long revision2, String mimeType1, String mimeType2, Map originalProperties, Map diff) throws SVNException {
        return null;
    }

    public SVNStatusType fileDeleted(String path, File file1, File file2, String mimeType1, String mimeType2, Map originalProperties) throws SVNException {
        return null;
    }
    
    protected File getFile(String path) {
        return getAdminInfo().getAnchor().getFile(path);
    }

}
