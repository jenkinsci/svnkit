/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNReader2 {

    private static final String DEAFAULT_ERROR_TEMPLATE = "nccn";

    public static List readResponse(InputStream is, String template) throws SVNException {
        // "(success (10))"
        List readItems = readTuple(is, "wl");
        String word = (String) readItems.get(0);
        List list = (List) readItems.get(1);

        if ("success".equals(word)) {
            return parseTuple(template, list);
        } else if ("failure".equals(word)) {
            handleFailureStatus(list);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
            SVNErrorManager.error(err);
        }
        return null;
    }

    private static void handleFailureStatus(List list) throws SVNException {
        if (list.size() == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Empty error list");
            SVNErrorManager.error(err);
        }
        SVNErrorMessage topError = getErrorMessage((Item) list.get(list.size() - 1));
        SVNErrorMessage parentError = topError;
        for (int i = list.size() - 2; i >= 0; i++) {
            Item item = (Item) list.get(i);
            SVNErrorMessage error = getErrorMessage(item);
            parentError.setChildErrorMessage(error);
            parentError = error;                
        }
        SVNErrorManager.error(topError);
    }

    private static SVNErrorMessage getErrorMessage(Item item) throws SVNException {
        if (item.kind != Item.LIST) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Malformed error list");
            SVNErrorManager.error(err);
        }
        List errorItems = parseTuple(DEAFAULT_ERROR_TEMPLATE, item.items);
        int code = ((Long) errorItems.get(0)).intValue();
        SVNErrorCode errorCode = SVNErrorCode.getErrorCode(code);
        String errorMessage = (String) errorItems.get(1);
        errorMessage = errorMessage == null ? "" : errorMessage;
        //errorItems contains 2 items more (file and line) but native svn uses them only for debugging purposes.
        //May be we should use another error template.
        return SVNErrorMessage.create(errorCode, errorMessage);
    }

    private static List readTuple(InputStream is, String template) throws SVNException {
        char ch = readChar(is);
        Item item = readItem(is, null, ch);
        System.out.println(item);
        if (item.kind != Item.LIST) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
            SVNErrorManager.error(err);
        }
        return parseTuple(template, item.items);
    }

    private static List parseTuple(String template, Collection items) throws SVNException {
        List values = new ArrayList();
        int index = 0;
        for (Iterator iterator = items.iterator(); iterator.hasNext() && index < template.length(); index++) {
            Item item = (Item) iterator.next();
            char ch = template.charAt(index);
            if (ch == '?') {
                index++;
                ch = template.charAt(index);
            }

            if ((ch == 'n' || ch == 'r') && item.kind == Item.NUMBER) {
                values.add(new Long(item.number));
            } else if ((ch == 's' || ch == 'c') && item.kind == Item.STRING) {
                values.add(item.line);
            } else if (ch == 'w' && item.kind == Item.WORD) {
                values.add(item.word);
            } else if ((ch == 'b' || ch == 'B') && item.kind == Item.WORD) {
                if (String.valueOf(true).equals(item.word)) {
                    values.add(Boolean.TRUE);
                } else if (String.valueOf(false).equals(item.word)) {
                    values.add(Boolean.FALSE);
                } else {
                    break;
                }
            } else if (ch == 'l' && item.kind == Item.LIST) {
                values.add(item.items);
            } else if (ch == '(' && item.kind == Item.LIST) {
                index++;
                Collection listValues = parseTuple(template.substring(index), item.items);
                values.addAll(listValues);
            } else if (ch == ')') {
                return values;
            } else {
                break;
            }
        }
        if (index < template.length() && template.charAt(index) == '?') {
            int nestingLevel = 0;
            while (index < template.length()) {
                switch (template.charAt(index)) {
                    case'?':
                        break;
                    case'r':
                        values.add(new Long(SVNRepository.INVALID_REVISION));
                        break;
                    case's':
                    case'c':
                    case'w':
                    case'l':
                        values.add(null);
                        break;
                    case'B':
                    case'n':
                    case'(':
                        nestingLevel++;
                        break;
                    case')':
                        nestingLevel--;
                        if (nestingLevel < 0) {
                            return values;
                        }
                        break;
                    default:
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
                        SVNErrorManager.error(err);
                }
                index++;
            }
        }
        if (index == (template.length() - 1) && template.charAt(index) != ')') {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
            SVNErrorManager.error(err);
        }
        return values;
    }

    private static Item readItem(InputStream is, Item item, char ch) throws SVNException {
        if (item == null) {
            item = new Item();
        }
        if (Character.isDigit(ch)) {
            long value = Character.digit(ch, 10);
            long previousValue;
            while (true) {
                previousValue = value;
                ch = readChar(is);
                if (Character.isDigit(ch)) {
                    value = value * 10 + Character.digit(ch, 10);
                    if (previousValue != value / 10) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Number is larger than maximum");
                        SVNErrorManager.error(err);
                    }
                    continue;
                }
                break;
            }
            if (ch == ':') {
                // string.
                byte[] buffer = new byte[(int) value];
                try {
                    int toRead = (int) value;
                    while (toRead > 0) {
                        int r = is.read(buffer, buffer.length - toRead, toRead);
                        if (r <= 0) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
                            SVNErrorManager.error(err);
                        }
                        toRead -= r;
                    }
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
                    SVNErrorManager.error(err);
                }
                item.kind = Item.STRING;
                try {
                    item.line = new String(buffer, 0, buffer.length, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
                    SVNErrorManager.error(err);
                }
                ch = readChar(is);
            } else {
                // number.
                item.kind = Item.NUMBER;
                item.number = value;
            }
        } else if (Character.isLetter(ch)) {
            StringBuffer buffer = new StringBuffer();
            buffer.append(ch);
            while (true) {
                ch = readChar(is);
                if (Character.isLetterOrDigit(ch) && ch != '-') {
                    buffer.append(ch);
                    continue;
                }
                break;
            }
            item.kind = Item.WORD;
            item.word = buffer.toString();
        } else if (ch == '(') {
            item.kind = Item.LIST;
            item.items = new ArrayList();
            while (true) {
                ch = skipWhiteSpace(is);
                if (ch == ')') {
                    break;
                }
                Item child = new Item();
                item.items.add(child);
                readItem(is, child, ch);
            }
            ch = readChar(is);
        }
        if (!Character.isWhitespace(ch)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
            SVNErrorManager.error(err);
        }
        return item;
    }

    private static char readChar(InputStream is) throws SVNException {
        int r = 0;
        try {
            r = is.read();
            if (r < 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
                SVNErrorManager.error(err);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA);
            SVNErrorManager.error(err);
        }
        return (char) (r & 0xFF);
    }

    private static char skipWhiteSpace(InputStream is) throws SVNException {
        while (true) {
            char ch = readChar(is);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            return ch;
        }
    }

    private static class Item {

        public static final int WORD = 0;
        public static final int STRING = 1;
        public static final int LIST = 2;
        public static final int NUMBER = 3;

        public int kind;

        public long number = -1;
        public String word;  // success
        public String line; // 3:abc
        public Collection items;

        public String toString() {
            StringBuffer result = new StringBuffer();
            if (kind == WORD) {
                result.append("W").append(word);
            } else if (kind == STRING) {
                result.append("S").append(line.length()).append(":").append(line).append(" ");
            } else if (kind == NUMBER) {
                result.append("N").append(number);
            } else if (kind == LIST) {
                result.append("L(");
                for (Iterator elemenets = items.iterator(); elemenets.hasNext();) {
                    Item item = (Item) elemenets.next();
                    result.append(item.toString());
                    result.append(" ");
                }
                result.append(") ");
            }
            return result.toString();
        }
    }
}
