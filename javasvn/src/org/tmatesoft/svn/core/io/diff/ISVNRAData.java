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

import java.io.IOException;
import java.io.InputStream;

import org.tmatesoft.svn.core.SVNException;


/**
 * The <b>ISVNRAData</b> interface represents data storage 
 * that supports random access reading from and writing to it.
 * Used to provide random access to files and buffers upon which
 * delta is generated. 
 * 
 * @deprecated see {@link SVNDeltaGenerator}
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see     SVNRAFileData
 * @see     ISVNDeltaGenerator
 * 
 */
public interface ISVNRAData {
    /**
     * Reads the entire contents of this data storage.
     * 
     * @return              an input stream to read the entire 
     *                      contents
     * @throws SVNException
     */
    public InputStream readAll() throws SVNException;
    
    /**
     * Reads a number of bytes from the given position in this data 
     * storage. 
     * 
     * @param  offset        an offset in the storage to read from
     * @param  length        a number of bytes to read
     * @return               an input stream to read the bytes
     * @throws SVNException
     */
    public InputStream read(long offset, long length) throws SVNException;
    
    /**
     * Writes a number of the source bytes to the end of this
     * data storage.
     * 
     * @param source          a source input stream to read bytes
     * @param length          a number of bytes to read from 
     *                        <code>source</code> and append to this
     *                        data storage
     * @throws SVNException
     */
    public void append(InputStream source, long length) throws SVNException;
    
    /**
     * Returns the length in bytes of this data storage.
     * 
     * @return the length of this storage
     */
    public long length();
    
    /**
     * Returns the time that this data storage was last modified.
     * 
     * @return A long value representing the time the file was 
     *         last modified, measured in milliseconds since the 
     *         epoch (00:00:00 GMT, January 1, 1970), or 0L if 
     *         the file does not exist or if an I/O error occurs
     */
    public long lastModified();
    
    /**
     * Closes this RA data storage and releases any system 
     * resources associated with this storage. After closing
     * this data storage you can not perform any i/o operations. 
     *   
     * @throws IOException if an i/o error occurred
     */
    public void close() throws IOException;

}
