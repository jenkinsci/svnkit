/*
 * ====================================================================
 * Copyright (c) 2000-2008 SyntEvo GmbH, info@syntevo.com
 * All rights reserved.
 *
 * This software is licensed as described in the file SEQUENCE-LICENSE,
 * which you should have received as part of this distribution. Use is
 * subject to license terms.
 * ====================================================================
 */

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
