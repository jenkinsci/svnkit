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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionRoot;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.server.dav.DAVServletUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVURIInfo;
import org.tmatesoft.svn.core.internal.server.dav.DAVWorkingResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.handlers.DAVRequest.DAVElementProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.wc.SVNRevision;
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
        boolean isUnreserved = false;
        boolean createActivity = false;
        
        List activities = null;
        if (readLength > 0) {
            DAVCheckOutRequest davRequest = getCheckOutRequest();
            if (davRequest.isApplyToVersion()) {
                if (getRequestHeader(LABEL_HEADER) != null) {
                    response("DAV:apply-to-version cannot be used in conjunction with a Label header.", 
                            DAVServlet.getStatusLine(HttpServletResponse.SC_CONFLICT), HttpServletResponse.SC_CONFLICT);
                }
                applyToVSN = true;
            }
        
            isUnreserved = davRequest.isUnreserved();
            Map activitySetElements = davRequest.getActivitySetPropertyElements();
            
            if (activitySetElements != null) {
                if (activitySetElements.containsKey(DAVCheckOutRequest.NEW)) {
                    createActivity = true;
                } else {
                    activities = new LinkedList();
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

        DAVWorkingResource workingResource = null;
        try {
            workingResource = checkOut(resource, false, isUnreserved, createActivity, activities);
        } catch (DAVException dave) {
            throw new DAVException("Could not CHECKOUT resource {0}.", new Object[] { SVNEncodingUtil.xmlEncodeCDATA(resource.getResourceURI().getURI()) }, 
                    HttpServletResponse.SC_CONFLICT, null, SVNLogType.NETWORK, Level.FINE, dave, null, null, 0, null);
        }
        
 
        setResponseHeader(CACHE_CONTROL_HEADER, CACHE_CONTROL_VALUE);
        
        if (workingResource == null) {
            setResponseHeader(HTTPHeader.CONTENT_LENGTH_HEADER, "0");
            return;
        }
            
        handleDAVCreated(workingResource.getResourceURI().getURI(), "Checked-out resource", false);
    }

    protected DAVRequest getDAVRequest() {
        return new DAVCheckOutRequest();
    }

    private DAVWorkingResource checkOut(DAVResource resource, boolean isAutoCheckOut, boolean isUnreserved, boolean isCreateActivity, 
            List activities) throws DAVException {
        DAVResourceType resourceType = resource.getResourceURI().getType();

        if (isAutoCheckOut) {
            if (resourceType == DAVResourceType.VERSION && resource.isBaseLined()) {
                return null;
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
                try {
                    sharedActivity = SVNUUIDGenerator.formatUUID(SVNUUIDGenerator.generateUUID());
                } catch (SVNException svne) {
                    throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                            "cannot generate UUID for a shared activity", null);
                }
                
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
            resource.setRoot(txnRoot);
            return null;
        }
        
        if (resourceType != DAVResourceType.VERSION) {
            throw new DAVException("CHECKOUT can only be performed on a version resource [at this time].", null, 
                    HttpServletResponse.SC_METHOD_NOT_ALLOWED, null, SVNLogType.NETWORK, Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, 
                    DAVElement.SVN_DAV_ERROR_NAMESPACE, SVNErrorCode.UNSUPPORTED_FEATURE.getCode(), null);
        }
        
        if (isCreateActivity) {
            throw new DAVException("CHECKOUT can not create an activity at this time. Use MKACTIVITY first.", null, 
                    HttpServletResponse.SC_NOT_IMPLEMENTED, null, SVNLogType.NETWORK, Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, 
                    DAVElement.SVN_DAV_ERROR_NAMESPACE, SVNErrorCode.UNSUPPORTED_FEATURE.getCode(), null);
        }
        
        if (isUnreserved) {
            throw new DAVException("Unreserved checkouts are not yet available. A version history may not be checked out more than once, into a specific activity.", 
                    null, HttpServletResponse.SC_NOT_IMPLEMENTED, null, SVNLogType.NETWORK, Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, 
                    DAVElement.SVN_DAV_ERROR_NAMESPACE, SVNErrorCode.UNSUPPORTED_FEATURE.getCode(), null);
        }
        
        if (activities == null) {
            throw new DAVException("An activity must be provided for checkout.", null, HttpServletResponse.SC_CONFLICT, null, SVNLogType.NETWORK, 
                    Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, DAVElement.SVN_DAV_ERROR_NAMESPACE, SVNErrorCode.INCOMPLETE_DATA.getCode(), 
                    null);
        }
        
        if (activities.size() != 1) {
            throw new DAVException("Only one activity may be specified within the CHECKOUT.", null, HttpServletResponse.SC_CONFLICT, null, 
                    SVNLogType.NETWORK, Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, DAVElement.SVN_DAV_ERROR_NAMESPACE, 
                    SVNErrorCode.INCORRECT_PARAMS.getCode(), null);
        }
        
        DAVURIInfo parse = null;
        
        try {
            parse = DAVPathUtil.simpleParseURI((String) activities.get(0), resource);
        } catch (SVNException svne) {
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_CONFLICT, "The activity href could not be parsed properly.",
                    null);
        }
        
        if (parse.getActivityID() == null) {
            throw new DAVException("The provided href is not an activity URI.", null, HttpServletResponse.SC_CONFLICT, null, SVNLogType.NETWORK, 
                    Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, DAVElement.SVN_DAV_ERROR_NAMESPACE, SVNErrorCode.INCORRECT_PARAMS.getCode(), 
                    null);
        }
        
        String txnName = DAVServletUtil.getTxn(resource.getActivitiesDB(), parse.getActivityID());
        if (txnName == null) {
            throw new DAVException("The specified activity does not exist.", null, HttpServletResponse.SC_CONFLICT, null, SVNLogType.NETWORK, 
                    Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, DAVElement.SVN_DAV_ERROR_NAMESPACE, 
                    SVNErrorCode.APMOD_ACTIVITY_NOT_FOUND.getCode(), null);
        }
        
        if (resource.isBaseLined() || !SVNRevision.isValidRevisionNumber(resource.getRevision())) {
            long youngestRevision = -1;
            try {
                youngestRevision = myFSFS.getYoungestRevision();
            } catch (SVNException svne) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Could not determine the youngest revision for verification against the baseline being checked out.", null);
            }
            
            if (resource.getRevision() != youngestRevision) {
                throw new DAVException("The specified baseline is not the latest baseline, so it may not be checked out.", null, 
                        HttpServletResponse.SC_CONFLICT, null, SVNLogType.NETWORK, Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, 
                        DAVElement.SVN_DAV_ERROR_NAMESPACE, SVNErrorCode.APMOD_BAD_BASELINE.getCode(), null);
            }
        } else {
            FSTransactionInfo txnInfo = DAVServletUtil.openTxn(myFSFS, txnName);
            FSTransactionRoot txnRoot = null;
            String reposPath = resource.getResourceURI().getPath();
            
            try {
                txnRoot = myFSFS.createTransactionRoot(txnInfo);
            } catch (SVNException svne) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Could not open the transaction tree.", null);
            }
            
            long txnCreatedRevision = -1;
            try {
                FSRevisionNode node = txnRoot.getRevisionNode(reposPath);
                txnCreatedRevision = node.getCreatedRevision();
            } catch (SVNException svne) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Could not get created-rev of transaction node.", null);
            }
            
            if (SVNRevision.isValidRevisionNumber(txnCreatedRevision)) {
                if (resource.getRevision() < txnCreatedRevision) {
                    throw new DAVException("resource out of date; try updating", null, HttpServletResponse.SC_CONFLICT, null, SVNLogType.NETWORK, 
                            Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, DAVElement.SVN_DAV_ERROR_NAMESPACE, 
                            SVNErrorCode.FS_CONFLICT.getCode(), null);
                } else if (resource.getRevision() > txnCreatedRevision) {
                    String txnNodeRevID = null;
                    try {
                        FSRevisionNode node = txnRoot.getRevisionNode(reposPath);
                        txnNodeRevID = node.getId().getNodeID();
                    } catch (SVNException svne) {
                        SVNErrorMessage err = svne.getErrorMessage();
                        throw new DAVException("Unable to fetch the node revision id of the version resource within the transaction.", null, 
                                HttpServletResponse.SC_CONFLICT, err, SVNLogType.FSFS, Level.FINE, null, 
                                DAVXMLUtil.SVN_DAV_ERROR_TAG, DAVElement.SVN_DAV_ERROR_NAMESPACE, err.getErrorCode().getCode(), null);
                    }
                    
                    String urlNodeRevID = null;
                    try {
                        FSRoot root = resource.getRoot();
                        FSRevisionNode node = root.getRevisionNode(reposPath);
                        urlNodeRevID = node.getId().getNodeID();
                    } catch (SVNException svne) {
                        SVNErrorMessage err = svne.getErrorMessage();
                        throw new DAVException("Unable to fetch the node revision id of the version resource within the revision.", null, 
                                HttpServletResponse.SC_CONFLICT, err, SVNLogType.FSFS, Level.FINE, null, 
                                DAVXMLUtil.SVN_DAV_ERROR_TAG, DAVElement.SVN_DAV_ERROR_NAMESPACE, err.getErrorCode().getCode(), null);
                    }
                    
                    if (!urlNodeRevID.equals(txnNodeRevID)) {
                        throw new DAVException("version resource newer than txn (restart the commit)", null, HttpServletResponse.SC_CONFLICT, 
                                null, SVNLogType.NETWORK, Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, DAVElement.SVN_DAV_ERROR_NAMESPACE, 
                                SVNErrorCode.FS_CONFLICT.getCode(), null);
                    }
                }
            }
        }
    
        return createWorkingResource(resource, parse.getActivityID(), txnName);
    }
    
    private DAVWorkingResource createWorkingResource(DAVResource baseResource, String activityID, String txnName) {
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
