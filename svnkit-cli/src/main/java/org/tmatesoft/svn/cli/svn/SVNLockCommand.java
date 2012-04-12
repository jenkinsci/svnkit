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
package org.tmatesoft.svn.cli.svn;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
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
        options.add(SVNOption.FORCE);
        return options;
    }

    public void run() throws SVNException {
        Collection targets = new ArrayList(); 
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets, true);
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS), SVNLogType.CLIENT);
        }
        SVNWCClient client = getSVNEnvironment().getClientManager().getWCClient();
        client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        String message = getLockMessage();
        Collection paths = new ArrayList();
        Collection urls = new ArrayList();
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String targetName = (String) ts.next();
            SVNPath target = new SVNPath(targetName);
            if (target.isURL()) {
                urls.add(target.getURL());
            } else {
                paths.add(target.getFile());
            }
        }
        if (!paths.isEmpty()) {
            File[] filesArray = (File[]) paths.toArray(new File[paths.size()]);
            try {
                client.doLock(filesArray, getSVNEnvironment().isForce(), message);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    if (e.getErrorMessage().getRelatedObjects() != null
                            && e.getErrorMessage().getRelatedObjects().length > 0
                            && e.getErrorMessage().getRelatedObjects()[0] instanceof File) {
                        e.getErrorMessage().getRelatedObjects()[0] = ((File) e.getErrorMessage().getRelatedObjects()[0]).getAbsolutePath();
                    }
                }
                throw e;
            }
        }
        if (!urls.isEmpty()) {
            SVNURL[] urlsArray = (SVNURL[]) urls.toArray(new SVNURL[urls.size()]);
            client.doLock(urlsArray, getSVNEnvironment().isForce(), message);
        }
    }
    
    protected String getLockMessage() throws SVNException {
        if (getSVNEnvironment().getFileData() != null) {
            byte[] data = getSVNEnvironment().getFileData();
            for (int i = 0; i < data.length; i++) {
                if (data[i] == 0) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_BAD_LOG_MESSAGE, "Log message contains a zero byte"), SVNLogType.CLIENT);
                }
            }
            try {
                return new String(getSVNEnvironment().getFileData(), getSVNEnvironment().getEncoding() != null ? getSVNEnvironment().getEncoding() : "UTF-8");
            } catch (UnsupportedEncodingException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage()), SVNLogType.CLIENT);
            }
        } else if (getSVNEnvironment().getMessage() != null) {
            return getSVNEnvironment().getMessage();
        }
        return null;
    }

}
