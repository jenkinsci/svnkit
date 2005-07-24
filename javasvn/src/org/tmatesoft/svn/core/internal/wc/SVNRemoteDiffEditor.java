/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.ISVNRAData;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNRAFileData;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNRemoteDiffEditor implements ISVNEditor {

    private File myRoot;
    private SVNRepository myRepos;
    private long myRevision;
    private ISVNDiffGenerator myDiffGenerator;
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private OutputStream myResult;
    private String myRevision1;
    private String myRevision2;
    private String myBasePath;

    public SVNRemoteDiffEditor(String basePath, File tmpRoot, ISVNDiffGenerator diffGenerator,
            SVNRepository repos, long revision, OutputStream result) {
        myBasePath = basePath;
        myRoot = tmpRoot;
        myRepos = repos;
        myRevision = revision;
        myDiffGenerator = diffGenerator;
        myResult = result;
        myRevision1 = "(revision " + revision + ")";
    }

    public void targetRevision(long revision) throws SVNException {
        myRevision2 = "(revision " + revision + ")";
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(null, "");

        myCurrentDirectory.myBaseProperties = new HashMap();
        myRepos.getDir("", myRevision, myCurrentDirectory.myBaseProperties,
                (Collection) null);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        SVNNodeKind nodeKind = myRepos.checkPath(path, myRevision);
        // fire file deleted or dir deleted.
        if (nodeKind == SVNNodeKind.FILE) {
            String name = SVNPathUtil.tail(path);
            File tmpFile = SVNFileUtil.createUniqueFile(myRoot, name, ".tmp");
            SVNFileInfo info = new SVNFileInfo(path);
            try {
                info.loadFromRepository(tmpFile, myRepos, myRevision);
                String mimeType = (String) info.myBaseProperties
                        .get(SVNProperty.MIME_TYPE);
                String displayPath = SVNPathUtil.append(myBasePath, path);
                myDiffGenerator.displayFileDiff(displayPath, tmpFile, null,
                        myRevision1, myRevision2, mimeType, mimeType, myResult);
            } finally {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
            }
        }
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path);
        myCurrentDirectory.myBaseProperties = Collections.EMPTY_MAP;
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path);

        myCurrentDirectory.myBaseProperties = new HashMap();
        myRepos.getDir(path, myRevision, myCurrentDirectory.myBaseProperties,
                (Collection) null);
    }

    public void changeDirProperty(String name, String value)
            throws SVNException {
        if (name == null || name.startsWith(SVNProperty.SVN_WC_PREFIX)
                || name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            return;
        }
        if (myCurrentDirectory.myPropertyDiff == null) {
            myCurrentDirectory.myPropertyDiff = new HashMap();
        }
        myCurrentDirectory.myPropertyDiff.put(name, value);
    }

    public void closeDir() throws SVNException {
        if (myCurrentDirectory.myPropertyDiff != null) {
            String displayPath = SVNPathUtil.append(myBasePath, myCurrentDirectory.myPath);
            myDiffGenerator.displayPropDiff(displayPath,
                    myCurrentDirectory.myBaseProperties,
                    myCurrentDirectory.myPropertyDiff, myResult);
        }
        myCurrentDirectory = myCurrentDirectory.myParent;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        myCurrentFile = new SVNFileInfo(path);
        myCurrentFile.myBaseProperties = Collections.EMPTY_MAP;
        myCurrentFile.myBaseFile = SVNFileUtil.createUniqueFile(myRoot,
                SVNPathUtil.tail(path), ".tmp");
        SVNFileUtil.createEmptyFile(myCurrentFile.myBaseFile);
        myCurrentFile.myFile = SVNFileUtil.createUniqueFile(myRoot, SVNPathUtil
                .tail(path), ".tmp");
        SVNFileUtil.createEmptyFile(myCurrentFile.myFile);
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFile = new SVNFileInfo(path);
        myCurrentFile.myBaseFile = SVNFileUtil.createUniqueFile(myRoot,
                SVNPathUtil.tail(path), ".tmp");

        myCurrentFile.loadFromRepository(myCurrentFile.myBaseFile, myRepos,
                myRevision);
        myCurrentFile.myFile = SVNFileUtil.createUniqueFile(myRoot, SVNPathUtil
                .tail(path), ".tmp");
        SVNFileUtil.createEmptyFile(myCurrentFile.myFile);
    }

    public void changeFileProperty(String commitPath, String name, String value)
            throws SVNException {
        if (name == null || name.startsWith(SVNProperty.SVN_WC_PREFIX)
                || name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            return;
        }
        if (myCurrentFile.myPropertyDiff == null) {
            myCurrentFile.myPropertyDiff = new HashMap();
        }
        myCurrentFile.myPropertyDiff.put(name, value);
    }

    public void applyTextDelta(String commitPath, String baseChecksum)
            throws SVNException {
        myCurrentFile.myDiffWindows = new ArrayList();
        myCurrentFile.myDataFiles = new ArrayList();
    }

    public OutputStream textDeltaChunk(String commitPath,
            SVNDiffWindow diffWindow) throws SVNException {
        myCurrentFile.myDiffWindows.add(diffWindow);
        File chunkFile = SVNFileUtil.createUniqueFile(myRoot, SVNPathUtil
                .tail(myCurrentFile.myPath), ".chunk");
        myCurrentFile.myDataFiles.add(chunkFile);
        return SVNFileUtil.openFileForWriting(chunkFile);
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        File baseTmpFile = myCurrentFile.myBaseFile;
        File targetFile = myCurrentFile.myFile;
        ISVNRAData baseData = new SVNRAFileData(baseTmpFile, true);
        ISVNRAData target = new SVNRAFileData(targetFile, false);
        for (int i = 0; i < myCurrentFile.myDiffWindows.size(); i++) {
            SVNDiffWindow window = (SVNDiffWindow) myCurrentFile.myDiffWindows
                    .get(i);
            File dataFile = (File) myCurrentFile.myDataFiles.get(i);
            InputStream data = SVNFileUtil.openFileForReading(dataFile);
            try {
                window.apply(baseData, target, data, target.length());
            } finally {
                SVNFileUtil.closeFile(data);
            }
            dataFile.delete();
        }
        try {
            target.close();
            baseData.close();
        } catch (IOException e) {
            SVNErrorManager.error("svn: Cannot apply delta to '" + targetFile + "'");
        }
    }

    public void closeFile(String commitPath, String textChecksum)
            throws SVNException {
        String displayPath = SVNPathUtil.append(myBasePath, myCurrentFile.myPath);
        if (myCurrentFile.myFile != null) {
            String mimeType1 = (String) myCurrentFile.myBaseProperties
                    .get(SVNProperty.MIME_TYPE);
            String mimeType2 = myCurrentFile.myPropertyDiff != null ? (String) myCurrentFile.myPropertyDiff
                    .get(SVNProperty.MIME_TYPE)
                    : null;
            if (mimeType2 == null) {
                mimeType2 = mimeType1;
            }
            myDiffGenerator.displayFileDiff(displayPath,
                    myCurrentFile.myBaseFile, myCurrentFile.myFile,
                    myRevision1, myRevision2, mimeType1, mimeType2, myResult);
        }
        if (myCurrentFile.myPropertyDiff != null) {
            myDiffGenerator.displayPropDiff(displayPath,
                    myCurrentFile.myBaseProperties,
                    myCurrentFile.myPropertyDiff, myResult);
        }
        if (myCurrentFile.myFile != null) {
            myCurrentFile.myFile.delete();
        }
        if (myCurrentFile.myBaseFile != null) {
            myCurrentFile.myBaseFile.delete();
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    private static class SVNDirectoryInfo {

        public SVNDirectoryInfo(SVNDirectoryInfo parent, String path) {
            myParent = parent;
            myPath = path;
        }

        private String myPath;

        private Map myBaseProperties;

        private Map myPropertyDiff;

        private SVNDirectoryInfo myParent;
    }

    private static class SVNFileInfo {

        public SVNFileInfo(String path) {
            myPath = path;
        }

        public void loadFromRepository(File dst, SVNRepository repos,
                long revision) throws SVNException {
            OutputStream os = SVNFileUtil.openFileForWriting(dst);
            try {
                myBaseProperties = new HashMap();
                repos.getFile(myPath, revision, myBaseProperties, os);
            } finally {
                SVNFileUtil.closeFile(os);
            }
        }

        private String myPath;

        private File myFile;

        private File myBaseFile;

        private Map myBaseProperties;

        private Map myPropertyDiff;

        private List myDiffWindows;

        private List myDataFiles;
    }
}
