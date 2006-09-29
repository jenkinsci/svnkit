/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.util.Map;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNTranslatorInputStream extends InputStream {
    
    private PushbackInputStream mySource;
    private byte[] myEOLs;
    private Map myKeywords;
    private ByteBuffer myBuffer;


    public SVNTranslatorInputStream(InputStream source, byte[] eols, Map keywords) {
        if (keywords != null && keywords.isEmpty()) {
            keywords = null;
        }
        mySource = new PushbackInputStream(source, 2048);
        myEOLs = eols;
        myKeywords = keywords;
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

    
    public int read(byte[] b, int off, int len) throws IOException {
        int toRead = len;
        if (myBuffer != null) {
            toRead = len - myBuffer.position();
        }
        if (toRead > 0) {
            ensureBufferSize(toRead);
            //  fill buffer to contain at least 'len' bytes.
            fillBuffer(toRead);
        }
        // copy buffer contents to b[] (len) and shift what remains in buffer.
        myBuffer.flip();
        myBuffer.get(b, off, Math.min(len, myBuffer.remaining()));
        myBuffer.compact();
        // return read length.
        return len;
    }

    private int fillBuffer(int len) throws IOException {
        // put at least 'len' of translated bytes into the buffer.
        // or less if there are no more bytes in the source.
        int read = myBuffer.position();
        byte[] keywordBuffer = new byte[256];

        while (myBuffer.position() - read < len) {
            int r = mySource.read();
            if (r < 0) {
                return myBuffer.position() - read;
            }
            if ((r == '\r' || r == '\n') && myEOLs != null) {
                int next = mySource.read();
                read++;
                myBuffer.put(myEOLs);
                if (r == '\r' && next == '\n') {
                    continue;
                }
                if (next < 0) {
                    return myBuffer.position() - read;
                }
                mySource.unread(next);
            } else if (r == '$' && myKeywords != null) {
                // advance in buffer for 256 more chars.
                myBuffer.put((byte) (r & 0xFF));
                int length = mySource.read(keywordBuffer);
                int keywordLength = 0;
                for (int i = 0; i < length; i++) {
                    if (keywordBuffer[i] == '\r' || keywordBuffer[i] == '\n') {
                        // failure, save all before i, unread remains.
                        myBuffer.put(keywordBuffer, 0, i);
                        mySource.unread(keywordBuffer, i, length - i);
                        keywordLength = -1;
                        break;
                    } else if (keywordBuffer[i] == '$') {
                        keywordLength = i + 1;
                        break;
                    }
                }
                if (keywordLength == 0) {
                    if (length > 0) {
                        myBuffer.put(keywordBuffer, 0, length);
                    }
                } else if (keywordLength > 0) {
                    int from = translateKeyword(keywordBuffer, keywordLength);
                    mySource.unread(keywordBuffer, from, length - from);
                }
            } else {
                myBuffer.put((byte) (r & 0xFF));
            }
        }
        return read;
    }
    
    private int translateKeyword(byte[] keyword, int length) throws IOException {
        // $$ = 0, 2 => 1,0
        String keywordName = null;
        int i = 0;
        for (i = 0; i < length; i++) {
            if (keyword[i] == '$' || keyword[i] == ':') {
                // from first $ to the offset i, exclusive
                keywordName = new String(keyword, 0, i, "UTF-8");
                break;
            }
            // write to os, we do not need it.
            myBuffer.put(keyword[i]);
        }

        if (!myKeywords.containsKey(keywordName)) {
            // unknown keyword, just write trailing chars.
            // already written is $keyword[i]..
            // but do not write last '$' - it could be a start of another
            // keyword.
            myBuffer.put(keyword, i, length - i - 1);
            return length - 1;
        }
        byte[] value = (byte[]) myKeywords.get(keywordName);
        // now i points to the first char after keyword name.
        if (length - i > 5 && keyword[i] == ':' && keyword[i + 1] == ':'
                && keyword[i + 2] == ' '
                && (keyword[length - 2] == ' ' || keyword[length - 2] == '#')) {
            // :: x $
            // fixed size keyword.
            // 1. write value to keyword
            int vOffset = 0;
            int start = i;
            for (i = i + 3; i < length - 2; i++) {
                if (value == null) {
                    keyword[i] = ' ';
                } else {
                    keyword[i] = vOffset < value.length ? value[vOffset] : (byte) ' ';
                }
                vOffset++;
            }
            keyword[i] = (byte) (value != null && vOffset < value.length ? '#' : ' ');
            // now save all.
            myBuffer.put(keyword, start, length - start);
        } else if (length - i > 4 && keyword[i] == ':' && keyword[i + 1] == ' ' && keyword[length - 2] == ' ') {
            // : x $
            if (value != null) {
                myBuffer.put(keyword, i, value.length > 0 ? 1 : 2); // ': ' or ':'
                if (value.length > 250) {
                    myBuffer.put(value, 0, 250);
                } else {
                    myBuffer.put(value);
                }
                myBuffer.put(keyword, length - 2, 2); // ' $';
            } else {
                myBuffer.put((byte) '$');
            }
        } else if (keyword[i] == '$' || (keyword[i] == ':' && keyword[i + 1] == '$')) {
            // $ or :$
            if (value != null) {
                myBuffer.put((byte) ':');
                myBuffer.put((byte) ' ');
                if (value.length > 250 - keywordName.length()) {
                    myBuffer.put(value, 0, 250 - keywordName.length());
                } else {
                    myBuffer.put(value);
                }
                if (value.length > 0) {
                    myBuffer.put((byte) ' ');
                }
                myBuffer.put((byte) '$');
            } else {
                myBuffer.put((byte) '$');
            }
        } else {
            // something wrong. write all, but not last $
            myBuffer.put(keyword, i, length - i - 1);
            return length - 1;
        }
        return length;

    }

    private void ensureBufferSize(int len) {
        len += 2048;
        if (myBuffer == null) {
            myBuffer = ByteBuffer.allocate(len);
            return;
        }
        // there is a 'remaining' in buffer. it should be more or equals then len.
        if (myBuffer.remaining() < len) {
            ByteBuffer newBuffer = ByteBuffer.allocate(myBuffer.position() + len);
            myBuffer.flip();
            newBuffer.put(myBuffer);
            myBuffer = newBuffer;
        }
    }

}
