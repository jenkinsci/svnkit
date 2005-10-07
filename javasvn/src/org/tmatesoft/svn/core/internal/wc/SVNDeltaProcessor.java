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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowApplyBaton;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDeltaProcessor {
    
    private File myChunkFile;
    private ChunkOutputStream myChunkStream;
    private Collection myWindows;
    private String myChecksum;

    public OutputStream textDeltaChunk(File chunkFile, SVNDiffWindow window) throws SVNException {
        if (myChunkStream != null) {
            myWindows.add(window);
            return myChunkStream;
        }
        myWindows = new LinkedList();
        myChunkFile = chunkFile;
        myChunkStream = new ChunkOutputStream(SVNFileUtil.openFileForWriting(myChunkFile));
        myWindows.add(window);
        
        return myChunkStream;
    }
    
    public String getChecksum() {
        return myChecksum;
    }
    
    public boolean textDeltaEnd(File baseFile, File targetFile, boolean computeChecksum) throws SVNException {
        if (myChunkStream != null) {
            try {
                myChunkStream.reallyClose();
            } catch (IOException e) {
                SVNErrorManager.error(e.getMessage());
            } finally {
                myChunkStream = null;
            }
        }
        if (myChunkFile == null) {
            close();
            return false;
        }
        try {
            if (myWindows == null || myWindows.isEmpty()) {
                close();
                return false;
            }
            InputStream data = SVNFileUtil.openFileForReading(myChunkFile);
            MessageDigest digest = null;
            try {
                digest = computeChecksum ? MessageDigest.getInstance("MD5") : null;
            } catch (NoSuchAlgorithmException e) {
            }
            SVNDiffWindowApplyBaton baton = SVNDiffWindowApplyBaton.create(baseFile, targetFile, digest);
            try {
                for (Iterator windows = myWindows.iterator(); windows.hasNext();) {
                    SVNDiffWindow window = (SVNDiffWindow) windows.next();
                    window.apply(baton, data);
                }
            } finally {
                SVNFileUtil.closeFile(data);
                myChecksum = baton.close();
            }
        } finally {
            close();
        }
        return true;
    }
    
    public void close() {
        if (myChunkFile != null) {
            myChunkFile.delete();
        }
        myChunkFile = null;
        myWindows = null;
        myChunkStream = null;
    }
    
    private static class ChunkOutputStream extends FilterOutputStream {
        public ChunkOutputStream(OutputStream out) {
            super(out);
        }
        public void close() {
        }
        
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            out.flush();
        }
        public void reallyClose() throws IOException {
            super.close();
        }
    }

}
