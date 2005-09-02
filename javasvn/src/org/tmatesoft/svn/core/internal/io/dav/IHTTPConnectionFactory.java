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

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.commons.CommonsHTTPConnection;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface IHTTPConnectionFactory {
    
    public String HTTP_CLIENT_CLASS_NAME = "org.apache.commons.httpclient.HttpClient";
    public String HTTP_CLIENT_PROPERTY = "javasvn.httpclient";
    public String HTTP_CLIENT_JAKARTA = "jakarta";
    
    public IHTTPConnectionFactory DEFAULT = new IHTTPConnectionFactory() {

        public IHTTPConnection createHTTPConnection(SVNURL location, SVNRepository repository) {
            try {
                Class httpClientClass = getClass().getClassLoader().loadClass(HTTP_CLIENT_CLASS_NAME);
                if (httpClientClass != null && HTTP_CLIENT_JAKARTA.equalsIgnoreCase(System.getProperty(HTTP_CLIENT_PROPERTY))) {
                    return new CommonsHTTPConnection(location, repository);
                }
            } catch (ClassNotFoundException e) {
            }
            return new DefaultHTTPConnection(location, repository);
        }
        
    };
    
    public IHTTPConnection createHTTPConnection(SVNURL location, SVNRepository repository);

}
