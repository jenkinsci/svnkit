/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNSkel {

    private static final char TYPE_NOTHING = 0;
    private static final char TYPE_SPACE = 1;
    private static final char TYPE_DIGIT = 2;
    private static final char TYPE_PAREN = 3;
    private static final char TYPE_NAME = 4;

    private static final char[] CHAR_TYPES_TABLE = new char[]{
            0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 0, 0, 0, 0, 0, 0, 0, 3, 3, 0, 0, 0, 0, 0, 0,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0,

            /* 64 */
            0, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
            4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 3, 0, 3, 0, 0,
            0, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
            4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 0, 0, 0, 0, 0,

            /* 128 */
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            /* 192 */
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    };

    private static char getType(byte b) {
        return CHAR_TYPES_TABLE[b & 0xFF];
    }

    private static int parseLength(byte[] data, final int offset, final int maxLength, final int limit, int[] readed) {
        final int maxPrefix = limit / 10;
        final int maxDigit = limit % 10;
        final boolean countReaded = readed != null && readed.length == 1;
        int value = 0;
        int pos = offset;
        for (; pos < data.length && pos - offset < maxLength && '0' <= data[pos] && data[pos] <= '9'; pos++) {
            int digit = data[pos] - '0';
            if (value > maxPrefix || (value == maxPrefix && digit > maxDigit)) {
                if (countReaded) {
                    readed[0] = -1;
                }
                return 0;
            }
            value = (value * 10) + digit;
        }
        if (pos == offset) {
            if (countReaded) {
                readed[0] = -1;
            }
            return 0;
        } else {
            if (countReaded && pos < data.length) {
                readed[0] = pos - offset;
            }
            return value;
        }
    }

    private static int writeLength(int value, byte[] data, int maxLength) {
        int i = 0;
        do {
            if (i >= maxLength) {
                return 0;
            }
            if (i >= data.length) {
                return 0;
            }
            data[i] = (byte) ((value % 10) + '0');
            value = value / 10;
            i++;
        } while (value > 0);

        for (int left = 0, right = i - 1; left < right; left++, right--) {
            byte tmp = data[left];
            data[left] = data[right];
            data[right] = tmp;
        }
        return i;
    }

    private static byte[] writeLength(final int value, int maxLength) {
        int tmp = value;
        int length = 0;
        do {
            tmp = tmp / 10;
            length++;
        } while (tmp > 0);

        if (length >= maxLength) {
            return null;
        }

        byte[] data = new byte[length];
        writeLength(value, data, length);
        return data;
    }

    private static void error(char type) throws SVNException {
        SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.FS_MALFORMED_SKEL, "Malformed{0}{1} skeleton", new Object[]{type == TYPE_NOTHING ? "" : " ",
                type == TYPE_NOTHING ? "" : String.valueOf((int) type)});
        SVNErrorManager.error(error, SVNLogType.DEFAULT);
    }

    public static SVNSkel parse(byte[] data) {
        return parse(data, 0, data.length);        
    }

    public static SVNSkel parse(byte[] data, int offset, int length) {
        if (data == null || length == 0 || offset + length > data.length) {
            return null;
        }

        byte cur = data[offset];
        if (cur == '(') {
            return parseList(data, offset, length);
        }
        if (getType(cur) == TYPE_NAME) {
            return parseImplicitAtom(data, offset, length);
        }
        return parseExplicitAtom(data, offset, length);
    }

    private static SVNSkel parseList(byte[] data, final int offset, final int length) {
        final int end = offset + length;        
        if (data == null || length == 0 || end > data.length ||  data[offset] != '(') {
            return null;
        }
// Skip the opening paren
        int pos = offset + 1;
        SVNSkel children = null;
        while (true) {
            SVNSkel element;
            while (pos < end && getType(data[pos]) == TYPE_SPACE) {
                pos++;
            }
            if (pos >= end) {
                return null;
            }
            if (data[pos] == ')') {
                pos++;
                break;
            }
            element = parse(data, pos, end - pos);
            if (element == null) {
                return null;
            }
            if (children == null) {
                children = element;
            } else {
                children.setNext(element);
            }
            pos = pos + element.getLength();            
        }

        SVNSkel skel = new SVNSkel(false, data, offset, pos - offset);
        skel.setChild(children);
        return skel;
    }
    
    private static SVNSkel parseImplicitAtom(byte[] data, final int offset, final int length) {
        final int end = offset + length;
        if (data == null || length == 0 || end > length || getType(data[offset]) != TYPE_NAME) {
            return null;
        }
        int pos = offset;
        while (pos < end && getType(data[pos]) != TYPE_SPACE && getType(data[pos]) != TYPE_PAREN) {
            pos++;
        }
        return new SVNSkel(true, data, offset, pos - offset);
    }

    private static SVNSkel parseExplicitAtom(byte[] data, final int offset, final int length) {
        final int end = offset + length;
        if (data == null || end > data.length) {
            return null;
        }
        int[] readed = new int[1];
        int size = parseLength(data, offset, length, length, readed);
        if (readed[0] < 0) {
            return null;
        }
        int pos = offset + readed[0];
        if (pos >= end || getType(data[pos]) != TYPE_SPACE) {
            return null;
        }
        pos++;
        if (pos + size > end) {
            return null;
        }
        return new SVNSkel(true, data, pos, size);
    }

    final private boolean myIsAtom;
    private SVNSkel myNext;

    final private byte[] myData;
    final private int myOffset;
    final private int myLength;
    
    private SVNSkel myChild;

    protected SVNSkel(boolean isAtom, byte[] data, int offset, int length) {
        myIsAtom = isAtom;
        myData = data;
        myOffset = offset;
        myLength = length;
    }

    public boolean isAtom() {
        return myIsAtom;
    }

    public byte[] getData() {
        return myData;
    }

    public int getOffset() {
        return myOffset;
    }

    public int getLength() {
        return myLength;
    }

    public SVNSkel getChild() {
        return myChild;
    }

    private void setChild(SVNSkel child) {
        myChild = child;
    }

    public SVNSkel getNext() {
        return myNext;
    }

    private void setNext(SVNSkel next) {
        myNext = next;
    }

    public int getListLength() {
        if (isAtom()) {
            return -1;
        }
        int length = 0;
        for (SVNSkel child = getChild(); child != null; child = child.getNext()) {
            length++;
        }
        return length;
    }

    public boolean isValidPropListSkel() {
        int length = getListLength();
        if ((length >= 0) && (length & 1) == 0) {
            for (SVNSkel element = getChild(); element != null; element = element.getChild()) {
                if (!element.isAtom()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
