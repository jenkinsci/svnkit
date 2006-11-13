/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

import org.tmatesoft.svn.core.internal.wc.SVNSubstitutor;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNTranslatorInputStream extends InputStream {
    
    private InputStream mySource;
    private ByteBuffer myTranslatedBuffer;
    private SVNSubstitutor mySubstitutor;
    private byte[] mySourceBuffer;


    public SVNTranslatorInputStream(InputStream source, byte[] eols, boolean repair, Map keywords, boolean expand) {
        mySource = source;
        mySubstitutor = new SVNSubstitutor(eols, repair, keywords, expand);
        myTranslatedBuffer = ByteBuffer.allocate(2048);
        mySourceBuffer = new byte[2048];
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
        int available = myTranslatedBuffer.position();
        while(available < len) {
            int read = mySource.read(mySourceBuffer, 0, mySourceBuffer.length);
            if (read <= 0) {
                myTranslatedBuffer = mySubstitutor.translateChunk(null, myTranslatedBuffer);
                break;
            }
            myTranslatedBuffer = mySubstitutor.translateChunk(ByteBuffer.wrap(mySourceBuffer, 0, read), myTranslatedBuffer);
            available = myTranslatedBuffer.position();
        }
        myTranslatedBuffer.flip();
        len = Math.min(myTranslatedBuffer.remaining(), len);
        myTranslatedBuffer.get(b, off, len);
        myTranslatedBuffer.compact();
        return len;
    }

    public void close() throws IOException {
        mySource.close();
    }
}
