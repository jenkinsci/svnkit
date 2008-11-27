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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.handlers.BasicDAVHandler;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.tmatesoft.svn.core.internal.io.fs.FSCommitter;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionRoot;
import org.tmatesoft.svn.core.internal.server.dav.DAVAutoVersion;
import org.tmatesoft.svn.core.internal.server.dav.DAVConfig;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVIFHeader;
import org.tmatesoft.svn.core.internal.server.dav.DAVIFState;
import org.tmatesoft.svn.core.internal.server.dav.DAVIFStateType;
import org.tmatesoft.svn.core.internal.server.dav.DAVLock;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceHelper;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceKind;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceState;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.server.dav.DAVServletUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVURIInfo;
import org.tmatesoft.svn.core.internal.server.dav.DAVVersionResourceHelper;
import org.tmatesoft.svn.core.internal.server.dav.DAVWorkingResourceHelper;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.util.CountingInputStream;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.util.Version;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public abstract class ServletDAVHandler extends BasicDAVHandler {

    public static final int SC_MULTISTATUS = 207;
    public static final int SC_HTTP_LOCKED = 423;
    public static final int SC_FAILED_DEPENDANCY = 424;
    
    //some flag constants
    public static final int DAV_VALIDATE_RESOURCE  = 0x0010;
    public static final int DAV_VALIDATE_PARENT    = 0x0020;
    public static final int DAV_VALIDATE_ADD_LD    = 0x0040;
    public static final int DAV_VALIDATE_USE_424   = 0x0080;
    public static final int DAV_VALIDATE_IS_PARENT = 0x0100;
    
    protected static final String HTTP_STATUS_OK_LINE = "HTTP/1.1 200 OK";
    protected static final String HTTP_NOT_FOUND_LINE = "HTTP/1.1 404 NOT FOUND";
    
    protected static final String DAV_RESPONSE_BODY_1 = "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n<html><head>\n<title>";
    protected static final String DAV_RESPONSE_BODY_2 = "</title>\n</head><body>\n<h1>";
    protected static final String DAV_RESPONSE_BODY_3 = "</h1>\n<p>";
    protected static final String DAV_RESPONSE_BODY_4 = "</p>\n";
    protected static final String DAV_RESPONSE_BODY_5 = "</body></html>\n";
    
    protected static final String DEFAULT_XML_CONTENT_TYPE = "text/xml; charset=\"utf-8\"";

    protected static final String UTF8_ENCODING = "UTF-8";
    protected static final String BASE64_ENCODING = "base64";    

    //Specific svn headers
    protected static final String SVN_OPTIONS_HEADER = "X-SVN-Options";
    protected static final String SVN_DELTA_BASE_HEADER = "X-SVN-VR-Base";
    protected static final String SVN_VERSION_NAME_HEADER = "X-SVN-Version-Name";
    protected static final String SVN_CREATIONDATE_HEADER = "X-SVN-Creation-Date";
    protected static final String SVN_LOCK_OWNER_HEADER = "X-SVN-Lock-Owner";
    protected static final String SVN_BASE_FULLTEXT_MD5_HEADER = "X-SVN-Base-Fulltext-MD5";
    protected static final String SVN_RESULT_FULLTEXT_MD5_HEADER = "X-SVN-Result-Fulltext-MD5";

    //Precondition headers
    protected static final String IF_MATCH_HEADER = "If-Match";
    protected static final String IF_UNMODIFIED_SINCE_HEADER = "If-Unmodified-Since";
    protected static final String IF_NONE_MATCH_HEADER = "If-None-Match";
    protected static final String IF_MODIFIED_SINCE_HEADER = "If-Modified-Since";
    protected static final String ETAG_HEADER = "ETag";
    protected static final String RANGE_HEADER = "Range";

    //Common HTTP headers
    protected static final String DEPTH_HEADER = "Depth";
    protected static final String VARY_HEADER = "Vary";
    protected static final String LAST_MODIFIED_HEADER = "Last-Modified";
    protected static final String LABEL_HEADER = "Label";
    protected static final String USER_AGENT_HEADER = "User-Agent";
    protected static final String CONNECTION_HEADER = "Connection";
    protected static final String DATE_HEADER = "Date";
    protected static final String KEEP_ALIVE_HEADER = "Keep-Alive";
    protected static final String ACCEPT_RANGES_HEADER = "Accept-Ranges";
    protected static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";
    protected static final String CACHE_CONTROL_HEADER = "Cache-Control";
    
    //Supported live properties
    protected static final DAVElement GET_CONTENT_TYPE = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getcontenttype");
    protected static final DAVElement GET_LAST_MODIFIED = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getlastmodified");
    protected static final DAVElement GET_ETAG = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getetag");
    protected static final DAVElement LOG = DAVElement.getElement(DAVElement.SVN_SVN_PROPERTY_NAMESPACE, "log");

    //Common xml attributes
    protected static final String NAME_ATTR = "name";
    protected static final String NAMESPACE_ATTR = "namespace";

    protected static final String DIFF_VERSION_1 = "svndiff1";
    protected static final String DIFF_VERSION = "svndiff";

    protected static final String ACCEPT_RANGES_DEFAULT_VALUE = "bytes";    
    protected static final String CACHE_CONTROL_VALUE = "no-cache";
    
    private static final Pattern COMMA = Pattern.compile(",");

    //Report related stuff DAVOptionsHandler uses
    protected static Set REPORT_ELEMENTS = new SVNHashSet();
    protected static final DAVElement UPDATE_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "update-report");
    protected static final DAVElement LOG_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "log-report");
    protected static final DAVElement DATED_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "dated-rev-report");
    protected static final DAVElement GET_LOCATIONS = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locations");
    protected static final DAVElement FILE_REVISIONS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "file-revs-report");
    protected static final DAVElement GET_LOCKS_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "get-locks-report");
    protected static final DAVElement REPLAY_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "replay-report");
    protected static final DAVElement MERGEINFO_REPORT = DAVElement.getElement(DAVElement.SVN_NAMESPACE, "mergeinfo-report");

    private static SAXParserFactory ourSAXParserFactory;
    private SAXParser mySAXParser;

    private DAVRepositoryManager myRepositoryManager;
    private HttpServletRequest myRequest;
    private HttpServletResponse myResponse;
    private FSCommitter myCommitter;
    
    static {
        REPORT_ELEMENTS.add(UPDATE_REPORT);
        REPORT_ELEMENTS.add(LOG_REPORT);
        REPORT_ELEMENTS.add(DATED_REVISIONS_REPORT);
        REPORT_ELEMENTS.add(GET_LOCATIONS);
        REPORT_ELEMENTS.add(FILE_REVISIONS_REPORT);
        REPORT_ELEMENTS.add(GET_LOCKS_REPORT);
        REPORT_ELEMENTS.add(REPLAY_REPORT);
        REPORT_ELEMENTS.add(MERGEINFO_REPORT);
    }

    protected ServletDAVHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        myRepositoryManager = connector;
        myRequest = request;
        myResponse = response;
        init();
    }

    protected DAVRepositoryManager getRepositoryManager() {
        return myRepositoryManager;
    }

    protected DAVConfig getConfig() {
        return myRepositoryManager.getDAVConfig();
    }

    public abstract void execute() throws SVNException;

    protected abstract DAVRequest getDAVRequest();

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        getDAVRequest().startElement(parent, element, attrs);
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        getDAVRequest().endElement(parent, element, cdata);
    }

    protected DAVResource getRequestedDAVResource(boolean labelAllowed, boolean useCheckedIn) throws SVNException {
        String label = labelAllowed ? getRequestHeader(LABEL_HEADER) : null;
        String versionName = getRequestHeader(SVN_VERSION_NAME_HEADER);
        long version = DAVResource.INVALID_REVISION;
        try {
            version = Long.parseLong(versionName);
        } catch (NumberFormatException e) {
        }
        String clientOptions = getRequestHeader(SVN_OPTIONS_HEADER);
        String baseChecksum = getRequestHeader(SVN_BASE_FULLTEXT_MD5_HEADER);
        String resultChecksum = getRequestHeader(SVN_RESULT_FULLTEXT_MD5_HEADER);
        String deltaBase = getRequestHeader(SVN_DELTA_BASE_HEADER);
        String userAgent = getRequestHeader(USER_AGENT_HEADER);
        boolean isSVNClient = userAgent != null && (userAgent.startsWith("SVN/") || userAgent.startsWith("SVNKit"));
        return getRepositoryManager().getRequestedDAVResource(isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, 
                label, useCheckedIn);
    }

    protected DAVResourceState getResourceState(DAVResource resource) throws SVNException {
        if (resource.exists()) {
            return DAVResourceState.EXISTS;
        }

        DAVLockInfoProvider lockInfoProvider = DAVLockInfoProvider.createLockInfoProvider(this, true);
        
        try {
            if (lockInfoProvider.hasLocks(resource)) {
                return DAVResourceState.LOCK_NULL;
            }
        } catch (DAVException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, "Failed to query lock-null status for " + resource.getResourceURI().getPath());
            return DAVResourceState.ERROR;
        }
        
        return DAVResourceState.NULL; 
    }
    
    protected void validateRequest(DAVResource resource, DAVDepth depth, int flags, String lockToken, DAVLockInfoProvider lockInfoProvider) throws SVNException {
        boolean setETag = false;
        String eTag = getRequestHeader(ETAG_HEADER);
        if (eTag == null) {
            eTag = resource.getETag();
            if (eTag != null && eTag.length() > 0) {
                setResponseHeader(ETAG_HEADER, eTag);
                setETag = true;
            }
        }
        
        DAVResourceState resourceState = getResourceState(resource);
        int result = meetsCondition(resource, resourceState);
        
        if (setETag) {
            setResponseHeader(ETAG_HEADER, null);
        }
        
        if (result != 0) {
            throw new DAVException(null, null, result, null, SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
        }
       
        LinkedList ifHeaders = DAVServletUtil.processIfHeader(getRequestHeader(HTTPHeader.IF_HEADER));
        
        if (lockToken != null) {
            DAVIFState ifState = new DAVIFState(DAVIFState.IF_CONDITION_NORMAL, null, lockToken, DAVIFStateType.IF_OPAQUE_LOCK);
            DAVIFHeader ifHeader = new DAVIFHeader(resource.getResourceURI().getRequestURI(), true);
            ifHeader.addIFState(ifState);
            ifHeaders.addFirst(ifHeader);
        }
        
        if (lockInfoProvider == null) {
            lockInfoProvider = DAVLockInfoProvider.createLockInfoProvider(this, false);
        }
        
        DAVException exception = null;
        DAVResponse response = null;
        DAVValidateWalker validateHandler = new DAVValidateWalker();
        if (resource.exists() && depth.getID() > 0) {
            DAVResourceWalker walker = new DAVResourceWalker();
            int walkType = DAVResourceWalker.DAV_WALKTYPE_NORMAL | DAVResourceWalker.DAV_WALKTYPE_LOCKNULL;
            try {
                response = walker.walk(lockInfoProvider, resource, ifHeaders, flags, walkType, validateHandler, DAVDepth.DEPTH_INFINITY);
            } catch (DAVException dave) {
                exception = dave;
            }
        } else {
            try {
                validateHandler.validateResourceState(ifHeaders, resource, lockInfoProvider, null, flags);
            } catch (DAVException dave) {
                exception = dave;
            }
        }
        
        if (exception == null && (flags & DAV_VALIDATE_PARENT) != 0) {
            DAVResource parentResource = null;
            try {
                parentResource = DAVResourceHelper.createParentResource(resource);
            } catch (DAVException dave) {
                exception = dave;
            }
            
            if (exception == null) {
                try {
                    validateHandler.validateResourceState(ifHeaders, parentResource, lockInfoProvider, null, flags | DAV_VALIDATE_IS_PARENT);
                } catch (DAVException dave) {
                    exception = dave;
                }
                
                if (exception != null) {
                    String description = "A validation error has occurred on the parent resource, preventing the operation on the resource specified by the Request-URI.";
                    if (exception.getMessage() != null) {
                        description += " The error was: " + exception.getMessage();
                    }
                    response = new DAVResponse(description, parentResource.getResourceURI().getRequestURI(), response, null, exception.getResponseCode());
                    exception = null;
                }
            }
        }
        
        if (exception == null && response != null) {
            if ((flags & DAV_VALIDATE_USE_424) != 0) {
                throw new DAVException("An error occurred on another resource, preventing the requested operation on this resource.", 
                        SC_FAILED_DEPENDANCY, 0, response);
            }
            
            DAVPropsResult propStat = null;
            if ((flags & DAV_VALIDATE_ADD_LD) != 0) {
                propStat = new DAVPropsResult();
                propStat.addPropStatsText("<D:propstat>\n<D:prop><D:lockdiscovery/></D:prop>\n<D:status>HTTP/1.1 424 Failed Dependency</D:status>\n</D:propstat>\n");
            }
            
            response = new DAVResponse("An error occurred on another resource, preventing the requested operation on this resource.", 
                    resource.getResourceURI().getRequestURI(), response, propStat, SC_FAILED_DEPENDANCY);
            
            throw new DAVException("Error(s) occurred on resources during the validation process.", SC_MULTISTATUS, 0, response);
        }
        
        if (exception != null) {
            exception.setResponse(response);
            throw exception;
        }
    }
    
    protected DAVResource checkOut(DAVResource resource, boolean isAutoCheckOut, boolean isUnreserved, boolean isCreateActivity, 
            List activities) throws DAVException {
        DAVResourceType resourceType = resource.getResourceURI().getType();
        FSFS fsfs = resource.getFSFS();
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
                
                sharedTxnInfo = DAVServletUtil.createActivity(resource, fsfs);
                sharedTxnName = sharedTxnInfo.getTxnId();
                DAVServletUtil.storeActivity(resource, sharedTxnInfo);
                DAVServlet.setSharedActivity(sharedActivity);
            }
            
            if (sharedTxnName == null) {
                sharedTxnName = DAVServletUtil.getTxn(resource.getActivitiesDB(), sharedActivity);
                if (sharedTxnName == null) {
                    throw new DAVException("Cannot look up a txn_name by activity", null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, 
                            SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
                }
            }
            
            resource = DAVWorkingResourceHelper.createWorkingResource(resource, sharedActivity, sharedTxnName, true);
            resource.setIsAutoCkeckedOut(true);
            FSTransactionInfo txnInfo = DAVServletUtil.openTxn(fsfs, resource.getTxnName());
            FSTransactionRoot txnRoot = null;
            try {
                txnRoot = fsfs.createTransactionRoot(txnInfo);
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
                youngestRevision = fsfs.getYoungestRevision();
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
            FSTransactionInfo txnInfo = DAVServletUtil.openTxn(fsfs, txnName);
            FSTransactionRoot txnRoot = null;
            String reposPath = resource.getResourceURI().getPath();
            
            try {
                txnRoot = fsfs.createTransactionRoot(txnInfo);
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
    
        return DAVWorkingResourceHelper.createWorkingResource(resource, parse.getActivityID(), txnName, false);
    }

    protected DAVResource checkIn(DAVResource resource, boolean keepCheckedOut, boolean createVersionResource) throws DAVException {
        if (resource.getType() != DAVResourceType.WORKING) {
            throw new DAVException("CHECKIN called on non-working resource.", null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, 
                    SVNLogType.NETWORK, Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, DAVElement.SVN_DAV_ERROR_NAMESPACE, 
                    SVNErrorCode.UNSUPPORTED_FEATURE.getCode(), null);
        }
        
        DAVResource versionResource = null;
        DAVResourceURI resourceURI = resource.getResourceURI();
        String sharedActivity = DAVServlet.getSharedActivity();
        if (sharedActivity != null && sharedActivity.equals(resource.getActivityID())) {
            String sharedTxnName = DAVServletUtil.getTxn(resource.getActivitiesDB(), sharedActivity);
            if (sharedTxnName == null) {
                throw new DAVException("Cannot look up a txn_name by activity", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 0);
            }
            
            if (resource.getTxnName() != null && !sharedTxnName.equals(resource.getTxnName())) {
                throw new DAVException("Internal txn_name doesn't match autoversioning transaction.", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 0);
            }
            
            if (resource.getTxnInfo() == null) {
                throw new DAVException("Autoversioning txn isn't open when it should be.", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 0);
            }
            
            DAVServletUtil.setAutoRevisionProperties(resource);
            FSCommitter committer = getCommitter(resource.getFSFS(), resource.getRoot(), resource.getTxnInfo(), null, resource.getUserName());
            
            StringBuffer conflictPath = new StringBuffer(); 
            long newRev = SVNRepository.INVALID_REVISION;
            try {
                newRev = committer.commitTxn(true, true, null, conflictPath);
            } catch (SVNException svne) {
                try {
                    FSCommitter.abortTransaction(resource.getFSFS(), resource.getTxnInfo().getTxnId());
                } catch (SVNException svne2) {
                    //ignore
                }
                String message = null;
                Object[] objects = null;
                if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_CONFLICT) {
                    message = "A conflict occurred during the CHECKIN processing. The problem occurred with  the \"{0}\" resource.";
                    objects = new Object[] { conflictPath.toString() };
                } else {
                    message = "An error occurred while committing the transaction.";
                }
                
                DAVServletUtil.deleteActivity(resource, sharedActivity);
                DAVServlet.setSharedActivity(null);
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_CONFLICT, message, objects);
            }
            
            DAVServletUtil.deleteActivity(resource, sharedActivity);
            if (createVersionResource) {
                String uri = DAVPathUtil.buildURI(resourceURI.getContext(), DAVResourceKind.VERSION, newRev, resourceURI.getPath());
                versionResource = DAVVersionResourceHelper.createVersionResource(resource, uri);
            }
        }
        
        resource.setTxnName(null);
        resource.setTxnInfo(null);
        
        if (!keepCheckedOut) {
            resource.setIsAutoCkeckedOut(false);
            DAVResourceHelper.convertWorkingToRegular(resource);
        }
        return versionResource;
    }
    
    protected void uncheckOut(DAVResource resource) throws DAVException {
        if (resource.getType() != DAVResourceType.WORKING) {
            throw new DAVException("UNCHECKOUT called on non-working resource.", null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, 
                    SVNLogType.NETWORK, Level.FINE, null, DAVXMLUtil.SVN_DAV_ERROR_TAG, DAVElement.SVN_DAV_ERROR_NAMESPACE, 
                    SVNErrorCode.UNSUPPORTED_FEATURE.getCode(), null);
        }
        
        FSTransactionInfo txnInfo = resource.getTxnInfo();
        if (txnInfo != null) {
            try {
                FSCommitter.abortTransaction(resource.getFSFS(), txnInfo.getTxnId());
            } catch (SVNException svne) {
                //ignore
            }
        }
        
        DAVResourceURI resourceURI = resource.getResourceURI();
        if (resourceURI.getActivityID() != null) {
            try {
                DAVServletUtil.deleteActivity(resource, resourceURI.getActivityID());
            } catch (DAVException dave) {
                //ignore
            }
            DAVServlet.setSharedActivity(null);
        }
        
        resource.setTxnName(null);
        resource.setTxnInfo(null);
        resource.setIsAutoCkeckedOut(false);
        
        DAVResourceHelper.convertWorkingToRegular(resource);
    }
    
    protected DAVAutoVersionInfo autoCheckOut(DAVResource resource, boolean isParentOnly) throws DAVException {
        DAVAutoVersionInfo info = new DAVAutoVersionInfo();
        DAVLockInfoProvider[] lockProvider = new DAVLockInfoProvider[0];
        if (!resource.exists() || isParentOnly) {
            DAVResource parentResource = null;
            try {
                parentResource = DAVResourceHelper.createParentResource(resource);
            } catch (DAVException dave) {
                autoCheckIn(resource, true, false, info);
                throw dave;
            }
            
            if (parentResource == null || !parentResource.exists()) {
                autoCheckIn(resource, true, false, info);
                throw new DAVException("Missing one or more intermediate collections. Cannot create resource {0}.", 
                        new Object[] { SVNEncodingUtil.xmlEncodeCDATA(resource.getResourceURI().getRequestURI()) }, 
                        HttpServletResponse.SC_CONFLICT, 0);
            }
            
            info.setParentResource(parentResource);
            
            if (parentResource.isVersioned() && !parentResource.isWorking()) {
                boolean checkOutParent = false;
                try {
                    checkOutParent = canAutoCheckOut(parentResource, lockProvider, parentResource.getAutoVersion());
                } catch (DAVException dave) {
                    autoCheckIn(resource, true, false, info);
                    throw dave;
                }
                
                if (!checkOutParent) {
                    autoCheckIn(resource, true, false, info);
                    throw new DAVException("<DAV:cannot-modify-checked-in-parent>", HttpServletResponse.SC_CONFLICT, 0);
                }
               
                try {
                    checkOut(parentResource, true, false, false, null);
                } catch (DAVException dave) {
                    autoCheckIn(resource, true, false, info);
                    throw new DAVException("Unable to auto-checkout parent collection. Cannot create resource {0}.", 
                            new Object[] { resource.getResourceURI().getRequestURI() }, HttpServletResponse.SC_CONFLICT, dave, 0);
                }
                
                info.setParentCheckedOut(true);
            }
        }
        
        if (isParentOnly) {
            return info;
        }
        
        if (!resource.exists() && resource.getAutoVersion() == DAVAutoVersion.ALWAYS) {
            try {
                resource.versionControl(null);
            } catch (DAVException dave) {
                autoCheckIn(resource, true, false, info);
                throw new DAVException("Unable to create versioned resource {0}.", 
                        new Object[] { SVNEncodingUtil.xmlEncodeCDATA(resource.getResourceURI().getRequestURI()) }, 
                        HttpServletResponse.SC_CONFLICT, dave, 0); 
            }
            
            info.setResourceVersioned(true);
        }
        
        if (resource.isVersioned() && !resource.isWorking()) {
            boolean checkOutResource = false;
            try {
                checkOutResource = canAutoCheckOut(resource, lockProvider, resource.getAutoVersion());
            } catch (DAVException dave) {
                autoCheckIn(resource, true, false, info);
                throw dave;
            }
            
            if (!checkOutResource) {
                autoCheckIn(resource, true, false, info);
                throw new DAVException("<DAV:cannot-modify-version-controlled-content>", HttpServletResponse.SC_CONFLICT, 0);
            }
            
            try {
                checkOut(resource, true, false, false, null);
            } catch (DAVException dave) {
                autoCheckIn(resource, true, false, info);
                throw new DAVException("Unable to checkout resource {0}.", 
                        new Object[] { SVNEncodingUtil.xmlEncodeCDATA(resource.getResourceURI().getRequestURI()) }, 
                        HttpServletResponse.SC_CONFLICT, 0);
            }
            
            info.setResourceCheckedOut(true);
        }
        return info;
    }
    
    protected boolean canAutoCheckOut(DAVResource resource, DAVLockInfoProvider[] lockProvider, DAVAutoVersion autoVersion) throws DAVException {
        boolean autoCheckOut = false;
        DAVLock lock = null;
        if (autoVersion == DAVAutoVersion.ALWAYS) {
            autoCheckOut = true;
        } else if (autoVersion == DAVAutoVersion.LOCKED) {
            if (lockProvider[0] == null) {
                try {
                    lockProvider[0] = DAVLockInfoProvider.createLockInfoProvider(this, false);
                } catch (SVNException svne) {
                    throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                            "Cannot open lock database to determine auto-versioning behavior.", null);
                }
            }
            
            try {
                lock = lockProvider[0].getLock(resource);
            } catch (DAVException dave) {
                throw new DAVException("The locks could not be queried for determining auto-versioning behavior.", null, 
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR, dave, 0);
            }
            
            if (lock != null) {
                autoCheckOut = true;
            }
        }
        
        return autoCheckOut;
    }
    
    protected void autoCheckIn(DAVResource resource, boolean undo, boolean unlock, DAVAutoVersionInfo info) throws DAVException {
        if (undo) {
            if (resource != null) {
                if (info.isResourceCheckedOut()) {
                    try {
                        uncheckOut(resource);
                    } catch (DAVException dave) {
                        throw new DAVException("Unable to undo auto-checkout of resource {0}.", 
                                new Object[] { SVNEncodingUtil.xmlEncodeCDATA(resource.getResourceURI().getRequestURI()) }, 
                                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, SVNLogType.NETWORK, Level.FINE, dave, null, null, 0, null);
                    }
                }
                
                if (info.isResourceVersioned()) {
                    try {
                        removeResource(resource);
                    } catch (DAVException dave) {
                        throw new DAVException("Unable to undo auto-version-control of resource {0}.", 
                                new Object[] { SVNEncodingUtil.xmlEncodeCDATA(resource.getResourceURI().getRequestURI()) }, 
                                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, SVNLogType.NETWORK, Level.FINE, dave, null, null, 0, null);
                    }
                }
            }
            
            if (info.getParentResource() != null && info.isParentCheckedOut()) {
                try {
                    uncheckOut(info.getParentResource());
                } catch (DAVException dave) {
                    throw new DAVException("Unable to undo auto-checkout of parent collection {0}.", 
                            new Object[] { SVNEncodingUtil.xmlEncodeCDATA(info.getParentResource().getResourceURI().getRequestURI()) }, 
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, SVNLogType.NETWORK, Level.FINE, dave, null, null, 0, null);
                }
            }
            return;
        }
        
        if (resource != null && resource.isWorking() && (unlock || info.isResourceCheckedOut())) {
            DAVAutoVersion autoVersion = resource.getAutoVersion();
            if (autoVersion == DAVAutoVersion.ALWAYS || (unlock && autoVersion == DAVAutoVersion.LOCKED)) {
                try {
                    checkIn(resource, false, false);
                } catch (DAVException dave) {
                    throw new DAVException("Unable to auto-checkin resource {0}.", 
                            new Object[] { SVNEncodingUtil.xmlEncodeCDATA(resource.getResourceURI().getRequestURI()) }, 
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, SVNLogType.NETWORK, Level.FINE, dave, null, null, 0, null);
                }
            }
        }
        
        if (!unlock && info.isParentCheckedOut() && info.getParentResource() != null && 
                info.getParentResource().getType() == DAVResourceType.WORKING) {
            DAVAutoVersion autoVersion = info.getParentResource().getAutoVersion();
            if (autoVersion == DAVAutoVersion.ALWAYS) {
                try {
                    checkIn(info.getParentResource(), false, false);
                } catch (DAVException dave) {
                    throw new DAVException("Unable to auto-checkin parent collection {0}.", 
                            new Object[] { SVNEncodingUtil.xmlEncodeCDATA(info.getParentResource().getResourceURI().getRequestURI()) }, 
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, SVNLogType.NETWORK, Level.FINE, dave, null, null, 0, null);
                }
            }
        }
    }
    
    protected void removeResource(DAVResource resource) throws DAVException {
        DAVResourceURI uri = resource.getResourceURI();
        DAVResourceType resourceType = uri.getType();
        if (resourceType != DAVResourceType.REGULAR && resourceType != DAVResourceType.WORKING && resourceType != DAVResourceType.ACTIVITY) {
            throw new DAVException("DELETE called on invalid resource type.", HttpServletResponse.SC_METHOD_NOT_ALLOWED, 0);
        }
        
        DAVConfig config = getConfig();
        if (resourceType == DAVResourceType.REGULAR && !config.isAutoVersioning()) {
            throw new DAVException("DELETE called on regular resource, but autoversioning is not active.", 
                    HttpServletResponse.SC_METHOD_NOT_ALLOWED, 0);
        }
        
        if (resourceType == DAVResourceType.ACTIVITY) {
            DAVServletUtil.deleteActivity(resource, uri.getActivityID());
            return;
        }
        
        if (resourceType == DAVResourceType.REGULAR) {
            checkOut(resource, true, false, false, null);
        }
        
        if (SVNRevision.isValidRevisionNumber(resource.getVersion())) {
            long createdRevision = SVNRepository.INVALID_REVISION;
            try {
                createdRevision = resource.getCreatedRevisionUsingFS(null);
            } catch (SVNException svne) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Could not get created rev of resource", null);
            }
            
            if (resource.getVersion() < createdRevision) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_OUT_OF_DATE, "Item ''{0}'' is out of date", 
                        uri.getPath());
                throw DAVException.convertError(err, HttpServletResponse.SC_CONFLICT, "Can''t DELETE out-of-date resource", null);
            }
        }
        
        //TODO: lock pushing?
        
        FSCommitter committer = getCommitter(resource.getFSFS(), resource.getRoot(), resource.getTxnInfo(), null, resource.getUserName());
        try {
            committer.deleteNode(uri.getPath());
        } catch (SVNException svne) {
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "Could not delete the resource", null);
        }
        
        if (resource.isAutoCheckedOut()) {
            checkIn(resource, false, false);
        }
    }
    
    protected DAVDepth getRequestDepth(DAVDepth defaultDepth) throws SVNException {
        String depth = getRequestHeader(DEPTH_HEADER);
        if (depth == null) {
            return defaultDepth;
        }
        DAVDepth result = DAVDepth.parseDepth(depth);
        if (result == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Invalid depth ''{0}''", depth), SVNLogType.NETWORK);
        }
        return result;
    }

    protected void setDefaultResponseHeaders() {
        if (getRequestHeader(LABEL_HEADER) != null && getRequestHeader(LABEL_HEADER).length() > 0) {
            setResponseHeader(VARY_HEADER, LABEL_HEADER);
        }
    }

    protected String getURI() {
        return myRequest.getPathInfo();
    }

    protected String getRequestHeader(String name) {
        return myRequest.getHeader(name);
    }

    protected String getRequestMethod() {
        return myRequest.getMethod();
    }
    
    protected Enumeration getRequestHeaders(String name) {
        return myRequest.getHeaders(name);
    }

    protected long getRequestDateHeader(String name) {
        return myRequest.getDateHeader(name);
    }

    protected long[] parseRange() {
        String range = getRequestHeader(HTTPHeader.CONTENT_RANGE_HEADER);
        if (range == null) {
            return null;
        }
        
        range = range.toLowerCase(); 
        if (!range.startsWith("bytes ") || !range.contains("-") || !range.contains("/")) {
            return null;
        }
        
        range = range.substring("bytes ".length()).trim();
        int ind = range.indexOf('-');
        String rangeStartSubstring = range.substring(0, ind);
        long rangeStart = -1;
        try {
            rangeStart = Long.parseLong(rangeStartSubstring);
        } catch (NumberFormatException nfe) {
            return null;
        }
        
        range = range.substring(ind + 1);
        ind = range.indexOf('/');
        
        //String rangeEndSubstring
        
        return null;
    }
    
    protected InputStream getRequestInputStream() throws SVNException {
        try {
            return myRequest.getInputStream();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e, SVNLogType.NETWORK);
        }
        return null;
    }

    protected void setResponseHeader(String name, String value) {
        myResponse.setHeader(name, value);
    }

    protected void addResponseHeader(String name, String value) {
        myResponse.addHeader(name, value);
    }

    protected void setResponseStatus(int statusCode) {
        myResponse.setStatus(statusCode);
    }

    protected void setResponseContentType(String contentType) {
        myResponse.setContentType(contentType);
    }

    protected void setResponseContentLength(int length) {
        myResponse.setContentLength(length);
    }

    protected Writer getResponseWriter() throws SVNException {
        try {
            return myResponse.getWriter();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e, SVNLogType.NETWORK);
        }
        return null;
    }

    protected OutputStream getResponseOutputStream() throws SVNException {
        try {
            return myResponse.getOutputStream();
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e, SVNLogType.NETWORK);
        }
        return null;
    }

    protected void handleDAVCreated(String location, String what, boolean isReplaced) throws SVNException {
        if (location == null) {
            location = getURI();
        }
        
        if (isReplaced) {
            return;
        }

        setResponseHeader(HTTPHeader.LOCATION_HEADER, constructURL(location));
        String body = what + " " + SVNEncodingUtil.xmlEncodeCDATA(location) + " has been created.";
        response(body, DAVServlet.getStatusLine(HttpServletResponse.SC_CREATED), HttpServletResponse.SC_CREATED);
    }

    protected void response(String body, String statusLine, int statusCode) throws SVNException {
        setResponseStatus(statusCode);
        setResponseContentType("text/html; charset=ISO-8859-1");
        StringBuffer responseBuffer = new StringBuffer();
        responseBuffer.append(DAV_RESPONSE_BODY_1);
        responseBuffer.append(statusLine);
        responseBuffer.append(DAV_RESPONSE_BODY_2);
        responseBuffer.append(statusLine.substring(4));
        responseBuffer.append(DAV_RESPONSE_BODY_3);
        responseBuffer.append(body);
        responseBuffer.append(DAV_RESPONSE_BODY_4);
        appendServerSignature(responseBuffer, "<hr />\n");
        responseBuffer.append(DAV_RESPONSE_BODY_5);
        
        String responseBody = responseBuffer.toString();
        try {
            setResponseContentLength(responseBody.getBytes(UTF8_ENCODING).length);
        } catch (UnsupportedEncodingException e) {
            setResponseContentLength(responseBody.getBytes().length);
        }

        try {
            getResponseWriter().write(responseBody);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e, SVNLogType.NETWORK);
        }
    }
    
    protected String constructURL(String location) {
        StringBuffer url = new StringBuffer ();
        String scheme = myRequest.getScheme();
        String host = myRequest.getServerName();
        
        url.append(scheme);
        url.append("://");
        url.append(host);
        
        int port = myRequest.getServerPort();
        if ((scheme.equals ("http") && port != 80) || (scheme.equals ("https") && port != 443)) {
            url.append(':');
            url.append(port);
        }

        if (!location.startsWith("/")) {
            url.append('/');
        }
        url.append(location);
        return url.toString();
    }
    
    protected void appendServerSignature(StringBuffer buffer, String prefix) {
        buffer.append(prefix);
        buffer.append("<address>");
        ServletContext context = myRequest.getSession().getServletContext();
        
        buffer.append(context.getServerInfo());
        buffer.append(" ");
        buffer.append(Version.getVersionString());
        buffer.append(" ");

        String host = myRequest.getServerName();
        buffer.append("Server at ");
        buffer.append(SVNEncodingUtil.xmlEncodeCDATA(host));
        buffer.append(" Port ");

        int port = myRequest.getServerPort();
        buffer.append(port);
        buffer.append("</address>\n");
    }

    protected static Collection getSupportedLiveProperties(DAVResource resource, Collection properties) {
        if (properties == null) {
            properties = new ArrayList();
        }
        properties.add(DAVElement.DEADPROP_COUNT);
        properties.add(DAVElement.REPOSITORY_UUID);
        properties.add(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
        if (resource.getResourceURI().getKind() != DAVResourceKind.BASELINE_COLL) {
            properties.add(DAVElement.BASELINE_COLLECTION);
        } else {
            properties.remove(DAVElement.BASELINE_COLLECTION);
        }
        properties.add(DAVElement.BASELINE_RELATIVE_PATH);
        properties.add(DAVElement.RESOURCE_TYPE);
        properties.add(DAVElement.CHECKED_IN);
        properties.add(GET_ETAG);
        properties.add(DAVElement.CREATOR_DISPLAY_NAME);
        properties.add(DAVElement.CREATION_DATE);
        properties.add(GET_LAST_MODIFIED);
        properties.add(DAVElement.VERSION_NAME);
        properties.add(GET_CONTENT_TYPE);
        if (!resource.isCollection()) {
            properties.add(DAVElement.GET_CONTENT_LENGTH);
            properties.add(DAVElement.MD5_CHECKSUM);
        } else {
            properties.remove(DAVElement.GET_CONTENT_LENGTH);
            properties.remove(DAVElement.MD5_CHECKSUM);
        }
        return properties;
    }

    protected int checkPreconditions(String eTag, Date lastModified) {
        lastModified = lastModified == null ? new Date() : lastModified;
        long lastModifiedTime = lastModified.getTime();
        Enumeration ifMatch = getRequestHeaders(IF_MATCH_HEADER);
        if (ifMatch != null && ifMatch.hasMoreElements()) {
            String first = (String) ifMatch.nextElement();
            if (!first.startsWith("*") && (eTag == null || eTag.startsWith("W") || !first.equals(eTag) || !containsValue(ifMatch, eTag, null))) {
                return HttpServletResponse.SC_PRECONDITION_FAILED;
            }
        } else {
            long ifUnmodified = getRequestDateHeader(IF_UNMODIFIED_SINCE_HEADER);
            if (ifUnmodified != -1 && lastModifiedTime > ifUnmodified) {
                return HttpServletResponse.SC_PRECONDITION_FAILED;
            }
        }

        boolean notModified = false;
        Enumeration ifNoneMatch = getRequestHeaders(IF_NONE_MATCH_HEADER);
        if (ifNoneMatch != null && ifNoneMatch.hasMoreElements()) {
            String first = (String) ifNoneMatch.nextElement();
            if (DAVHandlerFactory.METHOD_GET.equals(getRequestMethod())) {
                if (first.startsWith("*")) {
                    notModified = true;
                } else if (eTag != null) {
                    if (getRequestHeader(RANGE_HEADER) != null) {
                        notModified = !eTag.startsWith("W") && containsValue(ifNoneMatch, eTag, null); 
                    } else {
                        notModified = containsValue(ifNoneMatch, eTag, null);
                    }
                }
            } else if (first.startsWith("*") || (eTag != null && containsValue(ifNoneMatch, eTag, null))) {
                return HttpServletResponse.SC_PRECONDITION_FAILED;
            }
        }
        
        long ifModifiedSince = getRequestDateHeader(IF_MODIFIED_SINCE_HEADER);
        if (DAVHandlerFactory.METHOD_GET.equals(getRequestMethod()) && (notModified || ifNoneMatch == null) && ifModifiedSince != -1) {
            long requestTime = myRequest.getSession().getLastAccessedTime();
            notModified = ifModifiedSince >= lastModifiedTime && ifModifiedSince <= requestTime;
        }
        
        if (notModified) {
            return HttpServletResponse.SC_NOT_MODIFIED;
        }

        return 0; 
    }

    protected boolean containsValue(Enumeration values, String stringToFind, String matchAllString) {
        boolean contains = false;
        if (values != null) {
            while (values.hasMoreElements()) {
                String currentCondition = (String) values.nextElement();
                contains = currentCondition.equals(stringToFind) || currentCondition.equals(matchAllString);
                if (contains) {
                    break;
                }
            }
        }
        return contains;
    }

    protected boolean getSVNDiffVersion() {
        boolean diffCompress = false;
        for (Enumeration headerEncodings = getRequestHeaders(ACCEPT_ENCODING_HEADER); headerEncodings.hasMoreElements();)
        {
            String currentEncodings = (String) headerEncodings.nextElement();
            String[] encodings = COMMA.split(currentEncodings);
            if (encodings.length > 1) {

                Arrays.sort(encodings, new Comparator() {
                    public int compare(Object o1, Object o2) {
                        String encoding1 = (String) o1;
                        String encoding2 = (String) o2;
                        return getEncodingRange(encoding1) > getEncodingRange(encoding2) ? 1 : -1;
                    }
                });

                for (int i = encodings.length - 1; i >= 0; i--) {
                    if (DIFF_VERSION_1.equals(getEncodingName(encodings[i]))) {
                        diffCompress = true;
                        break;
                    } else if (DIFF_VERSION.equals(getEncodingName(encodings[i]))) {
                        break;
                    }
                }
            }
        }
        return diffCompress;
    }
    
    private FSCommitter getCommitter(FSFS fsfs, FSRoot root, FSTransactionInfo txn, Collection lockTokens, String userName) {
        if (myCommitter == null) {
            myCommitter = new FSCommitter(fsfs, (FSTransactionRoot) root, txn, lockTokens, userName);
        } else {
            myCommitter.reset(fsfs, (FSTransactionRoot) root, txn, lockTokens, userName);
        }
        return myCommitter;
    }
    
    private float getEncodingRange(String encoding) {
        int delimiterIndex = encoding.indexOf(";");
        if (delimiterIndex != -1) {
            String qualityString = encoding.substring(delimiterIndex + 1);
            if (qualityString.startsWith("q=")) {
                try {
                    return Float.parseFloat(qualityString.substring("q=".length()));
                } catch (NumberFormatException e) {
                }
            }
        }
        return 1.0f;
    }

    private String getEncodingName(String encoding) {
        int delimiterIndex = encoding.indexOf(";");
        if (delimiterIndex != -1) {
            return encoding.substring(0, delimiterIndex);
        }
        return encoding;
    }

    private int meetsCondition(DAVResource resource, DAVResourceState resourceState) throws SVNException {
        Enumeration ifMatch = getRequestHeaders(IF_MATCH_HEADER);
        if (ifMatch != null && ifMatch.hasMoreElements()) {
            String first = (String) ifMatch.nextElement();
            if (first.startsWith("*") && resourceState != DAVResourceState.EXISTS) {
                return HttpServletResponse.SC_PRECONDITION_FAILED;
            }
        }
        
        int retVal = checkPreconditions(resource.getETag(), resource.getLastModified());
        if (retVal == HttpServletResponse.SC_PRECONDITION_FAILED) {
            Enumeration ifNoneMatch = getRequestHeaders(IF_NONE_MATCH_HEADER);
            if (ifNoneMatch != null && ifNoneMatch.hasMoreElements()) {
                String first = (String) ifNoneMatch.nextElement();
                if (first.startsWith("*") && resourceState != DAVResourceState.EXISTS) {
                    return 0;
                }
            }
        }
        
        return retVal;
    }

    protected long readInput(boolean ignoreInput) throws SVNException {
        if (ignoreInput) {
            InputStream inputStream = null;
            try {
                inputStream = getRequestInputStream();
                while (inputStream.read() != -1) {
                    continue;
                }
            } catch (IOException ioe) {
                //
            } finally {
                SVNFileUtil.closeFile(inputStream);
            }
            return -1;
        }

        if (mySAXParser == null) {
            CountingInputStream stream = null;
            try {
                mySAXParser = getSAXParserFactory().newSAXParser();
                if (myRequest.getContentLength() > 0) {
                    XMLReader reader = mySAXParser.getXMLReader();
                    reader.setContentHandler(this);
                    reader.setDTDHandler(this);
                    reader.setErrorHandler(this);
                    reader.setEntityResolver(this);
                    stream = new CountingInputStream(getRequestInputStream());
                    reader.parse(new InputSource(stream));
                }
            } catch (ParserConfigurationException e) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, e.getMessage());
                if (stream == null || stream.getBytesRead() > 0) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e, SVNLogType.NETWORK);
                }
            } catch (SAXException e) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, e.getMessage());
                if (stream == null || stream.getBytesRead() > 0) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e, SVNLogType.NETWORK);
                }
            } catch (IOException e) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, e.getMessage());
                if (stream == null || stream.getBytesRead() > 0) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e, SVNLogType.NETWORK);
                }
            }

            if (stream != null) {
                if (stream.getBytesRead() > 0) {
                    getDAVRequest().init();
                }
                return stream.getBytesRead();
            }
        }
        
        return 0;
    }

    protected void handleError(DAVException error, DAVResponse response) {
        if (response == null) {
            DAVException stackErr = error;
            while (stackErr != null && stackErr.getTagName() == null) {
                stackErr = stackErr.getPreviousException();
            }
            
            if (stackErr != null && stackErr.getTagName() != null) {
                myResponse.setContentType(DEFAULT_XML_CONTENT_TYPE);
                
                StringBuffer errorMessageBuffer = new StringBuffer();
                errorMessageBuffer.append('\n');
                errorMessageBuffer.append("<D:error xmlns:D=\"DAV:\"");
                
                if (stackErr.getMessage() != null) {
                    errorMessageBuffer.append(" xmlns:m=\"http://apache.org/dav/xmlns\"");
                }
                
                if (stackErr.getNameSpace() != null) {
                    errorMessageBuffer.append(" xmlns:C=\"");
                    errorMessageBuffer.append(stackErr.getNameSpace());
                    errorMessageBuffer.append("\">\n<C:");
                    errorMessageBuffer.append(stackErr.getTagName());
                    errorMessageBuffer.append("/>");
                } else {
                    errorMessageBuffer.append(">\n<D:");
                    errorMessageBuffer.append(stackErr.getTagName());
                    errorMessageBuffer.append("/>");
                }
                
                if (stackErr.getMessage() != null) {
                    
                }

            }
        }
    }

    private synchronized static SAXParserFactory getSAXParserFactory() {
        if (ourSAXParserFactory == null) {
            ourSAXParserFactory = SAXParserFactory.newInstance();
            try {
                ourSAXParserFactory.setFeature("http://xml.org/sax/features/namespaces", true);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            try {
                ourSAXParserFactory.setFeature("http://xml.org/sax/features/validation", false);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            try {
                ourSAXParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            ourSAXParserFactory.setNamespaceAware(true);
            ourSAXParserFactory.setValidating(false);
        }
        return ourSAXParserFactory;
    }
    
    protected static class DAVAutoVersionInfo {
        private boolean myIsResourceVersioned;
        private boolean myIsResourceCheckedOut;
        private boolean myIsParentCheckedOut;
        private DAVResource myParentResource;
        
        public boolean isResourceVersioned() {
            return myIsResourceVersioned;
        }
        
        public void setResourceVersioned(boolean isResourceVersioned) {
            myIsResourceVersioned = isResourceVersioned;
        }
        
        public boolean isResourceCheckedOut() {
            return myIsResourceCheckedOut;
        }
        
        public void setResourceCheckedOut(boolean isResourceCheckedOut) {
            myIsResourceCheckedOut = isResourceCheckedOut;
        }
        
        public boolean isParentCheckedOut() {
            return myIsParentCheckedOut;
        }
        
        public void setParentCheckedOut(boolean isParentCheckedOut) {
            myIsParentCheckedOut = isParentCheckedOut;
        }
        
        public DAVResource getParentResource() {
            return myParentResource;
        }
        
        public void setParentResource(DAVResource parentResource) {
            myParentResource = parentResource;
        }
        
    }
}
