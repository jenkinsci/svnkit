package de.regnis.q.sequence.line;

import java.io.*;

import de.regnis.q.sequence.core.*;

/**
 * @author Marc Strapetz
 */
class QSequenceLineFileSystemCacheSegment {

	// Fields =================================================================

	private final long segmentIndex;
	private final int maximumEntryCount;

	private long[] froms;
	private int[] lengths;
	private int[] hashes;

	// Setup ==================================================================

	public QSequenceLineFileSystemCacheSegment(long segmentIndex, int maximumEntryCount) {
		this.segmentIndex = segmentIndex;
		this.maximumEntryCount = maximumEntryCount;
		this.froms = new long[maximumEntryCount];
		this.lengths = new int[maximumEntryCount];
		this.hashes = new int[maximumEntryCount];
	}

	// Accessing ==============================================================

	public boolean isLoaded() {
		return froms != null;
	}

	public long getFrom(int index) {
		return froms[index];
	}

	public int getLength(int index) {
		return lengths[index];
	}

	public int getHash(int index) {
		return hashes[index];
	}

	public void setFromLengthHash(int index, long from, int length, int hash) {
		froms[index] = from;
		lengths[index] = length;
		hashes[index] = hash;
	}

	public void load(RandomAccessFile file) throws IOException {
		froms = new long[maximumEntryCount];
		lengths = new int[maximumEntryCount];
		hashes = new int[maximumEntryCount];

		final byte[] bytes = new byte[maximumEntryCount * QSequenceLineMedia.SEGMENT_ENTRY_SIZE];
		file.seek(segmentIndex * maximumEntryCount * QSequenceLineMedia.SEGMENT_ENTRY_SIZE);
		file.readFully(bytes);

		final ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		final DataInputStream is = new DataInputStream(bis);
		for (int index = 0; index < maximumEntryCount; index++) {
			froms[index] = is.readLong();
			lengths[index] = is.readInt();
			hashes[index] = is.readInt();
		}
	}

	public void unload(RandomAccessFile file) throws IOException {
		final ByteArrayOutputStream bos = new ByteArrayOutputStream(maximumEntryCount * QSequenceLineMedia.SEGMENT_ENTRY_SIZE);
		final DataOutputStream os = new DataOutputStream(bos);
		for (int index = 0; index < maximumEntryCount; index++) {
			os.writeLong(froms[index]);
			os.writeInt(lengths[index]);
			os.writeInt(hashes[index]);
		}

		final byte[] bytes = bos.toByteArray();
		QSequenceAssert.assertEquals(maximumEntryCount * QSequenceLineMedia.SEGMENT_ENTRY_SIZE, bytes.length);

		final long offset = segmentIndex * maximumEntryCount * QSequenceLineMedia.SEGMENT_ENTRY_SIZE;
		file.seek(offset);
		file.write(bytes);

		froms = null;
		lengths = null;
		hashes = null;
	}
}
