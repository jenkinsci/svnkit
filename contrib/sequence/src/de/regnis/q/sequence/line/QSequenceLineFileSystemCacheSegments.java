package de.regnis.q.sequence.line;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.regnis.q.sequence.core.QSequenceAssert;

/**
 * @author Marc Strapetz
 */
class QSequenceLineFileSystemCacheSegments {

	// Fields =================================================================

	private final QSequenceLineTempDirectoryFactory tempDirectoryFactory;
	private final int maximumEntriesPerSegment;
	private final int maximumSegmentsInMemory;
	private final List segments;
	private final List memorySegments;

	private File filePath;
	private RandomAccessFile file;

	// Setup ==================================================================

	public QSequenceLineFileSystemCacheSegments(QSequenceLineTempDirectoryFactory tempDirectoryFactory, int maximumBytesInMemory, int segmentBytesSize) {
		QSequenceAssert.assertTrue(segmentBytesSize >= QSequenceLineMedia.SEGMENT_ENTRY_SIZE);
		QSequenceAssert.assertTrue(maximumBytesInMemory >= segmentBytesSize);

		this.tempDirectoryFactory = tempDirectoryFactory;
		this.maximumEntriesPerSegment = segmentBytesSize / QSequenceLineMedia.SEGMENT_ENTRY_SIZE;
		this.maximumSegmentsInMemory = maximumBytesInMemory / (maximumEntriesPerSegment * QSequenceLineMedia.SEGMENT_ENTRY_SIZE);
		this.segments = new ArrayList();
		this.memorySegments = new LinkedList();

		final QSequenceLineFileSystemCacheSegment segment = new QSequenceLineFileSystemCacheSegment(0, maximumEntriesPerSegment);
		segments.add(segment);
		memorySegments.add(segment);
	}

	// Accessing ==============================================================

	public long getFrom(int index) throws IOException {
		final int segmentIndex = index / maximumEntriesPerSegment;
		final int relativeIndex = index % maximumEntriesPerSegment;
		return getSegment(segmentIndex).getFrom(relativeIndex);
	}

	public int getLength(int index) throws IOException {
		final int segmentIndex = index / maximumEntriesPerSegment;
		final int relativeIndex = index % maximumEntriesPerSegment;
		return getSegment(segmentIndex).getLength(relativeIndex);
	}

	public int getHash(int index) throws IOException {
		final int segmentIndex = index / maximumEntriesPerSegment;
		final int relativeIndex = index % maximumEntriesPerSegment;
		return getSegment(segmentIndex).getHash(relativeIndex);
	}

	public void setFromLengthHash(int index, long from, int length, int hash) throws IOException {
		final int segmentIndex = index / maximumEntriesPerSegment;
		final int relativeIndex = index % maximumEntriesPerSegment;
		final QSequenceLineFileSystemCacheSegment segment = getSegment(segmentIndex);
		segment.setFromLengthHash(relativeIndex, from, length, hash);
	}

	public void close() throws IOException {
		if (file == null) {
			return;
		}

		file.close();
		filePath.delete();
		tempDirectoryFactory.close();
	}

	// Utils ==================================================================

	private QSequenceLineFileSystemCacheSegment getSegment(int segmentIndex) throws IOException {
		if (segmentIndex >= segments.size()) {
			final QSequenceLineFileSystemCacheSegment segment = new QSequenceLineFileSystemCacheSegment(segmentIndex, maximumEntriesPerSegment);
			segments.add(segment);
			memorySegments.add(0, segment);
			maybeUnloadSegments();
			return segment;
		}

		final QSequenceLineFileSystemCacheSegment segment = (QSequenceLineFileSystemCacheSegment)segments.get(segmentIndex);
		if (!segment.isLoaded()) {
			segment.load(getFile());
			memorySegments.add(0, segment);
			maybeUnloadSegments();
		}

		return segment;
	}

	private void maybeUnloadSegments() throws IOException {
		while (memorySegments.size() > maximumSegmentsInMemory) {
			final QSequenceLineFileSystemCacheSegment segment = (QSequenceLineFileSystemCacheSegment)memorySegments.remove(memorySegments.size() - 1);
			segment.unload(getFile());
		}
	}

	private RandomAccessFile getFile() throws IOException {
		if (file == null) {
			final File tempDirectory = tempDirectoryFactory.getTempDirectory();
			if (!tempDirectory.isDirectory()) {
				tempDirectory.mkdirs();
			}

			filePath = File.createTempFile("sequence", null, tempDirectory);
			file = QSequenceLineRandomAccessFileFactory.createRandomAccessFile(filePath, "rw");
		}

		return file;
	}
}
