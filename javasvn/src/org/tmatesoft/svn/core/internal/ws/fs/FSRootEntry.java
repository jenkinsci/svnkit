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

package org.tmatesoft.svn.core.internal.ws.fs;

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

import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.ISVNRootEntry;
import org.tmatesoft.svn.core.io.SVNException;

/**
 * @author TMate Software Ltd.
 */
public class FSRootEntry extends FSDirEntry implements ISVNRootEntry {
    
    private static final String DEFAULT_GLOBAL_IGNORE = "*.o *.lo *.la #*# .*.rej *.rej .*~ *~ .#* .DS_Store";

    private Map myTempLocations;
    private String myID;
    private FSMerger myMerger;
    private String myGlobalIgnore; 

    public FSRootEntry(FSAdminArea area, String id, String location) {
        super(area, null, "", location);
        setGlobalIgnore(DEFAULT_GLOBAL_IGNORE);
        id = id.replace(File.separatorChar, '/');
        myID = id;
    }
    
    public String getType() {
        return "file";
    }
    
    public String getID() {
        return myID;
    }

    public String getWorkspaceProperty(String path, String name) throws SVNException {
        if (path == null || name == null) {
            return null;
        }
        ISVNEntry entry = locateEntry(path);
        if (entry != null) {
            return entry.getPropertyValue(name);
        }
        return null;
    }

    public void setWorkspaceProperty(String path, String name, String value) throws SVNException {
        if (path == null || name == null) {
            return;
        }
        ISVNEntry entry = locateEntry(path);
        if (entry != null) {
            entry.setPropertyValue(name, value);
        }
    }
    
    public void setGlobalIgnore(String ignore) {
        myGlobalIgnore = ignore;
    }
    
    public String getGlobalIgnore() {
        if (myGlobalIgnore == null) {
            return DEFAULT_GLOBAL_IGNORE;
        }
        return myGlobalIgnore;
    }

    public OutputStream createTemporaryLocation(Object id) throws IOException {
        if (id == null) {
            throw new IOException("id could not be null");
        }
        if (myTempLocations == null) {
            myTempLocations = new HashMap();
        }
        File tempFile = File.createTempFile("svn", "temp");
        if (tempFile == null) {
            throw new IOException("can't create temporary file");
        }
        tempFile.deleteOnExit();
        myTempLocations.put(id, tempFile);
        return new BufferedOutputStream(new FileOutputStream(tempFile));
    }

    public InputStream getTemporaryLocation(Object id) throws IOException {
        if (myTempLocations == null || id == null) {
            throw new IOException("no such location: " + id);
        }
        File file = (File) myTempLocations.get(id);
        if (file == null) {
            throw new IOException("no such location: " + id);
        }
        return new BufferedInputStream(new FileInputStream(file));
    }
    
    public long getLength(Object id) throws IOException {
        if (myTempLocations == null || id == null) {
            throw new IOException("no such location: " + id);
        }
        File file = (File) myTempLocations.get(id);
        if (file == null) {
            throw new IOException("no such location: " + id);
        }
        return file.length();
    }

    public void deleteTemporaryLocation(Object id) {
        if (myTempLocations == null || id == null) {
            return;
        }
        File file = (File) myTempLocations.remove(id);
        if (file != null) {
            file.delete();
        }
    }
    
    public File getWorkingCopyFile(ISVNEntry entry) {
        return new File(myID, entry.getPath());
    }
    
    public File createTemporaryFile() throws SVNException {
        File file;
        try {
            file = File.createTempFile("svn.", ".tmp");
        } catch (IOException e) {
            throw new SVNException(e);
        }
        file.deleteOnExit();
        return file;
    }
    
    public boolean isObstructed() {
        return false;
    }
    
    public boolean revert(String childName) throws SVNException {
        if (childName == null) {
            revertProperties();            
        } else {
            return super.revert(childName);
        }
        return true;
    }
    
    public FSMerger getMerger() {
        if (myMerger == null) {
            myMerger = new FSMerger();
        }
        return myMerger;
    }
    
    protected FSRootEntry getRootEntry() {
        return this;
    }
    
    protected ISVNEntry locateEntry(String path) throws SVNException {
        if (path == null) {
            return null;
        }
        ISVNEntry entry = this;
        for(StringTokenizer tokens = new StringTokenizer(path, "/"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (entry.isDirectory()) {
                entry = entry.asDirectory().getChild(token);
            } else {
                return null;
            }
            if (entry == null) {
                return null;
            }
        }    
        return entry;
    }
}
