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
package org.tmatesoft.svn.cli.svnadmin;

import java.util.Collection;
import java.util.LinkedList;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminDumpCommand extends SVNAdminCommand implements ISVNAdminEventHandler {

    public SVNAdminDumpCommand() {
        super("dump", null);
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNAdminOption.REVISION);
        options.add(SVNAdminOption.INCREMENTAL);
        options.add(SVNAdminOption.DELTAS);
        options.add(SVNAdminOption.QUIET);
        return options;
    }
    
    public void run() throws SVNException {

        SVNRevision start = getSVNAdminEnvironment().getStartRevision();
        SVNRevision end = getSVNAdminEnvironment().getEndRevision();
        
        SVNURL url = SVNURL.fromFile(getLocalRepository());
        SVNRepository repository = SVNRepositoryFactory.create(url);
        repository.setCanceller(getEnvironment());
        long latestRevision = repository.getLatestRevision();
        long startRev = getRevisionNumber(start, latestRevision, repository);
        long endRev = getRevisionNumber(end, latestRevision, repository);
        if (startRev < 0) {
            startRev = 0;
            endRev = latestRevision;
        } else if (endRev < 0) {
            endRev = startRev;
        }
        
        if (startRev > endRev) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "First revision cannot be higher than second"), SVNLogType.CLIENT);
        }

        boolean incremental = getSVNAdminEnvironment().isIncremental();
        boolean deltas = getSVNAdminEnvironment().isDeltas();

        SVNAdminClient client = getEnvironment().getClientManager().getAdminClient();
        if (!getSVNAdminEnvironment().isQuiet()) {
            client.setEventHandler(this);
        }
        start = SVNRevision.create(startRev);
        end = SVNRevision.create(endRev);
        client.doDump(getLocalRepository(), getEnvironment().getOut(), start, end, incremental, deltas);
    }

    public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
        if (event != null && event.getAction() == SVNAdminEventAction.REVISION_DUMPED) {
            getEnvironment().getErr().println(event.getMessage());
        }
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
    }

    public void checkCancelled() throws SVNCancelException {
        getEnvironment().checkCancelled();
    }

}
