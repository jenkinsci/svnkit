package de.regnis.q.sequence.line;

import java.io.*;
import java.util.*;

import de.regnis.q.sequence.line.simplifier.*;

/**
 * @author Marc Strapetz
 */
final class QSequenceLineMemoryCache implements QSequenceLineCache {

	// Constants ==============================================================

	public static QSequenceLineMemoryCache read(InputStream is, QSequenceLineSimplifier simplifier) throws IOException {
		final QSequenceLineMemoryCache cache = new QSequenceLineMemoryCache();
		final QSequenceLineReader reader = new QSequenceLineReader();
		reader.read(is, cache, simplifier);
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