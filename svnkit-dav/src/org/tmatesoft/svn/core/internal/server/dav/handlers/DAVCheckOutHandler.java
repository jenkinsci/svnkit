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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionRoot;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceFactory;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.server.dav.DAVServletUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVWorkingResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVRequest.DAVElementProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVCheckOutHandler extends ServletDAVHandler {

    private DAVCheckOutRequest myDAVRequest;
    private FSFS myFSFS;
    
    public DAVCheckOutHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }

    public void execute() throws SVNException {
        long readLength = readInput(false);
        boolean applyToVSN = false;
        List activities = new LinkedList();
        
        if (readLength > 0) {
            DAVCheckOutRequest davRequest = getCheckOutRequest();
            if (davRequest.isApplyToVersion()) {
                if (getRequestHeader(LABEL_HEADER) != null) {
                    response("DAV:apply-to-version cannot be used in conjunction with a Label header.", 
                            DAVServlet.getStatusLine(HttpServletResponse.SC_CONFLICT), HttpServletResponse.SC_CONFLICT);
                }
                applyToVSN = true;
            }
        
            boolean isUnversioned = davRequest.isUnreserved();
            boolean isForkOk = davRequest.isForkOk();
            boolean createActivity = false;
            Map activitySetElements = davRequest.getActivitySetPropertyElements();
            
            if (activitySetElements != null) {
                if (activitySetElements.containsKey(DAVCheckOutRequest.NEW)) {
                    createActivity = true;
                } else {
                    for (Iterator activitySetIter = activitySetElements.keySet().iterator(); activitySetIter.hasNext();) {
                        DAVElement element = (DAVElement) activitySetIter.next();
                        if (element == DAVElement.HREF) {
                            DAVElementProperty elementProperty = (DAVElementProperty) activitySetElements.get(element);
                            activities.add(elementProperty.getFirstValue());
                        }
                    }
                    
                    if (activities.isEmpty()) {
                        throw new DAVException("Within the DAV:activity-set element, the DAV:new element must be used, or at least one DAV:href must be specified.", 
                                null, HttpServletResponse.SC_BAD_REQUEST, null, SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
                    }
                }
            }
        }
        
        DAVResource resource = getRequestedDAVResource(true, applyToVSN);
        if (!resource.exists()) {
            throw new DAVException(DAVServlet.getStatusLine(HttpServletResponse.SC_NOT_FOUND), null, HttpServletResponse.SC_NOT_FOUND, null, 
                    SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
        }
        
        if (resource.getResourceURI().getType() != DAVResourceType.REGULAR && 
                resource.getResourceURI().getType() != DAVResourceType.VERSION) {
            response("Cannot checkout this type of resource.", DAVServlet.getStatusLine(HttpServletResponse.SC_CONFLICT), 
                    HttpServletResponse.SC_CONFLICT);
        }
        
        if (!resource.isVersioned()) {
            response("Cannot checkout unversioned resource.", DAVServlet.getStatusLine(HttpServletResponse.SC_CONFLICT), 
                    HttpServletResponse.SC_CONFLICT);
        }
        
        if (resource.isWorking()) {
            response("The resource is already checked out to the workspace.", DAVServlet.getStatusLine(HttpServletResponse.SC_CONFLICT), 
                    HttpServletResponse.SC_CONFLICT);
        }
        

        
        FSRepository repos = (FSRepository) resource.getRepository();
        myFSFS = repos.getFSFS();
 

    }

    protected DAVRequest getDAVRequest() {
        return new DAVCheckOutRequest();
    }

    private void checkOut(DAVResource resource, boolean isAutoCheckOut, boolean isUnreserved, boolean isForkOK, boolean isCreateActivity, 
            List activities) throws SVNException {
        DAVResourceType resourceType = resource.getResourceURI().getType();

        if (isAutoCheckOut) {
            if (resourceType == DAVResourceType.VERSION && resource.isBaseLined()) {
                return;
            }
            
            if (resourceType != DAVResourceType.REGULAR) {
                throw new DAVException("auto-checkout attempted on non-regular version-controlled resource.", null, 
                        HttpServletResponse.SC_METHOD_NOT_ALLOWED, null, SVNLogType.NETWORK, Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, 
                        DAVElement.SVN_DAV_ERROR_NAMESPACE, SVNErrorCode.UNSUPPORTED_FEATURE.getCode(), null);
            }
            
            if (resource.isBaseLined()) {
                new DAVException("auto-checkout attempted on baseline collection, which is not supported.", null, 
                        HttpServletResponse.SC_METHOD_NOT_ALLOWED, null, SVNLogType.NETWORK, Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, 
                        DAVElement.SVN_DAV_ERROR_NAMESPACE, SVNErrorCode.UNSUPPORTED_FEATURE.getCode(), null);
            }
         
            String sharedActivity = DAVServlet.getSharedActivity();
            String sharedTxnName = null;
            FSTransactionInfo sharedTxnInfo = null;
            if (sharedActivity == null) {
                sharedActivity = SVNUUIDGenerator.formatUUID(SVNUUIDGenerator.generateUUID());
                sharedTxnInfo = createActivity(resource, myFSFS);
                sharedTxnName = sharedTxnInfo.getTxnId();
                storeActivity(resource, sharedTxnInfo);
                DAVServlet.setSharedActivity(sharedActivity);
            }
            
            if (sharedTxnName == null) {
                sharedTxnName = DAVServletUtil.getTxn(resource.getActivitiesDB(), sharedActivity);
                if (sharedTxnName == null) {
                    throw new DAVException("Cannot look up a txn_name by activity", null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, 
                            SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
                }
            }
            
            resource = createWorkingResource(resource, sharedActivity, sharedTxnName);
            resource.setIsAutoCkeckedOut(true);
            FSTransactionInfo txnInfo = DAVServletUtil.openTxn(myFSFS, resource.getTxnName());
            FSTransactionRoot txnRoot = null;
            try {
                txnRoot = myFSFS.createTransactionRoot(txnInfo);
            } catch (SVNException svne) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Could not open a (transaction) root in the repository", null);
            }
            
            resource.setTxnInfo(txnInfo);
            resource.setTxnRoot(txnRoot);
            return;
        }
        
        if (resourceType != DAVResourceType.VERSION) {
            
        }
    }
    
    private DAVWorkingResource createWorkingResource(DAVResource baseResource, String activityID, String txnName) throws DAVException {
        StringBuffer pathBuffer = new StringBuffer();
        if (baseResource.isBaseLined()) {
            pathBuffer.append('/');
            pathBuffer.append(DAVResourceURI.SPECIAL_URI);
            pathBuffer.append("/wbl/");
            pathBuffer.append(activityID);
            pathBuffer.append('/');
            pathBuffer.append(baseResource.getRevision());
        } else {
            pathBuffer.append('/');
            pathBuffer.append(DAVResourceURI.SPECIAL_URI);
            pathBuffer.append("/wrk/");
            pathBuffer.append(activityID);
            pathBuffer.append(baseResource.getResourceURI().getPath());
        }
        
        String uriPath = SVNEncodingUtil.uriEncode(pathBuffer.toString());
        DAVResourceURI uri = new DAVResourceURI(baseResource.getResourceURI().getContext(), uriPath, baseResource.getResourceURI().getPath(), 
                baseResource.getRevision(), baseResource.getResourceURI().getKind(), DAVResourceType.WORKING, 
                activityID, true, true, baseResource.isBaseLined(), true);
        return new DAVWorkingResource(baseResource, uri, txnName);
    }

    private DAVCheckOutRequest getCheckOutRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVCheckOutRequest();
        }
        return myDAVRequest;
    }

}
