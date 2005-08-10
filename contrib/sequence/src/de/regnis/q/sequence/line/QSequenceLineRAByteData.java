package de.regnis.q.sequence.line;

import java.io.*;

/**
 * @author Marc Strapetz
 */
public final class QSequenceLineRAByteData implements QSequenceLineRAData {

	// Constants ==============================================================

	public static QSequenceLineRAByteData create(InputStream is) throws IOException {
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		for (; ;) {
			final int b = is.read();
			if (b == -1) {
				break;
			}

			os.write(b);
		}

		return new QSequenceLineRAByteData(os.toByteArray());
	}


	// Fields =================================================================

	private final byte[] bytes;

	// Setup ==================================================================

	public QSequenceLineRAByteData(byte[] bytes) {
		this.bytes = bytes;
	}

	// Implemented ============================================================

	public long length() {
		return bytes.length;
	}

	public void get(byte[] bytes, long offset, long length) {
		System.arraycopy(this.bytes, (int)offset, bytes, 0, (int)length);
	}

	public InputStream read(long offset, long length) {
		return new ByteArrayInputStream(bytes, (int)offset, (int)length);
	}
}