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

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.internal.wc.ISVNInputFile;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * Represents where in the current svndiff data block each
 * representation is.
 *  
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRepresentationState {
    ISVNInputFile file;
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

    public FSRepresentationState(ISVNInputFile file, long start, long offset, long end, int version, int index) {
        this.file = file;
        this.start = start;
        this.offset = offset;
        this.end = end;
        this.version = version;
        chunkIndex = index;
    }

    /* Build an array of FSRepresentationState objects in 'result' giving the delta
     * reps from firstRep to a self-compressed rep. 
     * NOTE: result list must not be null! If it's a PLAIN text returns a rep state 
     * we find at the end of the chain, or to null if the final delta representation 
     * is self-compressed. 
     */
    public static FSRepresentationState buildRepresentationList(FSRepresentation firstRep, LinkedList result, File reposRootDir) throws SVNException {
        ISVNInputFile file = null;
        FSRepresentation rep = new FSRepresentation(firstRep);
        try{
            while(true){
                file = FSReader.openAndSeekRepresentation(rep, reposRootDir);
                FSRepresentationArgs repArgs = readRepresentationLine(file);
                /* Create the rep_state for this representation. */
                FSRepresentationState repState = new FSRepresentationState();
                repState.file = file;
                repState.start = file.getFilePointer();
                repState.offset = repState.start;
                repState.end = repState.start + rep.getSize();
                if(!repArgs.isDelta){
                    /* This is a plaintext, so just return the current repState. */
                    return repState;
                }
                /* We are dealing with a delta, find out what version. */
                byte[] buffer = new byte[4];
                int r = file.read(buffer);
                if(!(buffer[0] == 'S' && buffer[1] == 'V' && buffer[2] == 'N' && r == 4)){
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed svndiff data in representation");
                    SVNErrorManager.error(err);
                }
                repState.version = buffer[3];
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
            SVNFileUtil.closeFile(file);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }catch(SVNException svne){
            SVNFileUtil.closeFile(file);
            throw svne;
        }
        return null;
    }

    /* Read the next line from a file and parse it as a text
     * representation entry. Return parsed args.
     */
    private static FSRepresentationArgs readRepresentationLine(ISVNInputFile file) throws SVNException {
        try{
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Malformed representation header");
            String line = FSReader.readNextLine(file, 160);
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
                SVNErrorManager.error(err);
            }
            if(!FSConstants.REP_DELTA.equals(args[0])){
                SVNErrorManager.error(err);
            }
            try{
                repArgs.myBaseRevision = Long.parseLong(args[1]);
                repArgs.myBaseOffset = Long.parseLong(args[2]);
                repArgs.myBaseLength = Long.parseLong(args[3]);
            }catch(NumberFormatException nfe){
                SVNErrorManager.error(err);
            }
            return repArgs;
        }catch(SVNException svne){
            SVNFileUtil.closeFile(file);
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
