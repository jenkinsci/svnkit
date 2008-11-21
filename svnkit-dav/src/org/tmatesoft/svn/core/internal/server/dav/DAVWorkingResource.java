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
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.File;
import java.util.logging.Level;

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVWorkingResource extends DAVResource {
    
    public DAVWorkingResource(SVNRepository repository, DAVResourceURI resourceURI, boolean isSVNClient, String deltaBase, long version, 
            String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) throws SVNException {
        super(repository, resourceURI, isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, userName, activitiesDB);
    }

    public DAVWorkingResource(SVNRepository repository, DAVResourceURI resourceURI, long revision, boolean isSVNClient, String deltaBase, 
            long version, String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) {
        super(repository, resourceURI, revision, isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, userName, 
                activitiesDB);
    }

    public DAVWorkingResource(DAVResource baseResource, DAVResourceURI resourceURI, String txnName) {
        super(baseResource.getRepository(), resourceURI, baseResource.getRevision(), baseResource.isSVNClient(), baseResource.getDeltaBase(), 
                baseResource.getVersion(), baseResource.getClientOptions(), baseResource.getBaseChecksum(), baseResource.getResultChecksum(), 
                baseResource.getUserName(), baseResource.getActivitiesDB());
        
        myTxnName = txnName;
        myRoot = baseResource.myRoot;
        myFSFS = baseResource.myFSFS;
    }
    
    private DAVWorkingResource() {
    }
    
    protected void prepare() throws DAVException {
        String txnName = getTxn();
        if (txnName == null) {
            throw new DAVException("An unknown activity was specified in the URL. This is generally caused by a problem in the client software.", 
                    null, HttpServletResponse.SC_BAD_REQUEST, null, SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
        }
        myTxnName = txnName;
        FSTransactionInfo txnInfo = null;
        try {
            txnInfo = myFSFS.openTxn(myTxnName);
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NO_SUCH_TRANSACTION) {
                throw new DAVException("An activity was specified and found, but the corresponding SVN FS transaction was not found.", 
                        null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null, SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null); 
            }
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "An activity was specified and found, but the corresponding SVN FS transaction was not found.", null);
        }
        
        if (isBaseLined()) {
            setExists(true);
            //myIsExists = true;
            return;
        }
        
        if (myUserName != null) {
            SVNProperties props = null;
            try {
                props = myFSFS.getTransactionProperties(myTxnName);
            } catch (SVNException svne) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Failed to retrieve author of the SVN FS transaction corresponding to the specified activity.", null);
            }
            
            String currentAuthor = props.getStringValue(SVNRevisionProperty.AUTHOR);
            if (currentAuthor == null) {
                try {
                    myFSFS.setTransactionProperty(myTxnName, SVNRevisionProperty.AUTHOR, SVNPropertyValue.create(myUserName));
                } catch (SVNException svne) {
                    throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                            "Failed to set the author of the SVN FS transaction corresponding to the specified activity.", null);
                }
            } else if (!currentAuthor.equals(myUserName)) {
                throw new DAVException("Multi-author commits not supported.", null, HttpServletResponse.SC_NOT_IMPLEMENTED, null, 
                        SVNLogType.NETWORK, Level.FINE, null, null, null, 0, null);
            }
        }
        
        try {
            myRoot = myFSFS.createTransactionRoot(txnInfo);
        } catch (SVNException svne) {
            throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                    "Could not open the (transaction) root of the repository", null);
        }
        
        SVNNodeKind kind = DAVServletUtil.checkPath(myRoot, getResourceURI().getPath());
        setExists(kind != SVNNodeKind.NONE);
        setCollection(kind == SVNNodeKind.DIR);
    }

    public DAVResource dup() {
        DAVWorkingResource copy = new DAVWorkingResource();
        copyTo(copy);
        return copy;
    }

    public DAVResource getParentResource() throws DAVException {
        return DAVPrivateResource.createPrivateResource(this, DAVResourceKind.WORKING);
    }

    public static DAVWorkingResource createWorkingResource(DAVResource baseResource, String activityID, String txnName) {
        StringBuffer pathBuffer = new StringBuffer();
        if (baseResource.isBaseLined()) {
            pathBuffer.append('/');
            pathBuffer.append(DAVResourceURI.SPECIAL_URI);
            pathBuffer.append("/wbl/");
            pathBuffer.append(activityID);
            pathBuffer.append('/');
            pathBuffer.append(baseResource.getRevision());
        } else {
            pathBuffer.append('/');
            pathBuffer.append(DAVResourceURI.SPECIAL_URI);
            pathBuffer.append("/wrk/");
            pathBuffer.append(activityID);
            pathBuffer.append(baseResource.getResourceURI().getPath());
        }
        
        String uriPath = SVNEncodingUtil.uriEncode(pathBuffer.toString());
        DAVResourceURI uri = new DAVResourceURI(baseResource.getResourceURI().getContext(), uriPath, baseResource.getResourceURI().getPath(), 
                baseResource.getRevision(), baseResource.getResourceURI().getKind(), DAVResourceType.WORKING, 
                activityID, true, true, baseResource.isBaseLined(), true);
        return new DAVWorkingResource(baseResource, uri, txnName);
    }

}
