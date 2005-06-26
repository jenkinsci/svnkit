/*
 * Created on 31.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

public class SVNMerger {

    private boolean myIsDryRun;
    private SVNWCAccess myWCAccess;
    private boolean myIsForce;
    
    private String myAddedPath;
    private String myURL;
    private long myTargetRevision;

    public SVNMerger(SVNWCAccess wcAccess, String url, long rev, boolean force, boolean dryRun) {
        myWCAccess = wcAccess;
        myIsDryRun = dryRun;
        myIsForce = force;
        myTargetRevision = rev;
        myURL = url;
    }
    
    public boolean isDryRun() {
        return myIsDryRun;
    }

    public SVNStatusType directoryDeleted(final String path) throws SVNException {
        SVNDirectory parentDir = getParentDirectory(path);
        if (parentDir == null) {
            return SVNStatusType.MISSING;
        }
        String name = PathUtil.tail(path);
        File targetFile = parentDir.getFile(name, false);
        DebugLog.log("target file for deletion: " + targetFile);
        if (targetFile.isDirectory()) {
            // check for normal entry?
            final ISVNEventHandler oldDispatcher = myWCAccess.getEventDispatcher();
            myWCAccess.setEventDispatcher(new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) {
                    String eventPath = event.getPath();
                    eventPath = eventPath.replace(File.separatorChar, '/');
                    if (event.getPath().equals(path)) {
                        return;
                    }
                    if (oldDispatcher != null) {
                        oldDispatcher.handleEvent(event, progress);
                    }
                }
                public void checkCancelled() throws SVNCancelException {
                    if (oldDispatcher != null) {
                        oldDispatcher.checkCancelled();
                    }
                }
            });
            try {
                if (!myIsForce) {
                    try {
                        parentDir.canScheduleForDeletion(name);
                    } catch (SVNException e) {
                        DebugLog.log("can't schedule for deletion: " + targetFile);
                        DebugLog.error(e);
                        return SVNStatusType.OBSTRUCTED;
                    }
                }
                if (!myIsDryRun) {
                    try {
                        parentDir.scheduleForDeletion(name);
                    } catch (SVNException e) {
                        return SVNStatusType.OBSTRUCTED;
                    }
                }
            } finally {
                myWCAccess.setEventDispatcher(oldDispatcher);
            }
            return SVNStatusType.CHANGED;
        } else if (targetFile.isFile()) {
            return SVNStatusType.OBSTRUCTED;
        } 
        return SVNStatusType.MISSING;
    }

    public SVNStatusType fileDeleted(String path) throws SVNException {
        SVNDirectory parentDir = getParentDirectory(path);
        if (parentDir == null) {
            return SVNStatusType.MISSING;
        }
        String name = PathUtil.tail(path);
        File targetFile = parentDir.getFile(name, false);
        if (targetFile.isDirectory()) {
            return SVNStatusType.OBSTRUCTED;
        } else if (targetFile.isFile()) {
            ISVNEventHandler oldDispatcher = myWCAccess.getEventDispatcher();
            try {
                myWCAccess.setEventDispatcher(null);
                if (!myIsForce) {
                    try {
                        parentDir.canScheduleForDeletion(name);
                    } catch (SVNException e) {
                        return SVNStatusType.OBSTRUCTED;
                    }
                }
                if (!myIsDryRun) {
                    try {
                        parentDir.scheduleForDeletion(name);
                    } catch (SVNException e) {
                        return SVNStatusType.OBSTRUCTED;
                    }
                }
            } finally {
                myWCAccess.setEventDispatcher(oldDispatcher);
            }
            return SVNStatusType.CHANGED;
        } 
        return SVNStatusType.MISSING;
    }
    
    public SVNStatusType directoryAdded(String path, Map entryProps, long revision) throws SVNException {
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
            SVNEntry entry = parentDir.getEntries().getEntry(name, true);
            if (entry != null && !entry.isScheduledForDeletion()) {
                // missing entry.
                return SVNStatusType.OBSTRUCTED;
            }
            if (!myIsDryRun) {
                file.mkdirs();
                String url = PathUtil.append(myURL, PathUtil.encode(getPathInURL(path)));
                addDirectory(parentDir, name, url, myTargetRevision, entryProps);
            } else {
                myAddedPath = path + "/";
            }
            return SVNStatusType.CHANGED;
        } else if (file.isDirectory()) {
            SVNEntry entry = parentDir.getEntries().getEntry(name, true);
            if (entry == null || entry.isScheduledForDeletion()) {
                if (myIsDryRun) {
                    myAddedPath = path + "/";
                } else {
                    String url = PathUtil.append(myURL, PathUtil.encode(getPathInURL(path)));
                    addDirectory(parentDir, name, url, myTargetRevision, entryProps);
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
        return SVNStatusType.MISSING;
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
        SVNEntry entry = parentDir.getEntries().getEntry(name, true);
        
        if (!mine.isFile() || entry == null || entry.isHidden()) {
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
                    boolean equals = SVNFileUtil.compareFiles(mine, older, null);
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
                DebugLog.log("merging: " + name + " in dir: " + parentDir.getPath());
                mergeResult = parentDir.mergeText(minePath, olderPath, yoursPath, targetLabel, leftLabel, rightLabel, myIsDryRun);
                parentDir.getEntries().save(true);
            }
            
            if (mergeResult == SVNStatusType.CONFLICTED) {
                result[0] = SVNStatusType.CONFLICTED;
            } else if (isTextModified) {
                result[0] = SVNStatusType.MERGED;
            } else if (mergeResult == SVNStatusType.MERGED) {
                result[0] = SVNStatusType.CHANGED;
            } else {
                result[0] = SVNStatusType.UNCHANGED;
            }
        }
        return result;
    }
    
    public SVNStatusType[] fileAdded(String path, File older, File yours, long rev1, long rev2,
            String mimeType1, String mimeType2,
            Map baseProps, Map propDiff, Map entryProps) throws SVNException {
        SVNStatusType[] result = new SVNStatusType[] {SVNStatusType.UNKNOWN, SVNStatusType.UNKNOWN};
        SVNDirectory parentDir = getParentDirectory(path);
        if (parentDir == null) {
            DebugLog.log("parent dir is null for: " + path);
            DebugLog.log("added path: " + myAddedPath);
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
            SVNEntry entry = parentDir.getEntries().getEntry(name, true);
            if (entry != null && !entry.isScheduledForDeletion()) {
                result[0] = SVNStatusType.OBSTRUCTED;
                return result;
            } else if (!myIsDryRun) {
                String pathInURL = getPathInURL(path);
                String copyFromURL = PathUtil.append(myURL, PathUtil.encode(pathInURL));
                addFile(parentDir, name, SVNFileUtil.getBasePath(yours), propDiff, copyFromURL, myTargetRevision, entryProps);
            }
            result[0] = SVNStatusType.CHANGED;
            if (propDiff != null && !propDiff.isEmpty()) {
                result[1] = SVNStatusType.CHANGED;
            }            
        } else if (mine.isDirectory()) {
            result[0] = SVNStatusType.OBSTRUCTED;
        } else if (mine.isFile()) {
            SVNEntry entry = parentDir.getEntries().getEntry(name, true);
            if (entry == null || entry.isScheduledForDeletion()) {
                result[0] = SVNStatusType.OBSTRUCTED;
            } else {
                return fileChanged(path, older, yours, rev1, rev2, mimeType1, mimeType2, baseProps, propDiff);
            }
        }
        return result;
    }

    private String getPathInURL(String path) {
        String pathInURL = path;
        if (!"".equals(myWCAccess.getTargetName())) {
            if (pathInURL.indexOf('/') > 0) {
                pathInURL = pathInURL.substring(pathInURL.indexOf('/') + 1);
            } else {
                pathInURL = "";
            }
        }
        pathInURL = PathUtil.removeLeadingSlash(pathInURL);
        pathInURL = PathUtil.removeTrailingSlash(pathInURL);
        return pathInURL;
    }

    public SVNStatusType directoryPropertiesChanged(String path, Map baseProps, Map propDiff) throws SVNException {
        return propertiesChanged(path, "", baseProps, propDiff);
    }
    
    public File getFile(String path, boolean base) {
        SVNDirectory dir = null; 
        DebugLog.log("fetching tmp file, added path: " + myAddedPath);
        //if (myIsDryRun) {            
            String parentPath = path;
            while(dir == null && !PathUtil.isEmpty(parentPath)) {
                dir = getParentDirectory(parentPath);
                parentPath = PathUtil.removeTail(parentPath);
            }
//        } else {
//            dir = getParentDirectory(path);
//        }
        String name = PathUtil.tail(path);
        if (dir != null) {
            String extension = base ? ".tmp-base" : ".tmp-work";
            return SVNFileUtil.createUniqueFile(dir.getFile(".svn/tmp/text-base", false), name , extension);
        }
        return null;
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
        // 1. convert props to diff (need we?), just use remote diff 
        // -> 
        // 2. get local mods.
        DebugLog.log("entry name: " + name);
        SVNProperties localBaseProps = dir.getBaseProperties(name, false);
        SVNProperties localWCProps = dir.getProperties(name, false);
        
        // will contain all deleted and added, but not unchanged.
        Map wcProps = localWCProps.asMap();
        Map localDiff = localBaseProps.compareTo(localWCProps);
        // now add all non-null from wc to localDiff.
        DebugLog.log("all wc props: " + wcProps);
        /*
        for(Iterator wcPropsNames = wcProps.keySet().iterator(); wcPropsNames.hasNext();) {
            String wcPropName = (String) wcPropsNames.next();
            DebugLog.log("wc prop: " + wcPropName);
            if (!localDiff.containsKey(wcPropName)) {
                DebugLog.log("not modified: " + wcProps.get(wcPropName));
                localDiff.put(wcPropName, wcProps.get(wcPropName));
            }
        }*/
        // 3. merge
        DebugLog.log("merging props, remote diff:" + propDiff);
        DebugLog.log("merging props, local diff:" + localDiff);
        result = dir.mergeProperties(name, propDiff, localDiff, false, log);
        DebugLog.log("running log: " + log);
        if (log != null) {
            log.save();
            dir.runLogs();
        } 
        // to make python tests pass.
        if (result == SVNStatusType.MERGED || result == SVNStatusType.CONFLICTED) {
            result = SVNStatusType.CHANGED;
        }
        return result;
    }    
    
    private void addDirectory(SVNDirectory parentDir, String name, String copyFromURL, long copyFromRev, Map entryProps) throws SVNException {
        // 1. update or create entry in parent
        SVNEntries entries = parentDir.getEntries();
        SVNEntry entry = entries.getEntry(name, true);
        String url = null;
        String uuid = entries.getEntry("", true).getUUID();
        if (entry != null) {
            entry.loadProperties(entryProps);
            if (entry.isScheduledForDeletion()) {
                entry.scheduleForReplacement();
            }
            url = entry.getURL();
        } else {
            entry = parentDir.getEntries().addEntry(name);
            entry.loadProperties(entryProps);
            entry.setKind(SVNNodeKind.DIR);
            entry.scheduleForAddition();
            url = PathUtil.append(entries.getEntry("", true).getURL(), PathUtil.encode(name));
        }
        entry.setCopied(true);
        entry.setCopyFromURL(copyFromURL);
        entry.setCopyFromRevision(copyFromRev);
        // 2. create dir if doesn't exists and update its root entry.
        entries.save(false);
        SVNDirectory childDir = parentDir.getChildDirectory(name);
        if (childDir == null) {
            childDir = parentDir.createChildDirectory(name, url, copyFromRev);
            SVNEntry root = childDir.getEntries().getEntry("", true);
            root.scheduleForAddition();
            root.setUUID(uuid);
        } else {
            childDir.getWCProperties("").delete();
            SVNEntry root = childDir.getEntries().getEntry("", true);
            if (root.isScheduledForDeletion()) {
                root.scheduleForReplacement();
            }
        }
        entries = childDir.getEntries();
        SVNEntry rootEntry = entries.getEntry("", true);
        rootEntry.setCopyFromURL(copyFromURL);
        rootEntry.setCopyFromRevision(copyFromRev);
        rootEntry.setCopied(true);
        entries.save(false);
    }

    private void addFile(SVNDirectory parentDir, String name, String filePath, Map baseProps, String copyFromURL, long copyFromRev,
            Map entryProps) throws SVNException {
        SVNEntries entries = parentDir.getEntries();
        SVNEntry entry = entries.getEntry(name, true);
        if (entry != null) {
            if (entry.isScheduledForDeletion()) {
                entry.scheduleForReplacement();
            }
            // put all entry props.
            entry.loadProperties(entryProps);
        } else {
            entry = parentDir.getEntries().addEntry(name);
            entry.loadProperties(entryProps);
            entry.setKind(SVNNodeKind.FILE);
            entry.scheduleForAddition();
        }
        entry.setCopied(true);
        entry.setCopyFromURL(copyFromURL);
        entry.setCopyFromRevision(copyFromRev);
        String url = PathUtil.append(entries.getEntry("", true).getURL(), PathUtil.encode(name));
        entries.save(false);
        parentDir.getWCProperties(name).delete();
        
        SVNLog log = parentDir.getLog(0);
        Map command = new HashMap();

        // 1. props.
        SVNProperties wcPropsFile = parentDir.getProperties(name, false);
        SVNProperties basePropsFile = parentDir.getBaseProperties(name, false);
        for (Iterator propNames = baseProps.keySet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            wcPropsFile.setPropertyValue(propName, (String) baseProps.get(propName)); 
            basePropsFile.setPropertyValue(propName, (String) baseProps.get(propName)); 
        }
        if (baseProps.isEmpty()) {
            // force prop file creation.
            wcPropsFile.setPropertyValue("x", "x");
            basePropsFile.setPropertyValue("x", "x");
            wcPropsFile.setPropertyValue("x", null);
            basePropsFile.setPropertyValue("x", null);
        }
        command.put(SVNLog.NAME_ATTR, wcPropsFile.getPath());
        log.addCommand(SVNLog.READONLY, command, false);
        command.put(SVNLog.NAME_ATTR, basePropsFile.getPath());
        log.addCommand(SVNLog.READONLY, command, false);
        
        // 2. entry
        command.put(SVNLog.NAME_ATTR, name);
        command.put(SVNProperty.shortPropertyName(SVNProperty.KIND), SVNProperty.KIND_FILE);
        command.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), Long.toString(copyFromRev));
        command.put(SVNProperty.shortPropertyName(SVNProperty.DELETED), Boolean.FALSE.toString());
        command.put(SVNProperty.shortPropertyName(SVNProperty.ABSENT), Boolean.FALSE.toString());
        command.put(SVNProperty.shortPropertyName(SVNProperty.URL), url);
        command.put(SVNProperty.shortPropertyName(SVNProperty.COPIED), Boolean.TRUE.toString());
        command.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_URL), copyFromURL);
        command.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_REVISION), Long.toString(copyFromRev));
        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        command.clear();

        // 3. text files.
        String basePath = SVNFileUtil.getBasePath(parentDir.getBaseFile(name, false));
        command.put(SVNLog.NAME_ATTR, filePath);
        command.put(SVNLog.DEST_ATTR, basePath);
        log.addCommand(SVNLog.MOVE, command, false);
        command.clear();
        command.put(SVNLog.NAME_ATTR, basePath);
        log.addCommand(SVNLog.READONLY, command, false);
        command.clear();
        command.put(SVNLog.NAME_ATTR, basePath);
        command.put(SVNLog.DEST_ATTR, name);
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();
        if (myWCAccess.getOptions().isUseCommitTimes() && wcPropsFile.getPropertyValue(SVNProperty.SPECIAL) == null) {
            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNLog.TIMESTAMP_ATTR, entry.getCommittedDate());
            log.addCommand(SVNLog.SET_TIMESTAMP, command, false);
            command.clear();
        }

        command.put(SVNLog.NAME_ATTR, name);
        command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_TIME), SVNLog.WC_TIMESTAMP);
        command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), SVNLog.WC_TIMESTAMP);
        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);

        log.save();
        parentDir.runLogs();
    }
    
    private SVNDirectory getParentDirectory(String path) {
        path = PathUtil.removeTail(path);
        path = PathUtil.removeLeadingSlash(path);
        return myWCAccess.getDirectory(path);
    }
}