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
package org.tmatesoft.svn.core.internal.io.svn;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNRollbackInputStream extends InputStream {
    
    private int myLength;
    private byte[] myBuffer;
    
    private InputStream mySource;
    private int myPosition;
    
    private List myMarks;
    
    public SVNRollbackInputStream(InputStream source, int maxBufferSize) {
        mySource = source;
        myBuffer = new byte[maxBufferSize];
        myLength = 0;
        myPosition = 0;
        myMarks = new ArrayList();
    }
    
    public int read() throws IOException {
        // 1. from buffer.
        if (myPosition < myLength) {
            myPosition++;
            return myBuffer[myPosition - 1];
        }
        // 2. directly from source.
        int r = mySource.read();
        // 3. buffer what was read.
        if (r >= 0) {
            buffer((byte) (r & 0xff));
        }
        return r;
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int read = 0;
        int reallyRead = 0;
        if (myPosition < myLength) {
            // from buffer as long as possible.
            read = Math.min(len, myLength - myPosition);
            reallyRead = read;
            System.arraycopy(myBuffer, myPosition, b, off, read);
            myPosition += read;
        }
        if (read < len) {
            // from the source.
            reallyRead += mySource.read(b, off + read, len - read);
            buffer(b, off + read, reallyRead - read);
        }
        return reallyRead;
    }

    public synchronized void mark(int readlimit) {
        myMarks.add(new Mark(myPosition));
    }

    public synchronized void reset() throws IOException {
        if (myMarks.isEmpty()) {
            throw new IOException("No valid mark found");
        }
        Mark lastMark = (Mark) myMarks.remove(myMarks.size() - 1);
        myPosition = lastMark.position;
    }

    public boolean markSupported() {
        return true;
    }

    // if there is an active mark =>
    // these two methods should fill buffer,
    // update length and position 
    // and validate existing marks.
    // then check if there are valid marks...
    private void buffer(byte b) {
        if (myMarks.isEmpty()) {
            myPosition = myLength = 0;
            return;
        }
        if (myLength < myBuffer.length) {
            myBuffer[myLength] = b;
            myLength++;
        } else {
            shiftBufferLeft(1);
            myBuffer[myLength - 1] = b;
            adjustMarks(1);
        }
        myPosition = myLength;
    }

    private void buffer(byte b[], int off, int len) {
        if (myMarks.isEmpty()) {
            myPosition = myLength = 0;
            return;
        }
        if (myLength + len < myBuffer.length) {
            System.arraycopy(b, off, myBuffer, myLength, len);
            myLength += len;
        } else {
            if (len < myBuffer.length) {
                shiftBufferLeft(len);
                System.arraycopy(b, off, myBuffer, myBuffer.length - len, len);
                adjustMarks(len);
            } else {
                myLength = 0;
                myMarks.clear();
            }
        }
        myPosition = myLength;
    }

    private void shiftBufferLeft(int shift) {
        byte[] newBuffer = new byte[myBuffer.length];
        System.arraycopy(myBuffer, shift, newBuffer, 0, myBuffer.length - shift);
        myBuffer = newBuffer;
    }
    
    private void adjustMarks(int shift) {
        for (Iterator marks = myMarks.iterator(); marks.hasNext();) {
            Mark mark = (Mark) marks.next();
            mark.position -= shift;
            if (mark.position < 0) {
                marks.remove();
            }
        }
    }

    private static class Mark {
        public Mark(int pos) {
            position = pos;
        }
        int position;
    }
}
