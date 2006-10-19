package de.regnis.q.sequence.line;

import java.io.IOException;
import java.io.InputStream;

import de.regnis.q.sequence.line.simplifier.*;

/**
 * @author Marc Strapetz
 */
final class QSequenceLineFileSystemCache implements QSequenceLineCache {

	// Static =================================================================

	public static QSequenceLineFileSystemCache create(QSequenceLineRAData data, QSequenceLineTempDirectoryFactory tempDirectoryFactory, int maximumBytesInMemory, int maximumSegmentSize, QSequenceLineSimplifier simplifier) throws IOException {
		final QSequenceLineFileSystemCache cache = new QSequenceLineFileSystemCache(data, tempDirectoryFactory, maximumBytesInMemory, maximumSegmentSize, simplifier);
		final QSequenceLineReader reader = new QSequenceLineReader();
		final InputStream stream = data.read(0, data.length());
		reader.read(stream, cache, simplifier);
		stream.close();
		return cache;
	}

	// Fields =================================================================

	private final QSequenceLineRAData data;
	private final QSequenceLineSimplifier simplifier;
	private final QSequenceLineFileSystemCacheSegments segments;

	private int lineCount;

	// Setup ==================================================================

	private QSequenceLineFileSystemCache(QSequenceLineRAData data, QSequenceLineTempDirectoryFactory tempDirectoryFactory, int maximumBytesInMemory, int maximumSegmentSize, QSequenceLineSimplifier simplifier) {
		this.data = data;
		this.simplifier = simplifier;
		this.segments = new QSequenceLineFileSystemCacheSegments(tempDirectoryFactory, maximumBytesInMemory, maximumSegmentSize);
	}

	// Implemented ============================================================

	public void close() throws IOException {
		segments.close();
	}

	public void addLine(QSequenceLine line) throws IOException {
		if (lineCount >= Integer.MAX_VALUE) {
			throw new IOException("Too many lines.");
		}

		segments.setFromLengthHash(lineCount, line.getFrom(), line.getContentLength(), line.getCompareHash());
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
		return new QSequenceLine(from, bytes, simplifier);
	}

	public int getLineHash(int index) throws IOException {
		return segments.getHash(index);
	}
}