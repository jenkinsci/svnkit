/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNImportMediator implements ISVNWorkspaceMediator {

    private File myRoot;

    private Map myLocations;

    public SVNImportMediator(File root) {
        myRoot = root;
        myLocations = new HashMap();
    }

    public String getWorkspaceProperty(String path, String name)
            throws SVNException {
        return null;
    }

    public void setWorkspaceProperty(String path, String name, String value)
            throws SVNException {
    }

    public OutputStream createTemporaryLocation(String path, Object id)
            throws IOException {
        File tmpFile = SVNFileUtil.createUniqueFile(myRoot,
                PathUtil.tail(path), ".tmp");
        OutputStream os;
        try {
            os = SVNFileUtil.openFileForWriting(tmpFile);
        } catch (SVNException e) {
            throw new IOException(e.getMessage());
        }
        myLocations.put(id, tmpFile);
        return os;
    }

    public InputStream getTemporaryLocation(Object id) throws IOException {
        File file = (File) myLocations.get(id);
        if (file != null) {
            try {
                return SVNFileUtil.openFileForReading(file);
            } catch (SVNException e) {
                throw new IOException(e.getMessage());
            }
        }
        return null;
    }

    public long getLength(Object id) throws IOException {
        File file = (File) myLocations.get(id);
        if (file != null) {
            return file.length();
        }
        return 0;
    }

    public void deleteTemporaryLocation(Object id) {
        File file = (File) myLocations.remove(id);
        if (file != null) {
            file.delete();
        }
    }
}
