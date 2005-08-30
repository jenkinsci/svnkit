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

package org.tmatesoft.svn.core.internal.util;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * @version 1.0
 * @author TMate Software Ltd.
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
        ISO8601_FORMAT_OUT.format(date, buffer, new FieldPosition(0));
    }

    public static String formatDate(Date date) {
        if (date == null || date.getTime() == 0) {
            return null;
        }
        return ISO8601_FORMAT_OUT.format(date);
    }

    public static Date parseDate(String str) {
        if (str == null) {
            return NULL;
        }
        try {
            int year = Integer.parseInt(str.substring(0, 4));
            int month = Integer.parseInt(str.substring(5, 7));
            int date = Integer.parseInt(str.substring(8, 10));

            int hour = Integer.parseInt(str.substring(11, 13));
            int min = Integer.parseInt(str.substring(14, 16));
            int sec;
            int ms;
            if (str.charAt(18) == '.') {
                sec = Integer.parseInt(str.substring(17, 18));
                ms = Integer.parseInt(str.substring(19, 22));
            } else {
                sec = Integer.parseInt(str.substring(17, 19));
                ms = Integer.parseInt(str.substring(20, 23));
            }

            CALENDAR.clear();
            CALENDAR.set(year, month - 1, date, hour, min, sec);
            CALENDAR.set(Calendar.MILLISECOND, ms);
            return CALENDAR.getTime();
        } catch (Throwable th) {
            //
        }
        return NULL;
    }

    public static long parseDateAsLong(String str) {
        if (str == null) {
            return -1;
        }
        int year = Integer.parseInt(str.substring(0, 4));
        int month = Integer.parseInt(str.substring(5, 7));
        int date = Integer.parseInt(str.substring(8, 10));

        int hour = Integer.parseInt(str.substring(11, 13));
        int min = Integer.parseInt(str.substring(14, 16));
        int sec = Integer.parseInt(str.substring(17, 19));
        int ms = Integer.parseInt(str.substring(20, 23));

        CALENDAR.clear();
        CALENDAR.set(year, month - 1, date, hour, min, sec);
        CALENDAR.set(Calendar.MILLISECOND, ms);
        return CALENDAR.getTimeInMillis();
    }
}
