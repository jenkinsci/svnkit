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
package org.tmatesoft.svn.core.internal.io.fs2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * Usage example (uses less memory, more reliable, because allows any amount of changed paths):
 * 
 *         FSRevision revision = new FSRevision(new File("c:/i/jsvn/db/revs/1"));
 *         try {
 *           for(Iterator paths = revision.changedPaths2(); paths.hasNext();) {
 *               FSChangedPath path = (FSChangedPath) paths.next();
 *               System.out.println(path.getKind() + " " + path.getPath());
 *           }
 *         } finally {
 *            revision.iterationCompleted();
 *         }
 *         
 * Or (works faster, easier to use, but limits changed paths data length to int value):
 * 
 *         FSRevision revision = new FSRevision(new File("c:/i/jsvn/db/revs/1"));
 *         for(Iterator paths = revision.changedPaths(); paths.hasNext();) {
 *             FSChangedPath path = (FSChangedPath) paths.next();
 *             System.out.println(path.getKind() + " " + path.getPath());
 *         }
 * 
 * To reset FSRevision to its initial state (no caches, just file reference):
 *        
 *         revision.dispose();
 *         
 * I think you have to extend this class to include rev-props access, 
 * node-reps access (similar to stream-based changed paths one) and delta access.
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRevision {
    
    private File myFile;
    private long myRootOffset;
    private long myChangedPathsOffset;
    private byte[] myChangedPaths;
    private int myChangedPathsLength;
    private long myChangedPathsLength2;
    private InputStream myCurrentStream;

    public FSRevision(File file) {
        myFile = file;
        myRootOffset = -1;
        myChangedPathsOffset = -1;
    }
    
    public Iterator changedPaths() throws SVNException {
        loadOffsets();
        loadChangedPaths();
        // no need to keep opened stream.
        return new FSChangedPathsIterator(new ByteArrayInputStream(myChangedPaths), myChangedPathsLength2);
    }
    
    public Iterator changedPaths2() throws SVNException {
        if (myCurrentStream != null) {
            // or throw exception here - concurrent stream access problem.
            // or implement pool of opened streams, in that case this method
            // and 'completed' one has to accept 'requestor' object to map stream to.
            iterationCompleted();
        }

        loadOffsets();
        InputStream is = null;
        try {
            is = SVNFileUtil.openFileForReading(myFile);
            is.skip(myChangedPathsOffset);
        } catch (SVNException e) {
            SVNFileUtil.closeFile(is);
            throw e;
        } catch (IOException e) {
            SVNFileUtil.closeFile(is);
            e.printStackTrace();
        }
        myCurrentStream = is;
        return new FSChangedPathsIterator(is, myChangedPathsLength2);
    }
    
    public void iterationCompleted() {
        SVNFileUtil.closeFile(myCurrentStream);
        myCurrentStream = null;
    }
    
    public void dispose() {
        iterationCompleted();
        myRootOffset = -1;
        myChangedPaths = null;
        myChangedPathsOffset = -1;
        myChangedPathsLength = 0;
        myChangedPathsLength2 = 0;
    }
    
    private void loadChangedPaths() throws SVNException {
        if (myChangedPaths != null) {
            return;
        }
        if (myChangedPathsLength == 0) {
            myChangedPaths = new byte[0];
            return;
        }
        InputStream is = null;
        try {
            is = SVNFileUtil.openFileForReading(myFile);
            is.skip(myChangedPathsOffset);
            myChangedPaths = new byte[myChangedPathsLength];
            is.read(myChangedPaths);
        } catch (IOException e) {
            myChangedPaths = null;
            e.printStackTrace();
        } finally {
            SVNFileUtil.closeFile(is);
        }
    }
    
    private void loadOffsets() throws SVNException {
        if (myRootOffset >= 0) {
            return;
        }
        InputStream is = null;
        byte[] buffer = new byte[64];
        try {
            is = SVNFileUtil.openFileForReading(myFile);
            long readStart = Math.max(0, myFile.length() - 64); 
            is.skip(readStart);
            int read = is.read(buffer);
            String line = null; 
            long changedPathsEnd = 0;
            if (buffer[read - 1] == '\n') {
                for(int i = read - 2; i >= 0; i--) {
                    if (buffer[i] == '\n') {
                        changedPathsEnd = readStart + i;
                        line = new String(buffer, i + 1, read - 2 - i);
                        break;
                    }
                }
            }
            if (line != null) {
                myRootOffset = Long.parseLong(line.substring(0, line.indexOf(' ')));
                myChangedPathsOffset = Long.parseLong(line.substring(line.indexOf(' ') + 1));
                myChangedPathsLength2 = changedPathsEnd - myChangedPathsOffset;
                myChangedPathsLength = (int) (changedPathsEnd - myChangedPathsOffset);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            SVNFileUtil.closeFile(is);
        }
    }
    
    private static class FSChangedPathsIterator implements Iterator {
        
        private InputStream myStream;
        private long myLength;
        private FSChangedPath myCurrentPath;
        private ByteArrayOutputStream mySegmentBuffer;

        public FSChangedPathsIterator(InputStream is, long length) {
            myStream = is;
            myLength = length;
            mySegmentBuffer = new ByteArrayOutputStream();
            try {
                readChangedPath();
            } catch (IOException e) {
                myCurrentPath = null;
                SVNFileUtil.closeFile(myStream);
                e.printStackTrace();
            }
        }

        public boolean hasNext() {
            if (myCurrentPath == null) {
                SVNFileUtil.closeFile(myStream);
            }
            return myCurrentPath != null;
        }

        public Object next() {
            Object next = myCurrentPath;
            try {
                readChangedPath();
            } catch (IOException e) {
                myCurrentPath = null;
            }
            if (myCurrentPath == null) {
                SVNFileUtil.closeFile(myStream);
            }
            return next;
        }

        private void readChangedPath() throws IOException {
            if (myLength <= 0) {
                myCurrentPath = null;
                return;
            }
            String id = null;
            String kind = null;
            String path = null;            
            boolean hasTextMods = false;
            boolean hasPropMods = false;
            String cpFromPath = null;
            long cpFromRev = -1;

            int r;
            int lineNumber = 0;
            int segmentNumber = 0;
            while(lineNumber < 2 && myLength > 0 && (r = myStream.read()) >= 0) {
                myLength--;
                if (r == '\n' || r == ' ') {
                    if (mySegmentBuffer.size() > 0) {
                        String value = new String(mySegmentBuffer.toByteArray(), "UTF-8"); 
                        switch (segmentNumber) {
                            case 0:
                                id = value; 
                                break;
                            case 1:
                                kind = value;
                                break;
                            case 2:
                                hasTextMods = Boolean.valueOf(value).booleanValue();
                                break;
                            case 3:
                                hasPropMods = Boolean.valueOf(value).booleanValue();
                                break;
                            case 4:
                                path = value;
                                break;
                            case 5:
                                cpFromRev = Long.parseLong(value);
                                break;
                            case 6:
                                cpFromPath = value;
                                break;
                        }
                    }
                    segmentNumber++;
                    if (r == '\n') {
                        lineNumber++;
                    }
                    mySegmentBuffer.reset();
                } else {
                    mySegmentBuffer.write(r);
                }
            }
            mySegmentBuffer.reset();
            myCurrentPath = new FSChangedPath(id, kind, hasPropMods, hasTextMods, path, cpFromPath, cpFromRev);
        }

        public void remove() {
        }
    }
}
