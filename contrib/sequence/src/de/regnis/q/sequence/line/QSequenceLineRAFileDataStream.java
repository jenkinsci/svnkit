package de.regnis.q.sequence.line;

import java.io.*;

/**
 * @author Marc Strapetz
 */
final class QSequenceLineRAFileDataStream extends InputStream {

	// Fields =================================================================

	private final RandomAccessFile myFile;
	private long offset;
	private int length;

	// Setup ==================================================================

	public QSequenceLineRAFileDataStream(RandomAccessFile myFile, long offset, int length) {
		this.myFile = myFile;
		this.length = length;
		this.offset = offset;
	}

	// Implemented ============================================================

	public int read() throws IOException {
		byte[] buffer = new byte[]{-1};
		read(buffer, 0, 1);
		return buffer[0];
	}

	public int read(byte[] buffer, int userOffset, int userLength) throws IOException {
		if (length <= 0) {
			return -1;
		}
		int available = (int)(myFile.length() - offset);
		int toRead = Math.min(available, length);
		toRead = Math.min(userLength, toRead);
		length -= toRead;

		long pos = myFile.length();
		myFile.seek(offset);
		int result = myFile.read(buffer, userOffset, toRead);
		myFile.seek(pos);
		offset += toRead;
		return result;
	}

	// Accessing ==============================================================

	public void reset(long offset, int length) {
		this.offset = offset;
		this.length = length;
	}
}