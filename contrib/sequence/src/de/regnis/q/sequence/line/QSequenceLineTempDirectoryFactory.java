package de.regnis.q.sequence.line;

import java.io.File;
import java.io.IOException;

/**
 * @author Marc Strapetz
 */
public interface QSequenceLineTempDirectoryFactory {
	File getTempDirectory() throws IOException;

	void close();
}
