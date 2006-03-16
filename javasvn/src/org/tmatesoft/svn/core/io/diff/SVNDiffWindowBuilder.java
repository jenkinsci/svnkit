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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.fs.FSFile;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * The <b>SVNDiffWindowBuilder</b> class is used to build diff windows
 * from the raw delta data. 
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see     SVNDiffWindow
 * @see     SVNDiffInstruction
 */
public class SVNDiffWindowBuilder {

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
                    myInstructions = myInstructions == null ? new byte[0] : myInstructions;
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
    public void accept(FSFile file) throws SVNException, IOException {       
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
                    myInstructions = myInstructions == null ? new byte[0] : myInstructions;
                    myDiffWindow = createDiffWindow(myOffsets, myInstructions);
                } 
                myState = DONE;
            default:
                // all is read.
        }
    }

    public boolean accept(InputStream is, ISVNDeltaConsumer consumer, String path) throws SVNException {       
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
        if (!window.hasInstructions()) {
            return;
        }
        long[] offsets = new long[5];
        offsets[0] = window.getSourceViewOffset();
        offsets[1] = window.getSourceViewLength();
        offsets[2] = window.getTargetViewLength();
        offsets[3] = window.getInstructionsLength();
        offsets[4] = window.getNewDataLength();
        for(int i = 0; i < offsets.length; i++) {
            SVNDiffInstruction.writeInt(os, offsets[i]);
        }
        saveInstructions(window, os);
    }

    private static void saveInstructions(SVNDiffWindow window, OutputStream bos) throws IOException {
        bos.write(window.getInstructionsData());
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

    private static void readInt(FSFile file, int[] target, int index) throws SVNException, IOException {
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
        instructions = instructions == null ? new byte[0] : instructions;
		SVNDiffWindow window = new SVNDiffWindow(offsets[0], offsets[1], offsets[2], 
				instructions.length,
				offsets[4]);
        window.setInstructionsData(instructions);
		return window;
	}
	
}
