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
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

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
        } else {
        	DebugLog.log("can't locate entry at: " + path);
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

    public OutputStream createTemporaryLocation(String path, Object id) throws IOException {
        if (id == null) {
            throw new IOException("id could not be null");
        }
        if (myTempLocations == null) {
            myTempLocations = new HashMap();
        }
        String name = PathUtil.tail(path);
        path = PathUtil.removeTail(path);
        path = PathUtil.append(path, ".svn/tmp");
        File parent = new File(myID, path);
        if (!parent.exists()) {
            parent.mkdirs();
            FSUtil.setHidden(parent.getParentFile(), true);
        }
        File tempFile = File.createTempFile("svn." + name + ".", ".tmp", parent);
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
    
    public void deleteAdminFiles(String path) {
        path = PathUtil.append(path, ".svn");
        File parent = new File(myID, path);
        FSUtil.deleteAll(parent);
        myTempLocations.clear();
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
    
    public File createTemporaryFile(FSFileEntry source) throws SVNException {
        File parent = new File(source.getAdminArea().getAdminArea(source), "tmp");
        if (!parent.exists()) {
            parent.mkdirs();
            FSUtil.setHidden(parent.getParentFile(), true);
        }
        File file = null;
        try {
             file = File.createTempFile("svn." + source.getName() + ".", ".tmp", parent);
             file.deleteOnExit();
        } catch (IOException e) {
            DebugLog.error(e);
        }
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
                entry = entry.asDirectory().getUnmanagedChild(token);
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
