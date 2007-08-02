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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.cli2.SVNCommand;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.cli2.SVNNotifyPrinter;
import org.tmatesoft.svn.cli2.SVNOption;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNStatusCommand extends SVNCommand implements ISVNStatusHandler {

    private SVNStatusPrinter myStatusPrinter;
    private Map myStatusCache;

    public SVNStatusCommand() {
        super("status", new String[] {"stat", "st"});
    }
    
    protected Collection createSupportedOptions() {
        Collection options = new HashSet();

        options.add(SVNOption.UPDATE);
        options.add(SVNOption.VERBOSE);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.NO_IGNORE);
        options.add(SVNOption.INCREMENTAL);
        options.add(SVNOption.XML);

        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.IGNORE_EXTERNALS);
        options.add(SVNOption.CHANGELIST);

        return SVNOption.addAuthOptions(options);
    }

    public void run() throws SVNException {
        Collection targets = new ArrayList(); 
        if (getEnvironment().getChangelist() != null) {
            getEnvironment().setOperatingPath("", new File("").getAbsoluteFile());
            SVNChangelistClient changelistClient = getEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(getEnvironment().getOperatingFile(), getEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no such changelist ''{0}''", getEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        targets = getEnvironment().combineTargets(targets);
        if (targets.isEmpty()) {
            targets.add("");
        }
        myStatusPrinter = new SVNStatusPrinter(getEnvironment());
        SVNStatusClient client = getEnvironment().getClientManager().getStatusClient();
        if (!getEnvironment().isXML()) {
            client.setEventHandler(new SVNNotifyPrinter(getEnvironment()));
        }
        for (Iterator ts = targets.iterator(); ts.hasNext();) {
            String target = (String) ts.next();
            getEnvironment().setOperatingPath(target, new File(target).getAbsoluteFile());
            try {
                client.doStatus(getEnvironment().getOperatingFile(), SVNRevision.HEAD,
                        getEnvironment().getDepth(), getEnvironment().isUpdate(),
                        getEnvironment().isVerbose(), getEnvironment().isNoIgnore(),
                        false, this);
            } catch (SVNException e) {
                getEnvironment().handleWarning(e.getErrorMessage(), new SVNErrorCode[] {SVNErrorCode.WC_NOT_DIRECTORY});
            }
        }
        if (myStatusCache != null) {
            for (Iterator changelists = myStatusCache.keySet().iterator(); changelists.hasNext();) {
                String changelist = (String) changelists.next();
                Map statuses = (Map) myStatusCache.get(changelist);
                getEnvironment().getOut().println("\n--- Changelist '" + changelist + "':");
                for (Iterator paths = statuses.keySet().iterator(); paths.hasNext();) {
                    String path = (String) paths.next();
                    SVNStatus status = (SVNStatus) statuses.get(path);
                    myStatusPrinter.printStatus(path, status, 
                            getEnvironment().isVerbose() || getEnvironment().isUpdate(), 
                            getEnvironment().isVerbose(), getEnvironment().isQuiet(), getEnvironment().isUpdate());
                }
            }
        }
    }

    public void handleStatus(SVNStatus status) throws SVNException {
        String path = getEnvironment().getRelativePath(status.getFile());
        path = SVNCommandUtil.getLocalPath(path);
        if (status != null && status.getChangelistName() != null) {
            if (myStatusCache == null) {
                myStatusCache = new TreeMap();
            }
            if (!myStatusCache.containsKey(status.getChangelistName())) {
                myStatusCache.put(status.getChangelistName(), new LinkedHashMap());
            }
            ((Map) myStatusCache.get(status.getChangelistName())).put(path, status);
            return;
        }
        myStatusPrinter.printStatus(path, status, 
                getEnvironment().isVerbose() || getEnvironment().isUpdate(), 
                getEnvironment().isVerbose(), getEnvironment().isQuiet(), getEnvironment().isUpdate());
    }
}
