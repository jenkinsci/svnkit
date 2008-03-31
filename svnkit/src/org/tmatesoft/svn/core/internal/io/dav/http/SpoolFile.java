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
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SpoolFile {

    private static final long LIMIT = 1024*1024*512; // 512MB
    
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
            if (read != 1) {
                return -1;
            }
            return buffer[0];
        }

        public int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        public int read(byte[] b, int off, int len) throws IOException {
            int read = 0;
            while(len - read > 0) {
                if (myCurrentFile == null) {
                    if (myFiles.isEmpty()) {
                        SVNDebugLog.getDefaultLog().info("FAILED TO READ SPOOLED RESPONSE FULLY (no more files): " + (read == 0 ? -1 : read));
                        return read == 0 ? -1 : read;
                    }
                    openNextFile();
                }
                int toRead = (int) Math.min(len - read, myCurrentSize);
                int wasRead = myCurrentInput.read(b, off + read, toRead);
                if (wasRead < 0) {
                    SVNDebugLog.getDefaultLog().info("FAILED TO READ SPOOLED RESPONSE FULLY (cannot read more from the current file): " + (read == 0 ? -1 : read));
                    return read == 0 ? -1 : read;
                }
                read += wasRead;
                myCurrentSize -= wasRead;
                if (myCurrentSize == 0) {
                    SVNDebugLog.getDefaultLog().info("SPOOLED RESPONSE FULLY READ");
                    closeCurrentFile();
                }
            }
            return read;
        }

        private void openNextFile() throws FileNotFoundException {
            myCurrentFile = (File) myFiles.removeFirst();
            SVNDebugLog.getDefaultLog().info("READING SPOOLED FILE: " + myCurrentFile);
            myCurrentSize = myCurrentFile.length();
            SVNDebugLog.getDefaultLog().info("ABOUT TO READ: " + myCurrentSize);
            myCurrentInput = new BufferedInputStream(new FileInputStream(myCurrentFile));
        }

        public long skip(long n) throws IOException {
            int skipped = 0;
            while(n - skipped > 0) {
                if (myCurrentFile == null) {
                    if (myFiles.isEmpty()) {
                        return skipped == 0 ? -1 : skipped;
                    }
                    openNextFile();
                }
                long toSkip = Math.min(n - skipped, myCurrentSize);
                long wasSkipped = myCurrentInput.skip(toSkip);
                if (wasSkipped < 0) {
                    return skipped == 0 ? -1 : skipped;
                }
                skipped += wasSkipped;
                myCurrentSize -= wasSkipped;
                if (myCurrentSize == 0) {
                    closeCurrentFile();
                }
            }
            return skipped;
        }

        private void closeCurrentFile() throws IOException {
            try {
                myCurrentInput.close();
            } finally {
                try {
                    SVNFileUtil.deleteFile(myCurrentFile);
                } catch (SVNException e) {

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
                SVNDebugLog.getDefaultLog().info("SPOOLING RESPONSE TO FILE: " + file);
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
                    SVNDebugLog.getDefaultLog().info("SPOOLED: " + myCurrentSize);
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
            File file = File.createTempFile("svnkit.", ".spool", myDirectory);
            file.createNewFile();
            return file;
        }
        
    }

}
