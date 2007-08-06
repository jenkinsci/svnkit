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
public class DAVResourceUtil {

    public static String buildURI(String context, String repositoryPath, DAVResourceKind davResourceKind, long revision, String uri) {
        StringBuffer resultURI = new StringBuffer();
        resultURI.append(context);
        resultURI.append(repositoryPath.startsWith("/") ? "" : "/");
        resultURI.append(repositoryPath);
        resultURI.append(("".equals(repositoryPath) || repositoryPath.endsWith("/")) ? "" : "/");
        if (davResourceKind == DAVResourceKind.ACT_COLLECTION) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString());
        } else if (davResourceKind == DAVResourceKind.BASELINE || davResourceKind == DAVResourceKind.BASELINE_COLL) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
        } else if (davResourceKind == DAVResourceKind.PUBLIC) {
            resultURI.append(uri).append("/");
        } else if (davResourceKind == DAVResourceKind.VERSION) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
            resultURI.append(uri).append("/");
        } else if (davResourceKind == DAVResourceKind.VCC) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(DAVResource.DEDAULT_VCC_NAME);
        }
        return resultURI.toString();
    }
}
