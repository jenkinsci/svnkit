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
package org.tmatesoft.svn.core.internal.io.dav;

import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface IDAVProxyManager {
    
    public static IDAVProxyManager DEFAULT = new IDAVProxyManager() {

        public boolean isProxyEnabled(SVNRepositoryLocation location) {
            if (Boolean.TRUE.toString().equalsIgnoreCase(System.getProperty("http.proxySet"))) {
                return !DAVUtil.matchHost(System.getProperty("http.nonProxyHosts"), location.getHost());
            }
            return false;
        }

        public String getProxyHost(SVNRepositoryLocation location) {
            return System.getProperty("http.proxyHost");
        }

        public int getProxyPort(SVNRepositoryLocation location) {
            String value = System.getProperty("http.proxyPort");
            if (value == null) {
                return 3128;
            }
            try {
                return Integer.parseInt(System.getProperty("http.proxyPort"));
            } catch (Throwable th) {            
            }
            return 3128;
        }

        public String getProxyUserName(SVNRepositoryLocation location) {
            return System.getProperty("http.proxyUser");
        }

        public String getProxyPassword(SVNRepositoryLocation location) {
            return System.getProperty("http.proxyPassword");
        }
        
    };
    
    public boolean isProxyEnabled(SVNRepositoryLocation location);

    public String getProxyHost(SVNRepositoryLocation location);

    public int getProxyPort(SVNRepositoryLocation location);
    
    public String getProxyUserName(SVNRepositoryLocation location);

    public String getProxyPassword(SVNRepositoryLocation location);
}
