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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;


/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVXMLUtil extends XMLUtil {

    public static final Map PREFIX_MAP = new HashMap();

    public static final String DAV_NAMESPACE_PREFIX = "D";
    public static final String SVN_NAMESPACE_PREFIX = "S";
    public static final String SVN_DAV_PROPERTY_PREFIX = "SD";
    public static final String SVN_CUSTOM_PROPERTY_PREFIX = "SC";
    public static final String SVN_SVN_PROPERTY_PREFIX = "SS";
    public static final String SVN_APACHE_PROPERTY_PREFIX = "SA";

    static {
        PREFIX_MAP.put(DAVElement.DAV_NAMESPACE, DAV_NAMESPACE_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_NAMESPACE, SVN_NAMESPACE_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_DAV_PROPERTY_NAMESPACE, SVN_DAV_PROPERTY_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_SVN_PROPERTY_NAMESPACE, SVN_SVN_PROPERTY_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE, SVN_CUSTOM_PROPERTY_PREFIX);
        PREFIX_MAP.put(DAVElement.SVN_APACHE_PROPERTY_NAMESPACE, SVN_APACHE_PROPERTY_PREFIX);
    }

    public static StringBuffer openNamespaceDeclarationTag(String prefix, String header, Collection namespaces, StringBuffer target) {
        return openNamespaceDeclarationTag(prefix, header, namespaces, null, target);
    }

    public static StringBuffer openNamespaceDeclarationTag(String prefix, String header, Collection namespaces, Map attrs, StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        target.append("<");
        target.append(prefix);
        target.append(":");
        target.append(header);
        //We should always add "DAV:" namespace
        target.append(" xmlns:");
        target.append(DAV_NAMESPACE_PREFIX);
        target.append("=\"");
        target.append(DAVElement.DAV_NAMESPACE);
        target.append("\"");
        if (namespaces != null && !namespaces.isEmpty()) {
            Collection usedNamespaces = new ArrayList();
            usedNamespaces.add(DAVElement.DAV_NAMESPACE);
            for (Iterator iterator = namespaces.iterator(); iterator.hasNext();) {
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
        if (attrs != null && !attrs.isEmpty()) {
            for (Iterator iterator = attrs.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry entry = (Map.Entry) iterator.next();
                String name = (String) entry.getKey();
                String value = (String) entry.getValue();
                target.append(" ");
                target.append(name);
                target.append("=\"");
                target.append(SVNEncodingUtil.xmlEncodeAttr(value));
                target.append("\"");
            }
        }
        target.append(">\n");
        return target;
    }
}
