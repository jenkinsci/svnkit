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
package org.tmatesoft.svn.core.internal.util;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNEncodingUtil {

    public static String uriEncode(String src) {
        StringBuffer sb = null;
        byte[] bytes;
        try {
            bytes = src.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            bytes = src.getBytes();
        }
        for (int i = 0; i < bytes.length; i++) {
            int index = bytes[i] & 0xFF;
            if (uri_char_validity[index] > 0) {
                if (sb != null) {
                    sb.append((char) bytes[i]);
                }
                continue;
            } else {
                if (sb == null) {                    
                    sb = new StringBuffer();
                    sb.append(new String(bytes, 0, i));
                }
                sb.append("%");
                
                sb.append(Character.toUpperCase(Character.forDigit((index & 0xF0) >> 4, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(index & 0x0F, 16)));
            }
        }
        return sb == null ? src : sb.toString();
    }
    
    public static String uriDecode(String src) {
        // this is string in ASCII-US encoding.
        boolean query = false;
        boolean decoded = false;
        ByteArrayOutputStream bos = new ByteArrayOutputStream(src.length());
        for(int i = 0; i < src.length(); i++) {
            byte ch = (byte) src.charAt(i);
            if (ch == '?') {
                query = true;
            } else if (ch == '+' && query) {
                ch = ' ';
            } else if (ch == '%' && i + 2 < src.length() &&
                    isHexDigit(src.charAt(i + 1)) && 
                    isHexDigit(src.charAt(i + 2))) {
                ch = (byte) (hexValue(src.charAt(i + 1))*0x10 + hexValue(src.charAt(i + 2)));
                decoded = true;
                i += 2;    
            }
            bos.write(ch);
        }
        if (!decoded) {
            return src;
        }
        try {
            return new String(bos.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return src;
    }
    
    public static String xmlEncodeCDATA(String src) {
        StringBuffer sb = new StringBuffer(src.length());
        for(int i = 0; i < src.length(); i++) {
            char ch = src.charAt(i);
            switch (ch) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '\r':
                    sb.append("&#13;");
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static String xmlEncodeAttr(String src) {
        StringBuffer sb = new StringBuffer(src.length());
        for(int i = 0; i < src.length(); i++) {
            char ch = src.charAt(i);
            switch (ch) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                case '\"':
                    sb.append("&quot;");
                    break;
                case '\r':
                    sb.append("&#13;");
                    break;
                case '\n':
                    sb.append("&#10;");
                    break;
                case '\t':
                    sb.append("&#9;");
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static boolean isXMLSafe(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 && ch != 0x0A && ch != 0x0D && ch != 0x09 && ch != 0x08) {
                return false;
            }
        }
        return true;
    }
    
    private static boolean isHexDigit(char ch) {
        return Character.isDigit(ch) ||
             (Character.toUpperCase(ch) >= 'A' && Character.toUpperCase(ch) <= 'F');
    }
    
    private static int hexValue(char ch) {
        if (Character.isDigit(ch)) {
            return ch - '0';
        }
        ch = Character.toUpperCase(ch);
        return (ch - 'A') + 0x0A;
    }
    
    private static final byte[] uri_char_validity = new byte[] {
        0, 0, 0, 0, 0, 0, 0, 0,   0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,   0, 0, 0, 0, 0, 0, 0, 0,
        0, 1, 0, 0, 1, 0, 1, 1,   1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1,   1, 1, 1, 0, 0, 1, 0, 0,

        /* 64 */
        1, 1, 1, 1, 1, 1, 1, 1,   1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1,   1, 1, 1, 0, 0, 0, 0, 1,
        0, 1, 1, 1, 1, 1, 1, 1,   1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1,   1, 1, 1, 0, 0, 0, 1, 0,

        /* 128 */
        0, 0, 0, 0, 0, 0, 0, 0,   0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,   0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,   0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,   0, 0, 0, 0, 0, 0, 0, 0,

        /* 192 */
        0, 0, 0, 0, 0, 0, 0, 0,   0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,   0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,   0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,   0, 0, 0, 0, 0, 0, 0, 0,
      };
}
