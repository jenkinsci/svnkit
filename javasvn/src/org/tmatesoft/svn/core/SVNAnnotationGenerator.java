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
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
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
 * The <b>SVNAnnotationGenerator</b> class is used to annotate files - that is
 * to place author and revision information in-line for the specified
 * file.
 * 
 * <p>
 * Since <b>SVNAnnotationGenerator</b> implements <b>ISVNFileRevisionHandler</b>,
 * it is merely passed to a {@link org.tmatesoft.svn.core.io.SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler) getFileRevisions()} 
 * method of <b>SVNRepository</b>. After that you handle the resultant annotated 
 * file line-by-line providing an <b>ISVNAnnotateHandler</b> implementation to the {@link #reportAnnotations(ISVNAnnotateHandler, String) reportAnnotations()}
 * method:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.SVNAnnotationGenerator;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.io.SVNRepositoryFactory;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.io.SVNRepository;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNAnnotateHandler;
 * ...
 * 
 *     File tmpFile;
 *     SVNRepository repos;
 *     ISVNAnnotateHandler annotateHandler;
 *     ISVNEventHandler cancelHandler;
 *     <span class="javakeyword">long</span> startRev = 0;
 *     <span class="javakeyword">long</span> endRev = 150;
 *     ...
 *     
 *     SVNAnnotationGenerator generator = <span class="javakeyword">new</span> SVNAnnotationGenerator(path, tmpFile, cancelHandler);
 *     <span class="javakeyword">try</span> {
 *         repos.getFileRevisions(<span class="javastring">""</span>, startRev, endRev, generator);
 *         generator.reportAnnotations(annotateHandler, <span class="javakeyword">null</span>);
 *     } <span class="javakeyword">finally</span> {
 *         generator.dispose();
 *     }
 * ...</pre>
 *   
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNAnnotationGenerator implements ISVNFileRevisionHandler {

    private File myTmpDirectory;
    private String myPath;

    private long myCurrentRevision;
    private String myCurrentAuthor;
    private Date myCurrentDate;

    private File myPreviousFile;
    private File myCurrentFile;

    private List myLines;
    private SVNDeltaProcessor myDeltaProcessor;
    private ISVNEventHandler myCancelBaton;
    private long myStartRevision;
    private boolean myIsForce;
    
    /**
     * Constructs an annotation generator object. A user may want to have
     * a chance to interrupt an operation - so, <code>cancelBaton</code>'s
     * {@link org.tmatesoft.svn.core.wc.ISVNEventHandler#checkCancelled()} method
     * is used for this purpose. 
     * 
     * @param path           a file path (relative to a repository location)
     * @param tmpDirectory   a revision to stop at
     * @param startRevision  a start revision to begin annotation with
     * @param cancelBaton    a baton which is used to check if an operation 
     *                       is cancelled
     */
    public SVNAnnotationGenerator(String path, File tmpDirectory, long startRevision, ISVNEventHandler cancelBaton) {
        this(path, tmpDirectory, startRevision, false, cancelBaton);
        
    }
    public SVNAnnotationGenerator(String path, File tmpDirectory, long startRevision, boolean force, ISVNEventHandler cancelBaton) {
        myTmpDirectory = tmpDirectory;
        myCancelBaton = cancelBaton;
        myPath = path;
        myIsForce = force;
        if (!myTmpDirectory.isDirectory()) {
            myTmpDirectory.mkdirs();
        }
        myLines = new ArrayList();
        myDeltaProcessor = new SVNDeltaProcessor();
        myStartRevision = startRevision;
    }
    
    /**
     * 
     * @param fileRevision
     * @throws SVNException if one of the following occurs:
     *                      <ul>
     *                      <li>the file is binary (not text)
     *                      <li>operation is cancelled
     *                      </ul>
     */
    public void openRevision(SVNFileRevision fileRevision) throws SVNException {
        Map propDiff = fileRevision.getPropertiesDelta();
        String newMimeType = (String) (propDiff != null ? propDiff.get(SVNProperty.MIME_TYPE) : null);
        if (!myIsForce && SVNProperty.isBinaryMimeType(newMimeType)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_IS_BINARY_FILE, "Cannot calculate blame information for binary file ''{0}''", myPath);
            SVNErrorManager.error(err);
        }
        myCurrentRevision = fileRevision.getRevision();
        boolean known = fileRevision.getRevision() >= myStartRevision;
        if (myCancelBaton != null) {
            SVNEvent event = SVNEventFactory.createAnnotateEvent(myPath, myCurrentRevision);
            myCancelBaton.handleEvent(event, ISVNEventHandler.UNKNOWN);
            myCancelBaton.checkCancelled();
        }
        Map props = fileRevision.getRevisionProperties();
        if (known && props != null && props.get(SVNRevisionProperty.AUTHOR) != null) {
            myCurrentAuthor = props.get(SVNRevisionProperty.AUTHOR).toString();
        } else {
            myCurrentAuthor = null;
        }
        if (known && props != null && props.get(SVNRevisionProperty.DATE) != null) {
            myCurrentDate = SVNTimeUtil.parseDate(fileRevision.getRevisionProperties().get(SVNRevisionProperty.DATE).toString());
        } else {
            myCurrentDate = null;
        }
        if (myPreviousFile == null) {
            // create previous file.
            myPreviousFile = SVNFileUtil.createUniqueFile(myTmpDirectory, "annotate", ".tmp");
            SVNFileUtil.createEmptyFile(myPreviousFile);
        }
    }
    
    /**
     * Does nothing.
     */
    public void closeRevision(String token) throws SVNException {
    }
    
    public void applyTextDelta(String token, String baseChecksum) throws SVNException {
        if (myCurrentFile != null) {
            myCurrentFile.delete();
        } else {
            myCurrentFile = SVNFileUtil.createUniqueFile(myTmpDirectory, "annotate", ".tmp");;
        }
        myDeltaProcessor.applyTextDelta(myPreviousFile, myCurrentFile, false);
    }

    public OutputStream textDeltaChunk(String token, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String token) throws SVNException {
        myDeltaProcessor.textDeltaEnd();
        
        RandomAccessFile left = null;
        RandomAccessFile right = null;
        try {
            left = new RandomAccessFile(myPreviousFile, "r");
            right = new RandomAccessFile(myCurrentFile, "r");

            ArrayList newLines = new ArrayList();
            int lastStart = 0;

            final QSequenceLineResult result = QSequenceLineMedia.createBlocks(new QSequenceLineRAFileData(left), new QSequenceLineRAFileData(right));
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
                        line.revision = myCurrentDate != null ? myCurrentRevision : -1;
                        line.author = myCurrentAuthor;
                        line.line = result.getRightCache().getLine(j).getContentBytes();
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
            SVNDebugLog.getDefaultLog().info(e);
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
        SVNFileUtil.rename(myCurrentFile, myPreviousFile);
    }
    
    /**
     * Dispatches file lines along with author & revision info to the provided
     * annotation handler.  
     * 
     * @param  handler        an annotation handler that processes file lines with
     *                        author & revision info
     * @param  inputEncoding  a desired character set (encoding) of text lines
     * @throws SVNException
     */
    public void reportAnnotations(ISVNAnnotateHandler handler, String inputEncoding) throws SVNException {
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
    
    /**
     * Finalizes an annotation operation releasing resources involved
     * by this generator. Should be called after {@link #reportAnnotations(ISVNAnnotateHandler, String) reportAnnotations()}. 
     *
     */
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
