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

package org.tmatesoft.svn.util;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author TMate Software Ltd.
 *
 */
public class TimeUtil {
	
    private static final DateFormat ISO8601_FORMAT_OUT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'000Z'");
    private static final DateFormat ISO8601_FORMAT_IN = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    static {
        ISO8601_FORMAT_IN.setTimeZone(TimeZone.getTimeZone("GMT"));
        ISO8601_FORMAT_OUT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    public static final void formatDate(Date date, StringBuffer buffer) {
    	ISO8601_FORMAT_OUT.format(date, buffer, new FieldPosition(0));
    }

    public static final String formatDate(Date date) {
        if (date == null || date.getTime() == 0) {
            return null;
        }
    	return ISO8601_FORMAT_OUT.format(date);
    }
    
    public static final Date parseDate(String str) {
    	if (str == null) {
    		return new Date(0);
    	}
        // truncate last nanoseconds.
        str = str.substring(0, 23);
    	try {
			return ISO8601_FORMAT_IN.parse(str);
		} catch (ParseException e) {
		}
		return new Date(0);
    }
    
    public static final String toHumanDate(String str) {
        if (str == null) {
            return "";
        }
        str = str.replace('T', ' ');
        str = str.substring(0, 19) + 'Z';
        return str;
    }
}
