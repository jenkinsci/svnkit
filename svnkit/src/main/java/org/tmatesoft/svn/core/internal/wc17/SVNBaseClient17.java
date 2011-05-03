/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc16.SVNBasicDelegate;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.SVNWCNodeReposInfo;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public abstract class SVNBaseClient17 extends SVNBasicDelegate {

    private SVNWCContext myContext;

    protected SVNBaseClient17(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNBaseClient17(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    public SVNWCContext getContext() throws SVNException {
        if (myContext == null) {
            synchronized (this) {
                if (myContext == null) {
                    myContext = new SVNWCContext(getOptions(), getEventDispatcher());
                }
            }
        }
        return myContext;
    }

    public void setContext(SVNWCContext context) {
        if (myContext == null) {
            synchronized (this) {
                if (myContext == null) {
                    myContext = new SVNWCContext(context.getDb(), getEventDispatcher());
                }
            }
        }
    }

    public void closeContext() throws SVNException {
        if (myContext != null) {
            myContext.close();
        }
    }

    protected SVNRepository createRepository(SVNURL baseUrl, File baseDirAbspath, boolean mayReuse) throws SVNException {
        String uuid = null;
        if (baseDirAbspath != null) {
            try {
                SVNWCNodeReposInfo nodeReposInfo = getContext().getNodeReposInfo(baseDirAbspath, false, false);
                uuid = nodeReposInfo.reposUuid;
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_WORKING_COPY && e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND
                        || e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_UPGRADE_REQUIRED) {
                    throw e;
                }
            }
        }
        return createRepository(baseUrl, uuid, mayReuse);
    }

}
