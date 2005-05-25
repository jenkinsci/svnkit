package de.regnis.q.sequence.line;

import java.io.*;
import java.util.*;

import de.regnis.q.sequence.*;
import de.regnis.q.sequence.core.*;
import de.regnis.q.sequence.media.*;

/**
 * @author Marc Strapetz
 */
public final class QSequenceLineMedia implements QSequenceCachableMedia, QSequenceMediaComparer {

	// Constants ==============================================================

	private static final int MEMORY_THRESHOLD = 1000000;

	// Static =================================================================

	public static QSequenceLineCache readLines(QSequenceLineRAData data, byte[] customEolBytes) throws IOException {
		if (data.length() <= MEMORY_THRESHOLD) {
			final InputStream stream = data.read(0, data.length());
			try {
				return QSequenceLineMemoryCache.read(stream, customEolBytes);
			}
			finally {
				stream.close();
			}
		}

		final File tempFile = File.createTempFile("q.sequence.line.", ".temp");
		tempFile.deleteOnExit();
		return QSequenceLineFileSystemCache.create(data, tempFile, customEolBytes);
	}

	public static QSequenceLineResult createBlocks(QSequenceLineRAData leftData, QSequenceLineRAData rightData, byte[] customEolBytes) throws IOException, QSequenceException {
		return createBlocks(leftData, rightData, customEolBytes, MEMORY_THRESHOLD);
	}

	public static QSequenceLineResult createBlocks(QSequenceLineRAData leftData, QSequenceLineRAData rightData, byte[] customEolBytes, long memoryThreshold) throws IOException, QSequenceException {
		if (leftData.length() <= memoryThreshold && rightData.length() <= memoryThreshold) {
			final InputStream leftStream = leftData.read(0, leftData.length());
			final InputStream rightStream = rightData.read(0, rightData.length());
			try {
				return createBlocksInMemory(leftStream, rightStream, customEolBytes);
			}
			finally {
				leftStream.close();
				rightStream.close();
			}
		}

		final File leftTempFile = File.createTempFile("q.sequence.line.", ".temp");
		final File rightTempFile = File.createTempFile("q.sequence.line.", ".temp");
		leftTempFile.deleteOnExit();
		rightTempFile.deleteOnExit();
		return createBlocksInFilesystem(leftData, leftTempFile, rightData, rightTempFile, customEolBytes);
	}

	static QSequenceLineResult createBlocksInMemory(final InputStream leftStream, final InputStream rightStream, byte[] customEolBytes) throws IOException, QSequenceException {
		final QSequenceLineMemoryCache leftCache = QSequenceLineMemoryCache.read(leftStream, customEolBytes);
		final QSequenceLineMemoryCache rightCache = QSequenceLineMemoryCache.read(rightStream, customEolBytes);
		final QSequenceLineMedia lineMedia = new QSequenceLineMedia(leftCache, rightCache);
		final QSequenceCachingMedia cachingMedia = new QSequenceCachingMedia(lineMedia, new QSequenceDummyCanceller());
		final QSequenceDiscardingMedia discardingMedia = new QSequenceDiscardingMedia(cachingMedia, new QSequenceDiscardingMediaNoConfusionDectector(true), new QSequenceDummyCanceller());
		final List blocks = new QSequenceDifference(discardingMedia, discardingMedia).getBlocks();
		new QSequenceDifferenceBlockShifter(cachingMedia, cachingMedia).shiftBlocks(blocks);
		return new QSequenceLineResult(blocks, leftCache, rightCache);
	}

	static QSequenceLineResult createBlocksInFilesystem(QSequenceLineRAData leftData, File leftTempFile, QSequenceLineRAData rightData, File rightTempFile, byte[] customEolBytes) throws IOException, QSequenceException {
		final QSequenceLineFileSystemCache leftCache = QSequenceLineFileSystemCache.create(leftData, leftTempFile, customEolBytes);
		final QSequenceLineFileSystemCache rightCache = QSequenceLineFileSystemCache.create(rightData, rightTempFile, customEolBytes);
		final QSequenceLineMedia lineMedia = new QSequenceLineMedia(leftCache, rightCache);
		final List blocks = new QSequenceDifference(lineMedia, new QSequenceMediaDummyIndexTransformer(lineMedia)).getBlocks();
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
}