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

import javax.servlet.http.HttpServletResponse;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVRegularResource extends DAVResource {

    public DAVRegularResource(SVNRepository repository, DAVResourceURI resourceURI, boolean isSVNClient, String deltaBase, long version, 
            String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) throws SVNException {
        super(repository, resourceURI, isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, userName, activitiesDB);
    }

    public DAVRegularResource(SVNRepository repository, DAVResourceURI resourceURI, long revision, boolean isSVNClient, String deltaBase, 
            long version, String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) {
        super(repository, resourceURI, revision, isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, userName, 
                activitiesDB);
    }

    private DAVRegularResource() {
    }

    protected void prepare() throws DAVException {
        if (!SVNRevision.isValidRevisionNumber(myRevision)) {
            try {
                setRevision(getLatestRevision());
            } catch (SVNException e) {
                throw DAVException.convertError(e.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Could not determine the proper revision to access", null);
            }
        }
        
        if (myRoot == null) {
            try {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "myFSFS == null is " + (myFSFS == null));
                myRoot = myFSFS.createRevisionRoot(myRevision);
            } catch (SVNException svne) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Could not open the root of the repository", null);
            }
        }
        
        SVNNodeKind kind = DAVServletUtil.checkPath(myRoot, getResourceURI().getPath());
        setExists(kind != SVNNodeKind.NONE);
        setCollection(kind == SVNNodeKind.DIR);
    }

    public DAVResource dup() {
        DAVRegularResource copy = new DAVRegularResource();
        copyTo(copy);
        return copy;
    }

    public DAVResource getParentResource() throws DAVException {
        DAVRegularResource parentResource = new DAVRegularResource();
        
        copyTo(parentResource);

        DAVResourceURI parentResourceURI = parentResource.getResourceURI();
        String uri = parentResourceURI.getURI();
        String path = parentResourceURI.getPath();
        
        parentResourceURI.setURI(SVNPathUtil.removeTail(uri));
        parentResourceURI.setPath(SVNPathUtil.removeTail(path));
        
        parentResource.setExists(true);
        parentResource.setCollection(true);
        parentResource.setVersioned(true);
        return parentResource;
    }

}
