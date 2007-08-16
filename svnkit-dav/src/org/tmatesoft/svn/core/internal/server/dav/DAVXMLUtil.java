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
package org.tmatesoft.svn.core.internal.server.dav;

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVXMLUtil {

    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";

    public static final Map PREFIX_MAP = new HashMap();


    public static final String DAV_NAMESPACE_PREFIX = "D";
    public static final String SVN_DAV_PROPERTY_PREFIX = "V";
    public static final String SVN_CUSTOM_PROPERTY_PREFIX = "C";
    public static final String SVN_SVN_PROPERTY_PREFIX = "S";
    public static final String SVN_APACHE_PROPERTY_PREFIX = "M";

    public static final int XML_STYLE_NORMAL = 1;
    public static final int XML_STYLE_PROTECT_PCDATA = 2;
    public static final int XML_STYLE_SELF_CLOSING = 4;

    static {
        PREFIX_MAP.put(DAVElement.DAV_NAMESPACE, DAV_NAMESPACE_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_DAV_PROPERTY_NAMESPACE, SVN_DAV_PROPERTY_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_SVN_PROPERTY_NAMESPACE, SVN_SVN_PROPERTY_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE, SVN_CUSTOM_PROPERTY_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_APACHE_PROPERTY_NAMESPACE, SVN_APACHE_PROPERTY_PREFIX);
    }

    public static StringBuffer addHeader(StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        target.append(XML_HEADER);
        return target;
    }

    public static StringBuffer openNamespaceDeclarationTag(String prefix, String header, Collection davElements, StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        target.append("<");
        target.append(prefix);
        target.append(":");
        target.append(header);
        //We should always add DAV: namespace, otherwise client will fail parsing response body
        target.append(" xmlns:");
        target.append(DAV_NAMESPACE_PREFIX);
        target.append("=\"");
        target.append(DAVElement.DAV_NAMESPACE);
        target.append("\"");
        if (davElements != null && !davElements.isEmpty()) {
            Collection usedNamespaces = new ArrayList();
            usedNamespaces.add(DAVElement.DAV_NAMESPACE);
            for (Iterator iterator = davElements.iterator(); iterator.hasNext();) {
                Object item = iterator.next();
                String currentNamespace = null;
                if (item instanceof DAVElement) {
                    DAVElement currentElement = (DAVElement) item;
                    currentNamespace = currentElement.getNamespace();
                } else if (item instanceof String) {
                    currentNamespace = (String) item;
                }
                if (currentNamespace != null && currentNamespace.length() > 0 && !usedNamespaces.contains(currentNamespace)) {
                    usedNamespaces.add(currentNamespace);
                    target.append(" xmlns:");
                    target.append(PREFIX_MAP.get(currentNamespace));
                    target.append("=\"");
                    target.append(currentNamespace);
                    target.append("\"");
                }
            }
            usedNamespaces.clear();
        }
        target.append(">\n");
        return target;
    }

    public static StringBuffer appendXMLFooter(String prefix, String header, StringBuffer target) {
        target.append("</");
        target.append(prefix);
        target.append(":");
        target.append(header);
        target.append(">");
        return target;
    }


    public static StringBuffer openCDataTag(String prefix, String tagName, String cdata, StringBuffer target) {
        if (cdata == null) {
            return target;
        }
        target = openXMLTag(prefix, tagName, XML_STYLE_PROTECT_PCDATA, null, target);
        target.append(SVNEncodingUtil.xmlEncodeCDATA(cdata));
        target = closeXMLTag(prefix, tagName, target);
        return target;
    }

    public static StringBuffer openXMLTag(String prefix, String tagName, int style, String attr, String value, StringBuffer target) {
        HashMap attributes = new HashMap();
        attributes.put(attr, value);
        return openXMLTag(prefix, tagName, style, attributes, target);
    }

    public static StringBuffer openXMLTag(String prefix, String tagName, int style, Map attributes, StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        target.append("<");
        target.append(prefix);
        target.append(":");
        target.append(tagName);
        if (attributes != null) {
            for (Iterator iterator = attributes.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                String name = (String) entry.getKey();
                String value = (String) entry.getValue();
                target.append(name);
                target.append("=\"");
                target.append(SVNEncodingUtil.xmlEncodeAttr(value));
            }
            attributes.clear();
        }
        if (style == XML_STYLE_SELF_CLOSING) {
            target.append("/");
        }
        target.append(">");
        return target;
    }

    public static StringBuffer closeXMLTag(String prefix, String tagName, StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        target.append("</");
        target.append(prefix);
        target.append(":");
        target.append(tagName);
        target.append(">\n");
        return target;
    }
}
