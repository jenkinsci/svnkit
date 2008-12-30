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
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import java.net.URI;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.fs.FSCommitter;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.server.dav.DAVServletUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVRequest.DAVElementProperty;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVMergeHandler extends ServletDAVHandler {
    private DAVMergeRequest myDAVRequest;

    protected DAVMergeHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    public void execute() throws SVNException {
        long readLength = readInput(false);
        if (readLength <= 0) {
            getMergeRequest().invalidXMLRoot();
        }

        DAVMergeRequest requestXMLObject = getMergeRequest();  
        DAVElementProperty rootElement = requestXMLObject.getRoot();
        DAVElementProperty sourceElement = rootElement.getChild(DAVElement.SOURCE);
        if (sourceElement == null) {
            throw new DAVException("The DAV:merge element must contain a DAV:source element.", HttpServletResponse.SC_BAD_REQUEST, 0);
        }
        
        DAVElementProperty hrefElement = sourceElement.getChild(DAVElement.HREF);
        if (hrefElement == null) {
            throw new DAVException("The DAV:source element must contain a DAV:href element.", HttpServletResponse.SC_BAD_REQUEST, 0);
        }
        
        String source = hrefElement.getFirstValue(false);
        URI uri = null; 
        try {
            uri = DAVServletUtil.lookUpURI(source, getRequest(), false);
        } catch (DAVException dave) {
            if (dave.getResponseCode() == HttpServletResponse.SC_BAD_REQUEST) {
                throw dave;
            }
            response(dave.getMessage(), DAVServlet.getStatusLine(dave.getResponseCode()), dave.getResponseCode());
        }

        String path = uri.getPath();
        DAVRepositoryManager manager = getRepositoryManager();
        String resourceContext = manager.getResourceContext();
        
        if (!path.startsWith(resourceContext)) {
            throw new DAVException("Destination url starts with a wrong context", HttpServletResponse.SC_BAD_REQUEST, 0);
        }
        
        DAVResource srcResource = getRequestedDAVResource(false, false, path);
        boolean noAutoMerge = rootElement.hasChild(DAVElement.NO_AUTO_MERGE);
        boolean noCheckOut = rootElement.hasChild(DAVElement.NO_CHECKOUT);
        DAVElementProperty propElement = rootElement.getChild(DAVElement.PROP);
        DAVResource resource = getRequestedDAVResource(false, false);
        if (!resource.exists()) {
            setResponseStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        
        setResponseHeader(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE);
        
    }

    protected DAVRequest getDAVRequest() {
        return getMergeRequest();
    }

    private void merge(DAVResource targetResource, DAVResource sourceResource, DAVElementProperty propElement) throws DAVException {
        boolean disableMergeResponse = false;
        if (sourceResource.getType() != DAVResourceType.ACTIVITY) {
            throw new DAVException("MERGE can only be performed using an activity as the source [at this time].", null, 
                    HttpServletResponse.SC_METHOD_NOT_ALLOWED, null, SVNLogType.NETWORK, Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, 
                    DAVElement.SVN_DAV_ERROR_NAMESPACE, SVNErrorCode.INCORRECT_PARAMS.getCode(), null);
        }
        
        Map locks = parseLocks(getMergeRequest().getRootElement(), targetResource.getResourceURI().getPath());
        if (!locks.isEmpty()) {
            sourceResource.setLockTokens(locks.values());
        }
        
        DAVServletUtil.openTxn(sourceResource.getFSFS(), sourceResource.getTxnName());
        FSCommitter committer = getCommitter(sourceResource.getFSFS(), sourceResource.getRoot(), sourceResource.getTxnInfo(), 
                sourceResource.getLockTokens(), sourceResource.getUserName());
        
        StringBuffer buffer = new StringBuffer();
        SVNErrorMessage[] postCommitHookErr = { null };
        
        try {
            committer.commitTxn(true, true, postCommitHookErr, buffer);
        } catch (SVNException svne) {
            if (postCommitHookErr[0] == null) {
                
            }
        }
    }
    
    private DAVMergeRequest getMergeRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVMergeRequest();
        }
        return myDAVRequest;
    }

}
