/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry2;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator2;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess2;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNDiffEditor implements ISVNEditor {

    private SVNWCAccess2 myWCAccess;
    private ISVNDiffGenerator myDiffGenerator;
    private boolean myUseAncestry;
    private boolean myIsReverseDiff;
    private boolean myIsCompareToBase;
    private OutputStream myResult;
    private boolean myIsRootOpen;
    private long myTargetRevision;
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private SVNDeltaProcessor myDeltaProcessor;
    private SVNAdminAreaInfo myAdminInfo;

    public SVNDiffEditor(SVNWCAccess2 wcAccess, SVNAdminAreaInfo info, ISVNDiffGenerator diffGenerator,
            boolean useAncestry, boolean reverseDiff, boolean compareToBase, OutputStream result) {
        myWCAccess = wcAccess;
        myAdminInfo = info;
        myDiffGenerator = diffGenerator;
        myUseAncestry = useAncestry;
        myIsReverseDiff = reverseDiff;
        myResult = result;
        myIsCompareToBase = compareToBase;
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
        SVNEntry2 entry = myWCAccess.getEntry(fullPath, false);
        if (entry == null) {
            return;
        }
        String name = SVNPathUtil.tail(path);
        myCurrentDirectory.myComparedEntries.add(name);
        if (!myIsCompareToBase && entry.isScheduledForDeletion()) {
            return;
        }
        String displayPath = dir.getFile(name).getAbsolutePath().replace(File.separatorChar, '/');
        if (entry.isFile()) {
            if (myIsReverseDiff) {
                File baseFile = dir.getBaseFile(name, false);
                Map baseProps = dir.getBaseProperties(name).asMap();
                String mimeType = (String) baseProps.get(SVNProperty.MIME_TYPE);
                String revStr = "(revision " + myTargetRevision + ")";
                myDiffGenerator.displayFileDiff(displayPath, baseFile, null, revStr, null, mimeType, null, myResult);
                Map diff = new HashMap(baseProps);
                for(Iterator names = baseProps.keySet().iterator(); names.hasNext();) {
                    String propName = (String) names.next();
                    diff.put(propName, null);
                }
                myDiffGenerator.displayPropDiff(displayPath, baseProps, diff, myResult);
            } else {
                reportAddedFile(dir, myCurrentDirectory, path, entry);
            }
        } else if (entry.isDirectory()) {
            SVNDirectoryInfo info = createDirInfo(myCurrentDirectory, path, false);
            reportAddedDir(info);
        }
        /*
        
        String name = SVNPathUtil.tail(path);
        String displayPath = dir.getFile(name).getAbsolutePath().replace(File.separatorChar, '/');
        if (entry != null && entry.isFile()) {
            SVNVersionedProperties baseProps = dir.getBaseProperties(name);
            SVNVersionedProperties wcProps = dir.getProperties(name);
            String baseMimeType = baseProps.getPropertyValue(SVNProperty.MIME_TYPE);
            String wcMimeType = wcProps.getPropertyValue(SVNProperty.MIME_TYPE);

            boolean deleted = entry.isScheduledForDeletion();
            if (deleted && !myIsCompareToBase) {
                myCurrentDirectory.myComparedEntries.add(name);
                return;
            }
            if (myIsReverseDiff) {
                // deleted
                File baseFile = dir.getBaseFile(name, false);
                String revStr = "(revision " + myTargetRevision + ")";
                myDiffGenerator.displayFileDiff(displayPath, baseFile, null,
                        !myIsCompareToBase ? revStr : null, !myIsCompareToBase ? null : revStr, baseMimeType, wcMimeType, myResult);
            } else {
                // added (compare agains wc file).
                File baseFile = myIsCompareToBase ? dir.getBaseFile(name, false) : dir.getFile(name);
                File emptyFile = null;
                String revStr2 = "(revision " + entry.getRevision() + ")";
                String revStr1 = "(revision 0)";
                myDiffGenerator.displayFileDiff(displayPath, emptyFile,
                        baseFile, revStr1, revStr2, wcMimeType,
                        baseMimeType, myResult);
            }
        } else if (entry != null && entry.isDirectory()) {
            SVNDirectoryInfo info = createDirInfo(myCurrentDirectory, path, true);
            localDirectoryDiff(info, true, myResult);
        }
        myCurrentDirectory.myComparedEntries.add(name);
        */
    }
    
    private void reportAddedDir(SVNDirectoryInfo info) throws SVNException {
        
    }

    private void reportAddedFile(SVNAdminArea dir, SVNDirectoryInfo info, String path, SVNEntry2 entry) throws SVNException {
        if (entry.isCopied()) {
            if (myIsCompareToBase) {
                return;
            }
            // display diff.
            return;
        }
        String name = SVNPathUtil.tail(path);
        String displayPath = dir.getFile(name).getAbsolutePath().replace(File.separatorChar, '/');
        Map wcProps = null;
        if (myIsCompareToBase) {
            wcProps = dir.getBaseProperties(name).asMap();
        } else {
            wcProps = dir.getProperties(name).asMap();
        }
        String mimeType = (String) wcProps.get(SVNProperty.MIME_TYPE);
        Map propDiff = computePropsDiff(new HashMap(), wcProps);
        
        File sourceFile;
        File tmpFile = null;
        if (myIsCompareToBase) {
            sourceFile = dir.getBaseFile(name, false);
        } else {
            sourceFile = dir.getFile(name);
            String tmpPath = SVNAdminUtil.getTextBasePath(name, true);
            tmpFile = dir.getFile(tmpPath);
            SVNTranslator2.translate(dir, name, name, tmpPath, false, false);
        }
        myDiffGenerator.displayFileDiff(displayPath, null, sourceFile, "(revision 0)", "(revision " + entry.getRevision() + ")", null, mimeType, myResult);
        myDiffGenerator.displayPropDiff(displayPath, null, propDiff, myResult);
        SVNFileUtil.deleteFile(tmpFile);
        
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentDirectory = createDirInfo(myCurrentDirectory, path, true);
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = createDirInfo(myCurrentDirectory, path, false);
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        if (name.startsWith(SVNProperty.SVN_WC_PREFIX) || name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            return;
        }
        if (myCurrentDirectory.myPropertyDiff == null) {
            myCurrentDirectory.myPropertyDiff = new HashMap();
        }
        myCurrentDirectory.myPropertyDiff.put(name, value);
        /*
        if (myCurrentDirectory.myBaseProperties == null) {
            File dirPath = new File(myAdminInfo.getAnchor().getRoot(), myCurrentDirectory.myPath);
            SVNAdminArea dir = retrieve(dirPath);
            if (dir != null) {
                myCurrentDirectory.myBaseProperties = dir.getBaseProperties("").asMap();
            } else {
                myCurrentDirectory.myBaseProperties = new HashMap();
            }
        }*/
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
                File dirPath = new File(myAdminInfo.getAnchor().getRoot(), myCurrentDirectory.myPath);
                SVNAdminArea dir = retrieve(dirPath);
                if (dir != null && myIsCompareToBase) {
                    originalProps = dir.getBaseProperties(dir.getThisDirName()).asMap();
                } else {
                    originalProps = dir.getProperties(dir.getThisDirName()).asMap();
                    SVNDebugLog.getDefaultLog().info("original: " + originalProps);
                    Map baseProps = dir.getBaseProperties(dir.getThisDirName()).asMap();
                    SVNDebugLog.getDefaultLog().info("repos: " + originalProps);
                    Map reposProps = new HashMap(baseProps);
                    for(Iterator diffNames = diff.keySet().iterator(); diffNames.hasNext();) {
                        String diffName = (String) diffNames.next();
                        reposProps.put(diffName, diff.get(diffName));
                    }
                    SVNDebugLog.getDefaultLog().info("repos with diff: " + reposProps);
                    diff = computePropsDiff(originalProps, reposProps);
                    SVNDebugLog.getDefaultLog().info("new diff: " + diff);
                    
                }
            }
            if (!myIsReverseDiff) {
                reversePropChanges(originalProps, diff);
            }
            String displayPath = new File(myAdminInfo.getAnchor().getRoot(), myCurrentDirectory.myPath).getAbsolutePath();
            displayPath = displayPath.replace(File.separatorChar, '/');
            myDiffGenerator.displayPropDiff(displayPath, originalProps, diff, myResult);
            myCurrentDirectory.myComparedEntries.add("");
        }
        if (!myCurrentDirectory.myIsAdded) {
            localDirectoryDiff(myCurrentDirectory, false, myResult);
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
        myCurrentFile = createFileInfo(path, true);
        myCurrentDirectory.myComparedEntries.add(name);
    }

    public void openFile(String path, long revision) throws SVNException {
        String name = SVNPathUtil.tail(path);
        myCurrentFile = createFileInfo(path, false);
        myCurrentDirectory.myComparedEntries.add(name);
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
        if (name.startsWith(SVNProperty.SVN_WC_PREFIX) || name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            return;
        }
        if (myCurrentFile.myPropertyDiff == null) {
            myCurrentFile.myPropertyDiff = new HashMap();
        }
        myCurrentFile.myPropertyDiff.put(name, value);
        SVNDebugLog.getDefaultLog().info("about to set base properties: " + myCurrentFile.myBaseProperties);
        if (myCurrentFile.myBaseProperties == null) {
            File dirPath = new File(myAdminInfo.getAnchor().getRoot(), myCurrentDirectory.myPath);
            SVNDebugLog.getDefaultLog().info("dirPath: " + dirPath);
            SVNAdminArea dir = retrieve(dirPath);
            String fileName = SVNPathUtil.tail(myCurrentFile.myPath);
            if (dir != null) {
                myCurrentFile.myBaseProperties = dir.getBaseProperties(fileName).asMap();
            } else {
                myCurrentFile.myBaseProperties = new HashMap();
            }
            SVNDebugLog.getDefaultLog().info("base properties set: " + myCurrentFile.myBaseProperties);
        }
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        File dirPath = new File(myAdminInfo.getAnchor().getRoot(), myCurrentDirectory.myPath);
        SVNAdminArea dir = retrieve(dirPath);
        String fileName = SVNPathUtil.tail(myCurrentFile.myPath);
        if (dir != null) {
            SVNEntry2 entry = dir.getEntry(fileName, true);
            if (entry != null && entry.getCopyFromURL() != null) {
                myCurrentFile.myIsAdded = false;
            }
        }
        File tmpFile = null;
        if (!myCurrentFile.myIsAdded) {
            tmpFile = dir.getBaseFile(fileName, true);
            myCurrentFile.myBaseFile = dir.getBaseFile(fileName, false);
        } else {
            // iterate till existing dir and get tmp file in it.
            SVNDirectoryInfo info = myCurrentDirectory.myParent;
            while (info != null) {
                File parentDirPath = new File(myAdminInfo.getAnchor().getRoot(), info.myPath);
                SVNAdminArea parentDir = retrieve(parentDirPath);
                if (parentDir != null) {
                    String tmpPath = SVNAdminUtil.getTextBasePath(fileName, true);
                    tmpFile = parentDir.getFile(tmpPath);
                    tmpFile = tmpFile.getParentFile();
                    tmpFile = SVNFileUtil.createUniqueFile(tmpFile, fileName, ".tmp");
                    myCurrentFile.myBaseFile = null;
                    if (parentDir.getAdminDirectory().exists()) {
                        break;
                    }
                }
                info = info.myParent;
            }
        }
        // it will be repos file.
        myCurrentFile.myFile = tmpFile;
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
        File dirPath = new File(myAdminInfo.getAnchor().getRoot(), myCurrentDirectory.myPath);
        String displayPath = new File(myAdminInfo.getAnchor().getRoot(), myCurrentFile.myPath).getAbsolutePath().replace(File.separatorChar, '/');
        SVNAdminArea dir = retrieve(dirPath);
        SVNEntry2 entry = myWCAccess.getEntry(new File(dirPath, fileName), false);
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
                myDiffGenerator.displayFileDiff(displayPath, null, reposFile, "(revision 0)", "(revision " + entry.getRevision() + ")", null, reposMimeType, myResult);
                myDiffGenerator.displayPropDiff(displayPath, new HashMap(), myCurrentFile.myPropertyDiff, myResult);
            } else {
                myDiffGenerator.displayFileDiff(displayPath, reposFile, null, "(revision " + myTargetRevision + ")", null, reposMimeType, null, myResult);
                myDiffGenerator.displayPropDiff(displayPath, new HashMap(), myCurrentFile.myPropertyDiff, myResult);
            }
            SVNFileUtil.deleteFile(myCurrentFile.myFile);
            return;
        }
        boolean modified = myCurrentFile.myFile != null;
        if (!modified && !myIsCompareToBase) {
            modified = dir.hasTextModifications(fileName, false);
        }
        File tmpFile = null;
        if (modified) {
            if (myIsCompareToBase) {
                localFile = dir.getBaseFile(fileName, false);
            } else {
                localFile = SVNFileUtil.createUniqueFile(myCurrentFile.myFile.getParentFile(), fileName,  ".tmp");
                String path = SVNFileUtil.getBasePath(localFile);
                SVNTranslator2.translate(dir, fileName, fileName, path, true, false);
                tmpFile = localFile;
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
            if (localFile != null || reposFile != null) {
                myDiffGenerator.displayFileDiff(displayPath, 
                        myIsReverseDiff ? localFile : reposFile, 
                        myIsReverseDiff ? reposFile : localFile, 
                        myIsReverseDiff ? null : "(revision " + myTargetRevision + ")",
                        myIsReverseDiff ? "(revision " + myTargetRevision + ")": null,
                        myIsReverseDiff ? originalMimeType : reposMimeType, 
                        myIsReverseDiff ? reposMimeType : originalMimeType, 
                        myResult);
            }
            // add those in base that missing in diff as deleted?
            if (originalProps != null && myCurrentFile.myPropertyDiff != null) {
                for(Iterator originalNames = originalProps.keySet().iterator(); originalNames.hasNext();) {
                    String name = (String) originalNames.next();
                    if (!myCurrentFile.myPropertyDiff.containsKey(name)) {
                        myCurrentFile.myPropertyDiff.put(name, null);
                    }
                }
            }
            myDiffGenerator.displayPropDiff(displayPath, originalProps, myCurrentFile.myPropertyDiff, myResult);
        }
        SVNFileUtil.deleteFile(myCurrentFile.myFile);
        SVNFileUtil.deleteFile(tmpFile);
        
        /*
        if (reposMimeType == null) {
            if (myCurrentFile.myBaseProperties == null) {
                myCurrentFile.myBaseProperties = dir != null ? dir.getBaseProperties(fileName).asMap() : new HashMap();
            }
            reposMimeType = (String) myCurrentFile.myBaseProperties.get(SVNProperty.MIME_TYPE);
        }
        
        SVNEntry2 entry = null;
        if (dir != null) {
            entry = dir.getEntry(fileName, true);
        }
        String displayPath = new File(myAdminInfo.getAnchor().getRoot(), myCurrentFile.myPath).getAbsolutePath().replace(File.separatorChar, '/');
        if (myCurrentFile.myIsAdded) {
            if (myIsReverseDiff) {
                // empty->repos
                String revStr = entry != null ? "(revision " + entry.getRevision() + ")" : null;
                myDiffGenerator.displayFileDiff(displayPath,
                        myCurrentFile.myBaseFile, myCurrentFile.myFile,
                        "(revision 0)", revStr, null, reposMimeType, myResult);
            } else {
                // repos->empty
                String revStr = "(revision " + myTargetRevision + ")";
                myDiffGenerator.displayFileDiff(displayPath,
                        myCurrentFile.myFile, null, revStr, null,
                        reposMimeType, null, myResult);
            }
        } else {
            if (myCurrentFile.myFile == null && !myIsCompareToBase) {
                // use base file?
                if (dir.hasTextModifications(fileName, false)) {
                    myCurrentFile.myFile = dir.getBaseFile(fileName, false);
                }
                SVNDebugLog.getDefaultLog().info("using base as a file from repo: " + myCurrentFile.myFile);
            }
            if (myCurrentFile.myFile != null) {
                String wcMimeType = dir.getProperties(fileName).getPropertyValue(SVNProperty.MIME_TYPE);
                if (!myIsCompareToBase && myCurrentFile.myIsScheduledForDeletion) {
                    myCurrentFile.myBaseFile = null;
                } else if (!myIsCompareToBase) {
                    File wcTmpFile = SVNFileUtil.createUniqueFile(myCurrentFile.myFile.getParentFile(), fileName,  ".tmp");
                    String path = SVNFileUtil.getBasePath(wcTmpFile);
                    // unexpand working to tmp.
                    SVNTranslator2.translate(dir, fileName, fileName, path, true, false);
                    myCurrentFile.myBaseFile = wcTmpFile;
                }
                SVNDebugLog.getDefaultLog().info("file: " + myCurrentFile.myFile);
                SVNDebugLog.getDefaultLog().info("base: " + myCurrentFile.myBaseFile);
                String revStr = "(revision " + myTargetRevision + ")";
                if (myIsReverseDiff) {
                    myDiffGenerator.displayFileDiff(displayPath,
                            myCurrentFile.myBaseFile, myCurrentFile.myFile,
                            null, revStr, wcMimeType, reposMimeType, myResult);
                } else {
                    myDiffGenerator.displayFileDiff(displayPath,
                            myCurrentFile.myFile, myCurrentFile.myBaseFile,
                            revStr, null, reposMimeType, wcMimeType, myResult);
                }
                if (myCurrentFile.myBaseFile != null
                        && !myCurrentFile.myIsScheduledForDeletion
                        && !myIsCompareToBase
                        && !fileName.equals(SVNFileUtil.getBasePath(myCurrentFile.myBaseFile))) {
                    myCurrentFile.myBaseFile.delete();
                }
                myCurrentFile.myFile.delete();
            }
            if (myCurrentFile.myPropertyDiff != null  && !myCurrentFile.myPropertyDiff.isEmpty()) {
                Map base = myCurrentFile.myBaseProperties;
                Map diff = myCurrentFile.myPropertyDiff;
                if (!myIsReverseDiff) {
                    reversePropChanges(base, diff);
                }
                myDiffGenerator.displayPropDiff(displayPath, base, diff, myResult);
            }
        }
        if (myCurrentFile.myFile != null) {
            myCurrentFile.myFile.delete();
        }*/
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (!myIsRootOpen) {
            localDirectoryDiff(createDirInfo(null, "", false), false, myResult);
        }
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    private void localDirectoryDiff(SVNDirectoryInfo info, boolean isAdded,  OutputStream result) throws SVNException {
        if (myIsCompareToBase) {
            return;
        }
        File dirPath = new File(myAdminInfo.getAnchor().getRoot(), info.myPath);
        SVNAdminArea dir = retrieve(dirPath);
        boolean anchor = !"".equals(myAdminInfo.getTargetName()) && dir == myAdminInfo.getAnchor();

        if (!anchor && !info.myComparedEntries.contains("")) {
            // generate prop diff for dir.
            if (dir.hasPropModifications("")) {
                SVNVersionedProperties baseProps = dir.getBaseProperties("");
                Map propDiff = baseProps.compareTo(dir.getProperties("")).asMap();
                String displayPath = dir.getRoot().getAbsolutePath().replace(File.separatorChar, '/');
                myDiffGenerator.displayPropDiff(displayPath, baseProps.asMap(),  propDiff, result);
            }
        }
        Set processedFiles = null;
        if (myDiffGenerator.isDiffUnversioned()) {
            processedFiles = new HashSet();
        }
        for (Iterator entries = dir.entries(false); entries.hasNext();) {
            SVNEntry2 entry = (SVNEntry2) entries.next();
            if (processedFiles != null && !"".equals(entry.getName())) {
                processedFiles.add(entry.getName());
            }
            if (anchor && !myAdminInfo.getTargetName().equals(entry.getName())) {
                continue;
            }
            if ("".equals(entry.getName())) {
                continue;
            }
            if (info.myComparedEntries.contains(entry.getName())) {
                continue;
            }
            info.myComparedEntries.add(entry.getName());
            if (entry.isDirectory()) {
                // recurse here.
                SVNDirectoryInfo childInfo = createDirInfo(info, SVNPathUtil.append(info.myPath, entry.getName()), false);
                File childDirPath = new File(myAdminInfo.getAnchor().getRoot(), childInfo.myPath); 
                SVNAdminArea childDir = retrieve(childDirPath);
                if (childDir != null) {
                    localDirectoryDiff(childInfo, isAdded, myResult);
                }
                continue;
            }
            String name = entry.getName();
            boolean added = entry.isScheduledForAddition() || isAdded;
            boolean replaced = entry.isScheduledForReplacement();
            boolean deleted = entry.isScheduledForDeletion();
            boolean copied = entry.isCopied();
            if (copied) {
                added = false;
                deleted = false;
                replaced = false;
            }
            if (replaced && !myUseAncestry) {
                replaced = false;
            }
            SVNVersionedProperties props = dir.getProperties(name);
            String fullPath = dir.getFile(name).getAbsolutePath().replace(File.separatorChar, '/');
            Map baseProps = dir.getBaseProperties(name).asMap();
            Map propDiff = null;
            if (!deleted && dir.hasPropModifications(name)) {
                propDiff = dir.getBaseProperties(name).compareTo(dir.getProperties(name)).asMap();
            }
            if (deleted || replaced) {
                // display text diff for deleted file.
                String mimeType1 = (String) baseProps.get(SVNProperty.MIME_TYPE);
                String rev1 = "(revision " + Long.toString(entry.getRevision()) + ")";
                myDiffGenerator.displayFileDiff(fullPath, dir.getBaseFile(name,
                        false), null, rev1, null, mimeType1, null, result);
                if (deleted) {
                    continue;
                }
            }

            File tmpFile = null;
            try {
                if (added || replaced) {
                    tmpFile = dir.getBaseFile(name, true);
                    SVNTranslator2.translate(dir, name, name, SVNFileUtil.getBasePath(tmpFile), false, false);
                    // display text diff for added file.

                    String mimeType1 = null;
                    String mimeType2 = props.getPropertyValue(SVNProperty.MIME_TYPE);
                    String rev2 = "(revision " + Long.toString(entry.getRevision()) + ")";
                    String rev1 = "(revision 0)";

                    myDiffGenerator.displayFileDiff(fullPath, null, tmpFile,
                            rev1, rev2, mimeType1, mimeType2, result);
                    if (propDiff != null && propDiff.size() > 0) {
                        // display prop diff.
                        myDiffGenerator.displayPropDiff(fullPath, baseProps, propDiff, result);
                    }
                    continue;
                }
                boolean isTextModified = dir.hasTextModifications(name, false);
                if (isTextModified) {
                    tmpFile = dir.getBaseFile(name, true);
                    SVNTranslator2.translate(dir, name, name, SVNFileUtil.getBasePath(tmpFile), false, false);

                    String mimeType1 = (String) baseProps.get(SVNProperty.MIME_TYPE);
                    String mimeType2 = props.getPropertyValue(SVNProperty.MIME_TYPE);
                    String rev1 = "(revision " + Long.toString(entry.getRevision()) + ")";
                    myDiffGenerator.displayFileDiff(fullPath, dir.getBaseFile(
                            name, false), tmpFile, rev1, null, mimeType1,
                            mimeType2, result);
                }
	            if (propDiff != null && propDiff.size() > 0) {
	                myDiffGenerator.displayPropDiff(fullPath, baseProps,
	                        propDiff, result);
	            }
            } finally {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
            }
        }
        if (myDiffGenerator.isDiffUnversioned()) {
            diffUnversioned(result, dir.getRoot(), dir, anchor, processedFiles);
        }
    }

    private void diffUnversioned(OutputStream result, File root, SVNAdminArea dir, boolean anchor, Set processedFiles) throws SVNException {
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
                diffUnversioned(result, file, null, false, null);
            } else if (fileType == SVNFileType.FILE) {
                String mimeType1 = null;
                String mimeType2 = SVNFileUtil.detectMimeType(file);
                String rev1 = "";
                String rev2 = "";

                String fullPath = file.getAbsolutePath().replace(File.separatorChar, '/');
                myDiffGenerator.displayFileDiff(fullPath, null, file, rev1, rev2, mimeType1, mimeType2, result);
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

    private SVNFileInfo createFileInfo(String path, boolean added) {
        SVNFileInfo info = new SVNFileInfo();
        info.myPath = path;
        info.myIsAdded = added;
        return info;
    }
    
    private SVNAdminArea retrieve(File path) throws SVNException {
        try {
            return myWCAccess.retrieve(path);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_LOCKED &&
                    e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                throw e;
            }            
        }
        return null;
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
        private Map myBaseProperties;
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
