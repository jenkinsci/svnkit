/*
 * Created on 01.04.2005
 */
package org.tmatesoft.svn.core.internal.io.dav;

import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

public interface IDAVProxyManager {
    
    public static IDAVProxyManager DEFAULT = new IDAVProxyManager() {

        public boolean isProxyEnabled(SVNRepositoryLocation location) {
            return Boolean.TRUE.toString().equalsIgnoreCase(System.getProperty("http.proxySet"));
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
