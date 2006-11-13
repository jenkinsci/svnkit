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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
class HTTPParser {
    
    public static HTTPStatus parseStatus(InputStream is) throws IOException, ParseException {
        String line = null;
        do {
            line = readLine(is);
        } while (line != null && line.length() == 0);
        if (line == null) {
            throw new ParseException("can not read HTTP status line", 0);
        }
        return HTTPStatus.createHTTPStatus(line);
    }
    
    public static String readLine(InputStream is) throws IOException {
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
    
    public static byte[] readPlainLine(InputStream is) throws IOException {
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