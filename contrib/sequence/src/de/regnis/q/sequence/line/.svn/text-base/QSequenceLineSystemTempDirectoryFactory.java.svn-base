package de.regnis.q.sequence.line;

import java.io.File;
import java.io.IOException;

/**
 * @author Marc Strapetz
 */
public class QSequenceLineSystemTempDirectoryFactory implements QSequenceLineTempDirectoryFactory {

	// Fields =================================================================

	private File tempDirectory;

	// Setup ==================================================================

	public QSequenceLineSystemTempDirectoryFactory() {
	}

	// Implemented ============================================================

	public File getTempDirectory() throws IOException {
		if (tempDirectory == null) {
			int tries = 0;
			for (;;) {
				try {
					tempDirectory = File.createTempFile("q.sequence.line.", ".temp" + tries);
					tempDirectory.delete();
					break;
				}
				catch (IOException ex) {
					tries++;
					if (tries > 100) {
						throw ex;
					}
				}
			}
		}

		return tempDirectory;
	}

	public void close() {
		tempDirectory.delete();
	}
}
