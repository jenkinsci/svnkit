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
package org.tmatesoft.svn.core.internal.io.dav.http;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class HTTPHeader {

    public static final String CONNECTION_HEADER = "Connection";
    public static final String PROXY_CONNECTION_HEADER = "Proxy-Connection";
    public static final String TRANSFER_ENCODING_HEADER = "Transfer-Encoding";
    public static final String CONTENT_LENGTH_HEADER = "Content-Length";
    public static final String CONTENT_ENCODING_HEADER = "Content-Encoding";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String AUTHENTICATE_HEADER = "WWW-Authenticate";
    public static final String PROXY_AUTHENTICATE_HEADER = "Proxy-Authenticate";
    public static final String LOCATION_HEADER = "Location";
    public static final String LOCK_OWNER_HEADER = "X-SVN-Lock-Owner";
    public static final String CREATION_DATE_HEADER = "X-SVN-Creation-Date";
    public static final String SVN_VERSION_NAME_HEADER = "X-SVN-Version-Name";
    public static final String SVN_OPTIONS_HEADER = "X-SVN-Options";
    public static final String TEXT_MD5 = "X-SVN-Result-Fulltext-MD5";
    public static final String BASE_MD5 = "X-SVN-Base-Fulltext-MD5";
    public static final String LOCK_TOKEN_HEADER = "Lock-Token";
    public static final String IF_HEADER = "If";
    public static final String DEPTH_HEADER = "Depth";
    public static final String LABEL_HEADER = "Label";
    public static final String DESTINATION_HEADER = "Destination";

    private Map myHeaders;

    public HTTPHeader() {
    }

    public String toString() {
        StringBuffer representation = new StringBuffer();
        if (myHeaders == null) {
            return representation.toString();
        }
        
        for(Iterator headers = myHeaders.keySet().iterator(); headers.hasNext();){
            String headerName = (String)headers.next();
            Collection headerValues = (Collection)myHeaders.get(headerName);
            for(Iterator values = headerValues.iterator(); values.hasNext();){
                String value = (String)values.next();
                representation.append(headerName);
                representation.append(": ");
                representation.append(value);
                representation.append(HTTPRequest.CRLF);
            }
        }
        return representation.toString();
    }

    public void addHeaderValue(String name, String value) {
        Map headers = getHeaders();
        Collection values = (Collection)headers.get(name);
        if (values == null) {
            values = new LinkedList();
            headers.put(name, values);
        }
        values.add(value);
    }

    public Collection getHeaderValues(String name) {
        if (myHeaders == null) {
            return null;
        }
        return (Collection)myHeaders.get(name);
    }
    
    public String getFirstHeaderValue(String name){
        if (myHeaders == null) {
            return null;
        }
        
        LinkedList values = (LinkedList)myHeaders.get(name);
        if (values != null) {
            return (String)values.getFirst();
        }
        return null;
    }
    
    public boolean hasHeader(String name){
        if (myHeaders != null){
            return myHeaders.containsKey(name);
        }
        return false;
    }
    
    public void removeHeader(String name){
        if (myHeaders != null) {
            myHeaders.remove(name);
        }
    }
    
    public void setHeaderValue(String name, String value){
        Map headers = getHeaders();
        Collection values = (Collection)headers.get(name);
        if (values == null) {
            values = new LinkedList();
            headers.put(name, values);
        }
        values.clear();
        values.add(value);
    }
    
    private Map getHeaders() {
        if (myHeaders == null) {
            myHeaders = new TreeMap();
        }
        return myHeaders;
    }

    public static HTTPHeader parseHeader(InputStream is) throws IOException, ParseException {
        HTTPHeader headers = new HTTPHeader();
        String name = null;
        StringBuffer value = null;
        for (; ;) {
            String line = HTTPParser.readLine(is);
            if ((line == null) || (line.trim().length() < 1)) {
                break;
            }
            if ((line.charAt(0) == ' ') || (line.charAt(0) == '\t')) {
                if (value != null) {
                    value.append(' ');
                    value.append(line.trim());
                }
            } else {
                if (name != null) {
                    headers.addHeaderValue(name, value.toString());
                }
                
                int colon = line.indexOf(":");
                if (colon < 0) {
                    throw new ParseException("Unable to parse header: " + line, 0);
                }
                name = line.substring(0, colon).trim();
                value = new StringBuffer(line.substring(colon + 1).trim());
            }
    
        }
    
        if (name != null) {
            headers.addHeaderValue(name, value.toString());
        }
        return headers;
    }
}
