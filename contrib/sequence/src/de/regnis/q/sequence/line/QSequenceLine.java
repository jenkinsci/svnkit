package de.regnis.q.sequence.line;

/**
 * @author Marc Strapetz
 */
public final class QSequenceLine {

	// Fields =================================================================

	private final long from;
	private final byte[] bytes;

	// Setup ==================================================================

	public QSequenceLine(long from, byte[] bytes) {
		this.from = from;
		this.bytes = bytes;
	}

	// Accessing ==============================================================

	public long getFrom() {
		return from;
	}

	public long getTo() {
		return from + bytes.length - 1;
	}

	public int getLength() {
		return bytes.length;
	}

	public byte[] getBytes() {
		return bytes;
	}

	// Implemented ============================================================

	public boolean equals(Object obj) {
		// Must be because of caching media! Find something better!
		final byte[] otherBytes = ((QSequenceLine)obj).getBytes();
		if (bytes.length != otherBytes.length) {
			return false;
		}

		for (int index = 0; index < bytes.length; index++) {
			if (bytes[index] != otherBytes[index]) {
				return false;
			}
		}

		return true;
	}
    
	public int hashCode() {
		int hashCode = 0;
		for (int index = 0; index < bytes.length; index++) {
			hashCode = 31 * hashCode + bytes[index];
		}

		return hashCode;
	}

	public String toString() {
		return new String(bytes);
	}
}