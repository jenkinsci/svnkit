/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNAdminCreateCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public void run(PrintStream out, PrintStream err) throws SVNException {
        String fsType = (String) getCommandLine().getArgumentValue(SVNArgument.FS_TYPE);
        if (fsType != null && !"fsfs".equals(fsType)) {
            SVNCommand.println(out, "Unsupported repository type '" + fsType + "'");
            System.exit(1);
        }
        
        boolean isOldFormat = getCommandLine().hasArgument(SVNArgument.PRE_14_COMPATIBLE);
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(out, "jsvnadmin: Repository argument required");
            System.exit(1);
        }
        
        String absolutePath = getCommandLine().getPathAt(0);
        SVNRepositoryFactory.createLocalRepository(new File(absolutePath), null, false, false, isOldFormat);
    }

}
