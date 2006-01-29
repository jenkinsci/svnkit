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

package org.tmatesoft.svn.core.io.diff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * The <b>SVNDiffWindowBuilder</b> class is used to build diff windows
 * from the raw delta data. 
 * 
 * <p>  
 * The process of restoring a diff window from raw delta data is 
 * represented by the following steps:
 * <ul>
 * <li>reading the header of the delta data; this step is represented
 * by the state {@link #HEADER} of this object;
 * <li>reading offsets for a diff window, now they are 5 and include:
 * a source view offset, a source view length, a target view length, 
 * instructions length, new data length; this step is represented by the 
 * step {@link #OFFSET} of this object;
 * <li>reading instructions bytes, parsing, decoding and converting them 
 * to <b>SVNDiffInstruction</b> objects; this step is represented by the 
 * state {@link #INSTRUCTIONS} of this object;
 * <li>after successful reading instructions data the builder is set to 
 * the state {@link #DONE} - the diff window is built and may be got via
 * calling {@link #getDiffWindow()}. 
 * </ul>
 * 
 * <p>
 * <b>Building a diff window:</b>
 * <p>
 * When a builder is set to the {@link #HEADER} state, at the first calling 
 * to a diff window restoring method -  <b>accept()</b> - the builder tries to 
 * read a header. If header bytes are valid, the builder resets itself to 
 * the state {@link #OFFSET}. 
 * 
 * <p>
 * At the second call to the <b>accept()</b> method
 * the builder tries to read offsets & lengths, and if succeeds, it resets to
 * the {@link #INSTRUCTIONS} state. 
 * 
 * <p>
 * At the next call it reads instructions bytes, 
 * but does not convert them to <b>SVNDiffInstruction</b> objects. So, the 
 * window being produced would not hold instruction objects but only instructions 
 * length, - this is done for memory economy, cause, for example, for large binary files 
 * (tens of Mbs) there could be several hundreds of windows and tens of thousands of  
 * instructions per window what may cause an out of memory exception. When applying a 
 * diff window with the {@link SVNDiffWindow#apply(SVNDiffWindowApplyBaton, InputStream) SVNDiffWindow.apply()} 
 * method, the method itself will restore diff instructions from the raw instructions data that must 
 * be concatenated with the new data provided to the apply method. But if you need to manually 
 * create diff instructions for the produced window, use the {@link #createInstructions(byte[]) createInstructions()} 
 * method. 
 * 
 * <p>
 * At last, if instructions have been read successfully, 
 * the builder resets to the {@link #DONE} state. 
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see     SVNDiffWindow
 * @see     SVNDiffInstruction
 */
public class SVNDiffWindowBuilder {
	
	private static final int MAX_DATA_CHUNK_LENGTH = 100*1024;

    /**
     * Creates a new diff window builder.
     * 
     * @return a new builder
	 */
    public static SVNDiffWindowBuilder newInstance() {
		return new SVNDiffWindowBuilder();
	}
	
    /**
     * The initial state of this object when it's created.
     * Denotes that the header of the diff window is to be read.
	 */
	public static final int HEADER = 0;
	
    /**
     * The state of this object that denotes that diff window offsets
     * are to be read.
	 */
    public static final int OFFSET = 1;
	
    /**
     * The state of this object that denotes that diff window instructions
     * are to be read.
	 */
    public static final int INSTRUCTIONS = 2;
	
    /**
     * The state of this object that denotes that the current diff window 
     * is built, and should be taken back via calling {@link #getDiffWindow()}.
	 */
    public static final int DONE = 3;
    
    private static final byte[] HEADER_BYTES = {'S', 'V', 'N', 0};
	
	private int myState;
	private int[] myOffsets;
	private byte[] myInstructions;
	private byte[] myHeader;
	private SVNDiffWindow myDiffWindow;
    private int myFedDataCount;
    private OutputStream myNewDataStream;
	
	private SVNDiffWindowBuilder() {
		reset();
	}
    
    /**
     * Resets this builder to the initial state - {@link #HEADER}, that 
     * is the very beginning of the raw diff window, its header, is 
     * expected. When calling a diff window restoring method - 
     * <b>accept()</b> - it tries to read a header. If header bytes are
     * valid, the builder resets itself to the state {@link #OFFSET}.
     * 
     * <p>
     * Remember that the inner state of the builder including the produced diff
     * window and its raw instructions data are lost after resetting the builder.  
     * 
     * @see  #reset(int)
     */
    public void reset() {
        reset(HEADER);
    }
	
    /**
     * Resets this builder to a particular state.
     *  
     * <p>
     * Remember that the inner state of the builder including the produced diff
     * window and its raw instructions data are lost after resetting the builder.
     *   
     * @param state one of the four defined state constants 
     * 
	 */
	public void reset(int state) {
		myOffsets = new int[5];
		myHeader = new byte[4];
		myInstructions = null;
		myState = state;
		for(int i = 0; i < myOffsets.length; i++) {
			myOffsets[i] = -1;
		}
		for(int i = 0; i < myHeader.length; i++) {
			myHeader[i] = -1;
		}
		myDiffWindow = null;
        myNewDataStream = null;
        myFedDataCount = 0;
	}
	
    /**
     * Says if the current diff window is built. Get the restored 
     * window via a call to {@link #getDiffWindow()}. 
     *  
     * @return  <span class="javakeyword">true</span> if the diff window
     *          is built, otherwise <span class="javakeyword">false</span>
	 */
	public boolean isBuilt() {
		return myState == DONE;
	}
	
    /**
     * Returns the built diff window.
     * 
     * @return a diff window
	 */
	public SVNDiffWindow getDiffWindow() {
		return myDiffWindow;
	}
    
    /**
     * Returns the raw instructions data of the current diff window.
     * 
     * @return instructions bytes or <span class="javakeyword">null</span> 
     *         if they have not been read
     */
    public byte[] getInstructionsData() {
        return myInstructions;
    }
	
    /**
     * Builds a diff window from raw delta bytes. If every step of restoring 
     * a diff window succeeds, the builder automatically resets to the next state
     * (up to the {@link #DONE} state) and recursively calls this method to perform 
     * the next step of restoring the window. When this method returns, check if 
     * the diff window is completed by {@link #isBuilt()}. 
     * 
     * <p>
     * If the raw delta data also includes new data, the return offset will
     * be the offset where this data begins in the provided buffer. 
     * 
     * <p>
     * If the <code>bytes</code> array is not exhausted (i.e. contains more 
     * than just one window), you should obtain the produced window, manually reset 
     * the builder to the {@link #OFFSET} state and call this <b>accept()</b> method
     * again for building the next window.
     * 
     * <p> 
     * Actually, diff windows created by the <b>SVNDiffWindowBuilder</b> builders
     * are not supplied with diff instructions, use {@link #createInstructions(byte[]) createInstructions()} 
     * to manually create them.
     * 
     * @param  bytes   raw delta bytes (header, offsets&lengths, instructions, new data, ...)
     * @param  offset  an offset in the raw delta bytes array, 
     *                 that marks the position from where the bytes are 
     *                 to be read;   
     * @return         an offset in the raw delta bytes array after reading a 
     *                 sequence of bytes
     * @see            #accept(InputStream, ISVNEditor, String)
	 */
    public int accept(byte[] bytes, int offset) {       
        switch (myState) {
            case HEADER:
                for(int i = 0; i < myHeader.length && offset < bytes.length; i++) {
                    if (myHeader[i] < 0) {
                        myHeader[i] = bytes[offset++];
                    }
                }
                if (myHeader[myHeader.length - 1] >= 0) {
                    myState = OFFSET;
                    if (offset < bytes.length) {
                        return accept(bytes, offset);
                    }
                }
                break;
            case OFFSET:
                for(int i = 0; i < myOffsets.length && offset < bytes.length; i++) {
                    if (myOffsets[i] < 0) {
                        // returns 0 if nothing was read, due to missing bytes.
                        offset = readInt(bytes, offset, myOffsets, i);
                        if (myOffsets[i] < 0) {
                            return offset;
                        }
                    }
                }
                if (myOffsets[myOffsets.length - 1] >= 0) {
                    myState = INSTRUCTIONS;
                    if (offset < bytes.length) {
                        return accept(bytes, offset);
                    }
                }
                break;
            case INSTRUCTIONS:
                if (myOffsets[3] > 0) {
                    if (myInstructions == null) {
                        myInstructions = new byte[myOffsets[3]];                        
                    }
                    // min of number of available and required.
                    int length =  Math.min(bytes.length - offset, myOffsets[3]);
                    System.arraycopy(bytes, offset, myInstructions, myInstructions.length - myOffsets[3], length);
                    myOffsets[3] -= length;
                    if (myOffsets[3] == 0) {
                        myState = DONE;
                        if (myDiffWindow == null) {
                            myDiffWindow = createDiffWindow(myOffsets, myInstructions);
                        }
                    }
                    return offset + length;
                }
                if (myDiffWindow == null) {
                    myDiffWindow = createDiffWindow(myOffsets, myInstructions);
                } 
                myState = DONE;
            default:
                // all is read.
        }
        return offset;
    }
    
    /**
     * Builds a diff window/windows from raw delta bytes. For every successful 
     * step of restoring a diff window, the builder automatically resets to the 
     * next state (up to the {@link #DONE} state) and returns <span class="javakeyword">true</span>.
     * 
     * <p>
     * <code>consumer</code> is used to collect diff windows and provide output 
     * streams to write raw instructions and new data to for each window. 
     * 
     * <p> 
     * Actually, diff windows created by the <b>SVNDiffWindowBuilder</b> builders
     * are not supplied with diff instructions, use {@link #createInstructions(byte[]) createInstructions()} 
     * to manually create them. Instructions are written into the same output stream 
     * as the new data (just before the new data):
     * <pre class="javacode">
     *     OutputStream os = consumer.textDeltaChunk(path, window);</pre>
     * 
     * @param  is            a source input stream from where raw delta bytes
     *                       are read
     * @param  consumer      an editor that collects diff windows 
     * @param  path          a path of a file for which delta is restored
     * @return               <span class="javakeyword">true</span> if the method
     *                       successfully processed the current state and reset
     *                       to the next one; <span class="javakeyword">false</span> 
     *                       in the following cases:
     *                       <ul>
     *                       <li>the builder is reset to an unknown state
     *                       <li>can't read raw bytes from <code>is</code> due to
     *                       errors
     *                       <li>EOF occurred while reading new data from <code>is</code> (check if 
     *                       the window {@link #isBuilt()})  
     *                       </ul> 
     * @throws SVNException  if an i/o error occurred while reading from <code>is</code>
     */
    public void accept(RandomAccessFile file) throws SVNException, IOException {       
        SVNErrorMessage err;
        switch (myState) {
            case HEADER:
                try{
                    for(int i = 0; i < myHeader.length; i++) {
                        if (myHeader[i] < 0) {
                            int r = file.read();
                            if (r < 0) {
                                break;
                            }
                            myHeader[i] = (byte) (r & 0xFF);
                        }
                    }
                    if (myHeader[myHeader.length - 1] >= 0) {
                        myState = OFFSET;
                        accept(file);
                        return;
                    }
                }catch(IOException ioe){
                    err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                    SVNErrorManager.error(err, ioe);
                }
                err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_INVALID_HEADER);
                SVNErrorManager.error(err);
            case OFFSET:
                for(int i = 0; i < myOffsets.length; i++) {
                    if (myOffsets[i] < 0) {
                        readInt(file, myOffsets, i);
                        if (myOffsets[i] < 0) {
                            err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_INVALID_OPS);
                            SVNErrorManager.error(err);
                        }
                    }
                }
                if (myOffsets[myOffsets.length - 1] >= 0) {
                    myState = INSTRUCTIONS;
                    accept(file);
                    return;
                }
                err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_INVALID_OPS);
                SVNErrorManager.error(err);
            case INSTRUCTIONS:
                if (myOffsets[3] > 0) {
                    if (myInstructions == null) {
                        myInstructions = new byte[myOffsets[3]];                        
                    }
                    int length =  myOffsets[3];
                    // read length bytes (!!!!)
                    try {
                        length = file.read(myInstructions, myInstructions.length - length, length);
                    } catch (IOException e) {
                        err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                        SVNErrorManager.error(err, e);
                    }
                    if (length <= 0) {
                        err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_UNEXPECTED_END);
                        SVNErrorManager.error(err);
                    }
                    myOffsets[3] -= length;
                    if (myOffsets[3] == 0) {
                        myState = DONE;
                        if (myDiffWindow == null) {
                            myDiffWindow = createDiffWindow(myOffsets, myInstructions);
                        }
                    }else{
                        err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_UNEXPECTED_END);
                        SVNErrorManager.error(err);
                    }
                }
                if (myDiffWindow == null) {
                    myDiffWindow = createDiffWindow(myOffsets, myInstructions);
                } 
                myState = DONE;
            default:
                // all is read.
        }
    }

    public boolean accept(InputStream is, ISVNEditor consumer, String path) throws SVNException {       
        switch (myState) {
            case HEADER:
                try {
                    for(int i = 0; i < myHeader.length; i++) {
                        if (myHeader[i] < 0) {
                            int r = is.read();
                            if (r < 0) {
                                break;
                            }
                            myHeader[i] = (byte) (r & 0xFF);
                        }
                    }
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                    SVNErrorManager.error(err, e);
                }
                if (myHeader[myHeader.length - 1] >= 0) {
                    myState = OFFSET;
                }
                break;
            case OFFSET:
                for(int i = 0; i < myOffsets.length; i++) {
                    if (myOffsets[i] < 0) {
                        // returns 0 if nothing was read, due to missing bytes.
                        // but it may be partially read!
                        try {
                            readInt(is, myOffsets, i);
                        } catch (IOException e) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                            SVNErrorManager.error(err, e);
                        }
                        if (myOffsets[i] < 0) {
                            // can't read?
                            return false;
                        }
                    }
                }
                if (myOffsets[myOffsets.length - 1] >= 0) {
                    myState = INSTRUCTIONS;
                }
                break;
            case INSTRUCTIONS:
                if (myOffsets[3] > 0) {
                    if (myInstructions == null) {
                        myInstructions = new byte[myOffsets[3]];
                    }
                    // min of number of available and required.
                    int length =  myOffsets[3];
                    // read length bytes (!!!!)
                    try {
                        length = is.read(myInstructions, myInstructions.length - length, length);
                    } catch (IOException e) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                        SVNErrorManager.error(err, e);
                    }
                    if (length <= 0) {
                        return false;
                    }
                    myOffsets[3] -= length;
                    if (myOffsets[3] == 0) {
                        myState = DONE;
                        if (myDiffWindow == null) {
                            myDiffWindow = createDiffWindow(myOffsets, myInstructions);
                            myFedDataCount = 0;
                            myNewDataStream = consumer.textDeltaChunk(path, myDiffWindow);
                            if (myNewDataStream == null) {
                                myNewDataStream = SVNFileUtil.DUMMY_OUT;
                            }
                            try {
                                myNewDataStream.write(myInstructions);
                            } catch (IOException e) {
                                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                                SVNErrorManager.error(err, e);
                            }
                        }
                    }
                    break;
                }
                myState = DONE;
                if (myDiffWindow == null) {
                    myDiffWindow = createDiffWindow(myOffsets, myInstructions);
                    myFedDataCount = 0;
                    myNewDataStream = consumer.textDeltaChunk(path, myDiffWindow);
                    if (myNewDataStream == null) {
                        myNewDataStream = SVNFileUtil.DUMMY_OUT;
                    }
                }
                break;
            case DONE:
                try {
                    while(myFedDataCount < myDiffWindow.getNewDataLength()) {
                        int r = is.read();
                        if (r < 0) {
                            return false;
                        }
                        myNewDataStream.write(r);
                        myFedDataCount++;
                    }
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
                    SVNErrorManager.error(err, e);
                }
                SVNFileUtil.closeFile(myNewDataStream);
                reset(SVNDiffWindowBuilder.OFFSET);
                break;
            default:
                SVNDebugLog.logInfo("invalid diff window builder state: " + myState);
                return false;
        }
        return true;
    }
    
    /**
     * Writes the given diff window to the provided output stream as raw delta
     * bytes. 
     * 
     * <p>
     * For the very first diff window <code>saveHeader</code> must be 
     * <span class="javakeyword">true</span>, but if the delta is represented by 
     * more than one diff window, call this method for writing the rest windows 
     * with <code>saveHeader</code> set to <span class="javakeyword">false</span>. 
     * 
     * @param  window         a diff window
     * @param  saveHeader     if <span class="javakeyword">true</span> then also writes
     *                        delta header (the first three characters of which are 
     *                        <span class="javastring">"SVN"</span>), otherwise starts 
     *                        writing with the window's offsets & lengths
     * @param  os             a target output stream where raw delta bytes are to be 
     *                        written
     * @throws IOException    if an i/o error occurred
     */
    public static void save(SVNDiffWindow window, boolean saveHeader, OutputStream os) throws IOException {
        if (saveHeader) {
            os.write(HEADER_BYTES);
        } 
        if (window.getInstructionsCount() == 0) {
            return;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for(int i = 0; i < window.getInstructionsCount(); i++) {
            SVNDiffInstruction instruction = window.getInstructionAt(i);
            byte first = (byte) (instruction.type << 6);
            if (instruction.length <= 0x3f && instruction.length > 0) {
                // single-byte lenght;
                first |= (instruction.length & 0x3f);
                bos.write(first & 0xff);
            } else {
                bos.write(first & 0xff);
                writeInt(bos, instruction.length);
            }
            if (instruction.type == 0 || instruction.type == 1) {
                writeInt(bos, instruction.offset);
            }
        }

        long[] offsets = new long[5];
        offsets[0] = window.getSourceViewOffset();
        offsets[1] = window.getSourceViewLength();
        offsets[2] = window.getTargetViewLength();
        offsets[3] = bos.size();
        offsets[4] = window.getNewDataLength();
        for(int i = 0; i < offsets.length; i++) {
            writeInt(os, offsets[i]);
        }
        bos.writeTo(os);
    }

    public static void save(SVNDiffWindow window, boolean saveHeader, RandomAccessFile file) throws IOException {
        if (saveHeader) {
            file.write(HEADER_BYTES);
        } 
        if (window.getInstructionsCount() == 0) {
            return;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for(int i = 0; i < window.getInstructionsCount(); i++) {
            SVNDiffInstruction instruction = window.getInstructionAt(i);
            byte first = (byte) (instruction.type << 6);
            if (instruction.length <= 0x3f && instruction.length > 0) {
                // single-byte lenght;
                first |= (instruction.length & 0x3f);
                bos.write(first & 0xff);
            } else {
                bos.write(first & 0xff);
                writeInt(bos, instruction.length);
            }
            if (instruction.type == 0 || instruction.type == 1) {
                writeInt(bos, instruction.offset);
            }
        }

        long[] offsets = new long[5];
        offsets[0] = window.getSourceViewOffset();
        offsets[1] = window.getSourceViewLength();
        offsets[2] = window.getTargetViewLength();
        offsets[3] = bos.size();
        offsets[4] = window.getNewDataLength();
        for(int i = 0; i < offsets.length; i++) {
            writeInt(file, offsets[i]);
        }
        file.write(bos.toByteArray());
    }
    
    /**
     * Creates a diff window intended for replacing the whole contents of a file
     * with new data. It is mainly intended for binary and newly added text files.  
     * 
     * <p>
     * The number of instructions depends on the <code>dataLength</code>: one 
     * instruction per 100K data chunk. 
     * 
     * @param  dataLength    the length of new data 
     * @return               a new diff window
     */
    public static SVNDiffWindow createReplacementDiffWindow(long dataLength) {
        if (dataLength == 0) {
            return new SVNDiffWindow(0, 0, dataLength, new SVNDiffInstruction[0], dataLength);
        }
        // divide data length in 100K segments
        long totalLength = dataLength;
        long offset = 0;
        int instructionsCount = (int) ((dataLength / (MAX_DATA_CHUNK_LENGTH)) + 1);
        Collection instructionsList = new ArrayList(instructionsCount);
        while(dataLength > MAX_DATA_CHUNK_LENGTH) {
            dataLength -= MAX_DATA_CHUNK_LENGTH; 
            instructionsList.add(new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_NEW_DATA, MAX_DATA_CHUNK_LENGTH, offset));
            offset += MAX_DATA_CHUNK_LENGTH;
        }
        if (dataLength > 0) {
            instructionsList.add(new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_NEW_DATA, dataLength, offset));
        }
        SVNDiffInstruction[] instructions = (SVNDiffInstruction[]) instructionsList.toArray(new SVNDiffInstruction[instructionsList.size()]);
        return new SVNDiffWindow(0, 0, totalLength, instructions, totalLength);
    }

    /**
     * Creates a diff window/windows intended for replacing the whole contents of 
     * a file with new data. It is mainly intended for binary and newly added text 
     * files.  
     * 
     * <p>
     * The point is that the data length may be too huge, so that one diff window 
     * will eat a large amount of memory (on the machine where an svnserve process 
     * runs, not JavaSVN) to apply its instructions. It's more safe 
     * to devide the contents into a number of smaller windows.
     * 
     * <p>
     * The number of windows produced depends on <code>dataLength</code> and 
     * <code>maxWindowLength</code>: one diff window (with a single instruction) 
     * per <code>maxWindowLength</code> data chunk.  
     *  
     * @param   dataLength        the length of new data 
     * @param   maxWindowLength   the maximum length of one diff window
     * @return                    diff windows 
     */
    public static SVNDiffWindow[] createReplacementDiffWindows(long dataLength, int maxWindowLength) {
        if (dataLength == 0) {
            return new SVNDiffWindow[] {new SVNDiffWindow(0, 0, dataLength, new SVNDiffInstruction[0], dataLength)};
        }
        int instructionsCount = (int) ((dataLength / (maxWindowLength)) + 1);
        Collection windows = new ArrayList(instructionsCount);
        while(dataLength > maxWindowLength) {
            SVNDiffInstruction instruction = new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_NEW_DATA, maxWindowLength, 0); 
            SVNDiffWindow window = new SVNDiffWindow(0, 0, maxWindowLength, new SVNDiffInstruction[] { instruction}, maxWindowLength);
            windows.add(window);
            dataLength -= maxWindowLength; 
        }
        if (dataLength > 0) {
            SVNDiffInstruction instruction = new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_NEW_DATA, dataLength, 0);
            SVNDiffWindow window = new SVNDiffWindow(0, 0, dataLength, new SVNDiffInstruction[] {instruction}, dataLength);
            windows.add(window);
        }
        return (SVNDiffWindow[]) windows.toArray(new SVNDiffWindow[windows.size()]);
    }
    
    private static void writeInt(OutputStream os, long i) throws IOException {
        if (i == 0) {
            os.write(0);
            return;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while(i > 0) {
            byte b = (byte) (i & 0x7f);
            i = i >> 7;
            if (bos.size() > 0) {
                b |= 0x80;
            }
            bos.write(b);            
        } 
        byte[] bytes = bos.toByteArray();
        for(int j = bytes.length - 1; j  >= 0; j--) {
            os.write(bytes[j]);
        }
    }

    private static void writeInt(RandomAccessFile file, long i) throws IOException {
        if (i == 0) {
            file.write(0);
            return;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while(i > 0) {
            byte b = (byte) (i & 0x7f);
            i = i >> 7;
            if (bos.size() > 0) {
                b |= 0x80;
            }
            bos.write(b);            
        } 
        byte[] bytes = bos.toByteArray();
        for(int j = bytes.length - 1; j  >= 0; j--) {
            file.write(bytes[j]);
        }
    }
	
    private static int readInt(byte[] bytes, int offset, int[] target, int index) {
        int newOffset = offset;
        target[index] = 0;
        while(true) {
            byte b = bytes[newOffset];
            target[index] = target[index] << 7;
            target[index] = target[index] | (b & 0x7f);
            if ((b & 0x80) != 0) {
                // high bit
                newOffset++;
                if (newOffset >= bytes.length) {
                    target[index] = -1;
                    return offset;
                }
                continue;
            }
            // integer read.
            return newOffset + 1;
        }
    }

    private static void readInt(InputStream is, int[] target, int index) throws IOException {
        target[index] = 0;
        is.mark(10);
        while(true) {
            int r = is.read();
            if (r < 0) {
                is.reset();
                target[index] = -1;
                return;
            }
            byte b = (byte) (r & 0xFF);
            target[index] = target[index] << 7;
            target[index] = target[index] | (b & 0x7f);
            if ((b & 0x80) != 0) {
                continue;
            }
            return;
        }
    }

    private static void readInt(RandomAccessFile file, int[] target, int index) throws SVNException, IOException {
        target[index] = 0;
        while(true) {
            int r = file.read();
            if (r < 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_UNEXPECTED_END);
                SVNErrorManager.error(err);
            }
            byte b = (byte) (r & 0xFF);
            target[index] = target[index] << 7;
            target[index] = target[index] | (b & 0x7f);
            if ((b & 0x80) != 0) {
                continue;
            }
            return;
        }
    }

	private static SVNDiffWindow createDiffWindow(int[] offsets, byte[] instructions) {
		SVNDiffWindow window = new SVNDiffWindow(offsets[0], offsets[1], offsets[2], 
				instructions.length,
				offsets[4]);
		return window;
	}
	
    /**
     * Parses, decodes the raw instructions bytes and converts them to 
     * diff instruction objects.
     * 
     * @param  bytes  raw instructions bytes
     * @return        diff instruction objects
	 */
	public static SVNDiffInstruction[] createInstructions(byte[] bytes) {
        Collection instructions = new ArrayList();
        int[] instr = new int[2];
        for(int i = 0; i < bytes.length;) {            
            SVNDiffInstruction instruction = new SVNDiffInstruction();
            instruction.type = (bytes[i] & 0xC0) >> 6;
            instruction.length = bytes[i] & 0x3f;
            i++;
            if (instruction.length == 0) {
                // read length from next byte                
            	i = readInt(bytes, i, instr, 0);
                instruction.length = instr[0];
            } 
            if (instruction.type == 0 || instruction.type == 1) {
                // read offset from next byte (no offset without length).
            	i = readInt(bytes, i, instr, 1);
                instruction.offset = instr[1];
            } 
            instructions.add(instruction);
            instr[0] = 0;
            instr[1] = 0;
        }        
        return (SVNDiffInstruction[]) instructions.toArray(new SVNDiffInstruction[instructions.size()]);
    }

}
