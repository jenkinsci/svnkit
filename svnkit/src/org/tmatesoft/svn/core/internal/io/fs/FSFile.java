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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.0
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
    private MessageDigest myDigest;
    
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

    public long size() {
        return myFile.length();
    }
    
    public void resetDigest() {
        if (myDigest == null) {
            try {
                myDigest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
            }
        }
        myDigest.reset();
    }
    
    public String digest() {
        String digest =  SVNFileUtil.toHexDigest(myDigest);
        myDigest = null;
        return digest;
    }
    
    public int readInt() throws SVNException {
        String line = readLine(80);
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", myFile);
            SVNErrorManager.error(err);
        }
        try {
            return Integer.parseInt(line);
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", myFile);
            SVNErrorManager.error(err);
        }
        return -1;
    }
    
    public String readLine(int limit) throws SVNException {
        allocateReadBuffer(limit);
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
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can''t read length line from file {0}: {1}", new Object[]{getFile(), e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        }
        return null;
    }

    public String readLine(StringBuffer buffer) throws SVNException {
        if (buffer == null) {
            buffer = new StringBuffer();
        }
        boolean endOfLineMet = false;
        try {
            while (!endOfLineMet) {
                allocateReadBuffer(160);
                while(myReadLineBuffer.hasRemaining()) {
                    int b = read();
                    if (b < 0) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Can''t read length line from file {0}", getFile());
                        SVNErrorManager.error(err);
                    } else if (b == '\n') {
                        endOfLineMet = true;
                        break;
                    }
                    myReadLineBuffer.put((byte) (b & 0XFF));
                }
                myReadLineBuffer.flip();
                buffer.append(myDecoder.decode(myReadLineBuffer).toString());
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can''t read length line from file {0}: {1}", new Object[]{getFile(), e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        }
        return buffer.toString();
    }

    public Map readProperties(boolean allowEOF) throws SVNException {
        Map map = new HashMap();
        String line = null;
        try {
            while(true) {
                try {
                    line = readLine(160); // K length or END, there may be EOF.
                } catch (SVNException e) {
                    if (allowEOF && e.getErrorMessage().getErrorCode() == SVNErrorCode.STREAM_UNEXPECTED_EOF) {
                        break;
                    }
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err, e);
                }
                if (line == null || "".equals(line)) {
                    break;
                } else if (!allowEOF && "END".equals(line)) {
                    break;
                }
                char kind = line.charAt(0);
                int length = -1;
                if ((kind != 'K' && kind != 'D') || line.length() < 3 || line.charAt(1) != ' ' || line.length() < 3) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                } 
                try {
                    length = Integer.parseInt(line.substring(2));
                } catch (NumberFormatException nfe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                if (length < 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                allocateReadBuffer(length + 1);
                read(myReadLineBuffer);
                myReadLineBuffer.flip();
                myReadLineBuffer.limit(myReadLineBuffer.limit() - 1);
                String key = myDecoder.decode(myReadLineBuffer).toString();
                if (kind == 'D') {
                    map.put(key, null);
                    continue;
                }
                line = readLine(160);
                if (line == null || line.length() < 3 || line.charAt(0) != 'V' || line.charAt(1) != ' ') {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                try {
                    length = Integer.parseInt(line.substring(2));
                } catch (NumberFormatException nfe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                if (length < 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                allocateReadBuffer(length + 1);
                read(myReadLineBuffer);
                myReadLineBuffer.flip();
                myReadLineBuffer.limit(myReadLineBuffer.limit() - 1);
                String value = myDecoder.decode(myReadLineBuffer).toString();
                map.put(key, value);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
            SVNErrorManager.error(err, e);
        }
        return map;
    }
    
    public Map readHeader() throws SVNException {
        Map map = new HashMap();
        String line;
        while(true) {
            line = readLine(1024);
            if ("".equals(line)) {
                break;
            }
            if (line == null) {
                
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0 || line.length() <= colonIndex + 2) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Found malformed header in revision file");
                SVNErrorManager.error(err);
            } else if (line.charAt(colonIndex + 1) != ' ') {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Found malformed header in revision file");
                SVNErrorManager.error(err);
            }
            String key = line.substring(0, colonIndex);
            String value = line.substring(colonIndex + 2);
            map.put(key, value);
        }
        return map;
    }
    
    public int read() throws IOException {
        if (myChannel == null || myPosition < myBufferPosition || myPosition >= myBufferPosition + myBuffer.limit()) {
            if (fill() <= 0) {
                return -1;
            }
        } else {
            myBuffer.position((int) (myPosition - myBufferPosition));
        }
        int r = (myBuffer.get() & 0xFF);
        if (myDigest != null) {
            myDigest.update((byte) r);
        }
        myPosition++;
        return r;
    }

    public int read(ByteBuffer target) throws IOException {
        int read = 0;
        while(target.hasRemaining()) {
            if (fill() < 0) {
                return read > 0 ? read : -1;
            }
            myBuffer.position((int) (myPosition - myBufferPosition));

            int couldRead = Math.min(myBuffer.remaining(), target.remaining());
            int readFrom = myBuffer.position() + myBuffer.arrayOffset();
            target.put(myBuffer.array(), readFrom, couldRead);
            if (myDigest != null) {
                myDigest.update(myBuffer.array(), readFrom, couldRead);
            }
            myPosition += couldRead;
            read += couldRead;
            myBuffer.position(myBuffer.position() + couldRead);
        }
        return read;
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = 0;
        int toRead = length;
        while(toRead > 0) {
            if (fill() < 0) {
                return read > 0 ? read : -1;
            }
            myBuffer.position((int) (myPosition - myBufferPosition));

            int couldRead = Math.min(myBuffer.remaining(), toRead);
            myBuffer.get(buffer, offset, couldRead);
            if (myDigest != null) {
                myDigest.update(buffer, offset, couldRead);
            }
            toRead -= couldRead;
            offset += couldRead;
            myPosition += couldRead;
            read += couldRead;
        }
        return read;
    }

    public File getFile() {
        return myFile;
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
            myDigest = null;
        }
        
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
        return 0;
    }
    
    private void allocateReadBuffer(int limit) {
        if (limit > myReadLineBuffer.capacity()) {
            myReadLineBuffer = ByteBuffer.allocate(limit*3/2);
        }
        myReadLineBuffer.clear();
        myReadLineBuffer.limit(limit);
    }
    
    private FileChannel getChannel() throws IOException {
        if (myChannel == null) {
            myInputStream = new FileInputStream(myFile);
            myChannel = myInputStream.getChannel();
        }
        return myChannel;
    }
    
    public PathInfo readPathInfoFromReportFile() throws IOException {
        int firstByte = read();
        if (firstByte == -1 || firstByte == '-') {
            return null;
        }
        String path = readStringFromReportFile();
        String linkPath = read() == '+' ? readStringFromReportFile() : null;
        long revision = readRevisionFromReportFile();
        boolean startEmpty = read() == '+' ? true : false;
        String lockToken = read() == '+' ? readStringFromReportFile() : null;
        return new PathInfo(path, linkPath, lockToken, revision, startEmpty);
    }

    private String readStringFromReportFile() throws IOException {
        int length = readNumberFromReportFile();
        if (length == 0) {
            return "";
        }
        byte[] buffer = new byte[length];
        read(buffer, 0, length);
        return new String(buffer, "UTF-8");
    }

    private int readNumberFromReportFile() throws IOException {
        int b;
        ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
        while ((b = read()) != ':') {
            resultStream.write(b);
        }
        return Integer.parseInt(new String(resultStream.toByteArray(), "UTF-8"), 10);
    }

    private long readRevisionFromReportFile() throws IOException {
        if (read() == '+') {
            return readNumberFromReportFile();
        }
        return FSRepository.SVN_INVALID_REVNUM;
    }
    
}
