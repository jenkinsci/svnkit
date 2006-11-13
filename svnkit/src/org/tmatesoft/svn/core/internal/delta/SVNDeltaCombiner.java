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
package org.tmatesoft.svn.core.internal.delta;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.zip.InflaterInputStream;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.delta.SVNRangeTree.SVNRangeListNode;
import org.tmatesoft.svn.core.internal.io.fs.FSFile;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.diff.SVNDiffInstruction;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNDeltaCombiner {
    
    private SVNDiffWindow myWindow;
    
    private ByteBuffer myWindowData;    
    private ByteBuffer myNextWindowInstructions;
    private ByteBuffer myNextWindowData;
    private ByteBuffer myTarget;
    private ByteBuffer myRealTarget;
    private ByteBuffer myReadWindowBuffer;
    
    private SVNRangeTree myRangeTree;
    private SVNOffsetsIndex myOffsetsIndex;
    private SVNDiffInstruction[] myWindowInstructions;
    private SVNDiffInstruction myInstructionTemplate;
    
    public SVNDeltaCombiner() {
        myRangeTree = new SVNRangeTree();
        myWindowInstructions = new SVNDiffInstruction[10];
        myInstructionTemplate = new SVNDiffInstruction(0,0,0);
        myOffsetsIndex = new SVNOffsetsIndex();
        
        myNextWindowData = ByteBuffer.allocate(1024*20);
    }

    public void reset() {
        myWindow = null;
        myWindowData = null;
        myReadWindowBuffer = null;
        myNextWindowData = clearBuffer(myNextWindowData);
        myNextWindowInstructions = null;
        myTarget = null;
        myRealTarget = null;
        
        myRangeTree.dispose();
    }
    
    public SVNDiffWindow readWindow(FSFile file, int version) throws SVNException {
        myReadWindowBuffer = clearBuffer(myReadWindowBuffer);
        myReadWindowBuffer = ensureBufferSize(myReadWindowBuffer, 4096);
        long position = 0;
        try {
            position = file.position();
            file.read(myReadWindowBuffer);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_CORRUPT_WINDOW);
            SVNErrorManager.error(err, e);
        }
        myReadWindowBuffer.flip();
        long sourceOffset = readLongOffset(myReadWindowBuffer);
        int sourceLength = readOffset(myReadWindowBuffer);
        int targetLength = readOffset(myReadWindowBuffer);
        int instructionsLength = readOffset(myReadWindowBuffer);
        int dataLength = readOffset(myReadWindowBuffer);
        if (sourceOffset < 0 || sourceLength < 0 || targetLength < 0 || instructionsLength < 0 || dataLength < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_CORRUPT_WINDOW);
            SVNErrorManager.error(err);
        }
        position += myReadWindowBuffer.position();
        file.seek(position);

        myReadWindowBuffer = clearBuffer(myReadWindowBuffer);
        myReadWindowBuffer = ensureBufferSize(myReadWindowBuffer, instructionsLength + dataLength);
        myReadWindowBuffer.limit(instructionsLength + dataLength);
        try {
            file.read(myReadWindowBuffer);
        } catch (IOException e) {
            SVNDebugLog.getDefaultLog().error(e);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_CORRUPT_WINDOW);
            SVNErrorManager.error(err, e);
        }
        myReadWindowBuffer.position(0);
        myReadWindowBuffer.limit(myReadWindowBuffer.capacity());
        if (version == 1) {
            // decompress instructions and new data, put back to the buffer.
            try {
                int[] lenghts = decompress(instructionsLength, dataLength);
                instructionsLength = lenghts[0];
                dataLength = lenghts[1];
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_CORRUPT_WINDOW);
                SVNErrorManager.error(err, e);
            }
        }
        SVNDiffWindow window = new SVNDiffWindow(sourceOffset, sourceLength, targetLength, instructionsLength, dataLength);
        window.setData(myReadWindowBuffer);
        return window;
    }

    private int[] decompress(int instructionsLength, int dataLength) throws IOException {
        int originalPosition = myReadWindowBuffer.position();
        int realInstructionsLength = readOffset(myReadWindowBuffer);
        byte[] instructionsData = new byte[realInstructionsLength];
        byte[] data = null;
        int realDataLength = 0;
        int compressedLength = instructionsLength - (myReadWindowBuffer.position() - originalPosition);
        if (realInstructionsLength == compressedLength) {
            myReadWindowBuffer.get(instructionsData, 0, realInstructionsLength);
        } else {
            byte[] compressedData = new byte[compressedLength];
            myReadWindowBuffer.get(compressedData, 0, compressedLength);
            InflaterInputStream is = new InflaterInputStream(new ByteArrayInputStream(compressedData));
            int read = 0;
            while(read < realInstructionsLength) {
                read += is.read(instructionsData, read, realInstructionsLength - read);
            }
        }
        if (dataLength > 0) {
            originalPosition = myReadWindowBuffer.position();
            realDataLength = readOffset(myReadWindowBuffer);
            compressedLength = dataLength - (myReadWindowBuffer.position() - originalPosition);
            data = new byte[realDataLength];
            if (compressedLength == realDataLength) {
                myReadWindowBuffer.get(data, 0, realDataLength);
            } else {
                byte[] compressedData = new byte[compressedLength];
                myReadWindowBuffer.get(compressedData, 0, compressedLength);
                InflaterInputStream is = new InflaterInputStream(new ByteArrayInputStream(compressedData));
                int read = 0;
                while(read < realDataLength) {
                    read += is.read(data, read, realDataLength - read);
                }
            }
        }
        myReadWindowBuffer = clearBuffer(myReadWindowBuffer);
        myReadWindowBuffer = ensureBufferSize(myReadWindowBuffer, realInstructionsLength + realDataLength);
        myReadWindowBuffer.put(instructionsData);
        if (data != null) {
            myReadWindowBuffer.put(data);
        }
        myReadWindowBuffer.position(0);
        myReadWindowBuffer.limit(myReadWindowBuffer.capacity());
        return new int[] {realInstructionsLength, realDataLength};
    }

    public void skipWindow(FSFile file) throws SVNException {
        myReadWindowBuffer = clearBuffer(myReadWindowBuffer);
        myReadWindowBuffer = ensureBufferSize(myReadWindowBuffer, 4096);
        long position = 0;
        try {
            position = file.position();
            file.read(myReadWindowBuffer);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_CORRUPT_WINDOW);
            SVNErrorManager.error(err, e);
        }
        myReadWindowBuffer.flip();
        if (readLongOffset(myReadWindowBuffer) < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_CORRUPT_WINDOW);
            SVNErrorManager.error(err);
        }
        if (readOffset(myReadWindowBuffer) < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_CORRUPT_WINDOW);
            SVNErrorManager.error(err);
        }
        if (readOffset(myReadWindowBuffer) < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_CORRUPT_WINDOW);
            SVNErrorManager.error(err);
        }
        int instructionsLength = readOffset(myReadWindowBuffer);
        int dataLength = readOffset(myReadWindowBuffer);
        if (instructionsLength < 0 || dataLength < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_CORRUPT_WINDOW);
            SVNErrorManager.error(err);
        }
        position += myReadWindowBuffer.position();
        file.seek(position + dataLength + instructionsLength);
        myReadWindowBuffer = clearBuffer(myReadWindowBuffer);
    }
    
    // when true, there is a target and my window.
    public ByteBuffer addWindow(SVNDiffWindow window) {
        // 1.
        // if window doesn't has cpfrom source apply it to empty file and save results to the target.
        // and we're done.
        if (window.getSourceViewLength() == 0 || !window.hasCopyFromSourceInstructions()) {
            // apply window, make sure target not less then getTargetViewLength.
            myTarget = clearBuffer(myTarget);
            myTarget = ensureBufferSize(myTarget, window.getTargetViewLength());
            window.apply(new byte[0], myTarget.array());
            // and then apply myWindow if any.
            ByteBuffer result = null;
            if (myWindow != null) {
                myRealTarget = clearBuffer(myRealTarget);
                myRealTarget = ensureBufferSize(myRealTarget, myWindow.getTargetViewLength());
                myWindow.apply(myTarget.array(), myRealTarget.array());
                result = myRealTarget;
            } else {
                result = myTarget;
            }
            result.position(0);
            int tLength = myWindow != null ? myWindow.getTargetViewLength() : window.getTargetViewLength();
            result.limit(tLength);
            return result;
        }
        
        // 2.
        // otherwise combine window with myWindow, and save it in place of myWindow.
        // we're not done.
        if (myWindow != null) {
            myWindow = combineWindows(window);
            return null;
        } 
        
        // 3. 
        // if we do not have myWindow yet, just save window as myWindow.
        // make sure window is 'copied', so that its full data goes to our buffer.
        // and also make sure that myWindowData has enough free space for that window.
        // we're not done.
        myWindowData = clearBuffer(myWindowData);
        myWindowData = ensureBufferSize(myWindowData, window.getDataLength());
        myWindow = window.clone(myWindowData);
        return null;
    }

    private SVNDiffWindow combineWindows(SVNDiffWindow window /* A, B - myWindow */) {
        myNextWindowInstructions = clearBuffer(myNextWindowInstructions);
        myNextWindowData = clearBuffer(myNextWindowData);
        
        int targetOffset = 0;
        myWindowInstructions = window.loadDiffInstructions(myWindowInstructions);
        createOffsetsIndex(myWindowInstructions, window.getInstructionsCount());
        
        SVNRangeTree rangeIndexTree = myRangeTree;
        rangeIndexTree.dispose();
    
        for(Iterator instructions = myWindow.instructions(true); instructions.hasNext();) {
            SVNDiffInstruction instruction = (SVNDiffInstruction) instructions.next();
            if (instruction.type != SVNDiffInstruction.COPY_FROM_SOURCE) {
                myNextWindowInstructions = ensureBufferSize(myNextWindowInstructions, 10);
                instruction.writeTo(myNextWindowInstructions);
                if (instruction.type == SVNDiffInstruction.COPY_FROM_NEW_DATA) {
                    myNextWindowData = ensureBufferSize(myNextWindowData, instruction.length);
                    myWindow.writeNewData(myNextWindowData, instruction.offset, instruction.length);
                }
            } else {
                int offset = instruction.offset;
                int limit = instruction.offset + instruction.length;
                int tgt_off = targetOffset;
                rangeIndexTree.splay(offset);
                SVNRangeListNode listTail = rangeIndexTree.buildRangeList(offset, limit);
                SVNRangeListNode listHead = listTail.head;
                for(SVNRangeListNode range = listHead; range != null; range = range.next) {
                    if (range.kind == SVNRangeListNode.FROM_TARGET) {
                        myInstructionTemplate.type = SVNDiffInstruction.COPY_FROM_TARGET;
                        myInstructionTemplate.length = range.limit - range.offset;
                        myInstructionTemplate.offset = range.targetOffset;
                        myNextWindowInstructions = ensureBufferSize(myNextWindowInstructions, 10);
                        myInstructionTemplate.writeTo(myNextWindowInstructions);
                    } else {
                        copySourceInstructions(range.offset, range.limit, tgt_off, window, myWindowInstructions);
                    }
                    tgt_off += range.limit - range.offset;
                }
                assertCondition(tgt_off == targetOffset + instruction.length, "assert #1");
                rangeIndexTree.insert(offset, limit, targetOffset);
                rangeIndexTree.disposeList(listHead);
            }
            targetOffset += instruction.length;
        }
        // build window from 'next' buffers and replace myWindow with the new one.
        myNextWindowData.flip();
        myNextWindowInstructions.flip();
        int instrLength = myNextWindowInstructions.limit();
        int newDataLength = myNextWindowData.limit();
        myWindowData = clearBuffer(myWindowData);
        myWindowData = ensureBufferSize(myWindowData, instrLength + newDataLength);
        myWindowData.put(myNextWindowInstructions);
        myWindowData.put(myNextWindowData);
        myWindowData.position(0); // no need to set 'limit'...
        
        myWindow = new SVNDiffWindow(window.getSourceViewOffset(), window.getSourceViewLength(), myWindow.getTargetViewLength(), instrLength, newDataLength);
        myWindow.setData(myWindowData);
        
        myNextWindowInstructions = clearBuffer(myNextWindowInstructions);
        myNextWindowData = clearBuffer(myNextWindowData);
        return myWindow;
    }

    private void copySourceInstructions(int offset, int limit, int targetOffset, SVNDiffWindow window, SVNDiffInstruction[] windowInsructions) {
        int firstInstuctionIndex = findInstructionIndex(myOffsetsIndex, offset);
        int lastInstuctionIndex = findInstructionIndex(myOffsetsIndex, limit - 1);
        
        for(int i = firstInstuctionIndex; i <= lastInstuctionIndex; i++) {
            SVNDiffInstruction instruction = windowInsructions[i];
            int off0 = myOffsetsIndex.offsets[i];
            int off1 = myOffsetsIndex.offsets[i + 1];
            
            int fix_offset = offset > off0 ? offset - off0 : 0;
            int fix_limit = off1 > limit ? off1 - limit : 0;
            assertCondition(fix_offset + fix_limit < instruction.length, "assert #7");
            
            if (instruction.type != SVNDiffInstruction.COPY_FROM_TARGET) {
                int oldOffset = instruction.offset;
                int oldLength = instruction.length;
                
                instruction.offset += fix_offset;
                instruction.length = oldLength - fix_offset - fix_limit; 
                
                myNextWindowInstructions = ensureBufferSize(myNextWindowInstructions, 10);
                instruction.writeTo(myNextWindowInstructions);
                if (instruction.type == SVNDiffInstruction.COPY_FROM_NEW_DATA) {
                    myNextWindowData = ensureBufferSize(myNextWindowData, instruction.length);
                    window.writeNewData(myNextWindowData, instruction.offset, instruction.length);
                }
                instruction.offset = oldOffset;
                instruction.length = oldLength;
            } else {
                assertCondition(instruction.offset < off0, "assert #8");
                if (instruction.offset + instruction.length - fix_limit <= off0) {
                    copySourceInstructions(instruction.offset + fix_offset, 
                                           instruction.offset + instruction.length - fix_limit, 
                                           targetOffset, window, windowInsructions);
                } else {
                    int patternLength = off0 - instruction.offset;
                    int patternOverlap = fix_offset % patternLength;
                    assertCondition(patternLength > patternOverlap, "assert #9");
                    int fix_off = fix_offset;
                    int tgt_off = targetOffset;
                    
                    if (patternOverlap >= 0) {
                        int length = Math.min(instruction.length - fix_offset - fix_limit, patternLength - patternOverlap);
                        copySourceInstructions(instruction.offset + patternOverlap, 
                                               instruction.offset + patternOverlap + length, 
                                               tgt_off, window, windowInsructions);
                        tgt_off += length;
                        fix_off += length;
                    }
                    assertCondition(fix_off + fix_limit <= instruction.length, "assert #A");
                    if (patternOverlap > 0 && fix_off + fix_limit < instruction.length) {
                        int length = Math.min(instruction.length - fix_offset - fix_limit, patternOverlap);
                        copySourceInstructions(instruction.offset, 
                                               instruction.offset + length, 
                                               tgt_off, window, windowInsructions);
                        tgt_off += length;
                        fix_off += length;
                    }
                    assertCondition(fix_off + fix_limit <= instruction.length, "assert #B");
                    if (fix_off + fix_limit < instruction.length) {
                        myInstructionTemplate.type = SVNDiffInstruction.COPY_FROM_TARGET;
                        myInstructionTemplate.length = instruction.length - fix_off - fix_limit;
                        myInstructionTemplate.offset = tgt_off - patternLength;
                        myNextWindowInstructions = ensureBufferSize(myNextWindowInstructions, 10);
                        myInstructionTemplate.writeTo(myNextWindowInstructions);
                    }
                    
                }
            }
            targetOffset += instruction.length - fix_offset - fix_limit;
        }
        
    }

    private void createOffsetsIndex(SVNDiffInstruction[] instructions, int length) {
        if (myOffsetsIndex == null) {
            myOffsetsIndex = new SVNOffsetsIndex();
        }
        myOffsetsIndex.clear();
        int offset = 0;
        for (int i = 0; i < length; i++) {
            SVNDiffInstruction instruction = instructions[i];
            myOffsetsIndex.addOffset(offset);
            offset += instruction.length;
        }
        myOffsetsIndex.addOffset(offset);
    }
    
    private int findInstructionIndex(SVNOffsetsIndex offsets, int offset) {
        int lo = 0;
        int hi = offsets.length - 1;
        int op = (lo + hi)/2;
        
        assertCondition(offset < offsets.offsets[offsets.length - 1], "assert #2");
        
        for (; lo < hi; op = (lo + hi)/2 ) {
            int thisOffset = offsets.offsets[op];
            int nextOffset = offsets.offsets[op + 1];
            if (offset < thisOffset) {
                hi = op;
            } else if (offset > nextOffset) {
                lo = op;
            } else {
                if (offset == nextOffset) {
                    op++;
                }
                break;
            }
        }
        assertCondition(offsets.offsets[op] <= offset && offset < offsets.offsets[op + 1], "assert #3");
        return op;
    }
    
    private ByteBuffer clearBuffer(ByteBuffer b) {
        if (b != null) {
            b.clear();
        }
        return b;
    }
    
    private ByteBuffer ensureBufferSize(ByteBuffer buffer, int dataLength) {
        if (buffer == null || buffer.remaining() < dataLength) {
            ByteBuffer data = buffer != null ? 
                    ByteBuffer.allocate((buffer.position() + dataLength)*3/2) : 
                    ByteBuffer.allocate(dataLength*3/2);
            data.clear();
            if (buffer != null) {
                data.put(buffer.array(), 0, buffer.position());
            }
            buffer = data;
        }        
        return buffer;
    }

    private int readOffset(ByteBuffer buffer) {
        buffer.mark();
        int offset = 0;
        byte b;
        while(buffer.hasRemaining()) {
            b = buffer.get();
            offset = (offset << 7) | (b & 0x7F);
            if ((b & 0x80) != 0) {
                continue;
            }
            return offset;
        }
        buffer.reset();
        return -1;
    }

    private long readLongOffset(ByteBuffer buffer) {
        buffer.mark();
        long offset = 0;
        byte b;
        while(buffer.hasRemaining()) {
            b = buffer.get();
            offset = (offset << 7) | (b & 0x7F);
            if ((b & 0x80) != 0) {
                continue;
            }
            return offset;
        }
        buffer.reset();
        return -1;
    }
    
    static void assertCondition(boolean condition, String message) {
        if (!condition) {
            SVNDebugLog.getDefaultLog().error(message);
            SVNDebugLog.getDefaultLog().error(new Exception(message));
        }
    }
    
    private static class SVNOffsetsIndex {
        
        public int length;
        public int[] offsets;
        
        public SVNOffsetsIndex() {
            offsets = new int[10];
        }
        
        public void clear() {
            length = 0;
        }
        
        public void addOffset(int offset) {
            if (length >= offsets.length) {
                int[] newOffsets = new int[length*3/2];
                System.arraycopy(offsets, 0, newOffsets, 0, length);
                offsets = newOffsets;
            }
            offsets[length] = offset;
            length++;
        }
    }

}
