/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.serf;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.xml.sax.helpers.DefaultHandler;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SerfGETRequest implements ISerfRequest {

    private String myPath;
    private HTTPHeader myHeaders;
    private DefaultHandler myHandler;
    
    public SerfGETRequest(String path, HTTPHeader headers, DefaultHandler handler) {
        myPath = path;
        myHeaders = headers;
        myHandler = handler;
    }
    
    public void doRequest(SerfConnection connection) throws SVNException {
        //connection.doGet(path, deltaBaseVersionURL, os)
    }

}
