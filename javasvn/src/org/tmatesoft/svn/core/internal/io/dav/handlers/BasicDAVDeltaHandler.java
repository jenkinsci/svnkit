/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav.handlers;

import java.io.ByteArrayInputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;
import org.xml.sax.SAXException;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public abstract class BasicDAVDeltaHandler extends BasicDAVHandler {

    protected static final DAVElement TX_DELTA = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "txdelta");

    private boolean myIsDeltaProcessing;
    private SVNDiffWindowBuilder myDiffBuilder;
    private StringBuffer myDeltaOutputStream;
    private SequenceIntputStream myPreviousStream;

    protected void setDeltaProcessing(boolean processing) throws SVNException {
        myIsDeltaProcessing = processing;
        myPreviousStream = null;

        if (!myIsDeltaProcessing) {
            getEditor().textDeltaEnd(getCurrentPath());
        } else {
            myDiffBuilder.reset();
            myDeltaOutputStream.delete(0, myDeltaOutputStream.length());
            myPreviousStream = null;
        }
    }
    
    protected void init() {
        myDiffBuilder = SVNDiffWindowBuilder.newInstance();
        myDeltaOutputStream = new StringBuffer();
        super.init();
    }
    
    private int eolCount;

    public void characters(char[] ch, int start, int length) throws SAXException {
        if (myIsDeltaProcessing) {
            int offset = start;
            
            for(int i = start; i < start + length; i++) {
                if (ch[i] == '\r' || ch[i] == '\n') {
                    eolCount++;
                    myDeltaOutputStream.append(ch, offset, i - offset);
                    offset = i + 1;
                    if (i + 1 < (start + length) && ch[i + 1] == '\n') {
                        offset++;
                        i++;
                    }
                }
            }
            if (offset < start + length) {
                myDeltaOutputStream.append(ch, offset, start + length - offset);
            }
            // decode (those dividable by 4) 
            int stored = myDeltaOutputStream.length();
            if (stored < 4) {
                return;
            }
            int segmentsCount = stored/4;
            int remains = stored - (segmentsCount*4);
            
            StringBuffer toDecode = new StringBuffer();
            toDecode.append(myDeltaOutputStream);
            toDecode.delete(myDeltaOutputStream.length() - remains, myDeltaOutputStream.length());
            
            byte[] decoded = SVNBase64.base64ToByteArray(toDecode, null);
            myPreviousStream = new SequenceIntputStream(decoded, myPreviousStream);
            try {
                myDiffBuilder.accept(myPreviousStream, getEditor(), getCurrentPath());
                // delete saved bytes.
            } catch (SVNException e) {
                throw new SAXException(e);
            }
            myDeltaOutputStream.delete(0, toDecode.length());
        } else {
            super.characters(ch, start, length);
        }
    }

    protected abstract String getCurrentPath();

    protected abstract ISVNEditor getEditor();
    
    private static class SequenceIntputStream extends ByteArrayInputStream {
        SequenceIntputStream(byte[] bytes, SequenceIntputStream previous) {
            super(bytes);
            if (previous != null && previous.available() > 0) {
                byte[] realBytes = new byte[bytes.length + previous.available()];
                System.arraycopy(previous.buf, previous.pos, realBytes, 0, previous.available());
                System.arraycopy(bytes, 0, realBytes, previous.available(), bytes.length);
                buf = realBytes;
                count = buf.length;
                pos = 0;
            }
        }
    }
}
