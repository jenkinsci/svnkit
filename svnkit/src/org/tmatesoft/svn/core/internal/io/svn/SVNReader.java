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

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
class SVNReader {

    public static Date getDate(Object[] items, int index) {
        String str = getString(items, index);
        return SVNTimeUtil.parseDate(str);
    }

    public static long getLong(Object[] items, int index) {
        if (items == null || index >= items.length) {
            return -1;
        }
        if (items[index] instanceof Long) {
            return ((Long) items[index]).longValue();
        } else if (items[index] instanceof Integer) {
            return ((Integer) items[index]).intValue();
        }
        return -1;
    }

    public static boolean getBoolean(Object[] items, int index) {
        if (items == null || index >= items.length) {
            return false;
        }
        if (items[index] instanceof Boolean) {
            return ((Boolean) items[index]).booleanValue();
        } else if (items[index] instanceof String) {
            return Boolean.valueOf((String) items[index]).booleanValue();
        }
        return false;

    }

    public static Map getMap(Object[] items, int index) {
        if (items == null || index >= items.length) {
            return Collections.EMPTY_MAP;
        }
        if (items[index] instanceof Map) {
            return (Map) items[index];
        }
        return Collections.EMPTY_MAP;
    }

    public static List getList(Object[] items, int index) {
        if (items == null || index >= items.length) {
            return Collections.EMPTY_LIST;
        }
        if (items[index] instanceof List) {
            return (List) items[index];
        }
        return Collections.EMPTY_LIST;
    }

    public static String getString(Object[] items, int index) {
        if (items == null || index >= items.length) {
            return null;
        }
        if (items[index] instanceof byte[]) {
            try {
                return new String((byte[]) items[index], "UTF-8");
            } catch (IOException e) {
                return null;
            }
        } else if (items[index] instanceof String) {
            return (String) items[index];
        }
        return null;
    }

    public static boolean hasValue(Object[] items, int index, boolean value) {
        return hasValue(items, index, Boolean.valueOf(value));
    }

    public static boolean hasValue(Object[] items, int index, int value) {
        return hasValue(items, index, new Long(value));
    }

    public static boolean hasValue(Object[] items, int index, Object value) {
        if (items == null || index >= items.length) {
            return false;
        }
        if (items[index] instanceof List) {
            // look in list.
            for (Iterator iter = ((List) items[index]).iterator(); iter
                    .hasNext();) {
                Object element = iter.next();
                if (element.equals(value)) {
                    return true;
                }
            }
        } else {
            if (items[index] == null) {
                return value == null;
            }
            if (items[index] instanceof byte[] && value instanceof String) {
                try {
                    items[index] = new String((byte[]) items[index], "UTF-8");
                } catch (IOException e) {
                    return false;
                }
            }
            return items[index].equals(value);
        }
        return false;
    }

    /**
     * upper case - read or delegate lower case - skip
     * 
     * 's' - read string as string or delegate 'w' - read word as string or
     * delegate 't' - read word as Boolean 'b' - read string as byte[] or
     * delegate 'i' - push input to passed output stream 'n' - read number as
     * Integer 'p' - properties map entry (name => value) 'l' - lock description
     * 
     * 'd' - dir entry (get-dir svn command response) 'f' - stat command
     * responce 'e' - edit command
     * 
     * '(' and ')' - list brackets '[' and ']' - command response, check for
     * 'failure', equals to '(w?(*e))' where w = success | failure
     * 
     * '?' - 0..1 tokens '*' - 0..* tokens cardinality only applicable for
     * tokens, not for groups.
     * 
     */

    public static Object[] parse(InputStream is, String templateStr, Object[] target) throws SVNException {
        if (target != null) {
            for (int i = 0; i < target.length; i++) {
                if (target[i] instanceof Collection || target[i] instanceof Map
                        || target[i] instanceof ISVNDirEntryHandler
                        || target[i] instanceof ISVNEditor
                        || target[i] instanceof OutputStream) {
                    continue;
                }
                target[i] = null;
            }
        }
        StringBuffer template = normalizeTemplate(templateStr);
        SVNEditModeReader editorBaton = null;
        int targetIndex = 0;
        boolean unconditionalThrow = false;
        for (int i = 0; i < template.length(); i++) {
            char ch = template.charAt(i);
            boolean optional = ch == '?' || ch == '*';
            boolean multiple = ch == '*';
            boolean doRead = Character.isUpperCase(ch)
                    && !isListed(INVALID_CARDINALITY_SUBJECTS, ch);
            ch = Character.toLowerCase(ch);
            if (optional) {
                // cardinality
                char next = i + 1 < template.length() ? template.charAt(i + 1) : '<';
                doRead = Character.isUpperCase(next)
                        && !isListed(INVALID_CARDINALITY_SUBJECTS, next);
                next = Character.toLowerCase(next);
                if (isListed(INVALID_CARDINALITY_SUBJECTS, next)) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed template data, ''{0}''", templateStr));
                }
                i++;
                ch = next;
            }
            is.mark(0x200);
            Object result = null;
            try {
                if (ch == 'b' || ch == 'i' || ch == 's') {
                    if (ch == 'b') {
                        result = readStringAsBytes(is);
                    } else if (ch == 's') {
                        result = readString(is);
                    } else {
                        result = createDelegatingStream(is);
                    }
                } else if (ch == 'p') {
                    readChar(is, '(');
                    String name = readString(is);
                    String value = null;
                    // may not be there
                    is.mark(0x100);
                    try {
                        value = readString(is);
                    } catch (SVNException exception) {
                        try {
                            value = null;
                            is.reset();
                        } catch (IOException e1) {
                        }
                    }
                    readChar(is, ')');
                    result = new String[] { name, value };
                } else if (ch == 'z') {
                    readChar(is, '(');
                    String name = readString(is);
                    String value = null;
                    // may not be there
                    readChar(is, '(');
                    is.mark(0x100);
                    try {
                        value = readString(is);
                    } catch (SVNException exception) {
                        try {
                            value = null;
                            is.reset();
                        } catch (IOException e1) {
                        }
                    }
                    readChar(is, ')');
                    readChar(is, ')');
                    result = new String[] { name, value };
                } else if (ch == 'w') {
                    String word = readWord(is);
                    result = word;
                } else if (ch == 't') {
                    result = Boolean.valueOf(readBoolean(is));
                } else if (ch == 'n') {
                    result = new Long(readNumber(is));
                } else if (ch == '[') {
                    readChar(is, '(');
                    String word = readWord(is);
                    if ("failure".equals(word)) {
                        // read errors and throw
                        readChar(is, '(');
                        List errorMessages = new ArrayList();
                        try {
                            while (true) {
                                is.mark(0x100);
                                SVNErrorMessage err = readError(is);
                                errorMessages.add(err);
                            }
                        } catch (SVNException e) {
                            is.reset();
                            readChar(is, ')');
                            readChar(is, ')');
                        } 
                        unconditionalThrow = true;
                        SVNErrorMessage topError = (SVNErrorMessage) errorMessages.get(0);
                        for(int k = 1; k < errorMessages.size(); k++) {
                            SVNErrorMessage child = (SVNErrorMessage) errorMessages.get(k);
                            topError.setChildErrorMessage(child);
                            topError = child;
                        }   
                        SVNErrorManager.error((SVNErrorMessage) errorMessages.get(0));
                    } else if (!"success".equals(word)) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Unknown status ''{0}'' in command response", word));
                    }
                } else if (ch == ')' || ch == ']') {
                    readChar(is, ')');
                } else if (ch == '(') {
                    readChar(is, '(');
                } else if (ch == 'd') {
                    result = readDirEntry(is);
                } else if (ch == 'f') {
                    result = readStatEntry(is);
                } else if (ch == 'e') {
                    if (editorBaton == null) {
                        editorBaton = new SVNEditModeReader();
                        if (target[targetIndex] instanceof ISVNEditor) {
                            editorBaton
                                    .setEditor((ISVNEditor) target[targetIndex]);
                        }
                    }
                    readChar(is, '(');
                    String commandName = readWord(is);
                    boolean hasMore = false;
                    try {
                        hasMore = editorBaton.processCommand(commandName, is);
                    } catch (Throwable th) {
                        unconditionalThrow = true;
                        SVNDebugLog.getDefaultLog().info(th);
                        if (th instanceof SVNException) {
                            throw ((SVNException) th);
                        }
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, th.getMessage()), th);
                    }
                    if (!"textdelta-chunk".equals(commandName)) {
                        readChar(is, ')');
                    }
                    if (!hasMore) {
                        return target;
                    }
                } else if (ch == 'x') {
                    try {
                        String word = readWord(is);
                        if (!"done".equals(word)) {
                            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed network data, 'done' expected"));
                        }
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_SVN_MALFORMED_DATA) {
                            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed network data, 'done' expected"));
                        }
                    }
                } else if (ch == 'l') {
                    result = readLock(is);//new RollbackInputStream(is));
                }
                if (doRead) {
                    target = reportResult(target, targetIndex, result, multiple);
                }
                if (multiple) {
                    i -= 2;
                } else if (doRead) {
                    targetIndex++;
                }
            } catch (SVNException e) {
                if (unconditionalThrow || e instanceof SVNCancelException || e instanceof SVNAuthenticationException) {
                    throw e;
                }
                try {
                    is.reset();
                } catch (IOException e1) {
                    //
                }
                if (optional) {
                    if (doRead) {
                        targetIndex++;
                    }
                } else {
                    throw e;
                }
            } catch (IOException ioException) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, ioException.getMessage()));
            }
        }
        if (target == null) {
            target = new Object[0];
        }
        return target;
    }

    private static final char[] VALID_TEMPLATE_CHARS = { '(', ')', '[', ']', // groups
            's', 'w', 'b', 'i', 'n', 't', 'p', // items
            'd', 'f', 'l', 'a', 'r', 'e', 'x', 'l', // command-specific
            '?', '*', 'z' };

    private static final char[] INVALID_CARDINALITY_SUBJECTS = { '(', ')', '[',
            ']', '?', '*', '<' };

    private static Object[] reportResult(Object[] target, int index, Object result, boolean multiple) throws SVNException {
        // capacity
        if (target == null) {
            target = new Object[index + 1];
        } else if (index >= target.length) {
            Object[] array = new Object[index + 1];
            System.arraycopy(target, 0, array, 0, target.length);
            target = array;
        }
        // delegating
        if (target[index] instanceof ISVNDirEntryHandler) {
            ISVNDirEntryHandler handler = ((ISVNDirEntryHandler) target[index]);
            if (result instanceof SVNDirEntry) {
                handler.handleDirEntry((SVNDirEntry) result);
            }
        } else if (target[index] == null) {
            if (result instanceof String[]) {
                target[index] = new HashMap();
            } else if (multiple) {
                target[index] = new LinkedList();
            } else {
                target[index] = result;
            }
        } else if (target[index] instanceof OutputStream && result instanceof InputStream) {
            InputStream in = (InputStream) result;
            OutputStream out = (OutputStream) target[index];
            byte[] buffer = new byte[2048];
            boolean cancelled = false;
            try {
                while (true) {
                    int read = in.read(buffer);
                    if (read > 0) {
                        out.write(buffer, 0, read);
                    } else {
                        break;
                    }
                }
                out.flush();
            } catch (IOException e) {
                cancelled = true;
                if (e instanceof SVNCancellableOutputStream.IOCancelException) {
                    SVNErrorManager.cancel(e.getMessage());
                }
                //
            } finally {
                // no need to do that if operation was cancelled!
                try {
                    while (!cancelled && in.read(buffer) > 0) {
                    }
                } catch (IOException e1) {
                    //
                }
            }
        }
        if (target[index] instanceof List) {
            ((List) target[index]).add(result);
        } else if (target[index] instanceof Map && result instanceof String[]) {
            String[] property = (String[]) result;
            ((Map) target[index]).put(property[0], property[1]);
        }
        return target;
    }

    private static StringBuffer normalizeTemplate(String template) throws SVNException {
        StringBuffer sb = new StringBuffer(template.length());
        for (int i = 0; i < template.length(); i++) {
            char ch = template.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            char tch = Character.toLowerCase(ch);
            if (isListed(VALID_TEMPLATE_CHARS, tch)) {
                sb.append(ch);
                continue;
            }
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Template ''{0}'' is not valid", template));
        }
        return sb;
    }

    private static boolean isListed(char[] chars, char test) {
        for (int i = 0; i < chars.length; i++) {
            if (test == chars[i]) {
                return true;
            }
        }
        return false;
    }

    private static char skipWhitespace(InputStream is) throws IOException {
        while (true) {
            char read = (char) is.read();
            if (Character.isWhitespace(read)) {
                continue;
            }
            return read;
        }
    }

    private static byte[] readStringAsBytes(InputStream is) throws IOException, SVNException {
        int length = readStringLength(is);
        return readBytes(is, length, null);
    }

    private static String readString(InputStream is) throws IOException, SVNException {
        int length = readStringLength(is);
        return new String(readBytes(is, length, null), 0, length, "UTF-8");
    }

    private static int readStringLength(InputStream is) throws IOException, SVNException {
        char ch = skipWhitespace(is);
        int length = 0;
        while (Character.isDigit(ch)) {
            length *= 10;
            length += (ch - '0');
            ch = (char) is.read();
        }
        if (ch == ':') {
            return length;
        }
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed network data, string length expected"));
        return -1;
    }

    private static int readNumber(InputStream is) throws IOException, SVNException {
        char ch = skipWhitespace(is);
        int length = 0;
        while (Character.isDigit(ch)) {
            length *= 10;
            length += (ch - '0');
            ch = (char) is.read();
        }
        if (Character.isWhitespace(ch)) {
            return length;
        }
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed network data, number expected"));
        return -1;
    }

    private static String readWord(InputStream is) throws IOException, SVNException {
        char ch = skipWhitespace(is);
        StringBuffer buffer = new StringBuffer();
        int count = 0;
        while (true) {
            if (Character.isWhitespace(ch)) {
                break;
            }
            if (count == 0 && !Character.isLetter(ch)) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed network data"));
            } else if (count > 0
                    && !(Character.isLetterOrDigit(ch) || ch == '-')) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed network data"));
            }
            buffer.append(ch);
            count++;
            ch = (char) is.read();
        }
        return buffer.toString();
    }

    private static boolean readBoolean(InputStream is) throws IOException, SVNException {
        String word = readWord(is);
        if ("true".equalsIgnoreCase(word)) {
            return true;
        } else if ("false".equalsIgnoreCase(word)) {
            return false;
        }
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed network data, 'true' or 'false' expected"));
        return false;
    }

    private static void readChar(InputStream is, char test) throws IOException, SVNException {
        char ch = skipWhitespace(is);
        if (ch != test) {
            if (ch < 0 || ch == Character.MAX_VALUE) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED));
            }
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed network data"));
        }
    }

    private static byte[] readBytes(InputStream is, int length, byte[] buffer) throws IOException {
        if (buffer == null || buffer.length < length) {
            buffer = new byte[length];
        }
        int offset = 0;
        while (offset < length) {
            int r = is.read(buffer, offset, length - offset);
            if (r <= 0) {
                throw new IOException("Input/Output error while receiving svn data");
            }
            offset += r;
        }
        return buffer;
    }

    private static InputStream createDelegatingStream(final InputStream source) throws IOException, SVNException {
        int length = readStringLength(source);
        final int[] counter = new int[] { length };
        return new InputStream() {
            public int read() throws IOException {
                while (counter[0] > 0) {
                    counter[0]--;
                    return source.read();
                }
                return -1;
            }
        };
    }

    private static SVNErrorMessage readError(InputStream is) throws IOException, SVNException {
        InputStream pis = is;
        readChar(pis, '(');
        int code = readNumber(pis);
        String errorMessage = readString(pis);
        readString(pis);
        readNumber(pis);
        readChar(pis, ')');
        SVNErrorCode errorCode = SVNErrorCode.getErrorCode(code);        
        return SVNErrorMessage.create(errorCode, errorMessage);
    }

    private static SVNDirEntry readDirEntry(InputStream is) throws SVNException {
        Object[] items = SVNReader.parse(is, "(SWNTN(?S)(?S))", null);

        String name = SVNReader.getString(items, 0);
        SVNNodeKind kind = SVNNodeKind.parseKind(SVNReader.getString(items, 1));
        long size = SVNReader.getLong(items, 2);
        boolean hasProps = SVNReader.getBoolean(items, 3);
        long revision = SVNReader.getLong(items, 4);
        Date date = items[5] != null ? SVNTimeUtil.parseDate(SVNReader.getString(items, 5)) : null;
        String author = SVNReader.getString(items, 6);
        return new SVNDirEntry(null, name, kind, size, hasProps, revision, date, author);
    }

    private static SVNDirEntry readStatEntry(InputStream is) throws SVNException {
        Object[] items = SVNReader.parse(is, "(WNTN(?S)(?S))", null);

        SVNNodeKind kind = SVNNodeKind.parseKind(SVNReader.getString(items, 0));
        long size = SVNReader.getLong(items, 1);
        boolean hasProps = SVNReader.getBoolean(items, 2);
        long revision = SVNReader.getLong(items, 3);
        Date date = items[4] != null ? SVNTimeUtil.parseDate(SVNReader.getString(items, 4)) : null;
        String author = SVNReader.getString(items, 5);
        return new SVNDirEntry(null, null, kind, size, hasProps, revision, date, author);
    }

    private static SVNLock readLock(InputStream is) throws SVNException {
        Object[] items = SVNReader.parse(is, "(SSS(?S)S(?S))", new Object[6]);

        String path = (String) items[0];
        String id = (String) items[1];
        String owner = (String) items[2];
        String comment = (String) items[3];
        String creationDate = (String) items[4];
        String expirationDate = (String) items[5];
        Date created = creationDate != null ? SVNTimeUtil.parseDate(creationDate)
                : null;
        Date expires = expirationDate != null ? SVNTimeUtil
                .parseDate(expirationDate) : null;

        return new SVNLock(path, id, owner, comment, created, expires);
    }

}
