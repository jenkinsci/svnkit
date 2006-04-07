package de.regnis.q.sequence.line;

import java.io.*;
import junit.framework.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceLineReaderTest extends TestCase {

	// Accessing ==============================================================

	public void test() throws IOException {
		test("A simple string.", 1);
		test("Two\nlines.", 2);
		test("Three\nli\nnes.", 3);
		test("\nLine\n", 2);
		test("Line\r\n\r", 2);
		test("Line\r\n\r\n", 2);
		test("Line\r\r", 2);
		test("Line\r\r ", 3);
		test("Line\n\r\r", 3);
		test("\n\n\n", 3);
		test("", 0);

		test("Line", 1);
		test("Line\n", 1);
		test("Line\r\n", 1);
		test("Line\n\r", 2);
		test("Line\n\r\r", 3);
	}

	// Utils ==================================================================

	private void test(String testString, int expectedLineCount) throws IOException {
		final byte[] bytes = testString.getBytes();
		final QSequenceLineMemoryCache cache = new QSequenceLineMemoryCache();
		final QSequenceLineReader reader = new QSequenceLineReader(4);
		reader.read(new ByteArrayInputStream(bytes), cache);
		assertEquals(expectedLineCount, cache.getLineCount());

		for (int index = 0; index < cache.getLineCount(); index++) {
			final QSequenceLine line = cache.getLine(index);
			if (index == 0) {
				assertEquals(0, line.getFrom());
			}
			else if (index == cache.getLineCount() - 1) {
				assertEquals(bytes.length, line.getFrom() + line.getLength());
			}
			else {
				final int expectedTo = (int)cache.getLine(index - 1).getFrom() + cache.getLine(index - 1).getLength();
				assertEquals(expectedTo, line.getFrom());
			}

			for (int byteIndex = (int)line.getFrom(); byteIndex < line.getFrom() + line.getLength(); byteIndex++) {
				assertEquals(bytes[byteIndex], line.getBytes()[byteIndex - (int)line.getFrom()]);
			}
		}
	}
}