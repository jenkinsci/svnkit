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
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVVersionResource extends DAVResource {

    public DAVVersionResource(SVNRepository repository, DAVResourceURI resourceURI, boolean isSVNClient, String deltaBase, long version, 
            String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) throws SVNException {
        super(repository, resourceURI, isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, userName, activitiesDB);
    }

    public DAVVersionResource(SVNRepository repository, DAVResourceURI resourceURI, long revision, boolean isSVNClient, String deltaBase, 
            long version, String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) {
        super(repository, resourceURI, revision, isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, userName, 
                activitiesDB);
    }

    private DAVVersionResource() {
    }

    protected void prepare() throws DAVException {
        if (!SVNRevision.isValidRevisionNumber(myRevision)) {
            try {
                myRevision = getLatestRevision();
            } catch (SVNException e) {
                throw DAVException.convertError(e.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Could not fetch 'youngest' revision to enable accessing the latest baseline resource.", null);
            }
        }
        
        if (myRoot == null) {
            try {
                myRoot = myFSFS.createRevisionRoot(myRevision);
            } catch (SVNException svne) {
                throw DAVException.convertError(svne.getErrorMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                        "Could not open a revision root.", null);
            }
        }
        
        getResourceURI().setURI(DAVPathUtil.buildURI(null, DAVResourceKind.BASELINE, getRevision(), null));
        setExists(true);
    }

    public DAVResource dup() {
        DAVVersionResource copy = new DAVVersionResource();
        copyTo(copy);
        return copy;
    }
}
