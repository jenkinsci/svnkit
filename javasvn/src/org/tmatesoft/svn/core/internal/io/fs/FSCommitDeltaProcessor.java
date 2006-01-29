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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowApplyBaton;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSCommitDeltaProcessor extends FSBufferStream{
    private InputStream mySourceStream;
    
    private OutputStream myTargetStream;
    
    private SVNDiffWindowApplyBaton myApplyBaton;
    
    private SVNDiffWindow myCurrentWindow;
    
    private boolean isClosed;
    
    public FSCommitDeltaProcessor(InputStream sourceStream, OutputStream targetStream){
        super();
        mySourceStream = sourceStream;
        myTargetStream = targetStream;
        myApplyBaton = SVNDiffWindowApplyBaton.create(mySourceStream, myTargetStream, null);
        isClosed = true;
    }
    
    public OutputStream handleDiffWindow(SVNDiffWindow window){
        myCurrentWindow = window;
        isClosed = false;
        return this;
    }
    
    public void close() throws IOException {
        if(!isClosed){
            super.close();
            if(!(super.myBufferLength == 0 && myCurrentWindow.getTargetViewLength() == 0)){
                try{
                    myCurrentWindow.apply(myApplyBaton, new ByteArrayInputStream(super.myBuffer == null ? new byte[0] : super.myBuffer));
                    super.myBufferLength = 0;
                    super.myBuffer = null;
                }catch(SVNException svne){
                    throw new IOException(svne.getMessage());
                }
            }
            isClosed = true;
        }
    }
    
    public void onTextDeltaEnd(){
        myApplyBaton.close();
    }
}
