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
package org.tmatesoft.svn.core;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNDeltaProcessor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.util.SVNDebugLog;

import de.regnis.q.sequence.QSequenceDifferenceBlock;
import de.regnis.q.sequence.line.QSequenceLineMedia;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;
import de.regnis.q.sequence.line.QSequenceLineResult;


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

    private File myPreviousFile;
    private File myCurrentFile;

    private List myLines;
    private SVNDeltaProcessor myDeltaProcessor;
    private ISVNEventHandler myCancelBaton;

    public SVNAnnotationGenerator(String path, long startRevision, File tmpDirectory, ISVNEventHandler cancelBaton) {
        myTmpDirectory = tmpDirectory;
        myCancelBaton = cancelBaton;
        myPath = path;
        myStartRevision = startRevision;
        if (!myTmpDirectory.isDirectory()) {
            myTmpDirectory.mkdirs();
        }
        myLines = new ArrayList();
        myDeltaProcessor = new SVNDeltaProcessor();
    }
    
    /**
     * @param  fileRevision
     * @throws SVNException
     */
    public void handleFileRevision(SVNFileRevision fileRevision) throws SVNException {
        Map propDiff = fileRevision.getPropertiesDelta();
        String newMimeType = (String) (propDiff != null ? propDiff.get(SVNProperty.MIME_TYPE) : null);
        if (SVNProperty.isBinaryMimeType(newMimeType)) {
            SVNErrorManager.error("svn: Cannot calculate blame information for binary file '" + myPath + "'");
        }
        myCurrentRevision = fileRevision.getRevision();
        if (myCancelBaton != null) {
            SVNEvent event = SVNEventFactory.createAnnotateEvent(myPath, myCurrentRevision);
            myCancelBaton.handleEvent(event, ISVNEventHandler.UNKNOWN);
            myCancelBaton.checkCancelled();
        }
        Map props = fileRevision.getProperties();
        if (props != null && props.get(SVNRevisionProperty.AUTHOR) != null) {
            myCurrentAuthor = props.get(SVNRevisionProperty.AUTHOR).toString();
        } else {
            myCurrentAuthor = null;
        }
        if (props != null && props.get(SVNRevisionProperty.DATE) != null) {
            myCurrentDate = SVNTimeUtil.parseDate(fileRevision.getProperties().get(SVNRevisionProperty.DATE).toString());
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
        }
    }

    public OutputStream handleDiffWindow(String token, SVNDiffWindow diffWindow) throws SVNException {
        if (myCurrentFile == null) {
            myCurrentFile = SVNFileUtil.createUniqueFile(myTmpDirectory, "annotate", ".tmp");
            SVNFileUtil.createEmptyFile(myCurrentFile);
        }        
        File tmpFile = SVNFileUtil.createUniqueFile(myTmpDirectory, "annotate", ".tmp");
        return myDeltaProcessor.textDeltaChunk(tmpFile, diffWindow);
    }

    public void handleDiffWindowClosed(String token) throws SVNException {
        if (!myDeltaProcessor.textDeltaEnd(myPreviousFile, myCurrentFile, false)) {
            return;
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

                final QSequenceLineResult result = QSequenceLineMedia.createBlocks(new QSequenceLineRAFileData(left), new QSequenceLineRAFileData(right), null);
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
                SVNDebugLog.logInfo(e);
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
