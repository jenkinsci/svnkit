package de.regnis.q.sequence.line;

import java.io.*;
import junit.framework.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceLineReaderTest extends TestCase {

	// Accessing ==============================================================

	public void test() throws IOException {
		test("A simple string.", 1, false);
		test("Two\nlines.", 2, false);
		test("Three\nli\nnes.", 3, false);
		test("\nLine\n", 2, false);
		test("Line\r\n\r", 2, false);
		test("Line\r\n\r\n", 2, false);
		test("Line\r\r", 2, false);
		test("Line\r\r ", 3, false);
		test("Line\n\r\r", 3, false);
		test("\n\n\n", 3, false);
		test("", 0, false);

		test("Line", 1, true);
		test("Line\n", 2, true);
		test("Line\r\n", 2, true);
		test("Line\n\r", 3, true);
		test("Line\n\r\r", 4, true);
	}

	// Utils ==================================================================

	private void test(String testString, int expectedLineCount, boolean skipEol) throws IOException {
		final byte[] bytes = testString.getBytes();
		byte[] customEolBytes = skipEol ? new byte[0] : null;
		final QSequenceLineMemoryCache cache = new QSequenceLineMemoryCache();
		final QSequenceLineReader reader = new QSequenceLineReader(4, customEolBytes);
		reader.read(new ByteArrayInputStream(bytes), cache);
		final QSequenceLineMemoryCache lines = cache;
		assertEquals(expectedLineCount, lines.getLineCount());

		for (int index = 0; index < lines.getLineCount(); index++) {
			final QSequenceLine line = lines.getLine(index);
			if (index == 0) {
				assertEquals(0, line.getFrom());
			}
			else if (index == lines.getLineCount() - 1) {
				assertEquals(bytes.length, line.getFrom() + line.getLength());
			}
			else {
				int expectedTo = (int)lines.getLine(index - 1).getFrom() + lines.getLine(index - 1).getLength();
				if (skipEol && expectedTo >= 0 && bytes[expectedTo] == '\r') {
					expectedTo++;
				}
				if (skipEol && expectedTo >= 0 && bytes[expectedTo] == '\n') {
					expectedTo++;
				}
				assertEquals(expectedTo, line.getFrom());
			}

			for (int byteIndex = (int)line.getFrom(); byteIndex < line.getFrom() + line.getLength(); byteIndex++) {
				assertEquals(bytes[byteIndex], line.getBytes()[byteIndex - (int)line.getFrom()]);
			}
		}
	}
}