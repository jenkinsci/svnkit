/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav.handlers;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;

import org.xml.sax.Attributes;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class DAVProppatchHandler extends BasicDAVHandler {

    private static final Collection NAMESPACES = new LinkedList();

    static {
        NAMESPACES.add(DAVElement.DAV_NAMESPACE);
        NAMESPACES.add(DAVElement.SVN_DAV_PROPERTY_NAMESPACE);
        NAMESPACES.add(DAVElement.SVN_SVN_PROPERTY_NAMESPACE);
        NAMESPACES.add(DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE);
    }

    public static StringBuffer generatePropertyRequest(StringBuffer buffer, String name, SVNPropertyValue value) {
        SVNProperties props = new SVNProperties();
        props.put(name, value);
        return generatePropertyRequest(buffer, props);
    }

    public static StringBuffer generatePropertyRequest(StringBuffer buffer, String name, byte[] value) {
        SVNProperties props = new SVNProperties();
        props.put(name, value);
        return generatePropertyRequest(buffer, props);
    }

    public static StringBuffer generatePropertyRequest(StringBuffer xmlBuffer, SVNProperties properties) {
        xmlBuffer = xmlBuffer == null ? new StringBuffer() : xmlBuffer;
        SVNXMLUtil.addXMLHeader(xmlBuffer);
        SVNXMLUtil.openNamespaceDeclarationTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "propertyupdate", NAMESPACES,
                SVNXMLUtil.PREFIX_MAP, xmlBuffer);

        // if there are non-null values
        if (properties.hasNotNullValues()) {
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "set", SVNXMLUtil.XML_STYLE_NORMAL, null,
                    xmlBuffer);
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "prop", SVNXMLUtil.XML_STYLE_NORMAL, null,
                    xmlBuffer);
            for (Iterator names = properties.nameSet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                SVNPropertyValue value = properties.getSVNPropertyValue(name);
                if (value != null) {
                    xmlBuffer = appendProperty(xmlBuffer, name, value);
                }
            }
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "prop", xmlBuffer);
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "set", xmlBuffer);
        }

        // if there are null values
        if (properties.hasNullValues()) {
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "remove", SVNXMLUtil.XML_STYLE_NORMAL, null,
                    xmlBuffer);
            SVNXMLUtil.openXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "prop", SVNXMLUtil.XML_STYLE_NORMAL, null,
                    xmlBuffer);
            for (Iterator names = properties.nameSet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                SVNPropertyValue value = properties.getSVNPropertyValue(name);
                if (value == null) {
                    xmlBuffer = appendProperty(xmlBuffer, name, value);
                }
            }
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "prop", xmlBuffer);
            SVNXMLUtil.closeXMLTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "remove", xmlBuffer);
        }

        SVNXMLUtil.addXMLFooter(SVNXMLUtil.DAV_NAMESPACE_PREFIX, "propertyupdate", xmlBuffer);
        return xmlBuffer;
    }

    private static StringBuffer appendProperty(StringBuffer xmlBuffer, String name, SVNPropertyValue value) {
        String prefix = SVNProperty.isSVNProperty(name) ? SVNXMLUtil.SVN_SVN_PROPERTY_PREFIX : SVNXMLUtil.SVN_CUSTOM_PROPERTY_PREFIX;
        String tagName = SVNProperty.shortPropertyName(name);
        Map attrs = null;
        String str = null;
        if (!value.isXMLSafe()) {
            attrs = new HashMap(1);
            String attrPrefix = (String) SVNXMLUtil.PREFIX_MAP.get(DAVElement.SVN_DAV_PROPERTY_NAMESPACE);
            attrs.put(attrPrefix + ":encoding", "base64");            
            str = SVNBase64.byteArrayToBase64(SVNPropertyValue.getPropertyAsBytes(value));
        }
        return SVNXMLUtil.openCDataTag(prefix, tagName, str, attrs, xmlBuffer);
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
    }
}
