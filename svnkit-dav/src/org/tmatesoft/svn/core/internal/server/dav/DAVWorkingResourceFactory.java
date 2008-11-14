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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVWorkingResourceFactory extends DAVResourceFactory {

    protected DAVResource createDAVResourceChildImpl(SVNRepository repository, DAVResourceURI resourceURI, long revision, boolean isSVNClient, 
            String deltaBase, long version, String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) {
        return new DAVWorkingResource(repository, resourceURI, revision, isSVNClient, deltaBase, version, clientOptions, baseChecksum, 
                resultChecksum, userName, activitiesDB);
    }

    protected DAVResource createDAVResourceImpl(SVNRepository repository, DAVResourceURI resourceURI, boolean isSVNClient, String deltaBase, 
            long version, String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) throws SVNException {
        return new DAVWorkingResource(repository, resourceURI, isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, 
                userName, activitiesDB);
    }

}
