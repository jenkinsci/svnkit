package org.tmatesoft.svn.core.diff.delta;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Marc Strapetz
 */
public class SVNSequenceLineReader {

	// Fields =================================================================

	private final boolean skipEOL;
	private byte[] buffer;

	// Setup ==================================================================

	public SVNSequenceLineReader(boolean skipEndOfLine) {
		this(8192, skipEndOfLine);
	}

	public SVNSequenceLineReader(int initialBufferSize, boolean skipEol) {
		this.skipEOL = skipEol;
		buffer = new byte[initialBufferSize];
	}

	// Static =================================================================

	public SVNSequenceLine[] read(InputStream rawStream) throws IOException {
		final List lines = new ArrayList();
		final BufferedInputStream stream = new BufferedInputStream(rawStream);
		try {
			int pushBack = -1;
			int from = 0;
			int length = 0;
			int eolLength = 0;
            int lastLength = 0;
			for (; ;) {
				int ch = pushBack;
				if (ch != -1) {
					pushBack = -1;
				}
				else {
					ch = stream.read();
				}

				if (ch != -1) {
					append(length, (byte) (ch & 0xff));
					length++;
				}

				switch (ch) {
				case '\r':
					pushBack = stream.read();
					if (pushBack == '\n') {
						append(length, (byte) (pushBack & 0xff));
						length++;
						eolLength++;
						pushBack = -1;
					}
				case '\n':
					eolLength++;
				case -1:
					if (length > 0) {
						final int actualLength = skipEOL ? length - eolLength : length;
						byte[] bytes = new byte[actualLength];
						System.arraycopy(buffer, 0, bytes, 0, actualLength);
						lines.add(new SVNSequenceLine(from, from + actualLength - 1, bytes));
                        lastLength = length;
					}                    
					from = from + length;
					length = 0;
					eolLength = 0;
                    
				}

				if (ch == -1) {
                    lastLength--;
                    if (skipEOL && lastLength < buffer.length && lastLength >= 0) {
                        if (buffer[lastLength] == '\r' || buffer[lastLength] == '\n') {
                            lines.add(new SVNSequenceLine(from, from, new byte[0]));
                        }
                    }
					break;
				}
			}

			return (SVNSequenceLine[])lines.toArray(new SVNSequenceLine[lines.size()]);
		}
		finally {
			stream.close();
		}
	}

	// Utils ==================================================================

	private void append(int position, byte ch) {
		if (position >= buffer.length) {
			final byte[] newArray = new byte[buffer.length * 2];
			System.arraycopy(buffer, 0, newArray, 0, buffer.length);
			buffer = newArray;
		}
		buffer[position] = ch;
	}
}