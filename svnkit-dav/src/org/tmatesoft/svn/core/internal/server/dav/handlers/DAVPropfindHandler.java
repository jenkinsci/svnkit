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
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.server.dav.DAVConfig;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVLock;
import org.tmatesoft.svn.core.internal.server.dav.DAVLockScope;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceKind;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceState;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;
import org.tmatesoft.svn.core.internal.server.dav.DAVServlet;
import org.tmatesoft.svn.core.internal.server.dav.DAVServletUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class DAVPropfindHandler extends ServletDAVHandler implements IDAVResourceWalkHandler {
    public static final List NAMESPACES = new LinkedList();
    static {
        NAMESPACES.add(DAVElement.DAV_NAMESPACE);
        NAMESPACES.add(DAVElement.SVN_DAV_PROPERTY_NAMESPACE);
    }

    private static final String DEFAULT_AUTOVERSION_LINE = "DAV:checkout-checkin";
    private static final String COLLECTION_RESOURCE_TYPE = "<D:collection/>\n";
    
    private DAVPropfindRequest myDAVRequest;
    private boolean myIsAllProp;
    private boolean myIsPropName;
    private boolean myIsProp;
    private DAVElementProperty myDocRoot;
    private StringBuffer myPropStat404;
    private StringBuffer myResponseBuffer;
    private DAVLockInfoProvider myLocksProvider;

    public DAVPropfindHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    protected DAVRequest getDAVRequest() {
        return getPropfindRequest();
    }

    public void execute() throws SVNException {
        SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "in propfind");

        readInput(false);

        SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "read the input");
        
        DAVResource resource = getRequestedDAVResource(true, false);
        
        SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "got the resource");

        StringBuffer body = new StringBuffer();
        DAVDepth depth = getRequestDepth(DAVDepth.DEPTH_INFINITY);
        generatePropertiesResponse(body, resource, depth);
        String responseBody = body.toString();

        try {
            setResponseContentLength(responseBody.getBytes(UTF8_ENCODING).length);
        } catch (UnsupportedEncodingException e) {
        }

        setDefaultResponseHeaders();
        setResponseContentType(DEFAULT_XML_CONTENT_TYPE);
        setResponseStatus(SC_MULTISTATUS);

        try {
            getResponseWriter().write(responseBody);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e, SVNLogType.NETWORK);
        }
    }

    public void execute2() throws SVNException {
        DAVResource resource = getRequestedDAVResource(true, false);

        DAVResourceState resourceState = getResourceState(resource);
        if (resourceState == DAVResourceState.NULL) {
            //TODO: what body should we send?
            setResponseStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        
        DAVDepth depth = getRequestDepth(DAVDepth.DEPTH_INFINITY);
        //TODO: check the depth is not less than 0; if it is, send BAD_REQUEST
        
        if (depth == DAVDepth.DEPTH_INFINITY && resource.isCollection()) {
            DAVConfig config = getConfig();
            if (!config.isAllowDepthInfinity()) {
                String message = "PROPFIND requests with a Depth of \"infinity\" are not allowed for " + 
                    SVNEncodingUtil.xmlEncodeCDATA(getURI()) + ".";
                response(message, DAVServlet.getStatusLine(HttpServletResponse.SC_FORBIDDEN), HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }

        long readCount = readInput(false);
        DAVPropfindRequest request = getPropfindRequest();
        DAVElementProperty rootElement = request.getRootElement();
        
        if (readCount > 0 && rootElement.getName() != DAVElement.PROPFIND) {
            //TODO: maybe add logging here later
            //TODO: what body should we send?
            setResponseStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        
        myIsAllProp = false;
        myIsPropName = false;
        myIsProp = false;
        if (readCount == 0 || rootElement.hasChild(DAVElement.ALLPROP)) {
            myIsAllProp = true;
        } else if (rootElement.hasChild(DAVElement.PROPNAME)) {
            myIsPropName = true;
        } else if (rootElement.hasChild(DAVElement.PROP)) {
            myIsProp = true;
        } else {
            //TODO: what body should we send?
            //TODO: maybe add logging here later
            setResponseStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        
        myLocksProvider = null;
        try {
            myLocksProvider = DAVLockInfoProvider.createLockInfoProvider(this, false);
        } catch (SVNException svne) {
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "The lock database could not be opened, preventing access to the various lock properties for the PROPFIND.", null);
        }
    
        StringBuffer body = new StringBuffer();
        DAVXMLUtil.beginMultiStatus(getHttpServletResponse(), SC_MULTISTATUS, getNamespaces(), body);
        
        int walkType = DAVResourceWalker.DAV_WALKTYPE_NORMAL | DAVResourceWalker.DAV_WALKTYPE_AUTH | DAVResourceWalker.DAV_WALKTYPE_LOCKNULL; 
        DAVResourceWalker walker = new DAVResourceWalker();

        
        
        
        
        
        
        
        
        
        generatePropertiesResponse(body, resource, depth);
        String responseBody = body.toString();

        try {
            setResponseContentLength(responseBody.getBytes(UTF8_ENCODING).length);
        } catch (UnsupportedEncodingException e) {
        }

        setResponseStatus(SC_MULTISTATUS);

        try {
            getResponseWriter().write(responseBody);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e, SVNLogType.NETWORK);
        }
    }

    public DAVResponse handleResource(DAVResponse response, DAVResource resource, DAVLockInfoProvider lockInfoProvider, LinkedList ifHeaders, 
            int flags, DAVLockScope lockScope, CallType callType) throws DAVException {
        DAVPropertiesProvider propsProvider = null;
        try {
            propsProvider = DAVPropertiesProvider.createPropertiesProvider(resource, this);
        } catch (DAVException dave) {
            if (myIsProp) {
                cacheBadProps();
                DAVPropsResult badProps = new DAVPropsResult();
                badProps.addPropStatsText(myPropStat404.toString());
                streamResponse(resource, 0, badProps);
            } else {
                streamResponse(resource, HttpServletResponse.SC_OK, null);
            }
            return null;
        }
        
        DAVPropsResult result = null;
        if (myIsProp) {
            result = getProps(propsProvider, getPropfindRequest().getRootElement());
        } else {
            
        }
        
        return null;
    }
    
    private void getAllProps(DAVPropertiesProvider propsProvider, DAVInsertPropAction action) throws DAVException {
        boolean foundContentType = false;
        boolean foundContentLang = false;
        DAVPropsResult result = new DAVPropsResult();
        StringBuffer buffer = new StringBuffer();
        if (action != DAVInsertPropAction.INSERT_SUPPORTED) {
            if (propsProvider.isDeferred()) {
                propsProvider.open(true);
            }
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROPSTAT.getName(), SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROP.getName(), SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            
            Map namespaces = new HashMap();
            propsProvider.defineNamespaces(namespaces);
            Collection propNames = propsProvider.getPropertyNames();
            int ind = 0;
            for (Iterator propNamesIter = propNames.iterator(); propNamesIter.hasNext();) {
                DAVElement propNameElement = (DAVElement) propNamesIter.next();
                if (DAVElement.DAV_NAMESPACE.equals(propNameElement.getNamespace())) {
                    if (DAVElement.GET_CONTENT_TYPE.getName().equals(propNameElement.getName())) {
                        foundContentType = true;
                    } else if (DAVElement.GET_CONTENT_LANGUAGE.getName().equals(propNameElement.getName())) {
                        foundContentLang = true;
                    }
                    
                    if (action == DAVInsertPropAction.INSERT_VALUE) {
                        try {
                            propsProvider.outputValue(propNameElement, buffer);
                        } catch (DAVException dave) {
                            //TODO: probably change this behavior in future
                            continue;
                        }
                    } else {
                        ind = outputPropName(propNameElement, namespaces, ind, buffer);
                    }
                }
            }
            
            generateXMLNSNamespaces(result, namespaces);
        }
    }
    
    private DAVPropsResult getProps(DAVPropertiesProvider propsProvider, DAVElementProperty docRootElement) throws DAVException {
        StringBuffer buffer = new StringBuffer();
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROPSTAT.getName(), SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROP.getName(), SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
        
        StringBuffer badRes = null;
        Collection xmlnses = new LinkedList();
        boolean haveGood = false;
        boolean definedNamespaces = false;
        Map namespaces = new HashMap();
        int prefixInd = 0;
        
        List childrenElements = docRootElement.getChildren();
        for (Iterator childrenIter = childrenElements.iterator(); childrenIter.hasNext();) {
            DAVElementProperty childElement = (DAVElementProperty) childrenIter.next();
            LivePropertySpecification livePropSpec = findLiveProperty(childElement.getName());
            if (livePropSpec != null) {
                DAVInsertPropAction doneAction = insertLiveProp(propsProvider.getResource(), livePropSpec, 
                        DAVInsertPropAction.INSERT_VALUE, buffer);
                if (doneAction == DAVInsertPropAction.INSERT_VALUE) {
                    haveGood = true;
                    int ind = 0;
                    for (Iterator namespacesIter = NAMESPACES.iterator(); namespacesIter.hasNext();) {
                        String namespace = (String) namespacesIter.next();
                        String xmlns = " xmlns:lp" + ind + "=\"" + namespace + "\"";
                        xmlnses.add(xmlns);
                    }
                    continue;
                } 
            }
            
            if (propsProvider.isDeferred()) {
                propsProvider.open(true);
            }
            
            boolean found = false;
            try {
                found = propsProvider.outputValue(childElement.getName(), buffer);
            } catch (DAVException dave) {
                continue;
            }
            
            if (found) {
                haveGood = true;
                if (!definedNamespaces) {
                    propsProvider.defineNamespaces(namespaces);
                    definedNamespaces = true;
                }
                continue;
            }
            
            if (badRes == null) {
                badRes = new StringBuffer();
                SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROPSTAT.getName(), SVNXMLUtil.XML_STYLE_NORMAL, null, badRes);
                SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROP.getName(), SVNXMLUtil.XML_STYLE_NORMAL, null, badRes);
            }
            
            prefixInd = outputPropName(childElement.getName(), namespaces, prefixInd, buffer);
        }
        
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROP.getName(), buffer);
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.STATUS.getName(), SVNXMLUtil.XML_STYLE_PROTECT_CDATA, null, buffer);
        buffer.append("HTTP/1.1 200 OK");
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.STATUS.getName(), buffer);
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROPSTAT.getName(), buffer);
        
        DAVPropsResult result = new DAVPropsResult();
        if (badRes != null) {
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROP.getName(), badRes);
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.STATUS.getName(), SVNXMLUtil.XML_STYLE_PROTECT_CDATA, null, badRes);
            badRes.append("HTTP/1.1 404 Not Found");
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.STATUS.getName(), badRes);
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROPSTAT.getName(), badRes);
            if (!haveGood) {
                result.addPropStatsText(badRes.toString());
            } else {
                result.addPropStatsText(buffer.toString());
                result.addPropStatsText(badRes.toString());
            }
        } else {
            result.addPropStatsText(buffer.toString());
        }
        
        addNamespaces(result, xmlnses);
        generateXMLNSNamespaces(result, namespaces);
        return result;
    }
    
    private void addNamespaces(DAVPropsResult result, Collection xmlnses) {
        for (Iterator xmlnsesIter = xmlnses.iterator(); xmlnsesIter.hasNext();) {
            String xmlnsString = (String) xmlnsesIter.next();
            result.addNamespace(xmlnsString);
        }
    }
    
    private void generateXMLNSNamespaces(DAVPropsResult result, Map prefixesToNamespaces) {
        for (Iterator prefixesIter = prefixesToNamespaces.keySet().iterator(); prefixesIter.hasNext();) {
            String prefix = (String) prefixesIter.next();
            String uri = (String) prefixesToNamespaces.get(prefix);
            result.addNamespace(" xmlns:" + prefix + "=\"" + uri + "\"");
        }
    }
    
    private int outputPropName(DAVElement propName, Map prefixesToNamespaces, int ind, StringBuffer buffer) {
        if ("".equals(propName.getNamespace())) {
            SVNXMLUtil.openXMLTag(null, propName.getName(), SVNXMLUtil.XML_STYLE_SELF_CLOSING, null, buffer);
        } else {
            String prefix = prefixesToNamespaces != null ? (String) prefixesToNamespaces.get(propName.getNamespace()) : null;
            if (prefix == null) {
                prefix = "g" + ind;
                prefixesToNamespaces.put(propName.getNamespace(), prefix);
            }
            SVNXMLUtil.openXMLTag((String) prefixesToNamespaces.get(propName.getNamespace()), propName.getName(), 
                    SVNXMLUtil.XML_STYLE_SELF_CLOSING, null, buffer);
        }
        return ind++;
    }
    
    private DAVInsertPropAction insertCoreLiveProperty(DAVResource resource, DAVPropertiesProvider propProvider, DAVInsertPropAction propAction, 
            LivePropertySpecification livePropSpec) throws DAVException {
        DAVInsertPropAction inserted = DAVInsertPropAction.NOT_DEF;
        DAVElement livePropElement = livePropSpec.getPropertyName();
        String value = null;
        if (livePropElement == DAVElement.LOCK_DISCOVERY) {
            if (myLocksProvider != null) {
                DAVLock lock = null;
                try {
                    lock = myLocksProvider.getLock(resource);
                } catch (DAVException dave) {
                    throw new DAVException("DAV:lockdiscovery could not be determined due to a problem fetching the locks for this resource.", 
                            dave.getResponseCode(), dave, 0);
                }
                
                if (lock == null) {
                    value = "";
                } else {
                    value = DAVLockInfoProvider.getActiveLockXML(lock);
                }
            }
        } else if (livePropElement == DAVElement.SUPPORTED_LOCK) {
            if (myLocksProvider != null) {
                value = myLocksProvider.getSupportedLock(resource);
            }
        } else if (livePropElement == DAVElement.GET_CONTENT_TYPE) {
            //
        }
        
        return null;
    }
    
    private DAVInsertPropAction insertLiveProp(DAVResource resource, LivePropertySpecification livePropSpec, DAVInsertPropAction propAction, 
            StringBuffer buffer) {
        if (!livePropSpec.isSVNSupported()) {
            //this is a core WebDAV live prop
        }
        
        DAVElement livePropElement = livePropSpec.getPropertyName();
        if (!resource.exists() && livePropElement != DAVElement.VERSION_CONTROLLED_CONFIGURATION && 
                livePropElement != DAVElement.BASELINE_RELATIVE_PATH) {
            return DAVInsertPropAction.NOT_SUPP;
        }

        String value = null;
        DAVResourceURI uri = resource.getResourceURI();
        if (livePropElement == DAVElement.GET_LAST_MODIFIED || livePropElement == DAVElement.CREATION_DATE) {
            if (resource.getType() == DAVResourceType.PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
                return DAVInsertPropAction.NOT_SUPP;
            }
            
            if (livePropElement == DAVElement.CREATION_DATE) {
                try {
                    value = SVNDate.formatDate(getLastModifiedTime2(resource));
                } catch (SVNException svne) {
                    return DAVInsertPropAction.NOT_DEF;
                } 
            } else if (livePropElement == DAVElement.GET_LAST_MODIFIED) {
                try {
                    value = SVNDate.formatRFC1123Date(getLastModifiedTime2(resource));
                } catch (SVNException svne) {
                    return DAVInsertPropAction.NOT_DEF;
                }
            }
            value = SVNEncodingUtil.xmlEncodeCDATA(value, true);
        } else if (livePropElement == DAVElement.CREATOR_DISPLAY_NAME) {
            if (resource.getType() == DAVResourceType.PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
                return DAVInsertPropAction.NOT_SUPP;
            }
            
            long committedRev = -1;
            if (resource.isBaseLined() && resource.getType() == DAVResourceType.VERSION) {
                committedRev = resource.getRevision();
            } else if (resource.getType() == DAVResourceType.REGULAR || resource.getType() == DAVResourceType.WORKING || 
                    resource.getType() == DAVResourceType.VERSION) {
                try {
                    committedRev = resource.getCreatedRevisionUsingFS(null);
                } catch (SVNException svne) {
                    value = "###error###";
                }
            } else {
                return DAVInsertPropAction.NOT_SUPP;
            }
            
            String lastAuthor = null;
            try {
                lastAuthor = resource.getAuthor(committedRev);
            } catch (SVNException svne) {
                value = "###error###";
            }
            
            if (lastAuthor == null) {
                return DAVInsertPropAction.NOT_DEF;
            }
            
            value = SVNEncodingUtil.xmlEncodeCDATA(value, true);
        } else if (livePropElement == DAVElement.GET_CONTENT_LANGUAGE) {
            return DAVInsertPropAction.NOT_SUPP;
        } else if (livePropElement == DAVElement.GET_CONTENT_LENGTH) {
            if (resource.isCollection() || resource.isBaseLined()) {
                return DAVInsertPropAction.NOT_SUPP;
            }

            long fileSize = 0;
            try {
                fileSize = resource.getContentLength(null);
                value = String.valueOf(fileSize);
            } catch (SVNException e) {
                value = "0";
            }
        } else if (livePropElement == DAVElement.GET_CONTENT_TYPE) {
            if (resource.isBaseLined() && resource.getType() == DAVResourceType.VERSION) {
                return DAVInsertPropAction.NOT_SUPP;
            }
            
            if (resource.getType() == DAVResourceType.PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
                return DAVInsertPropAction.NOT_SUPP;
            }
            
            if (resource.isCollection()) {
                value = DAVResource.DEFAULT_COLLECTION_CONTENT_TYPE;
            } else {
                SVNPropertyValue contentType = null;
                try {
                    contentType = resource.getProperty(null, SVNProperty.MIME_TYPE);
                } catch (SVNException svne) {
                    //
                }
                
                if (contentType != null) {
                    value = contentType.getString();
                } else if (!resource.isSVNClient() && getRequest().getContentType() != null) {
                    value = getRequest().getContentType();
                } else {
                    value = DAVResource.DEFAULT_FILE_CONTENT_TYPE;
                }

                try {
                    SVNPropertiesManager.validateMimeType(value);
                } catch (SVNException svne) {
                    return DAVInsertPropAction.NOT_DEF;
                }
            }
        } else if (livePropElement == DAVElement.GET_ETAG) {
            if (resource.getType() == DAVResourceType.PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
                return DAVInsertPropAction.NOT_SUPP;
            }
            
            value = resource.getETag();
        } else if (livePropElement == DAVElement.AUTO_VERSION) {
            if (getConfig().isAutoVersioning()) {
                value = DEFAULT_AUTOVERSION_LINE;
            } else {
                return DAVInsertPropAction.NOT_DEF;
            }
        } else if (livePropElement == DAVElement.BASELINE_COLLECTION) {
            if (resource.getType() != DAVResourceType.VERSION || !resource.isBaseLined()) {
                return DAVInsertPropAction.NOT_SUPP;
            }
            value = DAVPathUtil.buildURI(uri.getContext(), DAVResourceKind.BASELINE_COLL, resource.getRevision(), null, 
                    true);
        } else if (livePropElement == DAVElement.CHECKED_IN) {
            String s = null;
            if (resource.getType() == DAVResourceType.PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
                long revNum = -1;
                try {
                    revNum = resource.getLatestRevision();
                    s = DAVPathUtil.buildURI(uri.getContext(), DAVResourceKind.BASELINE, revNum, null, false);
                    StringBuffer buf = SVNXMLUtil.openCDataTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.HREF.getName(), s, null, true, null);
                    value = buf.toString();
                } catch (SVNException svne) {
                    value = "###error###";
                }
            } else if (resource.getType() != DAVResourceType.REGULAR) {
                return DAVInsertPropAction.NOT_SUPP;
            } else {
                long revToUse = DAVServletUtil.getSafeCreatedRevision((FSRevisionRoot) resource.getRoot(), uri.getPath());
                s = DAVPathUtil.buildURI(uri.getContext(), DAVResourceKind.VERSION, revToUse, uri.getPath(), false);
                StringBuffer buf = SVNXMLUtil.openCDataTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.HREF.getName(), s, null, true, null);
                value = buf.toString();
            }
        } else if (livePropElement == DAVElement.VERSION_CONTROLLED_CONFIGURATION) {
            if (resource.getType() != DAVResourceType.REGULAR) {
                return DAVInsertPropAction.NOT_SUPP;
            }
            value = DAVPathUtil.buildURI(uri.getContext(), DAVResourceKind.VCC, -1, null, true);
        } else if (livePropElement == DAVElement.VERSION_NAME) {
            if (resource.getType() != DAVResourceType.VERSION && !resource.isVersioned()) {
                return DAVInsertPropAction.NOT_SUPP;
            }
            
            if (resource.getType() == DAVResourceType.PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
                return DAVInsertPropAction.NOT_SUPP;
            }
            
            if (resource.isBaseLined()) {
                value = String.valueOf(resource.getRevision());
            } else {
                try {
                    long committedRev = resource.getCreatedRevisionUsingFS(null);
                    value = String.valueOf(committedRev);
                    value = SVNEncodingUtil.xmlEncodeCDATA(value, true);
                } catch (SVNException svne) {
                    value = "###error###";
                }    
            }
        } else if (livePropElement == DAVElement.BASELINE_RELATIVE_PATH) {
            if (resource.getType() != DAVResourceType.REGULAR) {
                return DAVInsertPropAction.NOT_SUPP;
            }
            value = SVNEncodingUtil.xmlEncodeCDATA(DAVPathUtil.dropLeadingSlash(uri.getPath()), true);
        } else if (livePropElement == DAVElement.MD5_CHECKSUM) {
            if (!resource.isCollection() && !resource.isBaseLined() && (resource.getType() == DAVResourceType.REGULAR || 
                    resource.getType() == DAVResourceType.VERSION || resource.getType() == DAVResourceType.WORKING)) {
                try {
                    value = resource.getMD5Checksum(null);
                    if (value == null) {
                        return DAVInsertPropAction.NOT_SUPP;
                    }
                } catch (SVNException svne) {
                    value = "###error###";
                }
            } else {
                return DAVInsertPropAction.NOT_SUPP;
            }
        } else if (livePropElement == DAVElement.REPOSITORY_UUID) {
            try {
                value = resource.getRepositoryUUID(false);
            } catch (SVNException svne) {
                value = "###error###";
            }
        } else if (livePropElement == DAVElement.DEADPROP_COUNT) {
            if (resource.getType() != DAVResourceType.REGULAR) {
                return DAVInsertPropAction.NOT_SUPP;
            }
            
            SVNProperties props = null;
            try {
                props = resource.getSVNProperties(null);
                int deadPropertiesCount = props.size();
                value = String.valueOf(deadPropertiesCount);
            } catch (SVNException svne) {
                value = "###error###";
            }
        } else {
            return DAVInsertPropAction.NOT_SUPP;
        }

        int ind = NAMESPACES.indexOf(livePropElement.getNamespace());
        String prefix = "lp" + ind;
        if (propAction == DAVInsertPropAction.INSERT_NAME || 
                (propAction == DAVInsertPropAction.INSERT_VALUE && (value == null || value.length() == 0))) {
            SVNXMLUtil.openXMLTag(prefix, livePropElement.getName(), SVNXMLUtil.XML_STYLE_SELF_CLOSING, null, buffer);
        } else if (propAction == DAVInsertPropAction.INSERT_VALUE) {
            SVNXMLUtil.openXMLTag(prefix, livePropElement.getName(), SVNXMLUtil.XML_STYLE_NORMAL, null, buffer);
            buffer.append(value);
            SVNXMLUtil.closeXMLTag(prefix, livePropElement.getName(), buffer);
        } else {
            Map attrs = new HashMap();
            attrs.put("D:name", livePropElement.getName());
            attrs.put("D:namespace", livePropElement.getNamespace());
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.SUPPORTED_LIVE_PROPERTY.getName(), SVNXMLUtil.XML_STYLE_SELF_CLOSING, 
                    attrs, buffer);
        }
        
        return propAction;
    }
    
    private Date getLastModifiedTime2(DAVResource resource) throws SVNException {
        long revision = -1;
        if (resource.isBaseLined() && resource.getType() == DAVResourceType.VERSION) {
            revision = resource.getRevision();
        } else if (resource.getType() == DAVResourceType.REGULAR || resource.getType() == DAVResourceType.WORKING || 
                resource.getType() == DAVResourceType.VERSION) {
                revision = resource.getCreatedRevisionUsingFS(null);
        } else {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, 
                    "Failed to determine property"), SVNLogType.NETWORK);
        }
        return resource.getRevisionDate(revision);
    }

    private void streamResponse(DAVResource resource, int status, DAVPropsResult propStats) {
        DAVResponse response = new DAVResponse(null, resource.getResourceURI().getRequestURI(), null, propStats, status);
        DAVXMLUtil.sendOneResponse(response, myResponseBuffer);
    }
    
    private void cacheBadProps() {
        if (myPropStat404 != null) {
            return;
        }
        
        myPropStat404 = new StringBuffer();
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROPSTAT.getName(), SVNXMLUtil.XML_STYLE_PROTECT_CDATA, null, 
                myPropStat404);
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROP.getName(), SVNXMLUtil.XML_STYLE_PROTECT_CDATA, null, 
                myPropStat404);
        DAVElementProperty elem = myDocRoot.getChild(DAVElement.PROP);
        List childrenElements = elem.getChildren();
        for (Iterator childrenIter = childrenElements.iterator(); childrenIter.hasNext();) {
            DAVElementProperty childElement = (DAVElementProperty) childrenIter.next();
            DAVXMLUtil.addEmptyElement(DAVPropfindHandler.this.getNamespaces(), childElement.getName(), myPropStat404);
        }
        
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROP.getName(), myPropStat404);
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.STATUS.getName(), SVNXMLUtil.XML_STYLE_NORMAL, null, myPropStat404);
        myPropStat404.append("HTTP/1.1 404 Not Found");
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.STATUS.getName(), myPropStat404);
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.PROPSTAT.getName(), myPropStat404);
    }

    private DAVPropfindRequest getPropfindRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVPropfindRequest();
        }
        return myDAVRequest;
    }

    private void generatePropertiesResponse(StringBuffer xmlBuffer, DAVResource resource, DAVDepth depth) throws SVNException {
        Collection properties;
        if (getPropfindRequest().isPropRequest()) {
            properties = getPropfindRequest().getPropertyElements();
        } else {
            properties = convertDeadPropertiesToDAVElements(resource.getSVNProperties());
            getSupportedLiveProperties(resource, properties);
        }

        SVNXMLUtil.addXMLHeader(xmlBuffer);
        DAVXMLUtil.openNamespaceDeclarationTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "multistatus", null, xmlBuffer, false);

        generateResponse(xmlBuffer, resource, properties, depth);

        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "multistatus", xmlBuffer);
    }

    private void generateResponse(StringBuffer xmlBuffer, DAVResource resource, Collection properties, DAVDepth depth) throws SVNException {
        addResponse(xmlBuffer, resource, properties);
        if ((depth != DAVDepth.DEPTH_ZERO && resource.getResourceURI().getType() == DAVResourceType.REGULAR && resource.isCollection())) {
            DAVDepth newDepth = DAVDepth.decreaseDepth(depth);
            for (Iterator iterator = resource.getChildren(); iterator.hasNext();) {
                DAVResource child = (DAVResource) iterator.next();
                if (child == null) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Error while fetching child of ''{0}''", 
                            resource.getResourceURI().getPath()), SVNLogType.NETWORK);
                }
                if (getPropfindRequest().isAllPropRequest()) {
                    properties.clear();
                    properties.addAll(convertDeadPropertiesToDAVElements(child.getSVNProperties()));
                    getSupportedLiveProperties(child, properties);
                }
                generateResponse(xmlBuffer, child, properties, newDepth);
            }
        }
    }

    private void addResponse(StringBuffer xmlBuffer, DAVResource resource, Collection properties) throws SVNException {
        DAVXMLUtil.openNamespaceDeclarationTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "response", properties, xmlBuffer, false);
        SVNXMLUtil.openCDataTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "href", resource.getResourceURI().getRequestURI(), xmlBuffer);

        Collection badProperties = addPropstat(xmlBuffer, properties, resource, !getPropfindRequest().isPropNameRequest(), HTTP_STATUS_OK_LINE);
        if (badProperties != null && !badProperties.isEmpty()) {
            addPropstat(xmlBuffer, badProperties, resource, false, HTTP_NOT_FOUND_LINE);
        }

        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "response", xmlBuffer);
    }

    private Collection addPropstat(StringBuffer xmlBuffer, Collection properties, DAVResource resource, boolean addValue, String statusLine) throws SVNException {
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "propstat", SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "prop", SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);

        Collection badProperties = null;
        for (Iterator elements = properties.iterator(); elements.hasNext();) {
            DAVElement element = (DAVElement) elements.next();
            try {
                insertPropertyValue(element, resource, addValue, xmlBuffer);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_DAV_PROPS_NOT_FOUND) {
                    badProperties = badProperties == null ? new ArrayList() : badProperties;
                    badProperties.add(element);
                } else {
                    throw e;
                }
            }
        }

        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "prop", xmlBuffer);
        SVNXMLUtil.openCDataTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "status", statusLine, xmlBuffer);
        SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "propstat", xmlBuffer);
        return badProperties;
    }

    private void insertPropertyValue(DAVElement element, DAVResource resource, boolean addValue, StringBuffer xmlBuffer) throws SVNException {
        if (!resource.exists() && (element != DAVElement.VERSION_CONTROLLED_CONFIGURATION || element != DAVElement.BASELINE_RELATIVE_PATH)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PATH_NOT_FOUND, "Invalid path ''{0}''", 
                    resource.getResourceURI().getURI()), SVNLogType.NETWORK);
        }

        String prefix = (String) SVNXMLUtil.PREFIX_MAP.get(element.getNamespace());
        String name = element.getName();
        String value;
        boolean isCData = true;
        boolean isHref = false;

        if (!addValue) {
            value = null;
        } else if (element == DAVElement.VERSION_CONTROLLED_CONFIGURATION) {
            value = getVersionControlConfigurationProp(resource);
            isHref = true;
        } else if (element == DAVElement.RESOURCE_TYPE) {
            value = getResourceTypeIsCollection(resource) ? COLLECTION_RESOURCE_TYPE : "";
            isCData = false;
        } else if (element == DAVElement.BASELINE_RELATIVE_PATH) {
            value = getBaselineRelativePathProp(resource);
        } else if (element == DAVElement.REPOSITORY_UUID) {
            value = getRepositoryUUIDProp(resource);
        } else if (element == DAVElement.GET_CONTENT_LENGTH) {
            value = getContentLengthProp(resource);
        } else if (element == DAVElement.GET_CONTENT_TYPE) {
            value = getContentTypeProp(resource);
        } else if (element == DAVElement.CHECKED_IN) {
            value = getCheckedInProp(resource);
            isHref = true;
        } else if (element == DAVElement.VERSION_NAME) {
            value = getVersionNameProp(resource);
        } else if (element == DAVElement.BASELINE_COLLECTION) {
            value = getBaselineCollectionProp(resource);
            isHref = true;
        } else if (element == DAVElement.CREATION_DATE) {
            value = SVNDate.formatDate(getLastModifiedTime(resource));
        } else if (element == DAVElement.GET_LAST_MODIFIED) {
            value = SVNDate.formatRFC1123Date(getLastModifiedTime(resource));
        } else if (element == DAVElement.CREATOR_DISPLAY_NAME) {
            value = getCreatorDisplayNameProp(resource);
        } else if (element == DAVElement.DEADPROP_COUNT) {
            value = getDeadpropCountProp(resource);
        } else if (element == DAVElement.MD5_CHECKSUM) {
            value = getMD5ChecksumProp(resource);
        } else if (element == DAVElement.GET_ETAG) {
            value = getETag(resource);
        } else if (element == DAVElement.AUTO_VERSION) {
            value = getAutoVersionProp();
        } else if (element == DAVElement.DEADPROP_COUNT) {
            value = getDeadpropCountProp(resource);
        } else if (element == DAVElement.LOG) {
            value = getLogProp(resource);
        } else {
            value = getDeadProperty(element, resource);
        }

        if (value == null || value.length() == 0) {
            SVNXMLUtil.openXMLTag(prefix, name, SVNXMLUtil.XML_STYLE_SELF_CLOSING, null, xmlBuffer);
        } else if (isHref) {
            SVNXMLUtil.openXMLTag(prefix, name, SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
            SVNXMLUtil.openCDataTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "href", value, xmlBuffer);
            SVNXMLUtil.closeXMLTag(prefix, name, xmlBuffer);
        } else if (!isCData) {
            SVNXMLUtil.openXMLTag(prefix, name, SVNXMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
            xmlBuffer.append(value);
            SVNXMLUtil.closeXMLTag(prefix, name, xmlBuffer);
        } else {
            SVNXMLUtil.openCDataTag(prefix, name, value, xmlBuffer);
        }
    }

    private String getAutoVersionProp() {
        return DEFAULT_AUTOVERSION_LINE;
    }

    private Date getLastModifiedTime(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() == DAVResourceType.PRIVATE && resource.getResourceURI().getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        long revision;
        if (resource.getResourceURI().isBaseLined() && resource.getResourceURI().getType() == DAVResourceType.VERSION) {
            revision = resource.getRevision();
        } else
        if (resource.getResourceURI().getType() == DAVResourceType.REGULAR || resource.getResourceURI().getType() == DAVResourceType.WORKING
                || resource.getResourceURI().getType() == DAVResourceType.VERSION) {
            revision = resource.getCreatedRevision();
        } else {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        return resource.getRevisionDate(revision);
    }

    private String getBaselineCollectionProp(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() != DAVResourceType.VERSION || !resource.getResourceURI().isBaseLined()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        return DAVPathUtil.buildURI(resource.getResourceURI().getContext(), DAVResourceKind.BASELINE_COLL, resource.getRevision(), null, false);
    }

    private String getVersionNameProp(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() != DAVResourceType.VERSION && !resource.getResourceURI().isVersioned()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        if (resource.getResourceURI().getType() == DAVResourceType.PRIVATE && resource.getResourceURI().getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        if (resource.getResourceURI().isBaseLined()) {
            return String.valueOf(resource.getRevision());
        }
        return String.valueOf(resource.getCreatedRevision());
    }

    private String getContentLengthProp(DAVResource resource) throws SVNException {
        if (resource.isCollection() || resource.getResourceURI().isBaseLined()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        long fileSize = resource.getContentLength();
        return String.valueOf(fileSize);
    }

    private String getContentTypeProp(DAVResource resource) throws SVNException {
        return resource.getContentType();
    }

    private String getCreatorDisplayNameProp(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() == DAVResourceType.PRIVATE && resource.getResourceURI().getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
        }
        long revision;
        if (resource.getResourceURI().isBaseLined() && resource.getResourceURI().getType() == DAVResourceType.VERSION) {
            revision = resource.getRevision();
        } else
        if (resource.getResourceURI().getType() == DAVResourceType.REGULAR || resource.getResourceURI().getType() == DAVResourceType.WORKING
                || resource.getResourceURI().getType() == DAVResourceType.VERSION) {
            revision = resource.getCreatedRevision();
        } else {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        return resource.getAuthor(revision);
    }

    private String getBaselineRelativePathProp(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() != DAVResourceType.REGULAR) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        //path must be relative
        return DAVPathUtil.dropLeadingSlash(resource.getResourceURI().getPath());
    }

    private String getMD5ChecksumProp(DAVResource resource) throws SVNException {
        if (!resource.isCollection() && !resource.getResourceURI().isBaseLined()
                && (resource.getResourceURI().getType() == DAVResourceType.REGULAR || resource.getResourceURI().getType() == DAVResourceType.VERSION
                || resource.getResourceURI().getType() == DAVResourceType.WORKING)) {
            return resource.getMD5Checksum();
        }
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
        return null;
    }

    private String getETag(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() == DAVResourceType.PRIVATE && resource.getResourceURI().getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        return resource.getETag();
    }

    private String getRepositoryUUIDProp(DAVResource resource) throws SVNException {
        return resource.getRepositoryUUID(false);
    }

    private String getCheckedInProp(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() == DAVResourceType.PRIVATE && resource.getResourceURI().getKind() == DAVResourceKind.VCC) {
            return DAVPathUtil.buildURI(resource.getResourceURI().getContext(), DAVResourceKind.BASELINE, resource.getLatestRevision(), null, false);

        } else if (resource.getResourceURI().getType() != DAVResourceType.REGULAR) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        } else {
            return DAVPathUtil.buildURI(resource.getResourceURI().getContext(), DAVResourceKind.VERSION, resource.getCreatedRevision(), resource.getResourceURI().getPath(), false);
        }
    }

    private boolean getResourceTypeIsCollection(DAVResource resource) {
        return resource.isCollection();
    }

    private String getVersionControlConfigurationProp(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() != DAVResourceType.REGULAR) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        //Method doesn't use revision parameter at this moment
        return DAVPathUtil.buildURI(resource.getResourceURI().getContext(), DAVResourceKind.VCC, -1, resource.getResourceURI().getPath(), false);
    }

    private String getDeadpropCountProp(DAVResource resource) throws SVNException {
        int deadPropertiesCount = resource.getSVNProperties().size();
        return String.valueOf(deadPropertiesCount);
    }

    private String getLogProp(DAVResource resource) throws SVNException {
        return resource.getLog(resource.getCreatedRevision());
    }

    private String getDeadProperty(DAVElement element, DAVResource resource) throws SVNException {
        return resource.getProperty(convertDAVElementToDeadProperty(element));
    }

    //Next four methods we can use for dead properties only

    private Collection convertDeadPropertiesToDAVElements(SVNProperties deadProperties) throws SVNException {
        Collection elements = new ArrayList();
        for (Iterator iterator = deadProperties.nameSet().iterator(); iterator.hasNext();) {
            String propertyName = (String) iterator.next();
            elements.add(convertDeadPropertyToDAVElement(propertyName));
        }
        return elements;
    }

    private DAVElement convertDeadPropertyToDAVElement(String property) throws SVNException {
        if (!SVNProperty.isRegularProperty(property)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Unrecognized property prefix ''{0}''", property), SVNLogType.NETWORK);
        }
        String namespace = DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE;
        if (SVNProperty.isSVNProperty(property)) {
            namespace = DAVElement.SVN_SVN_PROPERTY_NAMESPACE;
        }
        property = SVNProperty.shortPropertyName(property);
        return DAVElement.getElement(namespace, property);
    }

    private String convertDAVElementToDeadProperty(DAVElement element) throws SVNException {
        return convertDAVElementToDeadProperty(element.getNamespace(), element.getName());
    }

    private String convertDAVElementToDeadProperty(String namespace, String name) throws SVNException {
        if (DAVElement.SVN_SVN_PROPERTY_NAMESPACE.equals(namespace)) {
            return SVNProperty.SVN_PREFIX + name;
        } else if (DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE.equals(namespace)) {
            return name;
        }
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Unrecognized namespace ''{0}''", namespace), SVNLogType.NETWORK);
        return null;
    }

    private static class DAVInsertPropAction {
        public static final DAVInsertPropAction NOT_DEF = new DAVInsertPropAction();
        public static final DAVInsertPropAction NOT_SUPP = new DAVInsertPropAction();
        public static final DAVInsertPropAction INSERT_VALUE = new DAVInsertPropAction();
        public static final DAVInsertPropAction INSERT_NAME = new DAVInsertPropAction();
        public static final DAVInsertPropAction INSERT_SUPPORTED = new DAVInsertPropAction();
        
        private DAVInsertPropAction() {
        }
    }
}
