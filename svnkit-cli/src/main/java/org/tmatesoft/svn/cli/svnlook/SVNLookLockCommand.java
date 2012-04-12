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

import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNLookLockCommand extends SVNLookCommand {
    
    protected SVNLookLockCommand() {
        super("lock", null);
    }

    protected Collection createSupportedOptions() {
        return new LinkedList();
    }

    public void run() throws SVNException {
        SVNLookCommandEnvironment environment = getSVNLookEnvironment(); 
        String path = environment.getFirstArgument();
        if (environment.getFirstArgument() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, 
                    "Missing path argument");
            SVNErrorManager.error(err, SVNLogType.CLIENT);
        } 

        SVNLookClient client = environment.getClientManager().getLookClient();
        SVNLock lock = client.doGetLock(environment.getRepositoryFile(), path);
        
        if (lock != null) {
            ISVNOptions options = getEnvironment().getClientManager().getOptions();
            String creationDate = SVNDate.formatHumanDate(lock.getCreationDate(), options);
            String expirationDate = lock.getExpirationDate() != null ? SVNDate.formatHumanDate(lock.getExpirationDate(), options) : ""; 
            int commentLinesCount = lock.getComment() != null ? SVNCommandUtil.getLinesCount(lock.getComment()) : 0; 

            getSVNLookEnvironment().getOut().println("UUID Token: " + lock.getID());
            getSVNLookEnvironment().getOut().println("Owner: " + lock.getOwner());
            getSVNLookEnvironment().getOut().println("Created: " + creationDate);
            getSVNLookEnvironment().getOut().println("Expires: " + expirationDate);
            
            String countMessage = "Comment (" + commentLinesCount + " ";
            String comment = lock.getComment() != null ? lock.getComment() : "";
            if (commentLinesCount != 1) {
                getSVNLookEnvironment().getOut().println(countMessage + " lines):");
                getSVNLookEnvironment().getOut().println(comment);
            } else {
                getSVNLookEnvironment().getOut().println(countMessage + "line):");
                getSVNLookEnvironment().getOut().println(comment);
            }
          
        }
        
    }

}
