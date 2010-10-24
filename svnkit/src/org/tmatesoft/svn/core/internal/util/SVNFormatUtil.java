/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNFormatUtil {

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

    public static String getHexNumberFromByte(byte b) {
        int lo = b & 0xf;
        int hi = (b >> 4) & 0xf;
        return Integer.toHexString(hi) + Integer.toHexString(lo);
    }

    public static void appendHexNumber(StringBuffer target, byte b) {
        int lo = b & 0xf;
        int hi = (b >> 4) & 0xf;
        target.append(HEX[hi]);
        target.append(HEX[lo]);
    }
    
    private static char[] HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

}
