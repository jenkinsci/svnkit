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

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.ws.fs.SVNRAFileData;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNExportEditor implements ISVNEditor {

    private File myRoot;

    private boolean myIsForce;

    private String myEOLStyle;

    private File myCurrentDirectory;

    private File myCurrentFile;

    private File myCurrentTmpFile;

    private String myCurrentPath;

    private Map myExternals;

    private Map myFileProperties;

    private Collection myDiffWindows;

    private Collection myDataFiles;

    private ISVNEventHandler myEventDispatcher;

    private String myURL;

    public SVNExportEditor(ISVNEventHandler eventDispatcher, String url,
            File dstPath, boolean force, String eolStyle) {
        myRoot = dstPath;
        myIsForce = force;
        myEOLStyle = eolStyle;
        myExternals = new HashMap();
        myEventDispatcher = eventDispatcher;
        myURL = url;
    }

    public Map getCollectedExternals() {
        return myExternals;
    }

    public void openRoot(long revision) throws SVNException {
        // create root if missing or delete (if force).
        addDir("", null, -1);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        myCurrentDirectory = new File(myRoot, path);
        myCurrentPath = path;

        if (!myIsForce && myCurrentDirectory.isFile()) {
            // export is obstructed.
            SVNErrorManager.error(0, null);
        } else if (myIsForce && myCurrentDirectory.exists()) {
            SVNFileUtil.deleteAll(myCurrentDirectory);
        }
        if (!myCurrentDirectory.exists()) {
            if (!myCurrentDirectory.mkdirs()) {
                SVNErrorManager.error(0, null);
            }
        }
        myEventDispatcher.handleEvent(SVNEventFactory.createExportAddedEvent(
                myRoot, myCurrentDirectory), ISVNEventHandler.UNKNOWN);
    }

    public void changeDirProperty(String name, String value)
            throws SVNException {
        if (SVNProperty.EXTERNALS.equals(name) && value != null) {
            myExternals.put(myCurrentDirectory, value);
        }
    }

    public void closeDir() throws SVNException {
        myCurrentDirectory = myCurrentDirectory.getParentFile();
        myCurrentPath = PathUtil.tail(myCurrentPath);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        File file = new File(myRoot, path);

        if (!myIsForce && file.exists()) {
            SVNErrorManager.error(0, null);
        }
        myCurrentFile = file;
        myFileProperties = new HashMap();
    }

    public void changeFileProperty(String commitPath, String name, String value)
            throws SVNException {
        myFileProperties.put(name, value);
    }

    public void applyTextDelta(String commitPath, String baseChecksum)
            throws SVNException {
    }

    public OutputStream textDeltaChunk(String commitPath,
            SVNDiffWindow diffWindow) throws SVNException {
        if (myDiffWindows == null) {
            myDiffWindows = new LinkedList();
            myDataFiles = new LinkedList();
        }
        myDiffWindows.add(diffWindow);
        File tmpFile = SVNFileUtil.createUniqueFile(myCurrentDirectory,
                myCurrentFile.getName(), ".tmp");
        myDataFiles.add(tmpFile);

        try {
            return new FileOutputStream(tmpFile);
        } catch (FileNotFoundException e) {
            SVNErrorManager.error(0, e);
        }
        return null;
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        // apply all deltas
        myCurrentTmpFile = SVNFileUtil.createUniqueFile(myCurrentDirectory,
                myCurrentFile.getName(), ".tmp");
        try {
            myCurrentTmpFile.createNewFile();
        } catch (IOException e) {
            myCurrentTmpFile.delete();
            SVNErrorManager.error(0, e);
        }
        SVNRAFileData target = new SVNRAFileData(myCurrentTmpFile, false);
        File fakeBase = SVNFileUtil.createUniqueFile(myCurrentDirectory,
                myCurrentFile.getName(), ".tmp");
        SVNRAFileData base = new SVNRAFileData(fakeBase, true);

        try {
            Iterator windows = myDiffWindows.iterator();
            for (Iterator files = myDataFiles.iterator(); files.hasNext();) {
                File dataFile = (File) files.next();
                SVNDiffWindow window = (SVNDiffWindow) windows.next();
                // apply to tmp file, use 'fake' base.
                InputStream is = SVNFileUtil.openFileForReading(dataFile);
                try {
                    window.apply(base, target, is, target.length());
                } finally {
                    SVNFileUtil.closeFile(is);
                }
            }
        } finally {
            if (target != null) {
                try {
                    target.close();
                } catch (IOException e) {
                }
            }
            if (base != null) {
                try {
                    base.close();
                } catch (IOException e) {
                }
            }
            for (Iterator files = myDataFiles.iterator(); files.hasNext();) {
                File file = (File) files.next();
                file.delete();
            }
            if (myDiffWindows != null) {
                myDataFiles.clear();
                myDiffWindows.clear();
            }
            fakeBase.delete();
        }
    }

    public void closeFile(String commitPath, String textChecksum)
            throws SVNException {
        if (textChecksum == null) {
            textChecksum = (String) myFileProperties.get(SVNProperty.CHECKSUM);
        }
        if (myIsForce) {
            myCurrentFile.delete();
        }
        if (textChecksum != null
                && !textChecksum.equals(SVNFileUtil
                        .computeChecksum(myCurrentTmpFile))) {
            SVNErrorManager.error(0, null);
        }
        // retranslate.
        try {
            String date = (String) myFileProperties
                    .get(SVNProperty.COMMITTED_DATE);
            String keywords = (String) myFileProperties
                    .get(SVNProperty.KEYWORDS);
            Map keywordsMap = null;
            if (keywords != null) {
                String url = PathUtil.append(myURL, PathUtil
                        .encode(myCurrentPath));
                url = PathUtil.append(url, PathUtil.encode(myCurrentFile
                        .getName()));
                String author = (String) myFileProperties
                        .get(SVNProperty.LAST_AUTHOR);
                String revStr = (String) myFileProperties
                        .get(SVNProperty.COMMITTED_REVISION);
                keywordsMap = SVNTranslator.computeKeywords(keywords, url,
                        author, date, revStr);
            }
            String eolStyle = myEOLStyle != null ? myEOLStyle
                    : (String) myFileProperties.get(SVNProperty.EOL_STYLE);
            byte[] eolBytes = SVNTranslator.getWorkingEOL(eolStyle);
            boolean special = myFileProperties.get(SVNProperty.SPECIAL) != null;
            boolean executable = myFileProperties.get(SVNProperty.EXECUTABLE) != null;

            SVNTranslator.translate(myCurrentTmpFile, myCurrentFile, eolBytes,
                    keywordsMap, special, true);
            if (executable) {
                SVNFileUtil.setExecutable(myCurrentFile, true);
            }
            if (!special && date != null) {
                myCurrentFile.setLastModified(TimeUtil.parseDate(date)
                        .getTime());
            }
            myEventDispatcher.handleEvent(SVNEventFactory
                    .createExportAddedEvent(myRoot, myCurrentFile),
                    ISVNEventHandler.UNKNOWN);
        } finally {
            myCurrentTmpFile.delete();
        }

    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void targetRevision(long revision) throws SVNException {
    }

    public void deleteEntry(String path, long revision) throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void openDir(String path, long revision) throws SVNException {
    }

    public void openFile(String path, long revision) throws SVNException {
    }

    public void abortEdit() throws SVNException {
    }
}