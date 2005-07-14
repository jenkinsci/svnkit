package org.tmatesoft.svn.core.test.diff;

import java.io.*;
import java.util.*;
import junit.framework.*;
import org.tmatesoft.svn.core.diff.delta.*;
import org.tmatesoft.svn.core.internal.diff.*;
import org.tmatesoft.svn.core.io.*;

/**
 * @author Marc Strapetz
 */
public class SVNSequenceDeltaGeneratorTest extends TestCase {

	public static String createLines(String text) {
		return createLines(text, "\n");
	}

	public static String createLines(String text, String eol) {
		final StringBuffer lines = new StringBuffer();
		for (int ch = 0; ch < text.length(); ch++) {
			lines.append(text.charAt(ch));
			lines.append(eol);
		}
		return lines.toString();
	}

	public void test() throws SVNException {
		test("abc", "bcd");
		test("abcde", "aabcef");
		test("abc", "xyz");
		test("a", "xay");
		test("xay", "a");
		test("a", "");
		test("", "x");
		test("harac", "hc");
		test("ahugeamountofcharacters", "separatedbynosemicolon");

		test("abc", "\r\n", "xyz", "\r\n");
		test("abc", "\r", "xyz", "\n");
		test("abc", "\n", "xyz", "\r\n");
	}

	private void test(String workFile, String baseFile) throws SVNException {
		test(workFile, "\n", baseFile, "\n");
	}

	private void test(String workFile, String workEol, String baseFile, String baseEol) throws SVNException {
		workFile = createLines(workFile, workEol);
		baseFile = createLines(baseFile, baseEol);

		final ISVNDeltaGenerator generator = new SVNSequenceDeltaGenerator();
		final DeltaConsumer consumer = new DeltaConsumer();
		generator.generateDiffWindow("", consumer, new RAData(workFile), new RAData(baseFile));

		final RAData testData = new RAData("");

		for (int index = 0; index < consumer.getWindows().size(); index++) {
			final SVNDiffWindow window = (SVNDiffWindow)consumer.getWindows().get(index);
			final ByteArrayOutputStream stream = (ByteArrayOutputStream)consumer.getStreams().get(index);
			window.apply(new RAData(baseFile), testData, new ByteArrayInputStream(stream.toByteArray()), 0);
		}

		assertEquals(workFile, testData.toString());
	}

	private static final class RAData implements ISVNRAData {

		private final StringBuffer myText;

		public RAData(String text) {
			this.myText = new StringBuffer(text);
		}

		public InputStream read(long offset, long length) throws IOException {
			return new ByteArrayInputStream(myText.toString().getBytes(), (int)offset, (int)length);
		}

		public void append(InputStream source, long length) throws IOException {
			for (int index = 0; index < length; index++) {
				myText.append((char)source.read());
			}
		}

		public long lastModified() {
			return 0;
		}

        public void close() throws IOException {
		}

		public long length() {
			return myText.toString().getBytes().length;
		}

		public String toString() {
			return myText.toString();
		}
	}

	private static final class DeltaConsumer implements ISVNDeltaConsumer {
		private final List windows = new ArrayList();
		private final List streams = new ArrayList();

		public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
			final ByteArrayOutputStream stream = new ByteArrayOutputStream();
			windows.add(diffWindow);
			streams.add(stream);
			return stream;
		}

		public void textDeltaEnd(String path) throws SVNException {
			try {
				((ByteArrayOutputStream)streams.get(streams.size() - 1)).close();
			}
			catch (IOException ex) {
				throw new SVNException(ex);
			}
		}

		public List getWindows() {
			return windows;
		}

		public List getStreams() {
			return streams;
		}
	}
}