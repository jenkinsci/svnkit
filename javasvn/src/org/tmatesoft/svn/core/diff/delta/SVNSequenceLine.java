package org.tmatesoft.svn.core.diff.delta;

/**
 * @author Marc Strapetz
 */
public class SVNSequenceLine {

	// Fields =================================================================

	private final int myFrom;
	private final int myTo;
	private final byte[] myBytes;

	// Setup ==================================================================

	public SVNSequenceLine(int from, int to, byte[] bytes) {
		this.myFrom = from;
		this.myTo = to;
		this.myBytes = bytes;
	}

	// Accessing ==============================================================

	public int getFrom() {
		return myFrom;
	}

	public int getTo() {
		return myTo;
	}

	public byte[] getBytes() {
		return myBytes;
	}

	// Implemented ============================================================

	public boolean equals(Object obj) {
		// Must be because of caching media! Find something better!
		final byte[] otherBytes = ((SVNSequenceLine)obj).getBytes();
		if (myBytes.length != otherBytes.length) {
			return false;
		}

		for (int index = 0; index < myBytes.length; index++) {
			if (myBytes[index] != otherBytes[index]) {
				return false;
			}
		}

		return true;
	}
    
	public int hashCode() {
		int hashCode = 0;
		for (int index = 0; index < myBytes.length; index++) {
			hashCode = 31 * hashCode + myBytes[index];
		}

		return hashCode;
	}

	public String toString() {
		return new String(myBytes);
	}
}