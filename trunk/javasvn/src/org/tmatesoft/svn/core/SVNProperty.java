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

package org.tmatesoft.svn.core;

/**
 * @author TMate Software Ltd.
 */
public class SVNProperty {
    
    public static final String SVN_PREFIX = "svn:";
    public static final String SVN_WC_PREFIX = "svn:wc:";
    public static final String SVN_ENTRY_PREFIX = "svn:entry:";
    
    public static final String EOL_STYLE = SVN_PREFIX + "eol-style";
    public static final String IGNORE = SVN_PREFIX + "ignore";
    public static final String MIME_TYPE = SVN_PREFIX + "mime-type";
    public static final String KEYWORDS = SVN_PREFIX + "keywords";
    public static final String EXECUTABLE = SVN_PREFIX + "executable";
    public static final String EXTERNALS = SVN_PREFIX + "externals";
    
    public static final String REVISION = SVN_ENTRY_PREFIX + "revision";
    public static final String COMMITTED_REVISION = SVN_ENTRY_PREFIX + "committed-rev";
    public static final String COMMITTED_DATE = SVN_ENTRY_PREFIX + "committed-date";
    public static final String CHECKSUM = SVN_ENTRY_PREFIX + "checksum";
    public static final String URL = SVN_ENTRY_PREFIX + "url";
    public static final String COPYFROM_URL = SVN_ENTRY_PREFIX + "copyfrom-url";
    public static final String COPYFROM_REVISION = SVN_ENTRY_PREFIX + "copyfrom-rev";
    public static final String SCHEDULE = SVN_ENTRY_PREFIX + "schedule";
    public static final String COPIED = SVN_ENTRY_PREFIX + "copied";
    public static final String LAST_AUTHOR = SVN_ENTRY_PREFIX + "last-author";
    public static final String UUID = SVN_ENTRY_PREFIX + "uuid";
    public static final String PROP_TIME = SVN_ENTRY_PREFIX + "prop-time";
    public static final String TEXT_TIME = SVN_ENTRY_PREFIX + "text-time";
    public static final String NAME = SVN_ENTRY_PREFIX + "name";
    public static final String KIND = SVN_ENTRY_PREFIX + "kind";
    public static final String CONFLICT_OLD = SVN_ENTRY_PREFIX + "conflict-old";
    public static final String CONFLICT_NEW = SVN_ENTRY_PREFIX + "conflict-new";
    public static final String CONFLICT_WRK = SVN_ENTRY_PREFIX + "conflict-wrk";
    public static final String PROP_REJECT_FILE = SVN_ENTRY_PREFIX + "prop-reject-file";
    public static final String DELETED = SVN_ENTRY_PREFIX + "deleted";
    public static final String CORRUPTED = SVN_ENTRY_PREFIX + "corrupted";

    public static final String KIND_DIR = "dir";
    public static final String KIND_FILE = "file";
    
    public static final String EOL_STYLE_LF = "LF";
    public static final String EOL_STYLE_CR = "CR";
    public static final String EOL_STYLE_CRLF = "CRLF";
    public static final String EOL_STYLE_NATIVE = "native";
    
    public static final String SCHEDULE_ADD = "add";
    public static final String SCHEDULE_DELETE = "delete";
    public static final String SCHEDULE_REPLACE = "replace";
    
    private static final byte[] EOL_LF_BYTES = {'\n'};
    private static final byte[] EOL_CRLF_BYTES = {'\r', '\n'};
    private static final byte[] EOL_CR_BYTES = {'\r'};
    private static final byte[] EOL_NATIVE_BYTES = System.getProperty("line.separator").getBytes();
    
    public static boolean isWorkingCopyProperty(String name) {
        return name != null && name.startsWith(SVN_WC_PREFIX);        
    }
    public static boolean isEntryProperty(String name) {
        return name != null && name.startsWith(SVN_ENTRY_PREFIX);        
    }
    public static boolean isSVNProperty(String name) {
        return name != null && name.startsWith(SVN_PREFIX);        
    }
    public static boolean isTextType(String mimeType) {
        return mimeType == null || mimeType.startsWith("text/");
    }
    public static byte[] getEOLBytes(String eolType) {
        if (eolType == null) {
            return null;
        } else if (SVNProperty.EOL_STYLE_NATIVE.equals(eolType)) {
            return EOL_NATIVE_BYTES; 
        } else if (SVNProperty.EOL_STYLE_CR.equals(eolType)) {
            return EOL_CR_BYTES;
        } else if (SVNProperty.EOL_STYLE_CRLF.equals(eolType)) {
            return EOL_CRLF_BYTES;
        } 
        return EOL_LF_BYTES;
    }
    public static boolean booleanValue(String text) {
        return text == null ? false : Boolean.valueOf(text.trim()).booleanValue();
    }
    public static long longValue(String text) {
        if (text != null) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {}
        }
        return -1;
    }
    public static String toString(boolean b) {
        return Boolean.toString(b); 
    }
    public static String toString(long i) {
        return Long.toString(i);
    }
    
}
