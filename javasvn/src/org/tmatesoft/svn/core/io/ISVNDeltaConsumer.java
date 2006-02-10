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
package org.tmatesoft.svn.core.io;

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNDeltaConsumer {
    /**
     * Starts applying text delta(s) to an opened file. 
     *  
     * @param  path             a file path relative to the root       
     *                          directory opened by {@link #openRoot(long) openRoot()}                  
     * @param  baseChecksum     an MD5 checksum for the base file contents (before the
     *                          file is changed) 
     * @throws SVNException     if the calculated base file checksum didn't match the expected 
     *                          <code>baseChecksum</code> 
     */
    public void applyTextDelta(String path, String baseChecksum) throws SVNException;
    
    /**
     * Collects a next delta chunk. Returns an ouput stream to write diff window
     * instructions and new text data. If there are more than one windows for the file,
     * this method is called several times.
     * 
     * @param  path           a file path relative to the root       
     *                        directory opened by {@link #openRoot(long) openRoot()}
     * @param  diffWindow     a next diff window
     * @return                an output stream where instructions and new text data
     *                        will be written to
     * @throws SVNException
     */
    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException;
    
    /**
     * Finalizes collecting text delta(s) and applies them to file contents.  
     * 
     * @param  path           a file path relative to the root       
     *                        directory opened by {@link #openRoot(long) openRoot()}
     * @throws SVNException
     */
    public void textDeltaEnd(String path) throws SVNException;

}
