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

    private SVNCharsetConvertor myCharsetConvertor;
    private byte[] myBuffer;
    private int myOffset;
    private int myLength;
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
        if (myBuffer != null) {
            try {
                myOutputBuffer = myCharsetConvertor.convertChunk(myBuffer, myOffset, myLength, myOutputBuffer, false);
                myOutputBuffer.flip();
                out.write(myOutputBuffer.array(), myOutputBuffer.arrayOffset(), myOutputBuffer.limit());
            } catch (SVNException e) {
                throw new IOExceptionWrapper(e);
            }
        }
        myBuffer = b;
        myOffset = off;
        myLength = len;
    }

    public void flush() throws IOException {
        if (myBuffer != null) {
            try {
                myOutputBuffer = myCharsetConvertor.convertChunk(myBuffer, myOffset, myLength, myOutputBuffer, true);
                myOutputBuffer.flip();
                out.write(myOutputBuffer.array(), myOutputBuffer.arrayOffset(), myOutputBuffer.limit());
            } catch (SVNException e) {
                throw new IOExceptionWrapper(e);
            } finally {
                myBuffer = null;
            }
        }

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
        super.close();
    }
}