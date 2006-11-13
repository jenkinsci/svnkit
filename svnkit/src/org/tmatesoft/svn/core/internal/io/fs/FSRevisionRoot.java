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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class FSRevisionRoot extends FSRoot {
    private long myRevision;
    private long myRootOffset;
    private long myChangesOffset;

    public FSRevisionRoot(FSFS owner, long revision) {
        super(owner);
        myRevision = revision;
        myRootOffset = -1;
        myChangesOffset = -1;
    }

    public long getRevision() {
        return myRevision;
    }

    public Map getChangedPaths() throws SVNException {
        FSFile file = getOwner().getRevisionFile(getRevision());
        loadOffsets(file);
        try {
            file.seek(myChangesOffset);
            return fetchAllChanges(file, true);
        } finally {
            file.close();
        }
    }

    public FSCopyInheritance getCopyInheritance(FSParentPath child) throws SVNException{
        return null;
    }

    public FSRevisionNode getRootRevisionNode() throws SVNException {
        if (myRootRevisionNode == null) {
            FSFile file = getOwner().getRevisionFile(getRevision());
            try {
                loadOffsets(file);
                file.seek(myRootOffset);
                Map headers = file.readHeader();
                myRootRevisionNode = FSRevisionNode.fromMap(headers);
            } finally {
                file.close();
            }
        }
        return myRootRevisionNode;
    }

    private void loadOffsets(FSFile file) throws SVNException {
        if (myRootOffset >= 0) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocate(64);
        file.seek(file.size() - 64);
        try {
            file.read(buffer);
        } catch (IOException e) {
        }
        buffer.flip();
        if (buffer.get(buffer.limit() - 1) != '\n') {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Revision file lacks trailing newline");
            SVNErrorManager.error(err);
        }
        int spaceIndex = -1;
        int eolIndex = -1;
        for(int i = buffer.limit() - 2; i >=0; i--) {
            byte b = buffer.get(i);
            if (b == ' ' && spaceIndex < 0) {
                spaceIndex = i;
            } else if (b == '\n' && eolIndex < 0) {
                eolIndex = i;
                break;
            }
        }
        if (eolIndex < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file longer than 64 characters");
            SVNErrorManager.error(err);
        }
        if (spaceIndex < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file missing space");
            SVNErrorManager.error(err);
        }
        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
        try {
            buffer.limit(buffer.limit() - 1);
            buffer.position(spaceIndex + 1);
            String line = decoder.decode(buffer).toString();
            myChangesOffset = Long.parseLong(line);

            buffer.limit(spaceIndex);
            buffer.position(eolIndex + 1);
            line = decoder.decode(buffer).toString();
            myRootOffset = Long.parseLong(line); 
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file missing changes and root offsets");
            SVNErrorManager.error(err, nfe);
        } catch (CharacterCodingException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Final line in revision file missing changes and root offsets");
            SVNErrorManager.error(err, e);
        }
    }

}
