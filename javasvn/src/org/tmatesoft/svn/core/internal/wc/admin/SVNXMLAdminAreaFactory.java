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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * 
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNXMLAdminAreaFactory extends SVNAdminAreaFactory {
    private static final int WC_FORMAT = 4;

    static {
        SVNAdminAreaFactory.registerFactory(new SVNXMLAdminAreaFactory());
    }

    protected SVNAdminArea doOpen(File path, int version) throws SVNException {
        if (version != WC_FORMAT) {
            return null;
        }
        return new SVNXMLAdminArea(path);
    }

    protected SVNAdminArea doUpgrade(SVNAdminArea area) throws SVNException {
        return area;
    }

    protected SVNAdminArea doCreateVersionedDirectory(File dir) throws SVNException {
        SVNAdminArea adminArea = new SVNXMLAdminArea(dir);
        return adminArea.createVersionedDirectory(); 
    }

    protected int getSupportedVersion() {
        return WC_FORMAT;
    }

    protected int getVersion(File path) throws SVNException {
        File adminDir = new File(path, SVNFileUtil.getAdminDirectoryName());
        File formatFile = new File(adminDir, "format");
        BufferedReader reader = null;
        String line = null;
        int formatVersion = -1;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(formatFile), "UTF-8"));
            line = reader.readLine();
            formatVersion = Integer.parseInt(line.trim());
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read format file ''{0}'': {1}", new Object[] {formatFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy: {1}", new Object[] {path, nfe.getLocalizedMessage()});
            SVNErrorManager.error(err, nfe);
        } catch (SVNException svne) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
            err.setChildErrorMessage(svne.getErrorMessage());
            SVNErrorManager.error(err, svne);
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        return formatVersion;
    }
}
