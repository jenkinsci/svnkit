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

package org.tmatesoft.svn.core.internal.ws.fs;

import java.io.*;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.*;

/**
 * @author TMate Software Ltd.
 */
public class FSMerger {
    
    private static final String EOL = System.getProperty("line.separator");

	// Accessing ==============================================================

	public boolean isApplicable(ISVNEntry entry) {
		return entry instanceof FSEntry;
	}

	public int pretendMergeFiles(File base, File local, File latest) throws SVNException {
		if (base == null || local == null || latest == null) {
			throw new SVNException("At least one file is missing, can't merge");
		}

		return doMergeFiles(base, local, latest, null, "", "");
	}

	public int mergeFiles(File base, File local, File latest, File result, String start, String end) throws SVNException {
		if (base == null || local == null || latest == null) {
			throw new SVNException("At least one file is missing, can't merge");
		}

		return doMergeFiles(base, local, latest, result, start, end);
	}

	// Utils ==================================================================

	private int doMergeFiles(File base, File local, File latest, File result, String start, String end) throws SVNException {
		final String conflictStart = "<<<<<<< " + start;
		final String conflictEnd = ">>>>>>> " + end;
		final String conflictSeparator = "=======";

		final FSMergerBySequence merger = new FSMergerBySequence(conflictStart.getBytes(), conflictSeparator.getBytes(), conflictEnd.getBytes(), EOL.getBytes());
		try {
			final OutputStream resultStream = result != null ? (OutputStream)new FileOutputStream(result) : (OutputStream)new ByteArrayOutputStream();
			try {
				return merger.merge(new FileInputStream(base), new FileInputStream(local), new FileInputStream(latest), resultStream);
			}
			finally {
				resultStream.close();
			}
		}
		catch (IOException ex) {
			throw new SVNException(ex);
		}
	}
}
