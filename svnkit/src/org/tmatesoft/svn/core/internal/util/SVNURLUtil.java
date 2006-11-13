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
package org.tmatesoft.svn.core.internal.util;

import java.util.Collection;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;


/**
 * @version 1.1.0
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
            if (!url1.getUserInfo().equals(url2.getUserInfo())) {
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
            return url1.setPath(commonPath, false);
        } catch (SVNException e) {
        }
        return null;
    }

    public static SVNURL condenceURLs(SVNURL[] urls, Collection condencedPaths, boolean removeRedundantURLs) {
        if (urls == null || urls.length == 0) {
            return null;
        }
        if (urls.length == 1) {
            return urls[0];
        }
        SVNURL rootURL = urls[0];
        for (int i = 0; i < urls.length; i++) {
            rootURL = getCommonURLAncestor(rootURL, urls[i]);
        }
        
        if (condencedPaths != null && removeRedundantURLs) {
            for (int i = 0; i < urls.length; i++) {
                SVNURL url1 = urls[i];
                if (url1 == null) {
                    continue;
                }
                for (int j = 0; j < urls.length; j++) {
                    if (i == j) {
                        continue;
                    }
                    SVNURL url2 = urls[j];
                    if (url2 == null) {
                        continue;
                    }
                    SVNURL common = getCommonURLAncestor(url1, url2);
                    if (common == null) {
                        continue;
                    }
                    if (common.equals(url1)) {
                        urls[j] = null;
                    } else if (common.equals(url2)) {
                        urls[i] = null;
                    }
                }
            }
            for (int j = 0; j < urls.length; j++) {
                SVNURL url = urls[j];
                if (url != null && url.equals(rootURL)) {
                    urls[j] = null;
                }
            }
        }
    
        if (condencedPaths != null) {
            for (int i = 0; i < urls.length; i++) {
                SVNURL url = urls[i];
                if (url == null) {
                    continue;
                }
                String path = url.toString();
                if (rootURL != null) {
                    path = path.substring(rootURL.toString().length());
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                }
                condencedPaths.add(path);
            }
        }
        return rootURL;
    }

}
