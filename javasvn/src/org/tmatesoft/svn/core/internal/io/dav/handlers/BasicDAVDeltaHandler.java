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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.util.SVNDebugLog;
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
    private File myTmpFile;
    private OutputStream myTmpOutputStream;

    protected void setDeltaProcessing(boolean processing) throws SVNException {
        myIsDeltaProcessing = processing;

        if (!myIsDeltaProcessing) {
            SVNFileUtil.closeFile(myTmpOutputStream);
            if (myTmpFile.length() == 4) {
                try {
                    handleDiffWindowClosed();
                } catch (SVNException e) {
                    SVNDebugLog.log(e);
                }
                myTmpFile.delete();
                return;
            }
            InputStream is = SVNFileUtil.openFileForReading(myTmpFile);
            SVNDiffWindow window = null;
            OutputStream os = null;

            try {
                myDiffBuilder.accept(is);
                window = myDiffBuilder.getDiffWindow();
    
                while(window != null) {
                    try {
                        os = handleDiffWindow(window);
                        if (os != null) {
                            for(int i = 0; i < window.getNewDataLength(); i++) {
                                int r = is.read();
                                if (r >= 0) {
                                    os.write(r);
                                }
                            }
                        }
                    } catch (IOException e) {
                        throw new SVNException(e);
                    } finally {
                        
                        try {
                            if (os != null) {
                                os.close();
                            }
                        } catch (IOException e) {
                            
                        }
                    }
                    if (is.available() > 0) {
                        myDiffBuilder.reset(1);
                        myDiffBuilder.accept(is);
                        window = myDiffBuilder.getDiffWindow();
                    } else {
                        window = null;
                    }
                }
            } catch (IOException e) {
                SVNErrorManager.error(e.getMessage());
            }
            handleDiffWindowClosed();
            myTmpFile.delete();
        } else {
            myDiffBuilder.reset();
            myDeltaOutputStream.delete(0, myDeltaOutputStream.length());
            try {
                myTmpFile = File.createTempFile("javasvn.", ".tmp");
            } catch (IOException e) {
                SVNErrorManager.error(e.getMessage());
            }
            myTmpOutputStream = SVNFileUtil.openFileForWriting(myTmpFile);
        }
    }
    
    protected void init() {
        myDiffBuilder = SVNDiffWindowBuilder.newInstance();
        myDeltaOutputStream = new StringBuffer();
        super.init();
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        if (myIsDeltaProcessing) {
            // save directly to tmp file(?).
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
            // decode (those dividable by 4) 
            int stored = myDeltaOutputStream.length();
            if (stored < 4) {
                return;
            }
            int segmentsCount = stored/4;
            int remains = stored - (segmentsCount*4);
            
            // copy segments to new buffer.
            StringBuffer toDecode = new StringBuffer();
            toDecode.append(myDeltaOutputStream);
            toDecode.delete(myDeltaOutputStream.length() - remains, myDeltaOutputStream.length());
            
            byte[] decoded = SVNBase64.base64ToByteArray(toDecode, null);
            try {
                myTmpOutputStream.write(decoded);
                // delete saved bytes.
                myDeltaOutputStream.delete(0, toDecode.length());
            } catch (IOException e) {
                throw new SAXException(e);
            }
        } else {
            super.characters(ch, start, length);
        }
    }

    protected abstract OutputStream handleDiffWindow(SVNDiffWindow window) throws SVNException;
    
    protected abstract void handleDiffWindowClosed() throws SVNException;
}
