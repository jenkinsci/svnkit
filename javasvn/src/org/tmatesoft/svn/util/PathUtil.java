/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.util;


/**
 * This class is a utility which aim is to help in work with path strings. The
 * <code>PathUtility</code> is used along with strings denoting relative paths
 * composed of entry names separated by <i>'/'</i> character (for example,
 * <i>"/some/directory/some/file"</i>).
 * 
 * @version 1.0
 * @author TMate Software Ltd.
 * 
 */
public class PathUtil {
    /**
     * Determines if a path string contains no entry name (empty in other
     * words).
     * 
     * <p>
     * That is if the path is one of the following: <code>null</code>, <i>""</i>
     * or <i>"/"</i> (no matter if the path contains a number of whitespaces -
     * for example, <i>" "</i> will be interpreted as <i>""</i>).
     * 
     * @param path
     *            a path string that is to be examined
     * @return <code>true</code> - if the path string contains no entries,
     *         <code>false</code> otherwise
     */
    public static final boolean isEmpty(String path) {
        return path == null || "".equals(path.trim())
                || "/".equals(path.trim());
    }

    /**
     * Returns a substring of the original <code>path</code> string that
     * doesn't contain the last entry name. For example:
     * <code>removeTail(<i>"some/path/to/entry/"</i>)</code> returns
     * <i>"some/path/to"</i>.
     * 
     * <p>
     * If <code>path</code> is such that
     * {@link #isEmpty(String) isEmpty(path)} is <code>true</code> then
     * <code>null</code> is returned. For single entry name paths (like
     * <i>"/path"</i> or <i>"path"</i>) this method returns <i>"/"</i>
     * 
     * 
     * @param path
     *            a path string which last entry name is to be cut out
     * @return a substring of the source string not containing the last entry
     *         name.
     */
    public static final String removeTail(String path) {
        if (isEmpty(path)) {
            return null;
        }
        path = removeTrailingSlash(path);
        int index = path.lastIndexOf('/');
        if (index <= 0) {
            return "/";
        }
        return path.substring(0, index);
    }

    /**
     * Concatenates two path strings. The resultant string is the
     * <code>path</code> string that was appended the <code>segment</code>
     * string. For example,
     * <code>append(<i>"/some/root/path/"</i>, <i>"/some/file"</i>)</code>
     * returns <i>"/some/root/path/some/file"</i>.
     * 
     * @param path
     *            a parent path string
     * @param segment
     *            a child path string
     * @return a new full path string that is a concatenation of
     *         <code>path</code> and <code>segment</code>
     */
    public static final String append(String path, String segment) {
        path = isEmpty(path) ? "" : removeTrailingSlash(path);
        segment = isEmpty(segment) ? "" : removeLeadingSlash(segment);
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - "/".length());
        }
        if (segment.startsWith("/")) {
            segment = segment.substring("/".length());
        }
        return path + '/' + segment;
    }

    /**
     * Returns a substring that is the last entry name in a path string without
     * leading or trailing slashes. For example,
     * <code>tail(<i>"/some/path/"</i>)</code> will return <i>"path"</i>.
     * 
     * @param path
     *            a path string which last entry name is to be cut out
     * @return the last entry name or this string itself (if it doesn't include
     *         any nested entry paths - like <code>"path"</code>)
     */
    public static String tail(String path) {
        path = removeTrailingSlash(path);
        int index = path.lastIndexOf('/');
        if (index >= 0) {
            return path.substring(index + 1);
        }
        return path;
    }

    /**
     * Returns a substring that is the first entry name in a path string without
     * leading or trailing slashes. For example,
     * <code>head(<i>"/some/path/"</i>)</code> will return <i>"some"</i>.
     * 
     * @param path
     *            a path string which first entry name is to be cut out
     * @return the first entry name or this string itself (if it doesn't include
     *         any nested entry paths - like <code>"path"</code>)
     */
    public static String head(String path) {
        path = removeLeadingSlash(path);
        int index = path.indexOf('/');
        if (index >= 0) {
            return path.substring(0, index);
        }
        return path;
    }

    /**
     * Removes the first '/' (if any) from a path string. For example,
     * <code>removeLeadingSlash(<i>"/some/path/"</i>)</code> will return
     * <i>"some/path/"</i>.
     * 
     * @param path
     *            a path string which leading slash is to be cut out
     * @return a substring of the path string that doesn't contain the leading
     *         slash or just the string itself
     */
    public static String removeLeadingSlash(String path) {
        if (path == null || path.length() == 0) {
            return "";
        }
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        return path;
    }

    /**
     * Removes the last '/' (if any) from a path string. For example,
     * <code>removeTralingSlash(<i>"some/path/"</i>)</code> will return
     * <i>"some/path"</i>.
     * 
     * @param path
     *            a path string which trailing slash is to be cut out
     * @return a substring of the path string that doesn't contain the trailing
     *         slash or just the string itself
     */
    public static final String removeTrailingSlash(String path) {
        if (isEmpty(path)) {
            return path;
        }
        if (path.charAt(path.length() - 1) == '/') {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
