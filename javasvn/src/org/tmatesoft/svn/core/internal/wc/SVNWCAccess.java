package org.tmatesoft.svn.core.internal.wc;

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

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.wc.ISVNEventListener;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNOptions;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.DebugLog;

public class SVNWCAccess implements ISVNEventListener {

    private SVNDirectory myAnchor;
    private SVNDirectory myTarget;
    private String myName;
    private SVNOptions myOptions;
    private ISVNEventListener myDispatcher;

    private Map myDirectories;
    private Map myExternals;

    public static SVNWCAccess create(File file) throws SVNException {
        file = file.getAbsoluteFile();
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
            SVNErrorManager.error("svn: '" + parentFile + "' does not exist");
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
            SVNEntry targetInAnchor = anchor.getEntries().getEntry(name, true);
            SVNDirectory anchorCopy = anchor;
            try {
                if (targetInAnchor == null) {
                    // target is disjoint, do not use anchor.
                    anchor = null;
                    if (target != null) {
                        target.setWCAccess(null, "");
                    }
                } else {
                        SVNEntry anchorEntry = anchor.getEntries().getEntry("", true);
                        SVNEntry targetEntry = target.getEntries().getEntry("", true);
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
                if (anchor == null && anchorCopy != null) {
                    anchorCopy.getEntries().close();
                    anchorCopy.dispose();
                    anchorCopy = null;
                }
                if (target != null) {
                    target.dispose();
                }
            }
        } else if (target == null && anchor == null) {
            // both are not versioned :(
            SVNErrorManager.error("svn: '" + file + "' is not under version control");
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
        SVNEntries anchorEntries = getAnchor().getEntries();
        SVNEntries targetEntries = getTarget().getEntries();
        if (!"".equals(myName) && getAnchor() != getTarget()) {
            String value = null;
            // another directory.
            if (targetEntries != null) {
                value = targetEntries.getPropertyValue("", propertyName);
            }
            if (value == null) {
                // no value or no entries, get from parent.
                value = anchorEntries.getPropertyValue(myName, propertyName);
                if (value == null) {
                    // no entry in parent.
                    value = anchorEntries.getPropertyValue("", propertyName);
                    if (value != null &&
                            (SVNProperty.URL.equals(propertyName)|| SVNProperty.COPYFROM_URL.equals(propertyName))) {
                        // special handling for URLs.
                        value = PathUtil.append(value, PathUtil.encode(myName));
                    }
                }
            }
            return value;
        }
        String value = anchorEntries.getPropertyValue(myName, propertyName);
        if (value == null && anchorEntries.getEntry(myName, true) == null) {
            // fetch from root.
            value = anchorEntries.getPropertyValue("", propertyName);
            if (value != null &&
                    (SVNProperty.URL.equals(propertyName)|| SVNProperty.COPYFROM_URL.equals(propertyName))) {
                // special handling for URLs.
                value = PathUtil.append(value, PathUtil.encode(myName));
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
        open(lock, false, recursive);
    }

    public void open(final boolean lock, final boolean stealLock, boolean recursive) throws SVNException {
        if (myDirectories == null) {
            myDirectories = new HashMap();
        }
        try {
            if (lock) {
                if (!(stealLock && myAnchor.isLocked())) {
                    myAnchor.lock();
                }
            }
            myDirectories.put("", myAnchor);
            if (myTarget != myAnchor) {
                if (lock) {
                    if (!(stealLock && myTarget.isLocked())) {
                        myTarget.lock();
                    }
                }
                myDirectories.put(myName, myTarget);
            }
            if (recursive) {
                visitDirectories(myTarget == myAnchor ? "" : myName, myTarget, new ISVNDirectoryVisitor() {
                    public void visit(String path, SVNDirectory dir) throws SVNException {
                        if (lock && (!dir.isLocked() || !stealLock)) {
                            dir.lock();
                        }
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
            if (myDirectories != null) {
                myDirectories = null;
            }
            myAnchor.dispose();
            myTarget.dispose();
            return;
        }
        myAnchor.dispose();
        myAnchor.unlock();
        if (myTarget != myAnchor) {
            myTarget.dispose();
            myTarget.unlock();
        }
        if (recursive) {
            for (Iterator dirs = myDirectories.values().iterator(); dirs.hasNext();) {
                SVNDirectory directory = (SVNDirectory) dirs.next();
                directory.unlock();
            }
        }
        myDirectories = null;
    }

    public SVNExternalInfo[] addExternals(SVNDirectory directory, String externals) throws SVNException {
        if (externals == null) {
            return null;
        }
        Collection result = new ArrayList();
        SVNExternalInfo[] parsed = parseExternals(directory.getPath(), externals);
        for (int i = 0; i < parsed.length; i++) {
            SVNExternalInfo info = addExternal(directory, parsed[i].getPath(), parsed[i].getOldURL(), parsed[i].getOldRevision());
            result.add(info);
        }
        // get existing externals and update all that are not in result but in this dir.
        for (Iterator exts = externals(); exts.hasNext();) {
            SVNExternalInfo info = (SVNExternalInfo) exts.next();
            if (!result.contains(info) && directory.getPath().equals(info.getOwnerPath())) {
                info.setNewExternal(null, -1);
            }
        }
        return (SVNExternalInfo[]) result.toArray(new SVNExternalInfo[result.size()]);
    }

    public static SVNExternalInfo[] parseExternals(String rootPath, String externals) {
        Collection result = new ArrayList();
        if (externals == null) {
            return (SVNExternalInfo[]) result.toArray(new SVNExternalInfo[result.size()]);
        }
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
            path = PathUtil.append(rootPath, (String) parts.get(0));
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
            if (path!= null && url != null) {
                path = PathUtil.removeLeadingSlash(path);
                path = PathUtil.removeTrailingSlash(path);
                if ("".equals(rootPath) && ((String) parts.get(0)).startsWith("/")) {
                    path = "/" + path;
                }
                url = PathUtil.removeTrailingSlash(url);

                SVNExternalInfo info = new SVNExternalInfo("", null, path, url, rev);
                    //addExternal(directory, path, url, rev);
                result.add(info);
            }
        }
        return (SVNExternalInfo[]) result.toArray(new SVNExternalInfo[result.size()]);
    }

    private SVNExternalInfo addExternal(SVNDirectory dir, String path, String url, long revision) {
        if (myExternals == null) {
            myExternals = new TreeMap();
        }

        SVNExternalInfo info = (SVNExternalInfo) myExternals.get(path);
        if (info == null) {
            // this means adding new external, either during report or during update,
            info = new SVNExternalInfo(dir.getPath(), new File(getAnchor().getRoot(), path), path, null, -1);
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

    private void visitDirectories(String parentPath, SVNDirectory root, ISVNDirectoryVisitor visitor) throws SVNException {
        Iterator entries = root.getEntries().entries(true);
        while (entries.hasNext()) {
            SVNEntry entry = (SVNEntry) entries.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            if (entry.getKind() == SVNNodeKind.FILE) {
                continue;
            }
            File dir = new File(root.getRoot(), entry.getName());
            if (dir.isDirectory() && entry.getKind() == SVNNodeKind.DIR) {
                String path = PathUtil.append(parentPath, dir.getName());
                path = PathUtil.removeLeadingSlash(path);
                SVNDirectory svnDir = new SVNDirectory(this,
                        "".equals(parentPath) ? dir.getName() :
                    PathUtil.append(parentPath, dir.getName()),
                    dir);
                if (svnDir.isVersioned()) {
                    visitDirectories(path, svnDir, visitor);
                    visitor.visit(path, svnDir);
                }
            }
        }
    }


    private interface ISVNDirectoryVisitor {
        public void visit(String path, SVNDirectory dir) throws SVNException;
    }

    public SVNDirectory addDirectory(String path, File file) throws SVNException {
        return addDirectory(path, file, false, false);
    }
    public SVNDirectory addDirectory(String path, File file, boolean recursive, boolean lock) throws SVNException {
        if (myDirectories != null) {
            SVNDirectory dir = new SVNDirectory(this, path, file);
            DebugLog.log("adding dir: " + path + " (" + file + ")");
            if (myDirectories.put(path, dir) == null && lock && !dir.isLocked()) {
                dir.lock();
            }
            if (recursive) {
                File[] dirs = file.listFiles();
                for (int i = 0; i < dirs.length; i++) {
                    File childDir = dirs[i];
                    if (".svn".equals(childDir)) {
                        continue;
                    }
                    SVNFileType fType = SVNFileType.getType(childDir);
                    if (fType == SVNFileType.DIRECTORY && SVNWCAccess.isVersionedDirectory(childDir)) {
                        // recurse
                        String childPath = PathUtil.append(path, childDir.getName());
                        addDirectory(childPath, childDir, recursive, lock);
                    }
                }
            }
            return dir;
        }
        return null;
    }

    public void removeDirectory(String path) throws SVNException {
        SVNDirectory dir = (SVNDirectory) myDirectories.remove(path);
        if (dir != null) {
            if (myExternals != null) {
                myExternals.remove(path);
            }
            dir.unlock();
        }
    }

    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("anchor: '" + getAnchor().getRoot().toString() + "'\n");
        result.append("target: '" + getTarget().getRoot().toString() + "'\n");
        result.append("target name: '" + getTargetName() + "'");
        return result.toString();
    }

    public void svnEvent(SVNEvent event) {
        svnEvent(event, ISVNEventListener.UNKNOWN);
    }

    public void svnEvent(SVNEvent event, double progress) {
        if (myDispatcher != null) {
            try {
                myDispatcher.svnEvent(event, progress);
            } catch (Throwable th) {
            }
        }
    }

    public void checkCancelled() throws SVNCancelException {
        if (myDispatcher != null) {
            myDispatcher.checkCancelled();
        }
    }

    public ISVNEventListener getEventDispatcher() {
        return myDispatcher;
    }
}
