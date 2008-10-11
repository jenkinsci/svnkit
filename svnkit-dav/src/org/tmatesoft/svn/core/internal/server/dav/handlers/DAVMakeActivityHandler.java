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
import java.util.Date;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionRoot;
import org.tmatesoft.svn.core.internal.server.dav.DAVException;
import org.tmatesoft.svn.core.internal.server.dav.DAVRepositoryManager;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceType;
import org.tmatesoft.svn.core.internal.server.dav.DAVResourceURI;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class DAVMakeActivityHandler extends ServletDAVHandler {

    private FSFS myFSFS;
    
    public DAVMakeActivityHandler(DAVRepositoryManager repositoryManager, HttpServletRequest request, HttpServletResponse response) {
        super(repositoryManager, request, response);
    }
    
    public void execute() throws SVNException {
        DAVResource resource = getRequestedDAVResource(false, false);
        FSRepository repos = (FSRepository) resource.getRepository();
        myFSFS = repos.getFSFS();
 
        readInput(true);
        if (resource.exists()) {
            throw new DAVException("<DAV:resource-must-be-null/>", HttpServletResponse.SC_CONFLICT, SVNLogType.NETWORK);
        }

        if (!resource.canBeActivity()) {
            throw new DAVException("<DAV:activity-location-ok/>", HttpServletResponse.SC_FORBIDDEN, SVNLogType.NETWORK);
        }
        
    }

    protected DAVRequest getDAVRequest() {
        return null;
    }

    private void makeActivity(DAVResource resource) throws DAVException {
        FSTransactionInfo txnInfo = createActivity(resource);
        storeActivity(resource, txnInfo);
        
    }
    
    private void storeActivity(DAVResource resource, FSTransactionInfo txnInfo) throws DAVException {
        DAVResourceURI resourceURI = resource.getResourceURI();
        String activityID = resourceURI.getActivityID();
        File activitiesDB = resource.getActivitiesDB();
        if (!activitiesDB.mkdirs()) {
            throw new DAVException("could not initialize activity db.", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, SVNLogType.NETWORK, 
                    Level.FINE, null, null, null, 0, null);
        }
        
        String safeActivityID = SVNFileUtil.computeChecksum(activityID);
        File finalActivityFile = new File(activitiesDB, safeActivityID);
        File tmpFile = null;
        try {
            tmpFile = SVNFileUtil.createUniqueFile(activitiesDB, safeActivityID, "tmp", false);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Can't open activity db");
            throw DAVException.convertError(err, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "could not open files.");
        }
        
        StringBuffer activitiesContents = new StringBuffer();
        activitiesContents.append(txnInfo.getTxnId());
        activitiesContents.append('\n');
        activitiesContents.append(activityID);
        activitiesContents.append('\n');
        
        try {
            SVNFileUtil.writeToFile(tmpFile, activitiesContents.toString(), null);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Can't write to activity db");
            try {
                SVNFileUtil.deleteFile(tmpFile);
            } catch (SVNException e) {
            }
            throw DAVException.convertError(err, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "could not write files.");
        }
        
        try {
            SVNFileUtil.rename(tmpFile, finalActivityFile);
        } catch (SVNException svne) {
            try {
                SVNFileUtil.deleteFile(tmpFile);
            } catch (SVNException e) {
            }
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "could not replace files.");
        }
    }
    
    private FSTransactionInfo createActivity(DAVResource resource) throws DAVException {
        SVNProperties properties = new SVNProperties();
        properties.put(SVNRevisionProperty.AUTHOR, resource.getUserName());
        long revision = SVNRepository.INVALID_REVISION;
        try {
            myFSFS.getYoungestRevision();
        } catch (SVNException svne) {
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "could not determine youngest revision");
        }
        
        FSTransactionInfo txnInfo = null;
        try {
            txnInfo = FSTransactionRoot.beginTransactionForCommit(revision, properties, myFSFS);
        } catch (SVNException svne) {
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "could not begin a transaction");
        }
        
        return txnInfo;
    }
    
}
