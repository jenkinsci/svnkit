package de.regnis.q.sequence.line;

import java.io.File;
import java.io.IOException;

/**
 * @author Marc Strapetz
 */
public class QSequenceLineFixedTempDirectoryFactory implements QSequenceLineTempDirectoryFactory {

	// Fields =================================================================

	private final File tempDirectory;

	// Setup ==================================================================

	public QSequenceLineFixedTempDirectoryFactory(File tempDirectory) {
		this.tempDirectory = tempDirectory;
	}

	// Implemented ============================================================

	public File getTempDirectory() throws IOException {
		return tempDirectory;
	}

	public void close() {
	}
}
