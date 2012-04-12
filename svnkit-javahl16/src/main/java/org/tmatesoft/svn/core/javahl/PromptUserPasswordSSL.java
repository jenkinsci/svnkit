/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.javahl;

import org.tigris.subversion.javahl.PromptUserPassword3;



/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public interface PromptUserPasswordSSL extends PromptUserPassword3 {

    public boolean promptSSL(String realm, boolean maySave);
    
    public String getSSLClientCertPath();
    
    public String getSSLClientCertPassword();
}
