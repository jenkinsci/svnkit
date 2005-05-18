package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
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
    
    public SVNUpdateEditor(SVNWCAccess wcAccess, String switchURL, boolean recursive) throws SVNException {
        myWCAccess = wcAccess;
        myIsRecursive = recursive;
        myTarget = wcAccess.getTargetName();
        mySwitchURL = switchURL;
        
        SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry("");
        myTargetURL = entry.getURL();
        if (myTarget != null) {
            myTargetURL = PathUtil.append(myTargetURL, PathUtil.encode(myTarget));
        }
        wcAccess.getTarget().getEntries().close();

        if ("".equals(myTarget)) {
            myTarget = null;
        }
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myCurrentDirectory = createDirectoryInfo(null, "", false);
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
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);

        SVNLog log = myCurrentDirectory.getLog(true);
        Map attributes = new HashMap();
        String name = PathUtil.tail(path);
        
        attributes.put(SVNLog.NAME_ATTR, name);
        log.addCommand(SVNLog.DELETE_ENTRY, attributes, false);
        if (path.equals(myTarget)) {
            String kind = myCurrentDirectory.getDirectory().getFile(name).isFile() ? 
                    SVNProperty.KIND_FILE : SVNProperty.KIND_DIR;
            attributes.put(SVNLog.NAME_ATTR, name);
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.KIND), kind);
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), Long.toString(myTargetRevision));
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.DELETED), Boolean.TRUE.toString());
            log.addCommand(SVNLog.MODIFY_ENTRY, attributes, false);
            myIsTargetDeleted = true;
        }
        if (mySwitchURL != null) {
            myCurrentDirectory.getDirectory().destroy(name, true);
        }
        log.save();
        myCurrentDirectory.runLogs();
        myWCAccess.svnEvent(SVNEvent.createUpdateDeleteEvent(myWCAccess, myCurrentDirectory.getDirectory(), name));
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);

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
        
        SVNDirectory dir = parentDir.createChildDirectory(name, myCurrentDirectory.URL, myTargetRevision);
        if (dir == null) {
            SVNErrorManager.error(0, null);
        }
        dir.lock();
        myWCAccess.svnEvent(SVNEvent.createUpdateAddEvent(myWCAccess, parentDir, entry));
    }

    public void openDir(String path, long revision) throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);

        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, false);
        SVNEntries entries = myCurrentDirectory.getDirectory().getEntries();
        SVNEntry entry = entries.getEntry("");
        entry.setRevision(myTargetRevision);
        entry.setURL(myCurrentDirectory.URL);
        entry.setIncomplete(true);
        entries.save(true);        
    }

    public void absentDir(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.DIR);
    }

    public void absentFile(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.FILE);
    }
    
    private void absentEntry(String path, SVNNodeKind kind) throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);

        String name = PathUtil.tail(path);
        SVNEntries entries = myCurrentDirectory.getDirectory().getEntries();
        SVNEntry entry = entries.getEntry(name);
        if (entry != null && entry.isScheduledForAddition()) {
            SVNErrorManager.error(0, null);
        }
        if (entry == null) {
            entries.addEntry(name);
        }
        entry.setKind(kind);
        entry.setDeleted(false);
        entry.setRevision(myTargetRevision);
        entry.setAbsent(true);
        entries.save(true);        
        
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        myCurrentDirectory.propertyChanged(name, value);
    }

    public void closeDir() throws SVNException {
        Map modifiedWCProps = myCurrentDirectory.getChangedWCProperties();
        Map modifiedEntryProps = myCurrentDirectory.getChangedEntryProperties();
        Map modifiedProps = myCurrentDirectory.getChangedProperties();
        
        SVNLog log = myCurrentDirectory.getLog(true);
        log.logChangedWCProperties("", modifiedWCProps);
        log.logChangedEntryProperties("", modifiedEntryProps);
        log.save();

        myCurrentDirectory.runLogs();

        completeDirectory(myCurrentDirectory);
        myCurrentDirectory = myCurrentDirectory.Parent;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (!myIsRootOpen) {
            completeDirectory(myCurrentDirectory);
        }
        if (!myIsTargetDeleted) {
            bumpDirectories();
        }
        return null;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
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
        SVNDirectoryInfo info = new SVNDirectoryInfo(path);
        info.Parent = parent;
        info.IsAdded = added;
        String name = path != null ? PathUtil.tail(path) : "";

        if (mySwitchURL == null) {
            SVNDirectory dir = added ? null : info.getDirectory();
            if (dir != null && dir.getEntries().getEntry("") != null) {
                info.URL = dir.getEntries().getEntry("").getURL();
            }
            if (info.URL == null && parent != null) {
                info.URL = PathUtil.append(parent.URL, name);
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
                    info.URL = PathUtil.append(parent.URL, PathUtil.encode(name));
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
        public boolean IsAdded;
        public SVNDirectoryInfo Parent;
        public int RefCount;
        
        private String myPath;
        private int myLogCount;
        
        private Map myChangedProperties;
        private Map myChangedEntryProperties;
        private Map myChangedWCProperties;
        
        public SVNDirectoryInfo(String path) {
            myPath = path;
        }
        
        public void propertyChanged(String name, String value) {
            if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                myChangedEntryProperties = myChangedEntryProperties == null ? new HashMap() : myChangedEntryProperties;
                myChangedEntryProperties.put(name.substring(SVNProperty.SVN_ENTRY_PREFIX.length()), value);
            } else if (name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                myChangedWCProperties = myChangedWCProperties == null ? new HashMap() : myChangedWCProperties;
                myChangedWCProperties.put(name, value);
            } else {
                myChangedProperties = myChangedProperties == null ? new HashMap() : myChangedProperties;
                myChangedProperties.put(name, value);
            }
        }
        
        public Map getChangedWCProperties() {
            return myChangedWCProperties;
        }
        public Map getChangedEntryProperties() {
            return myChangedEntryProperties;
        }
        public Map getChangedProperties() {
            return myChangedProperties;
        }

        public SVNDirectory getDirectory() {
            return myWCAccess.getDirectory(myPath);
        }
        
        public SVNLog getLog(boolean increment) {
            SVNLog log = getDirectory().getLog(myLogCount);
            if (increment) {
                myLogCount++;
            }
            return log;
        }
        
        public void runLogs() throws SVNException {
            getDirectory().runLogs();
            myLogCount = 0;
        }
    }
}
