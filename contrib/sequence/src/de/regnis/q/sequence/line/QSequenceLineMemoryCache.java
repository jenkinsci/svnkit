package de.regnis.q.sequence.line;

import java.io.*;
import java.util.*;

/**
 * @author Marc Strapetz
 */
final class QSequenceLineMemoryCache implements QSequenceLineCache {

	// Constants ==============================================================

	public static QSequenceLineMemoryCache read(InputStream is, byte[] customEolBytes) throws IOException {
		final QSequenceLineMemoryCache cache = new QSequenceLineMemoryCache();
		final QSequenceLineReader reader = new QSequenceLineReader(customEolBytes);
		reader.read(is, cache);
		return cache;
	}

	// Fields =================================================================

	private final List lines;

	// Setup ==================================================================

	public QSequenceLineMemoryCache() {
		this.lines = new ArrayList();
	}

	// Implemented ============================================================

	public void addLine(QSequenceLine line) {
		lines.add(line);
	}

	public int getLineCount() {
		return lines.size();
	}

	public int getLineHash(int index) {
		return 0;
	}

	public QSequenceLine getLine(int index) {
		return (QSequenceLine)lines.get(index);
	}

	public void close() {
	}
}