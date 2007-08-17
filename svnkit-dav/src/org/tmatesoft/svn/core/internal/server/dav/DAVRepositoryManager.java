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

import javax.servlet.ServletConfig;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVRepositoryManager {

    private static final String PATH_PARAMETER = "SVNPath";
    private static final String PARENT_PATH_PARAMETER = "SVNParentPath";

    private SVNRepository myRepository;
    private String myRepositoryParentPath;

    public DAVRepositoryManager(ServletConfig config) throws SVNException {
        String repositoryPath = config.getInitParameter(PATH_PARAMETER);
        String repositoryParentPath = config.getInitParameter(PARENT_PATH_PARAMETER);
        if (repositoryPath != null && repositoryParentPath == null) {
            FSRepositoryFactory.setup();
            String repositoryURL = (repositoryPath.startsWith("/") ? "file://" : "file:///") + repositoryPath;
            myRepository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(repositoryURL));
            myRepositoryParentPath = null;
        } else if (repositoryParentPath != null && repositoryPath == null) {
            myRepositoryParentPath = repositoryParentPath;
            myRepository = null;
        } else {
            //repositoryPath == null <=> repositoryParentPath == null.
            if (repositoryPath == null) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "Neither SVNPath nor SVNParentPath directive were specified."));
            } else {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "Only one of SVNPath and SVNParentPath directives should be specified."));
            }
        }
    }

    public DAVResource createDAVResource(String requestContext, String requestURI, boolean isSVNClient, String versionName, String clientOptions,
                                         String baseChecksum, String resultChecksum, String label, boolean useCheckedIn) throws SVNException {
        if (myRepositoryParentPath != null) {
            if (requestURI == null || requestURI.length() == 0 || "/".equals(requestURI)) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED));
                //TODO: client tried to access repository parent path, result status code should be FORBIDDEN.
            }
            String repositoryName = DAVPathUtil.head(requestURI);
            requestContext = DAVPathUtil.append(requestContext, repositoryName);
            requestURI = DAVPathUtil.removeHead(requestURI, true);
            String repositoryURL = getRepositoryURL(repositoryName);

            FSRepositoryFactory.setup();
            myRepository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(repositoryURL));
        }
        DAVResourceURI resourceURI = new DAVResourceURI(requestContext, requestURI, label, useCheckedIn);
        return new DAVResource(myRepository, resourceURI, isSVNClient, versionName, clientOptions, baseChecksum, resultChecksum);
    }

    private String getRepositoryURL(String repositoryName) {
        StringBuffer urlBuffer = new StringBuffer();
        urlBuffer.append("file://");
        urlBuffer.append(myRepositoryParentPath.startsWith("/") ? "" : "/");
        urlBuffer.append(DAVPathUtil.addTrailingSlash(myRepositoryParentPath));
        urlBuffer.append(repositoryName);
        return urlBuffer.toString();
    }
}
