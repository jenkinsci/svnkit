/*
 * Created on 31.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.PathUtil;

public class SVNMerger {

    private boolean myIsDryRun;
    private SVNWCAccess myWCAccess;
    private boolean myIsForce;
    
    private String myAddedPath;
    private String myURL;
    private long myRevision;

    public SVNMerger(SVNWCAccess wcAccess, String url, long rev, boolean force, boolean dryRun) {
        myWCAccess = wcAccess;
        myIsDryRun = dryRun;
        myIsForce = force;
        myRevision = rev;
        myURL = url;
    }

    public SVNStatusType directoryDeleted(String path) throws SVNException {
        String parentPath = PathUtil.removeTail(path);
        SVNDirectory parentDir = myWCAccess.getDirectory(parentPath);
        if (parentDir == null) {
            return SVNStatusType.MISSING;
        }
        String name = PathUtil.tail(path);
        File targetFile = parentDir.getFile(name, false);
        if (targetFile.isDirectory()) {
            // TODO delete this dir (schedule for deletion).
            // if we got an exception - > return obstructed.
            return SVNStatusType.CHANGED;
        } else if (targetFile.isFile()) {
            return SVNStatusType.OBSTRUCTED;
        } 
        return SVNStatusType.MISSING;
    }

    public SVNStatusType fileDeleted(String path) throws SVNException {
        String parentPath = PathUtil.removeTail(path);
        SVNDirectory parentDir = myWCAccess.getDirectory(parentPath);
        if (parentDir == null) {
            return SVNStatusType.MISSING;
        }
        String name = PathUtil.tail(path);
        File targetFile = parentDir.getFile(name, false);
        if (targetFile.isDirectory()) {
            return SVNStatusType.OBSTRUCTED;
        } else if (targetFile.isFile()) {
            // TODO delete this file (schedule for deletion).
            // if we got an exception - > return obstructed.
            return SVNStatusType.CHANGED;
        } 
        return SVNStatusType.MISSING;
    }
    
    public SVNStatusType directoryAdded(String path, long revision) throws SVNException {
        SVNDirectory parentDir = getParentDirectory(path);
        if (parentDir == null) {
            if (myIsDryRun && myAddedPath != null && path.startsWith(myAddedPath)) {
                return SVNStatusType.CHANGED;
            } 
            return SVNStatusType.MISSING;
        }
        String name = PathUtil.tail(path);
        File file = parentDir.getFile(name, false);
        if (!file.exists()) {
            SVNEntry entry = parentDir.getEntries().getEntry(name);
            if (entry != null && entry.isScheduledForDeletion()) {
                // it was a file.
                return SVNStatusType.OBSTRUCTED;
            }
            if (!myIsDryRun) {
                file.mkdirs();
                String url = PathUtil.append(myURL, PathUtil.encode(path));
                addDirectory(parentDir, name, url, revision);
            }
            if (myIsDryRun) {
                myAddedPath = path + "/";
            }
            return SVNStatusType.CHANGED;
        } else if (file.isDirectory()) {
            SVNEntry entry = parentDir.getEntries().getEntry(name);
            if (entry == null || entry.isScheduledForDeletion()) {
                if (myIsDryRun) {
                    myAddedPath = path + "/";
                } else {
                    String url = PathUtil.append(myURL, PathUtil.encode(path));
                    addDirectory(parentDir, name, url, revision);
                }
                return SVNStatusType.CHANGED;
            } else {
                return SVNStatusType.OBSTRUCTED;
            }
        } else if (file.isFile()) {
            if (myIsDryRun) {
                myAddedPath = null;
            }
            return SVNStatusType.OBSTRUCTED;
        }
        return SVNStatusType.UNKNOWN;
    }

    public SVNStatusType[] fileChanged(String path, File older, File yours, long rev1, long rev2,
            String mimeType1, String mimeType2,
            Map baseProps, Map propDiff) throws SVNException {
        SVNStatusType[] result = new SVNStatusType[] {SVNStatusType.UNKNOWN, SVNStatusType.UNKNOWN};
        String parentPath = PathUtil.removeTail(path);
        SVNDirectory parentDir = myWCAccess.getDirectory(parentPath);
        if (parentDir == null) {
            result[0] = SVNStatusType.MISSING;
            result[1] = SVNStatusType.MISSING;
            return result;
        }

        String name = PathUtil.tail(path);
        File mine = parentDir.getFile(name, false);
        SVNEntry entry = parentDir.getEntries().getEntry(name);
        
        if (!mine.isFile() || entry == null) {
            result[0] = SVNStatusType.MISSING;
            result[1] = SVNStatusType.MISSING;
            return result;
        }
        if (propDiff != null && !propDiff.isEmpty()) {
            result[1] = propertiesChanged(parentPath, name, baseProps, propDiff);
        } else {
            result[1] = SVNStatusType.UNCHANGED;
        }
        if (older != null) {
            boolean isTextModified = parentDir.hasTextModifications(name, false);
            SVNStatusType mergeResult = null;
            if (!isTextModified) {
                if (SVNWCUtil.isBinaryMimetype(mimeType1) || SVNWCUtil.isBinaryMimetype(mimeType2)) {
                    boolean equals = false;
                    try {
                        equals = SVNFileUtil.compareFiles(mine, yours, null);
                    } catch (IOException e) {
                        equals = false;
                    }
                    if (equals) {
                        if (!myIsDryRun) {
                            try {
                                SVNFileUtil.rename(yours, mine);
                            } catch (IOException e) {
                                SVNErrorManager.error(0, e);
                            }
                        }
                        mergeResult = SVNStatusType.MERGED;
                    }
                }
            }
            if (mergeResult == null) {
                String minePath = name;
                String olderPath = SVNFileUtil.getBasePath(older);
                String yoursPath = SVNFileUtil.getBasePath(yours);
                String targetLabel = ".working";
                String leftLabel = ".merge-left.r" + rev1;
                String rightLabel = ".merge-right.r" + rev2;
                mergeResult = parentDir.mergeText(minePath, olderPath, yoursPath, targetLabel, leftLabel, rightLabel, myIsDryRun);
            }
            
            if (mergeResult == SVNStatusType.CONFLICTED) {
                result[0] = SVNStatusType.CONFLICTED;
            } else if (isTextModified) {
                result[0] = SVNStatusType.MERGED;
            } else if (mergeResult == SVNStatusType.MERGED) {
                result[0] = SVNStatusType.CHANGED;
            } else if (mergeResult == SVNStatusType.UNCHANGED) {
                result[0] = SVNStatusType.MISSING;
            } else {
                result[0] = SVNStatusType.UNCHANGED;
            }
        }
        return result;
    }
    
    public SVNStatusType[] fileAdded(String path, File older, File yours, long rev1, long rev2,
            String mimeType1, String mimeType2,
            Map baseProps, Map propDiff) throws SVNException {
        SVNStatusType[] result = new SVNStatusType[] {SVNStatusType.UNKNOWN, SVNStatusType.UNKNOWN};
        SVNDirectory parentDir = getParentDirectory(path);
        if (parentDir == null) {
            if (myIsDryRun && myAddedPath != null && path.startsWith(myAddedPath)) {
                result[0] = SVNStatusType.CHANGED;
                result[1] = propDiff != null && !propDiff.isEmpty() ? SVNStatusType.CHANGED : result[1];
            } else {
                result[0] = SVNStatusType.MISSING;
            }
            return result;
        }
        String name = PathUtil.tail(path);
        File mine = parentDir.getFile(name, false);
        
        if (!mine.exists()) {
            SVNEntry entry = parentDir.getEntries().getEntry(name);
            if (entry != null && !entry.isScheduledForDeletion()) {
                result[0] = SVNStatusType.OBSTRUCTED;
                return result;
            } else if (!myIsDryRun) {
                String url = PathUtil.append(myURL, PathUtil.encode(path));
                addFile(parentDir, name, SVNFileUtil.getBasePath(yours), propDiff, url, rev2);
            }
            result[0] = SVNStatusType.CHANGED;
            if (propDiff != null && !propDiff.isEmpty()) {
                result[1] = SVNStatusType.CHANGED;
            }            
        } else if (mine.isDirectory()) {
            result[0] = SVNStatusType.OBSTRUCTED;
        } else if (mine.isFile()) {
            SVNEntry entry = parentDir.getEntries().getEntry(name);
            if (entry == null || entry.isScheduledForDeletion()) {
                result[0] = SVNStatusType.OBSTRUCTED;
            } else {
                return fileChanged(path, older, yours, rev1, rev2, mimeType1, mimeType2, baseProps, propDiff);
            }
        }
        return result;
    }
    
    public SVNStatusType filePropertiesChanged(String path, Map baseProps, Map propDiff) throws SVNException {
        return propertiesChanged(PathUtil.removeTail(path), PathUtil.tail(path), baseProps, propDiff);
    }

    public SVNStatusType directoryPropertiesChanged(String path, Map baseProps, Map propDiff) throws SVNException {
        return propertiesChanged(path, "", baseProps, propDiff);
    }

    private SVNStatusType propertiesChanged(String path, String name, Map baseProps, Map propDiff) throws SVNException {
        if (propDiff == null || propDiff.isEmpty()) {
            return SVNStatusType.UNCHANGED;
        }
        SVNDirectory dir = myWCAccess.getDirectory(path);
        if (dir == null) {
            return SVNStatusType.MISSING;
        }
        SVNStatusType result = null;
        SVNLog log = null;
        if (!myIsDryRun) {
            log = dir.getLog(0);
        }
        result = dir.mergeProperties(name, propDiff, baseProps, log);
        if (log != null) {
            dir.runLogs();
        } 
        return result;
    }    
    
    private void addDirectory(SVNDirectory parentDir, String name, String copyFromURL, long copyFromRev) throws SVNException {
        // 1. update or create entry in parent
        SVNEntries entries = parentDir.getEntries();
        SVNEntry entry = entries.getEntry(name);
        String url = null;
        String uuid = entries.getEntry("").getUUID();
        if (entry != null) {
            if (entry.isScheduledForDeletion()) {
                entry.scheduleForReplacement();
            }
            url = entry.getURL();
        } else {
            entry = parentDir.getEntries().addEntry(name);
            entry.setKind(SVNNodeKind.DIR);
            entry.scheduleForAddition();
            url = PathUtil.append(entries.getEntry("").getURL(), PathUtil.encode(name));
        }
        entry.setCopied(true);
        entry.setCopyFromURL(copyFromURL);
        entry.setCopyFromRevision(copyFromRev);
        // 2. create dir if doesn't exists and update its root entry.
        entries.save(false);
        SVNDirectory childDir = parentDir.getChildDirectory(name);
        if (childDir == null) {
            childDir = parentDir.createChildDirectory(name, url, copyFromRev);
            SVNEntry root = childDir.getEntries().getEntry("");
            root.scheduleForAddition();
            root.setUUID(uuid);
        } else {
            childDir.getWCProperties("").delete();
            SVNEntry root = childDir.getEntries().getEntry("");
            if (root.isScheduledForDeletion()) {
                root.scheduleForReplacement();
            }
        }
        entries = childDir.getEntries();
        SVNEntry rootEntry = entries.getEntry("");
        rootEntry.setCopyFromURL(copyFromURL);
        rootEntry.setCopyFromRevision(copyFromRev);
        rootEntry.setCopied(true);
        entries.save(false);
    }

    private void addFile(SVNDirectory parentDir, String name, String filePath, Map baseProps, String copyFromURL, long copyFromRev) throws SVNException {
        SVNEntries entries = parentDir.getEntries();
        SVNEntry entry = entries.getEntry(name);
        if (entry != null) {
            if (entry.isScheduledForDeletion()) {
                entry.scheduleForReplacement();
            }
        } else {
            entry = parentDir.getEntries().addEntry(name);
            entry.setKind(SVNNodeKind.FILE);
            entry.scheduleForAddition();
        }
        entry.setCopied(true);
        entry.setCopyFromURL(copyFromURL);
        entry.setCopyFromRevision(copyFromRev);
        entries.save(false);
        // TODO install props, prop-base, then install text-base and finally wc file.
        // this should be done with the help of directory log.
        parentDir.getWCProperties(name).delete();
    }
    
    private SVNDirectory getParentDirectory(String path) {
        path = PathUtil.removeTail(path);
        path = PathUtil.removeLeadingSlash(path);
        return myWCAccess.getDirectory(path);
    }
}