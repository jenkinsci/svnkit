/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaCombiner;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class FSOutputStream extends OutputStream implements ISVNDeltaConsumer {

    public static final int WRITE_BUFFER_SIZE = 512000;
    public static final int SVN_DELTA_WINDOW_SIZE = 102400;

    private boolean isHeaderWritten;
    private CountingStream myTargetFile;
    private long myDeltaStart;
    private long myRepSize;
    private long myRepOffset;
    private InputStream mySourceStream;
    private SVNDeltaGenerator myDeltaGenerator;
    private FSRevisionNode myRevNode;
    private MessageDigest myDigest;
    private FSTransactionRoot myTxnRoot;
    private long mySourceOffset;
    private ByteBuffer myTextBuffer;
    private boolean myIsClosed;
    private boolean myIsCompress;

    private FSOutputStream(FSRevisionNode revNode, CountingStream file, InputStream source, long deltaStart, long repSize, long repOffset, FSTransactionRoot txnRoot, boolean compress) throws SVNException {
        myTxnRoot = txnRoot;
        myTargetFile = file;
        mySourceStream = source;
        myDeltaStart = deltaStart;
        myRepSize = repSize;
        myRepOffset = repOffset;
        isHeaderWritten = false;
        myRevNode = revNode;
        mySourceOffset = 0;
        myIsClosed = false;

        myDeltaGenerator = new SVNDeltaGenerator(SVN_DELTA_WINDOW_SIZE);
        myTextBuffer = ByteBuffer.allocate(WRITE_BUFFER_SIZE);

        try {
            myDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "MD5 implementation not found: {0}", nsae.getLocalizedMessage());
            SVNErrorManager.error(err, nsae);
        }
        myIsCompress = compress;
    }

    private void reset(FSRevisionNode revNode, CountingStream file, InputStream source, long deltaStart, long repSize, long repOffset, FSTransactionRoot txnRoot) {
        myTxnRoot = txnRoot;
        myTargetFile = file;
        mySourceStream = source;
        myDeltaStart = deltaStart;
        myRepSize = repSize;
        myRepOffset = repOffset;
        isHeaderWritten = false;
        myRevNode = revNode;
        mySourceOffset = 0;
        myIsClosed = false;
        myDigest.reset();
        myTextBuffer.clear();
    }

    public static OutputStream createStream(FSRevisionNode revNode, FSTransactionRoot txnRoot, OutputStream dstStream, boolean compress) throws SVNException {
        if (revNode.getType() != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_FILE, "Attempted to set textual contents of a *non*-file node");
            SVNErrorManager.error(err);
        }

        if (!revNode.getId().isTxn()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_NOT_MUTABLE, "Attempted to set textual contents of an immutable node");
            SVNErrorManager.error(err);
        }

        OutputStream targetOS = null;
        InputStream sourceStream = null;
        long offset = -1;
        long deltaStart = -1;

        try {
            File targetFile = txnRoot.getTransactionRevFile();
            offset = targetFile.length();
            targetOS = SVNFileUtil.openFileForWriting(targetFile, true);
            CountingStream revWriter = new CountingStream(targetOS, offset);

            FSRepresentation baseRep = revNode.chooseDeltaBase(txnRoot.getOwner());
            sourceStream = FSInputStream.createDeltaStream(new SVNDeltaCombiner(), baseRep, txnRoot.getOwner());
            String header;

            if (baseRep != null) {
                header = FSRepresentation.REP_DELTA + " " + baseRep.getRevision() + " " + baseRep.getOffset() + " " + baseRep.getSize() + "\n";
            } else {
                header = FSRepresentation.REP_DELTA + "\n";
            }

            revWriter.write(header.getBytes("UTF-8"));
            deltaStart = revWriter.getPosition();

            if ((dstStream != null) && (dstStream instanceof FSOutputStream)) {
                FSOutputStream fsOS = (FSOutputStream) dstStream;
                fsOS.reset(revNode, revWriter, sourceStream, deltaStart, 0, offset, txnRoot);
                return dstStream;
            }

            return new FSOutputStream(revNode, revWriter, sourceStream, deltaStart, 0, offset, txnRoot, compress);

        } catch (IOException ioe) {
            SVNFileUtil.closeFile(targetOS);
            SVNFileUtil.closeFile(sourceStream);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        } catch (SVNException svne) {
            SVNFileUtil.closeFile(targetOS);
            SVNFileUtil.closeFile(sourceStream);
            throw svne;
        }
        return null;
    }

    public void write(int b) throws IOException {
        write(new byte[] {
            (byte) (b & 0xFF)
        }, 0, 1);
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        myDigest.update(b, off, len);
        myRepSize += len;
        int toWrite = 0;
        while (len > 0) {
            toWrite = Math.min(len, myTextBuffer.remaining());
            myTextBuffer.put(b, off, toWrite);
            if (myTextBuffer.remaining() == 0) {
                try {
                    ByteArrayInputStream target = new ByteArrayInputStream(myTextBuffer.array(), 0, myTextBuffer.capacity());
                    myDeltaGenerator.sendDelta(null, mySourceStream, mySourceOffset, target, this, false);
                } catch (SVNException svne) {
                    throw new IOException(svne.getMessage());
                }
                myTextBuffer.clear();
            }
            off += toWrite;
            len -= toWrite;
        }
    }

    public void close() throws IOException {
        if (myIsClosed) {
            return;
        }
        myIsClosed = true;

        try {
            ByteArrayInputStream target = new ByteArrayInputStream(myTextBuffer.array(), 0, myTextBuffer.position());
            myDeltaGenerator.sendDelta(null, mySourceStream, mySourceOffset, target, this, false);

            FSRepresentation rep = new FSRepresentation();
            rep.setOffset(myRepOffset);

            long offset = myTargetFile.getPosition();

            rep.setSize(offset - myDeltaStart);
            rep.setExpandedSize(myRepSize);
            rep.setTxnId(myRevNode.getId().getTxnID());
            rep.setRevision(FSRepository.SVN_INVALID_REVNUM);

            rep.setHexDigest(SVNFileUtil.toHexDigest(myDigest));

            myTargetFile.write("ENDREP\n".getBytes("UTF-8"));
            myRevNode.setTextRepresentation(rep);

            myTxnRoot.getOwner().putTxnRevisionNode(myRevNode.getId(), myRevNode);
        } catch (SVNException svne) {
            throw new IOException(svne.getMessage());
        } finally {
            closeStreams();
        }
    }

    public void closeStreams() {
        SVNFileUtil.closeFile(myTargetFile);
        SVNFileUtil.closeFile(mySourceStream);
    }

    public FSRevisionNode getRevisionNode() {
        return myRevNode;
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        mySourceOffset += diffWindow.getInstructionsLength();
        try {
            diffWindow.writeTo(myTargetFile, !isHeaderWritten, myIsCompress);
            isHeaderWritten = true;
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String path) throws SVNException {
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
    }
}
