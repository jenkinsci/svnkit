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
import java.nio.ByteBuffer;
import java.util.LinkedList;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * Represents where in the current svndiff data block each
 * representation is.
 *  
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRepresentationState {
    FSFile file;
    /* The starting offset for the raw svndiff/plaintext data minus header. */
    long start;
    /* The current offset into the file. */
    long offset;
    /* The end offset of the raw data. */
    long end;
    /* If a delta, what svndiff version? */
    int version;
    
    int chunkIndex;

    public FSRepresentationState() {
    }

    public FSRepresentationState(FSFile file, long start, long offset, long end, int version, int index) {
        this.file = file;
        this.start = start;
        this.offset = offset;
        this.end = end;
        this.version = version;
        chunkIndex = index;
    }

    public static FSRepresentationState buildRepresentationList(FSRepresentation firstRep, LinkedList result, FSFS owner) throws SVNException {
        FSFile file = null;
        FSRepresentation rep = new FSRepresentation(firstRep);
        try{
            while(true){
                file = owner.openAndSeekRepresentation(rep);//FSReader.openAndSeekRepresentation(rep, reposRootDir);
                FSRepresentationArgs repArgs = readRepresentationLine(file);
                /* Create the rep_state for this representation. */
                FSRepresentationState repState = new FSRepresentationState();
                repState.file = file;
                repState.start = file.position();//file.getFilePointer();
                repState.offset = repState.start;
                repState.end = repState.start + rep.getSize();
                if(!repArgs.isDelta){
                    return repState;
                }
                //byte[] buffer = new byte[4];
                ByteBuffer buffer = ByteBuffer.allocate(4);
                int r = file.read(buffer);
                
                buffer.flip();
                byte[] header = new byte[4];
                for(int i = 0; i < 4 && buffer.hasRemaining(); i++){
                    header[i] = buffer.get(i); 
                }
                
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
            //SVNFileUtil.closeFile(file);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }catch(SVNException svne){
            file.close();
            //SVNFileUtil.closeFile(file);
            throw svne;
        }
        return null;
    }

    /* Read the next line from a file and parse it as a text
     * representation entry. Return parsed args.
     */
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
            //SVNFileUtil.closeFile(file);
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
