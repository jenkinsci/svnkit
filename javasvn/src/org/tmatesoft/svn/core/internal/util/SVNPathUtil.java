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
package org.tmatesoft.svn.core.internal.util;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNPathUtil {
    
    public static String append(String f, String s) {
        f = f == null ? "" : f;
        s = s == null ? "" : s;
        if ("".equals(f)) {
            return s;
        } else if ("".equals(s)) {
            return f;
        }
        StringBuffer result = new StringBuffer(f.length() + s.length());
        for(int i = 0; i < f.length(); i++) {
            char ch = f.charAt(i);
            if (i + 1 == f.length() && ch == '/') {
                break;
            }
            result.append(ch);
        }
        for(int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (i == 0 && ch != '/') {
                result.append('/');
            }
            result.append(ch);
        }
        return result.toString();
    }
    
    public static String removeTail(String path) {
        int index = path.length() - 1;
        while(index >= 0) {
            if (path.charAt(index) == '/') {
                return path.substring(0, index); 
            }
            index--;
        }
        return "";
    }
}
