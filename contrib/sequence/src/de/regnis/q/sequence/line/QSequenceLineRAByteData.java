package de.regnis.q.sequence.line;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Marc Strapetz
 */
public final class QSequenceLineRAByteData implements QSequenceLineRAData {

	// Constants ==============================================================

	public static final QSequenceLineRAByteData create(InputStream is) throws IOException {
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		for (;;) {
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

	public long length() throws IOException {
		return bytes.length;
	}

	public InputStream read(long offset, long length) throws IOException {
		return new ByteArrayInputStream(bytes, (int)offset, (int)length);
	}
}