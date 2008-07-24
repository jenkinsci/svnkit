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
package org.tmatesoft.svn.core.io;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public interface ISVNReplayHandler {

    public ISVNEditor handleStartRevision(long revision, SVNProperties revisionProperties) throws SVNException;
    
    public void handleEndRevision(long revision, SVNProperties revisionProperties, ISVNEditor editor) throws SVNException;
}
