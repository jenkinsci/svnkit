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

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.IOException;
import java.io.InputStream;


/**
 * @author Alexander Kitaev
 */
class XMLInputStream extends InputStream {
    
	private InputStream mySource;
	private boolean myIsEscaping;
    private int myColonCount;
    private boolean myIsClosed;

    public XMLInputStream(InputStream source) {
    	mySource = source;
    }
    
    public boolean isClosed() { 
        return myIsClosed;
    }
    
    public int read(byte[] b, int off, int len) throws IOException {
        int read = mySource.read(b, off, len);
        for(int i = 0; i < read; i++) {
            char ch = (char) b[off + i];
            if (ch < 0x20 && ch != '\r' &&
                    ch != '\n' && ch != '\t') {
                b[off + i] = ' ';
                continue;
            }
            if (myIsEscaping) {
                if (ch == ':') {
                    myColonCount++;
                    if (myColonCount > 1) {
                        b[off + i] = '_'; 
                    }
                } else if (Character.isWhitespace(ch) || ch == '>') {
                    myIsEscaping = false;
                }
            } else if (!myIsEscaping && ch == '<') {
                myIsEscaping = true;
                myColonCount = 0;
            } 
        }
        myIsClosed = read < 0;
        return read;
    }

    public int read() throws IOException {
        int read = mySource.read();        
        if (myIsEscaping) {
            if (read == ':') {
                myColonCount++;
                if (myColonCount > 1) {
                    read = '_'; 
                }
            } else if (Character.isWhitespace((char) read) || read == '>') {
                myIsEscaping = false;
            }
        } else if (!myIsEscaping && read == '<') {
            myIsEscaping = true;
            myColonCount = 0;
        } 
        return read;
    }
    
    

}
