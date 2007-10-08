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
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVHandlerFactory;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVRepositoryManager {

    private static final String FILE_PROTOCOL_LINE = "file://";
    private static final String DESTINATION_HEADER = "Destination";

    private DAVConfig myDAVConfig;

    private String myResourceRepositoryRoot;
    private String myResourceContext;
    private String myResourcePathInfo;

    public DAVRepositoryManager(DAVConfig config, HttpServletRequest request) throws SVNException {
        myDAVConfig = config;

        myResourceRepositoryRoot = getRepositoryRoot(request.getPathInfo());
        myResourceContext = getResourceContext(request.getContextPath(), request.getPathInfo());
        myResourcePathInfo = getResourcePathInfo(request.getPathInfo());

        if (config.isUsingPBA()) {
            String path = null;
            if (!DAVHandlerFactory.METHOD_MERGE.equals(request.getMethod())) {
                DAVResourceURI tmp = new DAVResourceURI(null, myResourcePathInfo, null, false);
                DAVPathUtil.standardize(tmp.getPath());
            }

            boolean checkDestinationPath = false;
            String destinationPath = null;
            if (DAVHandlerFactory.METHOD_MOVE.equals(request.getMethod()) || DAVHandlerFactory.METHOD_COPY.equals(request.getMethod())) {
                String destinationURL = request.getHeader(DESTINATION_HEADER);
                if (destinationURL == null) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Destination path missing"));
                }
                destinationPath = DAVPathUtil.standardize(getRepositoryRelativePath(SVNURL.parseURIEncoded(destinationURL)));
                checkDestinationPath = true;
            }

            String repository = getResourceRepositoryName(request.getPathInfo());
            String user = request.getRemoteUser();
            int access = getRequestedAccess(request.getMethod());
            checkAccess(repository, path, checkDestinationPath, destinationPath, user, access);
        }
    }

    private int getRequestedAccess(String method) {
        int access = SVNPathBasedAccess.SVN_ACCESS_NONE;
        if (DAVHandlerFactory.METHOD_COPY.equals(method) ||
                DAVHandlerFactory.METHOD_MOVE.equals(method) ||
                DAVHandlerFactory.METHOD_DELETE.equals(method)) {
            access |= SVNPathBasedAccess.SVN_ACCESS_RECURSIVE;
        } else if (DAVHandlerFactory.METHOD_OPTIONS.equals(method) ||
                DAVHandlerFactory.METHOD_PROPFIND.equals(method) ||
                DAVHandlerFactory.METHOD_GET.equals(method) ||
                DAVHandlerFactory.METHOD_REPORT.equals(method)) {
            access |= SVNPathBasedAccess.SVN_ACCESS_READ;
        } else if (DAVHandlerFactory.METHOD_MKCOL.equals(method) ||
                DAVHandlerFactory.METHOD_PUT.equals(method) ||
                DAVHandlerFactory.METHOD_PROPPATCH.equals(method) ||
                DAVHandlerFactory.METHOD_CHECKOUT.equals(method) ||
                DAVHandlerFactory.METHOD_MERGE.equals(method) ||
                DAVHandlerFactory.METHOD_MKACTIVITY.equals(method) ||
                DAVHandlerFactory.METHOD_LOCK.equals(method) ||
                DAVHandlerFactory.METHOD_UNLOCK.equals(method)) {
            access |= SVNPathBasedAccess.SVN_ACCESS_WRITE;
        } else {
            access |= SVNPathBasedAccess.SVN_ACCESS_RECURSIVE | SVNPathBasedAccess.SVN_ACCESS_WRITE;
        }
        return access;
    }

    private void checkAccess(String repository, String path, boolean checkDestinationPath, String destinationPath, String user, int access) throws SVNException {
        if (getDAVConfig().getSVNAccess() == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_INVALID_CONFIG_VALUE, "An error occured while loading configuration file."));
        }
        if (!getDAVConfig().isAnonymousAllowed() && user == null) {
            SVNErrorManager.authenticationFailed("Anonymous user is not allowed for resource", null);
        }

        if (path != null || (path == null && (access & SVNPathBasedAccess.SVN_ACCESS_WRITE) != SVNPathBasedAccess.SVN_ACCESS_NONE)) {
            if (!getDAVConfig().getSVNAccess().checkAccess(repository, path, user, access)) {
                if (user == null) {
                    SVNErrorManager.authenticationFailed("Forbidden for anonymous", null);
                } else {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.NO_AUTH_FILE_PATH));
                }
            }
        }

        if (checkDestinationPath) {
            if (path != null) {
                if (!getDAVConfig().getSVNAccess().checkAccess(repository, destinationPath, user, access)) {
                    if (user == null) {
                        SVNErrorManager.authenticationFailed("Forbidden for anonymous", null);
                    } else {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.NO_AUTH_FILE_PATH));
                    }
                }
            }
        }

    }

    private DAVConfig getDAVConfig() {
        return myDAVConfig;
    }

    public String getResourceRepositoryRoot() {
        return myResourceRepositoryRoot;
    }

    public String getResourceContext() {
        return myResourceContext;
    }

    public String getResourcePathInfo() {
        return myResourcePathInfo;
    }

    public SVNURL convertHttpToFile(SVNURL url) throws SVNException {
        String uri = DAVPathUtil.addLeadingSlash(url.getPath());
        if (!uri.startsWith(getResourceContext())) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Invalid URL ''{0}'' requested", url.toString()));
        }
        return SVNURL.parseURIEncoded(getResourceRepositoryRoot() + getRepositoryRelativePath(url));
    }

    public String getRepositoryRelativePath(SVNURL url) throws SVNException {
        String uri = getURI(url);
        DAVResourceURI resourceURI = new DAVResourceURI(null, getResourcePathInfo(uri), null, false);
        return resourceURI.getPath();
    }

    public String getURI(SVNURL url) throws SVNException {
        String uri = DAVPathUtil.addLeadingSlash(url.getPath());
        if (uri.startsWith(getResourceContext())) {
            uri = uri.substring(getResourceContext().length());
        } else {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Invalid URL ''{0}'' requested", url.toString()));
        }
        return uri;
    }

    public DAVResource createDAVResource(String requestURI, boolean isSVNClient, String deltaBase, long version, String clientOptions,
                                         String baseChecksum, String resultChecksum, String label, boolean useCheckedIn) throws SVNException {
        SVNRepository resourceRepository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(getResourceRepositoryRoot()));

        DAVResourceURI resourceURI = new DAVResourceURI(getResourceContext(), getResourcePathInfo(), label, useCheckedIn);
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

    private String getResourcePathInfo(String requestURI) throws SVNException {
        if (getDAVConfig().isUsingRepositoryPathDirective()) {
            return requestURI;
        }

        if (requestURI == null || requestURI.length() == 0 || "/".equals(requestURI)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED));
            //TODO: client tried to access repository parent path, result status code should be FORBIDDEN.
        }
        return DAVPathUtil.removeHead(requestURI, true);
    }

    private String getResourceRepositoryName(String requestURI) {
        if (getDAVConfig().isUsingRepositoryPathDirective()) {
            return "";
        } else return DAVPathUtil.head(requestURI);
    }

    private String getResourceContext(String requestContext, String requestURI) {
        if (getDAVConfig().isUsingRepositoryPathDirective()) {
            return requestContext;
        }
        return DAVPathUtil.append(requestContext, DAVPathUtil.head(requestURI));
    }
}
