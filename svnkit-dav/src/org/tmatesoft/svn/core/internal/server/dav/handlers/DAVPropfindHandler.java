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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceKind;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVPropfindHandler extends ServletDAVHandler {

    private static final String DEFAULT_AUTOVERSION_LINE = "DAV:checkout-checkin";
    private static final String COLLECTION_RESOURCE_TYPE = "<D:collection/>\n";

    private DAVPropfindRequest myDAVRequest;

    public DAVPropfindHandler(DAVRepositoryManager connector, HttpServletRequest request, HttpServletResponse response) {
        super(connector, request, response);
    }

    protected DAVPropfindRequest getDAVRequest() {
        if (myDAVRequest == null) {
            myDAVRequest = new DAVPropfindRequest();
        }
        return myDAVRequest;
    }

    public void execute() throws SVNException {
        getDAVRequest().readInput(getRequestInputStream());
        DAVResource resource = createDAVResource(true, false);

        StringBuffer body = new StringBuffer();
        DAVDepth depth = getRequestDepth(DAVDepth.DEPTH_INFINITY);
        generatePropertiesResponse(body, resource, depth);
        String responseBody = body.toString();

        try {
            setResponseContentLength(responseBody.getBytes(UTF_8_ENCODING).length);
        } catch (UnsupportedEncodingException e) {
        }

        setDefaultResponseHeaders();
        setResponseContentType(DEFAULT_XML_CONTENT_TYPE);
        setResponseStatus(SC_MULTISTATUS);

        try {
            getResponseWriter().write(responseBody);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    private void generatePropertiesResponse(StringBuffer xmlBuffer, DAVResource resource, DAVDepth depth) throws SVNException {
        XMLUtil.addXMLHeader(xmlBuffer);
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "multistatus", null, xmlBuffer);
        if (getDAVRequest().isAllPropRequest()) {
            Collection allProperties = convertDeadPropertiesToDAVElements(resource.getDeadProperties());
            getSupportedLiveProperties(resource, allProperties);
            generateResponse(xmlBuffer, resource, allProperties, depth, true);
        } else if (getDAVRequest().isPropNameRequest()) {
            //TODO: generate all properties' names
        } else if (getDAVRequest().isPropRequest()) {
            generateResponse(xmlBuffer, resource, getDAVRequest().getPropertyElements(), depth, false);
        }
        XMLUtil.closeXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "multistatus", xmlBuffer);
    }

    private void generateResponse(StringBuffer xmlBuffer, DAVResource resource, Collection properties, DAVDepth depth, boolean updateProperties) throws SVNException {
        addResponse(xmlBuffer, resource, properties);
        if (depth != DAVDepth.DEPTH_ZERO && resource.getResourceURI().getType() == DAVResourceType.REGULAR && resource.isCollection()) {
            DAVDepth newDepth = DAVDepth.decreaseDepth(depth);
            for (Iterator entriesIterator = resource.getEntries().iterator(); entriesIterator.hasNext();) {
                SVNDirEntry entry = (SVNDirEntry) entriesIterator.next();
                String entryURI = DAVPathUtil.append(resource.getResourceURI().getURI(), entry.getName());
                //TODO: check if native svn uses label from parent resource
                DAVResourceURI newResourceURI = new DAVResourceURI(resource.getResourceURI().getContext(), entryURI, null, false);
                DAVResource newResource = new DAVResource(resource.getRepository(), newResourceURI, resource.isSVNClient(), resource.getVersion(), resource.getClientOptions(), null, null);
                if (updateProperties) {
                    properties.clear();
                    properties.addAll(convertDeadPropertiesToDAVElements(newResource.getDeadProperties()));
                    getSupportedLiveProperties(newResource, properties);
                }
                generateResponse(xmlBuffer, newResource, properties, newDepth, updateProperties);
            }
        }
    }

    private void addResponse(StringBuffer xmlBuffer, DAVResource resource, Collection properties) throws SVNException {
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "response", properties, xmlBuffer);
        String uri = resource.getResourceURI().getRequestURI();
        XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "href", uri, xmlBuffer);
        Collection badProperties = addPropstat(xmlBuffer, properties, resource);
        addBadPropertiesPropstat(xmlBuffer, badProperties);
        XMLUtil.closeXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "response", xmlBuffer);
    }

    private Collection addPropstat(StringBuffer xmlBuffer, Collection properties, DAVResource resource) throws SVNException {
        XMLUtil.openXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "propstat", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        XMLUtil.openXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "prop", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
        Collection badProperties = new ArrayList();
        for (Iterator elements = properties.iterator(); elements.hasNext();) {
            DAVElement element = (DAVElement) elements.next();
            try {
                insertPropertyValue(element, resource, xmlBuffer);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_DAV_PROPS_NOT_FOUND) {
                    badProperties.add(element);
                } else {
                    throw e;
                }
            }
        }
        XMLUtil.closeXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "prop", xmlBuffer);
        XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "status", HTTP_STATUS_OK_LINE, xmlBuffer);
        XMLUtil.closeXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "propstat", xmlBuffer);
        return badProperties;
    }

    private void addBadPropertiesPropstat(StringBuffer xmlBuffer, Collection properties) {
        if (properties != null && !properties.isEmpty()) {
            XMLUtil.openXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "propstat", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
            XMLUtil.openXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "prop", XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
            for (Iterator elements = properties.iterator(); elements.hasNext();) {
                DAVElement element = (DAVElement) elements.next();
                String prefix = (String) DAVXMLUtil.PREFIX_MAP.get(element.getNamespace());
                String name = element.getName();
                XMLUtil.openXMLTag(prefix, name, XMLUtil.XML_STYLE_SELF_CLOSING, null, xmlBuffer);
            }
            XMLUtil.closeXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "prop", xmlBuffer);
            XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "status", HTTP_NOT_FOUND_LINE, xmlBuffer);
            XMLUtil.closeXMLTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "propstat", xmlBuffer);
        }
    }

    private void insertPropertyValue(DAVElement element, DAVResource resource, StringBuffer xmlBuffer) throws SVNException {
        if (!resource.exists() && (element != DAVElement.VERSION_CONTROLLED_CONFIGURATION || element != DAVElement.BASELINE_RELATIVE_PATH)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Invalid path ''{0}''", resource.getResourceURI().getURI()));
        }
        String prefix = (String) DAVXMLUtil.PREFIX_MAP.get(element.getNamespace());
        String name = element.getName();
        String value;
        boolean isCData = true;
        if (element == DAVElement.VERSION_CONTROLLED_CONFIGURATION) {
            value = getVersionControlConfigurationProp(resource);
            XMLUtil.openXMLTag(prefix, name, XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
            XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "href", value, xmlBuffer);
            XMLUtil.closeXMLTag(prefix, name, xmlBuffer);
            isCData = false;
        } else if (element == DAVElement.RESOURCE_TYPE) {
            value = getResourceTypeProp(resource);
            if (value.length() != 0) {
                XMLUtil.openXMLTag(prefix, name, XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
                xmlBuffer.append(value);
                XMLUtil.closeXMLTag(prefix, name, xmlBuffer);
                isCData = false;
            }
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
            XMLUtil.openXMLTag(prefix, name, XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
            XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "href", value, xmlBuffer);
            XMLUtil.closeXMLTag(prefix, name, xmlBuffer);
            isCData = false;
        } else if (element == DAVElement.VERSION_NAME) {
            value = getVersionNameProp(resource);
        } else if (element == DAVElement.BASELINE_COLLECTION) {
            value = getBaselineCollectionProp(resource);
            XMLUtil.openXMLTag(prefix, name, XMLUtil.XML_STYLE_NORMAL, null, xmlBuffer);
            XMLUtil.openCDataTag(DAVXMLUtil.DAV_NAMESPACE_PREFIX, "href", value, xmlBuffer);
            XMLUtil.closeXMLTag(prefix, name, xmlBuffer);
            isCData = false;
        } else if (element == DAVElement.CREATION_DATE) {
            value = SVNTimeUtil.formatDate(getLastModifiedTime(resource));
        } else if (element == GET_LAST_MODIFIED) {
            value = SVNTimeUtil.formatRFC1123Date(getLastModifiedTime(resource));
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
            value = getDeadProperty(element, resource);
        }
        if (value == null || value.length() == 0) {
            XMLUtil.openXMLTag(prefix, name, XMLUtil.XML_STYLE_SELF_CLOSING, null, xmlBuffer);
        } else if (isCData) {
            XMLUtil.openCDataTag(prefix, name, value, xmlBuffer);
        }
    }

    private String getAutoVersionProp() {
        return DEFAULT_AUTOVERSION_LINE;
    }

    private Date getLastModifiedTime(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() == DAVResourceType.PRIVATE && resource.getResourceURI().getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
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
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        return resource.getRevisionDate(revision);
    }

    private String getBaselineCollectionProp(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() != DAVResourceType.VERSION || !resource.getResourceURI().isBaseLined()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        return DAVPathUtil.buildURI(resource.getResourceURI().getContext(), DAVResourceKind.BASELINE_COLL, resource.getRevision(), null);
    }

    private String getVersionNameProp(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() != DAVResourceType.VERSION && !resource.getResourceURI().isVersioned()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        if (resource.getResourceURI().getType() == DAVResourceType.PRIVATE && resource.getResourceURI().getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        if (resource.getResourceURI().isBaseLined()) {
            return String.valueOf(resource.getRevision());
        }
        return String.valueOf(resource.getCreatedRevision());
    }

    private String getContentLengthProp(DAVResource resource) throws SVNException {
        if (resource.isCollection() || resource.getResourceURI().isBaseLined()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
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
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
        }
        long revision;
        if (resource.getResourceURI().isBaseLined() && resource.getResourceURI().getType() == DAVResourceType.VERSION) {
            revision = resource.getRevision();
        } else
        if (resource.getResourceURI().getType() == DAVResourceType.REGULAR || resource.getResourceURI().getType() == DAVResourceType.WORKING
                || resource.getResourceURI().getType() == DAVResourceType.VERSION) {
            revision = resource.getCreatedRevision();
        } else {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        return resource.getAuthor(revision);
    }

    private String getBaselineRelativePathProp(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() != DAVResourceType.REGULAR) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
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
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
        return null;
    }

    private String getETag(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() == DAVResourceType.PRIVATE && resource.getResourceURI().getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        return resource.getETag();
    }

    private String getRepositoryUUIDProp(DAVResource resource) throws SVNException {
        return resource.getRepositoryUUID(false);
    }

    private String getCheckedInProp(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() == DAVResourceType.PRIVATE && resource.getResourceURI().getKind() == DAVResourceKind.VCC) {
            return DAVPathUtil.buildURI(resource.getResourceURI().getContext(), DAVResourceKind.BASELINE, resource.getLatestRevision(), null);

        } else if (resource.getResourceURI().getType() != DAVResourceType.REGULAR) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        } else {
            return DAVPathUtil.buildURI(resource.getResourceURI().getContext(), DAVResourceKind.VERSION, resource.getCreatedRevision(), resource.getResourceURI().getPath());
        }
    }

    private String getResourceTypeProp(DAVResource resource) {
        return resource.isCollection() ? COLLECTION_RESOURCE_TYPE : "";
    }

    private String getVersionControlConfigurationProp(DAVResource resource) throws SVNException {
        if (resource.getResourceURI().getType() != DAVResourceType.REGULAR) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        //Method doesn't use revision parameter at this moment
        return DAVPathUtil.buildURI(resource.getResourceURI().getContext(), DAVResourceKind.VCC, -1, resource.getResourceURI().getPath());
    }

    private String getDeadpropCountProp(DAVResource resource) {
        int deadPropertiesCount = resource.getDeadProperties().size();
        return String.valueOf(deadPropertiesCount);
    }

    private String getLogProp(DAVResource resource) throws SVNException {
        return resource.getLog(resource.getCreatedRevision());
    }

    private String getDeadProperty(DAVElement element, DAVResource resource) throws SVNException {
        return resource.getProperty(convertDAVElementToDeadProperty(element));
    }

    //Next four methods we can use for dead properties only

    private Collection convertDeadPropertiesToDAVElements(Collection deadProperties) throws SVNException {
        Collection elements = new ArrayList();
        for (Iterator iterator = deadProperties.iterator(); iterator.hasNext();) {
            String propertyName = (String) iterator.next();
            elements.add(convertDeadPropertyToDAVElement(propertyName));
        }
        return elements;
    }

    private DAVElement convertDeadPropertyToDAVElement(String property) throws SVNException {
        if (!SVNProperty.isRegularProperty(property)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Unrecognized property prefix ''{0}''", property));
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
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Unrecognized namespace ''{0}''", namespace));
        return null;
    }
}
