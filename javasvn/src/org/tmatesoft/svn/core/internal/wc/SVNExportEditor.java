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
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;

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
    private ISVNEventHandler myEventDispatcher;
    private String myURL;
    
    private SVNDeltaProcessor myDeltaProcessor;

    public SVNExportEditor(ISVNEventHandler eventDispatcher, String url,
            File dstPath, boolean force, String eolStyle) {
        myRoot = dstPath;
        myIsForce = force;
        myEOLStyle = eolStyle;
        myExternals = new HashMap();
        myEventDispatcher = eventDispatcher;
        myURL = url;
        myDeltaProcessor = new SVNDeltaProcessor();
    }

    public Map getCollectedExternals() {
        return myExternals;
    }

    public void openRoot(long revision) throws SVNException {
        // create root if missing or delete (if force).
        addDir("", null, -1);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentDirectory = new File(myRoot, path);
        myCurrentPath = path;

        SVNFileType dirType = SVNFileType.getType(myCurrentDirectory);
        if (dirType == SVNFileType.FILE || dirType == SVNFileType.SYMLINK) {
            // export is obstructed.
            if (!myIsForce) {
                SVNErrorManager.error("svn: Failed to add directory '" + myCurrentDirectory + "': file of the same name already exists; use 'force' to overwrite existing file");
            } else {
                SVNFileUtil.deleteAll(myCurrentDirectory, myEventDispatcher);
            }
        } else if (dirType == SVNFileType.NONE) {
            if (!myCurrentDirectory.mkdirs()) {
                SVNErrorManager.error("svn: Failed to add directory '" + myCurrentDirectory + "'");
            }
        }
        myEventDispatcher.handleEvent(SVNEventFactory.createExportAddedEvent(
                myRoot, myCurrentDirectory, SVNNodeKind.DIR), ISVNEventHandler.UNKNOWN);
    }

    public void changeDirProperty(String name, String value)
            throws SVNException {
        if (SVNProperty.EXTERNALS.equals(name) && value != null) {
            myExternals.put(myCurrentDirectory, value);
        }
    }

    public void closeDir() throws SVNException {
        myCurrentDirectory = myCurrentDirectory.getParentFile();
        myCurrentPath = SVNPathUtil.tail(myCurrentPath);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        File file = new File(myRoot, path);

        if (!myIsForce && file.exists()) {
            SVNErrorManager.error("svn: Failed to add file '" + file + "': file of the same name already exists; use 'force' to overwrite exsiting file");
        }
        myCurrentFile = file;
        myFileProperties = new HashMap();
        myChecksum = null;
    }

    public void changeFileProperty(String commitPath, String name, String value)
            throws SVNException {
        myFileProperties.put(name, value);
    }

    public void applyTextDelta(String commitPath, String baseChecksum)
            throws SVNException {
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        File tmpFile = SVNFileUtil.createUniqueFile(myCurrentDirectory,  myCurrentFile.getName(), ".tmp");
        return myDeltaProcessor.textDeltaChunk(tmpFile, diffWindow);
    }
    
    private String myChecksum;

    public void textDeltaEnd(String commitPath) throws SVNException {
        // apply all deltas
        File fakeBase = SVNFileUtil.createUniqueFile(myCurrentDirectory, myCurrentFile.getName(), ".tmp");
        SVNFileUtil.createEmptyFile(fakeBase);
        myCurrentTmpFile = SVNFileUtil.createUniqueFile(myCurrentDirectory, myCurrentFile.getName(), ".tmp");
        try {
            myChecksum = myDeltaProcessor.textDeltaEnd(fakeBase, myCurrentTmpFile, true);
        } finally {
            fakeBase.delete();
        }
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        myDeltaProcessor.close();
        if (textChecksum == null) {
            textChecksum = (String) myFileProperties.get(SVNProperty.CHECKSUM);
        }
        if (myIsForce) {
            myCurrentFile.delete();
        }
        String realChecksum = myChecksum != null ? myChecksum : SVNFileUtil.computeChecksum(myCurrentTmpFile);
        myChecksum = null;
        if (textChecksum != null
                && !textChecksum.equals(realChecksum)) {
            SVNErrorManager.error("svn: Checksum differs, expected '" + textChecksum + "'; actual: '" + realChecksum + "'");
        }
        // retranslate.
        try {
            String date = (String) myFileProperties
                    .get(SVNProperty.COMMITTED_DATE);
            String keywords = (String) myFileProperties
                    .get(SVNProperty.KEYWORDS);
            Map keywordsMap = null;
            if (keywords != null) {
                String url = SVNPathUtil.append(myURL, SVNEncodingUtil.uriEncode(myCurrentPath));
                url = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(myCurrentFile.getName()));
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
                myCurrentFile.setLastModified(SVNTimeUtil.parseDate(date)
                        .getTime());
            }
            myEventDispatcher.handleEvent(SVNEventFactory
                    .createExportAddedEvent(myRoot, myCurrentFile, SVNNodeKind.FILE),
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