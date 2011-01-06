/*
 * ====================================================================
 * Copyright (c) 2004-2011 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.TestCase;

import org.tmatesoft.svn.core.internal.wc.FSMergerBySequence;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;

import de.regnis.q.sequence.line.QSequenceLineRAByteData;

/**
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
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
        test("abc", "xbc", "ac", "xc", FSMergerBySequence.MERGED);
        test("abc", "xabc", "abc", "xabc", FSMergerBySequence.MERGED);
        test("abc", "abcx", "abc", "abcx", FSMergerBySequence.MERGED);
        test("abc", "axxxbc", "abc", "axxxbc", FSMergerBySequence.MERGED);
        test("abc", "axxxbc", "abyyyc", "axxxbyyyc", FSMergerBySequence.MERGED);
        test("abcde", "xaxbcxxxdex", "abyycdyyyye", "xaxbyycxxxdyyyyex", FSMergerBySequence.MERGED);
        test("The base line.", "The changed base-line.", "The base line with changes.", "The changed base-line with changes.", FSMergerBySequence.MERGED);
        test("abcd", "abxcd", "abcxd", "abxcxd", FSMergerBySequence.MERGED);
        test("abcd", "axbcd", "abxcyd", "axbxcyd", FSMergerBySequence.MERGED);
        test("abcde", "abxcdez", "abcxdyez", "abxcxdyez", FSMergerBySequence.MERGED);

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
        test("abc", "xbc", "bc", ">x=<bc", FSMergerBySequence.CONFLICTED);
        test("abc", "axxxbc", "aybyyyc", "a>xxx=y<byyyc", FSMergerBySequence.CONFLICTED);
        test("abc", "axxxbc", "ayyyyc", "a>xxxb=yyyy<c", FSMergerBySequence.CONFLICTED);
        test("abcd", "ybcd", "xabcd", ">y=xa<bcd", FSMergerBySequence.CONFLICTED);
        test("abcd", "xbcxxd", "ybcdyyy", ">x=y<bcxxdyyy", FSMergerBySequence.CONFLICTED);
        test("abcdefg", "xxxdxxx", "abyyyfg", ">xxxdxxx=abyyyfg<", FSMergerBySequence.CONFLICTED);
        test("abcdefghijk", "xxxdxxxhxxx", "abyyyfyyyjk", ">xxxdxxxhxxx=abyyyfyyyjk<", FSMergerBySequence.CONFLICTED);
        test("abcdefghijklmnop", "xxxdxxxhixklmxoxxx", "abyyyfghijkymyo", ">xxxdxxx=abyyyfg<hixkym>x=y<o>xxx=<", FSMergerBySequence.CONFLICTED);
        test("Some base text", "Many changes to the base", "Confusing the base text", ">Many changes t=C<o> th=nfusing th<e base", FSMergerBySequence.CONFLICTED);
    }

    public void testAdjacentChangesVerifiedWithCommandLine() throws Throwable {
        test("abc", "abcd", "y", ">abcd=y<", FSMergerBySequence.CONFLICTED);

        test("abcd", "xbcd", "aycd", "xycd", FSMergerBySequence.MERGED);
        test("abcd", "xbcd", "aybcd", "xybcd", FSMergerBySequence.MERGED);
        test("abcd", "aycd", "bcd", "ycd", FSMergerBySequence.MERGED);
        test("abcd", "abcy", "abc", "abc>y=<", FSMergerBySequence.CONFLICTED);
        test("abcd", "xabcd", "bcd", ">xa=<bcd", FSMergerBySequence.CONFLICTED);
        test("abcd", "xabcd", "y", ">xabcd=y<", FSMergerBySequence.CONFLICTED);
        test("abce", "abcde", "ye", "yde", FSMergerBySequence.MERGED);
    }

    public void testEOLEdgeCases() throws IOException {
        testDirect("a\n", "a\nb\n", "a\nb\n", "a\nb\n", FSMergerBySequence.NOT_MODIFIED);
        testDirect("a", "a\nb\n", "a\nb\n", "a\nb\n", FSMergerBySequence.NOT_MODIFIED);
        testDirect("a\nb\nc\n", "a\nx\nc\n", "a\ny\nc\n", "a\n>" + FSMergerBySequence.DEFAULT_EOL + "x\n=" + FSMergerBySequence.DEFAULT_EOL + "y\n<" + FSMergerBySequence.DEFAULT_EOL + "c\n", FSMergerBySequence.CONFLICTED);
        testDirect("a\nb", "a\nx", "a\ny", "a\n>" + FSMergerBySequence.DEFAULT_EOL + "x=" + FSMergerBySequence.DEFAULT_EOL + "y<" + FSMergerBySequence.DEFAULT_EOL, FSMergerBySequence.CONFLICTED);
        testDirect("a", "a\nx", "a\ny", ">" + FSMergerBySequence.DEFAULT_EOL + "a\nx=" + FSMergerBySequence.DEFAULT_EOL + "a\ny<" + FSMergerBySequence.DEFAULT_EOL, FSMergerBySequence.CONFLICTED);
        testDirect("a", "a\nx\n", "a\ny\n", ">" + FSMergerBySequence.DEFAULT_EOL + "a\nx\n=" + FSMergerBySequence.DEFAULT_EOL + "a\ny\n<" + FSMergerBySequence.DEFAULT_EOL, FSMergerBySequence.CONFLICTED);
    }

    public void testPython() throws IOException {
        // Trans 5 Unix
        testDirect("1\n2\n3\n4\n4.5\n5\n6\n7\n8\n9\n",
                   "1\n2\n3\n4\n4.5\n5\n6\n7\n8\n9\n10\n",
                   "This is file rho.\n",
                   ">" + FSMergerBySequence.DEFAULT_EOL + "1\n2\n3\n4\n4.5\n5\n6\n7\n8\n9\n10\n=" + FSMergerBySequence.DEFAULT_EOL + "This is file rho.\n<" + FSMergerBySequence.DEFAULT_EOL, FSMergerBySequence.CONFLICTED);

        // Trans 5 Windows
        testDirect("1\n2\n3\n4\n4.5\n5\n6\n7\n8\n9\n",
                   "1\r\n2\r\n3\r\n4\r\n4.5\r\n5\r\n6\r\n7\r\n8\r\n9\r\n10\r\n",
                   "This is file rho.\n",
                   ">" + FSMergerBySequence.DEFAULT_EOL + "1\r\n2\r\n3\r\n4\r\n4.5\r\n5\r\n6\r\n7\r\n8\r\n9\r\n10\r\n=" + FSMergerBySequence.DEFAULT_EOL + "This is file rho.\n<" + FSMergerBySequence.DEFAULT_EOL, FSMergerBySequence.CONFLICTED);

        // Merge 25 Unix
        testDirect("This is the file 'mu'.\nr3\nr3\nr3\nr3\nr3\nr3\nr3\nr3\n",
                   "This is the file 'mu'.\n",
                   "This is the file 'mu'.\nr3\nr3\nr3\nr3\nr3\nr3\nr3\nr3\nr4\nr4\nr4\nr4\nr4\nr4\nr4\nr4\n",
                   "This is the file 'mu'.\n>" + FSMergerBySequence.DEFAULT_EOL + "=" + FSMergerBySequence.DEFAULT_EOL + "r3\nr3\nr3\nr3\nr3\nr3\nr3\nr3\nr4\nr4\nr4\nr4\nr4\nr4\nr4\nr4\n<" + FSMergerBySequence.DEFAULT_EOL, FSMergerBySequence.CONFLICTED);
    }

    public void testSVNSuite() throws IOException {
        testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd", "Aa\nBb\nCc\nEe", "Aa\nBb\nCc\n>\r\nDd=\r\nEe<\r\n", FSMergerBySequence.CONFLICTED);

        // test_three_way_merge_no_overlap
        testDirect("Aa\nBb\nCc\n", "Xx\nAa\nBb\nCc\n", "Aa\nBb\nCc\nYy\n", "Xx\nAa\nBb\nCc\nYy\n", FSMergerBySequence.MERGED);
        testDirect("Aa\nBb\nCc\n", "Xx\nAa\nBb\nCc\nYy\n", "Aa\nBb\nZz\nCc\n", "Xx\nAa\nBb\nZz\nCc\nYy\n", FSMergerBySequence.MERGED);
        testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc", "Xx\nBb\nCc\n", "Xx\nBb\nCc", FSMergerBySequence.MERGED);
        testDirect("Aa\nBb\nCc\n", "Xx\nBb\nCc\n", "Aa\nBb\nCc", "Xx\nBb\nCc", FSMergerBySequence.MERGED);
        testDirect("Aa\nBb\nCc\nDd\nEe\nFf\nGg\nHh\nIi\n", "Aa\nBb\nCc\nDd\nEe\nFf\nYy\nZz\nHh\nIi\n", "Bb\nCc\nDd\nEe\nFf\nGg\nHh\nIi\n", "Bb\nCc\nDd\nEe\nFf\nYy\nZz\nHh\nIi\n", FSMergerBySequence.MERGED);
        testDirect("Aa\r\nBb\nCc\n", "Xx\r\nAa\r\nBb\nCc\n", "Aa\r\nBb\nCc\nYy\r\n", "Xx\r\nAa\r\nBb\nCc\nYy\r\n", FSMergerBySequence.MERGED);
        testDirect("AaAaAaAaAaAa\nBb\nCc\n", "Xx\nBb\nCc\n", "AaAaAaAaAaAa\nBb\nCcCcCcCcCcCc\nYy\n", "Xx\nBb\nCcCcCcCcCcCc\nYy\n", FSMergerBySequence.MERGED);
        testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd", "Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd", FSMergerBySequence.MERGED);

        // test_three_way_merge_with_overlap
        testDirect("Aa\nBb\nCc\nDd\nEe\n", "Aa\nXx\nBb\nCc\nYy\nEe\n", "Aa\nBb\nCc\nYy\nEe\nZz\n", "Aa\nXx\nBb\nCc\nYy\nEe\nZz\n", FSMergerBySequence.MERGED);
        testDirect("Aa\nBb\nCc\nDd\nEe\nFf\n", "Aa\nYy\nZz\nDd\nPp\nQq\nFf\n", "Pp\nQq\nAa\nBb\nCc\nDd\nPp\nQq\nFf\nPp\nQq\n", "Pp\nQq\nAa\nYy\nZz\nDd\nPp\nQq\nFf\nPp\nQq\n", FSMergerBySequence.MERGED);
        testDirect("Aa\nBb\nCc\n", "Xx\nAa\nBb\nCc", "Aa\nXx\nBb\nCc", "Xx\nAa\nXx\nBb\nCc", FSMergerBySequence.MERGED);
        testDirect("Aa\nBb\nCc\nDd\nEe\nFf\nGg\nHh\n", "Aa\nFf\nGg\nHh\nBb\nCc\nXx\nDd\nEe\nYy\nFf\nGg\nHh\n", "Aa\nBb\nCc\nXx\nDd\nEe\nFf\nGg\nZz\nHh\n", "Aa\nFf\nGg\nHh\nBb\nCc\nXx\nDd\nEe\nYy\nFf\nGg\nZz\nHh\n", FSMergerBySequence.MERGED);

        // test_three_way_merge_with_conflict
        testDirect("Aa\nBb\nCc\n", "", "", "", FSMergerBySequence.NOT_MODIFIED);
        testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd\nEe\nFf\n", "", ">" + FSMergerBySequence.DEFAULT_EOL + "Aa\nBb\nCc\nDd\nEe\nFf\n=" + FSMergerBySequence.DEFAULT_EOL + "<" + FSMergerBySequence.DEFAULT_EOL, FSMergerBySequence.CONFLICTED);
        testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd\nEe\nFf\n", "Aa\nBb\n", "Aa\nBb\n>" + FSMergerBySequence.DEFAULT_EOL + "Cc\nDd\nEe\nFf\n=" + FSMergerBySequence.DEFAULT_EOL + "<" + FSMergerBySequence.DEFAULT_EOL, FSMergerBySequence.CONFLICTED);
        testDirect("Aa\nBb\nCc\n", "Aa\nBb\nCc\nDd", "Aa\nBb\nCc\nEe", "Aa\nBb\nCc\n>" + FSMergerBySequence.DEFAULT_EOL + "Dd=" + FSMergerBySequence.DEFAULT_EOL + "Ee<" + FSMergerBySequence.DEFAULT_EOL, FSMergerBySequence.CONFLICTED);

        // merge_adjacent_changes
        testDirect("foo\nbar\nbaz\n", "foo\nnew_bar\nbaz\n", "zig\nfoo\nbar\nnew_baz\n", "zig\nfoo\nnew_bar\nnew_baz\n", FSMergerBySequence.MERGED);

        // ignore_whitespace
        testDirect("Aa\nBb\nCc\n", "    Aa\nB b\nC c\n", "A  a\nBb \n Cc\nNew line in iota\n", "    Aa\nB b\nC c\nNew line in iota\n", FSMergerBySequence.MERGED, new SVNDiffOptions(true, true, false));

        // ignore_eolstyle
        testDirect("Aa\r\nBb\r\nCc\r\n", "Aa\nBb\rCc\n", "Aa\rBb\nCc\rNew line in iota\n", "Aa\nBb\rCc\nNew line in iota\n", FSMergerBySequence.MERGED, new SVNDiffOptions(false, false, true));
    }

    private void test(String baseFile, String localFile, String latestFile, String resultFile, int expectedStatus) throws IOException {
        baseFile = createLines(baseFile, "\n");
        localFile = createLines(localFile, "\n");
        latestFile = createLines(latestFile, "\n");
        resultFile = createLines(resultFile, "\n");
        resultFile = resultFile.replaceAll(">\n", ">" + FSMergerBySequence.DEFAULT_EOL);
        resultFile = resultFile.replaceAll("=\n", "=" + FSMergerBySequence.DEFAULT_EOL);
        resultFile = resultFile.replaceAll("<\n", "<" + FSMergerBySequence.DEFAULT_EOL);
        testDirect(baseFile, localFile, latestFile, resultFile, expectedStatus);
    }

    private void testDirect(String baseFile, String localFile, String latestFile, String resultFile, int expectedStatus) throws IOException {
        testDirect(baseFile, localFile, latestFile, resultFile, expectedStatus, new SVNDiffOptions());
    }

    private void testDirect(String baseFile, String localFile, String latestFile, String resultFile, int expectedStatus, SVNDiffOptions diffOptions) throws IOException {
        final FSMergerBySequence merger = new FSMergerBySequence(">".getBytes(), "=".getBytes(), "<".getBytes());
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        final int status = merger.merge(new QSequenceLineRAByteData(baseFile.getBytes()), new QSequenceLineRAByteData(localFile.getBytes()), 
                new QSequenceLineRAByteData(latestFile.getBytes()), diffOptions, result, null);

        assertEquals(resultFile, result.toString());
        assertEquals(expectedStatus, status);
    }

    private static String createLines(String linesWithoutEOL, String eol) {
        final StringBuffer linesWithEOL = new StringBuffer();
        for (int index = 0; index < linesWithoutEOL.length(); index++) {
            linesWithEOL.append(linesWithoutEOL.charAt(index));
            linesWithEOL.append(eol);
        }
        return linesWithEOL.toString();
    }
}
