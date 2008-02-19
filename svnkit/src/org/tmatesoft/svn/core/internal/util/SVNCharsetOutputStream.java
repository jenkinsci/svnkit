/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.IOExceptionWrapper;


/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class SVNCharsetOutputStream extends FilterOutputStream {

    private static final int DEFAULT_BUFFER_CAPACITY = 1024;

    private SVNCharsetConvertor myCharsetConvertor;
    private ByteBuffer myByteBuffer;
    private ByteBuffer myOutputBuffer;

    public SVNCharsetOutputStream(OutputStream out, Charset inputCharset, Charset outputCharset) {
        super(out);
        myCharsetConvertor = new SVNCharsetConvertor(inputCharset.newDecoder(), outputCharset.newEncoder());
    }

    public void write(int b) throws IOException {
        write(new byte[]{(byte) (b & 0xFF)});
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        convertAndWrite(false);
        myByteBuffer = copy(b, off, len, myByteBuffer);
    }

    public void flush() throws IOException {
        convertAndWrite(true);
        myByteBuffer = null;

        try {
            myOutputBuffer = myCharsetConvertor.flush(myOutputBuffer);
            myOutputBuffer.flip();
            out.write(myOutputBuffer.array(), myOutputBuffer.arrayOffset(), myOutputBuffer.limit());
        } catch (SVNException e) {
            throw new IOExceptionWrapper(e);
        }

        super.flush();
    }

    public void close() throws IOException {
        flush();
        out.close();
    }

    private void convertAndWrite(boolean endOfInput) throws IOException {
        if (myByteBuffer != null) {
            try {
                int offset = myByteBuffer.arrayOffset() + myByteBuffer.position();
                int length = myByteBuffer.remaining();
                myOutputBuffer = myCharsetConvertor.convertChunk(myByteBuffer.array(), offset, length, myOutputBuffer, endOfInput);
                myByteBuffer.clear();
                myOutputBuffer.flip();
                out.write(myOutputBuffer.array(), myOutputBuffer.arrayOffset(), myOutputBuffer.limit());
            } catch (SVNException e) {
                throw new IOExceptionWrapper(e);
            }
        }
    }

    private static ByteBuffer copy(byte[] src, int offset, int length, ByteBuffer dst) {
        if (dst == null) {
            dst = ByteBuffer.allocate(Math.max(length * 3 / 2, DEFAULT_BUFFER_CAPACITY));
        } else if (dst.remaining() < length) {
            ByteBuffer expandedBuffer = ByteBuffer.allocate((dst.position() + length) * 3 / 2);
            dst.flip();
            expandedBuffer.put(dst);
            dst = expandedBuffer;
        }
        dst.put(src, offset, length);
        dst.flip();
        return dst;
    }
}