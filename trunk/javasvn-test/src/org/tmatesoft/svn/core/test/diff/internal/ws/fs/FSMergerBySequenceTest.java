package org.tmatesoft.svn.core.test.diff.internal.ws.fs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.TestCase;

import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.internal.ws.fs.FSMergerBySequence;
import org.tmatesoft.svn.core.test.diff.SVNSequenceDeltaGeneratorTest;

/**
 * @author Marc Strapetz
 */
public class FSMergerBySequenceTest extends TestCase {

    public void testBasic() throws Throwable {
        // Not modified
        test("", "", "", "", SVNStatus.NOT_MODIFIED);
        test("a", "a", "a", "a", SVNStatus.NOT_MODIFIED);
        test("abc", "abc", "abc", "abc", SVNStatus.NOT_MODIFIED);

        // Merged ...
        test("abc", "xbc", "aby", "xby", SVNStatus.MERGED);
        test("abcd", "xbcd", "abyd", "xbyd", SVNStatus.MERGED);
        test("abc", "xabc", "abc", "xabc", SVNStatus.MERGED);
        test("abc", "abcx", "abc", "abcx", SVNStatus.MERGED);
        test("abc", "axxxbc", "abc", "axxxbc", SVNStatus.MERGED);
        test("abc", "axxxbc", "abyyyc", "axxxbyyyc", SVNStatus.MERGED);
        test("abcde", "xaxbcxxxdex", "abyycdyyyye", "xaxbyycxxxdyyyyex", SVNStatus.MERGED);
        test("The base line.", "The changed base-line.", "The base line with changes.", "The changed base-line with changes.", SVNStatus.MERGED);

        // Unreal conflicts
        test("abc", "abx", "abx", "abx", SVNStatus.MERGED);
        test("abc", "ab", "ab", "ab", SVNStatus.MERGED);
        test("abc", "b", "b", "b", SVNStatus.MERGED);
        test("abc", "", "", "", SVNStatus.MERGED);

        // Real Conflicts ...
        test("abc", "axc", "ayc", "a>x=y<c", SVNStatus.CONFLICTED);
        test("abc", "axxc", "ayc", "a>xx=y<c", SVNStatus.CONFLICTED);
        test("abc", "axxxc", "ayc", "a>xxx=y<c", SVNStatus.CONFLICTED);
        test("abc", "axxxc", "ayyc", "a>xxx=yy<c", SVNStatus.CONFLICTED);
        test("abc", "xbc", "yby", ">x=y<by", SVNStatus.CONFLICTED);
        test("abc", "xbc", "bc", ">x=<bc", SVNStatus.CONFLICTED);
        test("abc", "xbc", "", ">xbc=<", SVNStatus.CONFLICTED);
        test("abc", "xbc", "ac", ">xb=a<c", SVNStatus.CONFLICTED);
        test("abc", "axxxbc", "aybyyyc", "a>xxx=y<byyyc", SVNStatus.CONFLICTED);
        test("abc", "axxxbc", "ayyyyc", "a>xxxb=yyyy<c", SVNStatus.CONFLICTED);
        test("abcd", "xbcxxd", "ybcdyyy", ">x=y<bcxxdyyy", SVNStatus.CONFLICTED);
        test("abcdefg", "xxxdxxx", "abyyyfg", ">xxxdxxx=abyyyfg<", SVNStatus.CONFLICTED);
        test("abcdefghijk", "xxxdxxxhxxx", "abyyyfyyyjk", ">xxxdxxxhxxx=abyyyfyyyjk<", SVNStatus.CONFLICTED);
        test("abcdefghijklmnop", "xxxdxxxhixklmxoxxx", "abyyyfghijkymyo", ">xxxdxxx=abyyyfg<hixkym>x=y<o>xxx=<", SVNStatus.CONFLICTED);
        test("Some base text", "Many changes to the base", "Confusing the base text", ">Many changes t=C<o> th=nfusing th<e base", SVNStatus.CONFLICTED);
    }

    public void testDifferentEOL() throws Throwable {
        test("", "\n", "", "\r", "", "\r\n", "", "\n", SVNStatus.NOT_MODIFIED);
        test("abc", "\n", "xbc", "\n", "aby", "\r\n", "xby", "\r\n", SVNStatus.MERGED);
        test("abc", "\r", "axc", "\n", "ayc", "\r\n", "a>x=y<c", "\r", SVNStatus.CONFLICTED);
    }

    public void testEOLEdgeCases() throws IOException {
        testDirect("a\n", "a\nb\n", "a\nb\n", "a\nb\n", "\n", SVNStatus.MERGED);
        testDirect("a", "a\nb\n", "a\nb\n", "a\nb\n", "\n", SVNStatus.MERGED);
        testDirect("a\nb\nc\n", "a\nx\nc\n", "a\ny\nc\n", "a\n>\nx\n=\ny\n<\nc\n", "\n", SVNStatus.CONFLICTED);
        testDirect("a\nb", "a\nx", "a\ny", "a\n>\nx=\ny<\n", "\n", SVNStatus.CONFLICTED);
        testDirect("a", "a\nx", "a\ny", ">\na\nx=\na\ny<\n", "\n", SVNStatus.CONFLICTED);
        testDirect("a", "a\nx\n", "a\ny\n", ">\na\nx\n=\na\ny\n<\n", "\n", SVNStatus.CONFLICTED);
    }

    private void test(String baseFile, String localFile, String latestFile, String resultFile, int expectedStatus) throws IOException {
        test(baseFile, "\n", localFile, "\n", latestFile, "\n", resultFile, "\n", expectedStatus);
    }

    private void test(String baseFile, String baseEol, String localFile, String localEol, String latestFile, String latestEol, String resultFile,
            String resultEol, int expectedStatus) throws IOException {
        baseFile = SVNSequenceDeltaGeneratorTest.createLines(baseFile, baseEol);
        localFile = SVNSequenceDeltaGeneratorTest.createLines(localFile, localEol);
        latestFile = SVNSequenceDeltaGeneratorTest.createLines(latestFile, latestEol);
        resultFile = SVNSequenceDeltaGeneratorTest.createLines(resultFile, resultEol);
        testDirect(baseFile, localFile, latestFile, resultFile, resultEol, expectedStatus);
    }

    private void testDirect(String baseFile, String localFile, String latestFile, String resultFile, String resultEol, int expectedStatus) throws IOException {
        final FSMergerBySequence merger = new FSMergerBySequence(">".getBytes(), "=".getBytes(), "<".getBytes(), resultEol.getBytes());
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        final int status = merger.merge(new ByteArrayInputStream(baseFile.getBytes()), new ByteArrayInputStream(localFile.getBytes()),
                new ByteArrayInputStream(latestFile.getBytes()), result);

        assertEquals(resultFile, result.toString());
        assertEquals(expectedStatus, status);
    }
}