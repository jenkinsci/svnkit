/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2;

import java.io.File;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNNotifyPrinter implements ISVNEventHandler {
    
    private SVNCommandEnvironment myEnvironment;

    public SVNNotifyPrinter(SVNCommandEnvironment env) {
        myEnvironment = env;
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        File file = event.getFile();
        String path = null;
        if (file != null) {
            path = myEnvironment.getRelativePath(file);
            path = SVNCommandUtil.getLocalPath(path);
        } else if (event.getPath() != null) {
            path = event.getPath();
        }
        if (event.getAction() == SVNEventAction.STATUS_EXTERNAL) {
            myEnvironment.getOut().println();
            myEnvironment.getOut().println("Performing status on external item at '" + path + "'");
        } else if (event.getAction() == SVNEventAction.STATUS_COMPLETED) {
            String revStr = Long.toString(event.getRevision());
            myEnvironment.getOut().println("Status against revision: " + SVNCommandUtil.formatString(revStr, 6, false));
        }
    }

    public void checkCancelled() throws SVNCancelException {
    }

}
