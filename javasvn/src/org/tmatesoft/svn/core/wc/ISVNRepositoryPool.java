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
 * The <b>ISVNRepositoryPool</b> interface is used by 
 * <b>SVN</b>*<b>Client</b> objects to create a low-level SVN protocol
 * driver that allows them to directly work with a repository.
 * 
 * <p>
 * A default implementation of the <b>ISVNRepositoryPool</b> interface - 
 * <b>DefaultSVNRepositoryPool</b> class - may cache the created
 * <b>SVNRepository</b> objects in a common pool. Several threads may
 * share that pool, but each thread is able only to retrieve those objects,
 * that belong to it (were created in that thread). 
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 * @see     DefaultSVNRepositoryPool
 */
public interface ISVNRepositoryPool {
    /**
     * Creates a low-level SVN protocol driver to access a repository.
     * 
     * @param  url            a repository location to establish a 
     *                        connection with (will be the root directory
     *                        for the working session)
     * @return                a low-level API driver for direct interacting
     *                        with a repository
     * @throws SVNException   if <code>url</code> is malformed or there's
     *                        no appropriate implementation for a protocol
     */
    public SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException;
    
    /**
     * Forces cached <b>SVNRepository</b> drivers to close their socket 
     * connections. 
     * 
     * <p>
     * A default implementation <b>DefaultSVNRepositoryPool</b>
     * is able to cache <b>SVNRepository</b> objects in a common pool 
     * shared between multiple threads. This method allows to close
     * connections of all the cached objects.
     * 
     * @param shutdownAll if <span class="javakeyword">true</span> - closes
     *                    connections of all the <b>SVNRepository</b> objects,
     *                    if <span class="javakeyword">false</span> - connections
     *                    of only some part of <b>SVNRepository</b> objects (for example,
     *                    those, that are not needed anymore)
     *                    
     */
    public void shutdownConnections(boolean shutdownAll);
}
