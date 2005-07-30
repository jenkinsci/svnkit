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
package org.tmatesoft.svn.core.internal.util;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNURLUtil {

    public static SVNURL getCommonURLAncestor(SVNURL url1, SVNURL url2) {
        // skip protocol and host, if they are different -> return null;
        if (url1 == null || url2 == null) {
            return null;
        }
        if (!url1.getProtocol().equals(url2.getProtocol()) || !url1.getHost().equals(url2.getHost()) || 
                url1.getPort() != url2.getPort()) {
            return null;
        }
        if (url1.getUserInfo() != null) {
            if (url1.getUserInfo().equals(url2.getUserInfo())) {
                return null;
            }
        } else {
            if (url1.getUserInfo() != url2.getUserInfo()) {
                return null;
            }
        }
        String path1 = url1.getPath();
        String path2 = url2.getPath();
        String commonPath = SVNPathUtil.getCommonPathAncestor(path1, path2);
        try {
            return SVNURL.create(url1.getProtocol(), url1.getUserInfo(), url1.getHost(), url1.hasPort() ? url1.getPort() : -1, commonPath, false);
        } catch (SVNException e) {
        }
        return null;
    }
}
