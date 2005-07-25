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
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNRAFileData implements ISVNRAData {

    private RandomAccessFile myFile;

    private File myRawFile;

    private byte[] myBuffer;

    private boolean myIsReadonly;

    public SVNRAFileData(File file, boolean readonly) {
        myRawFile = file;
        myIsReadonly = readonly;
    }

    public InputStream read(final long offset, final long length) throws IOException {
        // read to tmp file when lenght is more then 64K.
        if (length >= 64*1024) {
            File tmpFile = SVNFileUtil.createUniqueFile(myRawFile.getParentFile(), myRawFile.getName(), ".tmp");
            try {
                final File fileToDelete = tmpFile;
                FileOutputStream fos = new FileOutputStream(tmpFile);
                FileChannel outChannel = fos.getChannel();
                long count = 0;
                while(count != length) {
                    long tranferred = getRAFile().getChannel().transferTo(offset, length, outChannel);
                    count += tranferred;
                    if (tranferred <= 0) {
                        break;
                    }
                }
                outChannel.close();
                try {
                    InputStream source = SVNFileUtil.openFileForReading(tmpFile);
                    tmpFile = null;
                    return new FilterInputStream(source) {
                        public void close() throws IOException {
                            super.close();
                            if (fileToDelete != null) {
                                fileToDelete.delete();
                            }
                        }
                    };
                } catch (SVNException e) {
                    throw new IOException(e.getMessage());
                }
            } finally {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
            }
        } else {
            byte[] resultingArray = new byte[(int) length];
            getRAFile().seek(offset);
            int read = getRAFile().read(resultingArray);
            for (int i = read; i < length; i++) {
                resultingArray[i] = resultingArray[i - read];
            }
            return new ByteArrayInputStream(resultingArray);
        }
    }

    public void append(InputStream source, long length) throws IOException {
        int lLength = (int) length;
        if (myBuffer == null || myBuffer.length < length) {
            myBuffer = new byte[lLength];
        }

        int read;
        read = source.read(myBuffer, 0, lLength);
        getRAFile().seek(getRAFile().length());
        getRAFile().write(myBuffer, 0, read);
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
            }
            if (!myIsReadonly) {
                try {
                    SVNFileUtil.setReadonly(myRawFile, false);
                } catch (SVNException e) {
                    //
                }
            }
            myFile = new RandomAccessFile(myRawFile, myIsReadonly ? "r" : "rw");
        }
        return myFile;
    }
}
