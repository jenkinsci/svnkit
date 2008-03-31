package de.regnis.q.sequence.line;

import de.regnis.q.sequence.line.simplifier.*;

/**
 * @author Marc Strapetz
 */
public final class QSequenceLine {

	// Fields =================================================================

	private final long from;
	private final byte[] contentBytes;
	private final byte[] compareBytes;

	// Setup ==================================================================

	public QSequenceLine(long from, byte[] contentBytes, QSequenceLineSimplifier simplifier) {
		this.from = from;
		this.contentBytes = contentBytes;
		this.compareBytes = simplifier.simplify(contentBytes);
	}

	// Accessing ==============================================================

	public long getFrom() {
		return from;
	}

	public long getTo() {
		return from + contentBytes.length - 1;
	}

	public int getContentLength() {
		return contentBytes.length;
	}

	public byte[] getContentBytes() {
		return contentBytes;
	}

	/**
	 * @deprecated
	 */
	public byte[] getBytes() {
		return getContentBytes();
	}

	public int getCompareHash() {
		return new String(compareBytes).hashCode();
	}

	// Implemented ============================================================

	public boolean equals(Object obj) {
		// Must be because of caching media! Find something better!
		final byte[] otherBytes = ((QSequenceLine)obj).compareBytes;
		if (compareBytes.length != otherBytes.length) {
			return false;
		}

		for (int index = 0; index < compareBytes.length; index++) {
			if (compareBytes[index] != otherBytes[index]) {
				return false;
			}
		}

		return true;
	}

	public int hashCode() {
		int hashCode = 0;
		for (int index = 0; index < compareBytes.length; index++) {
			hashCode = 31 * hashCode + compareBytes[index];
		}

		return hashCode;
	}

	public String toString() {
		return new String(contentBytes);
	}
}