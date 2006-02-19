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
package org.tmatesoft.svn.core.internal.io.fs.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class CommitMediator implements ISVNWorkspaceMediator {

    private Map myTmpFiles = new HashMap();

    public String getWorkspaceProperty(String path, String name) throws SVNException {
        return null;
    }

    public void setWorkspaceProperty(String path, String name, String value) throws SVNException {
    }

    public OutputStream createTemporaryLocation(String path, Object id) throws SVNException {
        ByteArrayOutputStream tempStorageOS = new ByteArrayOutputStream();
        myTmpFiles.put(id, tempStorageOS);
        return tempStorageOS;
    }

    public InputStream getTemporaryLocation(Object id) throws SVNException {
        return new ByteArrayInputStream(((ByteArrayOutputStream)myTmpFiles.get(id)).toByteArray());
    }

    public long getLength(Object id) throws SVNException {
        ByteArrayOutputStream tempStorageOS = (ByteArrayOutputStream)myTmpFiles.get(id);
        if (tempStorageOS != null) {
            return tempStorageOS.size();
        }
        return 0;
    }

    public void deleteTemporaryLocation(Object id) {
        myTmpFiles.remove(id);
    }
}
