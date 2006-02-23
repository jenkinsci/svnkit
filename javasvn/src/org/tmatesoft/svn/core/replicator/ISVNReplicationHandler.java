/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.replicator;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNReplicationHandler {

    public void revisionReplicating(SVNRepositoryReplicator source, SVNLogEntry logEntry) throws SVNException;

    public void revisionReplicated(SVNRepositoryReplicator source, SVNCommitInfo commitInfo) throws SVNException;

    public void checkCancelled() throws SVNCancelException;
}
