package org.tmatesoft.svn.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Marc Strapetz
 */
public class LoggingInputStream extends InputStream {

	// Fields =================================================================

	private final InputStream myInputStream;
	private final StringBuffer myBuffer;
	private final LoggingStreamLogger myLogger;

	// Setup ==================================================================

	public LoggingInputStream(InputStream inputStream,
			LoggingStreamLogger logger) {
		myInputStream = inputStream;
		myBuffer = logger != null ? new StringBuffer() : null;
		myLogger = logger;
	}

	// Implemented ============================================================

	public int read() throws IOException {
		int read = myInputStream.read();
		if (myBuffer != null && read >= 0) {
			myBuffer.append((char) read);
            if (myBuffer.length() > 8192) {
                log();
            }
		}
		return read;
	}

	public void close() throws IOException {
		myInputStream.close();
	}

	public int read(byte[] b, int off, int len) throws IOException {
		int read = myInputStream.read(b, off, len);
		if (myBuffer != null && read >= 0) {
			myBuffer.append(new String(b, off, read));
            if (myBuffer.length() > 8192) {
                log();
            }
		}
		return read;
	}

	public int available() throws IOException {
		return myInputStream.available();
	}

	public void reset() throws IOException {
		myInputStream.reset();
	}

	public boolean markSupported() {
		return myInputStream.markSupported();
	}

	public void mark(int readlimit) {
		myInputStream.mark(readlimit);
	}

	public long skip(long n) throws IOException {
		return myInputStream.skip(n);
	}

	// Accessing ==============================================================

	public void log() {
		if (myBuffer != null && myBuffer.length() > 0) {
			myLogger.logStream(myBuffer.toString(), false);
			myBuffer.delete(0, myBuffer.length());
		}
	}
}