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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * This class uses:
 * 
 *  shared read buffer.
 *  shared read line buffer.
 *  shared transfer buffer.
 *  
 * Only one instance of this class may be used per thread at time.
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSFile {
    
    private File myFile;
    private FileChannel myChannel;
    private FileInputStream myInputStream;
    private long myPosition;
    
    private long myBufferPosition;
    private ByteBuffer myBuffer;
    private ByteBuffer myReadLineBuffer;
    private CharsetDecoder myDecoder;
    
    public FSFile(File file) {
        myFile = file;
        myPosition = 0;
        myBufferPosition = 0;
        myBuffer = ByteBuffer.allocate(4096);
        myReadLineBuffer = ByteBuffer.allocate(4096);
        myDecoder = Charset.forName("UTF-8").newDecoder();
    }
    
    public void seek(long position) {
        myPosition = position;
    }

    public long position() {
        return myPosition;
    }
    
    public String readLine(int limit) throws SVNException {
        myReadLineBuffer.clear();
        myReadLineBuffer.limit(limit);
        try {
            while(myReadLineBuffer.hasRemaining()) {
                int b = read();
                if (b < 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Can''t read length line from file {0}", getFile());
                    SVNErrorManager.error(err);
                } else if (b == '\n') {
                    break;
                }
                myReadLineBuffer.put((byte) (b & 0XFF));
            }
            myReadLineBuffer.flip();
            return myDecoder.decode(myReadLineBuffer).toString();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can''t read length line from file {0}", getFile());
            SVNErrorManager.error(err, e);
        }
        return null;
    }
    
    public int read() throws IOException {
        if (myChannel == null || !myBuffer.hasRemaining()) {
            if (fill() <= 0) {
                return -1;
            }
        }
        int r = myBuffer.get();
        myPosition++;
        return r;
    }

    public int read(ByteBuffer target) throws IOException {
        int read = 0;
        while(target.hasRemaining()) {
            if (fill() < 0) {
                // return what was read so far.
                return read > 0 ? read : -1;
            }
            myBuffer.position((int) (myPosition - myBufferPosition));
            while(myBuffer.hasRemaining() && target.hasRemaining()) {
                target.put(myBuffer.get());
                myPosition++;
                read++;
            }
        }
        return read;
    }
    
    private int fill() throws IOException {
        if (myChannel == null || myPosition < myBufferPosition || myPosition >= myBufferPosition + myBuffer.limit()) {
            myBufferPosition = myPosition;
            getChannel().position(myBufferPosition);
            myBuffer.clear();
            int read = getChannel().read(myBuffer);
            myBuffer.position(0);
            myBuffer.limit(read >= 0 ? read : 0);
            return read;
        } 
        // position is within the buffer.
        return 0;
    }

    public void close() {
        if (myChannel != null) {
            try {
                myChannel.close();
            } catch (IOException e) {}
            SVNFileUtil.closeFile(myInputStream);
            myChannel = null;
            myInputStream = null;
            myPosition = 0;
        }
        
    }
    
    private FileChannel getChannel() throws IOException {
        if (myChannel == null) {
            myInputStream = new FileInputStream(myFile);
            myChannel = myInputStream.getChannel();
        }
        return myChannel;
    }

    public File getFile() {
        return myFile;
    }
}
