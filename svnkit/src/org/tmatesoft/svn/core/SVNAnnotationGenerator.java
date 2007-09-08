/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslatorInputStream;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.util.SVNDebugLog;

import de.regnis.q.sequence.QSequenceDifferenceBlock;
import de.regnis.q.sequence.line.QSequenceLineMedia;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;
import de.regnis.q.sequence.line.QSequenceLineResult;
import de.regnis.q.sequence.line.simplifier.QSequenceLineDummySimplifier;
import de.regnis.q.sequence.line.simplifier.QSequenceLineEOLUnifyingSimplifier;
import de.regnis.q.sequence.line.simplifier.QSequenceLineSimplifier;
import de.regnis.q.sequence.line.simplifier.QSequenceLineTeeSimplifier;
import de.regnis.q.sequence.line.simplifier.QSequenceLineWhiteSpaceReducingSimplifier;
import de.regnis.q.sequence.line.simplifier.QSequenceLineWhiteSpaceSkippingSimplifier;


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
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNAnnotationGenerator implements ISVNFileRevisionHandler {

    private File myTmpDirectory;
    private String myPath;

    private long myCurrentRevision;
    private String myCurrentAuthor;
    private Date myCurrentDate;
    private boolean myIsCurrentResultOfMerge;
    private String myCurrentPath;
    
    private File myPreviousFile;
    private File myPreviousOriginalFile;
    private File myCurrentFile;

    private LinkedList myMergeBlameChunks;
    private LinkedList myBlameChunks;
    private SVNDeltaProcessor myDeltaProcessor;
    private ISVNEventHandler myCancelBaton;
    private long myStartRevision;
    private boolean myIsForce;
    private boolean myIncludeMergedRevisions;
    private SVNDiffOptions myDiffOptions;
    private QSequenceLineSimplifier mySimplifier;
    
    /**
     * Constructs an annotation generator object. 
     * <p>
     * This constructor is equivalent to 
     * <code>SVNAnnotationGenerator(path, tmpDirectory, startRevision, false, cancelBaton)</code>.
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
    
    /**
     * Constructs an annotation generator object. 
     * 
     * @param path           a file path (relative to a repository location)
     * @param tmpDirectory   a revision to stop at
     * @param startRevision  a start revision to begin annotation with
     * @param force          forces binary files processing  
     * @param cancelBaton    a baton which is used to check if an operation 
     *                       is cancelled
     */
    public SVNAnnotationGenerator(String path, File tmpDirectory, long startRevision, boolean force, ISVNEventHandler cancelBaton) {
        this(path, tmpDirectory, startRevision, force, new SVNDiffOptions(), cancelBaton);
    }

    /**
     * Constructs an annotation generator object.
     *  
     * @param path           a file path (relative to a repository location)
     * @param tmpDirectory   a revision to stop at
     * @param startRevision  a start revision to begin annotation with
     * @param force          forces binary files processing  
     * @param diffOptions    diff options 
     * @param cancelBaton    a baton which is used to check if an operation 
     *                       is cancelled
     */
    public SVNAnnotationGenerator(String path, File tmpDirectory, long startRevision, boolean force, SVNDiffOptions diffOptions, ISVNEventHandler cancelBaton) {
        this(path, tmpDirectory, startRevision, force, false, diffOptions, cancelBaton);
    }
    
    public SVNAnnotationGenerator(String path, File tmpDirectory, long startRevision, boolean force, 
                                  boolean includeMergedRevisions, SVNDiffOptions diffOptions, 
                                  ISVNEventHandler cancelBaton) {
        myTmpDirectory = tmpDirectory;
        myCancelBaton = cancelBaton;
        myPath = path;
        myIsForce = force;
        if (!myTmpDirectory.isDirectory()) {
            myTmpDirectory.mkdirs();
        }
        myMergeBlameChunks = new LinkedList();
        myBlameChunks = new LinkedList();
        myDeltaProcessor = new SVNDeltaProcessor();
        myStartRevision = startRevision;
        myDiffOptions = diffOptions;
        myIncludeMergedRevisions = includeMergedRevisions;
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
        
        myIsCurrentResultOfMerge = fileRevision.isResultOfMerge();
        if (myIncludeMergedRevisions) {
            myCurrentPath = fileRevision.getPath();
        }
    }
    
    /**
     * Does nothing.
     * 
     * @param token       
     * @throws SVNException
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
        
        if (myIncludeMergedRevisions) {
            myMergeBlameChunks = addFileBlame(myPreviousFile, myCurrentFile, myMergeBlameChunks);
            if (!myIsCurrentResultOfMerge) {
                myBlameChunks = addFileBlame(myPreviousOriginalFile, myCurrentFile, myBlameChunks);
                if (myPreviousOriginalFile == null) {
                    myPreviousOriginalFile = myCurrentFile;
                    myCurrentFile = null;
                } else {
                    SVNFileUtil.rename(myCurrentFile, myPreviousOriginalFile);    
                }
                
                myPreviousFile = myPreviousOriginalFile;
            } else {
                if (myPreviousFile != null && myPreviousFile != myPreviousOriginalFile) {
                    SVNFileUtil.rename(myCurrentFile, myPreviousFile);    
                } else {
                    myPreviousFile = myCurrentFile;
                    myCurrentFile = null;
                }
            }
        } else {
            myBlameChunks = addFileBlame(myPreviousFile, myCurrentFile, myBlameChunks);
            if (myPreviousFile == null) {
                myPreviousFile = myCurrentFile;
                myCurrentFile = null;
            } else {
                SVNFileUtil.rename(myCurrentFile, myPreviousFile);
            }
        }
    }

	private LinkedList addFileBlame(File previousFile, File currentFile, LinkedList chain) throws SVNException {
        if (previousFile == null) {
            BlameChunk chunk = new BlameChunk();
            chunk.author = myCurrentAuthor;
            chunk.revision = myCurrentDate != null ? myCurrentRevision : -1;
            chunk.date = myCurrentDate;
            chunk.blockStart = 0;
            chunk.path = myCurrentPath;
            chain.add(chunk);
            return chain;
        }
        
        RandomAccessFile left = null;
        RandomAccessFile right = null;
        try {
            left = new RandomAccessFile(previousFile, "r");
            right = new RandomAccessFile(currentFile, "r");

            final QSequenceLineResult result = QSequenceLineMedia.createBlocks(new QSequenceLineRAFileData(left), new QSequenceLineRAFileData(right), createSimplifier());
            try {
                List blocksList = result.getBlocks();
                for(int i = 0; i < blocksList.size(); i++) {
                    QSequenceDifferenceBlock block = (QSequenceDifferenceBlock) blocksList.get(i);
                    if (block.getLeftSize() > 0) {
                        deleteBlameChunk(block.getRightFrom(), block.getLeftSize(), chain);
                    }
                    if (block.getRightSize() > 0) {
                        insertBlameChunk(myCurrentRevision, myCurrentAuthor, 
                                         myCurrentDate, myCurrentPath, 
                                         block.getRightFrom(), block.getRightSize(), chain);
                    }
                }
            } finally {
                result.close();
            }
        } catch (Throwable e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Exception while generating annotation: {0}", e.getMessage());
            SVNErrorManager.error(err, e);
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

        return chain;
    }
    
    private void insertBlameChunk(long revision, String author, Date date, String path, 
                                  int start, int length, LinkedList chain) {
        int[] index = new int[1];
        BlameChunk startPoint = findBlameChunk(chain, start, index);
        int adjustFromIndex = -1;
        if (startPoint.blockStart == start) {
            BlameChunk insert = new BlameChunk();
            insert.copy(startPoint);
            insert.blockStart = start + length;
            chain.add(index[0] + 1, insert);

            startPoint.author = author;
            startPoint.revision = revision;
            startPoint.date = date;
            startPoint.path = path;
            adjustFromIndex = index[0] + 2;
        } else {
            BlameChunk middle = new BlameChunk();
            middle.author = author;
            middle.revision = revision;
            middle.date = date;
            middle.path = path;
            middle.blockStart = start;
            
            BlameChunk insert = new BlameChunk();
            insert.copy(startPoint);
            insert.blockStart = start + length;
            chain.add(index[0] + 1, middle);
            chain.add(index[0] + 2, insert);
            adjustFromIndex = index[0] + 3;
        }
        
        adjustBlameChunks(chain, adjustFromIndex, length);
    }
    
    private void deleteBlameChunk(int start, int length, LinkedList chain) {
        int[] ind = new int[1];
        
        BlameChunk first = findBlameChunk(chain, start, ind);
        int firstInd = ind[0];
        
        BlameChunk last = findBlameChunk(chain, start + length, ind);
        int lastInd = ind[0];
        
        if (first != last) {
            int deleteCount = lastInd - firstInd - 1;
            for (int i = 0; i < deleteCount; i++) {
                chain.remove(firstInd + 1);
            }
            lastInd -= deleteCount;
            
            last.blockStart = start;
            if (first.blockStart == start) {
                first.copy(last);
                chain.remove(lastInd);
                lastInd--;
                last = first;
            }
        }

        int tailInd = lastInd < chain.size() - 1 ? lastInd + 1 : -1;
        BlameChunk tail = tailInd > 0 ? (BlameChunk)chain.get(tailInd) : null;

        if (tail != null && tail.blockStart == last.blockStart + length) {
            last.copy(tail);
            chain.remove(tail);
            tailInd--;
            tail = last;
        }
        
        if (tail != null) {
            adjustBlameChunks(chain, tailInd, -length);
        }
    }
    
    private void adjustBlameChunks(LinkedList chain, int startIndex, int adjust) {
        for (int i = startIndex; i < chain.size(); i++) {
            BlameChunk curChunk = (BlameChunk) chain.get(i);
            curChunk.blockStart += adjust;
        }
    }
    
    private BlameChunk findBlameChunk(LinkedList chain, int offset, int[] index) {
        BlameChunk prevChunk = null;
        index[0] = -1; 
        for (Iterator chunks = chain.iterator(); chunks.hasNext();) {
            BlameChunk chunk = (BlameChunk) chunks.next();
            if (chunk.blockStart > offset) {
                break;
            }
            prevChunk = chunk;
            index[0]++;
        }
        return prevChunk;
    }
    
    private void normalizeBlames(LinkedList chain, LinkedList mergedChain) {
        int i = 0, k = 0;
        for (; i < chain.size() - 1 && k < mergedChain.size() - 1; i++, k++) {
            BlameChunk chunk = (BlameChunk) chain.get(i);
            BlameChunk mergedChunk = (BlameChunk) mergedChain.get(k);
            SVNDebugLog.assertCondition(chunk.blockStart == mergedChunk.blockStart, 
                                        "ASSERTION FAILURE in " + 
                                        "SVNAnnotationGenerator.normalizeBlames():" +
                                        "current chunks should always be starting " + 
                                        "at the same offset");

            BlameChunk nextChunk = (BlameChunk) chain.get(i + 1);
            BlameChunk nextMergedChunk = (BlameChunk) mergedChain.get(k + 1);
            if (nextChunk.blockStart < nextMergedChunk.blockStart) {
                nextMergedChunk.blockStart = nextChunk.blockStart;
            }
            if (nextChunk.blockStart < nextMergedChunk.blockStart) {
                nextChunk.blockStart = nextMergedChunk.blockStart;
            }
        }

        if ((i == chain.size() - 1) && (k == mergedChain.size() - 1)) {
            return;
        }
        
        if (k == mergedChain.size() - 1) {
            for (i += 1; i < chain.size(); i++) {
                BlameChunk chunk = (BlameChunk) chain.get(i);
                BlameChunk mergedChunk = (BlameChunk) mergedChain.getLast();

                BlameChunk insert = new BlameChunk();
                insert.copy(mergedChunk);
                insert.blockStart = chunk.blockStart;
                mergedChain.add(insert);
                k++;
            }
        }

        if (i == chain.size() - 1) {
            for (k += 1; k < mergedChain.size(); k++) {
                BlameChunk mergedChunk = (BlameChunk) mergedChain.get(k);
                BlameChunk chunk = (BlameChunk) chain.getLast();

                BlameChunk insert = new BlameChunk();
                insert.copy(chunk);
                insert.blockStart = mergedChunk.blockStart;
                chain.add(insert);
                i++;
            }
        }
    }
       
    /**
     * Dispatches file lines along with author & revision info to the provided
     * annotation handler.  
     * 
     * <p>
     * If <code>inputEncoding</code> is <span class="javakeyword">null</span> then 
     * <span class="javastring">"file.encoding"</span> system property is used. 
     * 
     * @param  handler        an annotation handler that processes file lines with
     *                        author & revision info
     * @param  inputEncoding  a desired character set (encoding) of text lines
     * @throws SVNException
     */
    public void reportAnnotations(ISVNAnnotateHandler handler, String inputEncoding) throws SVNException {
        if (handler == null) {
            return;
        }

        SVNDebugLog.assertCondition(myPreviousFile != null, 
                                    "ASSERTION FAILURE in " + 
                                    "SVNAnnotationGenerator.reportAnnotations():" +
                                    "generator has to have been called at least once");
        int mergedCount = -1;
        if (myIncludeMergedRevisions) {
            if (myBlameChunks.isEmpty()) {
                BlameChunk chunk = new BlameChunk();
                chunk.blockStart = 0;
                chunk.author = myCurrentAuthor;
                chunk.date = myCurrentDate;
                chunk.revision = myCurrentRevision;
                chunk.path = myCurrentPath;
                myBlameChunks.add(chunk);
            }
            normalizeBlames(myBlameChunks, myMergeBlameChunks);
            mergedCount = 0;
        }
        
        inputEncoding = inputEncoding == null ? System.getProperty("file.encoding") : inputEncoding;
        CharsetDecoder decoder = Charset.forName(inputEncoding).newDecoder();

        InputStream stream = null;
        try {
            stream = new SVNTranslatorInputStream(SVNFileUtil.openFileForReading(myPreviousFile), 
                                                  SVNTranslator.LF, true, null, false);
            
            StringBuffer buffer = new StringBuffer();
            for (int i = 0; i < myBlameChunks.size(); i++) {
                BlameChunk chunk = (BlameChunk) myBlameChunks.get(i);
                String mergedAuthor = null;
                long mergedRevision = SVNRepository.INVALID_REVISION;
                Date mergedDate = null;
                String mergedPath = null;
                if (mergedCount >= 0) {
                    BlameChunk mergedChunk = (BlameChunk) myMergeBlameChunks.get(mergedCount++);
                    mergedAuthor = mergedChunk.author;
                    mergedRevision = mergedChunk.revision;
                    mergedDate = mergedChunk.date;
                    mergedPath = mergedChunk.path;
                }
                
                BlameChunk nextChunk = null;
                if (i < myBlameChunks.size() - 1) {
                    nextChunk = (BlameChunk) myBlameChunks.get(i + 1);
                }
                
                for (int lineNo = chunk.blockStart; 
                     nextChunk == null || lineNo < nextChunk.blockStart; lineNo++) {
                    myCancelBaton.checkCancelled();
                    buffer.setLength(0);
                    String line = SVNFileUtil.readLineFromStream(stream, buffer, decoder);
                    boolean isEOF = false;
                    if (line == null) {
                        isEOF = true;
                        if (buffer.length() > 0) {
                            line = buffer.toString();
                        }
                    }
                    
                    if (!isEOF || line != null) {
                        handler.handleLine(chunk.date, chunk.revision, chunk.author, 
                                           line, mergedDate, mergedRevision, mergedAuthor, 
                                           mergedPath, lineNo);
                    }
                    
                    if (isEOF) {
                        break;
                    }
                }
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } finally {
            SVNFileUtil.closeFile(stream);
        }
    }

    /**
     * Finalizes an annotation operation releasing resources involved
     * by this generator. Should be called after {@link #reportAnnotations(ISVNAnnotateHandler, String) reportAnnotations()}. 
     *
     */
    public void dispose() {
        myIsCurrentResultOfMerge = false;
        if (myCurrentFile != null) {
            myCurrentFile.delete();
            myCurrentFile = null;
        }
        if (myPreviousFile != null) {
            myPreviousFile.delete();
            myPreviousFile = null;
        }
        if (myPreviousOriginalFile != null) {
            myPreviousOriginalFile.delete();
            myPreviousOriginalFile = null;
        }
        
        myBlameChunks.clear();
        myMergeBlameChunks.clear();
    }
    
    private QSequenceLineSimplifier createSimplifier() {
        if (mySimplifier == null) {
            QSequenceLineSimplifier first = myDiffOptions.isIgnoreEOLStyle() ? 
                    (QSequenceLineSimplifier) new QSequenceLineEOLUnifyingSimplifier() :
                    (QSequenceLineSimplifier) new QSequenceLineDummySimplifier();
            QSequenceLineSimplifier second = new QSequenceLineDummySimplifier();
            if (myDiffOptions.isIgnoreAllWhitespace()) {
                second = new QSequenceLineWhiteSpaceSkippingSimplifier();
            } else if (myDiffOptions.isIgnoreAmountOfWhitespace()) {
                second = new QSequenceLineWhiteSpaceReducingSimplifier();
            }
            mySimplifier = new QSequenceLineTeeSimplifier(first, second);
        }
        return mySimplifier;
    }

    private static class BlameChunk {
        public int blockStart;
        public long revision;
        public String author;
        public Date date;
        public String path;
        
        public void copy(BlameChunk chunk) {
            author = chunk.author;
            date = chunk.date;
            revision = chunk.revision;
            path = chunk.path;
            blockStart = chunk.blockStart;
        }
    }
    
}
