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

import java.io.*;

/**
 * @author Marc Strapetz
 */
public interface QSequenceLineCache {
	int getLineCount();

	void addLine(QSequenceLine line) throws IOException;

	QSequenceLine getLine(int index) throws IOException;

	int getLineHash(int index) throws IOException;

	void close() throws IOException;
}