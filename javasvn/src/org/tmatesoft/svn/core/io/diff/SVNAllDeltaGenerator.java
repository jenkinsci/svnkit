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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNAllDeltaGenerator implements ISVNDeltaGenerator {

	// Accessing ==============================================================

	public void generateDiffWindow(String commitPath, ISVNDeltaConsumer consumer, ISVNRAData workFile, ISVNRAData baseFile) throws SVNException {
        long length = workFile.length();
		SVNDiffWindow window = SVNDiffWindowBuilder.createReplacementDiffWindow(length);
        DebugLog.log("NEW FILE LENGTH: " + length);
		OutputStream os = consumer.textDeltaChunk(commitPath, window);
        OutputStream fos = null;
        if (length == 0) {
            try {
                os.close();
            } catch (IOException e1) {
            }
            consumer.textDeltaEnd(commitPath);
            return;
        }
		InputStream is = null;
		try {
			is = new BufferedInputStream(workFile.read(0, workFile.length()));
			SVNFileUtil.copy(is, os);
		}
		catch (IOException e) {
			throw new SVNException(e);
		}
		finally {
            try {
                os.close();
            }
            catch (IOException e) {
            }
            try {
                if (fos != null) {
                    fos.close();
                }
            }
            catch (IOException e) {
            }
			if (is != null) {
				try {
					is.close();
				}
				catch (IOException e) {
				}
			}
		}
		consumer.textDeltaEnd(commitPath);
	}
}