package de.regnis.q.sequence.line;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import de.regnis.q.sequence.QSequenceDifference;
import de.regnis.q.sequence.QSequenceDifferenceBlockShifter;
import de.regnis.q.sequence.line.simplifier.*;
import de.regnis.q.sequence.core.QSequenceAssert;
import de.regnis.q.sequence.core.QSequenceDummyCanceller;
import de.regnis.q.sequence.core.QSequenceException;
import de.regnis.q.sequence.media.QSequenceCachableMedia;
import de.regnis.q.sequence.media.QSequenceCachingMedia;
import de.regnis.q.sequence.media.QSequenceDiscardingMedia;
import de.regnis.q.sequence.media.QSequenceDiscardingMediaNoConfusionDectector;
import de.regnis.q.sequence.media.QSequenceMediaComparer;
import de.regnis.q.sequence.media.QSequenceMediaDummyIndexTransformer;

/**
 * @author Marc Strapetz
 */
public final class QSequenceLineMedia implements QSequenceCachableMedia, QSequenceMediaComparer {

	// Constants ==============================================================

	public static final int FILE_SEGMENT_SIZE = 16384;
	public static final int SEGMENT_ENTRY_SIZE = 16;
	public static final int MEMORY_THRESHOLD;
	public static final double SEARCH_DEPTH_EXPONENT;

	static {
		MEMORY_THRESHOLD = parseMemoryTreshold(System.getProperty("q.sequence.memory-threshold", "1M"));
	}

	static {
		if (System.getProperty("q.sequence.search-depth-exponent") != null) {
			SEARCH_DEPTH_EXPONENT = Math.max(0.1, Math.min(1.0, Double.parseDouble(System.getProperty("q.sequence.search-depth-exponent"))));
		}
		else {
			SEARCH_DEPTH_EXPONENT = .5;
		}
	}

	// Static =================================================================

	public static QSequenceLineCache readLines(QSequenceLineRAData data) throws IOException {
		if (data.length() <= MEMORY_THRESHOLD) {
			final InputStream stream = data.read(0, data.length());
			try {
				return QSequenceLineMemoryCache.read(stream, new QSequenceLineDummySimplifier());
			}
			finally {
				stream.close();
			}
		}

		return QSequenceLineFileSystemCache.create(data, new QSequenceLineSystemTempDirectoryFactory(), MEMORY_THRESHOLD, FILE_SEGMENT_SIZE, new QSequenceLineDummySimplifier());
	}

	public static QSequenceLineResult createBlocks(QSequenceLineRAData leftData, QSequenceLineRAData rightData) throws IOException, QSequenceException {
		return createBlocks(leftData, rightData, new QSequenceLineDummySimplifier());
	}

	public static QSequenceLineResult createBlocks(QSequenceLineRAData leftData, QSequenceLineRAData rightData, QSequenceLineSimplifier simplifier) throws IOException, QSequenceException {
		return createBlocks(leftData, rightData, MEMORY_THRESHOLD, FILE_SEGMENT_SIZE, SEARCH_DEPTH_EXPONENT, new QSequenceLineSystemTempDirectoryFactory(), simplifier);
	}

	public static QSequenceLineResult createBlocks(QSequenceLineRAData leftData, QSequenceLineRAData rightData, int memoryThreshold, int fileSegmentSize, double searchDepthExponent, QSequenceLineTempDirectoryFactory tempDirectoryFactory, QSequenceLineSimplifier simplifier) throws IOException, QSequenceException {
		if (leftData.length() <= memoryThreshold && rightData.length() <= memoryThreshold) {
			final InputStream leftStream = leftData.read(0, leftData.length());
			final InputStream rightStream = rightData.read(0, rightData.length());
			try {
				return createBlocksInMemory(leftStream, rightStream, searchDepthExponent, simplifier);
			}
			finally {
				leftStream.close();
				rightStream.close();
			}
		}

		return createBlocksInFilesystem(leftData, rightData, tempDirectoryFactory, searchDepthExponent, memoryThreshold, fileSegmentSize, simplifier);
	}

	static QSequenceLineResult createBlocksInMemory(InputStream leftStream, InputStream rightStream, double searchDepthExponent, QSequenceLineSimplifier simplifier) throws IOException, QSequenceException {
		final QSequenceLineMemoryCache leftCache = QSequenceLineMemoryCache.read(leftStream, simplifier);
		final QSequenceLineMemoryCache rightCache = QSequenceLineMemoryCache.read(rightStream, simplifier);
		final QSequenceLineMedia lineMedia = new QSequenceLineMedia(leftCache, rightCache);
		final QSequenceCachingMedia cachingMedia = new QSequenceCachingMedia(lineMedia, new QSequenceDummyCanceller());
		final QSequenceDiscardingMedia discardingMedia = new QSequenceDiscardingMedia(cachingMedia, new QSequenceDiscardingMediaNoConfusionDectector(true), new QSequenceDummyCanceller());
		final List blocks = new QSequenceDifference(discardingMedia, discardingMedia, getSearchDepth(lineMedia, searchDepthExponent)).getBlocks();
		new QSequenceDifferenceBlockShifter(cachingMedia, cachingMedia).shiftBlocks(blocks);
		return new QSequenceLineResult(blocks, leftCache, rightCache);
	}

	static QSequenceLineResult createBlocksInFilesystem(QSequenceLineRAData leftData, QSequenceLineRAData rightData, QSequenceLineTempDirectoryFactory tempDirectoryFactory, double searchDepthExponent, int memoryThreshold, int fileSegmentSize, QSequenceLineSimplifier simplifier) throws IOException, QSequenceException {
		final QSequenceLineFileSystemCache leftCache = QSequenceLineFileSystemCache.create(leftData, tempDirectoryFactory, memoryThreshold, fileSegmentSize, simplifier);
		final QSequenceLineFileSystemCache rightCache = QSequenceLineFileSystemCache.create(rightData, tempDirectoryFactory, memoryThreshold, fileSegmentSize, simplifier);
		final QSequenceLineMedia lineMedia = new QSequenceLineMedia(leftCache, rightCache);
		final List blocks = new QSequenceDifference(lineMedia, new QSequenceMediaDummyIndexTransformer(lineMedia), getSearchDepth(lineMedia, searchDepthExponent)).getBlocks();
		new QSequenceDifferenceBlockShifter(lineMedia, lineMedia).shiftBlocks(blocks);
		return new QSequenceLineResult(blocks, leftCache, rightCache);
	}

	// Fields =================================================================

	private final QSequenceLineCache leftCache;
	private final QSequenceLineCache rightCache;

	// Setup ==================================================================

	public QSequenceLineMedia(QSequenceLineCache leftCache, QSequenceLineCache rightCache) {
		this.leftCache = leftCache;
		this.rightCache = rightCache;
	}

	// Implemented ============================================================

	public int getLeftLength() {
		return leftCache.getLineCount();
	}

	public int getRightLength() {
		return rightCache.getLineCount();
	}

	public Object getMediaLeftObject(int index) throws QSequenceException {
		try {
			return leftCache.getLine(index);
		}
		catch (IOException ex) {
			throw new QSequenceException(ex);
		}
	}

	public Object getMediaRightObject(int index) throws QSequenceException {
		try {
			return rightCache.getLine(index);
		}
		catch (IOException ex) {
			throw new QSequenceException(ex);
		}
	}

	public boolean equals(int leftIndex, int rightIndex) throws QSequenceException {
		try {
			final int leftHash = leftCache.getLineHash(leftIndex);
			final int rightHash = rightCache.getLineHash(rightIndex);
			if (leftHash != 0 && rightHash != 0 && leftHash != rightHash) {
				return false;
			}

			return leftCache.getLine(leftIndex).equals(rightCache.getLine(rightIndex));
		}
		catch (IOException ex) {
			throw new QSequenceException(ex);
		}
	}

	public boolean equalsLeft(int left1, int left2) throws QSequenceException {
		try {
			return leftCache.getLine(left1).equals(leftCache.getLine(left2));
		}
		catch (IOException ex) {
			throw new QSequenceException(ex);
		}
	}

	public boolean equalsRight(int right1, int right2) throws QSequenceException {
		try {
			return rightCache.getLine(right1).equals(rightCache.getLine(right2));
		}
		catch (IOException ex) {
			throw new QSequenceException(ex);
		}
	}

	// Utils ==================================================================

	private static int getSearchDepth(QSequenceLineMedia lineMedia, double searchDepthExponent) {
		QSequenceAssert.assertTrue(searchDepthExponent >= 0.0 && searchDepthExponent <= 1.0);

		if (searchDepthExponent == 1.0) {
			return Integer.MAX_VALUE;
		}

		return Math.max(256, (int)Math.pow(lineMedia.getLeftLength() + lineMedia.getRightLength(), searchDepthExponent));
	}

	private static int parseMemoryTreshold(String value) {
		if (value == null) {
			value = "1M";
		}
		value = value.toLowerCase();
		int factor = 1;
		if (value.endsWith("m")) {
			value = value.substring(0, value.length() - 1);
			factor = 1048576;
		}
		else if (value.endsWith("mb")) {
			value = value.substring(0, value.length() - 2);
			factor = 1048576;
		}
		else if (value.endsWith("k")) {
			value = value.substring(0, value.length() - 1);
			factor = 1024;
		}
		else if (value.endsWith("kb")) {
			value = value.substring(0, value.length() - 2);
			factor = 1024;
		}
		try {
			int amount = Integer.parseInt(value);
			amount = factor * amount;
			if (amount < FILE_SEGMENT_SIZE) {
				amount = FILE_SEGMENT_SIZE;
			}
			return amount;
		}
		catch (NumberFormatException e) {
			return parseMemoryTreshold(null);
		}
	}
}