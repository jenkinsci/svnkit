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
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
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
    private ISVNOptions myOptions;
    
    private SVNDeltaProcessor myDeltaProcessor;

    public SVNExportEditor(ISVNEventHandler eventDispatcher, String url,
            File dstPath, boolean force, String eolStyle, ISVNOptions options) {
        myRoot = dstPath;
        myIsForce = force;
        myEOLStyle = eolStyle;
        myExternals = new HashMap();
        myEventDispatcher = eventDispatcher;
        myURL = url;
        myDeltaProcessor = new SVNDeltaProcessor();
        myOptions = options;
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
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' exists and is not a directory", myCurrentDirectory);
                SVNErrorManager.error(err);
            } else {
                SVNFileUtil.deleteAll(myCurrentDirectory, myEventDispatcher);
            }
        } else if (dirType == SVNFileType.DIRECTORY && !myIsForce) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "''{0}'' already exists", myCurrentDirectory);
            SVNErrorManager.error(err);
        } else if (dirType == SVNFileType.NONE) {        
            if (!myCurrentDirectory.mkdirs()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "Cannot create directory ''{0}''", myCurrentDirectory);
                SVNErrorManager.error(err);
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
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "File ''{0}'' already exists", file);
            SVNErrorManager.error(err);
        }
        myCurrentFile = file;
        myFileProperties = new HashMap();
        myChecksum = null;
    }

    public void changeFileProperty(String commitPath, String name, String value)
            throws SVNException {
        myFileProperties.put(name, value);
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        myCurrentTmpFile = SVNFileUtil.createUniqueFile(myCurrentDirectory, ".export", ".tmp");
        myDeltaProcessor.applyTextDelta(null, myCurrentTmpFile, true);
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }
    
    private String myChecksum;

    public void textDeltaEnd(String commitPath) throws SVNException {
        myChecksum = myDeltaProcessor.textDeltaEnd();
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        if (textChecksum == null) {
            textChecksum = (String) myFileProperties.get(SVNProperty.CHECKSUM);
        }
        if (myIsForce) {
            myCurrentFile.delete();
        }
        String realChecksum = myChecksum != null ? myChecksum : SVNFileUtil.computeChecksum(myCurrentTmpFile);
        myChecksum = null;
        if (textChecksum != null && !textChecksum.equals(realChecksum)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                    new Object[] {myCurrentFile, textChecksum, realChecksum}); 
            SVNErrorManager.error(err);
        }
        // retranslate.
        try {
            String date = (String) myFileProperties.get(SVNProperty.COMMITTED_DATE);
            boolean special = myFileProperties.get(SVNProperty.SPECIAL) != null;
            boolean binary = SVNProperty.isBinaryMimeType((String) myFileProperties.get(SVNProperty.MIME_TYPE));
            String keywords = (String) myFileProperties.get(SVNProperty.KEYWORDS);
            Map keywordsMap = null;
            if (keywords != null) {
                String url = SVNPathUtil.append(myURL, SVNEncodingUtil.uriEncode(myCurrentPath));
                url = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(myCurrentFile.getName()));
                String author = (String) myFileProperties.get(SVNProperty.LAST_AUTHOR);
                String revStr = (String) myFileProperties.get(SVNProperty.COMMITTED_REVISION);
                keywordsMap = SVNTranslator.computeKeywords(keywords, url, author, date, revStr, myOptions);
            }
            byte[] eolBytes = null;
            if (SVNProperty.EOL_STYLE_NATIVE.equals(myFileProperties.get(SVNProperty.EOL_STYLE))) {
                eolBytes = SVNTranslator.getWorkingEOL(myEOLStyle != null ? myEOLStyle : (String) myFileProperties.get(SVNProperty.EOL_STYLE));
            } else if (myFileProperties.containsKey(SVNProperty.EOL_STYLE)) {
                eolBytes = SVNTranslator.getWorkingEOL((String) myFileProperties.get(SVNProperty.EOL_STYLE));
            }
            if (binary) {
                // no translation unless 'special'.
                eolBytes = null;
                keywordsMap = null;
            }
            if (eolBytes != null || (keywordsMap != null && !keywordsMap.isEmpty()) || special) {
                SVNTranslator.translate(myCurrentTmpFile, myCurrentFile, eolBytes, keywordsMap, special, true);
            } else {
                SVNFileUtil.rename(myCurrentTmpFile, myCurrentFile);
            }
            boolean executable = myFileProperties.get(SVNProperty.EXECUTABLE) != null;
            if (executable) {
                SVNFileUtil.setExecutable(myCurrentFile, true);
            }
            if (!special && date != null) {
                myCurrentFile.setLastModified(SVNTimeUtil.parseDate(date).getTime());
            }
            myEventDispatcher.handleEvent(SVNEventFactory.createExportAddedEvent(myRoot, myCurrentFile, SVNNodeKind.FILE), ISVNEventHandler.UNKNOWN);
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