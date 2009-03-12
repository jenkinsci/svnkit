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

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

    private static final int DEFAULT_BUFFER_SIZE = 1024;

    public static final char TYPE_NOTHING = 0;
    public static final char TYPE_SPACE = 1;
    public static final char TYPE_DIGIT = 2;
    public static final char TYPE_PAREN = 3;
    public static final char TYPE_NAME = 4;

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

    public static char getType(byte b) {
        return CHAR_TYPES_TABLE[b & 0xFF];
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

    public static SVNSkel parseList(byte[] data, final int offset, final int length) {
        final int end = offset + length;
        if (data == null || length == 0 || end > data.length || data[offset] != '(') {
            return null;
        }
// Skip the opening paren
        int pos = offset + 1;
        LinkedList children = new LinkedList();
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
            children.add(element);
            pos = pos + element.getLength();
        }

        return createList(data, offset, pos - offset, children);
    }

    public static SVNSkel parseImplicitAtom(byte[] data, final int offset, final int length) {
        final int end = offset + length;
        if (data == null || length == 0 || end > length || getType(data[offset]) != TYPE_NAME) {
            return null;
        }
        int pos = offset;
        while (pos < end && getType(data[pos]) != TYPE_SPACE && getType(data[pos]) != TYPE_PAREN) {
            pos++;
        }
        return createAtom(data, offset, pos - offset);
    }

    public static SVNSkel parseExplicitAtom(byte[] data, final int offset, final int length) {
        final int end = offset + length;
        if (data == null || end > data.length) {
            return null;
        }
        int[] readed = new int[1];
        int size = parseSize(data, offset, length, length, readed);
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
        return createAtom(data, pos, size);
    }

    public static SVNSkel createAtom(String str) {
        if (str == null) {
            return null;
        }
        byte[] data;
        try {
            data = str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            data = str.getBytes();
        }
        return createAtom(data, false);
    }

    public static SVNSkel createAtom(byte[] data) {
        if (data == null) {
            return null;
        }
        return createAtom(data, true);
    }

    private static SVNSkel createAtom(byte[] data, boolean shared) {
        if (shared) {
            return createAtom(data, 0, data.length);
        }
        return new SVNSkel(true, data);
    }

    public static SVNSkel createAtom(byte[] data, int offset, int length) {
        if (data == null) {
            return null;
        }
        byte[] raw = new byte[length];
        System.arraycopy(data, offset, raw, 0, length);
        return new SVNSkel(true, raw);
    }

    public static SVNSkel createEmptyList() {
        return new SVNSkel(false, null);
    }

    public static SVNSkel createList(byte[] data, int offset, int length, LinkedList children) {
        if (data == null) {
            return null;
        }
        byte[] raw = new byte[length];
        System.arraycopy(data, offset, raw, 0, length);
        return new SVNSkel(raw, children);
    }

    final private byte[] myRawData;
    final private LinkedList myList;

    protected SVNSkel(boolean isAtom, byte[] data) {
        myRawData = data;
        myList = isAtom ? null : new LinkedList();
    }

    private SVNSkel(byte[] data, LinkedList children) {
        myRawData = data;
        myList = children == null ? new LinkedList() : children;
    }

    public boolean isAtom() {
        return myList == null;
    }

    public byte[] getData() {
        return myRawData;
    }

    public List getChildren() {
        return myList;
    }

    public void addChild(SVNSkel child) {
        myList.add(child);
    }

    public int getLength() {
        if (isAtom()) {
            return -1;
        }
        return getChildren().size();
    }

    public String getValue() {
        if (isAtom()) {
            String str;
            try {
                str = new String(getData(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                str = new String(getData());
            }
            return str;
        }
        return null;
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[SVNSkel atom = ");
        buffer.append(isAtom());
        buffer.append("; raw data = ");
        if (isAtom()) {
            buffer.append(getValue());
        } else {
            buffer.append(" ( ");
            for (Iterator iterator = getChildren().iterator(); iterator.hasNext();) {
                SVNSkel element = (SVNSkel) iterator.next();
                buffer.append(element.toString());
            }
            buffer.append(" ) ");
        }
        buffer.append("]");
        return buffer.toString();
    }

    public boolean contentEquals(String str) {
        if (!isAtom()) {
            return false;
        }
        String value = getValue();
        return value.equals(str);
    }

    public boolean isValidPropList() {
        int length = getLength();
        if (length >= 0 && (length & 1) == 0) {
            for (Iterator iterator = getChildren().iterator(); iterator.hasNext();) {
                SVNSkel element = (SVNSkel) iterator.next();
                if (!element.isAtom()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public Map parsePropList(Map props) throws SVNException {
// TODO: check if iterator returns correct result for proplist       
        props = props == null ? new SVNHashMap() : props;
        if (!isValidPropList()) {
            error("proplist");
        }
        for (Iterator iterator = getChildren().iterator(); iterator.hasNext();) {
// We always have name - value pair since children length is even
            SVNSkel nameElement = (SVNSkel) iterator.next();
            SVNSkel valueElement = (SVNSkel) iterator.next();
            String name = nameElement.getValue();
            String value = valueElement.getValue();
            props.put(name, value);
        }
        return props;
    }

    public static SVNSkel unparseList(Map props) throws SVNException {
        SVNSkel list = createEmptyList();
        if (props == null) {
            return list;
        }
        for (Iterator iterator = props.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            SVNSkel name = createAtom((String) entry.getKey());
            SVNSkel value = createAtom((String) entry.getValue());
            list.addChild(value);
            list.addChild(name);
        }
        if (!list.isValidPropList()) {
            error("proplist");
        }
        return list;
    }

    public byte[] unparse() throws SVNException {
        int approxSize = estimateUnparsedSize();
        ByteBuffer buffer = ByteBuffer.allocate(approxSize);
        buffer = writeTo(buffer);
        buffer.flip();
        byte[] raw = new byte[buffer.limit() - buffer.arrayOffset()];
        System.arraycopy(buffer.array(), buffer.arrayOffset(), raw, 0, buffer.limit());
        return raw;
    }

    public ByteBuffer writeTo(ByteBuffer buffer) throws SVNException {
        if (isAtom()) {
            if (useImplicit()) {
                buffer = allocate(buffer, getLength());
                buffer = buffer.put(getData());
            } else {
                byte[] sizeBytes = getSizeBytes(getLength(), Integer.MAX_VALUE);
                if (sizeBytes == null) {
                    SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.FS_MALFORMED_SKEL, "Unable to write size bytes to buffer");
                    SVNErrorManager.error(error, SVNLogType.DEFAULT);
                }
                buffer = allocate(buffer, sizeBytes.length + 1 + getLength());
                buffer.put(sizeBytes);
                buffer.putChar(' ');
                buffer.put(getData());
            }
        } else {
            buffer = allocate(buffer, 1);
            buffer.putChar('(');
            for (Iterator iterator = getChildren().iterator(); iterator.hasNext();) {
                SVNSkel element = (SVNSkel) iterator.next();
                buffer = element.writeTo(buffer);
                if (iterator.hasNext()) {
                    buffer = allocate(buffer, 1);
                    buffer.putChar(' ');
                }
            }
            buffer = allocate(buffer, 1);
            buffer.putChar(')');
        }
        return buffer;
    }

    private int estimateUnparsedSize() {
        if (isAtom()) {
            byte[] data = getData();
            if (data.length < 100) {
// length bytes + whitespace
                return data.length + 3;
            } else {
                return data.length + 30;
            }
        } else {
            int total = 2;
            for (Iterator iterator = getChildren().iterator(); iterator.hasNext();) {
                SVNSkel element = (SVNSkel) iterator.next();
                total += element.estimateUnparsedSize();
// space between a pair of elements
                total++;
            }
            return total;
        }
    }

    private boolean useImplicit() {
        byte[] data = getData();
        if (data.length == 0 || data.length >= 100) {
            return false;
        }
        if (getType(data[0]) != TYPE_NAME) {
            return false;
        }
        for (int i = 0; i < data.length; i++) {
            byte cur = data[i];
            if (getType(cur) == TYPE_SPACE || getType(cur) == TYPE_PAREN) {
                return false;
            }
        }
        return true;
    }

    private static ByteBuffer allocate(ByteBuffer buffer, int capacity) {
        if (buffer == null) {
            capacity = Math.max(capacity * 3 / 2, DEFAULT_BUFFER_SIZE);
            return ByteBuffer.allocate(capacity);
        }
        if (capacity > buffer.remaining()) {
            ByteBuffer expandedBuffer = ByteBuffer.allocate((buffer.position() + capacity) * 3 / 2);
            buffer.flip();
            expandedBuffer.put(buffer);
            return expandedBuffer;
        }
        return buffer;
    }

    private static int parseSize(byte[] data, final int offset, final int maxLength, final int sizeLimit, int[] readed) {
        final int maxPrefix = sizeLimit / 10;
        final int maxDigit = sizeLimit % 10;
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

    private static int writeSize(int value, byte[] data, int maxLength) {
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

    private static byte[] getSizeBytes(final int value, int maxLength) {
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
        int count = writeSize(value, data, length);
        if (count < 0) {
            return null;
        }
        if (count < data.length) {
            byte[] result = new byte[count];
            System.arraycopy(data, 0, result, 0, count);
            return result;
        }
        return data;
    }

    private static void error(String type) throws SVNException {
        SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.FS_MALFORMED_SKEL, "Malformed{0}{1} skeleton", new Object[]{type == null ? "" : " ",
                type == null ? "" : type});
        SVNErrorManager.error(error, SVNLogType.DEFAULT);
    }
}
