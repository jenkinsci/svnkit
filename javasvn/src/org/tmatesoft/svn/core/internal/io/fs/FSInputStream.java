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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSInputStream extends InputStream {
    private LinkedList myRepStateList = new LinkedList();

    private int myChunkIndex;
    private boolean isChecksumFinalized;
    
    private String myHexChecksum;
    private long myLength;
    private long myOffset;
    
    private MessageDigest myDigest;
    private ByteBuffer myBuffer;
    
    private SVNDeltaCombiner myCombiner;
    
    private FSInputStream(SVNDeltaCombiner combiner, FSRepresentation representation, FSFS owner) throws SVNException {
        myCombiner = combiner;
        myChunkIndex = 0;
        isChecksumFinalized = false;
        myHexChecksum = representation.getHexDigest();
        myOffset = 0;
        myLength = representation.getExpandedSize();
        try {
            myDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", nsae.getLocalizedMessage());
            SVNErrorManager.error(err, nsae);
        }
        
        try {
            buildRepresentationList(representation, myRepStateList, owner);
        } catch(SVNException svne){
            /* Something terrible has happened while building rep list, 
             * need to close any files still opened 
             */
            close();
            throw svne;
        }
    }
    
    public static InputStream createDeltaStream(SVNDeltaCombiner combiner, FSRevisionNode fileNode, FSFS owner) throws SVNException {
        if(fileNode == null) {
            return SVNFileUtil.DUMMY_IN;
        } else if (fileNode.getType() != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Attempted to get textual contents of a *non*-file node");
            SVNErrorManager.error(err);
        }
        FSRepresentation representation = fileNode.getTextRepresentation(); 
        if(representation == null) {
            return SVNFileUtil.DUMMY_IN; 
        }
        return new FSInputStream(combiner, representation, owner);
    }

    public static InputStream createDeltaStream(SVNDeltaCombiner combiner, FSRepresentation fileRep, FSFS owner) throws SVNException {
        if(fileRep == null) {
            return SVNFileUtil.DUMMY_IN;
        }
        return new FSInputStream(combiner, fileRep, owner);
    }

    public int read(byte[] buf, int offset, int length) throws IOException {
        try { 
            return readContents(buf, offset, length); 
        } catch(SVNException svne){
            throw new IOException(svne.getMessage());
        }
    }
    
    public int read() throws IOException {
        byte[] buf = new byte[1];
        int r = 0;
        try{
            r = readContents(buf, 0, 1);
        }catch(SVNException svne){
            throw new IOException(svne.getMessage());
        }
        return r == 0 ? -1 : (int)(buf[0] & 0xFF);
    }
    
    private int readContents(byte[] buf, int offset, int length) throws SVNException {
        length = getContents(buf, offset, length);

        if(!isChecksumFinalized){
            myDigest.update(buf, offset, length);
            myOffset += length;
        
            if(myOffset == myLength){
                isChecksumFinalized = true;
                String hexDigest = SVNFileUtil.toHexDigest(myDigest);

                if (!myHexChecksum.equals(hexDigest)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Checksum mismatch while reading representation:\n   expected:  {0}\n     actual:  {1}", new Object[]{myHexChecksum, hexDigest});
                    SVNErrorManager.error(err);
                }
            }
        }
        
        return length;
    }
    
    private int getContents(byte[] buffer, int offset, int length) throws SVNException {
        int remaining = length;
        int targetPos = offset;

        while(remaining > 0){
            
            if(myBuffer != null && myBuffer.hasRemaining()) {
                int copyLength = Math.min(myBuffer.remaining(), remaining);
                /* Actually copy the data. */
                myBuffer.get(buffer, targetPos, copyLength);
                targetPos += copyLength;
                remaining -= copyLength;
            } else {
                FSRepresentationState resultState = (FSRepresentationState)myRepStateList.getFirst();
                if(resultState.offset == resultState.end) {
                    break;
                }
                myCombiner.reset();
                for(ListIterator states = myRepStateList.listIterator(); states.hasNext();){
                    FSRepresentationState curState = (FSRepresentationState)states.next();

                    while(curState.chunkIndex < myChunkIndex) {
                        myCombiner.skipWindow(curState.file);
                        curState.chunkIndex++;
                        curState.offset = curState.file.position();
                        if(curState.offset >= curState.end){
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Reading one svndiff window read beyond the end of the representation");
                            SVNErrorManager.error(err);
                        }
                    }
                    SVNDiffWindow window = myCombiner.readWindow(curState.file);
                    ByteBuffer target = myCombiner.addWindow(window);
                    curState.chunkIndex++;
                    curState.offset = curState.file.position();
                    if (target != null) {
                        myBuffer = target;
                        myChunkIndex++;
                        break;
                    }
                }
            }
        }
        return targetPos;
    }
    
    public void close() {
        for(Iterator states = myRepStateList.iterator(); states.hasNext();) {
            FSRepresentationState state = (FSRepresentationState)states.next();
            if(state.file != null){
                state.file.close();
            }
            states.remove();
        }
    }
    
    private FSRepresentationState buildRepresentationList(FSRepresentation firstRep, LinkedList result, FSFS owner) throws SVNException {
        FSFile file = null;
        FSRepresentation rep = new FSRepresentation(firstRep);
        ByteBuffer buffer = ByteBuffer.allocate(4);
        try{
            while(true){
                file = owner.openAndSeekRepresentation(rep);
                FSRepresentationArgs repArgs = readRepresentationLine(file);
                FSRepresentationState repState = new FSRepresentationState();
                repState.file = file;
                repState.start = file.position();
                repState.offset = repState.start;
                repState.end = repState.start + rep.getSize();
                if(!repArgs.isDelta){
                    return repState;
                }
                buffer.clear();
                int r = file.read(buffer);
                
                byte[] header = buffer.array();
                if(!(header[0] == 'S' && header[1] == 'V' && header[2] == 'N' && r == 4)){
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed svndiff data in representation");
                    SVNErrorManager.error(err);
                }
                repState.version = header[3];
                repState.chunkIndex = 0;
                repState.offset+= 4;
                /* Push this rep onto the list.  If it's self-compressed, we're done. */
                result.addLast(repState);
                if(repArgs.isDeltaVsEmpty){
                    return null;
                }
                rep.setRevision(repArgs.myBaseRevision);
                rep.setOffset(repArgs.myBaseOffset);
                rep.setSize(repArgs.myBaseLength);
                rep.setTxnId(null);
            }
        }catch(IOException ioe){
            file.close();
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }catch(SVNException svne){
            file.close();
            throw svne;
        }
        return null;
    }

    private static FSRepresentationArgs readRepresentationLine(FSFile file) throws SVNException {
        try{
            String line = file.readLine(160);//FSReader.readNextLine(file, 160);
            FSRepresentationArgs repArgs = new FSRepresentationArgs();
            repArgs.isDelta = false;
            if(FSConstants.REP_PLAIN.equals(line)){
                return repArgs;
            }
            if(FSConstants.REP_DELTA.equals(line)){
                /* This is a delta against the empty stream. */
                repArgs.isDelta = true;
                repArgs.isDeltaVsEmpty = true;
                return repArgs;
            }
            repArgs.isDelta = true;
            repArgs.isDeltaVsEmpty = false;
            /* We have hopefully a DELTA vs. a non-empty base revision. */
            String[] args = line.split(" ");
            if(args.length < 4){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed representation header");
                SVNErrorManager.error(err);
            }
            if(!FSConstants.REP_DELTA.equals(args[0])){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed representation header");
                SVNErrorManager.error(err);
            }
            try{
                repArgs.myBaseRevision = Long.parseLong(args[1]);
                repArgs.myBaseOffset = Long.parseLong(args[2]);
                repArgs.myBaseLength = Long.parseLong(args[3]);
            }catch(NumberFormatException nfe){
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed representation header");
                SVNErrorManager.error(err);
            }
            return repArgs;
        }catch(SVNException svne){
            file.close();
            throw svne;
        }
    }
    
    private static class FSRepresentationArgs {
        boolean isDelta;
        boolean isDeltaVsEmpty;
        long myBaseRevision;
        long myBaseOffset;
        long myBaseLength;
    }

}
