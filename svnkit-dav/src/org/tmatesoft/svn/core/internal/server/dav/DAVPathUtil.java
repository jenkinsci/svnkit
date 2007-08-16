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

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVPathUtil {

    private static final String SLASH = "/";

    public static String dropLeadingSlash(String uri) {
        return uri.startsWith(SLASH) ? uri.substring(SLASH.length()) : uri;
    }

    public static String addLeadingSlash(String uri) {
        return uri.startsWith(SLASH) ? uri : SLASH + uri;
    }

    public static String dropTraillingSlash(String uri) {
        return uri.endsWith(SLASH) ? uri.substring(0, uri.length() - SLASH.length()) : uri;
    }

    public static String addTrailingSlash(String uri) {
        return uri.endsWith(SLASH) ? uri : uri + SLASH;
    }

    public static String head(String uri) {
        uri = dropLeadingSlash(uri);
        int slashIndex = uri.indexOf(SLASH);
        if (slashIndex == -1) {
            return uri;
        }
        return uri.substring(0, slashIndex);
    }

    public static String removeHead(String uri, boolean doStandardize) {
        uri = dropLeadingSlash(uri);
        int headLength = head(uri).length();
        return doStandardize ? standardize(uri.substring(headLength)) : uri.substring(headLength);
    }

    public static String concat(String parent, String child) {
        StringBuffer uriBuffer = new StringBuffer();
        uriBuffer.append(standardize(parent));
        uriBuffer.append(standardize(child));
        return uriBuffer.toString();
    }

    public static String standardize(String uri) {
        if (uri == null) {
            return SLASH;
        }
        return addLeadingSlash(dropTraillingSlash(uri));
    }

    public static String buildURI(String context, DAVResourceKind davResourceKind, long revision, String path) {
        StringBuffer resultURI = new StringBuffer();
        path = path == null ? "" : path;
        resultURI.append(context);
        resultURI.append(SLASH);
        if (davResourceKind == DAVResourceKind.PUBLIC) {
            resultURI.append(path);
        } else {
            resultURI.append(DAVResource.SPECIAL_URI).append(SLASH);
            if (davResourceKind == DAVResourceKind.ACT_COLLECTION) {
                resultURI.append(davResourceKind.toString());
                resultURI.append(SLASH);
            } else if (davResourceKind == DAVResourceKind.BASELINE) {
                resultURI.append(davResourceKind.toString());
                resultURI.append(SLASH);
                resultURI.append(String.valueOf(revision));
            } else if (davResourceKind == DAVResourceKind.BASELINE_COLL) {
                resultURI.append(davResourceKind.toString());
                resultURI.append(SLASH);
                resultURI.append(String.valueOf(revision));
                resultURI.append(SLASH);
            } else if (davResourceKind == DAVResourceKind.VERSION) {
                resultURI.append(davResourceKind.toString());
                resultURI.append(SLASH);
                resultURI.append(String.valueOf(revision));
                resultURI.append(path);
            } else if (davResourceKind == DAVResourceKind.VCC) {
                resultURI.append(davResourceKind.toString());
                resultURI.append(SLASH);
                resultURI.append(DAVResource.DEDAULT_VCC_NAME);
            }
        }
        return resultURI.toString();
    }
}
