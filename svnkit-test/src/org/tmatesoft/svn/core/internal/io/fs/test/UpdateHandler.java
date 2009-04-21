/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs.test;

import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;

/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class UpdateHandler implements ISVNEventHandler {

    private Map myEvents;

    public UpdateHandler() {
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        if (myEvents != null) {
            myEvents.put(event.getFile().getAbsolutePath(), event);
        }
    }

    public void checkCancelled() throws SVNCancelException {
    }

    public void setEventsMap(Map pathsToEvents) {
        myEvents = pathsToEvents;
    }
}
