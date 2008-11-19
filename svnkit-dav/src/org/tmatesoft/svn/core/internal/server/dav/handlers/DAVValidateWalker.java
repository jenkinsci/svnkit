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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVIFHeader;
import org.tmatesoft.svn.core.internal.server.dav.DAVIFState;
import org.tmatesoft.svn.core.internal.server.dav.DAVIFStateType;
import org.tmatesoft.svn.core.internal.server.dav.DAVLock;
import org.tmatesoft.svn.core.internal.server.dav.DAVLockScope;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVValidateWalker implements IDAVResourceWalkHandler {

    public DAVResponse handleResource(DAVResponse response, DAVResource resource, DAVLockInfoProvider lockInfoProvider, int flags, 
            DAVLockScope lockScope, CallType callType) throws DAVException {
        return null;
    }

    private void validateResourceState(LinkedList ifHeaders, DAVResource resource, DAVLockInfoProvider provider, DAVLockScope lockScope, int flags) throws DAVException {
        DAVLock lock = null;
        if (provider != null) {
            try {
                lock = provider.getLock(resource);
            } catch (DAVException dave) {
                throw new DAVException("The locks could not be queried for verification against a possible \"If:\" header.", null, 
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, SVNLogType.NETWORK, Level.FINE, dave, null, null, 0, null);
            }
        }
    
        boolean seenLockToken = false;
        if (lockScope == DAVLockScope.EXCLUSIVE) {
            if (lock != null) {
                throw new DAVException("Existing lock(s) on the requested resource prevent an exclusive lock.", ServletDAVHandler.SC_HTTP_LOCKED, 0);
            }
            seenLockToken = true;
        } else if (lockScope == DAVLockScope.SHARED) {
            if (lock.getScope() == DAVLockScope.EXCLUSIVE) {
                throw new DAVException("The requested resource is already locked exclusively.", ServletDAVHandler.SC_HTTP_LOCKED, 0);
            }
            seenLockToken = true;
        } else {
            seenLockToken = lock == null;
        }
        
        if (ifHeaders == null || ifHeaders.isEmpty()) {
            if (seenLockToken) {
                return;
            }
            
            throw new DAVException("This resource is locked and an \"If:\" header was not supplied to allow access to the resource.", 
                    ServletDAVHandler.SC_HTTP_LOCKED, 0);
        }
        
        DAVIFHeader ifHeader = (DAVIFHeader) ifHeaders.getFirst();
        if (lock == null && ifHeader.isDummyHeader()) {
            if ((flags & ServletDAVHandler.DAV_VALIDATE_IS_PARENT) != 0) {
                return;
            }
            throw new DAVException("The locktoken specified in the \"Lock-Token:\" header is invalid because this resource has no outstanding locks.", 
                    HttpServletResponse.SC_BAD_REQUEST, 0);
        }

        String eTag = resource.getETag();
        String uri = DAVPathUtil.dropTraillingSlash(resource.getResourceURI().getRequestURI());
        
        
        int numThatAppy = 0;
        for (Iterator ifHeadersIter = ifHeaders.iterator(); ifHeadersIter.hasNext();) {
            ifHeader = (DAVIFHeader) ifHeadersIter.next();
            if (ifHeader.getURI() != null && !uri.equals(ifHeader.getURI())) {
                continue;
            }
            
            ++numThatAppy;
            LinkedList stateList = ifHeader.getStateList();
            for (Iterator stateListIter = stateList.iterator(); stateListIter.hasNext();) {
                DAVIFState state = (DAVIFState) stateListIter.next();
                if (state.getType() == DAVIFStateType.IF_ETAG) {
                    String currentETag = null;
                    String givenETag = null;
                    String stateETag = state.getETag();
                    if (stateETag.startsWith("W/")) {
                        givenETag = stateETag.substring(2);
                    } else {
                        givenETag = stateETag;
                    }
                    
                    if (eTag.startsWith("W/")) {
                        currentETag = eTag.substring(2);
                    } else {
                        currentETag = eTag;
                    }
                    
                    boolean eTagsDoNotMatch = !givenETag.equals(currentETag);
                    
                    if (state.getCondition() == DAVIFState.IF_CONDITION_NORMAL && eTagsDoNotMatch) {
                        
                    }
                }
            }
        }
        
    }
}
