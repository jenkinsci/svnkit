/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.util;

import java.io.File;
import java.util.StringTokenizer;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNUtil {

    public static String getPath(File file) {
        String path;
        String rootPath;
        path = file.getAbsolutePath();
        rootPath = new File("").getAbsolutePath();
        path = path.replace(File.separatorChar, '/');
        rootPath = rootPath.replace(File.separatorChar, '/');
        if (path.startsWith(rootPath)) {
            path = path.substring(rootPath.length());
        }
        // remove all "./"
        path = condensePath(path);
        path = PathUtil.removeLeadingSlash(path);
        path = PathUtil.removeTrailingSlash(path);
        path = path.replace('/', File.separatorChar);
        if (path.trim().length() == 0) {
            path = ".";
        }
        return path;
    }

    private static String condensePath(String path) {
        StringBuffer result = new StringBuffer();
        for (StringTokenizer tokens = new StringTokenizer(path, "/", true); tokens
                .hasMoreTokens();) {
            String token = tokens.nextToken();
            if (".".equals(token)) {
                if (tokens.hasMoreTokens()) {
                    String nextToken = tokens.nextToken();
                    if (!nextToken.equals("/")) {
                        result.append(nextToken);
                    }
                }
                continue;
            }
            result.append(token);
        }
        return result.toString();
    }

    public static String formatString(String str, int chars, boolean left) {
        if (str.length() > chars) {
            return str.substring(0, chars);
        }
        StringBuffer formatted = new StringBuffer();
        if (left) {
            formatted.append(str);
        }
        for (int i = 0; i < chars - str.length(); i++) {
            formatted.append(' ');
        }
        if (!left) {
            formatted.append(str);
        }
        return formatted.toString();
    }
}
