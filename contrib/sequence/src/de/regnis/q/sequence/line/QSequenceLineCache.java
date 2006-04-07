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