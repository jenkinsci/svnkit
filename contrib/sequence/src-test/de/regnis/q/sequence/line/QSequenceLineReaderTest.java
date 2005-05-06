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
		final ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
		final QSequenceLineReader reader = new QSequenceLineReader(4, skipEol ? new byte[0] : null);
		final QSequenceLine[] lines = reader.read(stream);
		assertEquals(expectedLineCount, lines.length);

		for (int index = 0; index < lines.length; index++) {
			final QSequenceLine line = lines[index];
			if (index == 0) {
				assertEquals(0, line.getFrom());
			}
			else if (index == lines.length - 1) {
				assertEquals(bytes.length - 1, line.getTo());
			}
			else {
				int expectedTo = lines[index - 1].getTo() + 1;
				if (skipEol && expectedTo >= 0 && bytes[expectedTo] == '\r') {
					expectedTo++;
				}
				if (skipEol && expectedTo >= 0 && bytes[expectedTo] == '\n') {
					expectedTo++;
				}
				assertEquals(expectedTo, line.getFrom());
			}

			for (int byteIndex = line.getFrom(); byteIndex <= line.getTo(); byteIndex++) {
				assertEquals(bytes[byteIndex], line.getBytes()[byteIndex - line.getFrom()]);
			}
		}
	}
}