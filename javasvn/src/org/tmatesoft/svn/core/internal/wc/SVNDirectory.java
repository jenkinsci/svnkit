package org.tmatesoft.svn.core.internal.wc;

import de.regnis.q.sequence.line.QSequenceLineRAData;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.ws.fs.FSMergerBySequence;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SVNDirectory {
    
    private File myDirectory;
    private SVNEntries myEntries;
    
    private SVNWCAccess myWCAccess;
    private String myPath;

    public SVNDirectory(SVNWCAccess wcAccess, String path, File dir) {
        myDirectory = dir;
        myPath = path;
        myWCAccess = wcAccess;
    }

    public SVNWCAccess getWCAccess() {
        return myWCAccess;
    }
    
    public String getPath() {
        return myPath;
    }

    public SVNDirectory[] getChildDirectories() {
        return myWCAccess.getChildDirectories(myPath);
    }

    public SVNDirectory getChildDirectory(String name) {
        return myWCAccess.getDirectory("".equals(myPath) ? name : PathUtil.append(myPath, name));
    }
    
    public boolean isVersioned() {
        return getAdminDirectory().isDirectory();
    }
    
    public boolean isLocked() {
        return getLockFile().isFile();
    }
    
    public boolean lock() throws SVNException {
        if (!isVersioned()) {
            return false;
        }
        boolean created = false;
        try {
            created = getLockFile().createNewFile();
        } catch (IOException e) {
            SVNErrorManager.error("svn: Cannot lock working copy '" + getRoot() + "': " + e.getMessage());
        }
        if (!created) {
            if (getLockFile().isFile()) {
                SVNErrorManager.error("svn: Working copy '" + getRoot() + "' locked");
            } else {
                SVNErrorManager.error("svn: Cannot lock working copy '" + getRoot() + "'");
            }
        }
        return created;
    }

    public boolean unlock() throws SVNException {
        if (!getLockFile().exists()) {
            return true;
        }
        boolean deleted = getLockFile().delete();
        if (!deleted) {
            SVNErrorManager.error(1, null);
        }
        return deleted;
    }
    
    public SVNEntries getEntries() throws SVNException {
        if (myEntries == null) {
            myEntries = new SVNEntries(new File(getAdminDirectory(), "entries"));
        }
        myEntries.open();
        return myEntries;
    }

    public SVNProperties getProperties(String name, boolean tmp) {
        String path = !tmp ? ".svn/" : ".svn/tmp/";
        path += "".equals(name) ? "dir-props" : "props/" + name + ".svn-work"; 
        File propertiesFile = new File(getRoot(), path); 
        return new SVNProperties(propertiesFile, path);
    }

    public SVNProperties getBaseProperties(String name, boolean tmp) {
        String path = !tmp ? ".svn/" : ".svn/tmp/";
        path += "".equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base"; 
        File propertiesFile = new File(getRoot(), path); 
        return new SVNProperties(propertiesFile, path);
    }
    
    public SVNProperties getWCProperties(String name) {
        String path = "".equals(name) ? ".svn/dir-wcprops" : ".svn/wcprops/" + name + ".svn-work"; 
        File propertiesFile = new File(getRoot(), path); 
        return new SVNProperties(propertiesFile, path);
    }
    
    public SVNStatusType mergeProperties(String name, Map changedProperties, Map locallyChanged, boolean updateBaseProps, SVNLog log) throws SVNException {
        changedProperties = changedProperties == null ? Collections.EMPTY_MAP : changedProperties;
        locallyChanged = locallyChanged == null ? Collections.EMPTY_MAP : locallyChanged;

        SVNProperties working = getProperties(name, false);
        SVNProperties workingTmp = getProperties(name, true);
        SVNProperties base = getBaseProperties(name, false);
        SVNProperties baseTmp = getBaseProperties(name, true);

        working.copyTo(workingTmp);
        if (updateBaseProps) {
            base.copyTo(baseTmp);
        }
        
        Collection conflicts = new ArrayList();
        SVNStatusType result = changedProperties.isEmpty() ? SVNStatusType.UNCHANGED : SVNStatusType.CHANGED;
        for (Iterator propNames = changedProperties.keySet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            String propValue = (String) changedProperties.get(propName);
            DebugLog.log("merging prop change: " + propName + "=" + propValue);
            if (updateBaseProps) {
                baseTmp.setPropertyValue(propName, propValue);
            }
            
            if (locallyChanged.containsKey(propName)) {
                String workingValue = (String) locallyChanged.get(propName);
                String conflict = null;
                //if (workingValue != null) {
                    if (workingValue == null && propValue != null) {
                        conflict = MessageFormat.format("Property ''{0}'' locally deleted, but update sets it to ''{1}''\n", 
                                new Object[] {propName, propValue});
                    } else if (workingValue != null && propValue == null) {
                        conflict = MessageFormat.format("Property ''{0}'' locally changed to ''{1}'', but update deletes it\n", 
                                new Object[] {propName, workingValue});
                    } else if (workingValue != null && !workingValue.equals(propValue)) {
                        conflict = MessageFormat.format("Property ''{0}'' locally changed to ''{1}'', but update sets it to ''{2}''\n", 
                                new Object[] {propName, workingValue, propValue});
                    }
                    if (conflict != null) {          
                        conflicts.add(conflict);
                        continue;
                    }
                    result = SVNStatusType.MERGED;
                //}
            }
            DebugLog.log("setting tmp wc value: " + propName + "=" + propValue);
            workingTmp.setPropertyValue(propName, propValue);
        }        
        // now log all.
        Map command = new HashMap();
        if (log != null) {
            command.put(SVNLog.NAME_ATTR, workingTmp.getPath());
            command.put(SVNLog.DEST_ATTR, working.getPath());
            log.addCommand(SVNLog.MOVE, command, false);
            command.clear();
            command.put(SVNLog.NAME_ATTR, working.getPath());
            log.addCommand(SVNLog.READONLY, command, false);
    
            if (updateBaseProps) {
                command.put(SVNLog.NAME_ATTR, baseTmp.getPath());
                command.put(SVNLog.DEST_ATTR, base.getPath());
                log.addCommand(SVNLog.MOVE, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, base.getPath());
                log.addCommand(SVNLog.READONLY, command, false);
            }
        }

        if (!conflicts.isEmpty()) {
            result = SVNStatusType.CONFLICTED;

            String prejTmpPath = "".equals(name) ? ".svn/tmp/dir_conflicts" : ".svn/tmp/props/" + name;
            File prejTmpFile = SVNFileUtil.createUniqueFile(getRoot(), prejTmpPath, ".prej");
            prejTmpPath = SVNFileUtil.getBasePath(prejTmpFile);
                
            String prejPath = getEntries().getEntry(name, true).getPropRejectFile();
            getEntries().close();

            if (prejPath == null) {
                prejPath = "".equals(name) ? "dir_conflicts" : name;
                File prejFile = SVNFileUtil.createUniqueFile(getRoot(), prejPath, ".prej");
                prejPath = SVNFileUtil.getBasePath(prejFile);
            }
            File file = getFile(prejTmpPath, false);
            OutputStream os = SVNFileUtil.openFileForWriting(file);
            try {
                for (Iterator lines = conflicts.iterator(); lines.hasNext();) {
                    String line = (String) lines.next();
                    os.write(line.getBytes("UTF-8"));
                }
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            } finally {
                SVNFileUtil.closeFile(os);
            }
            if (log != null) {
                command.put(SVNLog.NAME_ATTR, prejTmpPath);
                command.put(SVNLog.DEST_ATTR, prejPath);
                log.addCommand(SVNLog.APPEND, command, false);
                command.clear();
                command.put(SVNLog.NAME_ATTR, prejTmpPath);
                log.addCommand(SVNLog.DELETE, command, false);
                command.clear();
    
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_REJECT_FILE), prejPath);
                log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            }
        }
        if (log == null) {
            workingTmp.delete();
            baseTmp.delete();
        }

        return result;
    }
    
    public SVNStatusType mergeText(String localPath, String basePath, String latestPath, String localLabel, String baseLabel, String latestLabel, boolean dryRun) throws SVNException {
        String mimeType = getProperties(localPath, false).getPropertyValue(SVNProperty.MIME_TYPE);
        SVNEntry entry = getEntries().getEntry(localPath, true);
        if (mimeType != null && !mimeType.startsWith("text/")) {
            // binary
            if (!dryRun) {
                File oldFile = SVNFileUtil.createUniqueFile(getRoot(), localPath, baseLabel); 
                File newFile = SVNFileUtil.createUniqueFile(getRoot(), localPath, latestLabel);
                SVNFileUtil.copyFile(getFile(basePath, false), oldFile, false);
                SVNFileUtil.copyFile(getFile(latestPath, false), newFile, false);
                // update entry props
                entry.setConflictNew(SVNFileUtil.getBasePath(newFile));
                entry.setConflictOld(SVNFileUtil.getBasePath(oldFile));
                entry.setConflictWorking(null);                
            }
            return SVNStatusType.CONFLICTED;
        }
        // text
        // 1. destranslate local
        File localTmpFile = SVNFileUtil.createUniqueFile(getRoot(), localPath, ".tmp");
        SVNTranslator.translate(this, localPath, localPath, SVNFileUtil.getBasePath(localTmpFile), false, false);
        // 2. run merge between all files we have :)
        RandomAccessFile localIS = null;
        RandomAccessFile latestIS = null;
        RandomAccessFile baseIS = null;
        OutputStream result = null;
        File resultFile = dryRun ? null : SVNFileUtil.createUniqueFile(getRoot(), localPath, ".result");
        
        byte[] conflictStart = ("<<<<<<< " + localLabel).getBytes(); 
        byte[] conflictEnd = (">>>>>>> " + latestLabel).getBytes();
        byte[] separator = ("=======").getBytes();
        FSMergerBySequence merger = new FSMergerBySequence(conflictStart, separator, conflictEnd, null);
        int mergeResult = 0;
        try {
            localIS = new RandomAccessFile(localTmpFile, "r");
            latestIS = new RandomAccessFile(getFile(latestPath, false), "r");
            baseIS = new RandomAccessFile(getFile(basePath, false), "r");
            OutputStream dummy = new OutputStream() {
                public void write(int b) {}
            };
            result = resultFile == null ?  dummy : new FileOutputStream(resultFile);
            
            QSequenceLineRAData baseData = new QSequenceLineRAFileData(baseIS);
            QSequenceLineRAData localData = new QSequenceLineRAFileData(localIS);
            QSequenceLineRAData latestData = new QSequenceLineRAFileData(latestIS);
            debugQData("base  :\n", baseData);
            debugQData("local :\n", localData);
            debugQData("latest:\n", latestData);
            mergeResult = merger.merge(baseData, localData, latestData, result);
            DebugLog.log("merge result: " + mergeResult);
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            try {
                if (result != null) {
                    result.close();
                }
                localIS.close();
                baseIS.close();
                latestIS.close();
            } catch (IOException e) {
                //
            }
        }
        SVNStatusType status = SVNStatusType.UNCHANGED;
        if (mergeResult == 2) {
            status = SVNStatusType.CONFLICTED;
        } else if (mergeResult == 4) {
            status = SVNStatusType.MERGED;
        }
        if (dryRun) {
            localTmpFile.delete();
            return status;
        }
        if (status != SVNStatusType.CONFLICTED) {
            SVNTranslator.translate(this, localPath, SVNFileUtil.getBasePath(resultFile), localPath, true, true);
        } else {
            // copy all to wc.
            File mineFile = SVNFileUtil.createUniqueFile(getRoot(), localPath, localLabel);
            String minePath = SVNFileUtil.getBasePath(mineFile);
            SVNFileUtil.copyFile(getFile(localPath, false), mineFile, false);
            File oldFile = SVNFileUtil.createUniqueFile(getRoot(), localPath, baseLabel);
            String oldPath = SVNFileUtil.getBasePath(oldFile);
            File newFile = SVNFileUtil.createUniqueFile(getRoot(), localPath, latestLabel);
            String newPath = SVNFileUtil.getBasePath(newFile);
            SVNTranslator.translate(this, localPath, basePath, oldPath, true, false);
            SVNTranslator.translate(this, localPath, latestPath, newPath, true, false);
            // translate result to local
            SVNTranslator.translate(this, localPath, SVNFileUtil.getBasePath(resultFile), localPath, true, true);

            entry.setConflictNew(newPath);
            entry.setConflictOld(oldPath);
            entry.setConflictWorking(minePath);                
        }
        localTmpFile.delete();
        if (resultFile != null) {
            resultFile.delete();
        }
        return status;
    }
    
    public boolean markResolved(String name, boolean text, boolean props) throws SVNException {
        if (!text && !props) {
            return false;
        }
        SVNEntry entry = getEntries().getEntry(name, true);
        if (entry == null) {
            return false;
        }
        boolean modified = false;
        if (text && entry.getConflictOld() != null) {
            modified = true;
            File file = new File(myDirectory, entry.getConflictOld());
            file.delete();
            entry.setConflictOld(null);
        }
        if (text && entry.getConflictNew() != null) {
            modified = true;
            File file = new File(myDirectory, entry.getConflictNew());
            file.delete();
            entry.setConflictNew(null);
        }
        if (text && entry.getConflictWorking() != null) {
            modified = true;
            File file = new File(myDirectory, entry.getConflictWorking());
            file.delete();
            entry.setConflictWorking(null);
        }
        if (props && entry.getPropRejectFile() != null) {
            File file = new File(myDirectory, entry.getPropRejectFile());
            file.delete();
            modified = true;
            entry.setPropRejectFile(null);
        }
        if (modified) {
            getEntries().save(false);
        }
        return modified;
    }
    
    public boolean revert(String name) throws SVNException {
        DebugLog.log("reverting: " + name + " in " + getRoot());
        boolean magicPropsChanged = false;
        boolean wasReverted = false;
        
        SVNEntry entry = getEntries().getEntry(name, true);
        if (entry == null || entry.isHidden()) {
            return wasReverted;
        }
        
        SVNProperties baseProps = getBaseProperties(name, false);
        SVNProperties wcProps = getProperties(name, false);
        if (hasPropModifications(name)) {
            DebugLog.log("props modified");
            Map propDiff = baseProps.compareTo(wcProps);
            if (propDiff != null && !propDiff.isEmpty()) {
                magicPropsChanged = propDiff.containsKey(SVNProperty.EXECUTABLE) ||
                    propDiff.containsKey(SVNProperty.KEYWORDS) ||
                    propDiff.containsKey(SVNProperty.NEEDS_LOCK) ||
                    propDiff.containsKey(SVNProperty.EOL_STYLE) ||
                    propDiff.containsKey(SVNProperty.SPECIAL);                
            }
            // copy base over wc
            if (baseProps.getFile().isFile()) {
                baseProps.copyTo(wcProps);
                entry.setPropTime(TimeUtil.formatDate(new Date(wcProps.getFile().lastModified())));
            } else if (wcProps.getFile().isHidden()) {
                wcProps.delete();
                entry.setPropTime(null);
            }
            getEntries().save(false);            
            wasReverted = true;
        } else if (entry.isScheduledForReplacement()) {
            baseProps.copyTo(wcProps);
            entry.setPropTime(TimeUtil.formatDate(new Date(wcProps.getFile().lastModified())));
            wasReverted = true;
        }
        DebugLog.log("m.props changed: " + magicPropsChanged);
        
        if (entry.isFile()) {
            boolean textModified = false;
            if (!magicPropsChanged) {
                textModified = hasTextModifications(name, false);
            }
            File file = getFile(entry.getName(), false);
            if (textModified || magicPropsChanged || !file.exists()) {
                // copy base to wc and expand.
                boolean special = wcProps.getPropertyValue(SVNProperty.SPECIAL) != null;
                
                File src = getBaseFile(name, false);
                File dst = getFile(name, false);
                if (!src.exists()) {
                    SVNErrorManager.error("svn: Error restoring text for '" + dst + "'");
                }
                SVNTranslator.translate(this, name, SVNFileUtil.getBasePath(src), SVNFileUtil.getBasePath(dst), true, true);
                
                boolean executable = wcProps.getPropertyValue(SVNProperty.EXECUTABLE) != null;
                boolean needsLock = wcProps.getPropertyValue(SVNProperty.NEEDS_LOCK) != null;
                if (executable) {
                    SVNFileUtil.setExecutable(dst, true);
                }
                if (needsLock) {
                    SVNFileUtil.setReadonly(dst, entry.getLockToken() == null);
                }
                long tstamp = dst.lastModified();
                if (myWCAccess.getOptions().isUseCommitTimes() && !special) {
                    entry.setTextTime(entry.getCommittedDate());
                    tstamp = TimeUtil.parseDate(entry.getCommittedDate()).getTime();
                    dst.setLastModified(tstamp);
                } else {
                    entry.setTextTime(TimeUtil.formatDate(new Date(tstamp)));
                }
                getEntries().save(false);
                wasReverted |= true;
            }
            wasReverted |= markResolved(name, true, false);
        }
        return wasReverted;
    }
    
    public boolean hasTextModifications(String name, boolean force) throws SVNException {
        SVNFileType fType = SVNFileType.getType(getFile(name, false));
        if (fType == SVNFileType.DIRECTORY || fType == SVNFileType.NONE) {
            return false;
        }
        SVNEntries entries = getEntries();
        if (entries == null || entries.getEntry(name, true) == null) {
            return false;
        }
        SVNEntry entry = entries.getEntry(name, true);
        if (entry.isDirectory()) {
            return false;
        }
        if (!force) {
            String textTime = entry.getTextTime();
            long textTimeAsLong = SVNFileUtil.roundTimeStamp(TimeUtil.parseDate(textTime).getTime());
            long tstamp = SVNFileUtil.roundTimeStamp(getFile(name, false).lastModified());
            if (textTimeAsLong == tstamp ) {
                return false;
            }
        } 
        File baseFile = getBaseFile(name, false);
        if (!baseFile.isFile()) {
            return true;
        }
        // translate versioned file.
        File baseTmpFile = SVNFileUtil.createUniqueFile(getRoot(), SVNFileUtil.getBasePath(getBaseFile(name, true)), ".tmp");
        if (!baseTmpFile.getParentFile().exists()) {
            baseTmpFile.getParentFile().mkdirs();
        }
        File versionedFile = getFile(name, false);
        SVNTranslator.translate(this, name, name, SVNFileUtil.getBasePath(baseTmpFile), false, false);
        
        // now compare file and get base file checksum (when forced)
        MessageDigest digest;
        boolean equals = true;
        try {
            digest = force ? MessageDigest.getInstance("MD5") : null;
            equals = SVNFileUtil.compareFiles(baseFile, baseTmpFile, digest);
            if (force) {
                // if checksum differs from expected - throw exception
                String checksum = SVNFileUtil.toHexDigest(digest);
                if (!checksum.equals(entry.getChecksum())) {
                    SVNErrorManager.error(10, null);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            SVNErrorManager.error(0, e);
        } finally {
            baseTmpFile.delete();
        }
        
        if (equals && isLocked()) {
            entry.setTextTime(TimeUtil.formatDate(new Date(versionedFile.lastModified())));
            entries.save(false);
        }        
        return !equals;
    }

    public boolean hasPropModifications(String name) throws SVNException {
        File propFile;
        File baseFile;
        if ("".equals(name)) {
            propFile = getFile(".svn/dir-props", false);
            baseFile = getFile(".svn/dir-prop-base", false);
        } else {
            propFile = getFile(".svn/props/" + name + ".svn-work", false);
            baseFile = getFile(".svn/prop-base/" + name + ".svn-base", false);
        }
        SVNEntry entry = getEntries().getEntry(name, true);
        boolean propEmtpy = !propFile.exists() || propFile.length() <= 4;
        boolean baseEmtpy = !baseFile.exists() || baseFile.length() <= 4;
        if (entry.isScheduledForReplacement()) {
            return !propEmtpy;
        }
        if (baseEmtpy) {
            return !propEmtpy;
        }
        if (propEmtpy) {
            return true;
        }
        if (propFile.length() != baseFile.length()) {
            return true;
        }
        String timeStamp = entry.getPropTime();
        String realTimestamp = TimeUtil.formatDate(new Date(propFile.lastModified()));
        if (realTimestamp.equals(timeStamp)) {
            return false;
        }
        Map diff = getProperties(name, false).compareTo(getBaseProperties(name, false));
        if (diff == null || diff.isEmpty()) {
            // update tstanp
            if (isLocked()) {
                entry.setPropTime(realTimestamp);
                getEntries().save(false);
            }
            return false;
        }
        return true;
    }
    
    public void cleanup() throws SVNException {
        SVNEntries svnEntries = getEntries();
        for(Iterator entries = svnEntries.entries(true); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (!"".equals(entry.getName()) && entry.isDirectory()) {
                SVNDirectory childDir = getChildDirectory(entry.getName());
                if (childDir != null) {
                    childDir.cleanup();
                }
                continue;
            }
            hasTextModifications(entry.getName(), false);
            hasPropModifications(entry.getName());
        }
        svnEntries.save(true);
        if (new File(getRoot(), ".svn/KILLME").isFile()) {
            destroy("", true);
        } else {
            runLogs();
        }
        File tmpDir = getFile(".svn/tmp", false);
        if (tmpDir.isDirectory()) {
            SVNFileUtil.deleteAll(tmpDir, false);
        }
    }
    
    public void canScheduleForDeletion(String name) throws SVNException {
        // TODO use status call.
        // check if this dir doesn't have obstructed, unversioned or modified entries.
        SVNEntries entries = getEntries();
        File[] files = getRoot().listFiles();
        if (files == null) {
            return;
        }
        DebugLog.log("can schedule for deletion: " + name + " in " + getRoot());
        if ("".equals(name) && hasPropModifications(name)) {
            SVNErrorManager.error("svn: '" + getPath().replace('/', File.separatorChar)+ "' has local modifications");
        }
        for (int i = 0; i < files.length; i++) {
            File childFile = files[i];
            if (".svn".equals(childFile.getName())) {
                continue;
            }
            if (!"".equals(name) && !childFile.getName().equals(name)) {
                continue;
            }
            DebugLog.log("checking file: " + childFile);
            SVNEntry entry = entries.getEntry(childFile.getName(), true);
            DebugLog.log("entry: " + entry);
            String path = PathUtil.append(getPath(), childFile.getName());
            path = path.replace('/', File.separatorChar);
            if (entry == null || entry.isHidden()) {
                // no entry or entry is 'deleted'
                SVNErrorManager.error("svn: '" + path + "' is not under version control");
            } else {
                SVNNodeKind kind = entry.getKind();
                if ((childFile.isFile() && kind == SVNNodeKind.DIR) || 
                        (childFile.isDirectory() && !SVNFileUtil.isSymlink(childFile) && kind == SVNNodeKind.FILE) ||
                        (SVNFileUtil.isSymlink(childFile) && kind != SVNNodeKind.FILE)) {
                    SVNErrorManager.error("svn: '" + path + "' is in the way of the resource actually under version control");
                } else if (kind == SVNNodeKind.FILE) {
                    // chek for mods.
                    DebugLog.log("checking for mods (dir props will be checked later)");
                    if (hasTextModifications(entry.getName(), false) || hasPropModifications(entry.getName())) {
                        SVNErrorManager.error("svn: '" + path + "' has local modifications");
                    }
                }                    
                if (kind == SVNNodeKind.DIR) {
                    SVNDirectory childDir = getChildDirectory(childFile.getName());
                    if (childDir != null) {
                        childDir.canScheduleForDeletion("");
                    }
                }
            }
        }
    }
    
    public void dispose() {
        if (myEntries != null) {
            myEntries.close();
        }
        myEntries = null;
    }

    private File getLockFile() {
        return new File(getAdminDirectory(), "lock");
    }

    private File getAdminDirectory() {
        return new File(myDirectory, ".svn");
    }

    public File getFile(String name, boolean tmp) {
        String path = tmp ? ".svn/tmp/" + name : name;
        return new File(getRoot(), path);
    }

    public File getBaseFile(String name, boolean tmp) {
        String path = tmp ? ".svn/tmp/" : ".svn/";
        path += "text-base/" + name + ".svn-base";
        return new File(getRoot(), path);
    }
    
    public File getRoot() {
        return myDirectory;
    }
    
    public SVNLog getLog(int id) {
        return new SVNLog(this, id); 
    }
    
    public void runLogs() throws SVNException {
        SVNLogRunner runner = new SVNLogRunner();
        int index = 0;
        try {
            while(true) {
                SVNLog log = new SVNLog(this, index);
                index++;
                if (log.exists()) {
                    log.run(runner);
                    continue;
                }
                return;
            }
        } finally {
            runner.logCompleted(this);
        }
    }

    public SVNDirectory createChildDirectory(String name, String url, long revision) throws SVNException {
        File dir = new File(myDirectory, name);
        createVersionedDirectory(dir);
        
        String childPath = PathUtil.append(myPath, name);
        childPath = PathUtil.removeLeadingSlash(childPath);

        SVNDirectory child = myWCAccess.addDirectory(childPath, dir);
        SVNEntry rootEntry = child.getEntries().getEntry("", true);
        if (rootEntry == null) {
            rootEntry = child.getEntries().addEntry("");
        }
        if (url != null) {
            rootEntry.setURL(url);
        }
        rootEntry.setRevision(revision);
        rootEntry.setKind(SVNNodeKind.DIR);
        child.getEntries().save(true);
        return child;
    }

    public static void createVersionedDirectory(File dir) throws SVNException {
        dir.mkdirs();
        File adminDir = new File(dir, ".svn");
        adminDir.mkdirs();
        SVNFileUtil.setHidden(adminDir, true);
        File format = new File(adminDir, "format");
        OutputStream os = null;
        if (!format.exists()) {
            try {
                os = new FileOutputStream(format);
                os.write(new byte[] {'4', '\n'});
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            } finally {
                if (os != null) { 
                    try {
                        os.close();
                    } catch (IOException e) {
                        //
                    }
                }
            }
        }
        File readme = new File(adminDir, "README.txt");
        if (!readme.exists()) {
            os = SVNFileUtil.openFileForWriting(readme);
            try {
                String eol = System.getProperty("line.separator");
                eol = eol == null ? "\n" : eol;
                os.write(("This is a Subversion working copy administrative directory." + eol + 
                "Visit http://subversion.tigris.org/ for more information." + eol).getBytes());
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            } finally {
                SVNFileUtil.closeFile(os);
            }
        }
        File empty = new File(adminDir, "empty-file");
        if (!empty.exists()) {
            try {
                empty.createNewFile();
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            }
        }
        File[] tmp = {
                new File(adminDir, "tmp" + File.separatorChar + "props"),
                new File(adminDir, "tmp" + File.separatorChar + "prop-base"),
                new File(adminDir, "tmp" + File.separatorChar + "text-base"),
                new File(adminDir, "tmp" + File.separatorChar + "wcprops"),
                new File(adminDir, "props"),
                new File(adminDir, "prop-base"),
                new File(adminDir, "text-base"),
                new File(adminDir, "wcprops")};
        for(int i = 0; i < tmp.length; i++) {
            if (!tmp[i].exists()) {
                tmp[i].mkdirs();
            }
        }
    }
    
    public void destroy(String name, boolean deleteWorkingFiles) throws SVNException {
        if ("".equals(name)) {
            SVNDirectory parent = null;
            if ("".equals(myPath)) {
                SVNWCAccess parentWCAccess = null;
                try {
                    parentWCAccess = SVNWCAccess.create(getRoot().getParentFile());
                    parentWCAccess.open(true, false);
                    parent = parentWCAccess.getAnchor();
                    destroyDirectory(parent, this, deleteWorkingFiles);
                } catch (SVNException e) {
                    parent = null;
                } finally {
                    if (parent != null) {
                        parent.getEntries().save(true);
                    }
                    if (parentWCAccess != null) {
                        parentWCAccess.close(true);
                    }
                    myWCAccess.removeDirectory("");
                }
                if (parent != null) {
                    return;
                }
            } else {
                String parentPath = PathUtil.removeTail(myPath);
                parent = myWCAccess.getDirectory(parentPath);
                if (parent != null && !parent.isVersioned()) {
                    parent = null;
                }
            }
            destroyDirectory(parent, this, deleteWorkingFiles);
            myWCAccess.removeDirectory(myPath);
            if (parent != null) {
                parent.getEntries().save(true);
            }
        } else {
            File file = getFile(name, false);
            SVNFileType type = SVNFileType.getType(file);
            if (type == SVNFileType.DIRECTORY) {
                SVNDirectory childDir = getChildDirectory(name);
                if (childDir != null && childDir.isVersioned()) {
                    destroyDirectory(this, childDir, deleteWorkingFiles);
                    myWCAccess.removeDirectory(childDir.getPath());
                }
            } else {
                destroyFile(name, deleteWorkingFiles);
            }
        }
        getEntries().save(false);
    }
    
    public void scheduleForDeletion(String name) throws SVNException {
        SVNEntries entries = getEntries();
        SVNEntry entry = entries.getEntry(name, true);
        if (entry == null) {
            SVNFileUtil.deleteAll(getFile(name, false));
            return;
        }
        boolean added = entry.isScheduledForAddition();
        boolean deleted = false;
        SVNNodeKind kind = entry.getKind();
        if (entry.getKind() == SVNNodeKind.DIR) {
            // try to get parent entry
            SVNDirectory parent;
            SVNDirectory child;
            String nameInParent;
            if (!"".equals(name)) {
                parent = this;
                nameInParent = name;
                child = getChildDirectory(name);
            } else {
                child = this;
                nameInParent = PathUtil.tail(myPath); 
                String parentPath = PathUtil.removeTail(myPath);
                parentPath = PathUtil.removeLeadingSlash(parentPath);
                parent = myWCAccess.getDirectory(parentPath);
            }
            deleted = parent != null ? parent.getEntries().getEntry(nameInParent, true).isDeleted() : false;
            if (added && !deleted) {
                // destroy whole child dir.
                if (child != null) {
                    child.destroy("", true);
                } else if (parent != null) {
                    // no child, remove entry in parent
                    parent.getEntries().deleteEntry(nameInParent);
                    parent.getEntries().save(false);
                }
            } else if (child != null) {
                // recursively mark for deletion (but not "").
                child.updateEntryProperty(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_DELETE, true);
            }
            if (parent != null) {
                parent.getEntries().save(false);
            }
            if (child != null) {
                child.getEntries().save(false);
            }
        } 
        if (!(kind == SVNNodeKind.DIR && added && !deleted)) {
            // schedule entry in parent.            
            entry.scheduleForDeletion();
        }
        SVNEvent event = SVNEventFactory.createDeletedEvent(myWCAccess, this, entry.getName());
        myWCAccess.handleEvent(event);
        if (added) {
            SVNFileUtil.deleteAll(getFile(name, false));
        } else {
            deleteWorkingFiles(name);
        }
        entries.save(true);
    }
    
    public SVNEntry add(String name, boolean mkdir, boolean force) throws SVNException {
    	File file = getFile(name, false);
        SVNFileType fileType = SVNFileType.getType(file);
        if (fileType == SVNFileType.NONE && !mkdir) {
    		SVNErrorManager.error("svn: '" + file + "' not found");
    	}
    	SVNNodeKind fileKind = fileType == SVNFileType.NONE || fileType == SVNFileType.DIRECTORY ? SVNNodeKind.DIR : SVNNodeKind.FILE;
    	SVNEntry entry = getEntries().getEntry(name, true);

    	if (entry != null && !entry.isDeleted() && !entry.isScheduledForDeletion()) {
    		if (!force) {
    			SVNErrorManager.error("svn: '" + file + "' already under version control");
    		} 
    		return entry;
    	} else if (entry != null && entry.getKind() != fileKind) {
    		SVNErrorManager.error("svn: Can't replace '" + file + "' with a node of different type; commit the deletion, update the parent," +
    				" and then add '" + file + "'");
    	} else if (entry == null && SVNWCUtil.isVersionedDirectory(file)) {
            if (!force) {
                SVNErrorManager.error("svn: '" + file + "' already under version control");
            }
            return null;            
        }
    	boolean replace = entry != null && entry.isScheduledForDeletion();
    	// TODO check parent dir
    	if (entry == null) {
    		entry = getEntries().addEntry(name);
    	}
    	if (replace) {
    		entry.scheduleForReplacement();
    	} else {
    		entry.scheduleForAddition();
    	}
    	if (!replace) {
    		entry.setRevision(0);
    	}
    	entry.setKind(fileKind);
    	if (replace) {
    		getProperties(name, false).delete();
    	}
    	if (fileKind == SVNNodeKind.DIR) {
    		// compose new url
    		String parentURL = getEntries().getEntry("", true).getURL();
    		String childURL = PathUtil.append(parentURL, PathUtil.encode(name));
    		// if child dir exists (deleted) -> check that url is the same and revision is the same
    		SVNDirectory childDir = getChildDirectory(name);
    		if (childDir != null) {
    			String existingURL = childDir.getEntries().getEntry("", true).getURL();
    			if (!existingURL.equals(childURL)) {
    				SVNErrorManager.error("svn: URL doesn't match");
    			}
    		} else {
    			childDir = createChildDirectory(name, childURL, 0);
    		}
    		if (!replace) {
    			childDir.getEntries().getEntry("", true).scheduleForAddition();
    		} else {
    			childDir.getEntries().getEntry("", true).scheduleForReplacement();
    		}
    		childDir.getEntries().save(true);
        }
        SVNEvent event = SVNEventFactory.createAddedEvent(myWCAccess, this, entry);
        myWCAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);
    	getEntries().save(false);
        return entry;
    }
    
    public void updateEntryProperty(String propertyName, String value, boolean recursive) throws SVNException {
        SVNEntries entries = getEntries();
        for (Iterator ents = entries.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            if (entry.isDirectory() && recursive) {
                SVNDirectory childDir = getChildDirectory(entry.getName());
                if (childDir != null) {
                    childDir.updateEntryProperty(propertyName, value, recursive);
                }
            } 
            entries.setPropertyValue(entry.getName(), propertyName, value);
            if (SVNProperty.SCHEDULE_DELETE.equals(value)) {
                SVNEvent event = SVNEventFactory.createDeletedEvent(myWCAccess, this, entry.getName());
                myWCAccess.handleEvent(event);
            }
        }
        SVNEntry root = entries.getEntry("", true);
        if (!(SVNProperty.SCHEDULE_DELETE.equals(value) && root.isScheduledForAddition())) {
            root.scheduleForDeletion();
        }
        entries.save(false);
    }

    public void updateURL(String rootURL, boolean recursive) throws SVNException {
        SVNEntries entries = getEntries();
        for (Iterator ents = entries.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName()) && entry.isDirectory() && recursive) {
                SVNDirectory childDir = getChildDirectory(entry.getName());
                if (childDir != null) {
                    String childURL = PathUtil.append(rootURL, PathUtil.encode(entry.getName()));
                    childDir.updateURL(childURL, recursive);
                }
                continue;
            } 
            entries.setPropertyValue(entry.getName(), SVNProperty.URL, 
                    "".equals(entry.getName()) ? 
                            rootURL : 
                            PathUtil.append(rootURL, PathUtil.encode(entry.getName())));
        }
        entries.save(false);
    }

    private void deleteWorkingFiles(String name) throws SVNException {
        File file = getFile(name, false);
        if (file.isFile()) {
            file.delete();
        } else if (file.isDirectory()) {
            SVNDirectory childDir = getChildDirectory(file.getName());
            if (childDir != null) {
                SVNEntries childEntries = childDir.getEntries();
                for(Iterator childEnts = childEntries.entries(true); childEnts.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) childEnts.next();
                    if ("".equals(childEntry.getName())) {
                        continue;
                    }
                    childDir.deleteWorkingFiles(childEntry.getName());                    
                }
                File[] allFiles = file.listFiles();
                for (int i = 0; allFiles != null && i < allFiles.length; i++) {
                    if (".svn".equals(allFiles[i].getName())) {
                        continue;
                    }
                    if (childEntries.getEntry(allFiles[i].getName(), true) != null) {
                        continue;
                    }
                    SVNFileUtil.deleteAll(allFiles[i]);
                }
            } else {
                SVNFileUtil.deleteAll(file);
            }
        }
    }
    
    public void setWCAccess(SVNWCAccess wcAccess, String path) {
        myWCAccess = wcAccess;
        myPath = path;
    }
    
    private void destroyFile(String name, boolean deleteWorkingFile) throws SVNException {
        SVNEntries entries = getEntries();
        if (entries.getEntry(name, true) != null) {
            if (deleteWorkingFile && !hasTextModifications(name, false)) {
                getFile(name, false).delete();
            } 
        }
        entries.deleteEntry(name);
        
        getBaseProperties(name, false).delete();
        getWCProperties(name).delete();
        
        getProperties(name, false).delete();
        File baseFile = getBaseFile(name, false);
        baseFile.delete();
    }
    
    private static void destroyDirectory(SVNDirectory parent, SVNDirectory dir, boolean deleteWorkingFiles) throws SVNException {
        SVNEntries entries = dir.getEntries();
        entries.getEntry("", true).setIncomplete(true);
        
        for (Iterator ents = entries.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            if (entry.getKind() == SVNNodeKind.FILE) {
                dir.destroyFile(entry.getName(), deleteWorkingFiles);
            } else if (entry.getKind() == SVNNodeKind.DIR) {
                SVNDirectory childDirectory = dir.getChildDirectory(entry.getName());
                if (childDirectory == null) {
                    entries.deleteEntry(entry.getName());
                } else {
                    destroyDirectory(dir, childDirectory, deleteWorkingFiles);
                }
            }
        }

        if (parent != null) {
            SVNEntries parentEntries = parent.getEntries();
            parentEntries.deleteEntry(dir.getRoot().getName());
        }
        dir.getEntries().save(true);

        SVNFileUtil.deleteAll(new File(dir.getRoot(), ".svn"));
        dir.getRoot().delete();
    }

    private static void debugQData(String label, QSequenceLineRAData baseData) throws IOException {
        InputStream is = baseData.read(0, baseData.length());
        StringBuffer sb = new StringBuffer();
        while(true) {
            int r = is.read();
            if (r < 0) {
                break;
            }
            sb.append((char) (r & 0xFF));
        }
        is.close();
        DebugLog.log(label + ":" + sb.toString());
    }

    public void commit(String target, SVNCommitInfo info, Map wcPropChanges, boolean removeLock, boolean recursive) throws SVNException {
        DebugLog.log("commit is called on " + getRoot() + ", target: " + target);
        SVNLog log = getLog(0);

        //
        String checksum = null;
        if (!"".equals(target)) {
            File baseFile = getBaseFile(target, true);
            SVNFileType baseType = SVNFileType.getType(baseFile);
            if (baseType == SVNFileType.NONE) {
                baseFile = getBaseFile(target, false);
                baseType = SVNFileType.getType(baseFile);
            }
            if (baseType == SVNFileType.FILE) {
                checksum = SVNFileUtil.computeChecksum(baseFile);
            }
            recursive = false;
        } else {

        }
        Map command = new HashMap();
        if (info != null) {
            command.put(SVNLog.NAME_ATTR, target);
            command.put(SVNProperty.shortPropertyName(SVNProperty.COMMITTED_REVISION), Long.toString(info.getNewRevision()));
            command.put(SVNProperty.shortPropertyName(SVNProperty.COMMITTED_DATE), TimeUtil.formatDate(info.getDate()));
            command.put(SVNProperty.shortPropertyName(SVNProperty.LAST_AUTHOR), info.getAuthor());
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (checksum != null) {
            command.put(SVNLog.NAME_ATTR, target);
            command.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), checksum);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (removeLock) {
            command.put(SVNLog.NAME_ATTR, target);
            log.addCommand(SVNLog.DELETE_LOCK, command, false);
            command.clear();
        }
        command.put(SVNLog.NAME_ATTR, target);
        command.put(SVNLog.REVISION_ATTR, info == null ? null : Long.toString(info.getNewRevision()));
        log.addCommand(SVNLog.COMMIT, command, false);
        command.clear();
        if (wcPropChanges != null && !wcPropChanges.isEmpty()) {
            for (Iterator propNames = wcPropChanges.keySet().iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                String propValue = (String) wcPropChanges.get(propName);
                command.put(SVNLog.NAME_ATTR, target);
                command.put(SVNLog.PROPERTY_NAME_ATTR, propName);
                command.put(SVNLog.PROPERTY_VALUE_ATTR, propValue);
                log.addCommand(SVNLog.MODIFY_WC_PROPERTY, command, false);
                command.clear();
            }
        }
        log.save();
        runLogs();

        if (recursive) {
            SVNEntries entries = getEntries();
            DebugLog.log("iterating entries...");
            for(Iterator ents = entries.entries(true); ents.hasNext();) {
                SVNEntry entry = (SVNEntry) ents.next();
                if ("".equals(entry.getName())) {
                    continue;
                }
                if (entry.getKind() == SVNNodeKind.DIR) {
                    SVNDirectory childDir = getChildDirectory(entry.getName());
                    if (childDir != null) {
                        childDir.commit("", info, null, removeLock, true);
                    }
                } else {
                    commit(entry.getName(), info, null, removeLock, false);
                }
            }
        }
    }
}
