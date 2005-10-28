package de.regnis.q.sequence.line;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Marc Strapetz
 */
final class QSequenceLineFileSystemCache implements QSequenceLineCache {

	// Static =================================================================

	public static QSequenceLineFileSystemCache create(QSequenceLineRAData data, QSequenceLineTempDirectoryFactory tempDirectoryFactory, byte[] customEolBytes, int maximumBytesInMemory, int maximumSegmentSize) throws IOException {
		final QSequenceLineFileSystemCache cache = new QSequenceLineFileSystemCache(data, tempDirectoryFactory, maximumBytesInMemory, maximumSegmentSize);
		final QSequenceLineReader reader = new QSequenceLineReader(customEolBytes);
		final InputStream stream = data.read(0, data.length());
		reader.read(stream, cache);
		stream.close();
		return cache;
	}

	// Fields =================================================================

	private final QSequenceLineRAData data;
	private final QSequenceLineFileSystemCacheSegments segments;

	private int lineCount;

	// Setup ==================================================================

	private QSequenceLineFileSystemCache(QSequenceLineRAData data, QSequenceLineTempDirectoryFactory tempDirectoryFactory, int maximumBytesInMemory, int maximumSegmentSize) {
		this.data = data;
		this.segments = new QSequenceLineFileSystemCacheSegments(tempDirectoryFactory, maximumBytesInMemory, maximumSegmentSize);
	}

	// Implemented ============================================================

	public void close() throws IOException {
		segments.close();
	}

	public void addLine(QSequenceLine line) throws IOException {
		segments.setFromLengthHash(lineCount, line.getFrom(), line.getLength(), new String(line.getBytes()).hashCode());
		if (lineCount >= Integer.MAX_VALUE) {
			throw new IOException("Too many lines."); 
		}

		lineCount++;
	}

	public int getLineCount() {
		return lineCount;
	}

	public QSequenceLine getLine(int index) throws IOException {
		final long from = segments.getFrom(index);
		final int length = segments.getLength(index);
		final byte[] bytes = new byte[length];
		data.get(bytes, from, length);
		return new QSequenceLine(from, bytes);
	}

	public int getLineHash(int index) throws IOException {
		return segments.getHash(index);
	}
}