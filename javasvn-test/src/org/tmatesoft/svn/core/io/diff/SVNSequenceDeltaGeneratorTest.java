package org.tmatesoft.svn.core.io.diff;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNEditor;

import de.regnis.q.sequence.line.QSequenceLineMedia;

/**
 * @author Marc Strapetz
 */
public class SVNSequenceDeltaGeneratorTest extends TestCase {

    public static String createLines(String text) {
        return createLines(text, "\n");
    }

    public static String createLines(String text, String eol) {
        final StringBuffer lines = new StringBuffer();
        for (int ch = 0; ch < text.length(); ch++) {
            lines.append(text.charAt(ch));
            lines.append(eol);
        }
        return lines.toString();
    }

    public void test() throws SVNException, IOException {
        test("abc", "bcd");
        test("abcde", "aabcef");
        test("abc", "xyz");
        test("a", "xay");
        test("xay", "a");
        test("a", "");
        test("", "x");
        test("harac", "hc");
        test("ahugeamountofcharacters", "separatedbynosemicolon");
        test("some shifting issues: xababab", "some shifting issues: yabababab");

        test("abc", "\r\n", "xyz", "\r\n");
        test("abc", "\r", "xyz", "\n");
        test("abc", "\n", "xyz", "\r\n");
    }

    private void test(String workFile, String baseFile) throws SVNException, IOException {
        test(workFile, "\n", baseFile, "\n");
    }

    private void test(String workFile, String workEol, String baseFile, String baseEol) throws IOException, SVNException {
        test(workFile, workEol, baseFile, baseEol, QSequenceLineMedia.MEMORY_THRESHOLD, 0.5);
        test(workFile, workEol, baseFile, baseEol, QSequenceLineMedia.SEGMENT_ENTRY_SIZE, 0.5);
        test(workFile, workEol, baseFile, baseEol, QSequenceLineMedia.SEGMENT_ENTRY_SIZE, 0.1);
    }

    private void test(String workFile, String workEol, String baseFile, String baseEol, int memoryThreshold, double searchDepthExponent) throws SVNException, IOException {
        workFile = createLines(workFile, workEol);
        baseFile = createLines(baseFile, baseEol);

        final File directory = File.createTempFile("javasvn", "test");
        directory.delete();
        directory.mkdirs();

        final RAData testData;
        try {
            final ISVNDeltaGenerator generator = new SVNSequenceDeltaGenerator(directory, memoryThreshold, memoryThreshold, searchDepthExponent);
            final DeltaConsumer consumer = new DeltaConsumer();
            generator.generateDiffWindow("", consumer, new RAData(workFile), new RAData(baseFile));

            testData = new RAData("");

            for (int index = 0; index < consumer.getWindows().size(); index++) {
//                final SVNDiffWindow window = (SVNDiffWindow) consumer.getWindows().get(index);
//                final ByteArrayOutputStream stream = (ByteArrayOutputStream) consumer.getStreams().get(index);
//                window.apply(new RAData(baseFile), testData, new ByteArrayInputStream(stream.toByteArray()), 0);
            }
        } finally {
            final boolean deleted = directory.delete();
            assertTrue(deleted);
        }

        assertEquals(workFile, testData.toString());
    }

    private static final class RAData implements ISVNRAData {

        private final StringBuffer myText;

        public RAData(String text) {
            this.myText = new StringBuffer(text);
        }

        public InputStream read(long offset, long length) {
            return new ByteArrayInputStream(myText.toString().getBytes(), (int) offset, (int) length);
        }

        public void append(InputStream source, long length) throws SVNException {
            try {
                for (int index = 0; index < length; index++) {
                    myText.append((char) source.read());
                }
            } catch (IOException ex) {
                throw new SVNException(ex);
            }
        }

        public long lastModified() {
            return 0;
        }

        public void close() {
        }

        public long length() {
            return myText.toString().getBytes().length;
        }

        public InputStream readAll() {
            return read(0, length());
        }

        public String toString() {
            return myText.toString();
        }
    }

    private static final class DeltaConsumer implements ISVNEditor {

        private final List windows = new ArrayList();
        private final List streams = new ArrayList();

        public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) {
            final ByteArrayOutputStream stream = new ByteArrayOutputStream();
            windows.add(diffWindow);
            streams.add(stream);
            return stream;
        }

        public void textDeltaEnd(String path) throws SVNException {
            try {
                ((ByteArrayOutputStream) streams.get(streams.size() - 1)).close();
            } catch (IOException ex) {
                throw new SVNException(ex);
            }
        }

        public List getWindows() {
            return windows;
        }

        public List getStreams() {
            return streams;
        }

        public void targetRevision(long revision) {
        }

        public void openRoot(long revision) {
        }

        public void deleteEntry(String path, long revision) {
        }

        public void absentDir(String path) {
        }

        public void absentFile(String path) {
        }

        public void addDir(String path, String copyFromPath, long copyFromRevision) {
        }

        public void openDir(String path, long revision) {
        }

        public void changeDirProperty(String name, String value) {
        }

        public void closeDir() {
        }

        public void addFile(String path, String copyFromPath, long copyFromRevision) {
        }

        public void openFile(String path, long revision) {
        }

        public void applyTextDelta(String path, String baseChecksum) {
        }

        public void changeFileProperty(String path, String name, String value) {
        }

        public void closeFile(String path, String textChecksum) {
        }

        public SVNCommitInfo closeEdit() {
            return null;
        }

        public void abortEdit() {
        }
    }
}
