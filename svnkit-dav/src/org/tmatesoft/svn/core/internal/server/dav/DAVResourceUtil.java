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
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVResourceUtil {

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

    public static String standardize(String uri) {
        if (uri == null) {
            return SLASH;
        }
        return addLeadingSlash(dropTraillingSlash(uri));
    }

    public static String buildURI(DAVResource resource) {
        return buildURI(resource.getContext(), resource.getKind(), resource.getRevision(), resource.getPath());
    }

    public static String buildURI(DAVResource resource, long revision) {
        return buildURI(resource.getContext(), resource.getKind(), revision, resource.getPath());
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
                resultURI.append(SLASH);
                resultURI.append(path);
            } else if (davResourceKind == DAVResourceKind.VCC) {
                resultURI.append(davResourceKind.toString());
                resultURI.append(SLASH);
                resultURI.append(DAVResource.DEDAULT_VCC_NAME);
            }
        }

        return resultURI.toString();
    }

    //Next three methods we can use for dead properties only
    public static DAVElement convertToDAVElement(String property) throws SVNException {
        if (!SVNProperty.isRegularProperty(property)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Unrecognized property prefix ''{0}''", property));
        }
        String namespace = DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE;
        if (SVNProperty.isSVNProperty(property)) {
            namespace = DAVElement.SVN_SVN_PROPERTY_NAMESPACE;
        }
        property = SVNProperty.shortPropertyName(property);
        return DAVElement.getElement(namespace, property);
    }

    public static String convertToSVNProperty(DAVElement element) throws SVNException {
        return convertToSVNProperty(element.getNamespace(), element.getName());
    }

    public static String convertToSVNProperty(String namespace, String name) throws SVNException {
        if (DAVElement.SVN_SVN_PROPERTY_NAMESPACE.equals(namespace)) {
            return SVNProperty.SVN_PREFIX + name;
        } else if (DAVElement.SVN_CUSTOM_PROPERTY_NAMESPACE.equals(namespace)) {
            return name;
        }
        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Unrecognized namespace ''{0}''", namespace));
        return null;
    }
}
