package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;

public class SVNWCAccess implements ISVNEventListener {
    
    private SVNDirectory myAnchor;
    private SVNDirectory myTarget;
    private String myName;
    private SVNOptions myOptions;
    private ISVNEventListener myDispatcher;

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
        SVNDirectory anchor = parentFile != null ? new SVNDirectory(parentFile) : null;
        SVNDirectory target = file.isDirectory() ? new SVNDirectory(file) : null;
        
        if (anchor == null || !anchor.isVersioned()) {
            // parent is not versioned, do not use it.
            anchor = null;
        }
        if (target == null || !target.isVersioned()) {
            // target is missing, target is file or not versioned dir.
            // do not use it.
            target = null;
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
                            } else if (targetInAnchor.isDirectory()){
                                // not switched, target is valid dir, just use it.
                                anchor = null;       
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

    public void open(boolean lock, boolean recursive) throws SVNException {
        if (!lock) {
            return;
        }
        final Collection lockedDirs = new ArrayList();
        try {
            myAnchor.lock();
            lockedDirs.add(myAnchor);
            if (myTarget != myAnchor) {
                myTarget.lock();
                lockedDirs.add(myTarget);
            }        
            if (recursive) {
                visitDirectories(myTarget, new ISVNDirectoryVisitor() {
                    public void visit(SVNDirectory dir) throws SVNException {
                        dir.lock();
                        lockedDirs.add(dir);
                    }
                });
            }
        } catch (SVNException e) {
            for (Iterator dirs = lockedDirs.iterator(); dirs.hasNext();) {
                SVNDirectory dir = (SVNDirectory) dirs.next();
                dir.unlock();
            }
            SVNErrorManager.error(2, e);
        }
    }

    public void close(boolean unlock, boolean recursive) throws SVNException {
        if (!unlock) {
            return;
        }
        myAnchor.unlock();
        myTarget.unlock();
        if (recursive) {
            visitDirectories(myTarget, new ISVNDirectoryVisitor() {
                public void visit(SVNDirectory dir) throws SVNException {
                    dir.unlock();
                }
            });
        }
    }

    public void svnEvent(SVNEvent event) {
        if (myDispatcher != null) {
            try { 
                myDispatcher.svnEvent(event);
            } catch (Throwable th) {
            }
        }
    }
    
    private void visitDirectories(SVNDirectory root, ISVNDirectoryVisitor visitor) throws SVNException {
        SVNDirectory[] dirs = root.getChildDirectories();
        for (int i = 0; dirs != null && i < dirs.length; i++) {
            if (dirs[i].isVersioned()) {
                visitDirectories(dirs[i], visitor);
            }
            visitor.visit(dirs[i]);
        }
    }
    
    private interface ISVNDirectoryVisitor {
        public void visit(SVNDirectory dir) throws SVNException;
    }

}
