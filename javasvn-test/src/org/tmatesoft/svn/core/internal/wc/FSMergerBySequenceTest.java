package org.tmatesoft.svn.core.internal.wc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.TestCase;

import org.tmatesoft.svn.core.io.diff.SVNSequenceDeltaGeneratorTest;

import de.regnis.q.sequence.line.QSequenceLineRAByteData;

/**
 * @author Marc Strapetz
 */
public class FSMergerBySequenceTest extends TestCase {

    public void testBasic() throws Throwable {
        // Not modified
        test("", "", "", "", FSMergerBySequence.NOT_MODIFIED);
        test("a", "a", "a", "a", FSMergerBySequence.NOT_MODIFIED);
        test("abc", "abc", "abc", "abc", FSMergerBySequence.NOT_MODIFIED);

        // Merged ...
        test("abc", "xbc", "aby", "xby", FSMergerBySequence.MERGED);
        test("abcd", "xbcd", "abyd", "xbyd", FSMergerBySequence.MERGED);
        test("abc", "xabc", "abc", "xabc", FSMergerBySequence.MERGED);
        test("abc", "abcx", "abc", "abcx", FSMergerBySequence.MERGED);
        test("abc", "axxxbc", "abc", "axxxbc", FSMergerBySequence.MERGED);
        test("abc", "axxxbc", "abyyyc", "axxxbyyyc", FSMergerBySequence.MERGED);
        test("abcde", "xaxbcxxxdex", "abyycdyyyye", "xaxbyycxxxdyyyyex", FSMergerBySequence.MERGED);
        test("The base line.", "The changed base-line.", "The base line with changes.", "The changed base-line with changes.", FSMergerBySequence.MERGED);

        // Unreal conflicts
        test("abc", "abx", "abx", "abx", FSMergerBySequence.NOT_MODIFIED);
        test("abc", "ab", "ab", "ab", FSMergerBySequence.NOT_MODIFIED);
        test("abc", "b", "b", "b", FSMergerBySequence.NOT_MODIFIED);
        test("abc", "", "", "", FSMergerBySequence.NOT_MODIFIED);

        // Real Conflicts ...
        test("abc", "axc", "ayc", "a>x=y<c", FSMergerBySequence.CONFLICTED);
        test("abc", "axxc", "ayc", "a>xx=y<c", FSMergerBySequence.CONFLICTED);
        test("abc", "axxxc", "ayc", "a>xxx=y<c", FSMergerBySequence.CONFLICTED);
        test("abc", "axxxc", "ayyc", "a>xxx=yy<c", FSMergerBySequence.CONFLICTED);
        test("abc", "xbc", "yby", ">x=y<by", FSMergerBySequence.CONFLICTED);
        test("abc", "xbc", "bc", ">x=<bc", FSMergerBySequence.CONFLICTED);
        test("abc", "xbc", "", ">xbc=<", FSMergerBySequence.CONFLICTED);
        test("abc", "xbc", "ac", ">xb=a<c", FSMergerBySequence.CONFLICTED);
        test("abc", "axxxbc", "aybyyyc", "a>xxx=y<byyyc", FSMergerBySequence.CONFLICTED);
        test("abc", "axxxbc", "ayyyyc", "a>xxxb=yyyy<c", FSMergerBySequence.CONFLICTED);
        test("abcd", "xbcxxd", "ybcdyyy", ">x=y<bcxxdyyy", FSMergerBySequence.CONFLICTED);
        test("abcdefg", "xxxdxxx", "abyyyfg", ">xxxdxxx=abyyyfg<", FSMergerBySequence.CONFLICTED);
        test("abcdefghijk", "xxxdxxxhxxx", "abyyyfyyyjk", ">xxxdxxxhxxx=abyyyfyyyjk<", FSMergerBySequence.CONFLICTED);
        test("abcdefghijklmnop", "xxxdxxxhixklmxoxxx", "abyyyfghijkymyo", ">xxxdxxx=abyyyfg<hixkym>x=y<o>xxx=<", FSMergerBySequence.CONFLICTED);
        test("Some base text", "Many changes to the base", "Confusing the base text", ">Many changes t=C<o> th=nfusing th<e base", FSMergerBySequence.CONFLICTED);
    }

    public void testDifferentEOL() throws Throwable {
        test("", "\n", "", "\r", "", "\r\n", "", "\n", FSMergerBySequence.NOT_MODIFIED);
        test("abc", "\n", "xbc", "\n", "aby", "\r\n", "xby", "\r\n", FSMergerBySequence.MERGED);
        test("abc", "\r", "axc", "\n", "ayc", "\r\n", "a>x=y<c", "\r", FSMergerBySequence.CONFLICTED);
    }

    public void testEOLEdgeCases() throws IOException {
        testDirect("a\n", "a\nb\n", "a\nb\n", "a\nb\n", "\n", FSMergerBySequence.NOT_MODIFIED);
        testDirect("a", "a\nb\n", "a\nb\n", "a\nb\n", "\n", FSMergerBySequence.NOT_MODIFIED);
        testDirect("a\nb\nc\n", "a\nx\nc\n", "a\ny\nc\n", "a\n>\nx\n=\ny\n<\nc\n", "\n", FSMergerBySequence.CONFLICTED);
        testDirect("a\nb", "a\nx", "a\ny", "a\n>\nx=\ny<\n", "\n", FSMergerBySequence.CONFLICTED);
        testDirect("a", "a\nx", "a\ny", ">\na\nx=\na\ny<\n", "\n", FSMergerBySequence.CONFLICTED);
        testDirect("a", "a\nx\n", "a\ny\n", ">\na\nx\n=\na\ny\n<\n", "\n", FSMergerBySequence.CONFLICTED);
    }

    private void test(String baseFile, String localFile, String latestFile, String resultFile, int expectedStatus) throws IOException {
        test(baseFile, "\n", localFile, "\n", latestFile, "\n", resultFile, "\n", expectedStatus);
    }

    private void test(String baseFile, String baseEol, String localFile, String localEol, String latestFile, String latestEol, String resultFile, String resultEol, int expectedStatus)
            throws IOException {
        baseFile = SVNSequenceDeltaGeneratorTest.createLines(baseFile, baseEol);
        localFile = SVNSequenceDeltaGeneratorTest.createLines(localFile, localEol);
        latestFile = SVNSequenceDeltaGeneratorTest.createLines(latestFile, latestEol);
        resultFile = SVNSequenceDeltaGeneratorTest.createLines(resultFile, resultEol);
        testDirect(baseFile, localFile, latestFile, resultFile, resultEol, expectedStatus);
    }

    private void testDirect(String baseFile, String localFile, String latestFile, String resultFile, String resultEol, int expectedStatus) throws IOException {
        final FSMergerBySequence merger = new FSMergerBySequence(">".getBytes(), "=".getBytes(), "<".getBytes(), resultEol.getBytes());
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        final int status = merger
                .merge(new QSequenceLineRAByteData(baseFile.getBytes()), new QSequenceLineRAByteData(localFile.getBytes()), new QSequenceLineRAByteData(latestFile.getBytes()), result);

        assertEquals(resultFile, result.toString());
        assertEquals(expectedStatus, status);
    }
}
