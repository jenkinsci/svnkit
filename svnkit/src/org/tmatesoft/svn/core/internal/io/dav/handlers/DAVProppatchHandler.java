/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav.handlers;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPStatus;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.xml.sax.Attributes;


/**
 * @version 1.1.1
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

    private StringBuffer myPropertyName;
    private StringBuffer myPropstatDescription;
    private StringBuffer myDescription;
    private boolean myPropstatContainsError;
    private boolean myResponseContainsError;
    private SVNErrorMessage myError;


    public DAVProppatchHandler() {
        init();
    }

    public SVNErrorMessage getError(){
        return myError;
    }

    private StringBuffer getPropertyName() {
        if (myPropertyName == null){
            myPropertyName = new StringBuffer();            
        }
        return myPropertyName;
    }

    private StringBuffer getPropstatDescription() {
        if (myPropstatDescription == null){
            myPropstatDescription = new StringBuffer();            
        }
        return myPropstatDescription;
    }

    private StringBuffer getDescription() {
        if (myDescription == null){
            myDescription = new StringBuffer();            
        }
        return myDescription;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (parent == DAVElement.PROP) {
            getPropertyName().setLength(0);
            if (DAVElement.SVN_DAV_PROPERTY_NAMESPACE.equals(element.getNamespace())) {
                getPropertyName().append(SVNProperty.SVN_PREFIX);
            } else if (DAVElement.DAV_NAMESPACE.equals(element.getNamespace())) {
                getPropertyName().append(DAVElement.DAV_NAMESPACE);
            }
            getPropertyName().append(element.getName());
        } else if (element == DAVElement.PROPSTAT) {
            myPropstatContainsError = false;
        }
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == DAVElement.MULTISTATUS) {
            if (myResponseContainsError) {
                String description = null;
                if (getDescription().length() == 0) {
                    description = "The request response contained at least one error";
                } else {
                    description = getDescription().toString();
                }
                myError = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, description);
            }
        } else if (element == DAVElement.RESPONSE_DESCRIPTION) {
            if (parent == DAVElement.PROPSTAT) {
                getPropstatDescription().append(cdata);
            } else {
                if (getDescription().length() != 0) {
                    getDescription().append('\n');
                }
                getDescription().append(cdata);
            }
        } else if (element == DAVElement.STATUS) {
            try {
                HTTPStatus status = HTTPStatus.createHTTPStatus(cdata.toString());
                if (parent != DAVElement.PROPSTAT) {
                    myResponseContainsError |= status.getCode() < 200 || status.getCode() >= 300;
                } else {
                    myPropstatContainsError = status.getCode() < 200 || status.getCode() >= 300;
                }
            } catch (ParseException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED,
                        "The response contains a non-conforming HTTP status line"));

            }
        } else if (element == DAVElement.PROPSTAT) {
            myResponseContainsError |= myPropstatContainsError;
            getDescription().append("Error setting property '");
            getDescription().append(getPropertyName());
            getDescription().append("':");
            getDescription().append(getPropstatDescription());
        }
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
