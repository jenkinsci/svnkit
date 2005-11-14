package de.regnis.q.sequence.line;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;
import de.regnis.q.sequence.QSequenceDifference;
import de.regnis.q.sequence.QSequenceDifferenceAssemblyTest;
import de.regnis.q.sequence.QSequenceDifferenceBlock;
import de.regnis.q.sequence.QSequenceDifferenceBlockShifter;
import de.regnis.q.sequence.core.QSequenceDummyCanceller;
import de.regnis.q.sequence.core.QSequenceException;
import de.regnis.q.sequence.media.QSequenceCachingMedia;
import de.regnis.q.sequence.media.QSequenceMediaDummyIndexTransformer;

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
		test(new String[]{"a", "c", "b."}, new String[]{"a", "d", "b."});
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
		test(left, right);
	}

	private void test(String[] left, String[] right) throws IOException, QSequenceException {
		final File leftFile = createTestFile(left, true);
		final File rightFile = createTestFile(right, false);
		try {
			test(leftFile, rightFile);
		}
		finally {
			leftFile.delete();
			rightFile.delete();
		}
	}

	private void test(final File leftFile, final File rightFile) throws IOException, QSequenceException {
		final QSequenceLineResult memoryResult = createBlocksInMemory(leftFile, rightFile);
		final QSequenceLineResult raFileResult1 = createRAFileBlocks(leftFile, rightFile, 16, 16);
		final QSequenceLineResult raFileResult2 = createRAFileBlocks(leftFile, rightFile, 256, 16);
		final QSequenceLineResult raFileResult3 = createRAFileBlocks(leftFile, rightFile, 256, 64);
		final QSequenceLineResult raFileResult4 = createRAFileBlocks(leftFile, rightFile, 256, 256);
		final QSequenceLineResult raFileResult5 = createRAFileBlocks(leftFile, rightFile, Integer.MAX_VALUE, 48);
		try {
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

	private QSequenceLineResult createBlocksInMemory(final File leftFile, final File rightFile) throws IOException, QSequenceException {
		final FileInputStream left = new FileInputStream(leftFile);
		final FileInputStream right = new FileInputStream(rightFile);
		final QSequenceLineMemoryCache leftCache = QSequenceLineMemoryCache.read(left);
		final QSequenceLineMemoryCache rightCache = QSequenceLineMemoryCache.read(right);
		final QSequenceLineMedia lineMedia = new QSequenceLineMedia(leftCache, rightCache);
		final QSequenceCachingMedia cachingMedia = new QSequenceCachingMedia(lineMedia, new QSequenceDummyCanceller());
		final List blocks = new QSequenceDifference(cachingMedia, new QSequenceMediaDummyIndexTransformer(cachingMedia)).getBlocks();
		new QSequenceDifferenceBlockShifter(cachingMedia, cachingMedia).shiftBlocks(blocks);
		return new QSequenceLineResult(blocks, leftCache, rightCache);
	}

	private QSequenceLineResult createRAFileBlocks(File leftFile, File rightFile, int maximumMemorySize, int fileSegmentSize) throws IOException, QSequenceException {
		final RandomAccessFile leftRAFile = new RandomAccessFile(leftFile, "r");
		final RandomAccessFile rightRAFile = new RandomAccessFile(rightFile, "r");
		try {
			return QSequenceLineMedia.createBlocksInFilesystem(new QSequenceLineRAFileData(leftRAFile), new QSequenceLineRAFileData(rightRAFile), new QSequenceLineSystemTempDirectoryFactory(), 1.0, maximumMemorySize, fileSegmentSize);
		}
		finally {
			leftRAFile.close();
			rightRAFile.close();
		}
	}

	private File createTestFile(String[] content, boolean left) throws IOException {
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

	private void compareBlocks(List expected, List actual) {
		assertEquals(expected.size(), actual.size());

		for (int index = 0; index < expected.size(); index++) {
			final QSequenceDifferenceBlock expectedBlock = (QSequenceDifferenceBlock)expected.get(index);
			final QSequenceDifferenceBlock actualBlock = (QSequenceDifferenceBlock)actual.get(index);

			assertEquals(expectedBlock.getLeftFrom(), actualBlock.getLeftFrom());
			assertEquals(expectedBlock.getLeftTo(), actualBlock.getLeftTo());
		}
	}
}