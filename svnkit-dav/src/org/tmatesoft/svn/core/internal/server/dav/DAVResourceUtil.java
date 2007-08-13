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

    public static String buildURI(String context, String repositoryPath, DAVResourceKind davResourceKind, long revision, String parameterPath) {
        StringBuffer resultURI = new StringBuffer();
        resultURI.append(context);
        resultURI.append(repositoryPath.startsWith("/") ? "" : "/");
        resultURI.append(repositoryPath);
        resultURI.append(("".equals(repositoryPath) || repositoryPath.endsWith("/")) ? "" : "/");
        if (davResourceKind == DAVResourceKind.ACT_COLLECTION) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString());
            resultURI.append("/");
        } else if (davResourceKind == DAVResourceKind.BASELINE) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
        } else if (davResourceKind == DAVResourceKind.BASELINE_COLL) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
            resultURI.append("/");
        } else if (davResourceKind == DAVResourceKind.PUBLIC) {
            resultURI.append(parameterPath).append("/");
        } else if (davResourceKind == DAVResourceKind.VERSION) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
            resultURI.append(parameterPath);
        } else if (davResourceKind == DAVResourceKind.VCC) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(DAVResource.DEDAULT_VCC_NAME);
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
