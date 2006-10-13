package de.regnis.q.sequence.line;

import java.io.*;

import de.regnis.q.sequence.line.simplifier.*;

/**
 * @author Marc Strapetz
 */
final class QSequenceLineReader {

	// Fields =================================================================

	private byte[] buffer;

	// Setup ==================================================================

	public QSequenceLineReader() {
		this(8192);
	}

	public QSequenceLineReader(int initialBufferSize) {
		buffer = new byte[initialBufferSize];
	}

	// Static =================================================================

	public void read(InputStream rawStream, QSequenceLineCache cache, QSequenceLineSimplifier simplifier) throws IOException {
		final BufferedInputStream stream = new BufferedInputStream(rawStream);
		try {
			int pushBack = -1;
			int from = 0;
			int length = 0;
			for (; ;) {
				int ch = pushBack;
				if (ch != -1) {
					pushBack = -1;
				}
				else {
					ch = stream.read();
				}

				if (ch != -1) {
					append(length, (byte)(ch & 0xff));
					length++;
				}

				switch (ch) {
				case '\r':
					pushBack = stream.read();
					if (pushBack == '\n') {
						append(length, (byte)(pushBack & 0xff));
						length++;
						pushBack = -1;
					}
				case '\n':
				case -1:
					if (length > 0) {
						final byte[] bytes;
						bytes = new byte[length];
						System.arraycopy(buffer, 0, bytes, 0, length);
						cache.addLine(new QSequenceLine(from, bytes, simplifier));
					}
					from = from + length;
					length = 0;
				}

				if (ch == -1) {
					break;
				}
			}
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