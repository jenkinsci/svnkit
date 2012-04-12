/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svnlook;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLookInfoCommand extends SVNLookCommand {

    public SVNLookInfoCommand() {
        super("info", null);
    }
    
    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNLookOption.REVISION);
        options.add(SVNLookOption.TRANSACTION);
        return options;
    }

    public void run() throws SVNException {
        SVNLookCommandEnvironment environment = getSVNLookEnvironment(); 
        SVNLookClient client = environment.getClientManager().getLookClient();
        SVNLogEntry logEntry = null;
        if (environment.isRevision()) {
            logEntry = client.doGetInfo(environment.getRepositoryFile(), 
                    getRevisionObject());
        } else {
            logEntry = client.doGetInfo(environment.getRepositoryFile(), environment.getTransaction());
        }
        
        String author = logEntry.getAuthor() != null ? logEntry.getAuthor() : "";
        String date = logEntry.getDate() != null ? SVNDate.formatCustomDate(logEntry.getDate()) : "";
        String log = logEntry.getMessage() != null ? logEntry.getMessage() : ""; 
        environment.getOut().println(author);
        environment.getOut().println(date);
        if (log == null || log.length() == 0) {
            environment.getOut().println("0");
        } else {
            environment.getOut().println(String.valueOf(log.length()));
            environment.getOut().println(log);
        }
    }

}
