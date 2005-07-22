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

import org.tmatesoft.svn.core.SVNURL;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNAuthenticationProvider {

    public int REJECTED = 0;
    public int ACCEPTED_TEMPORARY = 1;
    public int ACCEPTED = 2;

    public SVNAuthentication requestClientAuthentication(String kind,
            SVNURL url, String realm, String errorMessage, SVNAuthentication previousAuth,
            boolean authMayBeStored);

    public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored);
}
