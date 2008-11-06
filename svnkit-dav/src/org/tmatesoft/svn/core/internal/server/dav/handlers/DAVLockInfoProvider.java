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

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVLockInfoProvider {

    private static final String LOCK_BREAK_OPTION = "lock-break";
    private static final String LOCK_STEAL_OPTION = "lock-steal";
    private static final String RELEASE_LOCKS_OPTION = "release-locks";
    private static final String KEEP_LOCKS_OPTION = "keep-locks";
    private static final String NO_MERGE_RESPONSE = "no-merge-response";
    
    private boolean myIsReadOnly;
    private boolean myIsStealLock;
    private boolean myIsBreakLock;
    private boolean myIsKeepLocks;
    private long myWorkingRevision;
    private ServletDAVHandler myOwner;
    
    public static DAVLockInfoProvider createLockInfoProvider(ServletDAVHandler owner, boolean readOnly) {
        String clientOptions = owner.getRequestHeader(ServletDAVHandler.SVN_OPTIONS_HEADER);
        
        DAVLockInfoProvider provider = new DAVLockInfoProvider();
        provider.myOwner = owner;
        provider.myIsReadOnly = readOnly;
        
        if (clientOptions != null) {
            if (clientOptions.indexOf(LOCK_BREAK_OPTION) != -1) {
                provider.myIsBreakLock = true;
            } 
            if (clientOptions.indexOf(LOCK_STEAL_OPTION) != -1) {
                provider.myIsStealLock = true;
            }
            if (clientOptions.indexOf(KEEP_LOCKS_OPTION) != -1) {
                provider.myIsKeepLocks = true;
            }
        }
        
        String versionName = owner.getRequestHeader(ServletDAVHandler.SVN_VERSION_NAME_HEADER);
        provider.myWorkingRevision = SVNRepository.INVALID_REVISION;
        if (versionName != null) {
            provider.myWorkingRevision = Long.parseLong(versionName);
        }
        
        return provider;
    }

    public boolean hasLocks(DAVResource resource) throws DAVException {
        if (resource.getResourceURI().getPath() == null) {
            return false;
        }
        
        if (DAVHandlerFactory.METHOD_LOCK.equals(myOwner.getRequestMethod())) {
            return false;
        }
        
        //TODO: add authz check here later
        SVNLock lock = null;
        try {
            lock = resource.getLock(); 
        } catch (SVNException svne) {
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "Failed to check path for a lock.", null);
        }
        return lock != null;
    }
    
    public boolean isReadOnly() {
        return myIsReadOnly;
    }
    
    public boolean isStealLock() {
        return myIsStealLock;
    }
    
    public boolean isBreakLock() {
        return myIsBreakLock;
    }
    
    public boolean isKeepLocks() {
        return myIsKeepLocks;
    }
    
    public long getWorkingRevision() {
        return myWorkingRevision;
    }
    
}
