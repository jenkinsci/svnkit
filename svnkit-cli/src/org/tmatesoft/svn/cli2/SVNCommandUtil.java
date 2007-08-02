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
package org.tmatesoft.svn.cli2;

import java.io.File;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCommandUtil {
    
    public static String getLocalPath(String path) {
        path = path.replace('/', File.separatorChar);
        if ("".equals(path)) {
            path = ".";
        }
        return path;
    }

    public static String formatString(String str, int chars, boolean left) {
        if (str.length() > chars) {
            return str.substring(0, chars);
        }
        StringBuffer formatted = new StringBuffer();
        if (left) {
            formatted.append(str);
        }
        for(int i = 0; i < chars - str.length(); i++) {
            formatted.append(' ');
        }
        if (!left) {
            formatted.append(str);
        }
        return formatted.toString();
    }
}
