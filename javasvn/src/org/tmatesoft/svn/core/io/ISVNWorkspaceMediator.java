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

package org.tmatesoft.svn.core.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author TMate Software Ltd.
 */
public interface ISVNWorkspaceMediator {
    
    public String getWorkspaceProperty(String path, String name) throws SVNException;
    
    public void setWorkspaceProperty(String path, String name, String value) throws SVNException ;
    
    public OutputStream createTemporaryLocation(String path, Object id) throws IOException;
    
    public InputStream getTemporaryLocation(Object id) throws IOException;
    
    public long getLength(Object id) throws IOException;
    
    public void deleteTemporaryLocation(Object id);
    
    public void deleteAdminFiles(String path);
}
