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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public abstract class AbstractDiffCallback {
    
    private SVNAdminAreaInfo myAdminInfo;
    private File myBasePath;
    private Set myDeletedPaths;
    
    protected AbstractDiffCallback(SVNAdminAreaInfo info) {
        myAdminInfo = info;
    }
    
    public void setBasePath(File path) {
        myBasePath = path;
    }
    
    public abstract boolean isDiffUnversioned();
    
    public abstract File createTempDirectory() throws SVNException;

    public abstract SVNStatusType propertiesChanged(String path, Map originalProperties, Map diff) throws SVNException;

    public abstract SVNStatusType[] fileChanged(String path, File file1, File file2, long revision1, long revision2, String mimeType1, String mimeType2, 
            Map originalProperties, Map diff) throws SVNException;
    
    public abstract SVNStatusType[] fileAdded(String path, File file1, File file2, long revision1, long revision2, String mimeType1, String mimeType2, 
            Map originalProperties, Map diff) throws SVNException;
    
    public abstract SVNStatusType fileDeleted(String path, File file1, File file2, String mimeType1, String mimeType2, 
            Map originalProperties) throws SVNException;
    
    public abstract SVNStatusType directoryAdded(String path, long revision) throws SVNException;

    public abstract SVNStatusType directoryDeleted(String path) throws SVNException;
    
    protected String getDisplayPath(String path) {
        if (myAdminInfo == null) {
            if (myBasePath != null) {
                return new File(myBasePath, path).getAbsolutePath().replace(File.separatorChar, '/');
            }
            return path.replace(File.separatorChar, '/');
        }
        return myAdminInfo.getAnchor().getFile(path).getAbsolutePath().replace(File.separatorChar, '/');
    }
    
    protected void categorizeProperties(Map original, Map regular, Map entry, Map wc) {
        if (original == null) {
            return;
        }
        for(Iterator propNames = original.keySet().iterator(); propNames.hasNext();) {
            String name = (String) propNames.next();
            if (SVNProperty.isRegularProperty(name) && regular != null) {
                regular.put(name, original.get(name));
            } else if (SVNProperty.isEntryProperty(name) && entry != null) {
                entry.put(name, original.get(name));
            } else if (SVNProperty.isWorkingCopyProperty(name) && wc != null) {
                wc.put(name, original.get(name));
            }
        }
    }
    
    protected SVNAdminAreaInfo getAdminInfo() {
        return myAdminInfo;        
    }
    
    protected SVNWCAccess getWCAccess() {
        return getAdminInfo().getWCAccess();
    }
    
    protected void addDeletedPath(String path) {
        if (myDeletedPaths == null) {
            myDeletedPaths = new HashSet();
        }
        myDeletedPaths.add(path);
    }
    
    protected boolean isPathDeleted(String path) {
        return myDeletedPaths != null && myDeletedPaths.contains(path);
    }

    protected void clearDeletedPaths() {
        myDeletedPaths = null;
    }

}
