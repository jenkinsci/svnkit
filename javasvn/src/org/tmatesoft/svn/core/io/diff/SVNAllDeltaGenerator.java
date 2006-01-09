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
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;


/**
 * The <b>SVNAllDeltaGenerator</b> class is a delta generator that 
 * produces only full contents delta. This kind of delta is not a result of
 * comparing two sources, but one that contains instructions to copy 
 * bytes only from new data, where new data is the full contents of a file.
 * 
 * <p>
 * Used to generate diff windows for binary files (that are not generally
 * compared with base revision files), and for new text files, which contents
 * are represented as a delta versus empty contents.
 * @deprecated see {@link SVNDeltaGenerator}
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNAllDeltaGenerator implements ISVNDeltaGenerator {

    // Accessing ==============================================================
    /**
     * Generates a diff window (windows) containing instructions to apply
     * delta which is essentially the full contents of the target file. 
     * 
     * <p>
     * If the length of the working file represented by <code>workFile</code>
     * is more than 100K, then this method devides the full contents into 
     * a number of copy-from-new-data deltas, where for each delta you have
     * a 100K chunk of file contents.    
     * 
     * @param  commitPath     a file path  
     * @param  consumer       an editor that receives the generated
     *                        dif window(s)
     * @param  workFile       a working version of the file (target file)
     * @param  baseFile       a base file does not take part in
     *                        generating diff windows
     * @throws SVNException   if an i/o error occurred
     * @see                   ISVNDeltaGenerator#generateDiffWindow(String, ISVNEditor, ISVNRAData, ISVNRAData)
     * @see                   SVNDiffWindowBuilder#createReplacementDiffWindow(long)
     * @see                   SVNDiffWindowBuilder#createReplacementDiffWindows(long, int)
     */
	public void generateDiffWindow(String commitPath, ISVNEditor consumer, ISVNRAData workFile, ISVNRAData baseFile) throws SVNException {
        long length = workFile.length();
        if (length == 0) {
            SVNDiffWindow window = SVNDiffWindowBuilder.createReplacementDiffWindow(length);
            OutputStream os = consumer.textDeltaChunk(commitPath, window);
            SVNFileUtil.closeFile(os);
            consumer.textDeltaEnd(commitPath);
            return;
        }
        int maxWindowLenght = 1024*100; // 100K
        SVNDiffWindow[] windows = SVNDiffWindowBuilder.createReplacementDiffWindows(length, 1024*100);
		InputStream is = null;
        OutputStream os = null;
        byte[] newDataBuffer = new byte[maxWindowLenght];
		try {
			is = workFile.readAll();
            for (int i = 0; i < windows.length; i++) {
                SVNDiffWindow window = windows[i];
                os = consumer.textDeltaChunk(commitPath, window);
                is.read(newDataBuffer, 0, (int) window.getNewDataLength());
                os.write(newDataBuffer, 0, (int) window.getNewDataLength());
                SVNFileUtil.closeFile(os);
            }
		} catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
		}
		finally {
            SVNFileUtil.closeFile(os);
            SVNFileUtil.closeFile(is);
		}
		consumer.textDeltaEnd(commitPath);
	}
}