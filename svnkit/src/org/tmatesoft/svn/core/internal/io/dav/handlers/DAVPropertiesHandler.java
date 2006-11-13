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

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.DAVProperties;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPStatus;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.xml.sax.Attributes;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DAVPropertiesHandler extends BasicDAVHandler {
	
	public static StringBuffer generatePropertiesRequest(StringBuffer body, DAVElement[] properties) {
        body = body == null ? new StringBuffer() : body;
        body.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><propfind xmlns=\"DAV:\">");
        if (properties != null) {
            body.append("<prop>");
            for (int i = 0; i < properties.length; i++) {
                body.append("<");
                body.append(properties[i].getName());
                body.append(" xmlns=\"");
                body.append(properties[i].getNamespace());
                body.append("\"/>");
            }
            body.append("</prop></propfind>");
        } else {
            body.append("<allprop/></propfind>");
        }
        return body;

	}
    
    private static final Set PROP_ELEMENTS = new HashSet();
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
    }
    
    private DAVProperties myCurrentResource;
    private int myStatusCode;
    private String myEncoding;
    private Map myResources;
    private Map myCurrentProperties;
    
    public DAVPropertiesHandler() {
        init();
    }
    
    public Map getDAVProperties() {
        return myResources;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == DAVElement.RESPONSE) {
            if (myCurrentResource != null) {
                invalidXML();
            }
            myCurrentResource = new DAVProperties();
            myCurrentProperties = new HashMap();
            myStatusCode = 0;
        } else if (element == DAVElement.PROPSTAT) {
            myStatusCode = 0;
        } else if (element == DAVElement.COLLECTION) {
            myCurrentResource.setCollection(true);
        } else {
            myEncoding = attrs.getValue("encoding");
        }
	}

	protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        DAVElement name = null;
        String value = null;
        if (element == DAVElement.RESPONSE) {
            if (myCurrentResource.getURL() == null) {
                invalidXML();
            }
            myResources.put(myCurrentResource.getURL(), myCurrentResource);
            myCurrentResource = null;            
            return;
        } else if (element == DAVElement.PROPSTAT) {
            if (myStatusCode != 0) {
                for (Iterator names = myCurrentProperties.keySet().iterator(); names.hasNext();) {
                    DAVElement propName = (DAVElement) names.next();
                    String propValue = (String) myCurrentProperties.get(propName);
                    if (myStatusCode == 200) {
                        myCurrentResource.setProperty(propName, propValue);
                    }
                }
                myCurrentProperties.clear();
            } else {
                invalidXML();
            }
            return;
        } else if (element == DAVElement.STATUS) {
            if (cdata == null) {
                invalidXML();
            }
            try {
                HTTPStatus status = HTTPStatus.createHTTPStatus(cdata.toString());
                if (status == null) {
                    invalidXML();
                }
                myStatusCode = status.getCode();
            } catch (ParseException e) {
                invalidXML();
            }
            return;
        } else if (element == DAVElement.HREF) {
            if (parent == DAVElement.RESPONSE) {
                // set resource url
                String path = cdata.toString();
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                myCurrentResource.setURL(path);
                return;
            }
            name = parent;
            if (name == null) {
                return;
            }
            value = cdata.toString();
        } else if (cdata != null) {
            if (myCurrentProperties.containsKey(element)) {
                // was already set with href.
                return;
            }
            name = element;
            if (myEncoding == null) {
                value = cdata.toString();
            } else if ("base64".equals(myEncoding)) {
                byte[] buffer = allocateBuffer(cdata.length()); 
                int length = SVNBase64.base64ToByteArray(new StringBuffer(cdata.toString().trim()), buffer);
                try {
                    value = new String(buffer, 0, length, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    value = new String(buffer, 0, length);
                }                
            } else {
                invalidXML();
            }
            myEncoding = null;
        }
        if (name != null && value != null) {
            myCurrentProperties.put(name, value);
        }
	}
    
    public void setDAVProperties(Map result) {
        myResources = result;
    }

}