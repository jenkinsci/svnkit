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

package org.tmatesoft.svn.core.diff;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author TMate Software Ltd.
 */
public interface ISVNRAData {
    
    public InputStream read(long offset, long length) throws IOException;
    
    public void append(InputStream source, long length) throws IOException;
    
    public long length();
    
    public long lastModified();
    
    public void close() throws IOException;

}
