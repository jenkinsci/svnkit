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

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNTimeUtil {

    private static final DateFormat ISO8601_FORMAT_OUT = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'000Z'");

    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    private static final Date NULL = new Date(0);

    static {
        ISO8601_FORMAT_OUT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static void formatDate(Date date, StringBuffer buffer) {
        if (date instanceof SVNDate) {
            SVNDate extendedDate = (SVNDate) date;
            buffer.append(extendedDate.format());
            return;
        }

        synchronized (ISO8601_FORMAT_OUT) {
            ISO8601_FORMAT_OUT.format(date, buffer, new FieldPosition(0));
        }
    }

    public static String formatDate(Date date) {
        return formatDate(date, false);
    }

    public static String formatDate(Date date, boolean formatZeroDate) {
        if (date == null) {
            return null;
        } else if (!formatZeroDate && date.getTime() == 0) {
            return null;
        }
        
        if (date instanceof SVNDate) {
            SVNDate extendedDate = (SVNDate) date;
            return extendedDate.format();
        }
        
        synchronized (ISO8601_FORMAT_OUT) {
            return ISO8601_FORMAT_OUT.format(date);
        }
    }

    public static Date parseDate(String str) {
        if (str == null) {
            return NULL;
        }
        try {
            return SVNDate.parseDatestamp(str);
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().info(th);
        }
        return NULL;        
    }

    public static Date parseDateString(String str) throws SVNException {
        try {
            return SVNDate.parseDatestamp(str);
        } catch (SVNException svne) {
            throw svne;
        } catch (Throwable th) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_DATE);
            SVNErrorManager.error(err, th);
        }
        return NULL;        
    }
    
    private static final char[] DATE_SEPARATORS = {'-','-','T',':',':','.','Z'}; 

    public static long parseDateAsLong(String str) {
        if (str == null) {
            return -1;
        }
        int index = 0;
        int charIndex = 0;
        int startIndex = 0;
        int[] result = new int[7];
        while(index < DATE_SEPARATORS.length && charIndex < str.length()) {
            if (str.charAt(charIndex) == DATE_SEPARATORS[index]) {
                String segment = str.substring(startIndex, charIndex);
                if (segment.length() == 0) {
                    result[index] = 0;
                } else if (index + 1 < DATE_SEPARATORS.length) {
                    result[index] = Integer.parseInt(segment);
                } else {
                    result[index] = Integer.parseInt(segment.substring(0, Math.min(3, segment.length())));
                }
                startIndex = charIndex + 1;
                index++;
            }
            charIndex++;
        }
        int year = result[0];
        int month = result[1];
        int date = result[2];

        int hour = result[3];
        int min = result[4];
        int sec = result[5];
        int ms = result[6];

        synchronized (CALENDAR) {
            CALENDAR.clear();
            CALENDAR.set(year, month - 1, date, hour, min, sec);
            CALENDAR.set(Calendar.MILLISECOND, ms);
            return CALENDAR.getTimeInMillis();
        }
    }
}
