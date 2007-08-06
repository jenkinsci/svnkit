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
package org.tmatesoft.svn.core.internal.server.dav;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import javax.servlet.ServletConfig;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVRepositoryManager {

    private SVNRepository myRepository;

    public DAVRepositoryManager(ServletConfig config) throws SVNException {
        String repositoryPath = "file://" + config.getInitParameter("SVNPath");
        FSRepositoryFactory.setup();
        myRepository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(repositoryPath));
    }

    public DAVResource createDAVResource(String requestContext, String requestURI, String label, boolean useCheckedIn) throws SVNException {
        requestURI = requestURI.substring(requestContext.length());
        return new DAVResource(myRepository, requestContext, requestURI, label, useCheckedIn);
    }
}
