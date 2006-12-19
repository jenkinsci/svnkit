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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNDiffEditor implements ISVNEditor {

    private SVNWCAccess myWCAccess;
    private boolean myUseAncestry;
    private boolean myIsReverseDiff;
    private boolean myIsCompareToBase;
    private boolean myIsRootOpen;
    private long myTargetRevision;
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private SVNDeltaProcessor myDeltaProcessor;
    private SVNAdminAreaInfo myAdminInfo;
    private boolean myIsRecursive;
    private File myTempDirectory;
    private AbstractDiffCallback myDiffCallback;

    public SVNDiffEditor(SVNWCAccess wcAccess, SVNAdminAreaInfo info, AbstractDiffCallback callback,
            boolean useAncestry, boolean reverseDiff, boolean compareToBase, boolean recursive) {
        myWCAccess = wcAccess;
        myAdminInfo = info;
        myUseAncestry = useAncestry;
        myIsReverseDiff = reverseDiff;
        myIsRecursive = recursive;
        myIsCompareToBase = compareToBase;
        myDiffCallback = callback;
        myDeltaProcessor = new SVNDeltaProcessor();
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myCurrentDirectory = createDirInfo(null, "", false);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        File fullPath = new File(myAdminInfo.getAnchor().getRoot(), path);
        SVNAdminArea dir = myWCAccess.probeRetrieve(fullPath);
        SVNEntry entry = myWCAccess.getEntry(fullPath, false);
        if (entry == null) {
            return;
        }
        String name = SVNPathUtil.tail(path);
        myCurrentDirectory.myComparedEntries.add(name);
        if (!myIsCompareToBase && entry.isScheduledForDeletion()) {
            return;
        }
        if (entry.isFile()) {
            if (myIsReverseDiff) {
                File baseFile = dir.getBaseFile(name, false);
                Map baseProps = dir.getBaseProperties(name).asMap();
                getDiffCallback().fileDeleted(path, baseFile, null, null, null, baseProps);
            } else {
                reportAddedFile(myCurrentDirectory, path, entry);
            }
        } else if (entry.isDirectory()) {
            SVNDirectoryInfo info = createDirInfo(myCurrentDirectory, path, false);
            reportAddedDir(info);
        }
    }
    
    private void reportAddedDir(SVNDirectoryInfo info) throws SVNException {
        SVNAdminArea dir = retrieve(info.myPath);
        Map wcProps;
        if (myIsCompareToBase) {
            wcProps = dir.getBaseProperties(dir.getThisDirName()).asMap();
        } else {
            wcProps = dir.getProperties(dir.getThisDirName()).asMap();
        }
        Map propDiff = computePropsDiff(new HashMap(), wcProps);
        if (!propDiff.isEmpty()) {
            getDiffCallback().propertiesChanged(info.myPath, null, propDiff);
        }
        for(Iterator entries = dir.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (dir.getThisDirName().equals(entry.getName())) {
                continue;
            }
            if (!myIsCompareToBase && entry.isScheduledForDeletion()) {
                continue;
            }
            if (entry.isFile()) {
                reportAddedFile(info, SVNPathUtil.append(info.myPath, entry.getName()), entry);
            } else if (entry.isDirectory() && myIsRecursive) {
                SVNDirectoryInfo childInfo = createDirInfo(info, SVNPathUtil.append(info.myPath, entry.getName()), false);
                reportAddedDir(childInfo);
            }
        }
    }

    private void reportAddedFile(SVNDirectoryInfo info, String path, SVNEntry entry) throws SVNException {
        if (entry.isCopied()) {
            if (myIsCompareToBase) {
                return;
            }
            reportModifiedFile(info, entry);
            return;
        }
        SVNAdminArea dir = retrieve(info.myPath);
        String name = SVNPathUtil.tail(path);
        Map wcProps = null;
        if (myIsCompareToBase) {
            wcProps = dir.getBaseProperties(name).asMap();
        } else {
            wcProps = dir.getProperties(name).asMap();
        }
        String mimeType = (String) wcProps.get(SVNProperty.MIME_TYPE);
        Map propDiff = computePropsDiff(new HashMap(), wcProps);
        
        File sourceFile;
        if (myIsCompareToBase) {
            sourceFile = dir.getBaseFile(name, false);
        } else {
            sourceFile = detranslateFile(dir, name);
        }
        getDiffCallback().fileAdded(path, null, sourceFile, 0, entry.getRevision(), null, mimeType, null, propDiff);
    }
    
    private void reportModifiedFile(SVNDirectoryInfo dirInfo, SVNEntry entry) throws SVNException {
        SVNAdminArea dir = retrieve(dirInfo.myPath);
        String schedule = entry.getSchedule();
        String fileName = entry.getName();
        if (entry.isCopied()) {
            schedule = null;
        }
        if (!myUseAncestry && entry.isScheduledForReplacement()) {
            schedule = null;
        }
        Map propDiff = null;
        Map baseProps = null;
        File baseFile = dir.getBaseFile(fileName, false);
        if (!entry.isScheduledForDeletion()) {
            boolean modified = dir.hasPropModifications(fileName);
            if (modified) {
                baseProps = dir.getBaseProperties(fileName).asMap();
                propDiff = computePropsDiff(baseProps, dir.getProperties(fileName).asMap());
            } else {
                propDiff = new HashMap();
            }
        } else {
            baseProps = dir.getBaseProperties(fileName).asMap();
        }
        boolean isAdded = schedule != null && entry.isScheduledForAddition();
        String filePath = SVNPathUtil.append(dirInfo.myPath, fileName);
        if (schedule != null && (entry.isScheduledForDeletion() || entry.isScheduledForReplacement())) {
            String mimeType = dir.getBaseProperties(fileName).getPropertyValue(SVNProperty.MIME_TYPE);
            getDiffCallback().fileDeleted(filePath, baseFile, null, mimeType, null, dir.getBaseProperties(fileName).asMap());
            isAdded = entry.isScheduledForReplacement();
        }
        if (isAdded) {
            String mimeType = dir.getProperties(fileName).getPropertyValue(SVNProperty.MIME_TYPE);
            
            File tmpFile = detranslateFile(dir, fileName);
//            SVNDebugLog.getDefaultLog().info("added");
            getDiffCallback().fileAdded(filePath, null, tmpFile, 0, entry.getRevision(), mimeType, null, dir.getBaseProperties(fileName).asMap(), propDiff);
        } else if (schedule == null) {
            boolean modified = dir.hasTextModifications(fileName, false);
            File tmpFile = null;
            if (modified) {
                tmpFile = detranslateFile(dir, fileName);
            }
//            SVNDebugLog.getDefaultLog().info("modified: " + modified);
            if (modified || (propDiff != null && !propDiff.isEmpty())) {
                String baseMimeType = dir.getBaseProperties(fileName).getPropertyValue(SVNProperty.MIME_TYPE); 
                String mimeType = dir.getProperties(fileName).getPropertyValue(SVNProperty.MIME_TYPE);

                getDiffCallback().fileChanged(filePath, modified ? baseFile : null, tmpFile, entry.getRevision(), -1, 
                        baseMimeType, mimeType, baseProps, propDiff);
            }
        }
        
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentDirectory = createDirInfo(myCurrentDirectory, path, true);
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = createDirInfo(myCurrentDirectory, path, false);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        if (myCurrentDirectory.myPropertyDiff == null) {
            myCurrentDirectory.myPropertyDiff = new HashMap();
        }
        myCurrentDirectory.myPropertyDiff.put(name, value);
    }

    public void closeDir() throws SVNException {
        // display dir prop changes.
        Map diff = myCurrentDirectory.myPropertyDiff;
        if (diff != null && !diff.isEmpty()) {
            // reverse changes
            Map originalProps = null;
            if (myCurrentDirectory.myIsAdded) {
                originalProps = new HashMap();
            } else {
                SVNAdminArea dir = retrieve(myCurrentDirectory.myPath);
                if (dir != null && myIsCompareToBase) {
                    originalProps = dir.getBaseProperties(dir.getThisDirName()).asMap();
                } else {
                    originalProps = dir.getProperties(dir.getThisDirName()).asMap();
                    Map baseProps = dir.getBaseProperties(dir.getThisDirName()).asMap();
                    Map reposProps = new HashMap(baseProps);
                    for(Iterator diffNames = diff.keySet().iterator(); diffNames.hasNext();) {
                        String diffName = (String) diffNames.next();
                        reposProps.put(diffName, diff.get(diffName));
                    }
                    diff = computePropsDiff(originalProps, reposProps);
                    
                }
            }
            if (!myIsReverseDiff) {
                reversePropChanges(originalProps, diff);
            }
            getDiffCallback().propertiesChanged(myCurrentDirectory.myPath, originalProps, diff);
            myCurrentDirectory.myComparedEntries.add("");
        }
        if (!myCurrentDirectory.myIsAdded) {
            localDirectoryDiff(myCurrentDirectory);
        }
        String name = SVNPathUtil.tail(myCurrentDirectory.myPath);
        myCurrentDirectory = myCurrentDirectory.myParent;
        if (myCurrentDirectory != null) {
            myCurrentDirectory.myComparedEntries.add(name);
        }
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        String name = SVNPathUtil.tail(path);
        myCurrentFile = createFileInfo(myCurrentDirectory, path, true);
        myCurrentDirectory.myComparedEntries.add(name);
    }

    public void openFile(String path, long revision) throws SVNException {
        String name = SVNPathUtil.tail(path);
        myCurrentFile = createFileInfo(myCurrentDirectory, path, false);
        myCurrentDirectory.myComparedEntries.add(name);
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        if (myCurrentFile.myPropertyDiff == null) {
            myCurrentFile.myPropertyDiff = new HashMap();
        }
        myCurrentFile.myPropertyDiff.put(name, value);
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        SVNEntry entry = myWCAccess.getEntry(myAdminInfo.getAnchor().getFile(path), false);
        if (entry != null && entry.isCopied()) {
            myCurrentFile.myIsAdded = false;
        }
        if (!myCurrentFile.myIsAdded) {
            SVNAdminArea dir = retrieve(myCurrentDirectory.myPath);
            String fileName = SVNPathUtil.tail(myCurrentFile.myPath);
            myCurrentFile.myBaseFile = dir.getBaseFile(fileName, false);
        } 
        myCurrentFile.myFile = createTempFile();
        myDeltaProcessor.applyTextDelta(myCurrentFile.myBaseFile, myCurrentFile.myFile, false);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        myDeltaProcessor.textDeltaEnd();
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        String fileName = SVNPathUtil.tail(myCurrentFile.myPath);
        
        File filePath = myAdminInfo.getAnchor().getFile(myCurrentFile.myPath);
        SVNAdminArea dir = myWCAccess.probeRetrieve(filePath);
        SVNEntry entry = myWCAccess.getEntry(filePath, false);
        Map baseProperties = null;
        if (myCurrentFile.myIsAdded) {
            baseProperties = new HashMap();
        } else {
            baseProperties = dir != null ? dir.getBaseProperties(fileName).asMap() : new HashMap();
        }
        Map reposProperties = new HashMap(baseProperties);
        if (myCurrentFile.myPropertyDiff != null) {
            for(Iterator propNames = myCurrentFile.myPropertyDiff.keySet().iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                reposProperties.put(propName, myCurrentFile.myPropertyDiff.get(propName));
            }
        }
        String reposMimeType = (String) reposProperties.get(SVNProperty.MIME_TYPE);
        File reposFile = myCurrentFile.myFile;
        File localFile = null;
        if (reposFile == null) {
            reposFile = dir.getBaseFile(fileName, false);
        }
        if (myCurrentFile.myIsAdded || (!myIsCompareToBase && entry.isScheduledForDeletion())) {
            if (myIsReverseDiff) {
                getDiffCallback().fileAdded(commitPath, null, reposFile, 0, myTargetRevision, null, reposMimeType, null, myCurrentFile.myPropertyDiff);
            } else {
                getDiffCallback().fileDeleted(commitPath, reposFile, null, reposMimeType, null, reposProperties);
            }
            return;
        }
        boolean modified = myCurrentFile.myFile != null;
        if (!modified && !myIsCompareToBase) {
            modified = dir.hasTextModifications(fileName, false);
        }
        if (modified) {
            if (myIsCompareToBase) {
                localFile = dir.getBaseFile(fileName, false);
            } else {
                localFile = detranslateFile(dir, fileName);
            }
        } else {
            localFile = null;
            reposFile = null;
        }
        
        Map originalProps = null;
        if (myIsCompareToBase) {
            originalProps = baseProperties;
        } else {
            originalProps = dir.getProperties(fileName).asMap();
            myCurrentFile.myPropertyDiff = computePropsDiff(originalProps, reposProperties);
        }
        
        if (localFile != null || (myCurrentFile.myPropertyDiff != null && !myCurrentFile.myPropertyDiff.isEmpty())) {
            String originalMimeType = (String) originalProps.get(SVNProperty.MIME_TYPE);
            if (myCurrentFile.myPropertyDiff != null && !myCurrentFile.myPropertyDiff.isEmpty() && !myIsReverseDiff) {
                reversePropChanges(originalProps, myCurrentFile.myPropertyDiff);
            }
            if (localFile != null || reposFile != null || (myCurrentFile.myPropertyDiff != null && !myCurrentFile.myPropertyDiff.isEmpty())) {
                getDiffCallback().fileChanged(commitPath, 
                        myIsReverseDiff ? localFile : reposFile, 
                        myIsReverseDiff ? reposFile : localFile, 
                        myIsReverseDiff ? -1 : myTargetRevision,
                        myIsReverseDiff ? myTargetRevision : -1,
                        myIsReverseDiff ? originalMimeType : reposMimeType, 
                        myIsReverseDiff ? reposMimeType : originalMimeType,
                        originalProps, myCurrentFile.myPropertyDiff);
            }
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (!myIsRootOpen) {
            localDirectoryDiff(createDirInfo(null, "", false));
        }
        return null;
    }
    

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void cleanup() {
        if (myTempDirectory != null) {
            SVNFileUtil.deleteAll(myTempDirectory, true);
        }
    }

    private void localDirectoryDiff(SVNDirectoryInfo info) throws SVNException {
        if (myIsCompareToBase) {
            return;
        }
        SVNAdminArea dir = retrieve(info.myPath);
        boolean anchor = !"".equals(myAdminInfo.getTargetName()) && dir == myAdminInfo.getAnchor();
        if (!anchor && !info.myComparedEntries.contains("")) {
            // generate prop diff for dir.
            if (dir.hasPropModifications(dir.getThisDirName())) {
                SVNVersionedProperties baseProps = dir.getBaseProperties(dir.getThisDirName());
                Map propDiff = baseProps.compareTo(dir.getProperties(dir.getThisDirName())).asMap();
                getDiffCallback().propertiesChanged(info.myPath, baseProps.asMap(), propDiff);
            }
        }
        Set processedFiles = null;
        if (getDiffCallback().isDiffUnversioned()) {
            processedFiles = new HashSet();
        }
        for (Iterator entries = dir.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            
            if (processedFiles != null && !dir.getThisDirName().equals(entry.getName())) {
                processedFiles.add(entry.getName());
            }
            if (anchor && !myAdminInfo.getTargetName().equals(entry.getName())) {
                continue;
            }
            if (dir.getThisDirName().equals(entry.getName())) {
                continue;
            }
            if (info.myComparedEntries.contains(entry.getName())) {
                continue;
            }
            info.myComparedEntries.add(entry.getName());
            if (entry.isFile()) {
                reportModifiedFile(info, entry);
            } else if (entry.isDirectory()) {
                if (anchor || myIsRecursive) {
                    SVNDirectoryInfo childInfo = createDirInfo(info, SVNPathUtil.append(info.myPath, entry.getName()), false);
                    localDirectoryDiff(childInfo);
                }
            }
        }
        if (getDiffCallback().isDiffUnversioned()) {
            diffUnversioned(dir.getRoot(), dir, anchor, processedFiles);
        }
    }

    private void diffUnversioned(File root, SVNAdminArea dir, boolean anchor, Set processedFiles) throws SVNException {
        File[] allFiles = root.listFiles();
        for (int i = 0; allFiles != null && i < allFiles.length; i++) {
            File file = allFiles[i];
            if (SVNFileUtil.getAdminDirectoryName().equals(file.getName())) {
                continue;
            }
            if (processedFiles != null && processedFiles.contains(file.getName())) {
                continue;
            }
            if (anchor && !myAdminInfo.getTargetName().equals(file.getName())) {
                continue;
            } else if (dir != null) {// && SVNStatusEditor.isIgnored(, name)dir.isIgnored(file.getName())) {
                Collection globalIgnores = SVNStatusEditor.getGlobalIgnores(myWCAccess.getOptions());
                Collection ignores = SVNStatusEditor.getIgnorePatterns(dir, globalIgnores);
                if (SVNStatusEditor.isIgnored(ignores, file.getName())) {
                    continue;
                }
            }
            // generate patch as for added file.
            SVNFileType fileType = SVNFileType.getType(file);
            if (fileType == SVNFileType.DIRECTORY) {
                diffUnversioned(file, null, false, null);
            } else if (fileType == SVNFileType.FILE) {
                String mimeType1 = null;
                String mimeType2 = SVNFileUtil.detectMimeType(file);
                String filePath = SVNPathUtil.append(dir.getRelativePath(myAdminInfo.getAnchor()), file.getName());
                getDiffCallback().fileAdded(filePath, null, file, 0, 0, mimeType1, mimeType2, null, null);
            }
        }
    }

    private SVNDirectoryInfo createDirInfo(SVNDirectoryInfo parent, String path, boolean added) {
        SVNDirectoryInfo info = new SVNDirectoryInfo();
        info.myParent = parent;
        info.myPath = path;
        info.myIsAdded = added;
        return info;
    }

    private SVNFileInfo createFileInfo(SVNDirectoryInfo parent, String path, boolean added) {
        SVNFileInfo info = new SVNFileInfo();
        info.myPath = path;
        info.myIsAdded = added;
        if (parent.myIsAdded) {
            while(parent.myIsAdded) {
                parent = parent.myParent;
            }
            info.myPath = SVNPathUtil.append(parent.myPath, "fake");
        }
        return info;
    }
    
    private File detranslateFile(SVNAdminArea dir, String name) throws SVNException {
        SVNVersionedProperties properties = dir.getProperties(name);
        String keywords = properties.getPropertyValue(SVNProperty.KEYWORDS);
        String eolStyle = properties.getPropertyValue(SVNProperty.EOL_STYLE);
        boolean special = properties.getPropertyValue(SVNProperty.SPECIAL) != null;
        if (keywords == null && eolStyle == null && (!special || SVNFileUtil.isWindows)) {
            return dir.getFile(name);
        }
        byte[] eol = SVNTranslator.getEOL(eolStyle);
        File tmpFile = createTempFile();
        Map keywordsMap = SVNTranslator.computeKeywords(keywords, null, null, null, null, null);
        SVNTranslator.translate(dir.getFile(name), tmpFile, eol, keywordsMap, special, false);
        return tmpFile;
    }
    
    private File createTempFile() throws SVNException {
        File tmpFile = null;
        try {
            return File.createTempFile("diff.", ".tmp", getTempDirectory());
        } catch (IOException e) {
            SVNFileUtil.deleteFile(tmpFile);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err);
        }
        return null;
    }
    
    private File getTempDirectory() throws SVNException {
        if (myTempDirectory == null) {
            myTempDirectory = getDiffCallback().createTempDirectory();
        }
        return myTempDirectory;
    }
    
    private SVNAdminArea retrieve(String path) throws SVNException {
        File dir = myAdminInfo.getAnchor().getFile(path);
        return myWCAccess.retrieve(dir);
    }
    
    private AbstractDiffCallback getDiffCallback() {
        return myDiffCallback;
    }

    private static class SVNDirectoryInfo {

        private boolean myIsAdded;
        private String myPath;
        private Map myPropertyDiff;
        private SVNDirectoryInfo myParent;
        private Set myComparedEntries = new HashSet();
    }

    private static class SVNFileInfo {

        private boolean myIsAdded;
        private String myPath;
        private File myFile;
        private File myBaseFile;
        private Map myPropertyDiff;
    }

    private static void reversePropChanges(Map base, Map diff) {
        Collection namesList = new ArrayList(diff.keySet());
        for (Iterator names = namesList.iterator(); names.hasNext();) {
            String name = (String) names.next();
            String newValue = (String) diff.get(name);
            String oldValue = (String) base.get(name);
            if (oldValue == null && newValue != null) {
                base.put(name, newValue);
                diff.put(name, null);
            } else if (oldValue != null && newValue == null) {
                base.put(name, null);
                diff.put(name, oldValue);
            } else if (oldValue != null && newValue != null) {
                base.put(name, newValue);
                diff.put(name, oldValue);
            }
        }
    }

    private static Map computePropsDiff(Map props1, Map props2) {
        Map propsDiff = new HashMap();
        for (Iterator names = props2.keySet().iterator(); names.hasNext();) {
            String newPropName = (String) names.next();
            if (props1.containsKey(newPropName)) {
                // changed.
                Object oldValue = props2.get(newPropName);
                if (oldValue != null && !oldValue.equals(props1.get(newPropName))) {
                    propsDiff.put(newPropName, props2.get(newPropName));
                } else if (oldValue == null && props1.get(newPropName) != null) {
                    propsDiff.put(newPropName, props2.get(newPropName));
                }
            } else {
                // added.
                propsDiff.put(newPropName, props2.get(newPropName));
            }
        }
        for (Iterator names = props1.keySet().iterator(); names.hasNext();) {
            String oldPropName = (String) names.next();
            if (!props2.containsKey(oldPropName)) {
                // deleted
                propsDiff.put(oldPropName, null);
            }
        }
        return propsDiff;
    }

}
