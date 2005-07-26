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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNAllDeltaGenerator implements ISVNDeltaGenerator {

	// Accessing ==============================================================

	public void generateDiffWindow(String commitPath, ISVNEditor consumer, ISVNRAData workFile, ISVNRAData baseFile) throws SVNException {
        long length = workFile.length();
		SVNDiffWindow window = SVNDiffWindowBuilder.createReplacementDiffWindow(length);
		OutputStream os = consumer.textDeltaChunk(commitPath, window);
        if (length == 0) {
            SVNFileUtil.closeFile(os);
            consumer.textDeltaEnd(commitPath);
            return;
        }
		InputStream is = null;
        byte[] buffer = new byte[32*1024];
		try {
			is = workFile.readAll();
            while(true) {
                int read = is.read(buffer);
                if (read <= 0) {
                    break;
                }
                os.write(buffer, 0, read);
            }
		} catch (IOException e) {
            SVNErrorManager.error(e.getMessage());
		}
		finally {
            SVNFileUtil.closeFile(os);
            SVNFileUtil.closeFile(is);
		}
		consumer.textDeltaEnd(commitPath);
	}
}