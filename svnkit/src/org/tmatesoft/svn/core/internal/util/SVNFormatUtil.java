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
package org.tmatesoft.svn.core.internal.util;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.tmatesoft.svn.core.wc.ISVNOptions;


/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNFormatUtil {
    
    private static final DateFormat HUMAN_DATE_FORMAT = 
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss' 'ZZZZ' ('E', 'dd' 'MMM' 'yyyy')'");
    private static final DateFormat SHORT_DATE_FORMAT = 
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss'Z'");
    
    private static final Date NULL_DATE = new Date(0);
    
    static {
        SHORT_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    public static String formatHumanDate(Date date, ISVNOptions options) {
        DateFormat df = options == null ? null : options.getKeywordDateFormat();
        if (df == null) {
            df = HUMAN_DATE_FORMAT;
        }
        synchronized (df) {
            return df.format(date != null ? date : NULL_DATE);
        }
    }
    
    public static String formatDate(Date date) {
        synchronized (SHORT_DATE_FORMAT) {
            return SHORT_DATE_FORMAT.format(date != null ? date : NULL_DATE);
        }
    }
    
    public static String formatString(String src, int width, boolean left) {
        if (src.length() > width) {
            return src.substring(0, width);
        }
        StringBuffer formatted = new StringBuffer();
        if (left) {
            formatted.append(src);
        }
        for (int i = 0; i < width - src.length(); i++) {
            formatted.append(' ');
        }
        if (!left) {
            formatted.append(src);
        }
        return formatted.toString();
    }
    
    // path relative to the program home directory, or absolute path
    public static String formatPath(File file) {
        String path;
        String rootPath;
        path = file.getAbsolutePath();
        rootPath = new File("").getAbsolutePath();
        path = path.replace(File.separatorChar, '/');
        rootPath = rootPath.replace(File.separatorChar, '/');
        if (path.equals(rootPath)) {
            path  = "";
        } else if (path.startsWith(rootPath + "/")) {
            path = path.substring(rootPath.length() + 1);
        }
        // remove all "./"
        path = condensePath(path);
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

    public static String getHexNumberFromByte(byte b) {
        int lo = b & 0xf;
        int hi = (b >> 4) & 0xf;
        String hex = Integer.toHexString(hi) + Integer.toHexString(lo);
        return hex;
    }
}
