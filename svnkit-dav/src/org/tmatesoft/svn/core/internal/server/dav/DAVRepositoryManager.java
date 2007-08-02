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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import javax.servlet.ServletConfig;
import java.util.HashMap;
import java.util.Map;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVRepositoryManager {

    private Map myRepositories;
    private String myURIBase = "/svnkit-dav";

    public DAVRepositoryManager(ServletConfig config) throws SVNException {
        myRepositories = new HashMap();
        String repositoryPath = "file://" + config.getInitParameter("SVNPath");
        String repositoryName = SVNPathUtil.tail(repositoryPath);

        FSRepositoryFactory.setup();
        SVNRepository repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(repositoryPath));

        myRepositories.put(repositoryName, repository);
    }

    public DAVResource createDAVResource(String requestURI, String label, boolean useCheckedIn) throws SVNException {
        requestURI = requestURI.substring(myURIBase.length());
        DAVResource resource = new DAVResource(requestURI, label, useCheckedIn);
        SVNRepository repository = (SVNRepository) myRepositories.get(resource.getRepositoryName());
        if (repository == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Invalid URI ''{0}'' ", requestURI));
        }
        resource.setRepository((SVNRepository) myRepositories.get(resource.getRepositoryName()));
        resource.prepare();
        return resource;
    }
}
