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
package org.tmatesoft.svn.cli2.command;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.cli2.SVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNNotifyPrinter;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNLockCommand extends SVNCommand {

    public SVNLockCommand() {
        super("lock", null);
    }
    
    public boolean isCommitter() {
        return true;
    }
    
    public String getFileAmbigousErrorMessage() {
        return "Lock comment file is a versioned file; use '--force-log' to override";
    }

    public String getMessageAmbigousErrorMessage() {
        return "The lock comment is a pathname (was -F intended?); use '--force-log' to override";
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.MESSAGE);
        options.add(SVNOption.FILE);
        options.add(SVNOption.FORCE_LOG);
        options.add(SVNOption.ENCODING);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.FORCE);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        Collection targets = new ArrayList(); 
        if (getEnvironment().getChangelist() != null) {
            SVNCommandTarget target = new SVNCommandTarget("");
            SVNChangelistClient changelistClient = getEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(target.getFile(), getEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no such changelist ''{0}''", getEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        if (getEnvironment().getTargets() != null) {
            targets.addAll(getEnvironment().getTargets());
        }
        targets = getEnvironment().combineTargets(targets);
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
        }
        SVNWCClient client = getEnvironment().getClientManager().getWCClient();
        client.setEventHandler(new SVNNotifyPrinter(getEnvironment()));
        String message = getLockMessage();
        Collection paths = new ArrayList();
        Collection urls = new ArrayList();
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNCommandTarget target = new SVNCommandTarget(targetName);
            if (target.isURL()) {
                urls.add(target.getURL());
            } else {
                paths.add(target.getFile());
            }
        }
        if (!paths.isEmpty()) {
            File[] filesArray = (File[]) paths.toArray(new File[paths.size()]);
            client.doLock(filesArray, getEnvironment().isForce(), message);
        }
        if (!urls.isEmpty()) {
            SVNURL[] urlsArray = (SVNURL[]) urls.toArray(new SVNURL[urls.size()]);
            client.doLock(urlsArray, getEnvironment().isForce(), message);
        }
    }
    
    protected String getLockMessage() throws SVNException {
        if (getEnvironment().getFileData() != null) {
            byte[] data = getEnvironment().getFileData();
            for (int i = 0; i < data.length; i++) {
                if (data[i] == 0) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_BAD_LOG_MESSAGE, "Log message contains a zero byte"));
                }
            }
            try {
                return new String(getEnvironment().getFileData(), getEnvironment().getEncoding() != null ? getEnvironment().getEncoding() : "UTF-8");
            } catch (UnsupportedEncodingException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()));
            }
        } else if (getEnvironment().getMessage() != null) {
            return getEnvironment().getMessage();
        }
        return null;
    }

}
