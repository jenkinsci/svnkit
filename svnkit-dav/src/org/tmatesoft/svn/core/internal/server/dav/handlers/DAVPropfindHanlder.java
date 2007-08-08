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

import org.tmatesoft.svn.core.SVNDirEntry;
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
    private static final DAVElement GET_CONTENT_TYPE = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getcontenttype");
    private static final DAVElement GET_LAST_MODIFIED = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getlastmodified");
    private static final DAVElement GET_ETAG = DAVElement.getElement(DAVElement.DAV_NAMESPACE, "getetag");

    private static final String DEFAULT_AUTOVERSION_LINE = "DAV:checkout-checkin";

    private Collection myDAVElements;

    static {
        PROP_ELEMENTS.add(DAVElement.BASELINE_COLLECTION);
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
        PROP_ELEMENTS.add(DAVElement.DEADPROP_COUNT);
        PROP_ELEMENTS.add(GET_ETAG);
        PROP_ELEMENTS.add(GET_LAST_MODIFIED);
        PROP_ELEMENTS.add(GET_CONTENT_TYPE);
    }

    public DAVPropfindHanlder(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if ((element == ALLPROP || element == DAVElement.PROP || element == PROPNAME) && parent != PROPFIND) {
            invalidXML();
        } else if (PROP_ELEMENTS.contains(element) && parent == DAVElement.PROP) {
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

    public void execute() throws SVNException {
        String label = getRequestHeader(LABEL_HEADER);
        DAVResource resource = getRepositoryManager().createDAVResource(getRequestContext(), getRequestURI(), label, false);

        readInput(getRequestInputStream());

        StringBuffer body = new StringBuffer();
        DAVDepth depth = getRequestDepth(DAVDepth.DEPTH_INFINITY);

        generatePropertiesResponse(body, resource, depth);
        setResponseStatus(SC_MULTISTATUS);
        setDefaultResponseHeaders(resource);

        try {
            getResponseWriter().write(body.toString());
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    private void generatePropertiesResponse(StringBuffer xmlBuffer, DAVResource resource, DAVDepth depth) throws SVNException {
        appendXMLHeader(DAV_NAMESPACE_PREFIX, "multistatus", getDAVProperties(), xmlBuffer);
        //TODO: Handling PROPNAME element
        if (getDAVProperties().size() == 1 && getDAVProperties().contains(ALLPROP)) {
            generateAllPropResponse(xmlBuffer, resource, depth);
        } else {
            generatePropResponse(xmlBuffer, resource, depth);
            closeXMLTag(DAV_NAMESPACE_PREFIX, "multistatus", xmlBuffer);
        }
    }

    private void generateAllPropResponse(StringBuffer xmlBuffer, DAVResource resource, DAVDepth depth) throws SVNException {
        getDAVProperties().clear();
        getDAVProperties().addAll(PROP_ELEMENTS);
        generatePropResponse(xmlBuffer, resource, depth);
    }


    private void generatePropResponse(StringBuffer xmlBuffer, DAVResource resource, DAVDepth depth) throws SVNException {
        addResponse(xmlBuffer, resource);
        if (depth != DAVDepth.DEPTH_ZERO && resource.isCollection()) {
            DAVDepth newDepth = DAVDepth.decreaseDepth(depth);
            for (Iterator entriesIterator = resource.getEntries().iterator(); entriesIterator.hasNext();) {
                SVNDirEntry entry = (SVNDirEntry) entriesIterator.next();
                StringBuffer entryURI = new StringBuffer();
                entryURI.append(resource.getURI());
                entryURI.append(resource.getURI().endsWith("/") ? "" : "/");
                entryURI.append(entry.getName());
                DAVResource currentResource = new DAVResource(resource.getRepository(), resource.getContext(), entryURI.toString(), null, false);
                generatePropResponse(xmlBuffer, currentResource, newDepth);
            }
        }
    }

    private void addResponse(StringBuffer xmlBuffer, DAVResource resource) throws SVNException {
        String resultStatusCode = HTTP_STATUS_OK_LINE;
        Map prefixMapping = openNamespacesDeclarationTag(DAV_NAMESPACE_PREFIX, "response", XML_STYLE_NORMAL, getDAVProperties(), xmlBuffer);
        openXMLTag(DAV_NAMESPACE_PREFIX, "href", XML_STYLE_NORMAL, null, xmlBuffer);
        String uri = resource.getContext() + resource.getURI();
        xmlBuffer.append(uri);
        closeXMLTag(DAV_NAMESPACE_PREFIX, "href", xmlBuffer);
        openXMLTag(DAV_NAMESPACE_PREFIX, "propstat", XML_STYLE_NORMAL, null, xmlBuffer);
        openXMLTag(DAV_NAMESPACE_PREFIX, "prop", XML_STYLE_NORMAL, null, xmlBuffer);
        for (Iterator elements = getDAVProperties().iterator(); elements.hasNext();) {
            DAVElement element = (DAVElement) elements.next();
            String prefix = prefixMapping.get(element.getNamespace()).toString();
            String name = element.getName();
            String value = getPropertyValue(resource, element);
            if (value != null) {
                if ("".equals(value)) {
                    openXMLTag(prefix, name, XML_STYLE_SELF_CLOSING, null, xmlBuffer);
                } else {
                    openXMLTag(prefix, name, XML_STYLE_NORMAL, null, xmlBuffer);
                    xmlBuffer.append(value);
                    closeXMLTag(prefix, name, xmlBuffer);
                }
            } else {
                resultStatusCode = HTTP_NOT_FOUND_LINE;
            }
        }
        closeXMLTag(DAV_NAMESPACE_PREFIX, "prop", xmlBuffer);
        openCDataTag(DAV_NAMESPACE_PREFIX, "status", resultStatusCode, xmlBuffer);
        closeXMLTag(DAV_NAMESPACE_PREFIX, "propstat", xmlBuffer);
        closeXMLTag(DAV_NAMESPACE_PREFIX, "response", xmlBuffer);
    }

    protected Map openNamespacesDeclarationTag(String prefix, String tagName, int style, Collection incomingDAVElements, StringBuffer target) {
        Map prefixMapping = new HashMap();
        target.append("<");
        target.append(prefix);
        target.append(":");
        target.append(tagName);
        if (incomingDAVElements != null) {
            boolean useDeadpropNamespaces = false;
            for (Iterator iterator = incomingDAVElements.iterator(); iterator.hasNext();) {
                DAVElement currentElement = (DAVElement) iterator.next();
                String currentNamespace = currentElement.getNamespace();
                boolean isDeadprop = isDeadprop(currentNamespace);
                useDeadpropNamespaces = isDeadprop || useDeadpropNamespaces;
                if (currentNamespace != null && currentNamespace.length() > 0 && !isDeadprop
                        && !prefixMapping.keySet().contains(currentNamespace)) {
                    String currentPrefix = "lp" + (prefixMapping.size() + 1);
                    addXMLNamespaceAttr(currentPrefix, currentNamespace, prefixMapping, target);
                }
            }
            if (useDeadpropNamespaces) {
                addXMLNamespaceAttr(SVN_CUSTOM_PROPERTY_PREFIX, DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE, prefixMapping, target);
                addXMLNamespaceAttr(SVN_SVN_PROPERTY_PREFIX, DAVElement.SVN_SVN_PROPERTY_NAMESPACE, prefixMapping, target);
            }
        }
        if (style == XML_STYLE_SELF_CLOSING) {
            target.append("/");
        }
        target.append(">");
        return prefixMapping;
    }

    private void addXMLNamespaceAttr(String prefix, String namespace, Map prefixMapping, StringBuffer target) {
        prefixMapping.put(namespace, prefix);
        target.append(" xmlns:");
        target.append(prefix);
        target.append("=\"");
        target.append(namespace);
        target.append("\"");
    }

    private boolean isDeadprop(String namespace) {
        //TODO:SVN_DAV_PROPERTY_NAMESPACE equals some live properties namespace. Now we skip it, but native svn doesn't do it.
        return DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE.equals(namespace) || DAVElement.SVN_SVN_PROPERTY_NAMESPACE.equals(namespace);
    }

    private String getPropertyValue(DAVResource resource, DAVElement element) throws SVNException {
        if (!resource.exists() && (element != DAVElement.VERSION_CONTROLLED_CONFIGURATION || element != DAVElement.BASELINE_RELATIVE_PATH)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PATH_NOT_FOUND, "Invalid path ''{0}''", resource.getURI()));
        }
        String value = null;
        if (element == DAVElement.VERSION_CONTROLLED_CONFIGURATION) {
            value = getVersionControlConfigurationProp(resource);
        } else if (element == DAVElement.RESOURCE_TYPE) {
            value = getResourceTypeProp(resource);
        } else if (element == DAVElement.BASELINE_RELATIVE_PATH) {
            value = getBaselineRelativePathProp(resource);
        } else if (element == DAVElement.REPOSITORY_UUID) {
            value = getRepositoryUUIDProp(resource);
        } else if (element == DAVElement.GET_CONTENT_LENGTH) {
            value = getContentLengthProp(resource);
        } else if (element == GET_CONTENT_TYPE) {
            value = getContentTypeProp(resource);
        } else if (element == DAVElement.CHECKED_IN) {
            value = getCheckedInProp(resource);
        } else if (element == DAVElement.VERSION_NAME) {
            value = getVersionNameProp(resource);
        } else if (element == DAVElement.BASELINE_COLLECTION) {
            value = getBaselineCollectionProp(resource);
        } else if (element == DAVElement.CREATION_DATE) {
            value = getCreationDateProp(resource);
        } else if (element == GET_LAST_MODIFIED) {
            value = getLastModifiedProp(resource);
        } else if (element == DAVElement.CREATOR_DISPLAY_NAME) {
            value = getCreatorDisplayNameProp(resource);
        } else if (element == DAVElement.DEADPROP_COUNT) {
            value = getDeadpropCountProp(resource);
        } else if (element == DAVElement.MD5_CHECKSUM) {
            value = getMD5ChecksumProp(resource);
        } else if (element == GET_ETAG) {
            value = getETag(resource);
        } else if (element == DAVElement.AUTO_VERSION) {
            value = getAutoVersionProp();
        } else if (element == DAVElement.DEADPROP_COUNT) {
            value = getDeadpropCountProp(resource);
        }
        return value;
    }

    private String getAutoVersionProp() {
        return DEFAULT_AUTOVERSION_LINE;
    }

    private String getLastModifiedProp(DAVResource resource) throws SVNException {
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            return null;
        }
        long revision;
        if (resource.isBaseLined() && resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            revision = resource.getRevision();
        } else
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_REGULAR || resource.getType() == DAVResource.DAV_RESOURCE_TYPE_WORKING
                || resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            revision = resource.getCommitedRevision();
        } else {
            return null;
        }
        return resource.getLastModified(revision);
    }

    private String getBaselineCollectionProp(DAVResource resource) {
        if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_VERSION || !resource.isBaseLined()) {
            return null;
        }
        String uri = SVNEncodingUtil.uriEncode(DAVResourceUtil.buildURI(resource.getContext(), resource.getPath(), DAVResourceKind.BASELINE_COLL, resource.getRevision(), null));
        return addHrefTags(uri);
    }

    private String getVersionNameProp(DAVResource resource) {
        if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_VERSION && !resource.isVersioned()) {
            return null;
        }
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            return null;
        }
        if (resource.isBaseLined()) {
            return String.valueOf(resource.getRevision());
        }
        return String.valueOf(resource.getCommitedRevision());
    }

    private String getContentLengthProp(DAVResource resource) throws SVNException {
        if (resource.isCollection() || resource.isBaseLined()) {
            return null;
        }
        long fileSize = resource.getContentLength();
        return String.valueOf(fileSize);
    }

    private String getContentTypeProp(DAVResource resource) {
        if (resource.isBaseLined() && resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            return null;
        }
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            return null;
        }
        //TODO: native svn examine if client is not (?!) svn client to use request's header 'content type' 
        if (resource.isCollection()) {
            return DEFAULT_CONTENT_TYPE;
        }
        return resource.getContentType();
    }

    private String getCreationDateProp(DAVResource resource) throws SVNException {
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            return null;
        }
        //TODO: Native svn acts this way, mb there's another one.
        return getLastModifiedProp(resource);
    }

    private String getCreatorDisplayNameProp(DAVResource resource) throws SVNException {
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            return null;
        }
        long revision;
        if (resource.isBaseLined() && resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            revision = resource.getRevision();
        } else
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_REGULAR || resource.getType() == DAVResource.DAV_RESOURCE_TYPE_WORKING
                || resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            revision = resource.getCommitedRevision();
        } else {
            return null;
        }
        return resource.getLastAuthor(revision);
    }

    private String getBaselineRelativePathProp(DAVResource resource) {
        if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
            return null;
        }
        String parameterPath = resource.getParameterPath();
        if (parameterPath.startsWith("/")) {
            //path must be relative
            parameterPath = parameterPath.substring("/".length());
        }
        return parameterPath;
    }

    private String getMD5ChecksumProp(DAVResource resource) {
        if (!resource.isCollection() && !resource.isBaseLined()
                && (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_REGULAR || resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION
                || resource.getType() == DAVResource.DAV_RESOURCE_TYPE_WORKING)) {
            return resource.getMD5Checksum();
        }
        return null;
    }

    private String getETag(DAVResource resource) {
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            return null;
        }
        return resource.getETag();
    }

    private String getRepositoryUUIDProp(DAVResource resource) throws SVNException {
        return resource.getRepositoryUUID(false);
    }

    private String getCheckedInProp(DAVResource resource) throws SVNException {
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            long latestRevision = resource.getRepositoryLatestRevision();
            String uri = SVNEncodingUtil.uriEncode(DAVResourceUtil.buildURI(resource.getContext(), resource.getPath(), DAVResourceKind.BASELINE, latestRevision, null));
            return addHrefTags(uri);
        } else if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
            return null;
        } else {
            //TODO: complete.
        }
        return null;
    }

    private String getResourceTypeProp(DAVResource resource) {
        return resource.isCollection() ? "<D:collection/>" : "";
    }

    private String getVersionControlConfigurationProp(DAVResource resource) {
        if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
            return null;
        }
        //Method doesn't use revision parameter at this moment
        String uri = SVNEncodingUtil.uriEncode(DAVResourceUtil.buildURI(resource.getContext(), resource.getPath(), DAVResourceKind.VCC, -1, resource.getParameterPath()));
        return addHrefTags(uri);
    }

    private String getDeadpropCountProp(DAVResource resource) {
        return resource.getDeadpropCount();
    }
}
