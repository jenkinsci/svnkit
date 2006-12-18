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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TimeZone;


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
        
        String tzID = System.getProperty("svnkit.keyword.timezone", TimeZone.getDefault().getID());
        TimeZone tz =  null;
        if (tzID != null) {
            tz = TimeZone.getTimeZone(tzID);
        } 
        if (tz == null) {
            tz = TimeZone.getDefault();
        }
        String[] localeID = parseLocaleID(System.getProperty("svnkit.keyword.locale"));
        Locale locale = null;
        if (localeID != null) {
            locale = new Locale(localeID[0], localeID[1], localeID[2]);
        } 
        if (locale == null) {
            locale = Locale.getDefault();
        }
        setHumanTimeZone(tz, locale);
    }
    
    public static void setHumanTimeZone(TimeZone zone, Locale locale) {
        zone = zone == null ? TimeZone.getTimeZone("GMT") : zone;
        locale = locale == null || !isAvailable(locale) ? Locale.getDefault() : locale; 
        synchronized (SHORT_DATE_FORMAT) {
            HUMAN_DATE_FORMAT.setTimeZone(zone);
            HUMAN_DATE_FORMAT.setCalendar(Calendar.getInstance(zone, locale));
        }
    }
    
    
    public static String formatDate(Date date, boolean longFormat) {
        if (longFormat) {
            synchronized (HUMAN_DATE_FORMAT) {
                return HUMAN_DATE_FORMAT.format(date != null ? date : NULL_DATE);
            }
        }
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
    
    private static String[] parseLocaleID(String locale) {
        if (locale == null) {
            return null;
        }
        List parts = new ArrayList();
        for(StringTokenizer tokens = new StringTokenizer(locale, "_"); tokens.hasMoreTokens();) {
            parts.add(tokens.nextToken());
        }
        if (parts.isEmpty() || "".equals(((String) parts.get(0)).trim())) {
            return null;
        }
        String[] result = new String[] {"", "", ""};
        result[0] = (String) parts.get(0);
        if (parts.size() > 1) {
            result[1] = (String) parts.get(1);
            if (parts.size() > 2) {
                result[2] = (String) parts.get(2);
            }
        }
        return result;
    }
    
    private static boolean isAvailable(Locale l) {
        Locale[] available = Locale.getAvailableLocales();
        for (int i = 0; i < available.length; i++) {
            if (available[i].equals(l)) {
                return true;
            }
        }
        return false;
    }
}
