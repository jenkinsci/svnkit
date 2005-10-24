/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.io.diff;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * The <b>SVNRAFileData</b> class represents a random access data storage 
 * wrapper for files.
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNRAFileData implements ISVNRAData {

    private RandomAccessFile myFile;
    private File myRawFile;
    private boolean myIsReadonly;
    
    /**
     * Creates a new <b>SVNRAFileData</b> representation.
     * 
     * @param file     a file resource
     * @param readonly if <span class="javakeyword">true</span> then 
     *                 read-only file access is allowed, if <span class="javakeyword">false</span> - 
     *                 full access is allowed
     */
    public SVNRAFileData(File file, boolean readonly) {
        myRawFile = file;
        myIsReadonly = readonly;
    }
    
    public InputStream readAll() throws SVNException {
        return SVNFileUtil.openFileForReading(myRawFile);
    }

    public InputStream read(final long offset, final long length) throws SVNException {
        byte[] resultingArray = new byte[(int) length];
        int read = 0;
        try {
            getRAFile().seek(offset);
            read = getRAFile().read(resultingArray);
        } catch (IOException e) {
            SVNErrorManager.error(e.getMessage());
        } 
        for (int i = read; i < length; i++) {
            resultingArray[i] = resultingArray[i - read];
        }
        return new LocalInputStream(resultingArray);
    }

    public void append(InputStream source, long length) throws SVNException {
        try {
            getRAFile().seek(getRAFile().length());
            if (source instanceof LocalInputStream) {
                byte[] bytes = ((LocalInputStream) source).getBuffer();
                getRAFile().write(bytes, 0, (int) length);
            } else {
                byte[] bytes = new byte[(int) length];
                source.read(bytes, 0, (int) length);
                getRAFile().write(bytes, 0, (int) length);
            }
        } catch (IOException e) {
            SVNErrorManager.error(e.getMessage());
        } 
    }

    public void close() throws IOException {
        if (myFile == null) {
            return;
        }
        myFile.close();
        myFile = null;
    }

    public long length() {
        return myRawFile.length();
    }

    public long lastModified() {
        return myRawFile.lastModified();
    }

    private RandomAccessFile getRAFile() throws IOException {
        if (myFile == null) {
            if (!myRawFile.exists()) {
                myRawFile.getParentFile().mkdirs();
                myRawFile.createNewFile();
            } else if (!myIsReadonly) {
                SVNFileUtil.setReadonly(myRawFile, false);
            }
            myFile = new RandomAccessFile(myRawFile, myIsReadonly ? "r" : "rw");
        }
        return myFile;
    }

    private static class LocalInputStream extends ByteArrayInputStream {

        public LocalInputStream(byte[] buffer) {
            super(buffer);
        }
        
        public byte[] getBuffer() {
            return buf;
        }
        
    }
}
