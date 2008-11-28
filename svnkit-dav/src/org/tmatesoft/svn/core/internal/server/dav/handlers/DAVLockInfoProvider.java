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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.tmatesoft.svn.core.internal.io.fs.FSLock;
import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVErrorCode;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVLock;
import org.tmatesoft.svn.core.internal.server.dav.DAVLockRecType;
import org.tmatesoft.svn.core.internal.server.dav.DAVLockScope;
import org.tmatesoft.svn.core.internal.server.dav.DAVLockType;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;


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
    
    public static DAVLockInfoProvider createLockInfoProvider(ServletDAVHandler owner, boolean readOnly) throws SVNException {
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
            try {
                provider.myWorkingRevision = Long.parseLong(versionName);
            } catch (NumberFormatException nfe) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, nfe), SVNLogType.NETWORK);
            }
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
    
    public DAVLock getLock(DAVResource resource) throws DAVException {
        if (resource.getResourceURI().getPath() == null) {
            return null;
        }

        if (DAVHandlerFactory.METHOD_LOCK.equals(myOwner.getRequestMethod())) {
            return null;
        }
        
        //TODO: add authz check here later

        DAVLock davLock = null;
        FSLock lock = null;
        try {
            lock = (FSLock) resource.getLock(); 
        } catch (SVNException svne) {
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "Failed to check path for a lock.", null);
        }
        
        if (lock != null) {
            davLock = convertToDAVLock(lock, myIsBreakLock, resource.exists());
            myOwner.setResponseHeader(HTTPHeader.CREATION_DATE_HEADER, SVNDate.formatDate(lock.getCreationDate()));
            myOwner.setResponseHeader(HTTPHeader.LOCK_OWNER_HEADER, lock.getOwner());
        }
        return davLock;
    }
    
    public DAVLock findLock(DAVResource resource, String lockToken) throws DAVException {
        //TODO: add here authz check later
        
        DAVLock davLock = null;
        FSLock lock = null;
        try {
            lock = (FSLock) resource.getLock(); 
        } catch (SVNException svne) {
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "Failed to look up lock by path.", null);
        }
        
        if (lock != null) {
            if (!lockToken.equals(lock.getID())) {
                throw new DAVException("Incoming token doesn't match existing lock.", HttpServletResponse.SC_BAD_REQUEST, 
                        DAVErrorCode.LOCK_SAVE_LOCK);
            }
            davLock = convertToDAVLock(lock, false, resource.exists());
            myOwner.setResponseHeader(HTTPHeader.CREATION_DATE_HEADER, SVNDate.formatDate(lock.getCreationDate()));
            myOwner.setResponseHeader(HTTPHeader.LOCK_OWNER_HEADER, lock.getOwner());
        }
        return davLock;
    }
        
    public void removeLock(DAVResource resource, String lockToken) throws DAVException {
        DAVResourceURI resourceURI = resource.getResourceURI();
        if (resourceURI.getPath() == null) {
            return;
        }
        
        if (isKeepLocks()) {
            return;
        }
        
        //TODO: add here authz check later
        String token = null;
        SVNLock lock = null;
        if (lockToken == null) {
            try {
                lock = resource.getLock();
            } catch (SVNException svne) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Failed to check path for a lock.", null);
            }
            token = lock.getID();
        } else {
            token = lockToken;
        }
        
        if (token != null) {
            try {
                resource.unlock(token, isBreakLock());
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NO_USER) {
                    throw new DAVException("Anonymous lock removal is not allowed.", HttpServletResponse.SC_UNAUTHORIZED, 
                            DAVErrorCode.LOCK_SAVE_LOCK);
                }
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Failed to remove a lock.", null);
            }
            //TODO: add logging here
        }
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
    
    private DAVLock convertToDAVLock(FSLock lock, boolean hideAuthUser, boolean exists) {
        String authUser = null;
        StringBuffer owner = null;
        if (lock.getComment() != null) {
            owner = new StringBuffer();
            if (!lock.isDAVComment()) {
                List namespaces = new ArrayList(1);
                namespaces.add(DAVElement.DAV_NAMESPACE);
                owner = DAVXMLUtil.openNamespaceDeclarationTag(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.LOCK_OWNER.getName(), namespaces, 
                        null, owner, false);
                owner.append(SVNEncodingUtil.xmlEncodeAttr(lock.getComment()));
                owner = SVNXMLUtil.addXMLFooter(SVNXMLUtil.DAV_NAMESPACE_PREFIX, DAVElement.LOCK_OWNER.getName(), owner);
            } else {
                owner.append(lock.getComment());
            }
        }
        
        if (!hideAuthUser) {
            authUser = lock.getOwner();
        }
        
        return new DAVLock(authUser, DAVDepth.DEPTH_ZERO, exists, lock.getID(), owner != null ? owner.toString() : null, DAVLockRecType.DIRECT, 
                DAVLockScope.EXCLUSIVE, DAVLockType.WRITE, lock.getExpirationDate());
    }
    
    public static class GetLocksCallType {
        public static final GetLocksCallType RESOLVED = new GetLocksCallType();
        public static final GetLocksCallType PARTIAL = new GetLocksCallType();
        public static final GetLocksCallType COMPLETE = new GetLocksCallType();
        
        private GetLocksCallType() {
        }
    }
}
