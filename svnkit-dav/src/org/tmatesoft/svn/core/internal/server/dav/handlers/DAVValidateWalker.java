/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import java.util.LinkedList;
import java.util.logging.Level;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVLock;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVValidateWalker implements IDAVResourceWalkHandler {

    public DAVResponse handleResource(DAVResponse response, DAVResourceWalker callBack, CallType callType) throws DAVException {
        return null;
    }

    private void validateResourceState(LinkedList ifHeaders, DAVResourceWalker callBack) throws DAVException {
        DAVLock lock = null;
        DAVLockInfoProvider provider = callBack.getLockInfoProvider();
        if (provider != null) {
            try {
                lock = provider.getLock(callBack.getResource());
            } catch (DAVException dave) {
                throw new DAVException("The locks could not be queried for verification against a possible \"If:\" header.", null, 
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, SVNLogType.NETWORK, Level.FINE, dave, null, null, 0, null);
            }
        }
        
        
    }
}
