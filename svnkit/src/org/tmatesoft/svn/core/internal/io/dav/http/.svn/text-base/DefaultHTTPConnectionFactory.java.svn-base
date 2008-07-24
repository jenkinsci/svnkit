/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.http;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class DefaultHTTPConnectionFactory implements IHTTPConnectionFactory {
    
    private File mySpoolDirectory;
    private String myHTTPCharset;
    private boolean myIsSpoolAll;

    public DefaultHTTPConnectionFactory(File spoolDirectory, boolean spoolAll, String httpCharset) {
        mySpoolDirectory = spoolDirectory;
        myIsSpoolAll = spoolAll;
        myHTTPCharset = httpCharset;
    }

    public IHTTPConnection createHTTPConnection(SVNRepository repository) throws SVNException {
        String charset = myHTTPCharset != null ? myHTTPCharset : System.getProperty("svnkit.http.encoding", "US-ASCII");
        File spoolLocation = mySpoolDirectory;
        if (mySpoolDirectory != null && !mySpoolDirectory.isDirectory()) {
            spoolLocation = null;
        }
        return new HTTPConnection(repository, charset, spoolLocation, myIsSpoolAll);
    }

}
