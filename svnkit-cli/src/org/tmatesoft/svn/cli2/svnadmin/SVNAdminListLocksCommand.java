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
package org.tmatesoft.svn.cli2.svnadmin;

import java.util.Collection;
import java.util.LinkedList;

import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNOptions;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNAdminListLocksCommand extends SVNAdminCommand {

    public SVNAdminListLocksCommand() {
        super("lslocks", null);
    }

    protected Collection createSupportedOptions() {
        return new LinkedList();
    }

    public void run() throws SVNException {
        ISVNOptions options = getEnvironment().getClientManager().getOptions();
        
        SVNURL url = SVNURL.fromFile(getLocalRepository());
        SVNRepository repository = SVNRepositoryFactory.create(url);
        
        repository.setCanceller(getEnvironment());
        SVNLock[] locks = repository.getLocks("/");
        for (int i = 0; locks != null && i < locks.length; i++) {
            SVNLock lock = locks[i];
            StringBuffer buffer = new StringBuffer();
            
            String comment = "(0 lines):";
            if (lock.getComment() != null) {
                int count = SVNCommandUtil.getLinesCount(lock.getComment());
                comment = count != 1 ? count + " lines" : count + " line";
                comment = "(" + comment + "):\n" + lock.getComment();
            }
            
            String created = lock.getCreationDate() != null ? SVNFormatUtil.formatHumanDate(lock.getCreationDate(), options) : "";
            String expires = lock.getExpirationDate() != null ? SVNFormatUtil.formatHumanDate(lock.getExpirationDate(), options) : "";
            
            buffer.append("Path: " + lock.getPath() + "\n");
            buffer.append("UUID Token: " + lock.getID() + "\n");
            buffer.append("Owner: " + lock.getOwner() + "\n");
            buffer.append("Created: " + created + "\n");
            buffer.append("Expires: " + expires + "\n");
            buffer.append("Comment " + comment + "\n\n");
            
            getEnvironment().getOut().print(buffer.toString());
        }
    }
}
