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

package org.tmatesoft.svn.core.internal.io.svn;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public abstract class SVNAuthenticator {

    private String myName;

    protected SVNAuthenticator(String name) {
        myName = name;
    }

    public String getName() {
        return myName;
    }

    public abstract byte[] buildChallengeReponse(byte[] challenge);
}
