/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
class SVNAdminArea14Factory extends SVNAdminAreaFactory {
    
    public static final int WC_FORMAT = 8;
    
    protected void doCreateVersionedDirectory(File path, String url, String rootURL, String uuid, long revNumber) throws SVNException {
        SVNAdminArea adminArea = new SVNAdminArea14(path); 
        adminArea.createVersionedDirectory(path, url, rootURL, uuid, revNumber, true);
    }

    protected SVNAdminArea doOpen(File path, int version) throws SVNException {
        if (version != WC_FORMAT) {
            return null;
        }
        return new SVNAdminArea14(path);
    }

    protected SVNAdminArea doUpgrade(SVNAdminArea adminArea) throws SVNException {
        if (adminArea == null || adminArea.getClass() == SVNAdminArea14.class) {
            return adminArea;
        }
        SVNAdminArea14 newestAdminArea = new SVNAdminArea14(adminArea.getRoot());
        newestAdminArea.setLocked(true);
        return newestAdminArea.upgradeFormat(adminArea);
    }

    public int getSupportedVersion() {
        return WC_FORMAT;
    }

    protected int doCheckWC(File path) throws SVNException {
        File adminDir = new File(path, SVNFileUtil.getAdminDirectoryName());
        File entriesFile = new File(adminDir, "entries");
        int formatVersion = -1;

        BufferedReader reader = null;
        String line = null;
    
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(entriesFile), "UTF-8"));
            line = reader.readLine();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {entriesFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        } catch (SVNException svne) {
            SVNFileType type = SVNFileType.getType(path);
            if (type != SVNFileType.DIRECTORY || !entriesFile.exists()) { 
                if (type == SVNFileType.NONE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "''{0}'' does not exist", path);
                    SVNErrorManager.error(err);
                }
                return 0;
            }
            throw svne;
        } finally {
            SVNFileUtil.closeFile(reader);
        }

        if (line == null || line.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Reading ''{0}''", entriesFile);
            SVNErrorManager.error(err);
        }
        
        try {
            formatVersion = Integer.parseInt(line.trim());
        } catch (NumberFormatException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", entriesFile);
            SVNErrorManager.error(err);
        }
        return formatVersion;
    }
    
    protected int getVersion(File path) throws SVNException {
        File adminDir = new File(path, SVNFileUtil.getAdminDirectoryName());
        File entriesFile = new File(adminDir, "entries");
        int formatVersion = -1;

        BufferedReader reader = null;
        String line = null;
    
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(entriesFile), "UTF-8"));
            line = reader.readLine();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {entriesFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            err.setChildErrorMessage(svne.getErrorMessage());
            SVNErrorManager.error(err, svne);
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        
        if (line == null || line.length() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Reading ''{0}''", entriesFile);
            SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            err1.setChildErrorMessage(err);
            SVNErrorManager.error(err1);
        }
        
        try {
            formatVersion = Integer.parseInt(line.trim());
        } catch (NumberFormatException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", entriesFile);
            SVNErrorMessage err1 = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            err1.setChildErrorMessage(err);
            SVNErrorManager.error(err1);
        }
        return formatVersion;
    }
}
