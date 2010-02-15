/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.patch;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Level;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author TMate Software Ltd.
 */
public class SVNPatchFileStream {

    public interface SVNPatchFileLineFilter {

        boolean lineFilter(String line);
    }

    public interface SVNPatchFileLineTransformer {

        String lineTransformer(String line);
    }

    private static final long INFINITY = Long.MAX_VALUE;

    private File path;
    private boolean write;
    private long start;
    private long end = INFINITY;

    private RandomAccessFile file;

    private SVNPatchFileLineFilter lineFilter;
    private SVNPatchFileLineTransformer lineTransformer;

    private SVNPatchFileStream(File path, boolean write, long start, long end) {
        this(path, write);
        if (start >= 0 && end >= 0 && start <= end) {
            this.start = start;
            this.end = end;
        } else {
            throw new IllegalArgumentException("Bad start and end");
        }
    }

    private SVNPatchFileStream(File path, boolean write) {
        if (path != null) {
            this.path = path;
        } else {
            throw new IllegalArgumentException("Bad path");
        }
        this.write = write;
    }

    public static SVNPatchFileStream openReadOnly(File path) throws IOException, SVNException {
        final SVNPatchFileStream stream = new SVNPatchFileStream(path, false);
        stream.reset();
        return stream;
    }

    public static SVNPatchFileStream openRangeReadOnly(File path, long start, long end) throws IOException, SVNException {
        final SVNPatchFileStream stream = new SVNPatchFileStream(path, false, start, end);
        stream.reset();
        return stream;
    }

    public static SVNPatchFileStream openForWrite(File path) throws IOException, SVNException {
        final SVNPatchFileStream stream = new SVNPatchFileStream(path, true);
        stream.reset();
        return stream;
    }

    public File getPath() {
        return path;
    }

    public void setLineFilter(SVNPatchFileLineFilter lineFilter) {
        this.lineFilter = lineFilter;
    }

    public void setLineTransformer(SVNPatchFileLineTransformer lineTransfomer) {
        this.lineTransformer = lineTransfomer;
    }

    private RandomAccessFile getFile() throws SVNException {
        if (file == null) {
            synchronized (this) {
                if (file == null) {
                    if (write) {
                        file = SVNFileUtil.openRAFileForWriting(path, false);
                    } else {
                        file = SVNFileUtil.openRAFileForReading(path);
                    }
                }
            }
        }
        return file;
    }

    /**
     * Reset a generic stream back to its origin. E.g. On a file this would be
     * implemented as a seek to position 0). This function returns a
     * #SVN_ERR_STREAM_RESET_NOT_SUPPORTED error when the stream doesn't
     * implement resetting.
     * 
     * @throws IOException
     * @throws SVNException
     */
    public void reset() throws IOException, SVNException {
        final RandomAccessFile file = getFile();
        if (start != file.getFilePointer()) {
            file.seek(start);
        }
    }

    public void close() throws IOException {
        if (file != null) {
            file.close();
        }
    }

    public boolean isEOF() throws IOException, SVNException {
        final RandomAccessFile file = getFile();
        return file.getFilePointer() == file.length();
    }

    public long getSeekPosition() throws SVNException, IOException {
        final RandomAccessFile file = getFile();
        return file.getFilePointer();
    }

    public void setSeekPosition(long pos) throws SVNException, IOException {
        checkPos(pos);
        final RandomAccessFile file = getFile();
        file.seek(pos);
    }

    private void checkPos(long pos) throws SVNException {
        if (!isPosValid(pos)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Bad position for file ''{0}'': {1}. Range is {2}..{3}.", new Object[] {
                    path, new Long(pos), new Long(start), new Long(end)
            });
            SVNErrorManager.error(err, Level.FINE, SVNLogType.DEFAULT);
        }
    }

    private boolean isPosValid(long pos) {
        return start <= pos && pos <= end;
    }

    public void write(String str) throws SVNException, IOException {
        final RandomAccessFile file = getFile();
        final long pos = file.getFilePointer();
        checkPos(pos + str.length());
        file.write(str.getBytes());
    }

    public void write(StringBuffer str) throws SVNException, IOException {
        write(str.toString());
    }

    public void tryWrite(StringBuffer lineBuf) throws SVNException, IOException {
        write(lineBuf);
    }

    public boolean readLineWithEol(StringBuffer lineBuf, StringBuffer eolStr) throws IOException {
        return readLine(lineBuf, eolStr, true);
    }

    public boolean readLine(StringBuffer lineBuf) throws IOException {
        return readLine(lineBuf, null);
    }

    public boolean readLine(StringBuffer lineBuf, String eolStr) throws IOException {
        final StringBuffer eol = eolStr!=null ? new StringBuffer(eolStr) : null;
        return readLine(lineBuf, eol, false);
    }

    private boolean readLine(StringBuffer input, StringBuffer eolStr, boolean detectEol) throws IOException {

        input.setLength(0);
        if (eolStr != null) {
            eolStr.setLength(0);
        }

        int c = -1;
        boolean eol = false;

        while (!eol) {
            switch (c = file.read()) {
                case -1:
                case '\n':
                    if (detectEol && eolStr != null) {
                        eolStr.append('\n');
                    }
                    eol = true;
                    break;
                case '\r':
                    if (detectEol && eolStr != null) {
                        eolStr.append('\r');
                    }
                    long cur = file.getFilePointer();
                    if ((file.read()) != '\n') {
                        file.seek(cur);
                    } else {
                        if (detectEol && eolStr != null) {
                            eolStr.append('\n');
                        }
                    }
                    eol = true;
                    break;
                default:
                    input.append((char) c);
                    break;
            }
        }

        if ((c == -1) && (input.length() == 0)) {
            return true;
        }

        if (lineFilter != null) {
            if (lineFilter.lineFilter(input.toString())) {
                input.setLength(0);
                return false;
            }
        }

        if (lineTransformer != null) {
            final String line = lineTransformer.lineTransformer(input.toString());
            input.setLength(0);
            input.append(line);
        }

        return false;
    }

}
