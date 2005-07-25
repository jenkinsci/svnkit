package de.regnis.q.sequence.line;

import java.io.*;
import java.util.*;

/**
 * @author Marc Strapetz
 */
public final class QSequenceLineResult {

	// Fields =================================================================

	private final List blocks;
	private final QSequenceLineCache leftCache;
	private final QSequenceLineCache rightCache;

	// Setup ==================================================================

	public QSequenceLineResult(List blocks, QSequenceLineCache leftCache, QSequenceLineCache rightCache) {
		this.blocks = blocks;
		this.leftCache = leftCache;
		this.rightCache = rightCache;
	}

	// Accessing ==============================================================

	public List getBlocks() {
		return Collections.unmodifiableList(blocks);
	}

	public QSequenceLineCache getLeftCache() {
		return leftCache;
	}

	public QSequenceLineCache getRightCache() {
		return rightCache;
	}

	public void close() throws IOException {
		leftCache.close();
		rightCache.close();
	}
}