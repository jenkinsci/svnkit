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
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

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

    private static final DAVElement PROPERTY = DAVElement.getElement("", "prop");

    private Collection myDAVElements;
    private Map myNamespaces;
    private String myCurrentNamespace;

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

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        setCurrentNamespace(uri);
        super.startElement(uri, localName, qName, attributes);
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        setCurrentNamespace(null);
        super.endElement(uri, localName, qName);
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (getCurrentNamespace() == null) {
            invalidXML();
        }
        DAVElement correctElement = DAVElement.getElement(getCurrentNamespace(), element.getName());
        String parentName = "";
        if (parent != null) {
            parentName = parent.getName();
        }
        if ((correctElement == ALLPROP || correctElement == PROPERTY || correctElement == PROPNAME) && !PROPFIND.getName().equals(parentName)) {
            invalidXML();
        } else if (PROP_ELEMENTS.contains(correctElement) && PROPERTY.getName().equals(parentName)) {
            if (!getNamespaces().containsKey(getCurrentNamespace())) {
                getNamespaces().put(getCurrentNamespace(), "lp" + (getNamespaces().size() + 1));
            }
            getDAVProperties().add(correctElement);
        } else if (correctElement == ALLPROP) {
            getDAVProperties().add(ALLPROP);
        } else if (correctElement == PROPNAME) {
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

    private String getCurrentNamespace() {
        return myCurrentNamespace;
    }

    private void setCurrentNamespace(String currentNamspace) {
        myCurrentNamespace = currentNamspace;
    }

    public void execute() throws SVNException {
        String label = getRequestHeader(LABEL_HEADER);
        DAVResource resource = getRepositoryManager().createDAVResource(getRequestContext(), getRequestURI(), label, false);

        getRequestDepth(DAVDepth.DEPTH_INFINITY);
        //TODO: native subversion examine if DEPTH_INFINITE is allowed

        readInput(getRequestInputStream());

        StringBuffer body = new StringBuffer();

        generatePropertiesResponse(body, resource);

        setResponseStatus(SC_MULTISTATUS);
        setDefaultResponseHeaders();
        setResponseHeaders(resource);

        try {
            getResponseWriter().write(body.toString());
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    private void setResponseHeaders(DAVResource resource) throws SVNException {
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            setResponseHeader("Cache-Control", "max-age=604800");
        }
        //TODO: Specify all headers for propfind
    }


    private void generatePropertiesResponse(StringBuffer body, DAVResource resource) throws SVNException {
        startMultistatus(body);
        startResponseTag(body, resource.getURI());
        startPropstat(body);
        Iterator iterator = getDAVProperties().iterator();
        while (iterator.hasNext()) {
            DAVElement element = (DAVElement) iterator.next();
            String namespaceIndex = getNamespaces().get(element.getNamespace()).toString();
            String name = element.getName();
            String value = getPropertyValue(resource, element);
            if (value == null || "".equals(value)) {
                startPropertyTag(body, namespaceIndex, name, true);
            } else {
                startPropertyTag(body, namespaceIndex, name, false);
                body.append(value);
                finishPropertyTag(body, namespaceIndex, name);
            }
        }
        finishPropstat(body);
        finishResponseTag(body);
        finishMultistatus(body);

    }

    private void startMultistatus(StringBuffer body) {
        body.append(XML_HEADER);
        body.append("<D:multistatus xmlns:D=\"DAV:\"");
        Iterator iterator = getNamespaces().keySet().iterator();
        int i = 0;
        while (iterator.hasNext()) {
            body.append(" xmlns:ns").append(i).append("=");
            body.append("\"").append(iterator.next().toString()).append("\"");
            i++;
        }
        body.append(">\n");
    }

    private void finishMultistatus(StringBuffer body) {
        body.append("</D:multistatus>");
    }

    private void startResponseTag(StringBuffer body, String uri) {
        body.append("<D:response");
        Iterator iterator = getNamespaces().keySet().iterator();
        while (iterator.hasNext()) {
            String currentNamespace = iterator.next().toString();
            body.append(" xmlns:");
            body.append(getNamespaces().get(currentNamespace).toString());
            body.append("=\"");
            body.append(currentNamespace);
            body.append("\"");
        }
        body.append(">\n");
        body.append("<D:href>");
        if (!uri.endsWith("/")) {
            uri = uri + "/";
        }
        body.append(uri);
        body.append("</D:href>\n");
    }

    private void finishResponseTag(StringBuffer body) {
        body.append("</D:response>\n");
    }

    private void startPropstat(StringBuffer body) {
        body.append("<D:propstat>\n<D:prop>\n");
    }

    private void finishPropstat(StringBuffer body) {
        body.append("</D:prop>\n");
        body.append("<D:status>HTTP/1.1 200 OK</D:status>\n");
        body.append("</D:propstat>\n");
    }

    private String getPropertyValue(DAVResource resource, DAVElement element) throws SVNException {
        if (!resource.exists() && (element != DAVElement.VERSION_CONTROLLED_CONFIGURATION || element != DAVElement.BASELINE_RELATIVE_PATH)) {
            return null;
        }

        if (element == DAVElement.VERSION_CONTROLLED_CONFIGURATION) {
            if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
                //prop not supported
                return null;
            }

            //Method doesn't use revision parameter at this moment
            return DAVResourceUtil.buildURI(resource.getContext(), resource.getRepositoryName(), resource.getPath(), DAVResourceKind.VCC, -1, resource.getParameterPath(), true);

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
            return resource.getRepository().getRepositoryUUID(true);
        } else if (element == DAVElement.CHECKED_IN) {
            if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
                long latestRevision = resource.getRepository().getLatestRevision();
                return DAVResourceUtil.buildURI(resource.getContext(), resource.getRepositoryName(), resource.getPath(), DAVResourceKind.BASELINE, latestRevision, null, true);
            } else if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
                //prop not supported
                return null;
            } else {
                //TODO: get file created revision
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
            } else {
                //TODO: get file created revision
            }
        } else if (element == DAVElement.BASELINE_COLLECTION) {
            if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION || resource.isBaseLined()) {
                //prop not supported
                return null;
            }
            return DAVResourceUtil.buildURI(resource.getContext(), resource.getRepositoryName(), resource.getPath(), DAVResourceKind.BASELINE_COLL, resource.getRevision(), null, true);
        }
        return null;
    }

    private void startPropertyTag(StringBuffer body, String namespaceIndex, String name, boolean isEmpty) {
        body.append("<");
        body.append(namespaceIndex);
        body.append(":");
        body.append(name);
        body.append(isEmpty ? "/" : "");
        body.append(">");
        body.append(isEmpty ? "\n" : "");
    }

    private void finishPropertyTag(StringBuffer body, String namespaceIndex, String name) {
        body.append("</");
        body.append(namespaceIndex);
        body.append(":");
        body.append(name);
        body.append(">\n");
    }
}
