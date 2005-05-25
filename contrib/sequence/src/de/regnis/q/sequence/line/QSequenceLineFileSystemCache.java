package de.regnis.q.sequence.line;

import java.io.*;

/**
 * @author Marc Strapetz
 */
final class QSequenceLineFileSystemCache implements QSequenceLineCache {

	// Static =================================================================

	public static QSequenceLineFileSystemCache create(QSequenceLineRAData data, File tempFile, byte[] customEolBytes) throws IOException {
		final QSequenceLineFileSystemCache cache = new QSequenceLineFileSystemCache(data, tempFile);
		final QSequenceLineReader reader = new QSequenceLineReader(customEolBytes);
		final InputStream stream = data.read(0, data.length());
		reader.read(stream, cache);
		stream.close();
		return cache;
	}

	// Fields =================================================================

	private final QSequenceLineRAData data;
	private final File indexFilePath;
	private final RandomAccessFile indexFile;

	private int lineCount;

	// Setup ==================================================================

	private QSequenceLineFileSystemCache(QSequenceLineRAData data, File tempFile) throws IOException {
		this.data = data;

		if (tempFile.exists()) {
			boolean deleted = tempFile.delete();
			if (!deleted) {
				throw new IOException("Couldn't delete file '" + tempFile + "'.");
			}
		}

		this.indexFilePath = tempFile;
		this.indexFile = new RandomAccessFile(tempFile, "rw");
	}

	// Accessing ==============================================================

	public void close() throws IOException {
		indexFile.close();
		indexFilePath.delete();
	}

	// Implemented ============================================================

	public void addLine(QSequenceLine line) throws IOException {
		indexFile.writeLong(line.getFrom());
		indexFile.writeInt(line.getLength());
		lineCount++;
	}

	public int getLineCount() {
		return lineCount;
	}

	public QSequenceLine getLine(int index) throws IOException {
		indexFile.seek(index * (8 + 4));
		final long from = indexFile.readLong();
		final int length = indexFile.readInt();
		final InputStream stream = data.read(from, length);
		final byte[] bytes = new byte[length];

		int tempOffset = 0;
		int tempLength = length;
		for (; tempLength > 0;) {
			final int readBytes = stream.read(bytes, tempOffset, tempLength);
			tempOffset += readBytes;
			tempLength -= readBytes;
		}
		stream.close();
		return new QSequenceLine(from, bytes);
	}
}