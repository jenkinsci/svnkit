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
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceKind;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceUtil;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.xml.sax.Attributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVPropfindHanlder extends ServletDAVHandler {

    private static final Set PROP_ELEMENTS = new HashSet();

    private static final DAVElement PROPFIND = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "propfind");
    private static final DAVElement PROPNAME = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "propname");
    private static final DAVElement ALLPROP = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "allprop");

    private Collection myDAVElements;
    private Map myNamespaces;
    private int myCurrentStatus;

    static {
        PROP_ELEMENTS.add(DAVElement.HREF);
        PROP_ELEMENTS.add(DAVElement.STATUS);
        PROP_ELEMENTS.add(DAVElement.BASELINE);
        PROP_ELEMENTS.add(DAVElement.BASELINE_COLLECTION);
        PROP_ELEMENTS.add(DAVElement.COLLECTION);
        PROP_ELEMENTS.add(DAVElement.VERSION_NAME);
        PROP_ELEMENTS.add(DAVElement.GET_CONTENT_LENGTH);
        PROP_ELEMENTS.add(DAVElement.CREATION_DATE);
        PROP_ELEMENTS.add(DAVElement.CREATOR_DISPLAY_NAME);
        PROP_ELEMENTS.add(DAVElement.BASELINE_RELATIVE_PATH);
        PROP_ELEMENTS.add(DAVElement.MD5_CHECKSUM);
        PROP_ELEMENTS.add(DAVElement.REPOSITORY_UUID);
        PROP_ELEMENTS.add(DAVElement.CHECKED_IN);
        PROP_ELEMENTS.add(DAVElement.RESOURCE_TYPE);
        PROP_ELEMENTS.add(DAVElement.VERSION_CONTROLLED_CONFIGURATION);
    }

    public DAVPropfindHanlder(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if ((element == ALLPROP || element == DAVElement.PROP || element == PROPNAME) && parent != PROPFIND) {
            invalidXML();
        } else if (PROP_ELEMENTS.contains(element) && parent == DAVElement.PROP) {
            if (!getNamespaces().containsKey(element.getNamespace())) {
                getNamespaces().put(element.getNamespace(), "lp" + (getNamespaces().size() + 1));
            }
            getDAVProperties().add(element);
        } else if (element == ALLPROP) {
            getDAVProperties().add(ALLPROP);
        } else if (element == PROPNAME) {
            getDAVProperties().add(PROPNAME);
        }
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {

    }

    public Collection getDAVProperties() {
        if (myDAVElements == null) {
            myDAVElements = new ArrayList();
        }
        return myDAVElements;
    }

    private Map getNamespaces() {
        if (myNamespaces == null) {
            myNamespaces = new HashMap();
        }
        return myNamespaces;
    }

    public int getCurrentStatus() {
        return myCurrentStatus;
    }

    public void setCurrentStatus(int currentStatus) {
        myCurrentStatus = currentStatus;
    }

    public void execute() throws SVNException {
        String label = getRequestHeader(LABEL_HEADER);
        DAVResource resource = getRepositoryManager().createDAVResource(getRequestContext(), getRequestURI(), label, false);

        getRequestDepth(DAVDepth.DEPTH_INFINITY);
        //TODO: native subversion examine if DEPTH_INFINITE is allowed

        readInput(getRequestInputStream());

        StringBuffer body = new StringBuffer();

        try {
            generatePropertiesResponse(body, resource);
            setResponseStatus(SC_MULTISTATUS);
            setDefaultResponseHeaders(resource);
            getResponseWriter().write(body.toString());
        } catch (SVNException svne) {
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    private void generatePropertiesResponse(StringBuffer xmlBuffer, DAVResource resource) throws SVNException {
        appendXMLHeader(DAV_NAMESPACE_PREFIX, "multistatus", getNamespaces().keySet(), xmlBuffer);
        openNamespaceTag(DAV_NAMESPACE_PREFIX, "response", XML_STYLE_NORMAL, getNamespaces(), xmlBuffer);
        openXMLTag(DAV_NAMESPACE_PREFIX, "href", XML_STYLE_NORMAL, null, xmlBuffer);
        String uri = resource.getContext() + resource.getURI();
        if (!uri.endsWith("/")) {
            uri = uri + "/";
        }
        xmlBuffer.append(uri);
        closeXMLTag(DAV_NAMESPACE_PREFIX, "href", xmlBuffer);
        openXMLTag(DAV_NAMESPACE_PREFIX, "propstat", XML_STYLE_NORMAL, null, xmlBuffer);
        openXMLTag(DAV_NAMESPACE_PREFIX, "prop", XML_STYLE_NORMAL, null, xmlBuffer);
        for (Iterator elements = getDAVProperties().iterator(); elements.hasNext();) {
            DAVElement element = (DAVElement) elements.next();
            String prefix = getNamespaces().get(element.getNamespace()).toString();
            String name = element.getName();
            String value = getPropertyValue(resource, element);
            if (value == null || "".equals(value)) {
                openXMLTag(prefix, name, XML_STYLE_SELF_CLOSING, null, xmlBuffer);
            } else {
                openXMLTag(prefix, name, XML_STYLE_NORMAL, null, xmlBuffer);
                xmlBuffer.append(value);
                closeXMLTag(prefix, name, xmlBuffer);
            }
        }
        closeXMLTag(DAV_NAMESPACE_PREFIX, "prop", xmlBuffer);
        openCDataTag(DAV_NAMESPACE_PREFIX, "status", HTTP_STATUS_OK_STRING, xmlBuffer);
        closeXMLTag(DAV_NAMESPACE_PREFIX, "propstat", xmlBuffer);
        closeXMLTag(DAV_NAMESPACE_PREFIX, "response", xmlBuffer);
        closeXMLTag(DAV_NAMESPACE_PREFIX, "multistatus", xmlBuffer);
    }

    private String getPropertyValue(DAVResource resource, DAVElement element) throws SVNException {
        if (!resource.exists() && (element != DAVElement.VERSION_CONTROLLED_CONFIGURATION || element != DAVElement.BASELINE_RELATIVE_PATH)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PATH_NOT_FOUND, "Invalid path ''{0}''", resource.getURI()));
        }

        if (element == DAVElement.VERSION_CONTROLLED_CONFIGURATION) {
            if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
                //prop not supported
                return null;
            }

            //Method doesn't use revision parameter at this moment
            String uri = SVNEncodingUtil.uriEncode(DAVResourceUtil.buildURI(resource.getContext(), resource.getPath(), DAVResourceKind.VCC, -1, resource.getParameterPath()));
            return addHrefTags(uri);

        } else if (element == DAVElement.RESOURCE_TYPE) {

            return resource.isCollection() ? "<D:collection/>" : null;

        } else if (element == DAVElement.BASELINE_RELATIVE_PATH) {
            if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
                //prop not supported
                return null;
            }
            String parameterPath = resource.getParameterPath();
            if (parameterPath.startsWith("/")) {
                //path must be relative
                parameterPath = parameterPath.substring("/".length());
            }
            return parameterPath;
        } else if (element == DAVElement.REPOSITORY_UUID) {
            return SVNEncodingUtil.xmlEncodeCDATA(resource.getRepositoryUUID(true));
        } else if (element == DAVElement.CHECKED_IN) {
            if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
                long latestRevision = resource.getLatestRevision();
                String uri = SVNEncodingUtil.uriEncode(DAVResourceUtil.buildURI(resource.getContext(), resource.getPath(), DAVResourceKind.BASELINE, latestRevision, null));
                return addHrefTags(uri);
            } else if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
                //prop not supported
                return null;
            } else {
                resource.getDirEntry();
            }
        } else if (element == DAVElement.VERSION_NAME) {
            if ((resource.getType() != DAVResource.DAV_RESOURCE_TYPE_VERSION) && !resource.isVersioned()) {
                //prop not supported
                return null;
            }
            if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
                //prop not supported
                return null;
            }
            if (resource.isBaseLined()) {
                return String.valueOf(resource.getRevision());
            }
            //TODO: get file created revision
        } else if (element == DAVElement.BASELINE_COLLECTION) {
            if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION || resource.isBaseLined()) {
                //prop not supported
                return null;
            }
            String uri = SVNEncodingUtil.uriEncode(DAVResourceUtil.buildURI(resource.getContext(), resource.getPath(), DAVResourceKind.BASELINE_COLL, resource.getRevision(), null));
            return addHrefTags(uri);
        } else if (element == DAVElement.CREATOR_DISPLAY_NAME) {
            return resource.getAuthor();
        } else if (element == DAVElement.DEADPROP_COUNT){
            //TODO: implement this.            
        } else if ( element == DAVElement.MD5_CHECKSUM){
            //TODO: implement this.                        
        }
        return null;
    }
}
