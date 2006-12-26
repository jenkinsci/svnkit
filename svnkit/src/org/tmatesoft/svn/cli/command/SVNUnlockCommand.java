/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNUnlockCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(PrintStream out, PrintStream err) throws SVNException {
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);

        Collection files = new ArrayList();
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            files.add(new File(getCommandLine().getPathAt(i)));
        }
        File[] filesArray = (File[]) files.toArray(new File[files.size()]);
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNWCClient wcClient = getClientManager().getWCClient();
        if (filesArray.length > 0) {
            wcClient.doUnlock(filesArray, force);
        }
        files.clear();
        
        for (int i = 0; i < getCommandLine().getURLCount(); i++) {
            files.add(getCommandLine().getURL(i));
        }
        String[] urls = (String[]) files.toArray(new String[files.size()]);
        SVNURL[] svnURLs = new SVNURL[urls.length];
        for (int i = 0; i < urls.length; i++) {
            svnURLs[i] = SVNURL.parseURIEncoded(urls[i]);
        }
        if (urls.length > 0) {
            wcClient.doUnlock(svnURLs, force);
        }
    }

}
