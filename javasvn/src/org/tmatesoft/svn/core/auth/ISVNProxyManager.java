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
package org.tmatesoft.svn.core.auth;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNProxyManager {
    
    public String getProxyHost();
    
    public int getProxyPort();
    
    public String getProxyUserName();
    
    public String getProxyPassword();

    public void acknowledgeProxyContext(boolean accepted, String errorMessage);
}
