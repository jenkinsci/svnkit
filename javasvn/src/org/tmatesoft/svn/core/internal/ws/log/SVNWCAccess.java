package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;

public class SVNWCAccess implements ISVNEventListener {
    
    private SVNDirectory myAnchor;
    private SVNDirectory myTarget;
    private String myName;
    private SVNOptions myOptions;
    private ISVNEventListener myDispatcher;
    
    private Map myDirectories;

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
        myAnchor.unlock();
        myDirectories.remove("");
        if (myTarget != myAnchor) {
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

}
