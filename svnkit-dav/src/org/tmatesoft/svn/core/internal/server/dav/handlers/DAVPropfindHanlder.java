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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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

    //TODO: specify all myDAVElements for PROP_ELEMENTS
    static {
        PROP_ELEMENTS.add(DAVElement.HREF.getName());
        PROP_ELEMENTS.add(DAVElement.STATUS.getName());
        PROP_ELEMENTS.add(DAVElement.BASELINE.getName());
        PROP_ELEMENTS.add(DAVElement.BASELINE_COLLECTION.getName());
        PROP_ELEMENTS.add(DAVElement.COLLECTION.getName());
        PROP_ELEMENTS.add(DAVElement.VERSION_NAME.getName());
        PROP_ELEMENTS.add(DAVElement.GET_CONTENT_LENGTH.getName());
        PROP_ELEMENTS.add(DAVElement.CREATION_DATE.getName());
        PROP_ELEMENTS.add(DAVElement.CREATOR_DISPLAY_NAME.getName());
        PROP_ELEMENTS.add(DAVElement.BASELINE_RELATIVE_PATH.getName());
        PROP_ELEMENTS.add(DAVElement.MD5_CHECKSUM.getName());
        PROP_ELEMENTS.add(DAVElement.REPOSITORY_UUID.getName());
        PROP_ELEMENTS.add(DAVElement.CHECKED_IN.getName());
        PROP_ELEMENTS.add(DAVElement.RESOURCE_TYPE.getName());
        PROP_ELEMENTS.add(DAVElement.VERSION_CONTROLLED_CONFIGURATION.getName());
    }

    public DAVPropfindHanlder(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if ((element == ALLPROP || element == DAVElement.PROP || element == PROPNAME) && parent != PROPFIND) {
            invalidXML();
        } else if (PROP_ELEMENTS.contains(element.getName()) && PROPERTY.getName().equals(parent.getName())) {
            String namespace = attrs.getValue("xmlns");
            if (!getNamespaces().containsKey(namespace)) {
                getNamespaces().put(namespace, "lp" + getNamespaces().size());
            }
            getDAVPropetries().add(DAVElement.getElement("", element.getName()));
        }
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == ALLPROP) {
            getDAVPropetries().add(ALLPROP.getName());
        } else if (element == PROPNAME) {
            getDAVPropetries().add(PROPNAME.getName());
//        } else if (PROP_ELEMENTS.contains(element.getName())) {
//            getDAVPropetries().add(element);
//            if (!getNamespaces().containsKey(element.getNamespace())) {
//                getNamespaces().put(element.getNamespace(), "lp" + getNamespaces().size());
//            }
        }
    }

    public Collection getDAVPropetries() {
        if (myDAVElements == null) {
            myDAVElements = new HashSet();
        }
        return myDAVElements;
    }

    private Map getNamespaces() {
        if (myNamespaces == null) {
            myNamespaces = new HashMap();
        }
        return myNamespaces;
    }

    public void execute() throws SVNException {
        String label = getRequestHeader(LABEL_HEADER);
        DAVResource resource = getRepositoryManager().createDAVResource(getRequestURI(), label, false);

        getRequestDepth(DAVDepth.DEPTH_INFINITY);
        //TODO: native subversion examine if DEPTH_INFINITE is allowed

        readInput(getRequestInputStream());

        StringBuffer body = new StringBuffer();

        generatePropertiesResponse(body, resource);

        setDefaultResponseHeaders();
        setResponseHeaders(resource);

        try {
            getResponseWriter().write(body.toString());
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    private void setResponseHeaders(DAVResource resource) throws SVNException {
//        setResponseHeader("Last-Modified", resource.getLastModified().toString());
//        setResponseHeader("ETag", resource.getETag());
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            setResponseHeader("Cache-Control", "max-age=604800");
        }
        //TODO: Specify all headers for propfind
    }


    private void generatePropertiesResponse(StringBuffer body, DAVResource resource) throws SVNException {
        startMultistatus(body);
        startResponseTag(body, resource.getURI());
        startPropstat(body);
        Iterator iterator = getDAVPropetries().iterator();
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
            body.append("\"").append(iterator.toString()).append("\"");
            i++;
        }
        body.append(">");
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
            body.append(getNamespaces().get(currentNamespace));
            body.append("=\"");
            body.append(currentNamespace);
            body.append("\" ");
        }
        body.append(">");
        body.append("<D:href>");
        body.append(uri);
        body.append("</D:href>");
    }

    private void finishResponseTag(StringBuffer body) {
        body.append("</D:response>");
    }

    private void startPropstat(StringBuffer body) {
        body.append("<D:propstat><D:prop>");
    }

    private void finishPropstat(StringBuffer body) {
        body.append("</D:prop>");
        body.append("<D:status>HTTP/1.1 200 OK</D:status>");
        body.append("</propstat>");
    }

    private String getPropertyValue(DAVResource resource, DAVElement element) throws SVNException {
        if (!resource.exists() && (element != DAVElement.VERSION_CONTROLLED_CONFIGURATION || element != DAVElement.BASELINE_RELATIVE_PATH)) {
            //prop not supported
        }

        if (element == DAVElement.VERSION_CONTROLLED_CONFIGURATION) {
            if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
                //prop not supported
            }

            //Method doesn't use revision parameter at this moment
            return DAVResourceUtil.buildURI(resource.getRepositoryName(), resource.getPath(), DAVResourceKind.VCC, -1, resource.getParameterPath(), true);

        } else if (element == DAVElement.RESOURCE_TYPE) {

            return resource.isCollection() ? "<D:collection/>" : null;

        } else if (element == DAVElement.BASELINE_RELATIVE_PATH) {
            if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
                //prop not supported
            }
            return resource.getParameterPath();
        } else if (element == DAVElement.REPOSITORY_UUID) {
            return resource.getRepository().getRepositoryUUID(true);
        } else if (element == DAVElement.CHECKED_IN) {
            if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
                long latestRevision = resource.getRepository().getLatestRevision();
                return DAVResourceUtil.buildURI(resource.getRepositoryName(), resource.getPath(), DAVResourceKind.BASELINE, latestRevision, null, true);
            } else if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
                //prop not supported
            } else {
                //TODO: get file created revision
            }
        } else if (element == DAVElement.VERSION_NAME) {
            if ((resource.getType() != DAVResource.DAV_RESOURCE_TYPE_VERSION) && !resource.isVersioned()) {
                //prop not supported                
            }
            if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
                //prop not supported
            }
            if (resource.isBaseLined()) {
                return String.valueOf(resource.getRevision());
            } else {
                //TODO: get file created revision
            }
        } else if (element == DAVElement.BASELINE_COLLECTION) {
            if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION || resource.isBaseLined()) {
                //prop not supported
            }
            return DAVResourceUtil.buildURI(resource.getRepositoryName(), resource.getPath(), DAVResourceKind.BASELINE_COLL, resource.getRevision(), null, true);
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
    }

    private void finishPropertyTag(StringBuffer body, String namespaceIndex, String name) {
        body.append("</");
        body.append(namespaceIndex);
        body.append(":");
        body.append(name);
        body.append(">");
    }
}
