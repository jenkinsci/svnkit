/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * The <b>ISVNRepositoryFactory</b> interface is used by 
 * <b>SVN</b>*<b>Client</b> objects to create a low-level SVN protocol
 * driver that allows them to directly work with a repository.
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNRepositoryFactory {
    /**
     * Creates a low-level SVN protocol driver to access a repository.
     * 
     * @param  url            a repository location to establish a 
     *                        connection with (will be the root directory
     *                        for the working session)
     * @return                a low-level API driver for direct interacting
     *                        with a repository
     * @throws SVNException
     */
    public SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException;
}
