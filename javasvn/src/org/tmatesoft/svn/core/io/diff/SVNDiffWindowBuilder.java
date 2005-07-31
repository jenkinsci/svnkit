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

package org.tmatesoft.svn.core.io.diff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDiffWindowBuilder {
	
	private static final int MAX_DATA_CHUNK_LENGTH = 100*1024;

    public static SVNDiffWindowBuilder newInstance() {
		return new SVNDiffWindowBuilder();
	}
	
	private static final int HEADER = 0;
	private static final int OFFSET = 1;
	private static final int INSTRUCTIONS = 2;
	private static final int DONE = 3;
    
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
    
    public void reset() {
        reset(HEADER);
    }
	
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
	
	public boolean isBuilt() {
		return myState == DONE;
	}
	
	public SVNDiffWindow getDiffWindow() {
		return myDiffWindow;
	}
	
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
                    SVNErrorManager.error(e.getMessage());
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
                            SVNErrorManager.error(e.getMessage());
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
                        SVNErrorManager.error(e.getMessage());
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
                    SVNErrorManager.error(e.getMessage());
                }
                SVNFileUtil.closeFile(myNewDataStream);
                reset(1);
                break;
            default:
                SVNDebugLog.logInfo("invalid diff window builder state: " + myState);
                return false;
        }
        return true;
    }
    
    public static void save(SVNDiffWindow window, OutputStream os) throws IOException {
        os.write(HEADER_BYTES);
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
        os.write(bos.toByteArray());
    }
    
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
//        SVNDiffInstruction[] instructions = {new SVNDiffInstruction(SVNDiffInstruction.COPY_FROM_NEW_DATA, dataLength, 0)};
        SVNDiffInstruction[] instructions = (SVNDiffInstruction[]) instructionsList.toArray(new SVNDiffInstruction[instructionsList.size()]);
        return new SVNDiffWindow(0, 0, totalLength, instructions, totalLength);
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

	private static SVNDiffWindow createDiffWindow(int[] offsets, byte[] instructions) {
		SVNDiffWindow window = new SVNDiffWindow(offsets[0], offsets[1], offsets[2], 
				createInstructions(instructions),
				offsets[4]);
		return window;
	}

	private static SVNDiffInstruction[] createInstructions(byte[] bytes) {
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
