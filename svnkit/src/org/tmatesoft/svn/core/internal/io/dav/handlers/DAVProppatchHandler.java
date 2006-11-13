/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav.handlers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.xml.sax.Attributes;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVProppatchHandler extends BasicDAVHandler {
    
    public static StringBuffer generatePropertyRequest(StringBuffer buffer, String name, String value) {
        Map map = new HashMap();
        map.put(name, value);
        return generatePropertyRequest(buffer, map);
    }

    public static StringBuffer generatePropertyRequest(StringBuffer buffer, Map properties) {
        buffer = buffer == null ? new StringBuffer() : buffer;
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n");
        buffer.append("<D:propertyupdate xmlns:D=\"DAV:\" xmlns:V=\"");
        buffer.append(DAVElement.SVN_DAV_PROPERTY_NAMESPACE);
        buffer.append("\" xmlns:C=\"");
        buffer.append(DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE);
        buffer.append("\" xmlns:S=\"");
        buffer.append(DAVElement.SVN_SVN_PROPERTY_NAMESPACE);
        buffer.append("\" >\n");
        
        // if there are non-null values
        if (hasNotNullValues(properties)) {
            buffer.append("<D:set><D:prop>\n");
            for(Iterator names = properties.keySet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                String value = (String) properties.get(name);
                if (value != null) {
                    buffer = appendProperty(buffer, name, value);
                }
            }
            buffer.append("\n</D:prop></D:set>");
        }
        
        // if there are null values
        if (hasNullValues(properties)) {
            buffer.append("<D:remove><D:prop>\n");
            for(Iterator names = properties.keySet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                String value = (String) properties.get(name);
                if (value == null) {
                    buffer = appendProperty(buffer, name, value);
                }
            }
            buffer.append("\n</D:prop></D:remove>");
        }
        
        buffer.append("\n</D:propertyupdate>");
        return buffer;
    }
    
    private static StringBuffer appendProperty(StringBuffer buffer, String name, String value) {
        buffer.append("<");
        int index = buffer.length();
        if (name.startsWith("svn:")) {
            buffer.append("S:");
            buffer.append(name.substring("svn:".length()));
        } else {
            buffer.append("C:");
            buffer.append(name);
        }
        int index2 = buffer.length();
        if (value == null) {
            return buffer.append(" />");
        }
        if (SVNEncodingUtil.isXMLSafe(value)) {
            value = SVNEncodingUtil.xmlEncodeCDATA(value);            
        } else {
            value = SVNBase64.byteArrayToBase64(value.getBytes());
            buffer.append(" V:encoding=\"base64\"");
        }
        buffer.append(">");
        buffer.append(value);
        buffer.append("</");
        buffer.append(buffer.substring(index, index2));
        return buffer.append(">");        
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
    }
    
    private static boolean hasNullValues(Map map) {
        if (map.isEmpty()) {
            return false;
        }
        return map.containsValue(null);
    }

    private static boolean hasNotNullValues(Map map) {
        if (map.isEmpty()) {
            return false;
        }
        if (!hasNullValues(map)) {
            return true;
        }        
        for(Iterator entries = map.entrySet().iterator(); entries.hasNext();) {
            Map.Entry entry = (Map.Entry) entries.next();
            if (entry.getValue() != null) {
                return true;
            }
        }
        return false;
    }

}
