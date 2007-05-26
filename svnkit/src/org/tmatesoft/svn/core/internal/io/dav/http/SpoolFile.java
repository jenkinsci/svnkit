/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SpoolFile {

    private static final long LIMIT = 1024*1024*512; // 1/2GB.
    
    private File myDirectory;
    private LinkedList myFiles;

    public SpoolFile(File directory) {
        myDirectory = directory;
        myFiles = new LinkedList();
    }
    
    public OutputStream openForWriting() {
        return new SpoolOutputStream();
    }
    
    public InputStream openForReading() {
        return new SpoolInputStream();
    }
    
    public void delete() throws SVNException {
        for (Iterator files = myFiles.iterator(); files.hasNext();) {
            File file = (File) files.next();
            SVNFileUtil.deleteFile(file);
        }
    }
    
    private class SpoolInputStream extends InputStream {
        
        private File myCurrentFile;
        private long myCurrentSize;
        private InputStream myCurrentInput;

        public int read() throws IOException {
            byte[] buffer = new byte[1];
            int read = read(buffer);
            if (read <= 0) {
                return -1;
            }
            return read;
        }

        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        public int read(byte[] b, int off, int len) throws IOException {
            if (myCurrentFile == null) {
                if (myFiles.isEmpty()) {
                    return -1;
                }
                openNextFile();
            }
            int toRead = (int) Math.min(len, myCurrentSize);
            toRead = myCurrentInput.read(b, off, toRead);
            myCurrentSize -= toRead;
            if (myCurrentSize == 0) {
                closeCurrentFile();
            }
            return toRead;
        }

        private void openNextFile() throws FileNotFoundException {
            myCurrentFile = (File) myFiles.removeFirst();
            myCurrentSize = myCurrentFile.length();
            myCurrentInput = new BufferedInputStream(new FileInputStream(myCurrentFile));
        }

        public long skip(long n) throws IOException {
            if (myCurrentFile == null) {
                if (myFiles.isEmpty()) {
                    return -1;
                }
                openNextFile();
            }
            long toSkip = Math.min(myCurrentSize, n);
            toSkip = myCurrentInput.skip(toSkip);
            myCurrentSize -= toSkip;
            if (myCurrentSize == 0) {
                closeCurrentFile();
            }
            return toSkip;
        }

        private void closeCurrentFile() throws IOException {
            try {
                myCurrentInput.close();
            } finally {
                try {
                    SVNFileUtil.deleteFile(myCurrentFile);
                } catch (SVNException e) {
                    //
                }
                myCurrentFile = null;
            }
        }

        public void close() throws IOException {
            if (myCurrentFile != null) {
                closeCurrentFile();
            }
        }
    }
    
    
    private class SpoolOutputStream extends OutputStream {
        
        private OutputStream myCurrentOutput;
        private long myCurrentSize;
        
        public void write(int b) throws IOException {
            write(new byte[] {(byte) (b & 0xFF)});
        }

        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            if (myCurrentOutput == null) {
                // open first file.
                File file = createNextFile();
                myFiles.add(file);
                myCurrentOutput = new BufferedOutputStream(new FileOutputStream(file));
            }
            myCurrentOutput.write(b, off, len);
            myCurrentSize += len;
            if (myCurrentSize >= LIMIT) {
                close();
            }
        }

        public void close() throws IOException {
            if (myCurrentOutput != null) {
                try {
                    myCurrentOutput.close();
                } finally {
                    myCurrentOutput = null;
                }
            }
            myCurrentSize = 0;
        }

        public void flush() throws IOException {
            if (myCurrentOutput != null) {
                myCurrentOutput.flush();
            }
        }
        
        private File createNextFile() throws IOException {
            File file = File.createTempFile(".svnkit.", ".spool", myDirectory);
            file.createNewFile();
            return file;
        }
        
    }

}
