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

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.internal.server.dav.DAVDepth;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.handlers.IDAVResourceWalkHandler.CallType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVResourceWalker {
    public static final int DAV_WALKTYPE_AUTH = 0x0001;
    public static final int DAV_WALKTYPE_NORMAL = 0x0002;
    public static final int DAV_WALKTYPE_LOCKNULL = 0x0004;

    private LinkedList myIfHeaders;
    private DAVResource myResource;
    private DAVLockInfoProvider myLockInfoProvider;
    private int myFlags;
    private int myWalkType;
    
    public DAVResponse walk(DAVLockInfoProvider lockInfoProvider, DAVResource resource, LinkedList ifHeaders, int flags, int walkType, 
            IDAVResourceWalkHandler handler, DAVDepth depth) throws DAVException {
        myIfHeaders = ifHeaders;
        myLockInfoProvider = lockInfoProvider;
        myResource = resource;
        myFlags = flags;
        myWalkType = walkType;

        DAVResource resourceCopy = resource.dup();
        return null;
    }
 
    private DAVResponse doWalk(IDAVResourceWalkHandler handler, DAVDepth depth) throws DAVException {
        boolean isDir = myResource.isCollection();
        DAVResponse response = handler.handleResource(myResource, isDir ? CallType.COLLECTION : CallType.MEMBER);
        
        if (depth == DAVDepth.DEPTH_ZERO || !isDir) {
            return response;
        }
        
        if (myResource.isWorking()) {
            return response;
        }
        
        if (myResource.getType() != DAVResourceType.REGULAR) {
            throw new DAVException("Walking the resource hierarchy can only be done on 'regular' resources [at this time].", 
                    HttpServletResponse.SC_METHOD_NOT_ALLOWED, 0);
        }
 
        
        return null;
    }
    
}
