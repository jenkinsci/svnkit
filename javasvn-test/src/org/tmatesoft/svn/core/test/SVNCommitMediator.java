/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.internal.ws.fs.FSRootEntry;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author TMate Software Ltd.
 */
public class SVNCommitMediator implements ISVNWorkspaceMediator {
    
    private Map myFiles = new HashMap();
    private ISVNDirectoryEntry myRootEntry;
    
    public SVNCommitMediator(ISVNDirectoryEntry rootEntry) {
        myRootEntry = rootEntry;
    }

    public String getWorkspaceProperty(String path, String name) throws SVNException {
        if (myRootEntry == null) {
            return null;
        }
        ISVNEntry entry = myRootEntry;
        for(StringTokenizer segments = new StringTokenizer(path, "/"); segments.hasMoreTokens();) {
            String token = segments.nextToken();
            entry = entry.asDirectory().getChild(token);
            if (entry == null) {
                return null;
            }
        }
        return entry.getPropertyValue(name);
    }
    
    public void setWorkspaceProperty(String path, String name, String value) throws SVNException {
        if (myRootEntry == null) {
            return;
        }
        ISVNEntry entry = myRootEntry;
        for(StringTokenizer segments = new StringTokenizer(path, "/"); segments.hasMoreTokens();) {
            String token = segments.nextToken();
            entry = entry.asDirectory().getChild(token);
            if (entry == null) {
                return;
            }
        }
        entry.setPropertyValue(name, value);
    }
    
    public OutputStream createTemporaryLocation(String path, Object id) throws IOException {
        File tempFile = myRootEntry instanceof FSRootEntry ?
                new File(new File(((FSRootEntry) myRootEntry).getID(), path), "svn." + id.hashCode() + ".temp") :
                    File.createTempFile("svn.", ".temp");
                    
        tempFile.deleteOnExit();
        myFiles.put(id, tempFile);
        return new BufferedOutputStream(new FileOutputStream(tempFile));
    }

    public void deleteAdminFiles(String path) {
    }

    public InputStream getTemporaryLocation(Object id) throws IOException {
        File file = (File) myFiles.get(id);        
        return new BufferedInputStream(new FileInputStream(file));
    }
    
    public void deleteTemporaryLocation(Object id) {
        File file = (File) myFiles.remove(id);
        if (file != null) {
            file.delete();
        }
    }

    public long getLength(Object id) throws IOException {
        File file = (File) myFiles.get(id);        
        return file.length();
    }
}
