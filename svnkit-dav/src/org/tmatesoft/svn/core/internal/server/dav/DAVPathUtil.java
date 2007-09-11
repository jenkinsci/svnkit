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

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVPathUtil {

    private static final String SLASH = "/";

    public static String dropLeadingSlash(String uri) {
        if (uri == null) {
            return "";
        }
        return uri.startsWith(SLASH) ? uri.substring(SLASH.length()) : uri;
    }

    public static String addLeadingSlash(String uri) {
        if (uri == null) {
            return SLASH;
        }
        return uri.startsWith(SLASH) ? uri : SLASH + uri;
    }

    public static String dropTraillingSlash(String uri) {
        if (uri == null) {
            return "";
        }
        return uri.endsWith(SLASH) ? uri.substring(0, uri.length() - SLASH.length()) : uri;
    }

    public static String addTrailingSlash(String uri) {
        if (uri == null) {
            return SLASH;
        }
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

    public static String tail(String uri) {
        uri = dropTraillingSlash(uri);
        int lastSlashIndex = uri.lastIndexOf(SLASH);
        if (lastSlashIndex == -1) {
            return uri;
        }
        return uri.substring(lastSlashIndex);
    }

    public static String removeTail(String uri, boolean doStandardize) {
        uri = dropTraillingSlash(uri);
        int tailLength = tail(uri).length();
        return doStandardize ? standardize(uri.substring(0, uri.length() - tailLength)) : uri.substring(0, uri.length() - tailLength);
    }

    public static String append(String parent, String child) {
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

    public static String normalize(String uri) {
        return "".equals(uri) ? SLASH : uri;
    }

    public static void testCanonical(String path) throws SVNException {
        if (path != null && !path.equals(SVNPathUtil.canonicalizePath(path))) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Path ''{0}'' is not canonicalized;\nthere is a problem with the client.", path));
        }
    }

    public static String buildURI(String context, DAVResourceKind davResourceKind, long revision, String path) {
        StringBuffer resultURI = new StringBuffer();
        path = path == null ? "" : path;
        context = context == null ? "" : context;
        resultURI.append(context);
        resultURI.append(SLASH);
        if (davResourceKind == DAVResourceKind.PUBLIC) {
            resultURI.append(path);
        } else {
            resultURI.append(DAVResourceURI.SPECIAL_URI).append(SLASH);
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
                resultURI.append(addLeadingSlash(path));
            } else if (davResourceKind == DAVResourceKind.VERSION) {
                resultURI.append(davResourceKind.toString());
                resultURI.append(SLASH);
                resultURI.append(String.valueOf(revision));
                resultURI.append(addLeadingSlash(path));
            } else if (davResourceKind == DAVResourceKind.VCC) {
                resultURI.append(davResourceKind.toString());
                resultURI.append(SLASH);
                resultURI.append(DAVResourceURI.DEDAULT_VCC_NAME);
            }
        }
        return resultURI.toString();
    }
}
