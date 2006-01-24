/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
class HTTPParser {
    
    public static HTTPStatus parseStatus(InputStream is) throws IOException {
        String line = null;
        do {
            line = readLine(is);
        } while (line != null && line.length() == 0);
        if (line == null) {
            throw new IOException("can not read HTTP status line");
        }
        return HTTPStatus.createHTTPStatus(line);
    }
    
    public static Map parseHeader(InputStream is) throws IOException {
        Map headers = new HashMap();
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
                    headers.put(name, value.toString());
                }
                int colon = line.indexOf(":");
                if (colon < 0) {
                    throw new IOException("Unable to parse header: " + line);
                }
                name = line.substring(0, colon).trim();
                value = new StringBuffer(line.substring(colon + 1).trim());
            }
    
        }
        if (name != null) {
            headers.put(name, value.toString());
        }
        
        return headers;
    }

    private static String readLine(InputStream is) throws IOException {
        byte[] bytes = readPlainLine(is);
        if (bytes == null) {
            return null;
        }
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\n') {
            length--;
            if (length > 0 && bytes[length - 1] == '\r') {
                length--;
            }
        }
        return new String(bytes, 0, length);
    }
    
    private static byte[] readPlainLine(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int ch;
        while ((ch = is.read()) >= 0) {
            buf.write(ch);
            if (ch == '\n') {
                break;
            }
        }
        if (buf.size() == 0) {
            return null;
        }
        return buf.toByteArray();
    }

    public static Map parseAuthParameters(String source) {
        if (source == null) {
            return null;
        }
        source = source.trim();
        Map parameters = new HashMap();
        // parse strings: name="value" or name=value
        int index = source.indexOf(' ');
        if (index <= 0) {
            return null;
        }
        String method = source.substring(0, index);
        parameters.put("", method);
    
        source = source.substring(index).trim();
        if ("Basic".equalsIgnoreCase(method)) {
            if (source.indexOf("realm=") >= 0) {
                source = source.substring(source.indexOf("realm=") + "realm=".length());
                source = source.trim();
                if (source.startsWith("\"")) {
                    source = source.substring(1);
                }
                if (source.endsWith("\"")) {
                    source = source.substring(0, source.length() - 1);
                }
                parameters.put("realm", source);
            }
            return parameters;
        }
        char[] chars = source.toCharArray();
        int tokenIndex = 0;
        boolean parsingToken = true;
        String name = null;
        String value;
        int quotesCount = 0;
    
        for(int i = 0; i < chars.length; i++) {
            if (parsingToken) {
                if (chars[i] == '=') {
                    name = new String(chars, tokenIndex, i - tokenIndex);
                    name = name.trim();
                    tokenIndex = i + 1;
                    parsingToken = false;
                }
            } else {
                if (chars[i] == '\"') {
                    quotesCount = quotesCount > 0 ? 0 : 1;
                } else if ( i + 1 >= chars.length || (chars[i] == ',' && quotesCount == 0)) {
                    value = new String(chars, tokenIndex, i - tokenIndex);
                    value = value.trim();
                    if (value.charAt(0) == '\"' && value.charAt(value.length() - 1) == '\"') {
                        value = value.substring(1);
                        value = value.substring(0, value.length() - 1);
                    }
                    parameters.put(name, value);
                    tokenIndex = i + 1;
                    parsingToken = true;
                }
            }
        }
        return parameters;
    }

    public static StringBuffer getCanonicalPath(String path, StringBuffer target) {
        target = target == null ? new StringBuffer() : target;
        if (path.startsWith("http:") || path.startsWith("https:")) {
            target.append(path);
            return target;
        }
    
        int end = path.length() - 1;
        for(int i = 0; i <= end; i++) {
            char ch = path.charAt(i);
            switch (ch) {
            case '/':
                if (i == end && i != 0) {
                    // skip trailing slash
                    break;
                } else if (i > 0 && path.charAt(i - 1) == '/') {
                    // skip duplicated slashes
                    break;
                }
            default:
                target.append(ch);
            }
        }
        return target;
    
    }
}