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
    private static final DAVElement LOG = DAVElement.getElement(DAVElement.SVN_SVN_PROPERTY_NAMESPACE, "log");

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
        } else if (element == ALLPROP) {
            getDAVProperties().add(ALLPROP);
        } else if (element == PROPNAME) {
            getDAVProperties().add(PROPNAME);
        } else
        if ((PROP_ELEMENTS.contains(element) || DAVElement.SVN_SVN_PROPERTY_NAMESPACE.equals(element.getNamespace())
                || DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE.equals(element.getNamespace())) && parent == DAVElement.PROP) {
            getDAVProperties().add(element);
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

        setDefaultResponseHeaders(resource);
        setResponseStatus(SC_MULTISTATUS);

        try {
            getResponseWriter().write(body.toString());
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    private void generatePropertiesResponse(StringBuffer xmlBuffer, DAVResource resource, DAVDepth depth) throws SVNException {
        appendXMLHeader(DAV_NAMESPACE_PREFIX, "multistatus", getDAVProperties(), xmlBuffer);
        if (getDAVProperties().size() == 1 && getDAVProperties().contains(ALLPROP)) {
            //PROP_ELEMENTS contains only so called live properties, but to generate ALLPROP body we need some more: user defined + server defined
            // specific properties, so we add them from resource's SVNProperties.
            Collection allProperties = new ArrayList();
            for (Iterator iterator = resource.getDeadProperties().iterator(); iterator.hasNext();) {
                String property = (String) iterator.next();
                allProperties.add(DAVResourceUtil.convertToDAVElement(property));
            }
            allProperties.addAll(PROP_ELEMENTS);
            generateResponse(xmlBuffer, resource, allProperties, depth);
        } else {
            generateResponse(xmlBuffer, resource, getDAVProperties(), depth);
        }
        closeXMLTag(DAV_NAMESPACE_PREFIX, "multistatus", xmlBuffer);
    }

    private void generateResponse(StringBuffer xmlBuffer, DAVResource resource, Collection properties, DAVDepth depth) throws SVNException {
        addResponse(xmlBuffer, resource, properties);
        if (depth != DAVDepth.DEPTH_ZERO && resource.isCollection()) {
            DAVDepth newDepth = DAVDepth.decreaseDepth(depth);
            for (Iterator entriesIterator = resource.getEntries().iterator(); entriesIterator.hasNext();) {
                SVNDirEntry entry = (SVNDirEntry) entriesIterator.next();
                StringBuffer entryURI = new StringBuffer();
                entryURI.append(resource.getURI());
                entryURI.append(resource.getURI().endsWith("/") ? "" : "/");
                entryURI.append(entry.getName());
                //TODO: Check if native svn uses label = revision here
                DAVResource newResource = new DAVResource(resource.getRepository(), resource.getContext(), entryURI.toString(), null, false);
                generateResponse(xmlBuffer, newResource, properties, newDepth);
            }
        }
    }

    private void addResponse(StringBuffer xmlBuffer, DAVResource resource, Collection properties) throws SVNException {
        Map prefixMapping = new HashMap();
        //Should we split this method?        
        openNamespacesDeclarationTag(DAV_NAMESPACE_PREFIX, "response", XML_STYLE_NORMAL, properties, prefixMapping, xmlBuffer);
        String uri = resource.getContext() + resource.getURI();
        xmlBuffer.append(addHrefTags(uri));
        Collection badProperties = addPropstat(xmlBuffer, properties, resource, prefixMapping);
        addPropNames(xmlBuffer, prefixMapping, badProperties);
        closeXMLTag(DAV_NAMESPACE_PREFIX, "response", xmlBuffer);
    }

    private Collection addPropstat(StringBuffer xmlBuffer, Collection properties, DAVResource resource, Map prefixMapping) throws SVNException {
        openXMLTag(DAV_NAMESPACE_PREFIX, "propstat", XML_STYLE_NORMAL, null, xmlBuffer);
        openXMLTag(DAV_NAMESPACE_PREFIX, "prop", XML_STYLE_NORMAL, null, xmlBuffer);
        Collection badProperties = null;
        for (Iterator elements = properties.iterator(); elements.hasNext();) {
            DAVElement element = (DAVElement) elements.next();
            badProperties = insertPropertyValue(element, resource, prefixMapping, badProperties, xmlBuffer);
        }
        closeXMLTag(DAV_NAMESPACE_PREFIX, "prop", xmlBuffer);
        openCDataTag(DAV_NAMESPACE_PREFIX, "status", HTTP_STATUS_OK_LINE, xmlBuffer);
        closeXMLTag(DAV_NAMESPACE_PREFIX, "propstat", xmlBuffer);
        return badProperties;
    }

    private void addPropNames(StringBuffer xmlBuffer, Map prefixMapping, Collection properties) {
        if (properties != null) {
            openXMLTag(DAV_NAMESPACE_PREFIX, "propstat", XML_STYLE_NORMAL, null, xmlBuffer);
            openXMLTag(DAV_NAMESPACE_PREFIX, "prop", XML_STYLE_NORMAL, null, xmlBuffer);
            for (Iterator elements = properties.iterator(); elements.hasNext();) {
                DAVElement element = (DAVElement) elements.next();
                String prefix = prefixMapping.get(element.getNamespace()).toString();
                String name = element.getName();
                openXMLTag(prefix, name, XML_STYLE_SELF_CLOSING, null, xmlBuffer);
            }
            closeXMLTag(DAV_NAMESPACE_PREFIX, "prop", xmlBuffer);
            openCDataTag(DAV_NAMESPACE_PREFIX, "status", HTTP_NOT_FOUND_LINE, xmlBuffer);
            closeXMLTag(DAV_NAMESPACE_PREFIX, "propstat", xmlBuffer);
        }
    }

    private Map openNamespacesDeclarationTag(String prefix, String tagName, int style, Collection incomingDAVElements, Map prefixMapping, StringBuffer target) {
        prefixMapping = prefixMapping == null ? new HashMap() : prefixMapping;
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

    private Collection insertPropertyValue(DAVElement element, DAVResource resource, Map prefixMapping, Collection badProperties, StringBuffer xmlBuffer) throws SVNException {
        try {
            String value = getPropertyValue(resource, element);
            String prefix = prefixMapping.get(element.getNamespace()).toString();
            String name = element.getName();
            if (value == null || "".equals(value)) {
                openXMLTag(prefix, name, XML_STYLE_SELF_CLOSING, null, xmlBuffer);
            } else {
                openXMLTag(prefix, name, XML_STYLE_NORMAL, null, xmlBuffer);
                xmlBuffer.append(value);
                closeXMLTag(prefix, name, xmlBuffer);
            }
            return badProperties;
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_DAV_PROPS_NOT_FOUND) {
                if (badProperties == null) {
                    badProperties = new ArrayList();
                }
                badProperties.add(element);
                return badProperties;
            } else if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_DAV_MALFORMED_DATA) {
                return badProperties;
            } else {
                throw e;
            }
        }
    }

    private String getPropertyValue(DAVResource resource, DAVElement element) throws SVNException {
        if (!resource.exists() && (element != DAVElement.VERSION_CONTROLLED_CONFIGURATION || element != DAVElement.BASELINE_RELATIVE_PATH)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Invalid path ''{0}''", resource.getURI()));
        }
        String value;
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
        } else if (element == LOG) {
            value = getLogProp(resource);
        } else {
            value = getPropertyByName(element, resource);
        }
        return value;
    }

    private String getLogProp(DAVResource resource) throws SVNException {
        return resource.getLog(resource.getRevision());
    }

    private String getAutoVersionProp() {
        return DEFAULT_AUTOVERSION_LINE;
    }

    private String getLastModifiedProp(DAVResource resource) throws SVNException {
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
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
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        return resource.getLastModified(revision);
    }

    private String getBaselineCollectionProp(DAVResource resource) throws SVNException {
        if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_VERSION || !resource.isBaseLined()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Failed to determine property"));
            return null;
        }
        String uri = SVNEncodingUtil.uriEncode(DAVResourceUtil.buildURI(resource.getContext(), resource.getPath(), DAVResourceKind.BASELINE_COLL, resource.getRevision(), null));
        return addHrefTags(uri);
    }

    private String getVersionNameProp(DAVResource resource) throws SVNException {
        if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_VERSION && !resource.isVersioned()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        if (resource.isBaseLined()) {
            return String.valueOf(resource.getRevision());
        }
        return String.valueOf(resource.getCommitedRevision());
    }

    private String getContentLengthProp(DAVResource resource) throws SVNException {
        if (resource.isCollection() || resource.isBaseLined()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        long fileSize = resource.getContentLength();
        return String.valueOf(fileSize);
    }

    private String getContentTypeProp(DAVResource resource) throws SVNException {
        if (resource.isBaseLined() && resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        //TODO: native svn examine if client is not (?!) svn client to use request's header 'content type'.
        //TODO: move this condition to DAVResource getContentType.
        if (resource.isCollection()) {
            return DEFAULT_CONTENT_TYPE;
        }
        return resource.getContentType();
    }

    private String getCreationDateProp(DAVResource resource) throws SVNException {
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        return resource.getLastModified();
    }

    private String getCreatorDisplayNameProp(DAVResource resource) throws SVNException {
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
        }
        long revision;
        if (resource.isBaseLined() && resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            revision = resource.getRevision();
        } else
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_REGULAR || resource.getType() == DAVResource.DAV_RESOURCE_TYPE_WORKING
                || resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            revision = resource.getCommitedRevision();
        } else {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        return resource.getLastAuthor(revision);
    }

    private String getBaselineRelativePathProp(DAVResource resource) throws SVNException {
        if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        String parameterPath = resource.getParameterPath();
        if (parameterPath.startsWith("/")) {
            //path must be relative
            parameterPath = parameterPath.substring("/".length());
        }
        return parameterPath;
    }

    private String getMD5ChecksumProp(DAVResource resource) throws SVNException {
        if (!resource.isCollection() && !resource.isBaseLined()
                && (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_REGULAR || resource.getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION
                || resource.getType() == DAVResource.DAV_RESOURCE_TYPE_WORKING)) {
            return resource.getMD5Checksum();
        }
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Failed to determine property"));
        return null;
    }

    private String getETag(DAVResource resource) throws SVNException {
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        return resource.getETag();
    }

    private String getRepositoryUUIDProp(DAVResource resource) throws SVNException {
        return resource.getRepositoryUUID(false);
    }

    private String getCheckedInProp(DAVResource resource) throws SVNException {
        if (resource.getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && resource.getKind() == DAVResourceKind.VCC) {
            long latestRevision = resource.getLatestRevision();
            String uri = SVNEncodingUtil.uriEncode(DAVResourceUtil.buildURI(resource.getContext(), resource.getPath(), DAVResourceKind.BASELINE, latestRevision, null));
            return addHrefTags(uri);
        } else if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        } else {
            //TODO: complete.
        }
        return null;
    }

    private String getResourceTypeProp(DAVResource resource) {
        return resource.isCollection() ? "<D:collection/>" : "";
    }

    private String getVersionControlConfigurationProp(DAVResource resource) throws SVNException {
        if (resource.getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        //Method doesn't use revision parameter at this moment
        String uri = SVNEncodingUtil.uriEncode(DAVResourceUtil.buildURI(resource.getContext(), resource.getPath(), DAVResourceKind.VCC, -1, resource.getParameterPath()));
        return addHrefTags(uri);
    }

    private String getDeadpropCountProp(DAVResource resource) throws SVNException {
        int deadPropertiesCount = resource.getDeadProperties().size();
        return String.valueOf(deadPropertiesCount);
    }


    private String getPropertyByName(DAVElement element, DAVResource resource) throws SVNException {
        String value = resource.getProperty(element.getNamespace(), element.getName());
        if (value == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_MALFORMED_DATA, "Requested for unsufficient property ''{0}''", element.getName()));
        }
        return value;
    }
}
