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
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.admin.ISVNChangeEntryHandler;
import org.tmatesoft.svn.core.wc.admin.SVNChangeEntry;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNLookChangedCommand extends SVNLookCommand implements ISVNChangeEntryHandler {

    public SVNLookChangedCommand() {
        super("changed", null);
    }

    protected Collection createSupportedOptions() {
        List options = new LinkedList();
        options.add(SVNLookOption.REVISION);
        options.add(SVNLookOption.TRANSACTION);
        options.add(SVNLookOption.COPY_INFO);
        return options;
    }

    public void run() throws SVNException {
        SVNLookCommandEnvironment environment = getSVNLookEnvironment(); 
        SVNLookClient client = environment.getClientManager().getLookClient();
        if (environment.isRevision()) {
            client.doGetChanged(environment.getRepositoryFile(), 
                    getRevisionObject(), this, environment.isCopyInfo());
        } else {
            client.doGetChanged(environment.getRepositoryFile(), environment.getTransaction(), this, 
                    environment.isCopyInfo());
        }
    }

    public void handleEntry(SVNChangeEntry entry) throws SVNException {
        String[] status = new String[3];
        status[0] = entry.getType() == SVNChangeEntry.TYPE_UPDATED && !entry.hasTextModifications() ? "_" : 
            "" + entry.getType();
        status[1] = entry.hasPropertyModifications() ? "" + SVNChangeEntry.TYPE_UPDATED : " ";
        status[2] = entry.getCopyFromPath() != null ? "+" : " ";
        String path = !entry.getPath().endsWith("/") && entry.getKind() == SVNNodeKind.DIR ? 
                entry.getPath() + "/" : entry.getPath(); 
        path = path.startsWith("/") ? path.substring(1) : path;

        SVNLookCommandEnvironment environment = getSVNLookEnvironment(); 
        environment.getOut().println(status[0] + status[1] + status[2] + " " + path);
        if (entry.getCopyFromPath() != null) {
            String copyFromPath = entry.getCopyFromPath();
            if (copyFromPath.startsWith("/")) {
                copyFromPath = copyFromPath.substring(1);    
            }
            if (!copyFromPath.endsWith("/") && entry.getKind() == SVNNodeKind.DIR) {
                copyFromPath += "/";    
            }
            environment.getOut().println("    (from " + copyFromPath + ":r" + entry.getCopyFromRevision() + ")");
        }
    }

}
