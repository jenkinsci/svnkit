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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.IOExceptionWrapper;


/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class SVNCharsetInputStream extends FilterInputStream {

    private static final int DEFAULT_BUFFER_CAPACITY = 1024;

    private SVNCharsetConvertor myCharsetConvertor;
    private byte[] mySourceBuffer;
    private ByteBuffer myConvertedBuffer;

    public SVNCharsetInputStream(InputStream in, Charset inputCharset, Charset outputCharset) {
        super(in);
        myCharsetConvertor = new SVNCharsetConvertor(inputCharset.newDecoder(), outputCharset.newEncoder());
        mySourceBuffer = new byte[DEFAULT_BUFFER_CAPACITY];
        myConvertedBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_CAPACITY);
    }

    public int read() throws IOException {
        byte[] b = new byte[1];
        int r = read(b);
        if (r <= 0) {
            return -1;
        }
        return b[0];
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte b[], int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        int available = myConvertedBuffer.position();
        while (available < len) {
            int read = in.read(mySourceBuffer);
            read = read < 0 ? 0 : read;
            boolean endOfInput = read < mySourceBuffer.length;
            try {
                myConvertedBuffer = myCharsetConvertor.convertChunk(mySourceBuffer, 0, read, myConvertedBuffer, endOfInput);
                if (endOfInput) {
                    myConvertedBuffer = myCharsetConvertor.flush(myConvertedBuffer);
                    break;
                }
            } catch (SVNException e) {
                throw new IOExceptionWrapper(e);
            }
            available = myConvertedBuffer.position();
        }
        myConvertedBuffer.flip();
        len = Math.min(myConvertedBuffer.remaining(), len);
        myConvertedBuffer.get(b, off, len);
        myConvertedBuffer.compact();
        return len == 0 ? -1 : len;
    }
}