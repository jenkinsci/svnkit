package org.tmatesoft.svn.core.internal.wc;

import java.io.*;
import junit.framework.*;
import org.tmatesoft.svn.core.io.diff.*;

import de.regnis.q.sequence.line.*;

/**
 * @author Marc Strapetz
 */
public class FSMergerBySequenceTest extends TestCase {

	public void testBasic() throws Throwable {
		// Not modified
		testResultEol("", "", "", "", FSMergerBySequence.NOT_MODIFIED);
		testResultEol("a", "a", "a", "a", FSMergerBySequence.NOT_MODIFIED);
		testResultEol("abc", "abc", "abc", "abc", FSMergerBySequence.NOT_MODIFIED);

		// Merged ...
		testResultEol("abc", "xbc", "aby", "xby", FSMergerBySequence.MERGED);
		testResultEol("abcd", "xbcd", "abyd", "xbyd", FSMergerBySequence.MERGED);
		testResultEol("abc", "xbc", "ac", "xc", FSMergerBySequence.MERGED);
		testResultEol("abc", "xabc", "abc", "xabc", FSMergerBySequence.MERGED);
		testResultEol("abc", "abcx", "abc", "abcx", FSMergerBySequence.MERGED);
		testResultEol("abc", "axxxbc", "abc", "axxxbc", FSMergerBySequence.MERGED);
		testResultEol("abc", "axxxbc", "abyyyc", "axxxbyyyc", FSMergerBySequence.MERGED);
		testResultEol("abcde", "xaxbcxxxdex", "abyycdyyyye", "xaxbyycxxxdyyyyex", FSMergerBySequence.MERGED);
		testResultEol("The base line.", "The changed base-line.", "The base line with changes.", "The changed base-line with changes.", FSMergerBySequence.MERGED);
		testResultEol("abcd", "abxcd", "abcxd", "abxcxd", FSMergerBySequence.MERGED);
		testResultEol("abcd", "axbcd", "abxcyd", "axbxcyd", FSMergerBySequence.MERGED);
		testResultEol("abcde", "abxcdez", "abcxdyez", "abxcxdyez", FSMergerBySequence.MERGED);

		// Unreal conflicts
		testResultEol("abc", "abx", "abx", "abx", FSMergerBySequence.NOT_MODIFIED);
		testResultEol("abc", "ab", "ab", "ab", FSMergerBySequence.NOT_MODIFIED);
		testResultEol("abc", "b", "b", "b", FSMergerBySequence.NOT_MODIFIED);
		testResultEol("abc", "", "", "", FSMergerBySequence.NOT_MODIFIED);

		// Real Conflicts ...
		testResultEol("abc", "axc", "ayc", "a>x=y<c", FSMergerBySequence.CONFLICTED);
		testResultEol("abc", "axxc", "ayc", "a>xx=y<c", FSMergerBySequence.CONFLICTED);
		testResultEol("abc", "axxxc", "ayc", "a>xxx=y<c", FSMergerBySequence.CONFLICTED);
		testResultEol("abc", "axxxc", "ayyc", "a>xxx=yy<c", FSMergerBySequence.CONFLICTED);
		testResultEol("abc", "xbc", "yby", ">x=y<by", FSMergerBySequence.CONFLICTED);
		testResultEol("abc", "xbc", "bc", ">x=<bc", FSMergerBySequence.CONFLICTED);
		testResultEol("abc", "xbc", "", ">xbc=<", FSMergerBySequence.CONFLICTED);
		testResultEol("abc", "xbc", "bc", ">x=<bc", FSMergerBySequence.CONFLICTED);
		testResultEol("abc", "axxxbc", "aybyyyc", "a>xxx=y<byyyc", FSMergerBySequence.CONFLICTED);
		testResultEol("abc", "axxxbc", "ayyyyc", "a>xxxb=yyyy<c", FSMergerBySequence.CONFLICTED);
		testResultEol("abcd", "ybcd", "xabcd", ">y=xa<bcd", FSMergerBySequence.CONFLICTED);
		testResultEol("abcd", "xbcxxd", "ybcdyyy", ">x=y<bcxxdyyy", FSMergerBySequence.CONFLICTED);
		testResultEol("abcdefg", "xxxdxxx", "abyyyfg", ">xxxdxxx=abyyyfg<", FSMergerBySequence.CONFLICTED);
		testResultEol("abcdefghijk", "xxxdxxxhxxx", "abyyyfyyyjk", ">xxxdxxxhxxx=abyyyfyyyjk<", FSMergerBySequence.CONFLICTED);
		testResultEol("abcdefghijklmnop", "xxxdxxxhixklmxoxxx", "abyyyfghijkymyo", ">xxxdxxx=abyyyfg<hixkym>x=y<o>xxx=<", FSMergerBySequence.CONFLICTED);
		testResultEol("Some base text", "Many changes to the base", "Confusing the base text", ">Many changes t=C<o> th=nfusing th<e base", FSMergerBySequence.CONFLICTED);
	}

	public void testDifferentEOL() throws Throwable {
		testDifferentEols("", "\n", "", "\r", "", "\r\n", "", "\n", FSMergerBySequence.NOT_MODIFIED);
		testDifferentEols("abc", "\n", "xbc", "\n", "aby", "\r\n", "xby", "\r\n", FSMergerBySequence.MERGED);
		testDifferentEols("abc", "\r", "axc", "\n", "ayc", "\r\n", "a>x=y<c", "\r", FSMergerBySequence.CONFLICTED);
	}

	public void testAdjacentChangesVerifiedWithCommandLine() throws Throwable {
		testNoResultEol("abc", "abcd", "y", ">abcd=y<", FSMergerBySequence.CONFLICTED);

		testNoResultEol("abcd", "xbcd", "aycd", "xycd", FSMergerBySequence.MERGED);
		testNoResultEol("abcd", "xbcd", "aybcd", "xybcd", FSMergerBySequence.MERGED);
		testNoResultEol("abcd", "aycd", "bcd", "ycd", FSMergerBySequence.MERGED);
		testNoResultEol("abcd", "abcy", "abc", "abc>y=<", FSMergerBySequence.CONFLICTED);
		testNoResultEol("abcd", "xabcd", "bcd", ">xa=<bcd", FSMergerBySequence.CONFLICTED);
		testNoResultEol("abcd", "xabcd", "y", ">xabcd=y<", FSMergerBySequence.CONFLICTED);
		testNoResultEol("abce", "abcde", "ye", "yde", FSMergerBySequence.MERGED);
	}

	public void testEOLEdgeCases() throws IOException {
		testDirect("a\n", "a\nb\n", "a\nb\n", "a\nb\n", "\n", FSMergerBySequence.NOT_MODIFIED);
		testDirect("a", "a\nb\n", "a\nb\n", "a\nb\n", "\n", FSMergerBySequence.NOT_MODIFIED);
		testDirect("a\nb\nc\n", "a\nx\nc\n", "a\ny\nc\n", "a\n>\nx\n=\ny\n<\nc\n", "\n", FSMergerBySequence.CONFLICTED);
		testDirect("a\nb", "a\nx", "a\ny", "a\n>\nx=\ny<\n", "\n", FSMergerBySequence.CONFLICTED);
		testDirect("a", "a\nx", "a\ny", ">\na\nx=\na\ny<\n", "\n", FSMergerBySequence.CONFLICTED);
		testDirect("a", "a\nx\n", "a\ny\n", ">\na\nx\n=\na\ny\n<\n", "\n", FSMergerBySequence.CONFLICTED);
	}

	public void testPython() throws IOException {
		// Trans 5 Unix
		testDirect("1\n2\n3\n4\n4.5\n5\n6\n7\n8\n9\n",
		           "1\n2\n3\n4\n4.5\n5\n6\n7\n8\n9\n10\n",
		           "This is file rho.\n",
		           ">" + FSMergerBySequence.DEFAULT_EOL + "1\n2\n3\n4\n4.5\n5\n6\n7\n8\n9\n10\n=" + FSMergerBySequence.DEFAULT_EOL + "This is file rho.\n<" + FSMergerBySequence.DEFAULT_EOL, null, FSMergerBySequence.CONFLICTED);

		// Trans 5 Windows
		testDirect("1\n2\n3\n4\n4.5\n5\n6\n7\n8\n9\n",
		           "1\r\n2\r\n3\r\n4\r\n4.5\r\n5\r\n6\r\n7\r\n8\r\n9\r\n10\r\n",
		           "This is file rho.\n",
		           ">" + FSMergerBySequence.DEFAULT_EOL + "1\r\n2\r\n3\r\n4\r\n4.5\r\n5\r\n6\r\n7\r\n8\r\n9\r\n10\r\n=" + FSMergerBySequence.DEFAULT_EOL + "This is file rho.\n<" + FSMergerBySequence.DEFAULT_EOL, null, FSMergerBySequence.CONFLICTED);

		// Merge 25 Unix
		testDirect("This is the file 'mu'.\nr3\nr3\nr3\nr3\nr3\nr3\nr3\nr3\n",
		           "This is the file 'mu'.\n",
		           "This is the file 'mu'.\nr3\nr3\nr3\nr3\nr3\nr3\nr3\nr3\nr4\nr4\nr4\nr4\nr4\nr4\nr4\nr4\n",
		           "This is the file 'mu'.\n>" + FSMergerBySequence.DEFAULT_EOL + "=" + FSMergerBySequence.DEFAULT_EOL + "r3\nr3\nr3\nr3\nr3\nr3\nr3\nr3\nr4\nr4\nr4\nr4\nr4\nr4\nr4\nr4\n<" + FSMergerBySequence.DEFAULT_EOL, null, FSMergerBySequence.CONFLICTED);
	}

	public void testSVNSuite() throws IOException {
		testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd", "Aa\nBb\nCc\nEe", "Aa\nBb\nCc\n>\r\nDd=\r\nEe<\r\n", null, FSMergerBySequence.CONFLICTED);

		// test_three_way_merge_no_overlap
		testDirect("Aa\nBb\nCc\n", "Xx\nAa\nBb\nCc\n", "Aa\nBb\nCc\nYy\n", "Xx\nAa\nBb\nCc\nYy\n", null, FSMergerBySequence.MERGED);
		testDirect("Aa\nBb\nCc\n", "Xx\nAa\nBb\nCc\nYy\n", "Aa\nBb\nZz\nCc\n", "Xx\nAa\nBb\nZz\nCc\nYy\n", null, FSMergerBySequence.MERGED);
		testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc", "Xx\nBb\nCc\n", "Xx\nBb\nCc", null, FSMergerBySequence.MERGED);
		testDirect("Aa\nBb\nCc\n", "Xx\nBb\nCc\n", "Aa\nBb\nCc", "Xx\nBb\nCc", null, FSMergerBySequence.MERGED);
		testDirect("Aa\nBb\nCc\nDd\nEe\nFf\nGg\nHh\nIi\n", "Aa\nBb\nCc\nDd\nEe\nFf\nYy\nZz\nHh\nIi\n", "Bb\nCc\nDd\nEe\nFf\nGg\nHh\nIi\n", "Bb\nCc\nDd\nEe\nFf\nYy\nZz\nHh\nIi\n", null, FSMergerBySequence.MERGED);
		testDirect("Aa\r\nBb\nCc\n", "Xx\r\nAa\r\nBb\nCc\n", "Aa\r\nBb\nCc\nYy\r\n", "Xx\r\nAa\r\nBb\nCc\nYy\r\n", null, FSMergerBySequence.MERGED);
		testDirect("AaAaAaAaAaAa\nBb\nCc\n", "Xx\nBb\nCc\n", "AaAaAaAaAaAa\nBb\nCcCcCcCcCcCc\nYy\n", "Xx\nBb\nCcCcCcCcCcCc\nYy\n", null, FSMergerBySequence.MERGED);
		testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd", "Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd", null, FSMergerBySequence.MERGED);

		// test_three_way_merge_with_overlap
		testDirect("Aa\nBb\nCc\nDd\nEe\n", "Aa\nXx\nBb\nCc\nYy\nEe\n", "Aa\nBb\nCc\nYy\nEe\nZz\n", "Aa\nXx\nBb\nCc\nYy\nEe\nZz\n", null, FSMergerBySequence.MERGED);
		testDirect("Aa\nBb\nCc\nDd\nEe\nFf\n", "Aa\nYy\nZz\nDd\nPp\nQq\nFf\n", "Pp\nQq\nAa\nBb\nCc\nDd\nPp\nQq\nFf\nPp\nQq\n", "Pp\nQq\nAa\nYy\nZz\nDd\nPp\nQq\nFf\nPp\nQq\n", null, FSMergerBySequence.MERGED);
		testDirect("Aa\nBb\nCc\n", "Xx\nAa\nBb\nCc", "Aa\nXx\nBb\nCc", "Xx\nAa\nXx\nBb\nCc", null, FSMergerBySequence.MERGED);
		testDirect("Aa\nBb\nCc\nDd\nEe\nFf\nGg\nHh\n", "Aa\nFf\nGg\nHh\nBb\nCc\nXx\nDd\nEe\nYy\nFf\nGg\nHh\n", "Aa\nBb\nCc\nXx\nDd\nEe\nFf\nGg\nZz\nHh\n", "Aa\nFf\nGg\nHh\nBb\nCc\nXx\nDd\nEe\nYy\nFf\nGg\nZz\nHh\n", null, FSMergerBySequence.MERGED);

		// test_three_way_merge_with_conflict
		testDirect("Aa\nBb\nCc\n", "", "", "", null, FSMergerBySequence.NOT_MODIFIED);
		testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd\nEe\nFf\n", "", ">" + FSMergerBySequence.DEFAULT_EOL + "Aa\nBb\nCc\nDd\nEe\nFf\n=" + FSMergerBySequence.DEFAULT_EOL + "<" + FSMergerBySequence.DEFAULT_EOL, null, FSMergerBySequence.CONFLICTED);
		testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd\nEe\nFf\n", "Aa\nBb\n", "Aa\nBb\n>" + FSMergerBySequence.DEFAULT_EOL + "Cc\nDd\nEe\nFf\n=" + FSMergerBySequence.DEFAULT_EOL + "<" + FSMergerBySequence.DEFAULT_EOL, null, FSMergerBySequence.CONFLICTED);
		testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd", "Aa\nBb\nCc\nEe", "Aa\nBb\nCc\n>" + FSMergerBySequence.DEFAULT_EOL + "Dd=" + FSMergerBySequence.DEFAULT_EOL + "Ee<" + FSMergerBySequence.DEFAULT_EOL, null, FSMergerBySequence.CONFLICTED);

		// merge_adjacent_changes
		testDirect("foo\nbar\nbaz\n", "foo\nnew_bar\nbaz\n", "zig\nfoo\nbar\nnew_baz\n", "zig\nfoo\nnew_bar\nnew_baz\n", null, FSMergerBySequence.MERGED);
	}

	private void testResultEol(String baseFile, String localFile, String latestFile, String resultFile, int expectedStatus) throws IOException {
		baseFile = SVNSequenceDeltaGeneratorTest.createLines(baseFile, "\n");
		localFile = SVNSequenceDeltaGeneratorTest.createLines(localFile, "\n");
		latestFile = SVNSequenceDeltaGeneratorTest.createLines(latestFile, "\n");
		resultFile = SVNSequenceDeltaGeneratorTest.createLines(resultFile, "\n");
		testDirect(baseFile, localFile, latestFile, resultFile, "\n", expectedStatus);
	}

	private void testNoResultEol(String baseFile, String localFile, String latestFile, String resultFile, int expectedStatus) throws IOException {
		baseFile = SVNSequenceDeltaGeneratorTest.createLines(baseFile, "\n");
		localFile = SVNSequenceDeltaGeneratorTest.createLines(localFile, "\n");
		latestFile = SVNSequenceDeltaGeneratorTest.createLines(latestFile, "\n");
		resultFile = SVNSequenceDeltaGeneratorTest.createLines(resultFile, "\n");
		resultFile = resultFile.replaceAll(">\n", ">" + FSMergerBySequence.DEFAULT_EOL);
		resultFile = resultFile.replaceAll("=\n", "=" + FSMergerBySequence.DEFAULT_EOL);
		resultFile = resultFile.replaceAll("<\n", "<" + FSMergerBySequence.DEFAULT_EOL);
		testDirect(baseFile, localFile, latestFile, resultFile, null, expectedStatus);
	}

	private void testDifferentEols(String baseFile, String baseEol, String localFile, String localEol, String latestFile, String latestEol, String resultFile, String resultEol, int expectedStatus)
			throws IOException {
		baseFile = SVNSequenceDeltaGeneratorTest.createLines(baseFile, baseEol);
		localFile = SVNSequenceDeltaGeneratorTest.createLines(localFile, localEol);
		latestFile = SVNSequenceDeltaGeneratorTest.createLines(latestFile, latestEol);
		resultFile = SVNSequenceDeltaGeneratorTest.createLines(resultFile, resultEol);
		testDirect(baseFile, localFile, latestFile, resultFile, resultEol, expectedStatus);
	}

	private void testDirect(String baseFile, String localFile, String latestFile, String resultFile, String resultEol, int expectedStatus) throws IOException {
		final FSMergerBySequence merger = new FSMergerBySequence(">".getBytes(), "=".getBytes(), "<".getBytes(), resultEol != null ? resultEol.getBytes() : null);
		final ByteArrayOutputStream result = new ByteArrayOutputStream();
		final int status = merger.merge(new QSequenceLineRAByteData(baseFile.getBytes()), new QSequenceLineRAByteData(localFile.getBytes()), new QSequenceLineRAByteData(latestFile.getBytes()), result);

		assertEquals(resultFile, result.toString());
		assertEquals(expectedStatus, status);
	}
}
