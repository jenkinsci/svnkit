/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal;

import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.diff.ISVNRAData;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.ws.fs.SVNRAFileData;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.util.TimeUtil;
import org.tmatesoft.svn.util.DebugLog;

import java.io.OutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Collection;
import java.util.Date;

import de.regnis.q.sequence.line.QSequenceLineResult;
import de.regnis.q.sequence.line.QSequenceLineMedia;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;
import de.regnis.q.sequence.QSequenceDifferenceBlock;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNAnnotationGenerator implements ISVNFileRevisionHandler {

    private File myTmpDirectory;
    private long myStartRevision;
    private String myPath;

    private long myCurrentRevision;
    private String myCurrentAuthor;
    private Date myCurrentDate;
    private Collection myCurrentDiffWindows;

    private File myPreviousFile;
    private File myCurrentFile;
    private Map myCurrentDiffWindowsMap;

    private List myLines;

    public SVNAnnotationGenerator(String path, long startRevision, File tmpDirectory) {
        myTmpDirectory = tmpDirectory;
        myPath = path;
        myStartRevision = startRevision;
        if (!myTmpDirectory.isDirectory()) {
            myTmpDirectory.mkdirs();
        }
        myLines = new ArrayList();
    }

    public void handleFileRevision(SVNFileRevision fileRevision) throws SVNException {

        Map propDiff = fileRevision.getPropertiesDelta();
        String newMimeType = (String) (propDiff != null ? propDiff.get(SVNProperty.MIME_TYPE) : null);
        if (SVNWCUtil.isBinaryMimetype(newMimeType)) {
            SVNErrorManager.error("svn: Cannot calculate blame information for binary file '" + myPath + "'");
        }
        myCurrentDiffWindows = null;
        myCurrentRevision = fileRevision.getRevision();
        Map props = fileRevision.getProperties();
        if (props != null && props.get("svn:author") != null) {
            myCurrentAuthor = props.get("svn:author").toString();
        } else {
            myCurrentAuthor = null;
        }
        if (props != null && props.get("svn:date") != null) {
            myCurrentDate = TimeUtil.parseDate(fileRevision.getProperties().get("svn:date").toString());
        } else {
            myCurrentDate = null;
        }
        if (myCurrentFile != null) {
            myCurrentFile.delete();
        }
        myCurrentFile = null;

        if (myPreviousFile == null) {
            // create previous file.
            myPreviousFile = SVNFileUtil.createUniqueFile(myTmpDirectory, "annotate", ".tmp");
            SVNFileUtil.createEmptyFile(myPreviousFile);
            DebugLog.log("first base file created: " + myPreviousFile);
        }
    }

    public OutputStream handleDiffWindow(String token, SVNDiffWindow diffWindow) throws SVNException {
        if (myCurrentDiffWindows == null) {
            myCurrentDiffWindows = new ArrayList();
            myCurrentDiffWindowsMap = new HashMap();
        }
        if (myCurrentFile == null) {
            // create file for current revision.
            myCurrentFile = SVNFileUtil.createUniqueFile(myTmpDirectory, "annotate", ".tmp");
            SVNFileUtil.createEmptyFile(myCurrentFile);
            DebugLog.log("tmp file for revision '" + myCurrentRevision + "' created: " + myCurrentFile);
        }
        File tmpFile = SVNFileUtil.createUniqueFile(myTmpDirectory, "annotate", ".tmp");

        myCurrentDiffWindows.add(diffWindow);
        myCurrentDiffWindowsMap.put(diffWindow, tmpFile);

        return SVNFileUtil.openFileForWriting(tmpFile);
    }

    public void handleDiffWindowClosed(String token) throws SVNException {
        if (myCurrentDiffWindows == null) {
            // nothing changed, delete current file.
            if (myCurrentFile != null) {
                myCurrentFile.delete();
            }
            myCurrentDiffWindows = null;
            myCurrentDiffWindowsMap = null;
            return;
        }
        // apply windows to base file, save to current
        ISVNRAData tmpFile = new SVNRAFileData(myCurrentFile, false);
        ISVNRAData baseFile = new SVNRAFileData(myPreviousFile, false);

        try {
            for (Iterator windows = myCurrentDiffWindows.iterator(); windows.hasNext();) {
                SVNDiffWindow window = (SVNDiffWindow) windows.next();
                File chunkFile = (File) myCurrentDiffWindowsMap.get(window);
                try {
                    InputStream chunkData = SVNFileUtil.openFileForReading(chunkFile);
                    window.apply(baseFile, tmpFile, chunkData, tmpFile.length());
                } finally {
                    chunkFile.delete();
                }
            }
        } finally {
            myCurrentDiffWindows = null;
            myCurrentDiffWindowsMap = null;
            try {
                baseFile.close();
            } catch (IOException e) {
                //
            }
            try {
                tmpFile.close();
            } catch (IOException e) {
                //
            }
        }
        if (myCurrentRevision >= myStartRevision) {
            // compute lines info.
            RandomAccessFile left = null;
            RandomAccessFile right = null;
            try {
                left = new RandomAccessFile(myPreviousFile, "r");
                right = new RandomAccessFile(myCurrentFile, "r");

                ArrayList newLines = new ArrayList();
                int lastStart = 0;

                final QSequenceLineResult result = QSequenceLineMedia.createBlocks(new QSequenceLineRAFileData(left), new QSequenceLineRAFileData(right), new byte[0]);
                try {
                    List blocksList = result.getBlocks();
                    for(int i = 0; i < blocksList.size(); i++) {
                        QSequenceDifferenceBlock block = (QSequenceDifferenceBlock) blocksList.get(i);
                        int start = block.getLeftFrom();
                        for(int j = lastStart; j < Math.min(myLines.size(), start); j++) {
                            newLines.add(myLines.get(j));
                            lastStart = j + 1;
                        }
                        // skip it.
                        if (block.getLeftSize() > 0) {
                            lastStart += block.getLeftSize();
                        }
                        // copy all from right.
                        for (int j = block.getRightFrom(); j <= block.getRightTo(); j++) {
                            LineInfo line = new LineInfo();
                            line.revision = myCurrentRevision;
                            line.author = myCurrentAuthor;
                            line.line = result.getRightCache().getLine(j).getBytes();
                            line.date = myCurrentDate;
                            newLines.add(line);
                        }
                    }
                    for(int j = lastStart; j < myLines.size(); j++) {
                        newLines.add(myLines.get(j));
                    }
                    myLines = newLines;
                }
                finally {
                    result.close();
                }
            } catch (Throwable e) {
                DebugLog.error(e);
            } finally {
                if (left != null) {
                    try {
                        left.close();
                    } catch (IOException e) {
                        //
                    }
                }
                if (right != null) {
                    try {
                        right.close();
                    } catch (IOException e) {
                        //
                    }
                }
            }

        }

        SVNFileUtil.rename(myCurrentFile, myPreviousFile);
    }

    public void reportAnnotations(ISVNAnnotateHandler handler, String inputEncoding) {
        if (myLines == null || handler == null) {
            return;
        }
        inputEncoding = inputEncoding == null ? System.getProperty("file.encoding") : inputEncoding;
        for(int i = 0; i < myLines.size(); i++) {
            LineInfo info = (LineInfo) myLines.get(i);
            String lineAsString;
            byte[] bytes = info.line;
            int length = bytes.length;
            if (bytes.length >=2 && bytes[length - 2] == '\r' && bytes[length - 1] == '\n') {
                length -= 2;
            } else if (bytes.length >= 1 && (bytes[length - 1] == '\r' || bytes[length - 1] == '\n')) {
                length -= 1;
            } 
            try {
                lineAsString = new String(info.line, 0, length, inputEncoding);
            } catch (UnsupportedEncodingException e) {
                lineAsString = new String(info.line, 0, length);
            }
            handler.handleLine(info.date, info.revision, info.author, lineAsString);
        }
    }

    public void dispose() {
        myLines = null;
        if (myCurrentFile != null) {
            myCurrentFile.delete();
        }
        if (myPreviousFile != null) {
            myPreviousFile.delete();
        }
    }

    private static class LineInfo {
        public byte[] line;
        public long revision;
        public String author;
        public Date date;
    }
}
