/*
 * ====================================================================
 * Copyright (c) 2000-2008 SyntEvo GmbH, info@syntevo.com
 * All rights reserved.
 *
 * This software is licensed as described in the file SEQUENCE-LICENSE,
 * which you should have received as part of this distribution. Use is
 * subject to license terms.
 * ====================================================================
 */

package de.regnis.q.sequence.line;

import java.io.*;
import java.util.*;
import junit.framework.*;

import de.regnis.q.sequence.*;
import de.regnis.q.sequence.line.simplifier.*;
import de.regnis.q.sequence.core.*;
import de.regnis.q.sequence.media.*;

/**
 * @author Marc Strapetz
 */
public class QSequenceLineMediaTest extends TestCase {

	// Fields =================================================================

	private Random random;

	// Implemented ============================================================

	protected void setUp() throws Exception {
		super.setUp();
		random = new Random(0);
	}

	// Accessing ==============================================================

	public void testBasicMemoryVersusFileSystem() throws IOException, QSequenceException {
		test(new String[] {"a", "c", "b."}, new String[] {"a", "d", "b."}, new QSequenceLineDummySimplifier(), null);
	}

	public void testSimplifier() throws IOException, QSequenceException {
		final String[] left = new String[] {"equal", "number of ws only ", "all ws"};
		final String[] right = new String[] {"equal", "number\tof  ws only  ", " allws "};

		test(left, right, new QSequenceLineDummySimplifier(), Collections.singletonList(new QSequenceDifferenceBlock(1, 2, 1, 2)));
		test(left, right, new QSequenceLineWhiteSpaceReducingSimplifier(), Collections.singletonList(new QSequenceDifferenceBlock(2, 2, 2, 2)));
		test(left, right, new QSequenceLineWhiteSpaceSkippingSimplifier(), Collections.EMPTY_LIST);
	}

	public void testRandomMemoryVersusFileSystem() throws IOException, QSequenceException {
		for (int size = 1; size <= 50; size += 1) {
			testRandomStrings(size, 0.3, 0.1);
		}
	}

	// Utils ==================================================================

	private void testRandomStrings(int lineCount, double pMod, double pAddRemove) throws IOException, QSequenceException {
		final String[] left = QSequenceDifferenceAssemblyTest.createLines(lineCount, random);
		final String[] right = QSequenceDifferenceAssemblyTest.alterLines(left, pMod, pAddRemove, random);
		test(left, right, new QSequenceLineDummySimplifier(), null);
	}

	private void test(String[] left, String[] right, QSequenceLineSimplifier simplifier, List expectedBlocks) throws IOException, QSequenceException {
		final File leftFile = createTestFile(left, true);
		final File rightFile = createTestFile(right, false);
		try {
			final QSequenceLineResult memoryResult = createBlocksInMemory(leftFile, rightFile, simplifier);
			final QSequenceLineResult raFileResult1 = createRAFileBlocks(leftFile, rightFile, 16, 16, simplifier);
			final QSequenceLineResult raFileResult2 = createRAFileBlocks(leftFile, rightFile, 256, 16, simplifier);
			final QSequenceLineResult raFileResult3 = createRAFileBlocks(leftFile, rightFile, 256, 64, simplifier);
			final QSequenceLineResult raFileResult4 = createRAFileBlocks(leftFile, rightFile, 256, 256, simplifier);
			final QSequenceLineResult raFileResult5 = createRAFileBlocks(leftFile, rightFile, Integer.MAX_VALUE, 48, simplifier);
			try {
				if (expectedBlocks != null) {
					compareBlocks(expectedBlocks, memoryResult.getBlocks());
				}
				compareBlocks(memoryResult.getBlocks(), raFileResult1.getBlocks());
				compareBlocks(memoryResult.getBlocks(), raFileResult2.getBlocks());
				compareBlocks(memoryResult.getBlocks(), raFileResult3.getBlocks());
				compareBlocks(memoryResult.getBlocks(), raFileResult4.getBlocks());
				compareBlocks(memoryResult.getBlocks(), raFileResult5.getBlocks());
			}
			finally {
				memoryResult.close();
				raFileResult1.close();
				raFileResult2.close();
				raFileResult3.close();
				raFileResult4.close();
				raFileResult5.close();
			}
		}
		finally {
			leftFile.delete();
			rightFile.delete();
		}
	}

	private static QSequenceLineResult createBlocksInMemory(final File leftFile, final File rightFile, QSequenceLineSimplifier simplifier) throws IOException, QSequenceException {
		final FileInputStream left = new FileInputStream(leftFile);
		final FileInputStream right = new FileInputStream(rightFile);
		final QSequenceLineMemoryCache leftCache = QSequenceLineMemoryCache.read(left, simplifier);
		final QSequenceLineMemoryCache rightCache = QSequenceLineMemoryCache.read(right, simplifier);
		final QSequenceLineMedia lineMedia = new QSequenceLineMedia(leftCache, rightCache);
		final QSequenceCachingMedia cachingMedia = new QSequenceCachingMedia(lineMedia, new QSequenceDummyCanceller());
		final List blocks = new QSequenceDifference(cachingMedia, new QSequenceMediaDummyIndexTransformer(cachingMedia)).getBlocks();
		new QSequenceDifferenceBlockShifter(cachingMedia, cachingMedia).shiftBlocks(blocks);
		return new QSequenceLineResult(blocks, leftCache, rightCache);
	}

	private static QSequenceLineResult createRAFileBlocks(File leftFile, File rightFile, int maximumMemorySize, int fileSegmentSize, QSequenceLineSimplifier simplifier) throws IOException, QSequenceException {
		final RandomAccessFile leftRAFile = new RandomAccessFile(leftFile, "r");
		final RandomAccessFile rightRAFile = new RandomAccessFile(rightFile, "r");
		try {
			return QSequenceLineMedia.createBlocksInFilesystem(new QSequenceLineRAFileData(leftRAFile), new QSequenceLineRAFileData(rightRAFile), new QSequenceLineSystemTempDirectoryFactory(), 1.0, maximumMemorySize, fileSegmentSize, simplifier);
		}
		finally {
			leftRAFile.close();
			rightRAFile.close();
		}
	}

	private static void compareBlocks(List expected, List actual) {
		assertEquals(expected.size(), actual.size());

		for (int index = 0; index < expected.size(); index++) {
			final QSequenceDifferenceBlock expectedBlock = (QSequenceDifferenceBlock)expected.get(index);
			final QSequenceDifferenceBlock actualBlock = (QSequenceDifferenceBlock)actual.get(index);

			assertEquals(expectedBlock.getLeftFrom(), actualBlock.getLeftFrom());
			assertEquals(expectedBlock.getLeftTo(), actualBlock.getLeftTo());
			assertEquals(expectedBlock.getRightFrom(), actualBlock.getRightFrom());
			assertEquals(expectedBlock.getRightTo(), actualBlock.getRightTo());
		}
	}

	private static File createTestFile(String[] content, boolean left) throws IOException {
		final File file = File.createTempFile("SequenceMediaTest", left ? "left" : "right");
		file.delete();

		final FileOutputStream stream = new FileOutputStream(file);
		for (int index = 0; index < content.length; index++) {
			stream.write(content[index].getBytes());
			if (index < content.length - 1) {
				stream.write("\n".getBytes());
			}
		}

		stream.close();
		return file;
	}
}