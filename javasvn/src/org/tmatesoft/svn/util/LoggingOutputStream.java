package org.tmatesoft.svn.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Marc Strapetz
 */
public class LoggingOutputStream extends OutputStream {

	// Fields =================================================================

	private final OutputStream myOutputStream;
	private final StringBuffer myBuffer;
	private final LoggingStreamLogger myLogger;

	// Setup ==================================================================

	public LoggingOutputStream(OutputStream outputStream,
			LoggingStreamLogger logger) {
		myOutputStream = outputStream;
		myBuffer = logger != null ? new StringBuffer() : null;
		myLogger = logger;
	}

	// Implemented ============================================================

	public void write(int b) throws IOException {
		myOutputStream.write(b);
		if (myBuffer != null) {
			myBuffer.append((char) b);
		}
	}

	public void close() throws IOException {
		myOutputStream.close();
	}

	public void write(byte[] b, int off, int len) throws IOException {
		myOutputStream.write(b, off, len);
		if (myBuffer != null) {
			myBuffer.append(new String(b, off, len));
		}
	}

	public void flush() throws IOException {
		myOutputStream.flush();
	}

	// Accessing ==============================================================

	public void log() {
		if (myBuffer != null && myBuffer.length() > 0) {
			myLogger.logStream(myBuffer.toString(), true);
			myBuffer.delete(0, myBuffer.length());
		}
	}
}