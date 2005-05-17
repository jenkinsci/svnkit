package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.util.PathUtil;

public class SVNUpdateEditor implements ISVNEditor {
    
    private String mySwitchURL;
    private String myTarget;
    private String myTargetURL;
    private boolean myIsRecursive;
    private SVNWCAccess myWCAccess;
    
    private SVNDirectoryInfo myCurrentDirectory;
    private long myTargetRevision;
    private boolean myIsRootOpen;
    private boolean myIsTargetDeleted;
    
    public SVNUpdateEditor(SVNWCAccess wcAccess, String targetURL, String switchURL, boolean recursive) throws SVNException {
        myWCAccess = wcAccess;
        myIsRecursive = recursive;
        myTarget = wcAccess.getTargetName();
        mySwitchURL = switchURL;
        myTargetURL = targetURL;
        if (myTargetURL == null) {
            SVNEntry entry = wcAccess.getTarget().getEntries().getEntry(myTarget);
            myTargetURL = entry.getURL();
            wcAccess.getTarget().getEntries().close();
        }
        if ("".equals(myTarget)) {
            myTarget = null;
        }
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myCurrentDirectory = createDirectoryInfo(null, null, false);
        if (myTarget == null) {
            SVNEntries entries = myCurrentDirectory.getDirectory().getEntries();
            SVNEntry entry = entries.getEntry("");
            entry.setRevision(myTargetRevision);
            entry.setURL(myCurrentDirectory.URL);
            entry.setIncomplete(true);
            entries.save(true);
        }
    }

    public void deleteEntry(String path, long revision) throws SVNException {        
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        SVNDirectory parentDir = myCurrentDirectory.getDirectory();
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, true);
        
        String name = PathUtil.tail(path);
        File file = parentDir.getFile(name);
        if (file.exists()) {
            SVNErrorManager.error(0, null);
        } else if (".svn".equals(name)) {
            SVNErrorManager.error(0, null);
        } 
        SVNEntry entry = parentDir.getEntries().getEntry(name);
        if (entry != null) {
            if (entry.isScheduledForAddition()) {
                SVNErrorManager.error(0, null);
            }            
        } else {
            entry = parentDir.getEntries().addEntry(name);
        }
        entry.setKind(SVNNodeKind.DIR);
        entry.setAbsent(false);
        entry.setDeleted(false);
        parentDir.getEntries().save(true);
        
        SVNDirectory dir = parentDir.createChildDirectory(name);
        if (dir == null) {
            SVNErrorManager.error(0, null);
        }
        dir.lock();
        myWCAccess.svnEvent(SVNEvent.createUpdateAddEvent(myWCAccess, dir, entry));
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, false);
        SVNEntries entries = myCurrentDirectory.getDirectory().getEntries();
        SVNEntry entry = entries.getEntry("");
        entry.setRevision(myTargetRevision);
        entry.setURL(myCurrentDirectory.URL);
        entry.setIncomplete(true);
        entries.save(true);        
    }

    public void absentDir(String path) throws SVNException {
        String name = PathUtil.tail(path);
        SVNEntries entries = myCurrentDirectory.getDirectory().getEntries();
        SVNEntry entry = entries.getEntry(name);
        if (entry != null && entry.isScheduledForAddition()) {
            SVNErrorManager.error(0, null);
        }
        if (entry == null) {
            entries.addEntry(name);
        }
        entry.setKind(SVNNodeKind.DIR);
        entry.setDeleted(false);
        entry.setRevision(myTargetRevision);
        entry.setAbsent(true);
        entries.save(true);        
    }

    public void changeDirProperty(String name, String value) throws SVNException {
    }

    public void closeDir() throws SVNException {
        // merge properties

        // run log to complete properties merge.
        completeDirectory(myCurrentDirectory);
        myCurrentDirectory.dispose();
        myCurrentDirectory = myCurrentDirectory.Parent;

        // notify if there were prop changes and directory was not added.
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (!myIsRootOpen) {
            completeDirectory(myCurrentDirectory);
        }
        bumpDirectories();
        return null;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void openFile(String path, long revision) throws SVNException {
    }

    public void changeFileProperty(String name, String value) throws SVNException {
    }

    public void applyTextDelta(String baseChecksum) throws SVNException {
    }

    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd() throws SVNException {
    }

    public void closeFile(String textChecksum) throws SVNException {
    }

    public void abortEdit() throws SVNException {
    }
    
    private void bumpDirectories() throws SVNException {
        if (myIsTargetDeleted) {
            return;
        }
        SVNDirectory dir = myWCAccess.getTarget();
        if (myTarget != null){
            if (dir.getChildDirectory(myTarget) == null) {
                SVNEntry entry = dir.getEntries().getEntry(myTarget);
                boolean save = bumpEntry(dir.getEntries(), entry, mySwitchURL, myTargetRevision, false);
                if (save) {
                    dir.getEntries().save(true);
                } else {
                    dir.getEntries().close();
                }
                return;
            }
            dir = dir.getChildDirectory(myTarget);
        }
        bumpDirectory(dir, mySwitchURL);
    }
    
    private void bumpDirectory(SVNDirectory dir, String url) throws SVNException {
        SVNEntries entries = dir.getEntries();
        boolean save = bumpEntry(entries, entries.getEntry(""), url, myTargetRevision, false);
        Map childDirectories = new HashMap();
        for (Iterator ents = entries.entries(); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            String childURL = url != null ? PathUtil.append(url, PathUtil.encode(entry.getName())) : null;
            if (entry.getKind() == SVNNodeKind.FILE) {
                save |= bumpEntry(entries, entry, childURL, myTargetRevision, true);
            } else if (myIsRecursive && entry.getKind() == SVNNodeKind.DIR) {
                SVNDirectory childDirectory = dir.getChildDirectory(entry.getName());
                if (!entry.isScheduledForAddition() && (childDirectory == null || !childDirectory.isVersioned())) {
                    myWCAccess.svnEvent(SVNEvent.createUpdateDeleteEvent(myWCAccess, dir, entry));
                    entries.deleteEntry(entry.getName());
                    save = true;
                } else {
                    // schedule for recursion, map of dir->url
                    childDirectories.put(childDirectory, childURL);
                }
            } 
        }
        if (save) {
            entries.save(true);
        }
        for (Iterator children = childDirectories.keySet().iterator(); children.hasNext();) {
            SVNDirectory child = (SVNDirectory) children.next();
            String childURL = (String) childDirectories.get(child);
            bumpDirectory(child, childURL);
        }
    }
    
    private static boolean bumpEntry(SVNEntries entries, SVNEntry entry, String url, long revision, boolean delete) {
        boolean save = false;
        if (url != null) {
            save |= entry.setURL(url);
        }
        if (revision >=0 && !entry.isScheduledForAddition() && !entry.isScheduledForDeletion()) {
            save |= entry.setRevision(revision);
        }
        if (delete && (entry.isDeleted() || (entry.isAbsent() && entry.getRevision() != revision))) {
            entries.deleteEntry(entry.getName());
            save = true;
        }
        return save;
    }
    
    private void completeDirectory(SVNDirectoryInfo info) throws SVNException {
        while(info != null) {
            info.RefCount--;
            if (info.RefCount > 0) {
                return;
            }
            if (info.Parent == null && myTarget != null) {
                return;
            }
            SVNEntries entries = info.getDirectory().getEntries();
            if (entries.getEntry("") == null) {
                SVNErrorManager.error(0, null);
            }
            for (Iterator ents = entries.entries(); ents.hasNext();) {
                SVNEntry entry = (SVNEntry) ents.next();
                if ("".equals(entry.getName())) {
                    entry.setIncomplete(false);
                    continue;
                }
                if (entry.isDeleted()) {
                    if (!entry.isScheduledForAddition()) {
                        entries.deleteEntry(entry.getName());
                    } else {
                        entry.setDeleted(false);
                    }
                } else if (entry.isAbsent() && entry.getRevision() != myTargetRevision) {
                    entries.deleteEntry(entry.getName());
                } else if (entry.getKind() == SVNNodeKind.DIR) {
                    SVNDirectory childDirectory = info.getDirectory().getChildDirectory(entry.getName());
                    if ((childDirectory == null || !childDirectory.isVersioned()) 
                            && !entry.isAbsent() && !entry.isScheduledForAddition()) {
                        myWCAccess.svnEvent(SVNEvent.createUpdateDeleteEvent(myWCAccess, info.getDirectory(), entry));
                        entries.deleteEntry(entry.getName());
                    }
                }
            }
            entries.save(true);
            info = info.Parent;
        }
    }
    
    private SVNDirectoryInfo createDirectoryInfo(SVNDirectoryInfo parent, String path, boolean added) throws SVNException {
        SVNDirectoryInfo info = new SVNDirectoryInfo();
        info.Parent = parent;
        info.IsAdded = added;
        info.Path = parent != null ? new File(parent.Path, path) : 
            myWCAccess.getAnchor().getRoot();
        info.Name = path != null ? PathUtil.tail(path) : "";

        if (mySwitchURL == null) {
            SVNDirectory dir = info.getDirectory();
            if (dir.getEntries().getEntry("") != null) {
                info.URL = dir.getEntries().getEntry("").getURL();
            }
            if (info.URL == null && parent != null) {
                info.URL = PathUtil.append(parent.URL, PathUtil.encode(info.Name));
            } else if (info.URL == null && parent == null) {
                info.URL = myTargetURL;
            }
        } else {
            if (parent == null) {
                info.URL = myTarget == null ? mySwitchURL : PathUtil.removeTail(mySwitchURL);
            } else {
                if (myTarget != null && parent.Parent == null) {
                    info.URL = mySwitchURL;
                } else {
                    info.URL = PathUtil.append(parent.URL, PathUtil.encode(info.Name));
                }
            }
        }
        info.RefCount = 1;
        if (info.Parent != null) {
            info.Parent.RefCount++;
        }
        return info;
    }
    
    private class SVNDirectoryInfo {
        public String URL;
        public File Path;
        public String Name;
        public boolean IsAdded;
        public SVNDirectoryInfo Parent;
        public int RefCount;
        
        private  SVNDirectory myDirectory;
        
        public SVNDirectory getDirectory() {
            if (myDirectory == null) {
                myDirectory = new SVNDirectory(Path);
            }
            return myDirectory;
        }
        
        public void dispose() {
            if (myDirectory != null) {
                myDirectory.dispose();
                myDirectory = null;
            }
        }
    }
}
