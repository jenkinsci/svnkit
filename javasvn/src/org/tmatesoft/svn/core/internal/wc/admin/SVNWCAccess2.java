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

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNWCAccess2 {
    
    public static final int INFINITE_DEPTH = -1;
    
    private ISVNEventHandler myEventHandler;
    private Map myAdminAreas;

    public SVNWCAccess2 newInstance(ISVNEventHandler eventHandler) {
        return new SVNWCAccess2(eventHandler);
    }
    
    private SVNWCAccess2(ISVNEventHandler handler) {
        myEventHandler = handler;
    }
    
    public void setEventHandler(ISVNEventHandler handler) {
        myEventHandler = handler;
    }
    
    public ISVNEventHandler getEventHandler() {
        return myEventHandler;
    }
    
    public void checkCancelled() throws SVNCancelException {
        if (myEventHandler != null) {
            myEventHandler.checkCancelled();
        }
    }

    public SVNAdminAreaInfo openAnchor(File path, boolean writeLock, int depth) throws SVNException {
        return null;
    }
    
    public SVNAdminArea open(File path, boolean writeLock, boolean openParent, int depth) throws SVNException {
        if (openParent) {
            if (!path.isDirectory()) {
                path = path.getParentFile();
                depth = 0;
            }
        }
        Map tmp = new HashMap();
        SVNAdminArea area = doOpen(path, writeLock, depth, tmp);
        for(Iterator paths = tmp.keySet().iterator(); paths.hasNext();) {
            Object childPath = paths.next();
            SVNAdminArea childArea = (SVNAdminArea) tmp.get(childPath);
            myAdminAreas.put(childPath, childArea);
        }
        return area;
    }
    
    public SVNAdminArea get(File path, boolean getParent) throws SVNException {
        return null;
    }

    public SVNAdminArea get(File path, boolean writeLock, int depth) throws SVNException {
        return null;
    }
    
    public void close() throws SVNException {
        if (myAdminAreas != null) {
            doClose(myAdminAreas);
            myAdminAreas.clear();
        }
    }
    
    private SVNAdminArea doOpen(File path, boolean writeLock, int depth, Map tmp) throws SVNException {
        // no support for 'under consturction here' - it will go to adminAreaFactory.
        tmp = tmp == null ? new HashMap() : tmp; 
        if (myAdminAreas != null) {
            SVNAdminArea existing = (SVNAdminArea) myAdminAreas.get(path);
            if (existing != null && existing != SVNAdminArea.MISSING) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked", path);
                SVNErrorManager.error(error);
            }
        } else {
            myAdminAreas = new HashMap();
        }
        
        SVNAdminArea area = SVNAdminAreaFactory.open(path);
        if (writeLock) {
            area.lock();
            area = SVNAdminAreaFactory.upgrade(area);
        }
        
        if (depth != 0) {
            if (depth > 0) {
                depth--;
            }
            for(Iterator entries = area.entries(false); entries.hasNext();) {
                SVNEntry entry = (SVNEntry) entries.next();
                if (entry.getKind() != SVNNodeKind.DIR  || "".equals(entry.getName())) {
                    continue;
                }
                File childPath = new File(path, entry.getName());
                try {
                    // this method will put created area into our map.
                    doOpen(childPath, writeLock, depth, tmp);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                        doClose(tmp);
                        tmp.clear();
                        throw e;
                    }
                    tmp.put(childPath, SVNAdminArea.MISSING);
                    continue;
                }
            }
        }
        tmp.put(path, area);
        return area;
    }
    
    private void doClose(Map adminAreas) throws SVNException {
        
    }

    public SVNAdminArea probeRetrieve(File path) throws SVNException {
        File dir = probe(path);
        return retrieve(dir);
    }
    
    
    public SVNAdminArea retrieve(File path) throws SVNException {
        SVNAdminArea adminArea = getAdminArea(path);
        if (adminArea == null) {
            SVNEntry subEntry = null;
            try {
                SVNAdminArea dirAdminArea = getAdminArea(path.getParentFile());
                if (dirAdminArea != null) {
                    subEntry = dirAdminArea.getEntry(path.getName(), true);
                }
            } catch (SVNException svne) {
                subEntry = null;
            }
            SVNFileType type = SVNFileType.getType(path);
            if (subEntry != null) {
                if (subEntry.getKind() == SVNNodeKind.DIR && type == SVNFileType.FILE) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Expected ''{0}'' to be a directory but found a file", path);
                    SVNErrorManager.error(err);
                } else if (subEntry.getKind() == SVNNodeKind.FILE && type == SVNFileType.DIRECTORY) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Expected ''{0}'' to be a file but found a directory", path);
                    SVNErrorManager.error(err);
                }
            }
            File adminDir = new File(path, SVNFileUtil.getAdminDirectoryName());
            SVNFileType wcType = SVNFileType.getType(adminDir);
            
            if (type == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Directory ''{0}'' is missing", path);
                SVNErrorManager.error(err);
            } else if (type == SVNFileType.DIRECTORY && wcType == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Directory ''{0}'' containing working copy admin area is missing", adminDir);
                SVNErrorManager.error(err);
            } else if (type == SVNFileType.DIRECTORY && wcType == SVNFileType.DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Unable to lock ''{0}''", path);
                SVNErrorManager.error(err);
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy ''{0}'' is not locked", path);
            SVNErrorManager.error(err);
        }
        return adminArea;
    }
    
    private SVNAdminArea getAdminArea(File path) {
        //internal retrieve
        SVNAdminArea adminArea = null; 
        if (myAdminAreas != null) {
            adminArea = (SVNAdminArea) myAdminAreas.get(path);
        }
        if (adminArea == SVNAdminArea.MISSING) {
            adminArea = null;
        }
        return adminArea;
    }
    
    private File probe(File path) throws SVNException {
        int wcFormat = -1;
        SVNFileType type = SVNFileType.getType(path);
        if (type == SVNFileType.DIRECTORY) {
            wcFormat = SVNAdminAreaFactory.checkWC(path);
        } else {
            wcFormat = 0;
        }
        
        //non wc
        if (type != SVNFileType.DIRECTORY || wcFormat == 0) {
            if ("..".equals(path.getName()) || ".".equals(path.getName())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_BAD_PATH, "Path ''{0}'' ends in ''{1}'', which is unsupported for this operation", new Object[]{path, path.getName()});
                SVNErrorManager.error(err);
            }
            path = path.getParentFile();
        } 
        return path;
    }
}
