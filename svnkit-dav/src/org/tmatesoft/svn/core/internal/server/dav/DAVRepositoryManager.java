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

import javax.servlet.http.HttpServletRequest;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVRepositoryManager {

    private static final String FILE_PROTOCOL_LINE = "file://";

    private String myContext;
    private DAVConfig myDAVConfig;

    public DAVRepositoryManager(DAVConfig config, HttpServletRequest request) throws SVNException {
        myContext = request.getContextPath();

        if (config == null){
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "Failed to load config file."));            
        }
        myDAVConfig = config;
    }

    public SVNURL convertHttpToFile(SVNURL url) throws SVNException {
        String uri = DAVPathUtil.addLeadingSlash(url.getPath());
        if (uri.startsWith(myContext)) {
            uri = uri.substring(myContext.length());
        } else {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Invalid URL ''{0}'' requested", url.toString()));
        }
        String repositoryRootPath = DAVPathUtil.dropTraillingSlash(getRepositoryRoot(uri));
        return SVNURL.parseURIEncoded(repositoryRootPath + getRepositoryRelativePath(url));
    }

    private String getContext() {
        return myContext;
    }

    private DAVConfig getDAVConfig() {
        return myDAVConfig;
    }

    public String getRepositoryRelativePath(SVNURL url) throws SVNException {
        String uri = getURI(url);
        DAVResourceURI resourceURI = new DAVResourceURI(null, getPathInfo(uri), null, false);
        return resourceURI.getPath();
    }

    public String getURI(SVNURL url) throws SVNException {
        String uri = DAVPathUtil.addLeadingSlash(url.getPath());
        if (uri.startsWith(getContext())) {
            uri = uri.substring(getContext().length());
        } else {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Invalid URL ''{0}'' requested", url.toString()));
        }
        return uri;
    }

    public DAVResource createDAVResource(String requestURI, boolean isSVNClient, String deltaBase, long version, String clientOptions,
                                         String baseChecksum, String resultChecksum, String label, boolean useCheckedIn) throws SVNException {
        String resourceRepositoryRoot = getRepositoryRoot(requestURI);
        String resourceContext = getResourceContext(requestURI);
        String resourceRepositoryRelativePath = getPathInfo(requestURI);

        SVNRepository resourceRepository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(resourceRepositoryRoot));

        DAVResourceURI resourceURI = new DAVResourceURI(resourceContext, resourceRepositoryRelativePath, label, useCheckedIn);
        return new DAVResource(resourceRepository, resourceURI, isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum);
    }

    private String getRepositoryRoot(String requestURI) {
        StringBuffer repositoryURL = new StringBuffer();
        repositoryURL.append(FILE_PROTOCOL_LINE);
        if (getDAVConfig().isUsingRepositoryPathDirective()) {
            repositoryURL.append(getDAVConfig().getRepositoryPath().startsWith("/") ? "" : "/");
            repositoryURL.append(getDAVConfig().getRepositoryPath());
        } else {
            repositoryURL.append(getDAVConfig().getRepositoryParentPath().startsWith("/") ? "" : "/");
            repositoryURL.append(DAVPathUtil.addTrailingSlash(getDAVConfig().getRepositoryParentPath()));
            repositoryURL.append(DAVPathUtil.head(requestURI));
        }
        return repositoryURL.toString();
    }

    private String getPathInfo(String requestURI) throws SVNException {
        if (getDAVConfig().isUsingRepositoryPathDirective()) {
            return requestURI;
        }
        
        if (requestURI == null || requestURI.length() == 0 || "/".equals(requestURI)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED));
            //TODO: client tried to access repository parent path, result status code should be FORBIDDEN.
        }
        return DAVPathUtil.removeHead(requestURI, true);
    }

    private String getResourceContext(String requestURI) {
        if (getDAVConfig().isUsingRepositoryPathDirective()) {
            return myContext;
        }
        return DAVPathUtil.append(myContext, DAVPathUtil.head(requestURI));
    }
}
