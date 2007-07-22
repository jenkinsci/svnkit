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
package org.tmatesoft.svn.core.internal.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNDate extends Date {
    
    public static final SVNDate NULL = new SVNDate(0,0);
    
    private static final DateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    static {
        FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    private static final Calendar CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"), new Locale("en", "US"));
    private static final char[] DATE_SEPARATORS = {'-','-','T',':',':','.','Z'}; 

    private int myMicroSeconds;
    
    private SVNDate(long time, int micro) {
        super((1000*time + micro)/1000);
        myMicroSeconds = micro >= 0 ? micro % 1000 : 1000 + (micro % 1000);
    }
    
    public String format() {
        String formatted = null;
        synchronized (FORMAT) {
             formatted = FORMAT.format(this);
        }
        int micros = myMicroSeconds;
        int m1 = micros%10;
        int m2 = (micros/10)%10;
        int m3 = (micros)/100;
        formatted += m3;
        formatted += m2;
        formatted += m1;
        formatted += 'Z';
        return formatted;
    }
    
    public static SVNDate parseDatestamp(String str) throws SVNException {
        if (str == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_DATE);
            SVNErrorManager.error(err);
        }
        
        int index = 0;
        int charIndex = 0;
        int startIndex = 0;
        int[] result = new int[7];
        int microseconds = 0;
        int timeZoneInd = -1;
        while(index < DATE_SEPARATORS.length && charIndex < str.length()) {
            if (str.charAt(charIndex) == '-') {
                if (index > 1) {
                    timeZoneInd = charIndex;
                }
            } else if (str.charAt(charIndex) == '+') {
                timeZoneInd = charIndex;
            }
            if (str.charAt(charIndex) == DATE_SEPARATORS[index] || 
                    (index == 5 && str.charAt(charIndex) == DATE_SEPARATORS[index + 1])) {
                if (index == 5 && str.charAt(charIndex) == DATE_SEPARATORS[index + 1]) {
                    index++;
                }
                String segment = str.substring(startIndex, charIndex);
                if (segment.length() == 0) {
                    result[index] = 0;
                } else if (index + 1 < DATE_SEPARATORS.length) {
                    result[index] = Integer.parseInt(segment);
                } else {
                    result[index] = Integer.parseInt(segment.substring(0, Math.min(3, segment.length())));
                    if (segment.length() > 3) {
                        microseconds = Integer.parseInt(segment.substring(3));    
                    }
                }
                startIndex = charIndex + 1;
                index++;
            }
            charIndex++;
        }
        if (index < DATE_SEPARATORS.length) {
            String segment = str.substring(startIndex);
            if (segment.length() == 0) {
                result[index] = 0;
            } else {
                result[index] = Integer.parseInt(segment);
            }
        }
        
        int year = result[0];
        int month = result[1];
        int date = result[2];

        int hour = result[3];
        int min = result[4];
        int sec = result[5];
        int ms = result[6];

        String timeZoneId = null;
        if (timeZoneInd != -1 && timeZoneInd < str.length() - 1 && str.indexOf('Z') == -1 && str.indexOf('z') == -1) {
            timeZoneId = "GMT" + str.substring(timeZoneInd);
        }
        synchronized (CALENDAR) {
            CALENDAR.clear();
            TimeZone oldTimeZone = null;
            if (timeZoneId != null) {
                oldTimeZone = CALENDAR.getTimeZone();
                CALENDAR.setTimeZone(TimeZone.getTimeZone(timeZoneId));
            } else if (str.indexOf('Z') == -1 && str.indexOf('z') == -1) {
                oldTimeZone = CALENDAR.getTimeZone();
                CALENDAR.setTimeZone(TimeZone.getDefault());
            }

            CALENDAR.set(year, month - 1, date, hour, min, sec);
            CALENDAR.set(Calendar.MILLISECOND, ms);
            SVNDate resultDate = new SVNDate(CALENDAR.getTimeInMillis(), microseconds);
            if (oldTimeZone != null) {
                CALENDAR.setTimeZone(oldTimeZone);
            }
            return resultDate;
        }
    }

    public int hashCode() {
        return 31 * super.hashCode() + myMicroSeconds;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof SVNDate) {
            SVNDate date = (SVNDate) obj;
            return getTime() == date.getTime() && myMicroSeconds == date.myMicroSeconds;
        } 
        return super.equals(obj);
    }

    public boolean before(Date when) {
        if (super.equals(when) && when instanceof SVNDate) {
            return myMicroSeconds < ((SVNDate) when).myMicroSeconds;
        }
        return super.before(when);
    }

    public boolean after(Date when) {
        if (super.equals(when) && when instanceof SVNDate) {
            return myMicroSeconds > ((SVNDate) when).myMicroSeconds;
        }
        return super.after(when);
    }

    public int compareTo(Date anotherDate) {
        int result = super.compareTo(anotherDate);
        if (result == 0 && anotherDate instanceof SVNDate) {
            SVNDate date = (SVNDate) anotherDate;
            return (myMicroSeconds < date.myMicroSeconds ? -1 : (myMicroSeconds == date.myMicroSeconds ? 0 : 1));
        }
        return result;
    }
    
}
