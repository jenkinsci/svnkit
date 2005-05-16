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

import java.io.IOException;
import java.io.OutputStream;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.Base64;
import org.tmatesoft.svn.util.DebugLog;
import org.xml.sax.SAXException;

/**
 * @author TMate Software Ltd.
 */
public abstract class BasicDAVDeltaHandler extends BasicDAVHandler {

    protected static final DAVElement TX_DELTA = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "txdelta");

    private boolean myIsDeltaProcessing;
    private SVNDiffWindowBuilder myDiffBuilder;
    private StringBuffer myDeltaOutputStream;
    private byte[] myByteBuffer;

    protected void setDeltaProcessing(boolean processing) {
        myIsDeltaProcessing = processing;

        if (!myIsDeltaProcessing) {
            SVNDiffWindow window = null;
            OutputStream os = null;

            myByteBuffer = Base64.base64ToByteArray(myDeltaOutputStream, myByteBuffer);
            if (Base64.lastLength() == 4) {
                try {
                    handleDiffWindowClosed();
                } catch (SVNException e) {
                    DebugLog.error(e);
                }
                return;
            }
            int newOffset = myDiffBuilder.accept(myByteBuffer, 0);
            window = myDiffBuilder.getDiffWindow();

            while(window != null) {
                try {
                    os = handleDiffWindow(window);
                    if (os != null) {
                        os.write(myByteBuffer, newOffset, (int) window.getNewDataLength());
                        os.close();
                    }
                } catch (IOException e) {
                    DebugLog.error(e);
                } catch (SVNException e) {
                    DebugLog.error(e);
                } 
                newOffset = newOffset + (int) window.getNewDataLength();
                if (newOffset < Base64.lastLength()) {
                    myDiffBuilder.reset(1);
                    newOffset = myDiffBuilder.accept(myByteBuffer, newOffset);
                    window = myDiffBuilder.getDiffWindow();
                } else {
                    window = null;
                }
            }
            try {
                handleDiffWindowClosed();
            } catch (SVNException e) {
                DebugLog.error(e);
            }
        } else {
            myDiffBuilder.reset();
            myDeltaOutputStream.delete(0, myDeltaOutputStream.length());
        }
    }
    
    protected void init() {
        myDiffBuilder = SVNDiffWindowBuilder.newInstance();
        myDeltaOutputStream = new StringBuffer();
        super.init();
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        if (myIsDeltaProcessing) {
            int offset = start;
            for(int i = start; i < start + length; i++) {
                if (ch[i] == '\r' || ch[i] == '\n') {
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
        } else {
            super.characters(ch, start, length);
        }
    }

    protected abstract OutputStream handleDiffWindow(SVNDiffWindow window) throws SVNException;
    
    protected abstract void handleDiffWindowClosed() throws SVNException;
}
