package de.regnis.q.sequence.line;

import java.io.*;

/**
 * @author Marc Strapetz
 */
public interface QSequenceLineRAData {

	long length() throws IOException;

	void get(byte[] bytes, long offset, long length) throws IOException;

	InputStream read(long offset, long length) throws IOException;
}