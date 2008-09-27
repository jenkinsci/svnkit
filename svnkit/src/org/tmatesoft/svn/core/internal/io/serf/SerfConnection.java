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
import org.tmatesoft.svn.core.internal.io.dav.DAVConnection;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepository;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPStatus;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnection;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnectionFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.xml.sax.helpers.DefaultHandler;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class SerfConnection extends DAVConnection {

    private IHTTPConnection[] myHttpConnections = new IHTTPConnection[4];
    private int myCurrentConnection;
    private int myNumberOfActiveConnections;
    
    public SerfConnection(IHTTPConnectionFactory connectionFactory, SVNRepository repository) {
        super(connectionFactory, repository);
    }
    
    public void open(DAVRepository repository) throws SVNException {
        if (myHttpConnections[0] == null) {
            myHttpConnections[0] = myConnectionFactory.createHTTPConnection(repository);
            myNumberOfActiveConnections = 1;
            exchangeCapabilities();
        }
    }

    public void close() {
        for (int i = 0; i < myHttpConnections.length; i++) {
            if (myHttpConnections[i] != null) {
                myHttpConnections[i].close();
                myHttpConnections[i] = null;
            }
        }
        myNumberOfActiveConnections = 0;
        myLocks = null;
        myKeepLocks = false;
    }

    public void clearAuthenticationCache() {
        for (int i = 0; i < myHttpConnections.length; i++) {
            if (myHttpConnections[i] != null) {
                myHttpConnections[i].clearAuthenticationCache();
            }
        }        
    }

    public HTTPStatus doReport(String path, StringBuffer requestBody, DefaultHandler handler, boolean spool) throws SVNException {
        for (int i = myNumberOfActiveConnections; i < myHttpConnections.length; i++) {
            myHttpConnections[i] = myConnectionFactory.createHTTPConnection(getRepository());
            myNumberOfActiveConnections++;
        }
        myCurrentConnection = 1;
        
        IHTTPConnection httpConnection = getConnection();
        httpConnection.setSpoolResponse(spool || isReportResponseSpooled());
        try {
            HTTPHeader header = new HTTPHeader();
            return httpConnection.request("REPORT", path, header, requestBody, -1, 0, null, handler);
        } finally {
            httpConnection.setSpoolResponse(false);
        }
    }

    protected IHTTPConnection getCurrentConnection() {
        return myHttpConnections[myCurrentConnection];
    }
    
    protected IHTTPConnection getConnection() {
        return myHttpConnections[0];
    }
}
