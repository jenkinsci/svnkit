package org.tmatesoft.svn.core.internal.io.fs.revprop;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.io.fs.FSFile;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCProperties;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

public class SVNFSFSPackedRevProps {

    public static SVNFSFSPackedRevProps fromCompressedByteArray(byte[] compressedData) throws SVNException {
        final byte[] uncompressedData = decompress(compressedData);
        return fromUncompressedByteArray(uncompressedData);
    }

    private static SVNFSFSPackedRevProps fromUncompressedByteArray(byte[] uncompressedData) throws SVNException {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(uncompressedData);
        try {
            final long firstRevision = readNumber(inputStream);
            final long revisionsCount = readNumber(inputStream);

            int offset = 0;

            final List<Entry> entries = new ArrayList<Entry>((int) revisionsCount);
            for (int i = 0; i < revisionsCount; i++) {
                final Entry entry = new Entry(uncompressedData, offset, (int) readNumber(inputStream));
                entries.add(entry);

                offset += entry.length;
            }
            if (inputStream.read() != '\n') {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Header end not found");
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }

            final int headerLength = uncompressedData.length - inputStream.available();
            for (Entry entry : entries) {
                entry.offset += headerLength;
            }

            return new SVNFSFSPackedRevProps(firstRevision, entries, uncompressedData);

        } finally {
            SVNFileUtil.closeFile(inputStream);
        }
    }

    private final long firstRevision;
    private final List<Entry> entries;

    //cached values
    private byte[] cachedUncompressedByteArray;

    private SVNFSFSPackedRevProps(long firstRevision, List<Entry> entries, byte[] cachedUncompressedByteArray) {
        this.firstRevision = firstRevision;
        this.entries = entries;
        this.cachedUncompressedByteArray = cachedUncompressedByteArray;
    }

    public long getFirstRevision() {
        return firstRevision;
    }

    public long getRevisionsCount() {
        return entries.size();
    }

    public byte[] asCompressedLevelNoneByteArray() throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            writeCompressedLevelNone(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } finally {
            SVNFileUtil.closeFile(byteArrayOutputStream);
        }
    }

    public void writeCompressedLevelNone(OutputStream outputStream) throws SVNException {
        compressLevelNone(asUncompressedByteArray(), outputStream);
    }

    public SVNProperties parseProperties(long revision) throws SVNException {
        final int revisionIndex = (int) (revision - getFirstRevision());
        if (revisionIndex >= getRevisionsCount()) {
            return null;
        }
        final Entry entry = entries.get(revisionIndex);
        return parseProperties(entry.data, entry.offset, entry.length);
    }

    private static byte[] decompress(byte[] compressedData) throws SVNException {
        ByteArrayOutputStream outputStream = null;
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedData);
        InputStream inputStream = byteArrayInputStream;
        try {
            final long uncompressedSize = readEncodedUncompressedSize(byteArrayInputStream, 10);
            if (uncompressedSize == byteArrayInputStream.available()) {
                return arrayCopyOfRange(compressedData, (int)(compressedData.length - uncompressedSize), (int)uncompressedSize);
            }

            //otherwise pass the stream via inflater
            outputStream = new ByteArrayOutputStream();
            inputStream = new InflaterInputStream(inputStream);

            final byte[] buffer = new byte[2048];

            while (true) {
                final int read = inputStream.read(buffer);
                if (read < 0) {
                    break;
                }
                outputStream.write(buffer, 0, read);
            }

            return outputStream.toByteArray();

        } catch (IOException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR);
            SVNErrorManager.error(errorMessage, e, SVNLogType.FSFS);
        } finally {
            SVNFileUtil.closeFile(inputStream);
            SVNFileUtil.closeFile(outputStream);
        }

        return null;
    }

    private static byte[] arrayCopyOfRange(byte[] bytes, int offset, int length) {
        //we can't use Arrays.copyOf because it appeared in Java 6 only
        final byte[] copiedBytes = new byte[length];
        System.arraycopy(bytes, offset, copiedBytes, 0, length);
        return copiedBytes;
    }

    protected static void compressLevelNone(byte[] uncompressedData, OutputStream outputStream) throws SVNException {
        writeEncodedCompressedSize(uncompressedData, outputStream);
        writeBody(uncompressedData, outputStream);
    }

    private static void writeBody(byte[] bytes, OutputStream outputStream) throws SVNException {
        try {
            outputStream.write(bytes);
        } catch (IOException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR);
            SVNErrorManager.error(errorMessage, e, SVNLogType.FSFS);
        }
    }

    private static void writeEncodedCompressedSize(byte[] data, OutputStream outputStream) throws SVNException {
        long v = data.length >> 7;
        int n = 1;
        while (v > 0) {
            v = v >> 7;
            n++;
        }

        while (--n >= 0) {
            final byte cont = (byte) (((n > 0) ? 0x1 : 0x0) << 7);
            final byte b = (byte) (((data.length >> (n * 7)) & 0x7f) | cont);
            try {
                outputStream.write(b);
            } catch (IOException e) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR);
                SVNErrorManager.error(errorMessage, e, SVNLogType.FSFS);
            }
        }
    }

    private static long readEncodedUncompressedSize(InputStream inputStream, int lengthRecordSize) throws SVNException {
        int temp = 0;
        int bytesRead = 0;

        while (true) {
            if (lengthRecordSize == bytesRead) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_INVALID_HEADER, "Decompression of svndiff data failed: size too large");
                SVNErrorManager.error(errorMessage, SVNLogType.FSFS);
            }
            int c = 0;
            try {
                c = inputStream.read();
            } catch (IOException e) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR);
                SVNErrorManager.error(errorMessage, e, SVNLogType.FSFS);
            }
            if (c < 0) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.SVNDIFF_UNEXPECTED_END, "Decompression of svndiff data failed: no size");
                SVNErrorManager.error(errorMessage, SVNLogType.FSFS);
            }
            bytesRead++;
            temp = (temp << 7) | (c & 0x7f);
            if (c < 0x80) {
                return temp;
            }
        }
    }

    private byte[] asUncompressedByteArray() throws SVNException {
        if (cachedUncompressedByteArray == null) {
            cachedUncompressedByteArray = toUncompressedByteArray();
        }
        return cachedUncompressedByteArray;
    }

    private SVNProperties parseProperties(byte[] data, int offset, int length) throws SVNException {
        final FSFile fsFile = new FSFile(data, offset, length);
        try {
            return fsFile.readProperties(false, true);
        } finally {
            fsFile.close();
        }
    }

    private byte[] toUncompressedByteArray() throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            writeNumber(byteArrayOutputStream, getFirstRevision());
            writeNumber(byteArrayOutputStream, getRevisionsCount());

            for (Entry entry : entries) {
                writeNumber(byteArrayOutputStream, entry.getSize());
            }
            byteArrayOutputStream.write('\n');
            for (Entry entry : entries) {
                byteArrayOutputStream.write(entry.data, entry.offset, entry.length);
            }
            return byteArrayOutputStream.toByteArray();
        } finally {
            SVNFileUtil.closeFile(byteArrayOutputStream);
        }
    }

    private void writeNumber(OutputStream outputStream, long number) throws SVNException {
        try {
            outputStream.write(String.valueOf(number).getBytes());
            outputStream.write('\n');
        } catch (IOException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR);
            SVNErrorManager.error(errorMessage, e, SVNLogType.FSFS);
        }
    }

    private static long readNumber(InputStream inputStream) throws SVNException {
        char[] digits = new char[20];
        int digitsCount = 0;

        while (true) {
            int c = 0;
            try {
                c = inputStream.read();
            } catch (IOException e) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR);
                SVNErrorManager.error(errorMessage, e, SVNLogType.FSFS);
            }
            if (c < 0) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT);
                SVNErrorManager.error(errorMessage, SVNLogType.FSFS);
            }
            if (c != '\n' && (c < '0'|| c > '9')) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT);
                SVNErrorManager.error(errorMessage, SVNLogType.FSFS);
            }
            if (c == '\n') {
                return Long.parseLong(new String(digits, 0, digitsCount));
            }
            digits[digitsCount] = (char) c;
            digitsCount++;
        }
    }

    public static class Builder {
        private long firstRevision;
        private final List<Entry> entries;

        public Builder() {
            this.firstRevision = -1;
            this.entries = new ArrayList<Entry>();
        }

        public SVNFSFSPackedRevProps build() throws SVNException {
            if (!SVNRevision.isValidRevisionNumber(firstRevision)) {
                SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, "First revision is not set");
                SVNErrorManager.error(errorMessage, SVNLogType.FSFS);
            }
            return new SVNFSFSPackedRevProps(firstRevision, entries, null);
        }

        public void setFirstRevision(long firstRevision) {
            this.firstRevision = firstRevision;
        }

        public void addByteArrayEntry(byte[] data) {
            addByteArrayEntry(data, 0, data.length);
        }

        public void addByteArrayEntry(byte[] data, int offset, int length) {
            entries.add(new Entry(data, offset, length));
        }
    }

    private static class Entry {
        private byte[] data;
        private int offset;
        private int length;

        private Entry(byte[] data, int offset, int length) {
            this.data = data;
            this.offset = offset;
            this.length = length;
        }

        public long getSize() {
            return length;
        }
    }
}
