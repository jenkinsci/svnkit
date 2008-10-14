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

import java.io.File;
import java.io.IOException;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.DAVPathUtil;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;
import org.tmatesoft.svn.core.internal.server.dav.DAVServletUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class DAVActivityResourceTypeHandler implements IDAVResourceTypeHandler {

    public DAVResource handleResource(SVNRepository repository, DAVResourceURI resourceURI, boolean isSVNClient, String deltaBase, long version, 
            String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) throws SVNException {
        DAVResource resource =  new DAVResource(repository, resourceURI, isSVNClient, deltaBase, version, clientOptions, 
                baseChecksum, resultChecksum, userName, activitiesDB);
        String txnName = getTxn(resource);
        resource.setExists(txnName != null);
        return resource;
    }
    
    public String getTxn(DAVResource resource) {
        DAVResourceURI resourceURI = resource.getResourceURI();
        File activityFile = DAVPathUtil.getActivityPath(resource.getActivitiesDB(), resourceURI.getActivityID());
        try {
            return DAVServletUtil.readTxn(activityFile);
        } catch (IOException e) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.FSFS, e.getMessage());
        }
        return null;
    }

}
