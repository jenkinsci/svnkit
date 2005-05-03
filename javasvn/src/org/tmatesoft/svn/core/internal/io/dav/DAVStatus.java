/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.util.Map;


/**
 * @author Alexander Kitaev
 */
public class DAVStatus {
    
    public static DAVStatus parse(String str) {
        int code = -1;
        String message = null;
        String http = null;
        StringBuffer sb = new StringBuffer();
        for(int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (sb.length() == 0) {
                    continue;
                }
                if (http == null) {
                    http = sb.toString();
                } else if (code < 0) {
                    code = Integer.parseInt(sb.toString());
                } else {
                    // has all, just message
                    sb.append(ch);
                    continue;
                }
                sb = sb.delete(0, sb.length());
                continue;
            }
            sb.append(ch);
        }
        if (message == null) {
            message = sb.toString();
        }
        return new DAVStatus(code, message, http);
    }
    
    private int myResponseCode;
    private String myMessage;
    private Map myResponseHeader;
    private String myText;
    private String myHTTP;
    
    public DAVStatus(int responseCode, String message, String http) {
        myResponseCode = responseCode;
        myMessage = message;
        myHTTP = http;
    }
    
    public String getMessage() {
        return myMessage;
    }
    public int getResponseCode() {
        return myResponseCode;
    }
    public Map getResponseHeader() {
        return myResponseHeader;
    }
    public void setResponseHeader(Map header) {
        myResponseHeader = header;
    }

    public boolean isHTTP10() {
        return myHTTP != null && myHTTP.endsWith("/1.0");
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getResponseCode());
        sb.append(' ');
        sb.append(getMessage());
        if (myText != null) {
            sb.append("\n");
            sb.append(myText);
        }
        return sb.toString();
    }

    public void setErrorText(String text) {
        myText = text;
    }
    
    public String getErrorText() {
        return myText;
    }
}
