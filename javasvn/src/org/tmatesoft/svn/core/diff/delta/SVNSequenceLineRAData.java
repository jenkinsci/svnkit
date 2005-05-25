package org.tmatesoft.svn.core.diff.delta;

import java.io.*;

import de.regnis.q.sequence.line.*;

import org.tmatesoft.svn.core.diff.*;

/**
 * @author Marc Strapetz
 */
public class SVNSequenceLineRAData implements QSequenceLineRAData {

	private final ISVNRAData myData;

	public SVNSequenceLineRAData(ISVNRAData myData) {
		this.myData = myData;
	}

	public long length() throws IOException {
		return myData.length();
	}

	public InputStream read(long offset, long length) throws IOException {
		return myData.read(offset, length);
	}
}