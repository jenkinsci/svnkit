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

import de.regnis.q.sequence.line.QSequenceLineRAData;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNSequenceLineRAData implements QSequenceLineRAData {

	private final ISVNRAData myData;

	public SVNSequenceLineRAData(ISVNRAData myData) {
		this.myData = myData;
	}

	public long length() throws IOException {
		return myData.length();
	}

	public void get(byte[] bytes, long offset, long length) throws IOException {
		final InputStream stream = read(offset, length);

		for (int pos = 0; pos < length;) {
			pos += stream.read(bytes, pos, (int)(length - pos));
		}
	}

	public InputStream read(long offset, long length) throws IOException {
		return myData.read(offset, length);
	}
}