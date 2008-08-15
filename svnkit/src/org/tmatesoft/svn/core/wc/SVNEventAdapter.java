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
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;


/**
 * <code>SVNEventAdapter</code> is a simple no-op implementation of {@link ISVNEventHandler}.
 * 
 * @version 1.2.0
 * @author  TMate Software Ltd.
 * @since   1.2.0
 */
public class SVNEventAdapter implements ISVNEventHandler {

    /**
     * Does nothing.
     * @throws SVNCancelException no
     */
    public void checkCancelled() throws SVNCancelException {
    }

    /**
     * Does nothing.
     * 
     * @param event 
     * @param progress 
     * @throws SVNException no 
     */
    public void handleEvent(SVNEvent event, double progress) throws SVNException {
    }

}
