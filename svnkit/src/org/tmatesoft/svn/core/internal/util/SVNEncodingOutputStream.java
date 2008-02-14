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
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CharacterCodingException;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.internal.wc.IOExceptionWrapper;


/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class SVNEncodingOutputStream extends FilterOutputStream {

    private static final int DEFAULT_BUFFER_CAPACITY = 1024;

    private CharsetEncoder myEncoder;
    private CharsetDecoder myDecoder;

    private CharBuffer myCharBuffer;
    private ByteBuffer myInputByteBuffer;
    private ByteBuffer myOutputByteBuffer;

    public SVNEncodingOutputStream(OutputStream out, CharsetDecoder decoder, CharsetEncoder encoder) {
        super(out);
        myDecoder = decoder;
        myEncoder = encoder;
    }

    public void write(int b) throws IOException {
        write(new byte[]{(byte) (b & 0xFF)});
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (myInputByteBuffer != null) {
            convertAndWrite(false);
        }

        myInputByteBuffer = allocate(myInputByteBuffer, len);
        myInputByteBuffer.put(b, off, len);
    }

    private void convertAndWrite(boolean endOfInput) throws IOException {
        myInputByteBuffer.flip();
        myCharBuffer = allocate(myCharBuffer, (int) (myDecoder.maxCharsPerByte() * myInputByteBuffer.remaining()));

        CoderResult result = myDecoder.decode(myInputByteBuffer, myCharBuffer, endOfInput);
        if (result.isError()) {
            throwException(result);
        } else if (result.isUnderflow()) {
            myInputByteBuffer.compact();
        } else {
            myInputByteBuffer.clear();
        }

        myCharBuffer.flip();
        myOutputByteBuffer = allocate(myOutputByteBuffer, (int) (myEncoder.maxBytesPerChar() * myCharBuffer.remaining()));

        result = myEncoder.encode(myCharBuffer, myOutputByteBuffer, endOfInput);
        if (result.isError()) {
            throwException(result);
        } else if (result.isUnderflow()) {
            myCharBuffer.compact();
        } else {
            myCharBuffer.clear();
        }

        myOutputByteBuffer.flip();
        super.write(myOutputByteBuffer.array(), myOutputByteBuffer.arrayOffset(), myOutputByteBuffer.limit());
        myOutputByteBuffer.clear();
    }

    public void flush() throws IOException {
        if (myInputByteBuffer != null) {
            convertAndWrite(true);
            myInputByteBuffer = null;
        }

        myDecoder.flush(myCharBuffer);
        myCharBuffer.flip();
        myOutputByteBuffer = allocate(myOutputByteBuffer, (int) (myEncoder.maxBytesPerChar() * myCharBuffer.remaining()));
        myEncoder.encode(myCharBuffer, myOutputByteBuffer, true);
        myOutputByteBuffer.flip();
        super.write(myOutputByteBuffer.array(), myOutputByteBuffer.arrayOffset(), myOutputByteBuffer.limit());

        myEncoder.flush(myOutputByteBuffer);
        myOutputByteBuffer.flip();
        super.write(myOutputByteBuffer.array(), myOutputByteBuffer.arrayOffset(), myOutputByteBuffer.limit());

        super.flush();
    }

    public void close() throws IOException {
        flush();
        super.close();
    }

    private static ByteBuffer allocate(ByteBuffer buffer, int length) {
        if (buffer == null) {
            length = Math.max(length, DEFAULT_BUFFER_CAPACITY);
            return ByteBuffer.allocate(length);
        }
        if (buffer.remaining() < length) {
            ByteBuffer expandedBuffer = ByteBuffer.allocate((buffer.position() + length) * 3 / 2);
            buffer.flip();
            expandedBuffer.put(buffer);
            return expandedBuffer;
        }
        return buffer;
    }

    private static CharBuffer allocate(CharBuffer buffer, int length) {
        if (buffer == null) {
            length = Math.max(length, DEFAULT_BUFFER_CAPACITY);
            return CharBuffer.allocate(length);
        }
        if (buffer.remaining() < length) {
            CharBuffer expandedBuffer = CharBuffer.allocate((buffer.position() + length) * 3 / 2);
            buffer.flip();
            expandedBuffer.put(buffer);
            return expandedBuffer;
        }
        return buffer;
    }

    private static void throwException(CoderResult result) throws IOExceptionWrapper {
        try {
            result.throwException();
        } catch (CharacterCodingException e) {
            SVNException svne = new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
            throw new IOExceptionWrapper(svne);
        }
    }
}