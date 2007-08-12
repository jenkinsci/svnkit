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
package org.tmatesoft.svn.core.internal.server.dav;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class TimeFormatUtil {
    public static final DateFormat RFC1123_FORMAT = new SimpleDateFormat(
            "EEE, d MMM yyyy HH:mm:ss z", Locale.US);

    static {
        RFC1123_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        synchronized (RFC1123_FORMAT) {
            return RFC1123_FORMAT.format(date);
        }
    }
}
