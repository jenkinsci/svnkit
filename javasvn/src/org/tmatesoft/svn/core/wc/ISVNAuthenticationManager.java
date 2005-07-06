/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.util.Map;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public interface ISVNAuthenticationManager {

    public String PASSWORD = "simple";

    public String USERNAME = "user";

    public String SSH = "ssh";

    public String SSL_CLIENT = "ssl.client";

    public String PROXY = "proxy";

    // method to get available username, username/password, username/key or
    // https client cert.
    // in case of ssl, ssh or proxy kind, realm is host:port string.
    // in case of username kind, realm is realm without host/port.
    // in case of password kind, realm is '<reposRootURL> realm'

    // pass null for "kind" or "realm" to get all credentials or all credentials
    // for realm.
    public SVNAuthentication[] getAvailableAuthentications(String kind,
            String realm);

    public SVNAuthentication getFirstAuthentication(String kind, String realm);

    public SVNAuthentication getNextAuthentication(String kind, String realm);

    public void addAuthentication(String realm, SVNAuthentication credentials,
            boolean store);

    public void setDefaultAuthentication(String userName, String password);

    public void deleteAuthentication(SVNAuthentication credentials);

    public boolean isAuthStorageEnabled();

    public void setAuthStorageEnabled(boolean enabled);

    public void setAuthenticationProvider(ISVNAuthenticationProvider provider);

    public ISVNAuthenticationProvider getAuthenticationProvider();

    public void deleteAllAuthentications();

    public void setRuntimeAuthenticationCache(Map cache);
}
