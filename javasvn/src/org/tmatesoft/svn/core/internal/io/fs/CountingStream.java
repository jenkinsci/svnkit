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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.IOException;
import java.io.OutputStream;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class CountingStream extends OutputStream {

    private long myPosition;
    private OutputStream myWriter;
    
    public CountingStream(OutputStream writer, long offset) {
        super();
        myPosition = offset >= 0 ? offset : 0;
        myWriter = writer;
    }

    public void write(int b) throws IOException {
        myWriter.write(b);
        myPosition++;
    }
    
    public long getPosition(){
        return myPosition;
    }

    public OutputStream getRealStream(){
        return myWriter;
    }
}
