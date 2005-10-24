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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNEditor;


/**
 * <b>ISVNDeltaGenerator</b> is a common interface for different
 * types of delta generators. It may be:
 * <ul>
 * <li> a binary data delta generator (see {@link SVNAllDeltaGenerator}) 
 * <li> a text delta generator (see {@link SVNSequenceDeltaGenerator})
 * </ul>
 *  
 * <p>
 * A general use of delta generators: calculating the Working Copy 
 * changes against base files during a commit.  
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNDeltaGenerator {
	/**
     * Calculates differences between a working file and a base
     * one and generates a diff window (windows). 
     * 
     * <p>
     * Actually, it may be more than just one diff window - if 
     * new data is too "weighty", a several smaller size delta chunks 
     * will be produced instead.
     * 
     * <p>
     * <code>commitPath</code> is a relative path of the file on which
     * the delta is calculated. Given the two versions of this file - 
     * the working one (<code>workFile</code>) and a base one (<code>baseFile</code>) - 
     * the method generates diff window(s) and provides it/them to the <code>consumer</code>. 
     * That is, on every diff window generated the method calls: 
     * <pre class="javacode">
     *     OutputStream os = consumer.textDeltaChunk(commitPath, window);</pre>
     * And then writes new text/binary data bytes to the received output stream. 
     * 
     * <p>
     * After providing all diff windows to the <code>consumer</code>, the method
     * finishes with:  
     * <pre class="javacode">
     *     consumer.textDeltaEnd(commitPath);</pre>
     * Such is the common behaviour for this method.
     * 
     * <p>  
     * Use {@link SVNRAFileData} to wrap files.
     * 
     * @param  commitPath      a file path  
     * @param  consumer        an editor that receives the generated
     *                         dif window(s)
     * @param  workFile        a working version of the file (target file)
     * @param  baseFile        a base (prestine) version of the file
     * @throws SVNException    if an i/o error occurred
	 */
    void generateDiffWindow(String commitPath, ISVNEditor consumer, ISVNRAData workFile, ISVNRAData baseFile) throws SVNException;
}