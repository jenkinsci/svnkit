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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNWCAccess implements ISVNEventHandler {
    
    public static final int INFINITE_DEPTH = -1;
    
    private ISVNEventHandler myEventHandler;
    private ISVNOptions myOptions;
    private Map myAdminAreas;

    private File myAnchor;

    public static SVNWCAccess newInstance(ISVNEventHandler eventHandler) {
        return new SVNWCAccess(eventHandler);
    }
    
    private SVNWCAccess(ISVNEventHandler handler) {
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

    public void handleEvent(SVNEvent event) throws SVNException {
        handleEvent(event, ISVNEventHandler.UNKNOWN);
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        if (myEventHandler != null) {
            try {
                myEventHandler.handleEvent(event, progress);
            } catch (SVNException e) {
                throw e;
            } catch (Throwable th) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Error while dispatching event: {0}", th.getMessage());
                SVNErrorManager.error(err, th);
            }
        }
    }

    public void setOptions(ISVNOptions options) {
        myOptions = options;
    }

    public ISVNOptions getOptions() {
        if (myOptions == null) {
            myOptions = new DefaultSVNOptions();
        }
        return myOptions;
    }
    
    public void setAnchor(File anchor) {
        myAnchor = anchor;
    }
    
    public File getAnchor() {
        return myAnchor;
    }

    public SVNAdminAreaInfo openAnchor(File path, boolean writeLock, int depth) throws SVNException {
        File parent = path.getParentFile();
        if (parent == null) {
            SVNAdminArea anchor = open(path, writeLock, depth);
            return new SVNAdminAreaInfo(this, anchor, anchor, "");
        }

        String name = path.getName();
        SVNAdminArea parentArea = null;
        SVNAdminArea targetArea = null; 
        SVNException parentError = null;
        
        try {
            parentArea = open(parent, writeLock, false, 0);
        } catch (SVNException svne) {
            if (writeLock && svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LOCKED) {
                try {
                    parentArea = open(parent, false, false, 0);
                } catch (SVNException svne2) {
                    throw svne;
                }
                parentError = svne;
            } else if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                throw svne;
            }
        }
        
        try {
            targetArea = open(path, writeLock, false, depth);
        } catch (SVNException svne) {
            if (parentArea == null || svne.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                try {
                    close();
                } catch (SVNException svne2) {
                    //
                }
                throw svne;
            }
        }
        
        if (parentArea != null && targetArea != null) {
            SVNEntry parentEntry = null;
            SVNEntry targetEntry = null;
            SVNEntry targetInParent = null;
            try {
                targetInParent = parentArea.getEntry(name, false);
                targetEntry = targetArea.getEntry(targetArea.getThisDirName(), false);
                parentEntry = parentArea.getEntry(parentArea.getThisDirName(), false);
            } catch (SVNException svne) {
                try {
                    close();
                } catch (SVNException svne2) {
                    //
                }
                throw svne;
            }
            
            SVNURL parentURL = parentEntry.getSVNURL();
            SVNURL targetURL = targetEntry.getSVNURL();
            String encodedName = SVNEncodingUtil.uriEncode(name);
            if (targetInParent == null || (parentURL != null && targetURL != null && 
                    (!parentURL.equals(targetURL.removePathTail()) || !encodedName.equals(SVNPathUtil.tail(targetURL.getPath()))))) {
                if (myAdminAreas != null) {
                    myAdminAreas.remove(parent);
                }
                try {
                    doClose(parentArea, false);
                } catch (SVNException svne) {
                    try {
                        close();
                    } catch (SVNException svne2) {
                        //
                    }
                    throw svne;
                }
                parentArea = null;
            }
        }
        
        if (parentArea != null) {
            if (parentError != null && targetArea != null) {
                try {
                    close();
                } catch (SVNException svne) {
                    //
                }
                throw parentError;
            }
        }

        if (targetArea == null) {
            SVNEntry targetEntry = null;
            try {
                targetEntry = parentArea.getEntry(name, false); 
            } catch (SVNException svne) {
                try {
                    close();
                } catch (SVNException svne2) {
                    //
                }
                throw svne;
            }
            if (targetEntry != null && targetEntry.isDirectory()) {
                if (myAdminAreas != null) {
                    myAdminAreas.put(path, null);
                }
            }
        }
        SVNAdminArea anchor = parentArea != null ? parentArea : targetArea;
        SVNAdminArea target = targetArea != null ? targetArea : parentArea;
        return new SVNAdminAreaInfo(this, anchor, target, parentArea == null ? "" : name);
    }

    public SVNAdminArea open(File path, boolean writeLock, int depth) throws SVNException {
        return open(path, writeLock, false, depth);
    }
    
    public SVNAdminArea open(File path, boolean writeLock, boolean stealLock, int depth) throws SVNException {
        Map tmp = new HashMap();
        SVNAdminArea area;
        try {
            area = doOpen(path, writeLock, stealLock, depth, tmp);
        } finally {
            for(Iterator paths = tmp.keySet().iterator(); paths.hasNext();) {
                Object childPath = paths.next();
                SVNAdminArea childArea = (SVNAdminArea) tmp.get(childPath);
                myAdminAreas.put(childPath, childArea);
            }
        }
        return area;
    }
    
    public SVNAdminArea probeOpen(File path, boolean writeLock, int depth) throws SVNException {
        File dir = probe(path);
        if (!path.equals(dir)) {
            depth = 0;
        }
        SVNAdminArea adminArea = null;
        try {
            adminArea = open(dir, writeLock, false, depth);
        } catch (SVNException svne) {
            SVNFileType childKind = SVNFileType.getType(path);
            SVNErrorCode errCode = svne.getErrorMessage().getErrorCode(); 
            if (!path.equals(dir) && childKind == SVNFileType.DIRECTORY && errCode == SVNErrorCode.WC_NOT_DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
                SVNErrorManager.error(err);
            } else {
                throw svne;
            }
        }
        return adminArea;
    }
    
    public SVNAdminArea probeTry(File path, boolean writeLock, int depth) throws SVNException {
        SVNAdminArea adminArea = null;
        try {
            adminArea = probeRetrieve(path);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                try {
                    adminArea = probeOpen(path, writeLock, depth);
                } catch (SVNException svne2) {
                    if (svne2.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                        throw svne2; 
                    }
                }
            } else {
                throw svne;
            }
        }
        return adminArea;
    }
    
    public void close() throws SVNException {
        if (myAdminAreas != null) {
            doClose(myAdminAreas, false);
            myAdminAreas.clear();
        }
    }
    
    public void closeAdminArea(File path) throws SVNException {
        if (myAdminAreas != null) {
            SVNAdminArea area = (SVNAdminArea) myAdminAreas.get(path);
            if (area != null) {
                doClose(area, false);
                myAdminAreas.remove(path);
            }
        }
    }
    
    private SVNAdminArea doOpen(File path, boolean writeLock, boolean stealLock, int depth, Map tmp) throws SVNException {
        // no support for 'under consturction here' - it will go to adminAreaFactory.
        tmp = tmp == null ? new HashMap() : tmp; 
        if (myAdminAreas != null) {
            SVNAdminArea existing = (SVNAdminArea) myAdminAreas.get(path);
            if (myAdminAreas.containsKey(path) && existing != null) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked", path);
                SVNErrorManager.error(error);
            }
        } else {
            myAdminAreas = new HashMap();
        }
        
        SVNAdminArea area = SVNAdminAreaFactory.open(path);
        area.setWCAccess(this);
        
        if (writeLock) {
            area.lock(stealLock);
            area = SVNAdminAreaFactory.upgrade(area);
        }
        tmp.put(path, area);
        
        if (depth != 0) {
            if (depth > 0) {
                depth--;
            }
            for(Iterator entries = area.entries(false); entries.hasNext();) {
                try {
                    checkCancelled(); 
                } catch (SVNCancelException e) {
                    doClose(tmp, false);
                    throw e;
                }
                
                SVNEntry entry = (SVNEntry) entries.next();
                if (entry.getKind() != SVNNodeKind.DIR  || area.getThisDirName().equals(entry.getName())) {
                    continue;
                }
                File childPath = new File(path, entry.getName());
                try {
                    // this method will put created area into our map.
                    doOpen(childPath, writeLock, stealLock, depth, tmp);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                        doClose(tmp, false);
                        throw e;
                    }
                    // only for missing!
                    tmp.put(childPath, null);
                    continue;
                }
            }
        }
        return area;
    }
    
    private void doClose(Map adminAreas, boolean preserveLocks) throws SVNException {
        for (Iterator paths = adminAreas.keySet().iterator(); paths.hasNext();) {
            File path = (File) paths.next();
            SVNAdminArea adminArea = (SVNAdminArea) adminAreas.get(path);
            if (adminArea == null) {
                paths.remove();
                continue;
            }
            doClose(adminArea, preserveLocks);
            paths.remove();
        }
    }

    private void doClose(SVNAdminArea adminArea, boolean preserveLocks) throws SVNException {
        if (adminArea == null) {
            return;
        }
        if (!preserveLocks && adminArea.isLocked()) {
            adminArea.unlock();
        }
    }

    public SVNAdminArea probeRetrieve(File path) throws SVNException {
        File dir = probe(path);
        return retrieve(dir);
    }
    
    public boolean isMissing(File path) {
        if (myAdminAreas != null) {
            return myAdminAreas.containsKey(path) && myAdminAreas.get(path) == null;
        }
        return false;
    }

    public boolean isLocked(File path) throws SVNException {
        File lockFile = new File(path, SVNFileUtil.getAdminDirectoryName());
        lockFile = new File(lockFile, "lock");
        if (SVNFileType.getType(lockFile) == SVNFileType.FILE) {
            return true;
        } else if (SVNFileType.getType(lockFile) == SVNFileType.NONE) {
            return false;
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Lock file ''{0}'' is not a regular file", lockFile);
        SVNErrorManager.error(err);
        return false;
    }
    
    public boolean isWCRoot(File path) throws SVNException {
        SVNEntry entry = getEntry(path, false);
        SVNAdminArea parentArea = getAdminArea(path.getParentFile());
        if (parentArea == null) {
            try {
                parentArea = probeOpen(path.getParentFile(), false, 0);
            } catch (SVNException svne) {
                return true;
            }
        }
        
        SVNEntry parentEntry = getEntry(path.getParentFile(), false);
        if (parentEntry == null) {
            return true;
        }
        
        if (parentEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no ancestry information", path.getParentFile());
            SVNErrorManager.error(err);
        }
        
        // what about switched paths?
        /*
        if (entry != null && entry.getURL() != null) {
            if (!entry.getURL().equals(SVNPathUtil.append(parentEntry.getURL(), SVNEncodingUtil.uriEncode(path.getName())))) {
                return true;
            }
        }*/
        entry = parentArea.getEntry(path.getName(), false);
        if (entry == null) {
            return true;
        }
        return false;
    }
    
    public SVNEntry getEntry(File path, boolean showHidden) throws SVNException {
        SVNAdminArea adminArea = getAdminArea(path);
        String entryName = null;
        if (adminArea == null) {
            adminArea = getAdminArea(path.getParentFile());
            entryName = path.getName();
        } else {
            entryName = adminArea.getThisDirName();
        }
        
        if (adminArea != null) {
            return adminArea.getEntry(entryName, showHidden);
        }
        return null;
    }
    
    public void setRepositoryRoot(File path, SVNURL reposRoot) throws SVNException {
        SVNEntry entry = getEntry(path, false);
        if (entry == null) {
            return;
        }
        SVNAdminArea adminArea = null;
        String name = null;
        if (entry.isFile()) {
            adminArea = getAdminArea(path.getParentFile());
            name = path.getName();
        } else {
            adminArea = getAdminArea(path);
            name = adminArea != null ? adminArea.getThisDirName() : null;
        }
        
        if (adminArea == null) {
            return;
        }
        if (adminArea.tweakEntry(name, null, reposRoot.toString(), -1, false)) {
            adminArea.saveEntries(false);
        }
    }
    
    public SVNAdminArea[] getAdminAreas() {
        if (myAdminAreas != null) {
            return (SVNAdminArea[]) myAdminAreas.values().toArray(new SVNAdminArea[myAdminAreas.size()]);
        }
        return new SVNAdminArea[0];
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

    public static SVNExternalInfo[] parseExternals(String rootPath, String externals) {
        Collection result = new ArrayList();
        if (externals == null) {
            return (SVNExternalInfo[]) result.toArray(new SVNExternalInfo[result.size()]);
        }

        for (StringTokenizer lines = new StringTokenizer(externals, "\n\r"); lines.hasMoreTokens();) {
            String line = lines.nextToken().trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            String url = null;
            String path;
            long rev = -1;
            List parts = new ArrayList(4);
            for (StringTokenizer tokens = new StringTokenizer(line, " \t"); tokens
                    .hasMoreTokens();) {
                String token = tokens.nextToken().trim();
                parts.add(token);
            }
            if (parts.size() < 2) {
                continue;
            }
            path = SVNPathUtil.append(rootPath, (String) parts.get(0));
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (parts.size() == 2) {
                url = (String) parts.get(1);
            } else if (parts.size() == 3 && parts.get(1).toString().startsWith("-r")) {
                String revStr = parts.get(1).toString();
                revStr = revStr.substring("-r".length());
                if (!"HEAD".equals(revStr)) {
                    try {
                        rev = Long.parseLong(revStr);
                    } catch (NumberFormatException nfe) {
                        continue;
                    }
                }
                url = (String) parts.get(2);
            } else if (parts.size() == 4 && "-r".equals(parts.get(1))) {
                String revStr = parts.get(2).toString();
                if (!"HEAD".equals(revStr)) {
                    try {
                        rev = Long.parseLong(revStr);
                    } catch (NumberFormatException nfe) {
                        continue;
                    }
                }
                url = (String) parts.get(3);
            }
            if (path != null && url != null) {
                if ("".equals(rootPath) && ((String) parts.get(0)).startsWith("/")) {
                    path = "/" + path;
                }
                try {
                    url = SVNURL.parseURIEncoded(url).toString();
                } catch (SVNException e) {
                    continue;
                }
                
                try {
                    SVNExternalInfo info = new SVNExternalInfo("", null, path, SVNURL.parseURIEncoded(url), rev);
                    result.add(info);
                } catch (SVNException e) {
                }
            }
        }
        return (SVNExternalInfo[]) result.toArray(new SVNExternalInfo[result.size()]);
    }


    //analogous to retrieve_internal
    public SVNAdminArea getAdminArea(File path) {
        //internal retrieve
        SVNAdminArea adminArea = null; 
        if (myAdminAreas != null) {
            adminArea = (SVNAdminArea) myAdminAreas.get(path);
        }
        return adminArea;
    }
    
    private File probe(File path) throws SVNException {
        int wcFormat = -1;
        SVNFileType type = SVNFileType.getType(path);
        if (type == SVNFileType.DIRECTORY) {
            wcFormat = SVNAdminAreaFactory.checkWC(path, true);
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
