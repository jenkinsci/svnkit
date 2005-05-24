package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;

public class SVNWCAccess implements ISVNEventListener {
    
    private SVNDirectory myAnchor;
    private SVNDirectory myTarget;
    private String myName;
    private SVNOptions myOptions;
    private ISVNEventListener myDispatcher;
    
    private Map myDirectories;
    private Map myExternals;

    public static SVNWCAccess create(File file) throws SVNException {
        File parentFile = file.getParentFile();
        String name = file.getName();
        try {
            if (file.exists()) {
                name = file.getCanonicalFile().getName();
            }
        } catch (IOException e) {
        }
        if (parentFile != null && (!parentFile.exists() || !parentFile.isDirectory())) {
            // parent doesn't exist or not a directory
            SVNErrorManager.error(1, null);
        }
        SVNDirectory anchor = parentFile != null ? new SVNDirectory(null, "", parentFile) : null;
        SVNDirectory target = file.isDirectory() ? new SVNDirectory(null, name, file) : null;
        
        if (anchor == null || !anchor.isVersioned()) {
            // parent is not versioned, do not use it.
            anchor = null;
            if (target != null) {
                target.setWCAccess(null, "");
            }
        }
        if (target == null || !target.isVersioned()) {
            // target is missing, target is file or not versioned dir.
            // do not use it.
            target = null;
            if (target != null) {
                target.setWCAccess(null, "");
            }
        }
        if (target != null && anchor != null) {
            // both are versioned dirs, 
            // check whether target is switched.
            SVNEntry targetInAnchor = anchor.getEntries().getEntry(name);            
            SVNDirectory anchorCopy = anchor;
            try {
                if (targetInAnchor == null) {
                    // target is disjoint, do not use anchor.
                    anchor = null;
                    if (target != null) {
                        target.setWCAccess(null, "");
                    }
                } else {
                        SVNEntry anchorEntry = anchor.getEntries().getEntry("");
                        SVNEntry targetEntry = target.getEntries().getEntry("");
                        String anchorURL = anchorEntry.getURL();
                        String targetURL = targetEntry.getURL();
                        if (anchorURL != null && targetURL != null) {
                            String urlName = PathUtil.encode(targetInAnchor.getName());
                            String expectedURL = PathUtil.append(anchorURL, urlName);
                            if (!expectedURL.equals(targetURL) || 
                                    !anchorURL.equals(PathUtil.removeTail(targetURL))) {
                                // switched, do not use anchor.
                                anchor = null;                        
                                if (target != null) {
                                    target.setWCAccess(null, "");
                                }
                            } 
                        }
                } 
            } finally {
                // close entries.
                if (anchor == null) {
                    anchorCopy.getEntries().close();
                    anchorCopy.dispose();
                    anchorCopy = null;
                }
                target.dispose();
            }
        } else if (target == null && anchor == null) {
            // both are not versioned :(
            SVNErrorManager.error(1, null);
        }
        return new SVNWCAccess(
                anchor != null ? anchor : target, 
                target != null ? target : anchor, 
                anchor != null ? name : "");
    }
    
    public static boolean isVersionedDirectory(File path) {
        SVNDirectory dir = new SVNDirectory(null, null, path);
        return dir.isVersioned();
    }
    
    public SVNWCAccess(SVNDirectory anchor, SVNDirectory target, String name) {
        myAnchor = anchor;
        myTarget = target;
        myName = name;
        myAnchor.setWCAccess(this, "");
        if (myTarget != myAnchor) {
            myTarget.setWCAccess(this, myName);
        }
    }
    
    public void setOptions(SVNOptions options) {
        myOptions = options; 
    }
    
    public void setEventDispatcher(ISVNEventListener dispatcher) {
        myDispatcher = dispatcher;
    }
    
    public SVNOptions getOptions() {
        if (myOptions == null) {
            myOptions = new SVNOptions();
        }
        return myOptions;
    }

    public String getTargetName() {
        return myName;
    }

    public SVNDirectory getAnchor() {
        return myAnchor;
    }

    public SVNDirectory getTarget() {
        return myTarget;
    }
    
    public String getTargetEntryProperty(String propertyName) throws SVNException {
        SVNDirectory dir = "".equals(myName) ? getAnchor() : new SVNDirectory(this, myName, new File(getAnchor().getRoot(), myName));
        SVNEntries entries = null;
        String name = "";
        if (dir != null) {
            entries = dir.getEntries();
        }
        if (entries == null) {
            entries = getAnchor().getEntries();
            name = myName;
        }
        String value = null;
        if (entries != null) {
            try {
                if (entries.getEntry(name) == null) {
                    name = "";
                }
                value = entries.getPropertyValue(name, propertyName);
            } finally {
                entries.close();
            }
        }
        return value;        
    }
    
    public SVNDirectory getDirectory(String path) {
        if (myDirectories == null || path == null) {
            return null;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return (SVNDirectory) myDirectories.get(path);
    }
    
    public SVNDirectory[] getChildDirectories(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        Collection dirs = new ArrayList();
        for (Iterator paths = myDirectories.keySet().iterator(); paths.hasNext();) {
            String p = (String) paths.next();
            if ("".equals(path)) {
                if (!"".equals(p) && p.indexOf("/") < 0) {
                    dirs.add(myDirectories.get(p));
                }
            } else {
                p = PathUtil.removeTail(p);
                if (p.equals(path)) {
                    dirs.add(myDirectories.get(p));
                }
            }
        }
        return (SVNDirectory[]) dirs.toArray(new SVNDirectory[dirs.size()]);
    }
    
    public boolean hasDirectory(String path) {
        if (myDirectories == null || path == null) {
            return false;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return myDirectories.containsKey(path);
    }

    public void open(boolean lock, boolean recursive) throws SVNException {
        if (!lock) {
            return;
        }
        if (myDirectories == null) {
            myDirectories = new HashMap();
        }
        try {
            myAnchor.lock();
            myDirectories.put("", myAnchor);            
            if (myTarget != myAnchor) {
                myTarget.lock();
                myDirectories.put(myName, myTarget);
            }        
            if (recursive) {
                visitDirectories(myTarget == myAnchor ? "" : myName, myTarget, new ISVNDirectoryVisitor() {
                    public void visit(String path, SVNDirectory dir) throws SVNException {
                        dir.lock();
                        myDirectories.put(path, dir);
                    }
                });
            }
        } catch (SVNException e) {
            close(lock, recursive);
            SVNErrorManager.error(2, e);
        }
    }

    public void close(boolean unlock, boolean recursive) throws SVNException {
        if (!unlock || myDirectories == null) {
            return;
        }
        myAnchor.getEntries().close();
        myAnchor.unlock();
        myDirectories.remove("");
        if (myTarget != myAnchor) {
            myTarget.getEntries().close();
            myTarget.unlock();
            myDirectories.remove(myName);
        }
        if (recursive) {
            visitDirectories(myTarget == myAnchor ? "" : myName, myTarget, new ISVNDirectoryVisitor() {
                public void visit(String path, SVNDirectory dir) throws SVNException {
                    dir.unlock();
                    myDirectories.remove(path);
                }
            });
        }
        myDirectories = null;
    }
    
    public SVNExternalInfo[] addExternals(SVNDirectory directory, String externals) throws SVNException {
        if (externals == null) {
            return null;
        }
        Collection result = new ArrayList();
        for(StringTokenizer lines = new StringTokenizer(externals, "\n\r"); lines.hasMoreTokens();) {
            String line = lines.nextToken().trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            String url = null;
            String path = null;
            long rev = -1;
            List parts = new ArrayList(4); 
            for(StringTokenizer tokens = new StringTokenizer(line, " \t"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken().trim();
                parts.add(token);
            }
            if (parts.size() < 2) {
                continue;
            }
            path = PathUtil.append(directory.getPath(), (String) parts.get(0));
            path = PathUtil.removeLeadingSlash(path);
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
                url = (String) parts.get(4);                
            }
            if (path!= null && url != null) {
                SVNExternalInfo info = addExternal(path, url, rev);
                result.add(info);
            }
        }
        return (SVNExternalInfo[]) result.toArray(new SVNExternalInfo[result.size()]);
    }
    
    public SVNExternalInfo addExternal(String path, String url, long revision) {
        if (myExternals == null) {
            myExternals = new TreeMap();
        }
        
        SVNExternalInfo info = (SVNExternalInfo) myExternals.get(path);
        if (info == null) {
            // this means adding new external, either during report or during update,
            info = new SVNExternalInfo(new File(getAnchor().getRoot(), path), path, null, -1);
            myExternals.put(path, info);
        } 
        // set it as new, report will also set old values, update will left it as is.
        info.setNewExternal(url, revision);
        return info;
    }
    
    public Iterator externals() {
        if (myExternals == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        return myExternals.values().iterator();
    }

    public void svnEvent(SVNEvent event) {
        if (myDispatcher != null) {
            try { 
                myDispatcher.svnEvent(event);
            } catch (Throwable th) {
            }
        }
    }
    
    private void visitDirectories(String parentPath, SVNDirectory root, ISVNDirectoryVisitor visitor) throws SVNException {
        File[] dirs = root.getRoot().listFiles();
        for (int i = 0; dirs != null && i < dirs.length; i++) {
            if (dirs[i].isFile()) {
                continue;
            }
            String path = PathUtil.append(parentPath, dirs[i].getName());
            path = PathUtil.removeLeadingSlash(path);
            SVNDirectory dir = new SVNDirectory(this, PathUtil.append(parentPath, dirs[i].getName()), dirs[i]);
            if (dir.isVersioned()) {
                visitDirectories(path, dir, visitor);
                visitor.visit(path, dir);
            }
        }
    }
    
    private interface ISVNDirectoryVisitor {
        public void visit(String path, SVNDirectory dir) throws SVNException;
    }

    public SVNDirectory addDirectory(String path, File file) {
        if (myDirectories != null) {
            SVNDirectory dir = new SVNDirectory(this, path, file);
            myDirectories.put(path, dir);
            return dir;
        }
        return null;
    }
    
    public void removeDirectory(String path) throws SVNException {
        SVNDirectory dir = (SVNDirectory) myDirectories.remove(path);
        if (dir != null) {
            myExternals.remove(path);
            dir.unlock();
        }
    }
    
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("anchor: '" + getAnchor().getRoot().toString() + "'\n");
        result.append("target: '" + getTarget().getRoot().toString() + "'\n");
        result.append("target name: '" + getTargetName() + "'\n");
        return result.toString();
    }

}
