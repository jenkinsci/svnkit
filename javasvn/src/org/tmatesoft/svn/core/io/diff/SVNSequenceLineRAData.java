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

import de.regnis.q.sequence.line.QSequenceLineRAData;


/**
 * The <b>SVNSequenceLineRAData</b> class is an adapter for <b>ISVNRAData</b> 
 * objects, so that they could be used with <b>de.regnis.q.sequence.line</b> 
 * classes. This adapter is used by the <b>SVNSequenceDeltaGenerator</b> class.  
 * 
 * @deprecated see {@link SVNDeltaGenerator}
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see     SVNSequenceDeltaGenerator
 * @see     ISVNRAData
 */
public class SVNSequenceLineRAData implements QSequenceLineRAData {

	private final ISVNRAData myData;
	
    /**
     * Creates an adapter for an <b>ISVNRAData</b> object. 
     * 
     * @param myData a JavaSVN random access storage representation object
	 */
	public SVNSequenceLineRAData(ISVNRAData myData) {
		this.myData = myData;
	}
	
    /**
     * Returns the length of the data represented by this object. 
     * 
     * @return             the length of the data in bytes 
     * @throws IOException if an i/o error occurred
	 */
	public long length() throws IOException {
		return myData.length();
	}
	
    /**
     * Reads a number of bytes from the specified offset in the data storage to
     * the buffer provided.
     * 
     * @param  bytes        a buffer to get the bytes
     * @param  offset       an offset in the data storage represented
     *                      by this object
     * @param  length       a number of bytes to read
     * @throws IOException  if an i/o error occurred
	 */
	public void get(byte[] bytes, long offset, long length) throws IOException {
		final InputStream stream = read(offset, length);

		for (int pos = 0; pos < length;) {
			pos += stream.read(bytes, pos, (int)(length - pos));
		}
	}
	
    /**
     * Reads a number of bytes from the specified offset in the data and 
     * returns an input sream to get the read bytes.
     * 
     * @param  offset       an offset in the data storage represented
     *                      by this object
     * @param  length       a number of bytes to read
     * @return              an input stream to get the read bytes
     * @throws IOException  if an i/o error occurred
	 */
	public InputStream read(long offset, long length) throws IOException {
		try {
            return myData.read(offset, length);
        } catch (SVNException e) {
            throw new IOException(e.getMessage());
        }
	}
}