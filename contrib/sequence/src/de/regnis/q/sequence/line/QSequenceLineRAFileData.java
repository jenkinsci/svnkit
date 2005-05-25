package de.regnis.q.sequence.line;

import java.io.*;

/**
 * @author Marc Strapetz
 */
public final class QSequenceLineRAFileData implements QSequenceLineRAData {

	// Fields =================================================================

	private final RandomAccessFile randomAccessFile;

	private QSequenceLineRAFileDataStream stream;

	// Setup ==================================================================

	public QSequenceLineRAFileData(RandomAccessFile randomAccessFile) {
		this.randomAccessFile = randomAccessFile;
	}

	// Implemented ============================================================

	public long length() throws IOException {
		return randomAccessFile.length();
	}

	public InputStream read(long offset, long length) throws IOException {
		if (stream != null) {
			stream.reset(offset, (int)length);
		}
		else {
			stream = new QSequenceLineRAFileDataStream(randomAccessFile, offset, (int)length);
		}
		return stream;
	}
}